/** Fixed operator recipient; no child addresses, ink, invites or free text. */
export function createReportMailer({ store, apiKey = '', from = '', to = '', fetcher = fetch,
  intervalMs = 60_000, clock = Date.now, dailyLimit = 20, autoStart = true } = {}) {
  const address = value => /^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(value);
  const enabled = Boolean(apiKey && address(from) && address(to));
  let busy = false, stopped = false, timer;
  async function tick() {
    if (!enabled || busy || stopped) return;
    busy = true;
    try {
      const batch = await store.call('claimReportMail', { now: clock(), dailyLimit });
      if (!batch) return;
      let status = 'pending';
      try {
        const response = await fetcher('https://api.resend.com/emails', {
          method: 'POST', signal: AbortSignal.timeout(10_000),
          headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json', 'Idempotency-Key': `drawly-report-${batch.id}` },
          body: JSON.stringify({ from, to: [to], subject: 'Drawly safety reports need review',
            text: 'Review these reports using the private Drawly moderation tools. No drawings or invite links are included.\n\n' +
              batch.reports.map(report => `${report.id}: ${report.reason}`).join('\n') +
              '\n\nCheck the full open-report queue regularly, including when mail is unavailable or rate-limited. This address does not provide emergency response.' })
        });
        // Drain bounded provider response without recording its body or IDs.
        await response.body?.cancel();
        if (response.ok) status = 'sent';
        else if (response.status !== 429 && response.status !== 408 && response.status < 500) status = 'failed';
      } catch { /* Network failure is retried with the same key and body. */ }
      if (status === 'pending' && batch.attempt >= 8) status = 'failed';
      await store.call('finishReportMail', { id: batch.id, status,
        nextAt: clock() + Math.min(3_600_000, 60_000 * 2 ** Math.min(batch.attempt - 1, 6)) });
    } catch { /* Reports remain durable; never log private report data or keys. */ }
    finally { busy = false; }
  }
  if (enabled && autoStart) { timer = setInterval(tick, intervalMs); timer.unref(); }
  return { enabled, tick, async close() { stopped = true; clearInterval(timer); while (busy) await new Promise(resolve => setTimeout(resolve, 20)); } };
}
