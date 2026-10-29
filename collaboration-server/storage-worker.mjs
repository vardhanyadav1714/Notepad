import { parentPort, workerData } from 'node:worker_threads';
import { DatabaseSync } from 'node:sqlite';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';
import { createHash, randomBytes, randomUUID, timingSafeEqual } from 'node:crypto';

const TTL = workerData.idleTtlMs ?? 86_400_000;
const MAX_BYTES = 1_500_000;
const MAX_STROKES = 1200;
const budget = workerData.maxStorageBytes ?? 128 * 1024 * 1024;
const maxStoredRooms = workerData.maxStoredRooms ?? 10_000;
if (workerData.databasePath !== ':memory:') mkdirSync(dirname(workerData.databasePath), { recursive: true });
const db = new DatabaseSync(workerData.databasePath);
// Incremental vacuum applies to new databases; existing databases retain their mode.
db.exec(`PRAGMA auto_vacuum=INCREMENTAL; PRAGMA journal_mode=WAL; PRAGMA synchronous=FULL;
  PRAGMA foreign_keys=ON; PRAGMA busy_timeout=5000;
  CREATE TABLE IF NOT EXISTS rooms(id TEXT PRIMARY KEY,payload TEXT NOT NULL,expires_at INTEGER NOT NULL);
  CREATE INDEX IF NOT EXISTS rooms_expiry ON rooms(expires_at);
  CREATE TABLE IF NOT EXISTS room_values(room_id TEXT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    key TEXT NOT NULL,value TEXT NOT NULL,PRIMARY KEY(room_id,key));
  CREATE TABLE IF NOT EXISTS room_ink(room_id TEXT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    id TEXT NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(room_id,id));
  CREATE TABLE IF NOT EXISTS room_documents(room_id TEXT PRIMARY KEY REFERENCES rooms(id) ON DELETE CASCADE,
    count INTEGER NOT NULL DEFAULT 0,bytes INTEGER NOT NULL DEFAULT 2);
  CREATE TABLE IF NOT EXISTS room_owners(room_id TEXT PRIMARY KEY REFERENCES rooms(id) ON DELETE CASCADE,token_hash TEXT NOT NULL);
  CREATE TABLE IF NOT EXISTS reports(id TEXT PRIMARY KEY,room_id TEXT NOT NULL,reason TEXT NOT NULL,created_at INTEGER NOT NULL,status TEXT NOT NULL DEFAULT 'open');
  CREATE INDEX IF NOT EXISTS reports_created ON reports(created_at);
  CREATE INDEX IF NOT EXISTS reports_room ON reports(room_id,created_at);
  CREATE TABLE IF NOT EXISTS report_mail_batches(id TEXT PRIMARY KEY,payload TEXT NOT NULL,created_at INTEGER NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,next_at INTEGER NOT NULL,status TEXT NOT NULL DEFAULT 'pending');
  CREATE TABLE IF NOT EXISTS report_mail(report_id TEXT PRIMARY KEY REFERENCES reports(id) ON DELETE CASCADE,
    batch_id TEXT REFERENCES report_mail_batches(id));
  CREATE TABLE IF NOT EXISTS report_mail_attempts(at INTEGER NOT NULL);
  CREATE INDEX IF NOT EXISTS report_mail_due ON report_mail_batches(status,next_at);
  CREATE INDEX IF NOT EXISTS report_mail_attempt_time ON report_mail_attempts(at);
  INSERT OR IGNORE INTO report_mail(report_id) SELECT id FROM reports WHERE status='open';
  CREATE TABLE IF NOT EXISTS storage_usage(id INTEGER PRIMARY KEY CHECK(id=1),bytes INTEGER NOT NULL);
  INSERT OR IGNORE INTO storage_usage SELECT 1,COALESCE(SUM(bytes),0) FROM room_documents;
  CREATE TRIGGER IF NOT EXISTS document_insert AFTER INSERT ON room_documents BEGIN
    UPDATE storage_usage SET bytes=bytes+NEW.bytes WHERE id=1; END;
  CREATE TRIGGER IF NOT EXISTS document_update AFTER UPDATE OF bytes ON room_documents BEGIN
    UPDATE storage_usage SET bytes=bytes+NEW.bytes-OLD.bytes WHERE id=1; END;
  CREATE TRIGGER IF NOT EXISTS document_delete AFTER DELETE ON room_documents BEGIN
    UPDATE storage_usage SET bytes=bytes-OLD.bytes WHERE id=1; END;`);
