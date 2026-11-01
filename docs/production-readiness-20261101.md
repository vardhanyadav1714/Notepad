# Drawly release preparation — 1 November 2026

## Decision and scope

The new candidate is **2.4.4 / version code34**, package `in.innovaticshub.notepad`, origin `https://drawly.in`, target API36 and minimum API24. This follows the [29 October engineering and deployment record](production-readiness-20261029.md). Engineering checks do not guarantee Play approval or ongoing moderation. The old `drawly.smartattendance.xyz` service and unrelated Coolify applications must remain unchanged.

## Changes

The room-only block control did not stop the same installation appearing in a later invitation. New managed rooms now enforce participant identity and blocking before sending a snapshot. Each installation keeps a private random 256-bit secret, excluded from Android backup/transfer. The server hashes that secret into a public anonymous participant reference, binding joins and new/changed ink attribution to it. Changing a claimed user ID cannot impersonate another participant. Unchanged strokes keep their existing attribution during canvas replacement.

An artist's Safety menu can report that participant or block and leave without a PIN. Blocking persists both the participant and room on the device. The connection sends its block list; the server refuses conflicting participants in either join order, including concurrent handshakes, and refuses a blocker access to stored ink attributed to a blocked installation. Raw secrets and block lists stay out of the server database. Participant references in ink follow room retention; references attached to reports follow the30-day report lifecycle. No names, accounts, advertising IDs or profile photos were added.

This identifies an installation, not a real person across reinstalls or other devices. Block lists are capped at128 without silently removing earlier blocks. Reinstallation changes identity. New Android builds require safety-enabled rooms and refuse older invitations with an explanation to create a new room. Older clients retain their prior room protocol; the old Cloudflare Worker was not deployed. Keep invites private and supervise children.

The privacy policy distinguishes new controls from production version18 and discloses persistent anonymous participant references, provider/SDK processing, reports, email and deletion/backups. A deletion-request section explains what to send and explicitly excludes PINs and private secrets. The dedicated support inbox remains `support@drawly.in`; the owner confirmed daily human review. Email delivery is an alert mechanism, not moderation by itself.

## Validation

| Check | Evidence and limits |
| --- | --- |
| Backend |23/23 tests passed, including authenticated attribution, blocking both join orders, simultaneous blocked handshakes, restart persistence, stored-ink isolation, reports, mail retry, quota, expiry and backup restore. |
| Legacy protocol | Typecheck and14/14 Cloudflare protocol tests passed after the final change. No old-endpoint deployment. |
| Android units |26 debug and26 release unit tests passed. |
| Android regression | Full API37 emulator run:74/81 passed initially. Five mock-server tests failed resolving `localhost`; fixtures now bind numeric loopback with a matching TLS certificate. Two PixelCopy/redraw checks timed out during concurrent release compilation. An isolated22-test rerun, including all seven failures plus family controls and managed APIs, passed22/22. The remaining74 results are from the initial full run, not a claimed second81-test clean run. |
| Signed build | APK/AAB built successfully. Lint: zero errors,74 warnings. Two additional warnings recommend KTX preference editing; synchronous checked commits intentionally preserve safety-setting durability. Other recommendations include dependency updates, style and unused resources. |
| Signing | APK signature verified. Upload/release certificate SHA256 `b942a3f9a67f8ba6f2b2ad1dfc120832ef5d4ecf5aaf67877d0ebd33ea8ea781`, matching the original published bundle's upload signer. Play-generated APKs use the separate Play app-signing certificate. |
| Alignment | Actual code34 APK passed16KiB ZIP alignment; all six arm64/x86_64 native libraries have ELF LOAD alignment at least16KiB. Not a16KiB-device runtime acceptance check. |
| Bounded local traffic |100 clients, ten authenticated managed rooms,3,000 strokes/30,000 deliveries over30 rounds with transient updates: all delivered, p50≈95.84ms, p95≈229.21ms, maximum≈265.25ms, RSS≈159MiB, queues drained, no protocol errors.25 reconnects received complete snapshots; all ten fixture rooms expired. Loopback only, without the production CPU quota or internet/storage latency; thirty seconds is not a sustained soak or a capacity guarantee. |
| Daily backup | Latest approved B2 archive retrieved on1Nov; SQLite `quick_check=ok`, normalized schema present, zero live room rows. Archive SHA256 `6620a528c5c19cff58e9c39d4718d6aedae20c6939320b331e171a31f61af5b2`. Isolated restored payload files removed. Backup timing/retention and human report review remain ongoing duties. |

