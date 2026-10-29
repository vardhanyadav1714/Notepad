import assert from 'node:assert/strict';
import test from 'node:test';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve, dirname, basename } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { DatabaseSync } from 'node:sqlite';
import { WebSocket } from 'ws';
import { createCollaborationServer } from '../server.mjs';
import { backupRooms } from '../backup.mjs';

async function until(predicate) {
  const end = Date.now() + 8000;
  while (Date.now() < end) { const result = predicate(); if (result) return result; await delay(10); }
  throw Error('Timed out waiting for collaboration');
}
async function start(databasePath = ':memory:', options = {}) {
  const app = createCollaborationServer({ databasePath, legalApproved: true, supportEmail: 'support@drawly.example', backupRetentionDays: 0, logRetentionDays: 0, ...options });
  await new Promise(resolve => app.server.listen(0, '127.0.0.1', resolve));
  return { ...app, base: `http://127.0.0.1:${app.server.address().port}` };
}
async function removeTestDirectory(directory) {
  const target = resolve(directory);
  assert.equal(dirname(target), resolve(tmpdir()));
  assert.ok(basename(target).startsWith('drawly-collaboration-test-'));
  await rm(target, { recursive: true, force: true });
}
async function connect(app, room = 'qa-room', userId = 'qa-user') {
  const socket = new WebSocket(app.base.replace('http:', 'ws:') + '/ws/' + room);
  const received = [];
  socket.on('message', data => received.push(JSON.parse(data.toString())));
  await new Promise((resolve, reject) => { socket.once('open', resolve); socket.once('error', reject); });
  await until(() => received.some(message => message.type === 'room_snapshot'));
  socket.send(JSON.stringify({ type: 'join', userId }));
  await until(() => received.some(message => message.type === 'joined'));
  return { socket, received, send: message => socket.send(JSON.stringify(message)),
    memberId: received.find(message => message.type === 'joined').memberId };
}
function ink(index = 1) {
  return { id: `stroke-${index}`, userId: 'qa-user', pageId: 'page-1', createdAt: index,
    points: [{ x: 10, y: 20, pressure: .5 }, { x: 100, y: 120, pressure: .8 }],
    toolConfig: { type: 'PEN', style: 'SOLID', colorArgb: -16777216, baseWidth: 4, opacity: 1, usePressure: true } };
}

test('real paired clients see members and live ink; transient state is not saved', async () => {
  const app = await start();
  try {
    const a = await connect(app, 'qa-room', 'artist-a');
    const b = await connect(app, 'qa-room', 'artist-b');
    await until(() => a.received.some(message => message.type === 'presence' && message.members.length === 2));
    a.send({ type: 'presence', activity: 'drawing', userId: 'spoofed', cursor: { x: 100, y: 120, pageId: 'page-1' } });
    a.send({ type: 'stroke_preview', stroke: ink() });
    const update = await until(() => b.received.find(message => message.type === 'presence_update'));
    assert.equal(update.member.id, a.memberId);
    assert.equal(update.member.userId, 'artist-a');
    assert.match(update.member.label, /^Artist [0-9A-F]{4}$/);
    const preview = await until(() => b.received.find(message => message.type === 'stroke_preview'));
    assert.equal(preview.stroke.userId, 'artist-a');
    const c = await connect(app);
    assert.deepEqual(c.received.find(message => message.type === 'room_snapshot').strokes, []);
    a.send({ type: 'stroke', stroke: ink() });
    await until(() => b.received.find(message => message.type === 'stroke'));
    await until(() => b.received.find(message => message.type === 'stroke_preview' && message.stroke === null));
    a.socket.close(1000, 'Leaving');
    await until(() => b.received.some(message => message.type === 'presence' && !message.members.some(member => member.id === a.memberId)));
    const d = await connect(app);
    assert.equal(d.received.find(message => message.type === 'room_snapshot').strokes.length, 1);
    b.send({ type: 'canvas_state', state: { userId: 'artist-b', strokes: [ink(2)], updatedAt: Date.now() } });
    await until(() => d.received.some(message => message.type === 'canvas_state'));
    const edited = await connect(app);
    assert.deepEqual(edited.received.find(message => message.type === 'room_snapshot').strokes.map(stroke => stroke.id), ['stroke-2']);
  } finally { await app.close(); }
});

