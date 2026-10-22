# Drawly collaboration review — 21 October 2026

**Status: locally tested, suitable for staging; not yet cleared for an all-ages production release.**

This review covers the current Notepad/Drawly project and the locally built APK. No separate APK attachment was received. The live backend, DNS, Play Console declarations and the other application in Coolify have not been changed. The source changes are local and have not been pushed to GitHub.

## Implemented

- Round member avatars in the existing drawing header, plus a scrollable room member list. Labels and colours are generated per connection; no real name or photo is requested.
- Drawing, erasing and selecting indicators, interpolated peer cursors, and bounded live ink previews before a stroke is committed.
- Temporary previews and membership are separate from saved documents, exports, autosave and undo. Cancel, commit, leave, reconnect and a four-second stale timeout clear transient state.
- Peer markers remain at the actual ink coordinate at viewport edges; only labels are clamped into view.
- Optional protocol capabilities preserve the current behavior with an older relay. The Android default still uses `https://drawly.smartattendance.xyz`.
- One shared room implementation for the existing Cloudflare Worker and a new Node 24/SQLite service, with a separate Coolify Compose resource and dedicated volume. No Redis or additional server is required for this single-instance design.
- A validated `-PcollaborationOrigin=https://…` build property changes WebSockets, invitation links and the Android verified-link host together. This is a deployment configuration option, not an automatic room migration.

## Verification

| Check | Result |
| --- | --- |
| Android full applicable instrumented suite | 73 passed on the isolated preview package |
| Final cursor-edge/UI/network/zoom/theme/fullscreen checks | 16 passed after the last cursor fix; includes checking the cursor's actual rendered pixel near the right edge |
| JVM tests | 26 passed per checked debug/release variant |
| APK builds | Final debug and release assembly passed, including release shrinking and local signing |
| Android lint | Debug and release passed with no errors; existing warnings remain |
| Worker TypeScript validation and unit tests | Typecheck passed; 14 tests passed |
| Actual local Worker runtime | Roster, activity, preview cancellation, transient-state exclusion, paired clients, concurrent persistence, reconnect, malformed input and canvas edits passed |
| Standalone service | 5 integration tests passed, covering real WebSockets, SQLite recovery, room isolation, persisted edits, idle expiry, capacity and browser-origin rejection |
| Compose definition | `docker compose config --quiet` passed |
| Docker image / remote Coolify deployment | Not run: local Docker engine was unavailable; no remote access was provided |

The first full device run had one timing failure while a test tapped a toolbar moving with the system keyboard. It passed in isolation. The test now waits for the real hit target to settle, retains actual touch injection and all storage/theme assertions, and the subsequent 73-test run passed. The placeholder package-name test and explicitly opt-in live-production probe were excluded from preview testing.

The preview package is `in.innovaticshub.notepad.preview`, version 2.4.1 (31). It installs alongside the existing app. The staged review APK is `.ui-review/Drawly-collaboration-preview.apk`; its SHA-256 is `C5243049D3551558E4349A7C950224EAA443677B90AC72EED598139C24ACCFE1`.

Review captures are `.ui-review/presence-canvas-light.png`, `.ui-review/presence-canvas-edge.png`, `.ui-review/presence-members-light.png` and `.ui-review/presence-members-dark.png`. The captures use an isolated local TLS test relay, not real users. The default live relay must be upgraded, or the new host deployed and selected in a staging build, before these presence features appear in normal network use.

Testing used one project-owned emulator. It does not establish behavior on every physical device, stylus, network, Android version or sustained production workload. The service has capacity limits, and a full-canvas update retains the existing replacement protocol; these changes do not introduce a CRDT or guarantee conflict-free simultaneous transforms/undo by every client version.

## Release blockers for the declared child-inclusive audience

1. **Adult management of social features is still missing.** There is no parental enabling/disabling or adult authorization flow for shared drawing. `CanvasWorkspace.kt` also reconnects from incoming/saved room codes automatically. A safety reminder in the collaboration sheet does not replace an enforced pre-sharing flow or prevent someone drawing personal details. Apply the appropriate controls before opening a room socket, including external invite and restore paths.
2. **The live privacy policy still contradicts the declared audience.** The app's linked policy was fetched successfully during this review and still says it does not address users under 13. The audience supplied by the owner includes children from age five and under. Correct the actual policy to match the app, its intended users, shared ink, transient presence, hosting/providers, diagnostics and retention; publishing a draft in the repository alone would not fix the app's current link.
3. **Console and social-content declarations need review against the released behavior.** Verify Data safety, target audience, content rating and the controls for shared freeform content. Anonymous artist labels do not establish moderation, authorization, parental consent or policy compliance. This review cannot guarantee Google Play approval.

Evidence: the app's current policy link is `https://www.freeprivacypolicy.com/live/286e718d-0906-4361-a68b-3292405405f8`. The retrieved copy is retained locally at `.ui-review/privacy-policy-presence-review.html`.

The official [Play Families requirements](https://support.google.com/googleplay/android-developer/answer/9893335) describe pre-sharing safety reminders, adult management of social features and adult action before children exchange personal information. These requirements are not satisfied merely by compiling an APK or using guest names.

## Hosting and rollout

Use a **new Drawly application within the existing Coolify instance** at `coolify.merigf.com`, subject to spare server capacity. A separate domain is optional; a subdomain such as `drawly-collab.merigf.com` can point to the existing Hetzner server. Another domain could also point there without requiring another Coolify installation.

After the reviewed changes are pushed to the selected GitHub branch, configure `collaboration-server/compose.yaml` as a separate Compose resource. Set the chosen HTTPS application origin, configure its DNS and proxy/TLS, retain `/data` on its dedicated volume, and verify the deployed WebSocket connection and restore behavior. See [deployment instructions](../collaboration-server/README.md).

Keep one writer instance and avoid overlapping deployments against the same SQLite volume. Existing Cloudflare room data is not copied to the new service. Plan migration and app rollout before changing the live origin; matching room codes on different servers do not connect the same users.
