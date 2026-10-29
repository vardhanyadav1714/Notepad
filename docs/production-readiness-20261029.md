# Drawly readiness — 29 October 2026

## Release decision

The engineering changes are tested locally and the signed 2.4.3/code33 APK and AAB have been built. **Do not describe the app as guaranteed compliant or upload it to Play solely on these results.** Play Console declarations, the final deployed behavior, the reviewer workflow and applicable child-data obligations must match the release. No Play upload was performed.

This record supersedes the implementation findings in the [24 October assessment](production-readiness-20261024.md), which remains a historical baseline. The existing Play endpoint `drawly.smartattendance.xyz` is preserved. Ordinary Android builds retain that origin; the explicit new-domain build helper selects `drawly.in`.

## Implemented

- SQLite work runs in a dedicated worker. Strokes are normalized and appended incrementally; heartbeat metadata no longer rewrites full canvases. A transactional storage counter replaces a per-stroke full-table quota scan. Legacy persisted payloads migrate transactionally on access.
- Bounded admission, per-socket/room/global inbound queues, cache and outgoing buffers isolate overload. Health probes perform a bounded live worker round trip. A fatal worker failure exits the standalone service with a fixed non-private diagnostic so Docker's restart policy can recover it; planned shutdown does not trigger that failure path. One writable replica remains required. Limits are safeguards, not measured user-capacity guarantees.
- Sharing defaults off. Device authentication establishes a private family PIN; every new session requires the PIN and acceptance of the disclosure/rules. Deep links and restored notes do not autojoin. Backgrounding/leaving pauses sharing and requires reapproval. Reporting and block-and-leave do not require the PIN.
- Managed invites use random identifiers. A separate owner capability permits ending/deleting live room ink and revoking the invite. Leaving alone preserves ink for normal 24-hour inactivity expiry. Local copies and older backups are separate.
- Reports acknowledge only after durable storage. They hold a selected reason and room reference, not reporter contact data, free text or a copied drawing. The private operator CLI can review, resolve and remove a room. Reports expire after 30 days.
- An optional durable Resend outbox groups up to 100 report IDs/categories per digest to a fixed operator inbox, without ink or invite links. At most 20 delivery attempts per rolling day; stable idempotency across timeouts/restarts, exponential backoff, eight-attempt/23-hour retry ceiling, and visible failed-batch counts. Email failure never deletes a report.
- ML Kit automatic initialization is removed. Optional recognition starts after an adult approves the SDK disclosure; ink recognition runs locally, while model downloads and SDK diagnostics are separately disclosed. A device PIN is not legal age verification or proof of legally sufficient parental consent.
- Drawly-specific proxy labels disable route access logs, metrics and tracing. Application diagnostics avoid private payloads and rotate by size (three 10 MiB files). The shared proxy is not restarted or reconfigured. Recheck generated router IDs after Coolify upgrades or resource moves.
- The collaboration Docker build context includes only its source/build inputs. Runtime mail credentials are excluded from Android, Git and the image build inputs.

## Verification evidence

| Check | Result and boundary |
| --- | --- |
| Android instrumented checks | 81/81 applicable tests passed on owned API37 emulator; includes family controls, paused reconnect, managed deletion and drawing regressions. Not a physical-device matrix. |
| Signed release | `tools/build-drawly-in.ps1`: 26 release unit tests passed; release APK/AAB built; lint zero errors, 72 warnings. |
| APK signature | Verified; certificate SHA256 `b942a3f9a67f8ba6f2b2ad1dfc120832ef5d4ecf5aaf67877d0ebd33ea8ea781`. Check against the Play app-signing certificate separately. |
| 16 KiB alignment | APK ZIP alignment check passed; all six arm64/x86_64 native libraries have LOAD alignment at least 16 KiB. This is not a 16 KiB-device runtime acceptance test. |
| Node integration | 20/20 passed: live pairs, restart durability, isolation, admission/overload, legacy migration, storage quota, deletion, reports, mail retry/idempotency/caps, live-WAL restore, fatal-worker recovery callback and graceful shutdown. |
| Cloudflare compatibility | Protocol typecheck and 14 tests passed on 26 October. Old Worker not deployed. |
| Local synthetic load | 100 clients/ten rooms, 800 committed strokes/8,000 deliveries, concurrent previews/presence: all delivered, no protocol errors; median131.08ms, p95276.67ms, maximum323.80ms; measured RSS120.32MiB; queues drained; all ten synthetic rooms purged. No production CPU quota/internet latency/soak guarantee. Reproduce with `npm run benchmark`. |
| Drawly B2 backup | Owner explicitly approved Drawly-only `/data`, seven daily copies/seven days, brief resource-only pause. A backup was uploaded, downloaded into an isolated fixture and restored with SQLite `quick_check=ok`; restored payload files removed. The checked snapshot held zero rooms. Future executions/retention still require monitoring. |
| Mail DNS/inbox | All four Resend records verified in public DNS; Resend verified `drawly.in` for sending and receiving. `support@drawly.in` created; setup email accepted and visible in that inbox. Existing website A record, other DNS records and `merigf.com` mail setup preserved. |
| Reviewer | Owner explicitly confirmed daily inbox/report-queue review on 29 October. Creating an inbox is not itself moderation; review and action remain ongoing work. |