test('concurrent durable ink survives a service restart; rooms stay isolated', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'drawly-collaboration-test-'));
  const path = join(directory, 'rooms.sqlite');
  let app = await start(path);
  try {
    const a = await connect(app);
    const b = await connect(app);
    const other = await connect(app, 'other-room');
    for (let i = 0; i < 40; i++) (i % 2 ? a : b).send({ type: 'stroke', stroke: ink(i) });
    await until(() => a.received.filter(message => message.type === 'stroke').length === 40);
    assert.equal(other.received.some(message => message.type === 'stroke'), false);
    await app.close();
    app = await start(path);
    const restored = await connect(app);
    const strokes = restored.received.find(message => message.type === 'room_snapshot').strokes;
    assert.equal(strokes.length, 40);
    assert.equal(new Set(strokes.map(stroke => stroke.id)).size, 40);
  } finally { await app.close(); await removeTestDirectory(directory); }
});

test('a full room rejects the next client while existing peers still sync', async () => {
  const app = await start();
  try {
    const peers = [];
    for (let i = 0; i < 20; i++) peers.push(await connect(app, 'full-room', `artist-${i}`));
    const rejected = new WebSocket(app.base.replace('http:', 'ws:') + '/ws/full-room');
    const status = await new Promise(resolve => {
      rejected.on('unexpected-response', (_request, response) => { response.resume(); rejected.terminate(); resolve(response.statusCode); });
      rejected.on('error', () => {});
    });
    assert.equal(status, 429);
    peers[0].send({ type: 'stroke', stroke: ink() });
    await until(() => peers[19].received.some(message => message.type === 'stroke'));
  } finally { await app.close(); }
});

test('expired stored ink is not served after restart', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'drawly-collaboration-test-'));
  const path = join(directory, 'rooms.sqlite');
  let app = await start(path);
  try {
    const a = await connect(app);
    a.send({ type: 'stroke', stroke: ink() });
    await until(() => a.received.some(message => message.type === 'stroke'));
    await app.close();
    const db = new DatabaseSync(path);
    db.prepare('UPDATE rooms SET expires_at = ?').run(Date.now() - 1000);
    db.close();
    app = await start(path);
    const b = await connect(app);
    assert.deepEqual(b.received.find(message => message.type === 'room_snapshot').strokes, []);
  } finally { await app.close(); await removeTestDirectory(directory); }
});

test('HTTP health, malformed input, and hostile browser origins are handled', async () => {
  const app = await start();
  try {
    assert.equal((await fetch(app.base + '/health')).status, 200);
    assert.equal((await fetch(app.base + '/ws/qa-room')).status, 426);
    assert.equal((await fetch(app.base + '/r/%ZZ')).status, 400);
    const a = await connect(app);
    a.socket.send('null');
    await until(() => a.received.some(message => message.type === 'error'));
    a.send({ type: 'ping' });
    await until(() => a.received.some(message => message.type === 'pong'));
    const rejected = new WebSocket(app.base.replace('http:', 'ws:') + '/ws/qa-room', { origin: 'https://untrusted.example' });
    const status = await new Promise(resolve => {
      rejected.on('unexpected-response', (_request, response) => { response.resume(); rejected.terminate(); resolve(response.statusCode); });
      rejected.on('error', () => {});
    });
    assert.equal(status, 403);
  } finally { await app.close(); }
});

