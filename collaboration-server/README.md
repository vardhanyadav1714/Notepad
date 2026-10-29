# Drawly collaboration on Hetzner / Coolify

This optional service runs the **same room protocol as `cloudflare/src/index.ts`** on Node 24 with SQLite persistence. It adds anonymous round member avatars, activity/cursor updates and temporary live ink previews. Existing `join`, `stroke`, `canvas_state` and `room_snapshot` messages remain supported.

The Android default still points to `https://drawly.smartattendance.xyz`. Explicit new-domain builds use `tools/build-drawly-in.ps1` to select `https://drawly.in` and version 2.4.3 (33), without changing that legacy default or existing installed apps.

The 23 October deployment passed public HTTPS/WSS membership, activity, ink and reconnect checks. The changes prepared on 29 October add asynchronous SQLite persistence, bounded queues, managed-room deletion, reports and family controls. See [the current readiness record](../docs/production-readiness-20261029.md) for the exact tested and deployed state; source code alone is not evidence of a live deployment.

## Local checks

```powershell
cd collaboration-server
npm ci
npm test
npm start
```

Health: `http://localhost:3000/health`. The service creates `data/rooms.sqlite`. Do not commit this directory. Tests use isolated temporary databases and real paired WebSocket clients.

## Separate Coolify resource

The owner selected `https://drawly.in` on 22 October 2026. It is now the Compose service's default public origin; `DRAWLY_COLLAB_ORIGIN` can override it for staging. `https://coolify.merigf.com` remains the management dashboard.

1. At the DNS provider for `drawly.in`, add an **A** record with name **@** (the apex) and value equal to the Hetzner server's public IPv4 address. Use that server's actual address; the Coolify dashboard's DNS can point at a proxy and is not proof of the origin server's address. Add an AAAA record only if that server and its HTTPS route are configured for IPv6. Keep the existing application's records unchanged.
2. Create a **new** Docker Compose resource from this repository, with compose file `collaboration-server/compose.yaml` and base directory `/`. Build context is the repository root. For local Compose commands, run from the repository root with `docker compose --project-directory . -f collaboration-server/compose.yaml ...` so relative build paths resolve the same way as Coolify.
3. Set `DRAWLY_COLLAB_ORIGIN=https://drawly.in` (also shown in `.env.example`). Associate `https://drawly.in` with the `collaboration` service, container port **3000**, and let Coolify manage its proxy/TLS route. No host port is published by this Compose file. `www` is optional and is not the canonical application origin.
   The six route-observability labels in Compose are bound to the existing production Drawly resource. Before deploying another resource on the same proxy, replace all six with that resource's actual generated router IDs. Coolify escapes variables in custom label names, so environment interpolation cannot supply these IDs.
4. Keep the dedicated named volume mounted at `/data`; it holds the SQLite database and its WAL files. Enable volume backups and verify restore before release. Do not share it with an unrelated application or another writable replica.
5. Start with one replica and avoid overlapping/rolling instances writing the same volume. Stop the old instance before starting the replacement; clients reconnect after this brief interruption. Default resource limits are one CPU and 512 MB; test actual load and available server capacity before using these values in production.
6. Verify health, TLS, WebSocket upgrades, two-device live ink, reconnect, process restart and volume recovery on the deployed service. Local Node tests do not validate the remote proxy, certificate, Docker build or DNS.

The service rejects cross-origin browser WebSockets; the Android client does not send an Origin header. Use the configured origin for browser tests.

## Staging Android build

Use the dedicated helper from the repository root for reproducible new-domain artifacts:

```powershell
.\tools\build-drawly-in.ps1 -Preview
.\tools\build-drawly-in.ps1
```

The first command produces a preview package installed alongside production. The second checks and builds the signed production APK and App Bundle using existing local signing credentials. Both explicitly select `https://drawly.in`, version 2.4.3 / code 33, and copy named artifacts and SHA256 metadata into `app/build/outputs/drawly-in/`. Neither command uploads to Play. Use a higher `-VersionCode` and appropriate `-VersionName` for later releases. Ordinary builds without these properties keep the legacy defaults.

From the repository root, using the Android Studio JDK:

```powershell
.\gradlew.bat -PdevicePreview=true -PcollaborationOrigin=https://drawly.in :app:assembleDebug
```

This selects a separate preview package and the new origin for WebSockets, invitations and verified-link host. The server's asset links currently identify the production application and its existing certificate fingerprints; preview links can be pasted manually into the preview app. Production app-link verification must be checked using the actual Play signing certificate.

Expected public endpoints after deployment:

- Health: `https://drawly.in/health`
- WebSocket rooms: `wss://drawly.in/ws/<room-code>`
- Invitations: `https://drawly.in/r/<room-code>`
- Android association: `https://drawly.in/.well-known/assetlinks.json`

Buying the domain and updating these files does not configure DNS, create a Coolify resource, issue a certificate or switch existing Android installations. Verify those steps before distributing a build targeting the new origin.

## Existing rooms and rollout

Cloudflare room storage is **not automatically copied** into SQLite. Changing the origin on new installs does not move existing users or stored rooms. Users on the old endpoint and users on the new endpoint cannot collaborate with each other, even when they enter the same room code. Existing saved drawing files contain the room code, not a server origin.

Keep the live endpoint unchanged until a migration/rollout plan is agreed. Options include upgrading the current Worker first, or coordinating the move to Hetzner with room migration and an app update. Do not repoint the live domain merely because the new service is healthy. Preserve local drawings and verify cross-version behavior. The 24-hour retention period concerns **idle** rooms; active rooms can live longer.

## Data and limits

