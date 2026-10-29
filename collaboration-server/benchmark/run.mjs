// Synthetic loopback only. No URL argument: cannot target a live endpoint.
import assert from 'node:assert/strict';
import { fork } from 'node:child_process';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve, dirname, basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { DatabaseSync } from 'node:sqlite';
import { WebSocket } from 'ws';

const directory = await mkdtemp(join(tmpdir(), 'drawly-benchmark-'));
const databasePath = join(directory, 'rooms.sqlite');
const child = fork(fileURLToPath(new URL('./host.mjs', import.meta.url)), [databasePath], {
  execArgv: ['--max-old-space-size=192'], windowsHide: true, stdio: ['ignore', 'ignore', 'pipe', 'ipc']
});
const messages = [], errors = [], sockets = [], sent = new Map(), latencies = [];
child.on('message', message => messages.push(message));
child.stderr.on('data', data => { if (!String(data).includes('ExperimentalWarning')) errors.push('Server stderr'); });
const watchdog = setTimeout(() => { child.kill(); }, 70_000);
async function until(check, timeout = 12_000) {
  const end = Date.now() + timeout;
  while (Date.now() < end) { const result = check(); if (result) return result; await delay(10); }
  throw Error('Local benchmark timed out');
}
async function stats() { const before = messages.length; child.send({ type: 'stats' }); return until(() => messages.slice(before).find(x => x.type === 'stats')); }
const report = { scope: 'Synthetic local loopback; no production CPU quota or network guarantee', clients: 100, rooms: 10, clientsPerRoom: 10,
  rounds: 8, pointsPerStroke: 64, mainHeapMiB: 192, workerHeapMiB: 96 };
try {
  const { base } = await until(() => messages.find(x => x.type === 'ready'));
  for (let start = 0; start < 100; start += 10) await Promise.all(Array.from({ length: 10 }, (_, offset) => new Promise((resolve, reject) => {
    const id = start + offset, socket = new WebSocket(base.replace('http:', 'ws:') + '/ws/local-bench-' + Math.floor(id / 10));
    sockets.push(socket); socket.on('error', reject);
    socket.on('open', () => socket.send(JSON.stringify({ type: 'join', userId: 'bench-' + id })));
    socket.on('message', data => {
      const message = JSON.parse(String(data));
      if (message.type === 'joined') resolve();
      if (message.type === 'error') errors.push(message.message);
      if (message.type === 'stroke' && sent.has(message.stroke.id)) latencies.push(performance.now() - sent.get(message.stroke.id));
    });
  })));
  const ink = (id, round) => ({ id: `bench-${id}-${round}`, userId: `bench-${id}`, pageId: 'page-1', createdAt: Date.now(),
    points: Array.from({ length: 64 }, (_, i) => ({ x: i * 2, y: i, pressure: .5, timestamp: i })),
    toolConfig: { type: 'PEN', style: 'SOLID', colorArgb: -16777216, baseWidth: 4, opacity: 1, usePressure: true } });
  child.send({ type: 'measure' });
  const ephemeral = setInterval(() => sockets.forEach((socket, i) => {
    socket.send(JSON.stringify({ type: 'presence', activity: 'drawing', cursor: { x: 50, y: 50, pageId: 'page-1' } }));
    socket.send(JSON.stringify({ type: 'stroke_preview', stroke: ink(i, 'preview') }));
  }), 333);
  try {
    for (let round = 0; round < 8; round++) {
      const at = performance.now();
      sockets.forEach((socket, i) => { const stroke = ink(i, round); sent.set(stroke.id, performance.now()); socket.send(JSON.stringify({ type: 'stroke', stroke })); });
      await delay(Math.max(0, 1000 - (performance.now() - at)));
    }
  } finally { clearInterval(ephemeral); }
  await until(() => latencies.length === 8000, 20_000);
  latencies.sort((a, b) => a - b);
  Object.assign(report, { strokes: sent.size, expectedDeliveries: 8000, deliveries: latencies.length,
    p50Ms: latencies[4000], p95Ms: latencies[7600], maxMs: latencies.at(-1), metrics: await stats(), protocolErrors: errors });
  sockets.forEach(socket => socket.close(1000, 'Local benchmark finished')); await delay(500);
  // This path belongs to the fresh local fixture, never the live data volume.
  const db = new DatabaseSync(databasePath); db.prepare('UPDATE rooms SET expires_at=?').run(Date.now() - 1); db.close();
  let after;
  for (let n = 0; n < 30; n++) { after = await stats(); if (after.rows === 0) break; await delay(100); }
  report.roomsAfterExpiry = after.rows;
  report.passed = latencies.length === 8000 && errors.length === 0 && after.rows === 0;
  if (!report.passed) process.exitCode = 1;
} catch (error) { report.passed = false; report.error = error.message; process.exitCode = 1; }
finally {
  sockets.forEach(socket => socket.terminate());
  if (child.connected) child.send({ type: 'close' });
  if (child.exitCode === null) await Promise.race([new Promise(resolve => child.once('exit', resolve)), delay(5000)]);
  if (child.exitCode === null) child.kill(); clearTimeout(watchdog);
  const target = resolve(directory);
  assert.equal(dirname(target), resolve(tmpdir())); assert.ok(basename(target).startsWith('drawly-benchmark-'));
  await rm(target, { recursive: true, force: true });
}
console.log(JSON.stringify(report, null, 2));
