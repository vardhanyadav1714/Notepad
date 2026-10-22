# drawly.in deployment configuration

Selected by the owner on 22 October 2026. The collaboration service's default public origin is `https://drawly.in`; the existing Android/live origin stays `https://drawly.smartattendance.xyz` until the new deployment and rollout are verified.

| Setting | Value |
| --- | --- |
| Existing Coolify dashboard | `https://coolify.merigf.com` |
| New resource | Separate Drawly Docker Compose application |
| Compose location | `collaboration-server/compose.yaml` |
| Public domain | `https://drawly.in` |
| Environment | `DRAWLY_COLLAB_ORIGIN=https://drawly.in` |
| Internal container port | `3000` |
| Persistent volume path | `/data` |
| DNS record | A record, host `@`, value = the actual Hetzner public IPv4 |

The registrar and the actual Hetzner public IPv4 have not yet been supplied. No DNS records or remote Coolify settings have been changed. The previously suggested `drawly-collab.merigf.com` is no longer the proposed application domain; the dated review is retained as historical evidence.

After DNS and the new resource are configured, verify `https://drawly.in/health`, TLS, the WebSocket connection, invitation links, Android app association and saved-room recovery. Then build the isolated Android preview with `-PcollaborationOrigin=https://drawly.in`. Existing Cloudflare rooms do not automatically migrate.

See [the deployment guide](../collaboration-server/README.md) for the build and rollout instructions. The new domain does not resolve the parental-control and privacy-policy release blockers described in [the readiness review](collaboration-presence-readiness-20261021.md).
