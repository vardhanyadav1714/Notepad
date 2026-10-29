import assert from 'node:assert/strict';
import test from 'node:test';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve, dirname, basename } from 'node:path';
import { RoomStore } from '../storage.mjs';
import { createReportMailer } from '../report-mail.mjs';

test('report mail survives timeout and restart with stable idempotency and no room/ink data', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'drawly-mail-test-'));
  const options = { databasePath: join(directory, 'rooms.sqlite') };
  let store = new RoomStore(options), mailer, now = Date.now();
  const requests = [];
  const config = { apiKey: 'fake-test-key', from: 'reports@drawly.example', to: 'support@drawly.example', autoStart: false, clock: () => now,
    fetcher: async (url, request) => { requests.push({ url, ...request }); throw Error('Timeout after provider may have accepted mail'); } };
  try {
    const room = await store.call('create');
    const report = await store.call('report', { id: room.roomId, reason: 'unsafe_content' });
    mailer = createReportMailer({ store, ...config });
    await mailer.tick();
    assert.equal(requests.length, 1);
    const payload = JSON.parse(requests[0].body);
    assert.match(payload.text, new RegExp(report.reportId));
    assert.match(payload.text, /unsafe_content/);
    assert.equal(payload.text.includes(room.roomId), false);
    assert.equal(payload.text.includes(room.ownerToken), false);
    assert.deepEqual(Object.keys(payload).sort(), ['from', 'subject', 'text', 'to']);
    await mailer.close(); await store.close();
    store = new RoomStore(options);
    now += 61_000;
    mailer = createReportMailer({ store, ...config, fetcher: async (url, request) => { requests.push({ url, ...request }); return new Response('{}', { status: 200 }); } });
    await mailer.tick();
    assert.equal(requests.length, 2);
    assert.equal(requests[0].body, requests[1].body);
    assert.equal(requests[0].headers['Idempotency-Key'], requests[1].headers['Idempotency-Key']);
    await mailer.tick();
    assert.equal(requests.length, 2);
    assert.deepEqual((await store.call('reportMailStatus')).batches, [{ status: 'sent', count: 1 }]);
    assert.equal((await store.call('listReports')).length, 1);
  } finally {
    await mailer?.close(); await store.close();
    const target = resolve(directory);
    assert.equal(dirname(target), resolve(tmpdir())); assert.ok(basename(target).startsWith('drawly-mail-test-'));
    await rm(target, { recursive: true, force: true });
  }
});

test('mail is disabled without configuration; digests are capped and provider failures retain report queue', async () => {
  const store = new RoomStore({ databasePath: ':memory:' });
  let now = Date.now(), attempts = 0;
  const disabled = createReportMailer({ store, autoStart: false });
  const mailer = createReportMailer({ store, apiKey: 'fake', from: 'reports@drawly.example', to: 'support@drawly.example',
    autoStart: false, clock: () => now, dailyLimit: 2, fetcher: async () => { attempts++; return new Response('{}', { status: 503 }); } });
  try {
    assert.equal(disabled.enabled, false); await disabled.tick();
    const room = await store.call('create');
    await store.call('report', { id: room.roomId, reason: 'bullying' });
    await mailer.tick(); now += 61_000;
    await mailer.tick(); now += 121_000;
    await mailer.tick();
    assert.equal(attempts, 2);
    assert.equal((await store.call('listReports')).length, 1);
    assert.deepEqual((await store.call('reportMailStatus')).batches, [{ status: 'pending', count: 1 }]);
    // A key may no longer be idempotent after 24h: fail visibly instead of
    // silently resending a potentially accepted digest with an expired key.
    now += 86_400_000;
    await mailer.tick();
    assert.equal(attempts, 2);
    assert.deepEqual((await store.call('reportMailStatus')).batches, [{ status: 'failed', count: 1 }]);
  } finally { await mailer.close(); await disabled.close(); await store.close(); }
});

test('resolved unassigned reports are not emailed; permanent provider errors are visible to operators', async () => {
  const store = new RoomStore({ databasePath: ':memory:' });
  let attempts = 0;
  const mailer = createReportMailer({ store, apiKey: 'fake', from: 'reports@drawly.example', to: 'support@drawly.example',
    autoStart: false, fetcher: async () => { attempts++; return new Response('{}', { status: 401 }); } });
  try {
    const room = await store.call('create');
    const resolved = await store.call('report', { id: room.roomId, reason: 'other' });
    await store.call('resolveReport', { id: resolved.reportId });
    await mailer.tick(); assert.equal(attempts, 0);
    await store.call('report', { id: room.roomId, reason: 'personal_information' });
    await mailer.tick(); await mailer.tick(); assert.equal(attempts, 1);
    assert.deepEqual((await store.call('reportMailStatus')).batches, [{ status: 'failed', count: 1 }]);
    assert.equal((await store.call('listReports')).length, 1);
  } finally { await mailer.close(); await store.close(); }
});