const sql = statement => db.prepare(statement);
function transaction(operation) {
  db.exec('BEGIN IMMEDIATE');
  try { const value = operation(); db.exec('COMMIT'); return value; }
  catch (error) { db.exec('ROLLBACK'); throw error; }
}
function limit(message = 'Room storage limit reached') { throw Object.assign(new Error(message), { code: 'LIMIT' }); }
function checkBudget(delta) {
  const used = sql('SELECT bytes FROM storage_usage WHERE id=1').get().bytes;
  if (used + delta > budget) limit('Service storage limit reached');
}
function ensureRoom(id, create = true) {
  let room = sql('SELECT payload,expires_at FROM rooms WHERE id=?').get(id);
  if (room && room.expires_at <= Date.now()) {
    sql('DELETE FROM rooms WHERE id=?').run(id);
    room = undefined;
  }
  // Managed invites cannot recreate an ended or expired session.
  if (!room && (!create || id.startsWith('r-'))) throw Object.assign(new Error('Room ended or expired'), { code: 'GONE' });
  if (!room) {
    if (sql('SELECT COUNT(*) AS count FROM rooms').get().count >= maxStoredRooms) limit();
    checkBudget(2);
    sql("INSERT INTO rooms VALUES(?,'{}',?)").run(id, Date.now() + TTL);
    room = { payload: '{}' };
  }
  sql('INSERT OR IGNORE INTO room_documents(room_id) VALUES(?)').run(id);
  if (room.payload !== '{}') {
    const legacy = JSON.parse(room.payload);
    const strokes = legacy.strokes ?? (legacy.strokeIndex ?? []).map(key => legacy[`stroke:${key}`]).filter(Boolean);
    replaceInk(id, strokes);
    for (const [key, value] of Object.entries(legacy)) {
      if (key !== 'strokes' && key !== 'strokeIndex' && !key.startsWith('stroke:')) putValue(id, key, value);
    }
    sql("UPDATE rooms SET payload='{}' WHERE id=?").run(id);
  }
}
function putValue(id, key, value) {
  sql('INSERT INTO room_values VALUES(?,?,?) ON CONFLICT(room_id,key) DO UPDATE SET value=excluded.value').run(id, key, JSON.stringify(value));
}
function refresh(id, at = Date.now()) {
  if (!sql('SELECT 1 FROM rooms WHERE id=?').get(id)) return false;
  putValue(id, 'lastActiveAt', at);
  sql('UPDATE rooms SET expires_at=? WHERE id=?').run(at + TTL, id);
  return true;
}
function replaceInk(id, strokes) {
  const unique = [...new Map(strokes.map(stroke => [stroke.id, stroke])).values()];
  const encoded = unique.map(stroke => JSON.stringify(stroke));
  const bytes = 2 + encoded.reduce((sum, value) => sum + Buffer.byteLength(value), 0) + Math.max(0, unique.length - 1);
  if (unique.length > MAX_STROKES || bytes > MAX_BYTES) limit();
  const previous = sql('SELECT bytes FROM room_documents WHERE room_id=?').get(id)?.bytes ?? 2;
  checkBudget(bytes - previous);
  sql('DELETE FROM room_ink WHERE room_id=?').run(id);
  const insert = sql('INSERT INTO room_ink VALUES(?,?,?)');
  unique.forEach((stroke, i) => insert.run(id, stroke.id, encoded[i]));
  sql('UPDATE room_documents SET count=?,bytes=? WHERE room_id=?').run(unique.length, bytes, id);
}
const hash = token => createHash('sha256').update(token).digest('hex');
const operations = {
  ready() { sql('SELECT COUNT(*) FROM rooms').get(); return true; },
  load({ id }) { return transaction(() => {
    ensureRoom(id);
    const values = Object.fromEntries(sql('SELECT key,value FROM room_values WHERE room_id=?').all(id).map(row => [row.key, JSON.parse(row.value)]));
    values.strokes = sql('SELECT payload FROM room_ink WHERE room_id=? ORDER BY rowid').all(id).map(row => JSON.parse(row.payload));
    return values;
  }); },
  append({ id, stroke }) { return transaction(() => {
    ensureRoom(id);
    if (sql('SELECT 1 FROM room_ink WHERE room_id=? AND id=?').get(id, stroke.id)) return false;
    const document = sql('SELECT count,bytes FROM room_documents WHERE room_id=?').get(id);
    const payload = JSON.stringify(stroke);
    const delta = Buffer.byteLength(payload) + (document.count ? 1 : 0);
    if (document.count >= MAX_STROKES || document.bytes + delta > MAX_BYTES) limit();
    checkBudget(delta);
    sql('INSERT INTO room_ink VALUES(?,?,?)').run(id, stroke.id, payload);
    sql('UPDATE room_documents SET count=count+1,bytes=bytes+? WHERE room_id=?').run(delta, id);
    refresh(id);
    return true;
  }); },
  put({ id, key, value }) { return transaction(() => {
    ensureRoom(id);
    if (key === 'strokes') replaceInk(id, value); else putValue(id, key, value);
    refresh(id);
    return true;
  }); },
  alarm({ id, at }) { sql('UPDATE rooms SET expires_at=? WHERE id=?').run(at, id); return true; },
  remove({ id }) { sql('DELETE FROM rooms WHERE id=?').run(id); return true; },
  create() { return transaction(() => {
    if (sql('SELECT COUNT(*) AS count FROM rooms').get().count >= maxStoredRooms) limit();
    checkBudget(2);
    const id = 'r-' + randomBytes(24).toString('hex');
    const ownerToken = randomBytes(32).toString('hex');
    sql("INSERT INTO rooms VALUES(?,'{}',?)").run(id, Date.now() + TTL);
    sql('INSERT INTO room_documents(room_id) VALUES(?)').run(id);
    sql('INSERT INTO room_owners VALUES(?,?)').run(id, hash(ownerToken));
    return { roomId: id, ownerToken };
  }); },
  end({ id, token }) { return transaction(() => {
    const owner = sql('SELECT token_hash FROM room_owners WHERE room_id=?').get(id);
    if (!owner || !timingSafeEqual(Buffer.from(owner.token_hash, 'hex'), Buffer.from(hash(token), 'hex'))) {
      throw Object.assign(new Error('Owner authorization required'), { code: 'FORBIDDEN' });
    }
    sql('DELETE FROM rooms WHERE id=?').run(id);
    return true;
  }); },
  report({ id, reason }) { return transaction(() => {
    ensureRoom(id, false);
    if (!['unsafe_content', 'bullying', 'personal_information', 'other'].includes(reason)) throw new Error('Choose a report reason');
    if (sql('SELECT COUNT(*) AS count FROM reports').get().count >= 10_000) limit('Report queue is full; ask an adult to contact support');
    if (sql('SELECT COUNT(*) AS count FROM reports WHERE room_id=? AND created_at>?').get(id, Date.now() - 86_400_000).count >= 20) limit('Report limit reached for this room');
    const reportId = randomUUID();
    sql('INSERT INTO reports(id,room_id,reason,created_at) VALUES(?,?,?,?)').run(reportId, id, reason, Date.now());
    sql('INSERT INTO report_mail(report_id) VALUES(?)').run(reportId);
    return { reportId };
  }); },
  claimReportMail({ now = Date.now(), dailyLimit = 20 }) { return transaction(() => {
    // A persisted digest keeps the provider idempotency key AND body stable
    // across timeouts and restarts. Never retry past its 24-hour key lifetime.
    sql("UPDATE report_mail_batches SET status='failed' WHERE status='pending' AND created_at<?").run(now - 23 * 3_600_000);
    sql('DELETE FROM report_mail_attempts WHERE at<?').run(now - 86_400_000);
    if (sql('SELECT COUNT(*) AS count FROM report_mail_attempts').get().count >= Math.min(20, Math.max(1, dailyLimit))) return null;
    let batch = sql("SELECT * FROM report_mail_batches WHERE status='pending' AND next_at<=? ORDER BY created_at LIMIT 1").get(now);
    if (!batch) {
      const reports = sql("SELECT r.id,r.reason FROM reports r JOIN report_mail m ON m.report_id=r.id WHERE r.status='open' AND m.batch_id IS NULL ORDER BY r.created_at LIMIT 100").all();
      if (!reports.length) return null;
      const id = randomUUID(), payload = JSON.stringify(reports);
      sql('INSERT INTO report_mail_batches(id,payload,created_at,next_at) VALUES(?,?,?,?)').run(id, payload, now, now);
      const assign = sql('UPDATE report_mail SET batch_id=? WHERE report_id=?');
      for (const report of reports) assign.run(id, report.id);
      batch = { id, payload, created_at: now, attempts: 0 };
    }
    sql('INSERT INTO report_mail_attempts VALUES(?)').run(now);
    sql('UPDATE report_mail_batches SET attempts=attempts+1,next_at=? WHERE id=?').run(now + 60_000, batch.id);
    return { id: batch.id, reports: JSON.parse(batch.payload), attempt: batch.attempts + 1 };
  }); },
  finishReportMail({ id, status, nextAt = Date.now() }) {
    if (!['pending', 'sent', 'failed'].includes(status)) throw new Error('Invalid notification state');
    sql("UPDATE report_mail_batches SET status=?,next_at=? WHERE id=? AND status='pending'").run(status, nextAt, id);
    return true;
  },
  reportMailStatus() {
    return { batches: sql('SELECT status,COUNT(*) AS count FROM report_mail_batches GROUP BY status').all(),
      unassigned: sql("SELECT COUNT(*) AS count FROM report_mail m JOIN reports r ON r.id=m.report_id WHERE m.batch_id IS NULL AND r.status='open'").get().count };
  },
  sweep({ active = [] }) { return transaction(() => {
    const revoked = active.filter(id => !refresh(id));
    const expired = sql('SELECT id FROM rooms WHERE expires_at<=? LIMIT 1000').all(Date.now()).map(row => row.id);
    for (const id of expired) sql('DELETE FROM rooms WHERE id=?').run(id);
    // Reports contain category/session reference only, no drawing snapshot or free text.
    sql('DELETE FROM reports WHERE created_at<?').run(Date.now() - 30 * 86_400_000);
    sql('DELETE FROM report_mail WHERE batch_id IN (SELECT id FROM report_mail_batches WHERE created_at<?)').run(Date.now() - 30 * 86_400_000);
    sql('DELETE FROM report_mail_batches WHERE created_at<?').run(Date.now() - 30 * 86_400_000);
    sql('DELETE FROM report_mail_attempts WHERE at<?').run(Date.now() - 86_400_000);
    return { purged: expired.length, expired, revoked, rooms: sql('SELECT COUNT(*) AS count FROM rooms').get().count,
      inkBytes: sql('SELECT bytes FROM storage_usage WHERE id=1').get().bytes };
  }); },
  compact() { db.exec('PRAGMA wal_checkpoint(PASSIVE); PRAGMA incremental_vacuum(256)'); return true; },
  listReports() { return sql("SELECT id,room_id,reason,created_at FROM reports WHERE status='open' ORDER BY created_at LIMIT 100").all(); },
  reviewReport({ id }) {
    const report = sql('SELECT * FROM reports WHERE id=?').get(id);
    if (!report) throw new Error('Report not found');
    return { ...report, strokes: sql('SELECT payload FROM room_ink WHERE room_id=? ORDER BY rowid').all(report.room_id).map(row => JSON.parse(row.payload)) };
  },
  resolveReport({ id, removeRoom = false }) { return transaction(() => {
    const report = sql('SELECT room_id FROM reports WHERE id=?').get(id);
    if (!report) throw new Error('Report not found');
    if (removeRoom) sql('DELETE FROM rooms WHERE id=?').run(report.room_id);
    sql("UPDATE reports SET status='resolved' WHERE id=?").run(id);
    return true;
  }); },
  close() { db.exec('PRAGMA wal_checkpoint(TRUNCATE)'); db.close(); return true; }
};
parentPort.on('message', ({ id, operation, args }) => {
  try {
    if (!Object.hasOwn(operations, operation)) throw new Error('Unknown storage operation');
    const result = operations[operation](args);
    parentPort.postMessage({ id, result });
  } catch (error) {
    // Do not return SQL, drawings, local paths or credentials to socket clients.
    parentPort.postMessage({ id, error: { message: error.code === 'LIMIT' ? error.message : 'Room storage unavailable', code: error.code ?? 'STORAGE' } });
  }
});
