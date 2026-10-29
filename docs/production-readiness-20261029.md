# Drawly readiness — 29 October 2026

## Release decision

The engineering changes are tested locally and the signed 2.4.3/code33 APK and AAB have been built. **Do not describe the app as guaranteed compliant or upload it to Play solely on these results.** Play Console declarations, the final deployed behavior, the reviewer workflow and applicable child-data obligations must match the release. No Play upload was performed.

This record supersedes the implementation findings in the [24 October assessment](production-readiness-20261024.md), which remains a historical baseline. The existing Play endpoint `drawly.smartattendance.xyz` is preserved. Ordinary Android builds retain that origin; the explicit new-domain build helper selects `drawly.in`.

## Implemented

- SQLite work runs in a dedicated worker. Strokes are normalized and appended incrementally; heartbeat metadata no longer rewrites full canvases. A transactional storage counter replaces a per-stroke full-table quota scan. Legacy persisted payloads migrate transactionally on access.
- Bounded admission, per-socket/room/global inbound queues, cache and outgoing buffers isolate overload. One writable replica remains required. Limits are safeguards, not measured user-capacity guarantees.
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
| Node integration | 18/18 passed: live pairs, restart durability, isolation, admission/overload, legacy migration, storage quota, deletion, reports, mail retry/idempotency/caps and live-WAL restore. |
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

Only Coolify resource `drawly-collaboration` is in scope. Before this change it was healthy on the 23 October implementation. Backup/restore and email setup are verified. The code deployment and final public smoke checks must be recorded below after completion; this source record alone does not claim they are live.

## Before Play publication

1. Verify the new deployment's HTTPS/WSS, configured policy/terms/contact, managed room creation/deletion, a real report reaching the private review queue and its alert reaching the support inbox. Check other applications and the old endpoint remain healthy.
2. Update the actual Play Console privacy URL, Data safety, target audience and IARC social/UGC answers against this release. The earlier listing's “No data collected” is not established by having no ads/analytics: shared ink, reports, support email and SDK diagnostics must be assessed using Google's definitions and exceptions. Do not invent Console answers or demographic data.
3. Review applicable child-data/SDK and consent obligations for the intended countries and all selected ages. Family PIN controls and a policy page alone do not establish legal compliance. The app currently blocks a room on the device; it has no authenticated, persistent cross-room user identity or global user-blocking system. Assess whether that limitation satisfies the final UGC experience; do not advertise it as account-wide user blocking.
4. Review remaining lint warnings and perform physical phone/tablet, accessibility, rotation/large-canvas, min-supported-API, process-death/recovery and release-upgrade acceptance checks. The 81 emulator checks cover useful regressions but are not every device or feature combination.
5. Run an isolated production-sized soak and reconnect test before promising higher simultaneous traffic. Monitor disk space, service health, backup success/age, failed notification batches and the human report backlog. One server is not high availability; backup recovery is not instant failover.

Policy sources checked: [Families](https://support.google.com/googleplay/android-developer/answer/9893335), [UGC](https://support.google.com/googleplay/android-developer/answer/9876937), [ML Kit terms/privacy](https://developers.google.com/ml-kit/terms), [ML Kit disclosures](https://developers.google.com/ml-kit/android-data-disclosure). Platform requirements and provider behavior can change; reassess against the actual submission.
