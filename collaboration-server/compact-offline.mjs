import { DatabaseSync } from 'node:sqlite';
import { stat } from 'node:fs/promises';
import { resolve } from 'node:path';

if (!process.argv.includes('--server-stopped')) throw Error('Stop the Drawly service and back up the database first. Then pass --server-stopped.');
const path = resolve(process.env.DATABASE_PATH ?? './data/rooms.sqlite');
await stat(path);
const db = new DatabaseSync(path);
try {
  db.exec('PRAGMA wal_checkpoint(TRUNCATE); PRAGMA auto_vacuum=INCREMENTAL; VACUUM;');
  if (Object.values(db.prepare('PRAGMA quick_check').get())[0] !== 'ok') throw Error('Database check failed');
  console.log(JSON.stringify({ verified: true, bytes: (await stat(path)).size }));
} finally { db.close(); }