- Member identities are temporary server-generated labels and colours; no profile names, photos, contacts, birthdays or account identifiers are requested by this feature.
- Membership and cursors are connection state. Temporary previews never enter stored ink, export, autosave or undo history. The client removes stale cursors/previews after four seconds.
- Live packets are bounded, throttled and dropped under backpressure. Durable ink uses a separate rate quota and remains the source of saved drawings.
- Existing room limits remain 20 connected peers, 1,200 stored stroke messages and roughly 1.5 MB per stored canvas/message. A successful local small-group test is not a large-room capacity guarantee.
- Saved shared ink expires after 24 hours of room inactivity; local drawings remain on the device. TLS termination, server/proxy logs, backups and their retention need corresponding privacy disclosures.
- The invite is a bearer capability: anyone with it can join and edit. Managed room IDs have 192 random bits. Only the creator's separate 256-bit capability can end a managed room. Ending deletes live ink and revokes that invite; leaving alone preserves it for normal idle expiry. Old freeform room codes remain supported for installed clients and do not acquire creator ownership retroactively.
- SQLite runs in a dedicated worker and stores strokes incrementally. Limits include 10,000 persisted rooms, 128 MiB logical ink, 32 MiB cached ink, 16 MiB queued inbound bytes, 32 MiB outstanding outgoing bytes, and 1,000 sockets. These are admission controls, not measured capacity promises. Keep one writable replica.
- Android sharing defaults off. Device authentication establishes a family PIN; each session requires disclosure/rules acceptance and the PIN. Deep links and restored notes do not autojoin. Leaving/backgrounding pauses sharing. Anyone can report or block and leave without the PIN. These controls do not establish age or legally verified parental consent.

## Reports and operator email

Reports persist before acknowledgement and contain only a room reference, a selected reason, timestamp and status. They expire after 30 days and do not preserve an ink snapshot. An operator must review promptly while a room still exists. In the Drawly container:

```sh
node moderate.mjs list
node moderate.mjs review <report-id>
node moderate.mjs remove-room <report-id>
node moderate.mjs resolve <report-id>
node moderate.mjs mail-status
```

`review` displays sensitive shared ink: use a private terminal, never copy it into logs or tickets. Removal disconnects existing participants on the next maintenance sweep (normally within 30 seconds). Review and act on reports regularly even if email is unavailable.

Optional Resend alerts require `RESEND_API_KEY`, `DRAWLY_REPORT_FROM` and `DRAWLY_REPORT_TO` in this resource's runtime environment. Use a sending-only key restricted to Drawly. Never put it in Android or Git. A fixed recipient receives digests of at most 100 report IDs/categories, with no ink, invites or reporter contact information. The durable outbox groups notifications, permits at most 20 attempts per rolling day, uses stable provider idempotency keys and exponential retry, and stops after eight attempts or 23 hours. `mail-status` exposes pending/failed batches without message content. Mail failure does not remove a report. Provider/account quotas are shared with other apps and still need monitoring; notification limits do not cap report acceptance.

The configured support inbox is `support@drawly.in`, hosted in the owner's Resend dashboard. The owner confirmed daily review. Keep open/click tracking off. This release's server key has sending-only access restricted to `drawly.in`; the browser inbox handles replies and review notification messages.

## Backups and disk maintenance

The owner approved a Drawly-only daily Coolify volume backup to the existing off-server B2 destination: at most seven copies for seven days, pausing only this resource for a consistent archive. A 29 October backup was downloaded into an isolated local restore fixture and passed SQLite `quick_check`. Existing other-app backup settings were preserved. Verify future executions and retention; a configured schedule alone does not prove every future backup succeeds.

For an independent SQLite-aware backup, `node backup.mjs <private-backup-directory>` uses `DATABASE_PATH` and `DRAWLY_BACKUP_RETENTION_DAYS` and includes acknowledged WAL changes. Do not copy only a live `.sqlite` file. Restore into a separate volume first. Ended/expired ink can remain in older backups until they expire.

Expired rows free reusable pages. New databases use incremental auto-vacuum. Existing databases may need one planned offline `node compact-offline.mjs --server-stopped` run, using `DATABASE_PATH`, with adequate disk headroom and a verified backup. Do not run it against a live service. None of these operations promises secure erasure of historical disk pages.

After normalized writes begin, do not blindly roll back to the old full-JSON server: it cannot read the new stroke tables or enforce managed-room revocation. Use a compatible fix or a reviewed migration/restore plan. Restoring an older snapshot can lose later edits and must not silently restore deleted/revoked content.

## Reproducible local load check

`npm run benchmark` starts its own loopback server and fresh temporary database. It has no remote URL option. It uses 100 clients in ten rooms, 800 committed strokes, 8,000 expected deliveries, and concurrent transient updates. It reports delivery latency, CPU/RSS, queue sizes and logical expiry, then removes only its fixture. It does not impose the production CPU quota or emulate internet latency, production storage, reconnect bursts or a sustained soak. Run an isolated deployment-sized soak before advertising production concurrency.

## Release gates

Drawly's stated Play target audience includes children. New managed-room creation and legal publication remain off until `DRAWLY_LEGAL_APPROVED=1`, a working support email and actual numeric backup/log retention are configured. This preserves legacy wire clients while preventing the new app from creating sessions against an unfinished policy setup. Do not invent retention values or treat a PIN as legal age verification. Confirm the actual reviewer, public privacy policy, Data safety, target audience, IARC social/UGC answers and applicable SDK/child-data obligations before Play publication. Code and automated tests cannot guarantee Play approval or provide ongoing human moderation.

References: [Play Families requirements](https://support.google.com/googleplay/android-developer/answer/9893335), [Coolify networking](https://coolify.io/docs/core/networking-in-coolify), [persistent storage](https://coolify.io/docs/applications/configuration/persistent-storage).
