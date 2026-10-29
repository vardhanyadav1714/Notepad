import assert from 'node:assert/strict';
import test from 'node:test';
import { setTimeout as delay } from 'node:timers/promises';
import { RoomStore } from '../storage.mjs';

test('a fatal storage-worker exit invokes recovery once and rejects further work', async () => {
  let failures = 0;
  const store = new RoomStore({ databasePath: ':memory:', onFailure: () => failures++ });
  await store.ready;
  await store.worker.terminate();
  for (let i = 0; failures === 0 && i < 100; i++) await delay(10);
  assert.equal(failures, 1);
  await assert.rejects(store.call('create'), /Room storage unavailable/);
  await store.close().catch(() => {});
  assert.equal(failures, 1);
});

test('a planned graceful shutdown does not invoke failure recovery', async () => {
  let failures = 0;
  const store = new RoomStore({ databasePath: ':memory:', onFailure: () => failures++ });
  await store.ready;
  await store.close();
  assert.equal(failures, 0);
});
