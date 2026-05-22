export interface Env {
  ROOMS: DurableObjectNamespace;
}

type StrokePoint = {
  x: number;
  y: number;
  pressure: number;
};

type ToolConfig = {
  type: string;
  style: string;
  colorArgb: number;
  baseWidth: number;
  opacity: number;
  usePressure: boolean;
};

type RemoteStroke = {
  id: string;
  userId: string;
  points: StrokePoint[];
  toolConfig: ToolConfig;
  createdAt: number;
};

type ClientMessage =
  | { type: "join"; userId?: string }
  | { type: "stroke"; stroke?: RemoteStroke }
  | { type: "ping" };

const MAX_STORED_STROKES = 1200;
const MAX_ROOM_CLIENTS = 20;
const ROOM_FULL_MESSAGE = "Sorry, this room is full. Please create a new room.";

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (url.pathname.startsWith("/ws/")) {
      const roomId = decodeURIComponent(url.pathname.slice("/ws/".length)).trim();
      if (!roomId) {
        return json({ error: "Missing room id." }, 400);
      }
      if (request.headers.get("Upgrade") !== "websocket") {
        return json({ error: "Expected WebSocket upgrade." }, 426);
      }
      const id = env.ROOMS.idFromName(roomId);
      return env.ROOMS.get(id).fetch(request);
    }

    if (url.pathname === "/.well-known/assetlinks.json") {
      return json([
        {
          relation: ["delegate_permission/common.handle_all_urls"],
          target: {
            namespace: "android_app",
            package_name: "in.innovaticshub.notepad",
            sha256_cert_fingerprints: [
              "54:EF:4A:77:47:04:7A:E6:58:44:04:51:87:0D:C1:A5:DC:E6:F7:13:12:68:DA:D2:D3:DC:1B:F9:45:F8:8C:A2"
            ]
          }
        }
      ]);
    }

    if (url.pathname === "/api/rooms" && request.method === "POST") {
      const roomId = Date.now().toString();
      return json({
        roomId,
        shareUrl: `${url.origin}/r/${roomId}`,
        websocketUrl: `${url.protocol === "https:" ? "wss:" : "ws:"}//${url.host}/ws/${roomId}`
      });
    }

    const roomFromPath = url.pathname.startsWith("/r/")
      ? decodeURIComponent(url.pathname.slice("/r/".length)).trim()
      : "";
    const roomFromQuery = url.searchParams.get("room")?.trim() ?? "";
    const roomId = roomFromPath || roomFromQuery;

    if (roomId || url.pathname === "/" || url.pathname === "/canvas") {
      return sharePage(url.origin, roomId);
    }

    return new Response("Not found", { status: 404 });
  }
};

export class CanvasRoom {
  constructor(
    private readonly state: DurableObjectState,
    private readonly env: Env
  ) {}

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    const roomId = decodeURIComponent(url.pathname.slice("/ws/".length)).trim();
    const connectedClients = this.state.getWebSockets().length;

    if (connectedClients >= MAX_ROOM_CLIENTS) {
      return json(
        {
          error: "room_full",
          message: ROOM_FULL_MESSAGE,
          limit: MAX_ROOM_CLIENTS
        },
        429
      );
    }

    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);

    this.state.acceptWebSocket(server);
    server.serializeAttachment({ roomId, connectedAt: Date.now() });

    const strokes = await this.getStoredStrokes();
    server.send(JSON.stringify({ type: "room_snapshot", roomId, strokes }));

    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(sender: WebSocket, message: string | ArrayBuffer): Promise<void> {
    if (typeof message !== "string") {
      return;
    }

    let parsed: ClientMessage;
    try {
      parsed = JSON.parse(message) as ClientMessage;
    } catch {
      sender.send(JSON.stringify({ type: "error", message: "Invalid JSON." }));
      return;
    }

    if (parsed.type === "ping") {
      sender.send(JSON.stringify({ type: "pong", at: Date.now() }));
      return;
    }

    if (parsed.type === "join") {
      sender.serializeAttachment({
        ...((sender.deserializeAttachment() as object | undefined) ?? {}),
        userId: parsed.userId ?? "anonymous"
      });
      sender.send(JSON.stringify({ type: "joined", at: Date.now() }));
      return;
    }

    if (parsed.type === "stroke" && parsed.stroke) {
      const stroke = sanitizeStroke(parsed.stroke);
      if (!stroke) {
        sender.send(JSON.stringify({ type: "error", message: "Invalid stroke." }));
        return;
      }

      await this.storeStroke(stroke);
      this.broadcast({ type: "stroke", stroke, at: Date.now() });
    }
  }

  webSocketClose(ws: WebSocket, code: number, reason: string, wasClean: boolean): void {
    ws.close(code, reason);
  }

  private broadcast(payload: unknown): void {
    const encoded = JSON.stringify(payload);
    for (const socket of this.state.getWebSockets()) {
      socket.send(encoded);
    }
  }

  private async getStoredStrokes(): Promise<RemoteStroke[]> {
    const ids = (await this.state.storage.get<string[]>("strokeIndex")) ?? [];
    if (ids.length === 0) return [];

    const values = await this.state.storage.get<RemoteStroke>(
      ids.map((id) => `stroke:${id}`)
    );
    return ids
      .map((id) => values.get(`stroke:${id}`))
      .filter((stroke): stroke is RemoteStroke => Boolean(stroke));
  }

  private async storeStroke(stroke: RemoteStroke): Promise<void> {
    const index = (await this.state.storage.get<string[]>("strokeIndex")) ?? [];
    if (index.includes(stroke.id)) {
      return;
    }

    const nextIndex = [...index, stroke.id];
    const overflow = Math.max(0, nextIndex.length - MAX_STORED_STROKES);
    const removed = overflow > 0 ? nextIndex.splice(0, overflow) : [];

    await this.state.storage.put(`stroke:${stroke.id}`, stroke);
    await this.state.storage.put("strokeIndex", nextIndex);
    if (removed.length > 0) {
      await this.state.storage.delete(removed.map((id) => `stroke:${id}`));
    }
  }
}

