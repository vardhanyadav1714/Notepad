import { createServer } from 'node:http';
import { DatabaseSync } from 'node:sqlite';
import { mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { WebSocketServer, WebSocket } from 'ws';
import worker, { CanvasRoom } from './dist/protocol.mjs';

const IDLE_TTL = 24 * 60 * 60 * 1000;

/** The actual Worker room protocol runs here too; there is no second sync engine. */
export function createCollaborationServer({ databasePath = './data/rooms.sqlite', publicOrigin = 'http://localhost:3000', maxRooms = 250 } = {}) {
  const origin = new URL(publicOrigin);
  if (!['http:', 'https:'].includes(origin.protocol) || origin.pathname !== '/' || origin.username || origin.password || origin.search || origin.hash) throw Error('PUBLIC_ORIGIN must be an HTTP(S) origin');
  if (databasePath !== ':memory:') mkdirSync(dirname(resolve(databasePath)), { recursive: true });
  const db = new DatabaseSync(databasePath);
  db.exec('PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000; CREATE TABLE IF NOT EXISTS rooms (id TEXT PRIMARY KEY, payload TEXT NOT NULL, expires_at INTEGER NOT NULL)');
  const read = db.prepare('SELECT payload, expires_at FROM rooms WHERE id = ?');
  const write = db.prepare('INSERT INTO rooms(id,payload,expires_at) VALUES(?,?,?) ON CONFLICT(id) DO UPDATE SET payload=excluded.payload, expires_at=excluded.expires_at');
  const remove = db.prepare('DELETE FROM rooms WHERE id = ?');
  const extend = db.prepare('UPDATE rooms SET expires_at = ? WHERE id = ?');
  const rooms = new Map();
  const wss = new WebSocketServer({ noServer: true, maxPayload: 1_500_000, perMessageDeflate: false, clientTracking: true });
  let shuttingDown = false;

  function roomFor(id) {
    if (rooms.has(id)) return rooms.get(id);
    const stored = read.get(id);
    let values = stored && stored.expires_at > Date.now() ? JSON.parse(stored.payload) : {};
    if (stored && stored.expires_at <= Date.now()) remove.run(id);
    const entry = { peers: new Set(), pending: 0, tail: Promise.resolve(), lastUsed: Date.now(), protocol: null };
    const storage = {
      async get(key) { return Array.isArray(key) ? new Map(key.filter(k => k in values).map(k => [k, values[k]])) : values[key]; },
      async put(key, value) {
        const updated = { ...values, [key]: value };
        write.run(id, JSON.stringify(updated), Date.now() + IDLE_TTL);
        values = updated; // Storage errors must not silently change the in-memory document.
      },
      // Whole messages are serialized by enqueue below, so the read/modify/write
      // transaction cannot race another stroke or canvas edit in this room.
      async transaction(callback) { return callback(storage); },
      async setAlarm(at) { extend.run(at, id); },
      async deleteAll() { remove.run(id); values = {}; }
    };
    const state = { storage, acceptWebSocket: peer => entry.peers.add(peer),
      getWebSockets: () => [...entry.peers].filter(peer => peer.raw.readyState === WebSocket.OPEN) };
    entry.protocol = new CanvasRoom(state, {});
    rooms.set(id, entry);
    return entry;
  }

  function enqueue(entry, operation, onError = () => {}) {
    entry.lastUsed = Date.now();
    entry.tail = entry.tail.then(operation).catch(onError);
    return entry.tail;
  }

  const server = createServer(async (request, response) => {
    try {
      if (shuttingDown) { response.writeHead(503).end(); return; }
      const url = new URL(request.url ?? '/', origin);
      if (url.pathname === '/health') {
        response.writeHead(200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' }).end('{"status":"ok"}');
        return;
      }
      if (url.pathname.startsWith('/ws/')) { response.writeHead(426).end('WebSocket upgrade required'); return; }
      if (!['GET', 'HEAD', 'POST'].includes(request.method)) { response.writeHead(405).end(); return; }
      const result = await worker.fetch(new Request(url, { method: request.method }), {});
      response.writeHead(result.status, { ...Object.fromEntries(result.headers), 'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'no-referrer' });
      response.end(request.method === 'HEAD' ? undefined : await result.text());
    } catch { response.writeHead(500).end('Temporarily unavailable'); }
  });
  server.headersTimeout = 10_000;
  server.requestTimeout = 15_000;

  server.on('upgrade', (request, socket, head) => {
    function reject(status) { socket.end(`HTTP/1.1 ${status}\r\nConnection: close\r\n\r\n`); }
    try {
      if (shuttingDown) { reject('503 Service Unavailable'); return; }
      const path = new URL(request.url ?? '/', origin).pathname;
      const id = decodeURIComponent(path.slice(4));
      if (!path.startsWith('/ws/') || !/^[a-zA-Z0-9_-]{1,128}$/.test(id)) { reject('400 Bad Request'); return; }
      if (request.headers.origin && request.headers.origin !== origin.origin) { reject('403 Forbidden'); return; }
      if (!rooms.has(id) && rooms.size >= maxRooms) { reject('503 Service Unavailable'); return; }
      const entry = roomFor(id);
      if (entry.peers.size + entry.pending >= 20) { reject('429 Too Many Requests'); return; }
      entry.pending++;
      let upgraded = false;
      socket.once('close', () => { if (!upgraded) entry.pending--; });
      wss.handleUpgrade(request, socket, head, raw => {
        upgraded = true;
        let attachment = null;
        let ended = false;
        const peer = { raw,
          send(text) {
            if (raw.readyState !== WebSocket.OPEN) return;
            if (raw.bufferedAmount > 128_000 && /^\{"type":"(?:presence|presence_update|stroke_preview)"/.test(text)) return;
            if (raw.bufferedAmount > 2_000_000) { raw.close(1013, 'Connection too slow; reconnect'); return; }
            raw.send(text);
          },
          close(code, reason) { if (raw.readyState === WebSocket.OPEN) raw.close(code, reason); },
          serializeAttachment(value) { attachment = structuredClone(value); },
          deserializeAttachment() { return attachment; }
        };
        enqueue(entry, async () => {
          if (raw.readyState === WebSocket.OPEN) await entry.protocol.attachSocket(peer, id);
        }, () => raw.close(1011, 'Room storage unavailable')).finally(() => entry.pending--);
        raw.on('message', (data, binary) => {
          if (!binary) enqueue(entry, () => entry.protocol.webSocketMessage(peer, data.toString()), () => peer.send('{"type":"error","message":"Could not sync this update. Your local note is preserved."}'));
        });
        const leave = () => {
          if (ended) return;
          ended = true;
          enqueue(entry, () => { entry.peers.delete(peer); entry.protocol.webSocketClose(peer, 1000, 'Disconnected', true); });
        };
        raw.on('close', leave);
        raw.on('error', () => { leave(); raw.terminate(); });
        raw.isAlive = true;
        raw.on('pong', () => { raw.isAlive = true; });
      });
    } catch { reject('400 Bad Request'); }
  });

  const maintenance = setInterval(() => {
    for (const socket of wss.clients) {
      if (!socket.isAlive) { socket.terminate(); continue; }
      socket.isAlive = false;
      socket.ping();
    }
    for (const [id, entry] of rooms) {
      if (entry.peers.size === 0 && entry.pending === 0 && Date.now() - entry.lastUsed > 60_000)
        enqueue(entry, () => { if (entry.peers.size === 0 && entry.pending === 0) rooms.delete(id); });
    }
    const expired = db.prepare('SELECT id FROM rooms WHERE expires_at <= ? LIMIT 1000').all(Date.now());
    for (const row of expired) {
      const active = rooms.get(row.id);
      if (active?.peers.size) enqueue(active, () => active.protocol.alarm());
      else remove.run(row.id);
    }
  }, 30_000);
  maintenance.unref();

  async function close() {
    if (shuttingDown) return;
    shuttingDown = true;
    clearInterval(maintenance);
    for (const socket of wss.clients) socket.terminate();
    await new Promise(resolveClose => server.close(resolveClose));
    await Promise.all([...rooms.values()].map(room => room.tail));
    wss.close();
    db.close();
  }
  return { server, close };
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const port = Number(process.env.PORT ?? 3000);
  const app = createCollaborationServer({ databasePath: process.env.DATABASE_PATH ?? './data/rooms.sqlite', publicOrigin: process.env.PUBLIC_ORIGIN ?? `http://localhost:${port}` });
  app.server.listen(port, '0.0.0.0', () => console.log(`Drawly collaboration listening on port ${port}`));
  for (const signal of ['SIGTERM', 'SIGINT']) process.once(signal, () => app.close().then(() => process.exit(0)));
}
