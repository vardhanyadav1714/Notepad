# drawly.in deployment configuration

Selected by the owner on 22 October 2026. The new collaboration service uses `https://drawly.in`. Existing Play clients and ordinary legacy builds continue using `https://drawly.smartattendance.xyz`; explicit new-domain builds are separate, as described below.

| Setting | Value |
| --- | --- |
| Existing Coolify dashboard | `https://coolify.merigf.com` |
| New resource | Separate Drawly Docker Compose application |
| GitHub deployment branch | `drawly-collaboration` |
| Compose location | `collaboration-server/compose.yaml` |
| Public domain | `https://drawly.in` |
| Environment | `DRAWLY_COLLAB_ORIGIN=https://drawly.in` |
| Internal container port | `3000` |
| Persistent volume path | `/data` |
| DNS record required | A record, host `@`, value = the verified server public IPv4 |
| Dedicated volume | Separate resource-scoped named volume, mounted at `/data` |
| Resource limits | One CPU, 512 MiB memory |

## Public rollout verified on 23 October 2026

After the owner configured DNS, normal public resolution worked but the proxy still served its default self-signed certificate. Only the separate Drawly application was restarted so its already-configured HTTPS route could retry certificate provisioning. The shared proxy configuration was not changed or restarted, and the old Worker was not deployed or repointed.

Normal certificate validation now succeeds. `/health` returns HTTP 200 with `{"status":"ok"}`; invitation and asset-links endpoints return HTTP 200. Three real public WSS clients verified member rosters, drawing activity, temporary ink previews, committed strokes and a new connection recovering saved ink. The randomly generated QA room's synthetic ink was cleared afterward.

The Android preview using the new domain passed **73 applicable device tests**. Its installed APK hash matched the built artifact. A separate actual-UI check paired the app with an independent public WSS client: two artists appeared in the header, the member sheet exposed incoming drawing activity, touch input sent ink to the peer, and a cold restart/rejoin retained both clients' ink. The app left the room normally and the test cleared its own synthetic ink. This used a project-owned emulator, not two physical devices or real users.

### New-domain Android builds

From the repository root:

```powershell
.\tools\build-drawly-in.ps1 -Preview
.\tools\build-drawly-in.ps1
```

Both commands explicitly use `https://drawly.in`, version **2.4.2 / 32**. Preview installs as `in.innovaticshub.notepad.preview`; the signed release uses `in.innovaticshub.notepad` and the existing signing configuration. The release command runs release JVM tests and lint and builds an APK and App Bundle. It does not upload to Play.

Named artifacts and SHA256 metadata are under `app/build/outputs/drawly-in/`:

- `Drawly-2.4.2-drawly-in-preview.apk`
- `Drawly-2.4.2-drawly-in-release.apk`
- `Drawly-2.4.2-drawly-in-release.aab`

The preview and release builds each passed 26 JVM tests (including the existing arithmetic placeholder). Release lint completed with 0 errors and 65 warnings. APK and App Bundle signatures were verified; all 12 packaged native libraries in each artifact passed the alignment checker. The signed APK contains the new origin and new verified-link host, and its local signing fingerprint is present in the public asset-links response. This verifies the local signing configuration, not an independent check of Play App Signing.

The exact signed release APK was also installed into fresh, separate Android 17 / API 37 emulator userdata; its pulled installed hash matched the artifact, and no existing production app was replaced. Its actual UI displayed two artists and incoming drawing activity, sent touch ink to an independent public peer, and rejoined after a cold restart. Viewed screenshots confirmed both clients' ink remained rendered. It left normally and cleared only the synthetic QA room. Release verification used normal UI and TLS behavior without enabling debug access. This run does not establish physical-device or older-Android coverage.

Release SHA256 checksums:

```text
APK a6e92559f0b1d62dd58247e5108a3a19723446e444afb463eec0d3c5ed435f08
AAB 076335c325be736d1f009bf4a6d8a12987c70d12aaac1647bcf4cd6a38f30f5e
```

The helper passes validated `collaborationOrigin`, `drawlyVersionCode` and `drawlyVersionName` Gradle properties. Source defaults remain the old origin and 2.4.1 / 31. Existing installed Play versions were not updated or reconfigured by this work. New and old clients cannot share rooms across origins, even when room codes match; no stored room migration or transparent cross-origin bridge was performed. Use new-origin invitations between new-origin clients. Review this separation deliberately before distributing an update to existing users.

After the new-domain restart and live tests, all 13 recorded Coolify resource statuses matched the baseline. The shared proxy configuration was unchanged. A separate old-origin QA check still synchronized committed ink and recovered it on rejoin. No deployment or setting change was made to the old service; no Play release was uploaded.

## Historical staging checks on 22 October 2026

The supplied Coolify credentials worked. A separate Drawly project and application were created without editing existing application or DNS settings. The application built successfully from GitHub commit `2a872f82c0e1dfe3f46c14ef1b5e510506fa0054` and reports `running:healthy`. Automatic Git push deployments remain disabled during staging.

The first build exposed a Compose path issue: Coolify resolves paths from the repository root. The Compose build context was corrected to `.`; local commands must also specify that project directory, as documented in the deployment guide.

Direct requests to the server with hostname `drawly.in` verified HTTP-to-HTTPS redirection, `/health`, invitations, the Android asset-links response, paired WebSocket ink, anonymous membership, cursor activity, temporary previews, reconnect and recovery after malformed input. Forty concurrent test strokes survived a restart of only the Drawly application; the synthetic drawing was then cleared. All twelve pre-existing resource statuses matched the recorded baseline, including one resource that was already exited. Infrastructure identifiers and the server address are retained only in the ignored local review folder.

**At that staging checkpoint, public DNS and trusted TLS were incomplete.** The public A lookup returned NXDOMAIN. Direct route tests used a hostname override and disabled certificate verification only in the ignored QA script; a normal verified HTTPS request failed with an untrusted certificate. The 23 October checks above supersede that public-connectivity limitation. Production code continues enforcing HTTPS/WSS verification.

The owner subsequently configured GoDaddy DNS. No GoDaddy credentials were used and no DNS changes were made by this rollout. Preserve MX/TXT/NS and unrelated hostnames when maintaining the zone.

## Remaining rollout checks

Before Play distribution, confirm Android app-link association using the actual Play signing certificate, representative physical devices, server capacity and a backup/restore procedure for the dedicated volume. A valid asset-links response alone does not certify Play signing configuration. No external backup destination or backup schedule has been configured by this deployment.

Existing Cloudflare rooms do not automatically migrate. The Android default and the old live domain are unchanged; old and new endpoints are separate collaboration populations. The previously suggested `drawly-collab.merigf.com` is no longer the proposed application domain.

See [the deployment guide](../collaboration-server/README.md) for the build and rollout instructions. The new domain does not resolve the parental-control and privacy-policy release blockers described in [the readiness review](collaboration-presence-readiness-20261021.md).