function sanitizeStroke(input: RemoteStroke): RemoteStroke | null {
  if (!input || typeof input.id !== "string" || input.id.length > 160) return null;
  if (typeof input.userId !== "string" || input.userId.length > 160) return null;
  if (!Array.isArray(input.points) || input.points.length === 0 || input.points.length > 6000) return null;

  const points = input.points.map((point) => ({
    x: finiteNumber(point.x),
    y: finiteNumber(point.y),
    pressure: finiteNumber(point.pressure, 1)
  }));

  if (points.some((point) => point.x === null || point.y === null || point.pressure === null)) {
    return null;
  }

  const toolConfig = input.toolConfig ?? {};
  return {
    id: input.id,
    userId: input.userId,
    points: points as StrokePoint[],
    toolConfig: {
      type: stringOr(toolConfig.type, "PEN"),
      style: stringOr(toolConfig.style, "SOLID"),
      colorArgb: finiteNumber(toolConfig.colorArgb, 0xff000000) ?? 0xff000000,
      baseWidth: finiteNumber(toolConfig.baseWidth, 4) ?? 4,
      opacity: finiteNumber(toolConfig.opacity, 1) ?? 1,
      usePressure: Boolean(toolConfig.usePressure)
    },
    createdAt: finiteNumber(input.createdAt, Date.now()) ?? Date.now()
  };
}

function finiteNumber(value: unknown, fallback?: number): number | null {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return fallback ?? null;
  }
  return value;
}

function stringOr(value: unknown, fallback: string): string {
  return typeof value === "string" && value.trim() ? value : fallback;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" }
  });
}

function sharePage(origin: string, roomId: string): Response {
  const room = escapeHtml(roomId);
  const title = room ? `Join Notepad room ${room}` : "Notepad Collaboration";
  const appLink = room ? `notepad://canvas?room=${encodeURIComponent(roomId)}` : "notepad://canvas";
  const html = `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${title}</title>
  <style>
    body { margin: 0; font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; background: #f6f7fb; color: #111827; }
    main { min-height: 100vh; display: grid; place-items: center; padding: 24px; }
    section { width: min(420px, 100%); background: white; border: 1px solid #e5e7eb; border-radius: 20px; padding: 28px; box-shadow: 0 24px 60px rgba(15,23,42,.12); }
    h1 { margin: 0 0 10px; font-size: 26px; }
    p { color: #4b5563; line-height: 1.5; }
    code { display: block; padding: 12px; border-radius: 12px; background: #f3f4f6; overflow-wrap: anywhere; }
    a { display: inline-flex; align-items: center; justify-content: center; min-height: 46px; padding: 0 18px; border-radius: 999px; background: #111827; color: white; text-decoration: none; font-weight: 700; }
  </style>
</head>
<body>
  <main>
    <section>
      <h1>${room ? "Join this canvas" : "Notepad Collaboration"}</h1>
      <p>${room ? "Open this room in the Notepad app to draw together in real time." : "Create a room from the Notepad app and share the link with your study group."}</p>
      ${room ? `<p><a href="${appLink}">Open in app</a></p><p>Room code</p><code>${room}</code>` : ""}
      ${room ? `<script>setTimeout(() => { location.href = "${appLink}"; }, 250);</script>` : ""}
    </section>
  </main>
</body>
</html>`;

  return new Response(html, {
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Notepad-Origin": origin
    }
  });
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}
