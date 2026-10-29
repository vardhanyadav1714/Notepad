import { createServer } from 'node:http';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { isIP } from 'node:net';
import { WebSocketServer, WebSocket } from 'ws';
import { RoomStore } from './storage.mjs';
import { legalPage } from './legal.mjs';
import { createReportMailer } from './report-mail.mjs';
import worker, { CanvasRoom } from './dist/protocol.mjs';

const IDLE_TTL = 86_400_000;
/** The Worker and standalone host still share the same wire protocol. */
export function createCollaborationServer({ databasePath = './data/rooms.sqlite', publicOrigin = 'http://localhost:3000', maxRooms = 250, maxStorageBytes, maxStoredRooms, idleTtlMs = IDLE_TTL, maintenanceMs = 30_000, trustProxy = false, legalApproved = false,
  developerName = 'Innovatix Hub', supportEmail = '', backupRetentionDays, logRetentionDays,
  mail = {}, onStorageFailure } = {}) {
  const origin = new URL(publicOrigin);
  if (!['http:', 'https:'].includes(origin.protocol) || origin.pathname !== '/' || origin.username || origin.password || origin.search || origin.hash) throw Error('PUBLIC_ORIGIN must be an HTTP(S) origin');
  const store = new RoomStore({ databasePath: databasePath === ':memory:' ? databasePath : resolve(databasePath), maxStorageBytes, maxStoredRooms, idleTtlMs, onFailure: onStorageFailure });
  const reportMailer = createReportMailer({ store, ...mail });
  const sharingAvailable = legalApproved && Number.isInteger(backupRetentionDays) && backupRetentionDays >= 0 && backupRetentionDays <= 365 &&
    Number.isInteger(logRetentionDays) && logRetentionDays >= 0 && logRetentionDays <= 365 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(supportEmail) && developerName.trim().length > 0;
  const rooms = new Map();
  const wss = new WebSocketServer({ noServer: true, maxPayload: 1_500_000, perMessageDeflate: false, clientTracking: true });
  let shuttingDown = false, maintenanceBusy = false, queuedBytes = 0, cachedBytes = 0, outboundBytes = 0;
  const requests = new Map();
  function address(request) {
    const forwarded = request.headers['x-forwarded-for']?.split(',').at(-1)?.trim();
    return trustProxy && forwarded && isIP(forwarded) ? forwarded : request.socket.remoteAddress ?? 'unknown';
  }
  function admitted(address, cost = 1) {
    const now = Date.now();
    let entry = requests.get(address);
    if (!entry || now - entry.at > 60_000) {
      if (requests.size >= 10_000 && !entry) return false;
      entry = { at: now, count: 0 }; requests.set(address, entry);
    }
    entry.count += cost;
    return entry.count <= 300;
  }
  function roomFor(id) {
    if (rooms.has(id)) return rooms.get(id);
    const entry = { peers: new Set(), pending: 0, queued: 0, queuedBytes: 0, cacheBytes: 0, tail: Promise.resolve(), lastUsed: Date.now() };
    let values, alarmAt = 0, touchedAt = 0;
    function reserve(bytes) {
      if (cachedBytes - entry.cacheBytes + bytes > 32 * 1024 * 1024) throw Error('Active canvas memory limit reached');
      cachedBytes += bytes - entry.cacheBytes; entry.cacheBytes = bytes;
    }
    const loaded = store.call('load', { id }).then(data => {
      reserve(Buffer.byteLength(JSON.stringify(data.strokes ?? []))); values = data;
    });
    loaded.catch(() => {});
    const storage = {
      async get(key) { await loaded; return Array.isArray(key) ? new Map(key.filter(k => k in values).map(k => [k, values[k]])) : values[key]; },
      async appendInk(stroke) {
        await loaded;
        const size = Buffer.byteLength(JSON.stringify(stroke)) + 1;
        if (cachedBytes + size > 32 * 1024 * 1024) throw Error('Active canvas memory limit reached');
        const previous = entry.cacheBytes;
        reserve(previous + size);
        try {
          if (await store.call('append', { id, stroke })) values.strokes.push(stroke);
          else reserve(previous);
        } catch (error) { reserve(previous); throw error; }
        values.lastActiveAt = Date.now(); touchedAt = Date.now(); alarmAt = Date.now() + idleTtlMs;
      },
      async put(key, value) {
        await loaded;
        const now = Date.now();
        // Heartbeat metadata is separate from ink and coalesced. A committed
        // edit already refreshes expiry in the same SQLite transaction.
        if (key === 'lastActiveAt' && now - touchedAt < 30_000) { values[key] = value; return; }
        const bytes = key === 'strokes' ? Buffer.byteLength(JSON.stringify(value)) : entry.cacheBytes;
        if (cachedBytes - entry.cacheBytes + bytes > 32 * 1024 * 1024) throw Error('Active canvas memory limit reached');
        const previous = entry.cacheBytes;
        if (key === 'strokes') reserve(bytes);
        try { await store.call('put', { id, key, value }); values[key] = value; }
        catch (error) { if (key === 'strokes') reserve(previous); throw error; }
        touchedAt = now; alarmAt = now + idleTtlMs;
      },
      async transaction(callback) { return callback(storage); },
      async setAlarm(at) {
        await loaded;
        at = Math.min(at, Date.now() + idleTtlMs);
        if (Math.abs(at - alarmAt) < 30_000) return;
        await store.call('alarm', { id, at }); alarmAt = at;
      },
      async deleteAll() { await store.call('remove', { id }); values = { strokes: [] }; reserve(0); }
    };
    const state = { storage, acceptWebSocket: peer => entry.peers.add(peer),
      getWebSockets: () => [...entry.peers].filter(peer => peer.raw.readyState === WebSocket.OPEN) };
    entry.protocol = new CanvasRoom(state, {}); entry.loaded = loaded;
    rooms.set(id, entry);
    return entry;
  }
  function forget(id, entry) { if (rooms.get(id) === entry) { rooms.delete(id); cachedBytes -= entry.cacheBytes; } }
  function enqueue(entry, operation, onError = () => {}, bytes = 0) {
    entry.lastUsed = Date.now(); entry.queued++; entry.queuedBytes += bytes; queuedBytes += bytes;
    const result = entry.tail.then(operation).catch(onError).finally(() => {
      entry.queued--; entry.queuedBytes -= bytes; queuedBytes -= bytes;
    });
    // A failed HTTP operation must not poison all subsequent room operations.
    entry.tail = result.catch(() => {});
    return result;
  }
  function jsonResponse(response, status, value) {
    response.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' }).end(JSON.stringify(value));
  }
  async function readJson(request) {
    let text = '';
    for await (const chunk of request) { text += chunk.toString(); if (Buffer.byteLength(text) > 2048) throw Error('Request too large'); }
    return JSON.parse(text);
  }
  const server = createServer(async (request, response) => {
    try {
      if (shuttingDown) { response.writeHead(503).end(); return; }
      const url = new URL(request.url ?? '/', origin);
      if (url.pathname === '/health') {
        // Probe the worker now; startup readiness alone would stay green if
        // the live storage queue later stalls. Bound probe time for Coolify.
        let deadline;
        try {
          await Promise.race([store.call('ready'), new Promise((_, reject) => {
            deadline = setTimeout(() => reject(Error('Storage probe timed out')), 2000);
          })]);
        } finally { clearTimeout(deadline); }
        jsonResponse(response, 200, { status: 'ok' }); return;
      }
      if (url.pathname === '/api/config' && request.method === 'GET') {
        jsonResponse(response, 200, { sharingAvailable, privacyUrl: `${origin.origin}/privacy`, termsUrl: `${origin.origin}/terms` }); return;
      }
      if (['/privacy', '/terms'].includes(url.pathname) && ['GET', 'HEAD'].includes(request.method)) {
        if (!sharingAvailable) { jsonResponse(response, 503, { error: 'Policy publication pending operator review' }); return; }
        response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-cache',
          'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'no-referrer', 'Content-Security-Policy': "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'" });
        response.end(request.method === 'HEAD' ? undefined : legalPage(url.pathname, { developerName, supportEmail, backupRetentionDays, logRetentionDays, reportMailEnabled: reportMailer.enabled })); return;
      }
      if (url.pathname === '/api/rooms' || url.pathname.startsWith('/api/rooms/')) {
        if (request.headers.origin && request.headers.origin !== origin.origin) { jsonResponse(response, 403, { error: 'Origin not allowed' }); return; }
        if (!admitted(address(request), 5)) { jsonResponse(response, 429, { error: 'Please try later' }); return; }
        if (url.pathname === '/api/rooms' && request.method === 'POST') {
          if (!sharingAvailable) { jsonResponse(response, 503, { error: 'New sharing sessions are not available yet' }); return; }
          const result = await store.call('create');
          jsonResponse(response, 201, { ...result, shareUrl: `${origin.origin}/r/${result.roomId}` }); return;
        }
        const match = /^\/api\/rooms\/([a-zA-Z0-9_-]{1,128})(\/reports)?$/.exec(url.pathname);
        if (!match) { jsonResponse(response, 404, { error: 'Not found' }); return; }
        const id = match[1];
        if (match[2] && request.method === 'POST') {
          const body = await readJson(request);
          jsonResponse(response, 201, await store.call('report', { id, reason: body.reason })); return;
        }
        if (!match[2] && request.method === 'DELETE') {
          const token = request.headers.authorization?.replace(/^Bearer /, '') ?? '';
          if (!/^[a-f0-9]{64}$/.test(token)) { jsonResponse(response, 403, { error: 'Owner authorization required' }); return; }
          const entry = rooms.get(id);
          const end = async () => {
            await store.call('end', { id, token });
            if (entry) { entry.ended = true; for (const peer of entry.peers) peer.raw.close(1008, 'Room ended by its owner'); }
          };
          if (entry) await enqueue(entry, end, error => { throw error; }); else await end();
          jsonResponse(response, 200, { deleted: true }); return;
        }
        jsonResponse(response, 405, { error: 'Method not allowed' }); return;
      }
      if (url.pathname.startsWith('/ws/')) { response.writeHead(426).end('WebSocket upgrade required'); return; }
      if (!['GET', 'HEAD', 'POST'].includes(request.method)) { response.writeHead(405).end(); return; }
      const result = await worker.fetch(new Request(url, { method: request.method }), {});
      response.writeHead(result.status, { ...Object.fromEntries(result.headers), 'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'no-referrer' });
      response.end(request.method === 'HEAD' ? undefined : await result.text());
    } catch (error) {
      jsonResponse(response, error.code === 'FORBIDDEN' ? 403 : error.code === 'GONE' ? 410 : error.code === 'LIMIT' ? 429 : 503,
        { error: 'Request unavailable. Please retry or use a new room.' });
    }
  });
  server.headersTimeout = 10_000; server.requestTimeout = 15_000;
  server.on('upgrade', async (request, socket, head) => {
    function reject(status) { socket.end(`HTTP/1.1 ${status}\r\nConnection: close\r\n\r\n`); }
    try {
      if (shuttingDown || store.failed) { reject('503 Service Unavailable'); return; }
      const path = new URL(request.url ?? '/', origin).pathname;
      const id = decodeURIComponent(path.slice(4));
      if (!path.startsWith('/ws/') || !/^[a-zA-Z0-9_-]{1,128}$/.test(id)) { reject('400 Bad Request'); return; }
      if (request.headers.origin && request.headers.origin !== origin.origin) { reject('403 Forbidden'); return; }
      if (!admitted(address(request)) || wss.clients.size >= 1000) { reject('429 Too Many Requests'); return; }
      if (!rooms.has(id) && rooms.size >= maxRooms) { reject('503 Service Unavailable'); return; }
      const entry = roomFor(id);
      if (entry.peers.size + entry.pending >= 20) { reject('429 Too Many Requests'); return; }
      entry.pending++; let upgraded = false;
      socket.once('close', () => { if (!upgraded) entry.pending--; });
      try { await entry.loaded; } catch (error) {
        if (!entry.peers.size) forget(id, entry);
        reject(error.code === 'GONE' ? '410 Gone' : '503 Service Unavailable'); return;
      }
      if (socket.destroyed || entry.ended) { reject('410 Gone'); return; }
      wss.handleUpgrade(request, socket, head, raw => {
        upgraded = true;
        let attachment = null, ended = false;
        const peer = { raw,
          send(text) {
            if (raw.readyState !== WebSocket.OPEN) return;
            if (raw.bufferedAmount > 128_000 && /^\{"type":"(?:presence|presence_update|stroke_preview)"/.test(text)) return;
            if (raw.bufferedAmount > 2_000_000) { raw.close(1013, 'Connection too slow; reconnect'); return; }
            const bytes = Buffer.byteLength(text);
            if (outboundBytes + bytes > 32 * 1024 * 1024) { raw.close(1013, 'Service busy; reconnect'); return; }
            outboundBytes += bytes;
            let completed = false;
            const done = () => { if (!completed) { completed = true; outboundBytes -= bytes; } };
            try { raw.send(text, done); } catch { done(); raw.terminate(); }
          },
          close(code, reason) { if (raw.readyState === WebSocket.OPEN) raw.close(code, reason); },
          serializeAttachment(value) { attachment = structuredClone(value); }, deserializeAttachment() { return attachment; }
        };
        enqueue(entry, async () => { if (raw.readyState === WebSocket.OPEN && !entry.ended) await entry.protocol.attachSocket(peer, id); },
          () => raw.close(1011, 'Room storage unavailable')).finally(() => entry.pending--);
        let incomingAt = Date.now(), durableCount = 0, disposableCount = 0, pendingBytes = 0, pendingMessages = 0;
        raw.on('message', (data, binary) => {
          if (binary || entry.ended || ended || raw.readyState !== WebSocket.OPEN) return;
          if (Date.now() - incomingAt >= 10_000) { incomingAt = Date.now(); durableCount = 0; disposableCount = 0; }
          const text = data.toString(); let disposable = false;
          try { const type = JSON.parse(text)?.type; disposable = type === 'presence' || type === 'stroke_preview'; } catch { /* protocol handles bounded invalid input */ }
          if (disposable) {
            if (++disposableCount > 300 || data.length > 64_000) return;
            // Transient protocol operations are synchronous and do not mutate
            // durable ink. Never retain obsolete previews behind disk writes.
            entry.protocol.webSocketMessage(peer, text).catch(() => {}); return;
          }
          if (++durableCount > 240 || pendingMessages >= 32 || pendingBytes + data.length > 2_000_000 ||
              entry.queued >= 256 || entry.queuedBytes + data.length > 4_000_000 || queuedBytes + data.length > 16_000_000) {
            raw.close(1013, 'Too many pending updates; reconnect'); return;
          }
          pendingMessages++; pendingBytes += data.length;
          enqueue(entry, () => { if (!entry.ended) return entry.protocol.webSocketMessage(peer, text); },
            () => peer.send('{"type":"error","message":"Could not sync this update. Your local note is preserved."}'), data.length)
            .finally(() => { pendingMessages--; pendingBytes -= data.length; });
        });
        const leave = () => {
          if (ended) return; ended = true;
          enqueue(entry, () => { entry.peers.delete(peer); if (!entry.ended) entry.protocol.webSocketClose(peer, 1000, 'Disconnected', true); });
        };
        raw.on('close', leave); raw.on('error', () => { leave(); raw.terminate(); });
        raw.isAlive = true; raw.on('pong', () => { raw.isAlive = true; });
      });
    } catch { reject('400 Bad Request'); }
  });
  const maintenance = setInterval(async () => {
    if (maintenanceBusy || shuttingDown) return;
    maintenanceBusy = true;
    try {
      for (const socket of wss.clients) {
        if (!socket.isAlive) { socket.terminate(); continue; }
        socket.isAlive = false; socket.ping();
      }
      for (const [id, entry] of rooms) {
        if (entry.peers.size === 0 && entry.pending === 0 && entry.queued === 0 && Date.now() - entry.lastUsed > 60_000) forget(id, entry);
      }
      const swept = await store.call('sweep', { active: [...rooms].filter(([, room]) => !room.ended && (room.peers.size || room.pending || room.queued)).map(([id]) => id) });
      for (const id of swept.expired) {
        const room = rooms.get(id);
        if (room && !room.peers.size && !room.pending && !room.queued) forget(id, room);
      }
      for (const id of swept.revoked) {
        const room = rooms.get(id);
        if (room) { room.ended = true; for (const peer of room.peers) peer.raw.close(1008, 'Room ended'); }
      }
      if (!wss.clients.size) await store.call('compact');
      for (const [address, entry] of requests) if (Date.now() - entry.at > 60_000) requests.delete(address);
    } catch { /* Never log private room content. Worker failure makes health fail. */ }
    finally { maintenanceBusy = false; }
  }, maintenanceMs);
  maintenance.unref();
  async function close() {
    if (shuttingDown) return;
    shuttingDown = true; clearInterval(maintenance);
    for (const socket of wss.clients) socket.terminate();
    await new Promise(resolveClose => server.close(resolveClose));
    await Promise.all([...rooms.values()].map(room => room.tail));
    wss.close(); await reportMailer.close(); await store.close();
  }
  return { server, close, stats: () => ({ cachedBytes, queuedBytes, outboundBytes }) };
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const retention = name => /^\d+$/.test(process.env[name] ?? '') ? Number(process.env[name]) : undefined;
  const port = Number(process.env.PORT ?? 3000);
  const app = createCollaborationServer({ databasePath: process.env.DATABASE_PATH ?? './data/rooms.sqlite', publicOrigin: process.env.PUBLIC_ORIGIN ?? `http://localhost:${port}`, trustProxy: process.env.TRUST_PROXY === '1',
    legalApproved: process.env.DRAWLY_LEGAL_APPROVED === '1', developerName: process.env.DRAWLY_DEVELOPER_NAME,
    supportEmail: process.env.DRAWLY_SUPPORT_EMAIL, backupRetentionDays: retention('DRAWLY_BACKUP_RETENTION_DAYS'),
    logRetentionDays: retention('DRAWLY_LOG_RETENTION_DAYS'),
    mail: { apiKey: process.env.RESEND_API_KEY, from: process.env.DRAWLY_REPORT_FROM, to: process.env.DRAWLY_REPORT_TO },
    onStorageFailure: () => { console.error('Drawly storage failed; restarting service'); process.exit(1); } });
  app.server.listen(port, '0.0.0.0', () => console.log(`Drawly collaboration listening on port ${port}`));
  for (const signal of ['SIGTERM', 'SIGINT']) process.once(signal, () => app.close().then(() => process.exit(0)));
}