Ignored evidence and setup scripts are under `.ui-review/production-readiness-20261029/`. They are local operational evidence, not shipped application content. Credentials are not included in this document.

## Build artifacts

Local directory: `app/build/outputs/drawly-in/`.

- `Drawly-2.4.3-drawly-in-release.apk`, SHA256 `bd347e9c74810cab2ad35c65631f2bf380559be2190b3234414e5aa60da561de`.
- `Drawly-2.4.3-drawly-in-release.aab`, SHA256 `ad01b40f8fd7e002fa670d9d19058acc954bf4303bf7523346febb914ff36b43`.
- Named JSON metadata records the version, package, origin and hashes. Rebuild updates those hashes; check metadata for the actual artifact submitted.

## Deployment tracking

Only Coolify resource `drawly-collaboration` was deployed. The implementation commit `db103200636d0f77c9f434fc7203ce6b8eecbf9a` and the route-label correction `e739e33ef306762d6c5df9e1aed724e00828145f` are pushed to the existing `drawly-collaboration` GitHub branch. Coolify deployment `q1y3czm3dj8rknzzmrphaiua` finished on the corrected commit and the resource is `running:healthy`.

Public trusted HTTPS/WSS checks passed health, managed-sharing configuration, policy/rules publication, paired membership, drawing activity, transient preview, committed ink recovered by rejoin, report acceptance, rejection of an invalid owner capability, owner deletion and a 410 response when reopening the deleted invite. Only a fresh synthetic managed room was edited, and it was deleted afterward. The old `drawly.smartattendance.xyz` endpoint passed a real join/ping check without receiving test ink.

