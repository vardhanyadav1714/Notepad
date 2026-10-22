import assert from 'node:assert/strict';
import test from 'node:test';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve, dirname, basename } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { DatabaseSync } from 'node:sqlite';
import { WebSocket } from 'ws';
import { createCollaborationServer } from '../server.mjs';

async function until(predicate) {
  const end = Date.now() + 8000;
  while (Date.now() < end) { const result = predicate(); if (result) return result; await delay(10); }
  throw Error('Timed out waiting for collaboration');
}
async function start(databasePath = ':memory:') {
  const app = createCollaborationServer({ databasePath });
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