test('only the creator capability can end a managed room; ink and invites stay deleted after restart', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'drawly-collaboration-test-'));
  const path = join(directory, 'rooms.sqlite');
  let app = await start(path);
  try {
    const response = await fetch(app.base + '/api/rooms', { method: 'POST' });
    assert.equal(response.status, 201);
    const { roomId, ownerToken } = await response.json();
    const a = await connect(app, roomId);
    a.send({ type: 'stroke', stroke: ink() });
    await until(() => a.received.some(message => message.type === 'stroke'));
    assert.equal((await fetch(`${app.base}/api/rooms/${roomId}`, { method: 'DELETE', headers: { Authorization: 'Bearer ' + 'a'.repeat(64) } })).status, 403);
    const closed = new Promise(resolve => a.socket.once('close', code => resolve(code)));
    assert.equal((await fetch(`${app.base}/api/rooms/${roomId}`, { method: 'DELETE', headers: { Authorization: `Bearer ${ownerToken}` } })).status, 200);
    assert.equal(await closed, 1008);
    await app.close(); app = await start(path);
    const rejected = new WebSocket(app.base.replace('http:', 'ws:') + '/ws/' + roomId);
    const status = await new Promise(resolve => {
      rejected.on('unexpected-response', (_, response) => { response.resume(); rejected.terminate(); resolve(response.statusCode); });
      rejected.on('error', () => {});
    });
    assert.equal(status, 410);
    const db = new DatabaseSync(path);
    assert.equal(db.prepare('SELECT COUNT(*) AS count FROM room_ink').get().count, 0);
    assert.equal(db.prepare('SELECT COUNT(*) AS count FROM room_owners').get().count, 0);
    db.close();
  } finally { await app.close(); await removeTestDirectory(directory); }
});

test('a legacy full-payload database migrates without losing drawing or metadata', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'drawly-collaboration-test-'));
  const path = join(directory, 'rooms.sqlite');
  const db = new DatabaseSync(path);
  db.exec('CREATE TABLE rooms(id TEXT PRIMARY KEY,payload TEXT NOT NULL,expires_at INTEGER NOT NULL)');
  db.prepare('INSERT INTO rooms VALUES(?,?,?)').run('legacy', JSON.stringify({ strokes: [ink(10), ink(11)], lastActiveAt: Date.now() }), Date.now() + 86_400_000);
  db.close();
  let app = await start(path);
  try {
    const a = await connect(app, 'legacy');
    assert.deepEqual(a.received.find(message => message.type === 'room_snapshot').strokes.map(stroke => stroke.id), ['stroke-10', 'stroke-11']);
    a.send({ type: 'stroke', stroke: ink(12) });
    await until(() => a.received.some(message => message.type === 'stroke'));
    await app.close(); app = await start(path);
    const b = await connect(app, 'legacy');
    assert.equal(b.received.find(message => message.type === 'room_snapshot').strokes.length, 3);
  } finally { await app.close(); await removeTestDirectory(directory); }
});

test('storage quota rejects a write without broadcasting or changing the durable snapshot', async () => {
  const app = await start(':memory:', { maxStorageBytes: 100 });
  try {
    const a = await connect(app);
    a.send({ type: 'stroke', stroke: ink() });
    await until(() => a.received.some(message => message.type === 'error'));
    assert.equal(a.received.some(message => message.type === 'stroke'), false);
    const b = await connect(app);
    assert.deepEqual(b.received.find(message => message.type === 'room_snapshot').strokes, []);
  } finally { await app.close(); }
});

test('report acknowledgements are durable; reports contain a reason and no copied ink', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'drawly-collaboration-test-'));
  const path = join(directory, 'rooms.sqlite');
  const app = await start(path);
  try {
    const { roomId } = await (await fetch(app.base + '/api/rooms', { method: 'POST' })).json();
    const result = await fetch(`${app.base}/api/rooms/${roomId}/reports`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ reason: 'bullying', email: 'ignored', drawing: 'ignored' }) });
    assert.equal(result.status, 201);
    const { reportId } = await result.json();
    const db = new DatabaseSync(path);
    const report = db.prepare('SELECT * FROM reports WHERE id=?').get(reportId);
    assert.equal(report.reason, 'bullying');
    assert.equal(report.room_id, roomId);
    assert.equal(Object.hasOwn(report, 'email'), false);
    assert.equal(Object.hasOwn(report, 'drawing'), false);
    db.close();
  } finally { await app.close(); await removeTestDirectory(directory); }
});