## Artifact inspection and Play preparation

Production version18/name1.0 was retrieved from Drawly's signed-in Play Console Downloads page, including the original AAB and signed universal APK. Original bundle:24,419 class definitions; no Firebase, Google Analytics, ML Kit, Facebook, AppsFlyer, Adjust, Amplitude or Mixpanel namespaces. Its collaboration repository transmits shared ink, canvas settings and random member IDs to `wss://drawly.smartattendance.xyz/ws`. This is static inspection, not a full runtime traffic audit; some Compose UI methods exceeded decompiler reconstruction limits.

The original bundle SHA256 is `fcb44a453c9c6fb0251448aec38659f5268dff8880686e3e5f4ee990bab876bf`; Play universal APK SHA256 is `1bd38c153b3b6599822ba492970522123b6dffa77c773491d9abbff2d821d852`. The Play APK signer is `92940fe85e238d9132bb4c7e173e9b8971a117e1e222bbccb79bac095d83d505`, already included in the domain association. Use Play-delivered APKs for upgrade acceptance; a locally signed upload-key APK cannot directly replace a Play-signed installation.

Closed Alpha still offers version2/name2.0 to selected testers; its original bundle was also downloaded. Its12,158 class definitions have no Firebase/Analytics/ML Kit or OkHttp namespaces, and its APK has no Internet permission. AAB SHA256 `bf17a92d93e2b18c16e403708bdccc2893960cd3e8fb248afb0f388b379da53f`. The Alpha29 release is not yet sent for review; it was not discarded or submitted. Version30 is on internal testing, which Google's Data safety guidance treats separately from distributed closed/open/production artifacts.

A new Data safety draft is prepared against production18, closed2 and candidate34: collection Yes; encrypted in transit; no account creation/external login; deletion-request link `https://drawly.in/privacy#deletion`. Four optional, non-ephemeral categories are selected:

| Category | Purpose in prepared draft |
| --- | --- |
| Other user-generated content | App functionality; safety/compliance for reports. |
| App interactions | App functionality for presence; analytics for disclosed SDK events. |
| Diagnostics | SDK analytics/performance diagnostics. |
| Device or other IDs | Participant blocking/functionality and safety; SDK analytics. |

The draft uses Google's exemptions for provider processing and user-initiated/prominently disclosed transfers when classifying sharing. That does not mean no information reaches Google, infrastructure providers or invited participants: these transfers remain collected/disclosed and are explained in the app and policy. Review the exact draft before submitting; do not copy the old blanket no-collection answer. Local-only audio, photos, notes and PDFs are not declared as uploaded by collaboration.

At writing the draft is not published. Privacy URL, IARC interaction answers and reviewer access instructions still need final reconciliation with the same candidate. Target ages remain all six groups, including children; no audience narrowing or invented age analytics was introduced. API36 is verified in the artifact; building it alone does not clear the existing Console warning.

## Artifacts and rollout tracking

`app/build/outputs/drawly-in/Drawly-2.4.4-drawly-in-release.apk`: SHA256 `eb047012e455766fe4d2d166c136d601b072db0b8d67020dfb9408d2cf71da50`.

`app/build/outputs/drawly-in/Drawly-2.4.4-drawly-in-release.aab`: SHA256 `4c4b116872d54a638becb38f4811f51cfc7b4d884e7945606f584b71be08a6e1`.

Implementation deployment and final public smoke are pending at writing. Local ignored evidence is in `.ui-review/production-readiness-20261029/`; no credentials or private drawings belong in this document. A current14-resource/proxy baseline was captured before rollout to distinguish this change from earlier unrelated app updates.

## Remaining acceptance

Review the final Play declarations and IARC rating with the actual release before submission. Test a Play-delivered upgrade from18, physical phone/tablet and minimum-supported-API behavior, accessibility and process recovery. Run a deployment-sized sustained soak before promising higher concurrency. Keep monitoring health, disk, backups, notification failures and the human moderation backlog. The device PIN supplies an adult control; it does not prove legally sufficient parental consent in every jurisdiction. These engineering checks cannot certify every legal requirement.

Sources checked: [Google Data safety](https://support.google.com/googleplay/android-developer/answer/10787469), [Families](https://support.google.com/googleplay/android-developer/answer/9893335), [UGC](https://support.google.com/googleplay/android-developer/answer/9876937), [ML Kit privacy](https://developers.google.com/ml-kit/terms), [SDK disclosures](https://developers.google.com/ml-kit/android-data-disclosure).
