# Drawly collaboration on Hetzner / Coolify

This optional service runs the **same room protocol as `cloudflare/src/index.ts`** on Node 24 with SQLite persistence. It adds anonymous round member avatars, activity/cursor updates and temporary live ink previews. Existing `join`, `stroke`, `canvas_state` and `room_snapshot` messages remain supported.

The Android default still points to `https://drawly.smartattendance.xyz`. Explicit new-domain builds use `tools/build-drawly-in.ps1` to select `https://drawly.in` and version 2.4.2 (32), without changing that legacy default or existing installed apps.

Deployment status on 23 October 2026: the separate Coolify application is healthy, public DNS and trusted HTTPS work, and real public WSS checks passed membership, drawing activity, live ink previews, committed ink and rejoin. The new-domain Android preview passed 73 applicable device tests and paired against an independent public peer through its actual UI. The legacy Worker and shared proxy configuration were not changed. See [the deployment record](../docs/drawly-domain-setup.md) for evidence boundaries, build commands and remaining release requirements.

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

The first command produces a preview package installed alongside production. The second checks and builds the signed production APK and App Bundle using existing local signing credentials. Both explicitly select `https://drawly.in`, version 2.4.2 / code 32, and copy named artifacts and SHA256 metadata into `app/build/outputs/drawly-in/`. Neither command uploads to Play. Use a higher `-VersionCode` and appropriate `-VersionName` for later releases. Ordinary builds without these properties keep the legacy defaults.

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
- The room link is a bearer capability: anyone who has it can join and edit. This is not account-based authorization, owner moderation or a parental-control system.

## Release gates

Drawly's stated Play target audience includes children. Presence improvements do not by themselves establish production readiness. Adult controls for social features, the pre-sharing safety reminder, the public privacy policy, Data safety declarations, content-rating answers and any required reporting/moderation controls must be reviewed against actual behavior. The earlier policy copy excluded under-13 users, which conflicts with the declared audience; verify and correct the live policy before release.

References: [Play Families requirements](https://support.google.com/googleplay/android-developer/answer/9893335), [Coolify networking](https://coolify.io/docs/core/networking-in-coolify), [persistent storage](https://coolify.io/docs/applications/configuration/persistent-storage).