test('an update flood is disconnected and does not prevent another room from syncing', async () => {
  const app = await start();
  try {
    const a = await connect(app, 'flood');
    const b = await connect(app, 'healthy');
    const closed = new Promise(resolve => a.socket.once('close', code => resolve(code)));
    for (let i = 0; i < 400; i++) a.send({ type: 'stroke', stroke: ink(i) });
    assert.equal(await closed, 1013);
    b.send({ type: 'stroke', stroke: ink() });
    await until(() => b.received.some(message => message.type === 'stroke'));
    await until(() => app.stats().queuedBytes === 0);
  } finally { await app.close(); }
});

test('new sharing and legal publication remain off until operator review and retention values are supplied', async () => {
  const app = await start(':memory:', { legalApproved: false });
  try {
    assert.equal((await (await fetch(app.base + '/api/config')).json()).sharingAvailable, false);
    assert.equal((await fetch(app.base + '/api/rooms', { method: 'POST' })).status, 503);
    assert.equal((await fetch(app.base + '/privacy')).status, 503);
    // Existing installations' wire protocol remains available during rollout.
    const old = await connect(app, 'legacy-installed-app');
    old.send({ type: 'stroke', stroke: ink() });
    await until(() => old.received.some(message => message.type === 'stroke'));
  } finally { await app.close(); }
  const missing = await start(':memory:', { legalApproved: true, backupRetentionDays: undefined });
  try { assert.equal((await (await fetch(missing.base + '/api/config')).json()).sharingAvailable, false); }
  finally { await missing.close(); }
});

test('approved privacy and rules pages escape operator input and have restrictive browser headers', async () => {
  const app = await start(':memory:', { developerName: '<script>alert(1)</script>' });
  try {
    const response = await fetch(app.base + '/privacy');
    assert.equal(response.status, 200);
    const text = await response.text();
    assert.ok(text.includes('&lt;script&gt;'));
    assert.ok(!text.includes('<script>'));
    assert.ok(!text.includes('does not address anyone under'));
    assert.match(response.headers.get('content-security-policy'), /frame-ancestors 'none'/);
    assert.equal((await fetch(app.base + '/terms')).status, 200);
  } finally { await app.close(); }
});

test('SQLite-aware backup restores acknowledged ink while the source WAL remains live', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'drawly-collaboration-test-'));
  const path = join(directory, 'rooms.sqlite');
  const source = await start(path);
  let restored;
  try {
    const a = await connect(source, 'backup-room');
    a.send({ type: 'stroke', stroke: ink(50) });
    await until(() => a.received.some(message => message.type === 'stroke'));
    const copy = await backupRooms(path, join(directory, 'backups'), 7);
    restored = await start(copy);
    const b = await connect(restored, 'backup-room');
    assert.deepEqual(b.received.find(message => message.type === 'room_snapshot').strokes.map(stroke => stroke.id), ['stroke-50']);
    a.send({ type: 'stroke', stroke: ink(51) });
    await until(() => a.received.filter(message => message.type === 'stroke').length === 2);
    assert.equal(b.received.some(message => message.type === 'stroke'), false);
  } finally { if (restored) await restored.close(); await source.close(); await removeTestDirectory(directory); }
});

test('idle sweep deletes expired ink and invalidates its RAM snapshot without requiring a restart', async () => {
  const app = await start(':memory:', { idleTtlMs: 150, maintenanceMs: 50 });
  try {
    const a = await connect(app, 'idle-expiry');
    a.send({ type: 'stroke', stroke: ink() });
    await until(() => a.received.some(message => message.type === 'stroke'));
    const closed = new Promise(resolve => a.socket.once('close', resolve));
    a.socket.close(); await closed;
    await until(() => app.stats().cachedBytes === 0);
    const b = await connect(app, 'idle-expiry');
    assert.deepEqual(b.received.find(message => message.type === 'room_snapshot').strokes, []);
  } finally { await app.close(); }
});
