import { createCollaborationServer } from '../server.mjs';
import { monitorEventLoopDelay } from 'node:perf_hooks';
import { DatabaseSync } from 'node:sqlite';
const databasePath = process.argv[2];
const app = createCollaborationServer({ databasePath, maintenanceMs: 1000 });
await new Promise(resolve => app.server.listen(0, '127.0.0.1', resolve));
const lag = monitorEventLoopDelay({ resolution: 10 }); lag.enable();
let baseline = process.cpuUsage();
process.send({ type: 'ready', base: `http://127.0.0.1:${app.server.address().port}` });
process.on('message', async message => {
  if (message.type === 'measure') { lag.reset(); baseline = process.cpuUsage(); }
  if (message.type === 'stats') {
    const db = new DatabaseSync(databasePath, { readOnly: true });
    const rows = db.prepare('SELECT COUNT(*) AS count FROM rooms').get().count;
    db.close();
    const cpu = process.cpuUsage(baseline);
    process.send({ type: 'stats', rows, rssMiB: process.memoryUsage().rss / 1024 ** 2,
      cpuMs: (cpu.user + cpu.system) / 1000, eventLoopP95Ms: lag.percentile(95) / 1e6,
      eventLoopMaxMs: lag.max / 1e6, ...app.stats() });
  }
  if (message.type === 'close') { await app.close(); process.exit(0); }
});
