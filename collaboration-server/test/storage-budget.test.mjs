import assert from 'node:assert/strict';
import test from 'node:test';
import { RoomStore } from '../storage.mjs';

test('transactional quota counter releases capacity when a room is deleted', async () => {
  const store = new RoomStore({ databasePath: ':memory:', maxStorageBytes: 6 });
  try {
    const rooms = await Promise.all(Array.from({ length: 3 }, () => store.call('create')));
    await assert.rejects(store.call('create'), { code: 'LIMIT' });
    assert.equal((await store.call('sweep')).inkBytes, 6);
    await store.call('end', { id: rooms[0].roomId, token: rooms[0].ownerToken });
    assert.equal((await store.call('sweep')).inkBytes, 4);
    await store.call('create');
    assert.equal((await store.call('sweep')).inkBytes, 6);
    await assert.rejects(store.call('load', { id: 'legacy-new-room' }), { code: 'LIMIT' });
    assert.equal((await store.call('sweep')).rooms, 3);
  } finally { await store.close(); }
});
