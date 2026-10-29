import { RoomStore } from './storage.mjs';
import { resolve } from 'node:path';

// Operator-only CLI, inside the Drawly container. No public admin endpoint.
// A report's drawing can contain sensitive data. Never paste this output into
// logs or tickets. Review while the room is available, before its normal expiry.
const [command = 'list', id] = process.argv.slice(2);
if (!['list', 'mail-status', 'review', 'resolve', 'remove-room'].includes(command) || (!['list', 'mail-status'].includes(command) && !/^[a-f0-9-]{36}$/.test(id ?? ''))) {
  throw Error('Usage: node moderate.mjs list | mail-status | review <report-id> | resolve <report-id> | remove-room <report-id>');
}
const store = new RoomStore({ databasePath: resolve(process.env.DATABASE_PATH ?? './data/rooms.sqlite') });
try {
  await store.ready;
  const result = await store.call(command === 'list' ? 'listReports' : command === 'mail-status' ? 'reportMailStatus' : command === 'review' ? 'reviewReport' : 'resolveReport',
    { id, removeRoom: command === 'remove-room' });
  process.stdout.write(JSON.stringify(result, null, 2) + '\n');
} finally { await store.close(); }
