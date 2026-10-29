# Drawly production readiness — 24 October 2026

## Release decision

**Hold the child-inclusive Play release.** The collaboration service is live and functional, but the current app does not yet provide adequate adult control over child sharing, the linked privacy policy contradicts the declared audience, and a bounded local load test showed substantial drawing-delivery delays.

This assessment did not deploy code, change production settings, load-test production, or change the existing Play release or its `drawly.smartattendance.xyz` endpoint. The new build's endpoint is `drawly.in`. Readiness checks do not guarantee acceptance by Google Play.

## Child safety and privacy

The owner previously confirmed that Play Console includes all age groups, including children under 13. That is the audience used for this assessment; the current Console selections were not independently re-exported today.

1. **Adult management is missing.** The collaboration sheet permits room creation and joining directly. `CanvasWorkspace.kt:88` also joins a deep-linked or restored room automatically. There is no enforced adult enable/disable control covering these entry paths. A reminder exists in the sheet, but automatic joins can bypass it. Apply the chosen child-sharing safeguards consistently to creation, joining, deep links, restoration and reconnects.
2. **Invite possession currently grants access.** Anyone with a valid invite can join and exchange ink. Server-generated member labels do not prevent someone from drawing personal information. Before enabling child sharing, define adult approval and management, access revocation and an effective way to stop sharing. Validate applicable reporting/blocking requirements against the final feature design.
3. **The public policy conflicts with the audience.** The linked policy returned HTTP 200 on this review and was last updated 3 May 2025. Its children's section says the service does not address under-13 users. Update the actual public policy to accurately describe the intended audience, shared ink, recipients, retention/deletion, SDK behavior and applicable backup handling. Editing the app's link alone would not fix this.
4. **Reconcile declarations with the shipped build.** Review Play's target audience, Data safety, content rating and SDK disclosures against actual behavior. An earlier owner declaration of no ads or purchases does not establish that no data is transmitted. Do not change audience selections merely to avoid safeguards or introduce age tracking just for this review.

