import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import ts from 'typescript';

// Exercise the actual worker source with platform doubles; deployment is separate.
const source = readFileSync(new URL('../src/index.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 }
});
const { default: worker, CanvasRoom } = await import(`data:text/javascript;base64,${Buffer.from(outputText + '\n//# sourceURL=drawly-worker-under-test.js').toString('base64')}`);

function fixture() {
  const values = new Map();
  const sent = [];
  const socket = {
    send: text => sent.push(JSON.parse(text)),
    close: () => {},
    serializeAttachment: () => {},
    deserializeAttachment: () => ({})
  };
  const storage = {
    get: async key => values.get(key),
    put: async (key, value) => { values.set(key, value); },
    setAlarm: async () => {},
    transaction: async callback => callback(storage)
  };
  const state = { storage, getWebSockets: () => [socket], waitUntil: promise => promise };
  return { room: new CanvasRoom(state, {}), socket, sent, values, storage };
}

function stroke() {
  return { id: 'stroke-1', userId: 'user-1', points: [{ x: 1, y: 2, pressure: .5 }],
    toolConfig: { type: 'PEN', style: 'SOLID', colorArgb: -16777216, baseWidth: 4, opacity: 1, usePressure: true },
    createdAt: 1, pageId: 'page-1' };
}

test('malformed JSON and non-object messages return errors', async () => {
  for (const message of ['{', 'null', '42', '"stroke"', '[]']) {
    const f = fixture();
    await f.room.webSocketMessage(f.socket, message);
    assert.equal(f.sent.at(-1)?.type, 'error', message);
  }
});

test('null or primitive stroke points are rejected without crashing the room', async () => {
  for (const point of [null, 5, 'x', []]) {
    const f = fixture();
    await f.room.webSocketMessage(f.socket, JSON.stringify({ type: 'stroke', stroke: { ...stroke(), points: [point] } }));
    assert.equal(f.sent.at(-1)?.type, 'error');
    assert.equal(f.values.has('strokes'), false);
  }
});

test('valid strokes persist before being broadcast and survive a subsequent update', async () => {
  const f = fixture();
  await f.room.webSocketMessage(f.socket, JSON.stringify({ type: 'stroke', stroke: stroke() }));
  assert.equal(f.values.get('strokes')?.length, 1);
  assert.equal(f.sent.at(-1)?.type, 'stroke');
  await f.room.webSocketMessage(f.socket, JSON.stringify({ type: 'stroke', stroke: { ...stroke(), id: 'stroke-2' } }));
  assert.deepEqual(f.values.get('strokes').map(s => s.id), ['stroke-1', 'stroke-2']);
});

test('malformed URL encoding returns a client error', async () => {
  const response = await worker.fetch(new Request('https://example.com/ws/%ZZ', { headers: { Upgrade: 'websocket' } }), {});
  assert.equal(response.status, 400);
});

test('malformed canvas state does not overwrite persisted ink', async () => {
  const f = fixture();
  f.values.set('strokes', [stroke()]);
  await f.room.webSocketMessage(f.socket, JSON.stringify({ type: 'canvas_state', state: { userId: 'user-1', strokes: [null] } }));
  assert.equal(f.sent.at(-1)?.type, 'error');
  assert.equal(f.values.get('strokes').length, 1);
});

test('a failed storage write reports an error without broadcasting success', async () => {
  const f = fixture();
  f.storage.put = async () => { throw new Error('Storage unavailable'); };
  await f.room.webSocketMessage(f.socket, JSON.stringify({ type: 'stroke', stroke: stroke() }));
  assert.deepEqual(f.sent.map(message => message.type), ['error']);
});

test('a full room rejects new strokes while keeping all earlier ink', async () => {
  const f = fixture();
  const existing = Array.from({ length: 1200 }, (_, index) => ({ ...stroke(), id: `existing-${index}` }));
  f.values.set('strokes', existing);
  await f.room.webSocketMessage(f.socket, JSON.stringify({ type: 'stroke', stroke: stroke() }));
  assert.equal(f.sent.at(-1)?.type, 'error');
  assert.deepEqual(f.values.get('strokes'), existing);
});

test('oversized messages and invalid room paths are rejected', async () => {
  const f = fixture();
  await f.room.webSocketMessage(f.socket, 'x'.repeat(1_500_001));
  assert.equal(f.sent.at(-1)?.type, 'error');
  for (const path of ['/r/%ZZ', '/r/../../bad?room=bad/path', '/ws/' + 'x'.repeat(129)]) {
    const response = await worker.fetch(new Request('https://example.com' + path), {});
    assert.equal(response.status, 400);
  }
});
