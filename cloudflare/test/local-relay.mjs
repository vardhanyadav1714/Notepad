import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { setTimeout as delay } from 'node:timers/promises';

const origin = process.argv[2] ?? 'http://127.0.0.1:8791';
assert.equal(new URL(origin).hostname, '127.0.0.1', 'This probe is restricted to the local worker');
const room = `qa-${randomUUID()}`;
const sockets = [];
async function until(predicate) {
  const end = Date.now() + 10000;
  while (Date.now() < end) {
    const result = predicate();
    if (result) return result;
    await delay(25);
  }
  throw new Error('Timed out waiting for the local relay');
}
async function connect() {
  const received = [];
  const ws = new WebSocket(`${origin.replace('http:', 'ws:')}/ws/${room}`);
  sockets.push(ws);
  ws.addEventListener('message', event => received.push(JSON.parse(event.data)));
  await until(() => ws.readyState === WebSocket.OPEN);
  await until(() => received.find(message => message.type === 'room_snapshot'));
  const userId = randomUUID();
  ws.send(JSON.stringify({ type: 'join', userId }));
  await until(() => received.find(message => message.type === 'joined'));
  return { ws, received, userId };
}
function stroke(index) {
  return { id: `stroke-${index}`, userId: 'qa-user', pageId: 'page-2', createdAt: index,
    points: [{ x: index, y: 20, pressure: .5, tilt: .2, orientation: .3, timestamp: index }],
    toolConfig: { type: 'PEN', style: 'SOLID', colorArgb: -16777216, baseWidth: 5, opacity: 1, usePressure: true } };
}
try {
  const first = await connect();
  const second = await connect();
  await until(() => second.received.find(message => message.type === 'presence' && message.members.length === 2));
  first.ws.send(JSON.stringify({ type: 'presence', activity: 'drawing', cursor: { x: 10, y: 20, pageId: 'page-2' } }));
  first.ws.send(JSON.stringify({ type: 'stroke_preview', stroke: stroke(-1) }));
  const preview = await until(() => second.received.find(message => message.type === 'stroke_preview' && message.stroke));
  assert.equal(preview.stroke.userId, first.userId);
  await until(() => second.received.find(message => message.type === 'presence_update' && message.member.activity === 'drawing'));
  const temporary = await connect();
  assert.equal(temporary.received.find(message => message.type === 'room_snapshot').strokes.length, 0);
  first.ws.send(JSON.stringify({ type: 'stroke_preview', stroke: null }));
  await until(() => second.received.find(message => message.type === 'stroke_preview' && message.stroke === null));
  temporary.ws.close(1000, 'Transient probe complete');
  console.log('PASS: member roster, live activity, bounded previews, and no transient ink in room storage');
  for (let index = 0; index < 20; index++) {
    (index % 2 ? first : second).ws.send(JSON.stringify({ type: 'stroke', stroke: stroke(index) }));
  }
  await until(() => first.received.filter(message => message.type === 'stroke').length === 20);
  await until(() => second.received.filter(message => message.type === 'stroke').length === 20);
  const fresh = await connect();
  const snapshot = fresh.received.find(message => message.type === 'room_snapshot');
  assert.equal(snapshot.strokes.length, 20);
  assert.equal(new Set(snapshot.strokes.map(item => item.id)).size, 20);
  assert.equal(snapshot.strokes[0].pageId, 'page-2');
  assert.equal(snapshot.strokes[0].points[0].tilt, .2);
  console.log('PASS: paired clients, concurrent stroke persistence, fresh reconnect, page and stylus metadata');
  first.ws.send('null');
  await until(() => first.received.find(message => message.type === 'error'));
  first.ws.send(JSON.stringify({ type: 'ping' }));
  await until(() => first.received.find(message => message.type === 'pong'));
  console.log('PASS: malformed input does not break the room');
  first.ws.send(JSON.stringify({ type: 'canvas_state', state: { userId: 'qa-user', strokes: [stroke(0)], updatedAt: Date.now() } }));
  await until(() => second.received.find(message => message.type === 'canvas_state'));
  const final = await connect();
  assert.equal(final.received.find(message => message.type === 'room_snapshot').strokes.length, 1);
  console.log('PASS: canvas edits persist and reach other clients');
} finally {
  sockets.forEach(ws => ws.close(1000, 'QA complete'));
}