Google's [Families requirements](https://support.google.com/googleplay/android-developer/answer/9893335) address pre-exchange safety reminders, adult management of children's social features and adult action before features permit exchange of personal information. The [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311) governs accurate disclosures. The current [Drawly privacy policy](https://www.freeprivacypolicy.com/live/286e718d-0906-4361-a68b-3292405405f8) needs reconciliation with the declared audience.

## Live service and functional checks

- Coolify reports `drawly-collaboration` as `running:healthy`; a normally certificate-validated request to `https://drawly.in/health` returned HTTP 200 and `{"status":"ok"}`.
- Generated deployment configuration specifies one CPU, 512 MB memory and a persistent SQLite volume. These are configuration observations, not independently measured runtime resource usage or capacity guarantees.
- Five fresh backend integration tests passed: paired-client membership/live ink, transient state handling, durable concurrent ink and restart isolation, capacity rejection, expired data after restart, and malformed/origin/health handling (some checks share a test).
- No production traffic benchmark, backup restoration, failover exercise or physical-device acceptance test was completed in this assessment.

### Current safeguards and limits

| Control | Current behavior | Practical boundary |
| --- | --- | --- |
| Peers | Maximum 20 per room | Not a global concurrency guarantee |
| RAM room map | Maximum 250 entries | Includes recently disconnected rooms; does not cap all persisted rooms |
| Canvas | Up to 1,200 stored strokes; 1.5 MB stored payload limit | Large canvases still require latency/memory testing |
| Message rates | Durable 240/10 seconds; ephemeral 300/10 seconds per connection | Pending inbound work is not explicitly bounded before enqueueing |
| Slow recipients | Drop disposable updates above 128 KB buffered; close above 2 MB | Protects outgoing buffers, not every overload path |
| Connections | Heartbeats and browser-origin checks | Invite possession remains the access mechanism |

## Local concurrency test

The actual Node server ran on loopback against a synthetic SQLite database. The workload used 100 clients across ten rooms, ten clients per room, eight rounds of 64-point committed strokes and approximately six disposable presence/preview updates per client per second. It sent 800 committed strokes, yielding 8,000 expected recipient deliveries. The server process had a 256 MiB JavaScript heap cap; this was not a Docker one-CPU/512-MB replica of production.

| Measurement | Result |
| --- | --- |
| Committed deliveries | 8,000 / 8,000, no protocol errors |
| Median delivery delay | 1.41 seconds |
| 95th percentile delivery delay | **8.45 seconds** |
| Maximum delivery delay | 9.71 seconds |
| Server peak RSS | About 115 MiB |
| Maximum event-loop delay | About 8.65 seconds |

**Delivery correctness passed; smooth collaboration at this workload did not.** These measurements are local and workload-specific. They cannot certify a production user limit. Synchronous SQLite operations and repeated serialization/writes of the entire room payload are plausible contributors identified in source inspection; a CPU/storage profile is needed to quantify them.

Before advertising higher concurrency, bound inbound pending work, reduce redundant full-canvas persistence through a design that preserves durable ordering, profile the bottleneck and repeat tests in an isolated environment matching the deployment. Agree delivery-latency targets and test larger canvases, reconnect bursts and a sustained soak, with CPU, memory, queue, disconnect and disk monitoring. Increasing limits alone is insufficient. Multiple independent instances cannot safely provide consistent room state simply by sharing this SQLite file.

## Retention and space usage

| Data | Current lifecycle |
| --- | --- |
| Live presence and stroke previews | Transient; not saved as durable shared ink |
| Disconnected room in the RAM map | Eligible for eviction after 60 seconds idle; a 30-second sweep gives approximately 60–90 seconds in normal operation |
| Saved shared ink | Normally expires after 24 hours of inactivity; active rooms refresh their lifetime |
| Expired rows | Maintenance checks every 30 seconds, up to 1,000 rows per sweep; backlog or event-loop stalls can delay deletion |
| SQLite disk file | Deleted rows free reusable pages; the OS file does not necessarily shrink |
| Local notes, backups and logs | Separate lifecycles; room expiry does not establish their deletion |

Leaving or disconnecting **does not immediately delete shared ink**. There is no authorized host action that ends a session and deletes its server data immediately. If that is the intended product promise, implement ownership and deletion semantics with an appropriate reconnect grace period rather than deleting on every temporary connection loss.

The local test moved only its synthetic rooms' expiry timestamps into the past and waited for the real maintenance sweep. All ten stored room rows were deleted. The database file remained 4,550,656 bytes, with 1,108 of 1,111 pages free and automatic vacuum disabled. The result proves logical purge in that test, not immediate physical erasure or reclamation across backups. [SQLite documents free-page reuse and compaction](https://www.sqlite.org/lang_vacuum.html).

For storage predictability, add an expiry index, a global storage/admission budget, disk alerts and a deliberate checkpoint/compaction and backup-retention policy. Schedule compaction with disk headroom and latency in mind; running `VACUUM` after every departure would be inappropriate. Verify recovery from a backup as well as its retention/deletion behavior.

## Required release work

1. Resolve child-sharing design, enforce adult controls and show the reminder before exchange on every entry path.
2. Publish an accurate child-inclusive privacy policy and reconcile Console declarations with the final build and backend.
3. Address the measured latency and unbounded inbound work, then validate the intended concurrency in a matched, isolated environment.
4. Make retention visible to users; establish explicit room-ending behavior if required, storage monitoring/budget and a verified backup/restore lifecycle.
5. Run final Android regression and physical-device checks on the resulting release, including rotation/process restoration, offline/reconnect, invite handling, permissions and deletion. Do not infer these pass from server tests.

## Evidence locations

- Server: `collaboration-server/server.mjs`; shared protocol: `cloudflare/src/index.ts`.
- Android entry paths: `app/src/main/java/in/innovaticshub/notepad/feature/CanvasWorkspace.kt` and `app/src/main/java/in/innovaticshub/notepad/ui/canvas/screens/CollaborationSheet.kt`.
- Privacy link/disclosure: `app/src/main/java/in/innovaticshub/notepad/feature/NotebookScreens.kt`.
- Local scripts and raw evidence (ignored, not committed): `.ui-review/production-readiness-20261024/`, including `capacity-results.json` and `policy-results.json`.
- The raw capacity result's `passed: true` checks delivery and logical purge only. It is **not** a latency acceptance result.
