# drawly.in deployment configuration

Selected by the owner on 22 October 2026. The collaboration service's default public origin is `https://drawly.in`; the existing Android/live origin stays `https://drawly.smartattendance.xyz` until the new deployment and rollout are verified.

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

## Deployment verified on 22 October 2026

The supplied Coolify credentials worked. A separate Drawly project and application were created without editing existing application or DNS settings. The application built successfully from GitHub commit `2a872f82c0e1dfe3f46c14ef1b5e510506fa0054` and reports `running:healthy`. Automatic Git push deployments remain disabled during staging.

The first build exposed a Compose path issue: Coolify resolves paths from the repository root. The Compose build context was corrected to `.`; local commands must also specify that project directory, as documented in the deployment guide.

Direct requests to the server with hostname `drawly.in` verified HTTP-to-HTTPS redirection, `/health`, invitations, the Android asset-links response, paired WebSocket ink, anonymous membership, cursor activity, temporary previews, reconnect and recovery after malformed input. Forty concurrent test strokes survived a restart of only the Drawly application; the synthetic drawing was then cleared. All twelve pre-existing resource statuses matched the recorded baseline, including one resource that was already exited. Infrastructure identifiers and the server address are retained only in the ignored local review folder.

**Public DNS and trusted TLS remain incomplete.** The public A lookup returned NXDOMAIN. Direct route tests used a hostname override and disabled certificate verification only in the ignored QA script; a normal verified HTTPS request failed with an untrusted certificate. These checks do not establish that Android or public browsers can connect. Production code still enforces HTTPS/WSS verification.

The supplied access file contains Coolify URL/token keys only. The GoDaddy credentials file path is still needed; no GoDaddy authentication or DNS changes have occurred. Once provided, inspect the existing `drawly.in` zone before adding or updating only the necessary apex A record. Preserve MX/TXT/NS and unrelated hostnames. Check for conflicting AAAA records and certificate restrictions rather than replacing the whole zone.

## Remaining rollout checks

After DNS is configured, verify normal public resolution, trusted TLS, WSS, invitations and the Android app association. Then test the isolated Android preview with `-PcollaborationOrigin=https://drawly.in`. Confirm the actual Play signing certificate, server capacity and a backup/restore procedure for the dedicated volume before production use. No external backup destination or backup schedule has been configured by this deployment.

Existing Cloudflare rooms do not automatically migrate. The Android default and the old live domain are unchanged; old and new endpoints are separate collaboration populations. The previously suggested `drawly-collab.merigf.com` is no longer the proposed application domain.

See [the deployment guide](../collaboration-server/README.md) for the build and rollout instructions. The new domain does not resolve the parental-control and privacy-policy release blockers described in [the readiness review](collaboration-presence-readiness-20261021.md).
