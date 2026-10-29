import { DatabaseSync, backup } from 'node:sqlite';
import { mkdir, stat, readdir, unlink } from 'node:fs/promises';
import { resolve, join, dirname } from 'node:path';
import { pathToFileURL } from 'node:url';

/** SQLite-aware backup includes committed WAL data; never just copy rooms.sqlite. */
export async function backupRooms(databasePath, backupDirectory, retentionDays) {
  if (!Number.isInteger(retentionDays) || retentionDays < 1 || retentionDays > 365) throw Error('Set backup retention to 1–365 days');
  const source = resolve(databasePath), directory = resolve(backupDirectory);
  await stat(source); // Do not create an empty source by accident.
  await mkdir(directory, { recursive: true, mode: 0o700 });
  const name = `drawly-backup-${new Date().toISOString().replace(/[:.]/g, '-')}.sqlite`;
  const destination = join(directory, name);
  const db = new DatabaseSync(source, { readOnly: true });
  try { await backup(db, destination); } finally { db.close(); }
  const check = new DatabaseSync(destination, { readOnly: true });
  try {
    if (Object.values(check.prepare('PRAGMA quick_check').get())[0] !== 'ok') throw Error('Backup verification failed');
  } finally { check.close(); }
  // Retention only touches this tool's own named files inside the explicit
  // backup directory; it never enumerates or deletes the live volume.
  for (const file of await readdir(directory)) {
    if (!/^drawly-backup-\d{4}-\d{2}-\d{2}T[\d-]+Z\.sqlite$/.test(file)) continue;
    const target = resolve(directory, file);
    if (dirname(target) !== directory || target === source) throw Error('Unsafe backup cleanup path');
    if ((await stat(target)).mtimeMs < Date.now() - retentionDays * 86_400_000) await unlink(target);
  }
  return destination;
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const directory = process.argv[2];
  if (!directory) throw Error('Usage: node backup.mjs <private-backup-directory>');
  const destination = await backupRooms(process.env.DATABASE_PATH ?? './data/rooms.sqlite', directory, Number(process.env.DRAWLY_BACKUP_RETENTION_DAYS));
  console.log(JSON.stringify({ verified: true, backup: destination }));
}
