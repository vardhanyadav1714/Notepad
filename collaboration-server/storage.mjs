import { Worker } from 'node:worker_threads';

/** Only this worker owns SQLite. Socket handling never waits on synchronous disk IO. */
export class RoomStore {
  constructor({ onFailure, ...options }) {
    this.worker = new Worker(new URL('./storage-worker.mjs', import.meta.url), {
      workerData: options, resourceLimits: { maxOldGenerationSizeMb: 96, maxYoungGenerationSizeMb: 16 }
    });
    this.pending = new Map();
    this.sequence = 0;
    this.failed = null;
    this.closing = false;
    this.worker.on('message', ({ id, result, error }) => {
      const request = this.pending.get(id);
      if (!request) return;
      this.pending.delete(id);
      if (error) request.reject(Object.assign(new Error(error.message), { code: error.code }));
      else request.resolve(result);
    });
    const fail = () => {
      if (this.failed) return;
      this.failed = new Error('Room storage unavailable');
      for (const request of this.pending.values()) request.reject(this.failed);
      this.pending.clear();
      if (!this.closing) onFailure?.();
    };
    this.worker.on('error', fail);
    this.worker.on('exit', () => { if (!this.closing || this.pending.size) fail(); });
    this.ready = this.call('ready');
    // Callers also await ready; prevent an unhandled rejection during startup.
    this.ready.catch(() => {});
  }
  call(operation, args = {}) {
    if (this.failed) return Promise.reject(this.failed);
    if (this.pending.size >= 1024) return Promise.reject(new Error('Storage queue is full'));
    return new Promise((resolve, reject) => {
      const id = ++this.sequence;
      this.pending.set(id, { resolve, reject });
      this.worker.postMessage({ id, operation, args });
    });
  }
  async close() {
    this.closing = true;
    try { await this.call('close'); } finally { await this.worker.terminate(); }
  }
}