The live server's report digest arrived in `support@drawly.in` with the exact synthetic report ID and category, and no invite link. A one-off disabled Coolify task successfully resolved only that report using `moderate.mjs`; the temporary task was deleted. The Resend key's dashboard confirms sending-only permission restricted to `drawly.in`. Enforced TLS is configured for that domain; no open/click tracking was enabled. The owner confirmed daily review. Access the [Drawly support inbox](https://resend.com/inboxes/support%40drawly.in) in that Resend account.

All six generated Drawly HTTP/HTTPS observability labels match access logs/metrics/tracing disabled. Coolify escapes variables in custom label names, so the correction uses this resource's verified literal IDs; replace all six before reusing the Compose file for another resource. The shared proxy configuration matches the baseline exactly. All 13 other resource health states match the baseline (including one already-exited legacy SeaweedFS resource); no unrelated resource was deployed or restarted.

## Before Play publication

1. The deployed smoke and report-alert checks above passed. Continue checking health, moderation and backups during rollout; these point-in-time results do not establish continuing uptime or moderation.
2. The signed-in Drawly Console was checked on 29 October. Its current Data safety form still selects **No** for required data collection/sharing and **Yes** for the Families commitment; its privacy URL still points to the old FreePrivacyPolicy page. No Console answers were changed or submitted during these read-only checks. These declarations must be reconciled with the new release's shared ink/reports and ML Kit diagnostics, using Google's definitions and exceptions and the behavior of all distributed versions. Free download/no ads does not establish no collection. Do not invent Console answers or demographic data.
   All six age groups are still checked in the actual Console, including ages five and under. They were read without changing the selections; do not silently switch to adults-only to avoid the child-safety requirements.
   The IARC questionnaire is still dated 3 May 2025. Its social/UGC answers need review against the new collaborative drawing feature. The existing IARC certificate is not evidence that this changed release has already been rated.
   Drawly also has a live warning: updates must target Android 16/API36 by **1 November 2026**. The actual signed 2.4.3/code33 APK manifest was checked with `aapt2` and reports `targetSdkVersion=36`, `minSdkVersion=24`. Building it locally does not clear the warning; the appropriate release still needs to be submitted and accepted.
3. Review applicable child-data/SDK and consent obligations for the intended countries and all selected ages. Family PIN controls and a policy page alone do not establish legal compliance. The app currently blocks a room on the device; it has no authenticated, persistent cross-room user identity or global user-blocking system. Assess whether that limitation satisfies the final UGC experience; do not advertise it as account-wide user blocking.
4. Review remaining lint warnings and perform physical phone/tablet, accessibility, rotation/large-canvas, min-supported-API, process-death/recovery and release-upgrade acceptance checks. The 81 emulator checks cover useful regressions but are not every device or feature combination.
5. Run an isolated production-sized soak and reconnect test before promising higher simultaneous traffic. Monitor disk space, service health, backup success/age, failed notification batches and the human report backlog. One server is not high availability; backup recovery is not instant failover.

Policy sources checked: [Families](https://support.google.com/googleplay/android-developer/answer/9893335), [UGC](https://support.google.com/googleplay/android-developer/answer/9876937), [ML Kit terms/privacy](https://developers.google.com/ml-kit/terms), [ML Kit disclosures](https://developers.google.com/ml-kit/android-data-disclosure). Platform requirements and provider behavior can change; reassess against the actual submission.

## Prepared Console reconciliation

These are review inputs, not a claim that declarations were saved or approved.

| Item | Observed current state | Required release preparation |
| --- | --- | --- |
| Privacy policy | Old FreePrivacyPolicy URL | Use the verified `https://drawly.in/privacy` with the new release; keep disclosures accurate for distributed older versions too. |
| Support/safety contact | Dedicated inbox newly configured | Publish `support@drawly.in`; owner reviews the inbox and private report queue daily. |
| Data collection question | No | The new release sends readable shared ink and reports to its server and initializes optional ML Kit after approval. The blanket No answer cannot describe that behavior. |
| Data-type mapping | No types declared | Review App activity/Other user-generated content for shared ink/reports, App interactions for activity/SDK events, Diagnostics for ML Kit performance/errors, and Device or other identifiers for SDK installation identifiers. Determine final categories/purposes using the actual SDK version and all distributed builds. |
| Sharing and optionality | Not established for new flows | Check service-provider and user-initiated-transfer exceptions; do not equate no ads with no collection. Local-only files/audio are not automatically off-device collection. Do not mark SDK collection optional across every version without verifying older SDK initialization behavior. |
| Deletion | New managed-room deletion verified | Creator can end a room; others can request help at the published contact. Live ink, local copies, report records and backup copies have distinct lifecycles. Do not promise immediate deletion from every copy. |
| Target audience | All six groups checked | Keep truthful design-based selections and complete child/social disclosures; no demographic tracking or silent adults-only switch was introduced. |
| IARC | May2025 questionnaire | Reassess content exchange/social/UGC answers and receive the appropriate rating; do not copy the old rating unreviewed. |
| App access/reviewer instructions | Existing declaration predates family controls | Explain that local notes need no account. To test sharing, the reviewer sets a device screen lock, establishes their own six-digit family PIN through Settings, accepts the disclosure and creates/joins a room. Do not publish a universal bypass PIN or weaken the controls for review. |
| API warning | Target API36 by 1Nov2026 | The signed code33 artifact already targets API36; submission/acceptance is still required. |

Google's [Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469) covers SDK collection, optionality, distributed artifacts and relevant transfer exceptions. The SDK provider's disclosure supports the ML Kit entries; the developer remains responsible for the complete final form.
