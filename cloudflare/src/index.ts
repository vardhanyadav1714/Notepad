export interface Env {
  ROOMS: DurableObjectNamespace;
}

type StrokePoint = {
  x: number;
  y: number;
  pressure: number;
  timestamp?: number;
  tilt?: number;
  orientation?: number;
};

type ToolConfig = {
  type: string;
  style: string;
  colorArgb: number;
  baseWidth: number;
  opacity: number;
  usePressure: boolean;
  settings?: Record<string, unknown>;
};

type RemoteStroke = {
  id: string;
  userId: string;
  points: StrokePoint[];
  toolConfig: ToolConfig;
  createdAt: number;
  pageId?: string;
};

type RemoteCanvasState = {
  userId: string;
  strokes: RemoteStroke[];
  updatedAt: number;
};

type ClientMessage =
  | { type: "join"; userId?: string }
  | { type: "stroke"; stroke?: RemoteStroke }
  | { type: "canvas_state"; state?: RemoteCanvasState }
  | { type: "ping" };

const MAX_STORED_STROKES = 1200;
const MAX_MESSAGE_BYTES = 1_500_000;
const MAX_ROOM_CLIENTS = 20;
const MAX_MESSAGES_PER_WINDOW = 240;
const RATE_LIMIT_WINDOW_MS = 10_000;
const ROOM_IDLE_TTL_MS = 24 * 60 * 60 * 1000;
const ROOM_FULL_MESSAGE = "Sorry, this room is full. Please create a new room.";
const STORAGE_STROKES_KEY = "strokes";
const STORAGE_LAST_ACTIVE_KEY = "lastActiveAt";
const LEGACY_INDEX_KEY = "strokeIndex";

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (url.pathname.startsWith("/ws/")) {
      const roomId = decodeRoomId(url.pathname.slice("/ws/".length));
      if (!roomId) {
        return json({ error: "Invalid room id." }, 400);
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
              "54:EF:4A:77:47:04:7A:E6:58:44:04:51:87:0D:C1:A5:DC:E6:F7:13:12:68:DA:D2:D3:DC:1B:F9:45:F8:8C:A2",
              "92:94:0F:E8:5E:23:8D:91:32:BB:4C:7E:17:3E:9B:89:71:A1:17:E1:E2:22:BB:CC:B7:9B:AC:09:5D:83:D5:05",
              "B9:42:A3:F9:A6:7F:8B:A6:F2:B2:AD:1D:FC:12:08:32:EF:5D:4E:CF:5A:AF:67:87:7D:0E:BD:33:EA:8E:A7:81"
            ]
          }
        }
      ]);
    }

    if (url.pathname === "/api/rooms" && request.method === "POST") {
      const roomId = generateRoomId();
      return json({
        roomId,
        shareUrl: `${url.origin}/r/${roomId}`,
        websocketUrl: `${url.protocol === "https:" ? "wss:" : "ws:"}//${url.host}/ws/${roomId}`
      });
    }

    const roomFromPath = url.pathname.startsWith("/r/")
      ? decodeRoomId(url.pathname.slice("/r/".length))
      : "";
    const roomFromQuery = url.searchParams.get("room")?.trim() ?? "";
    const roomId = roomFromPath || roomFromQuery;

    if ((url.pathname.startsWith("/r/") && !roomFromPath) || (roomId && !validRoomId(roomId))) {
      return json({ error: "Invalid room id." }, 400);
    }

    if (roomId || url.pathname === "/" || url.pathname === "/canvas") {
      return sharePage(url.origin, roomId);
    }

    return new Response("Not found", { status: 404 });
  }
};

export class CanvasRoom {
  private readonly messageTimes = new WeakMap<WebSocket, number[]>();

  constructor(
    private readonly state: DurableObjectState,
    private readonly env: Env
  ) {}

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    const roomId = decodeRoomId(url.pathname.slice("/ws/".length));
    if (!roomId) return json({ error: "Invalid room id." }, 400);
    const connectedClients = this.connectedClients();

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

    let strokes: RemoteStroke[];
    try {
      strokes = await this.getStoredStrokes();
    } catch {
      return json({ error: "Room storage is temporarily unavailable. Please retry." }, 503);
    }
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);

    this.state.acceptWebSocket(server);
    server.serializeAttachment({ roomId, connectedAt: Date.now() });
    this.touchRoom();

    server.send(JSON.stringify({ type: "room_snapshot", roomId, strokes }));

    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(sender: WebSocket, message: string | ArrayBuffer): Promise<void> {
    if (typeof message !== "string") {
      return;
    }
    if (message.length > MAX_MESSAGE_BYTES || new TextEncoder().encode(message).byteLength > MAX_MESSAGE_BYTES) {
      sender.send(JSON.stringify({ type: "error", message: "This canvas update is too large to sync. Your local note is preserved." }));
      return;
    }

    if (!this.allowMessage(sender)) {
      sender.send(JSON.stringify({ type: "error", message: "Too many drawing updates. Please slow down for a moment." }));
      sender.close(1013, "Rate limit exceeded");
      return;
    }

    let parsed: ClientMessage;
    try {
      parsed = JSON.parse(message) as ClientMessage;
    } catch {
      sender.send(JSON.stringify({ type: "error", message: "Invalid JSON." }));
      return;
    }
    if (!isRecord(parsed)) {
      sender.send(JSON.stringify({ type: "error", message: "Expected a message object." }));
      return;
    }

    if (parsed.type === "ping") {
      this.touchRoom();
      sender.send(JSON.stringify({ type: "pong", at: Date.now() }));
      return;
    }

    if (parsed.type === "join") {
      sender.serializeAttachment({
        ...((sender.deserializeAttachment() as object | undefined) ?? {}),
        userId: typeof parsed.userId === "string" ? parsed.userId.slice(0, 160) : "anonymous"
      });
      sender.send(JSON.stringify({ type: "joined", at: Date.now() }));
      this.touchRoom();
      return;
    }

    if (parsed.type === "stroke" && parsed.stroke) {
      const stroke = sanitizeStroke(parsed.stroke);
      if (!stroke) {
        sender.send(JSON.stringify({ type: "error", message: "Invalid stroke." }));
        return;
      }

      try {
        await this.storeStroke(stroke);
        this.touchRoom();
        this.broadcast({ type: "stroke", stroke, at: Date.now() });
      } catch {
        sender.send(JSON.stringify({ type: "error", message: "Could not store this stroke. The room may be at its sync limit. Your local note is preserved." }));
      }
      return;
    }

    if (parsed.type === "canvas_state" && parsed.state) {
      const state = sanitizeCanvasState(parsed.state);
      if (!state) {
        sender.send(JSON.stringify({ type: "error", message: "Invalid canvas update." }));
        return;
      }

      try {
        await this.storeCanvasState(state.strokes);
        this.touchRoom();
        this.broadcast({ type: "canvas_state", state, at: Date.now() });
      } catch {
        sender.send(JSON.stringify({ type: "error", message: "Could not store this canvas update. Please retry. Your local note is preserved." }));
      }
    }
  }

  webSocketClose(ws: WebSocket, code: number, reason: string, wasClean: boolean): void {
    this.messageTimes.delete(ws);
    if (this.connectedClients() === 0) {
      this.scheduleCleanup();
    }
    ws.close(code, reason);
  }

  async alarm(): Promise<void> {
    if (this.connectedClients() > 0) {
      this.touchRoom();
      return;
    }

    const lastActiveAt = (await this.state.storage.get<number>(STORAGE_LAST_ACTIVE_KEY)) ?? 0;
    if (Date.now() - lastActiveAt >= ROOM_IDLE_TTL_MS) {
      await this.state.storage.deleteAll();
    } else {
      await this.state.storage.setAlarm(lastActiveAt + ROOM_IDLE_TTL_MS);
    }
  }

  private broadcast(payload: unknown): void {
    const encoded = JSON.stringify(payload);
    for (const socket of this.connectedSockets()) {
      try { socket.send(encoded); } catch { /* A disconnected peer must not interrupt delivery to others. */ }
    }
  }

  private async getStoredStrokes(storage: DurableObjectStorage | DurableObjectTransaction = this.state.storage): Promise<RemoteStroke[]> {
    const strokes = await storage.get<RemoteStroke[]>(STORAGE_STROKES_KEY);
    if (strokes) {
      return strokes.slice(-MAX_STORED_STROKES);
    }

    const ids = (await storage.get<string[]>(LEGACY_INDEX_KEY)) ?? [];
    if (ids.length === 0) return [];

    const values = await storage.get<RemoteStroke>(
      ids.map((id) => `stroke:${id}`)
    );
    return ids
      .map((id) => values.get(`stroke:${id}`))
      .filter((stroke): stroke is RemoteStroke => Boolean(stroke));
  }

  private async storeStroke(stroke: RemoteStroke): Promise<void> {
    await this.state.storage.transaction(async storage => {
      const strokes = await this.getStoredStrokes(storage);
      if (strokes.some(stored => stored.id === stroke.id)) return;
      const updated = [...strokes, stroke];
      this.checkStorageSize(updated);
      await storage.put(STORAGE_STROKES_KEY, updated);
    });
  }

  private async storeCanvasState(strokes: RemoteStroke[]): Promise<void> {
    this.checkStorageSize(strokes);
    await this.state.storage.put(STORAGE_STROKES_KEY, strokes);
  }

  private checkStorageSize(strokes: RemoteStroke[]): void {
    if (strokes.length > MAX_STORED_STROKES || new TextEncoder().encode(JSON.stringify(strokes)).byteLength > MAX_MESSAGE_BYTES) {
      throw new Error("Room sync capacity exceeded");
    }
  }

  private touchRoom(): void {
    const now = Date.now();
    this.state.storage
      .put(STORAGE_LAST_ACTIVE_KEY, now)
      .then(() => this.state.storage.setAlarm(now + ROOM_IDLE_TTL_MS))
      .catch(() => undefined);
  }

  private scheduleCleanup(): void {
    this.state.storage
      .setAlarm(Date.now() + ROOM_IDLE_TTL_MS)
      .catch(() => undefined);
  }

  private connectedSockets(): WebSocket[] {
    try {
      return this.state.getWebSockets();
    } catch {
      return [];
    }
  }

  private connectedClients(): number {
    return this.connectedSockets().length;
  }

  private allowMessage(socket: WebSocket): boolean {
    const now = Date.now();
    const recent = (this.messageTimes.get(socket) ?? []).filter(
      (sentAt) => now - sentAt < RATE_LIMIT_WINDOW_MS
    );
    recent.push(now);
    this.messageTimes.set(socket, recent);
    return recent.length <= MAX_MESSAGES_PER_WINDOW;
  }
}

function sanitizeStroke(input: RemoteStroke): RemoteStroke | null {
  if (!isRecord(input) || typeof input.id !== "string" || !input.id.trim() || input.id.length > 160) return null;
  if (typeof input.userId !== "string" || !input.userId.trim() || input.userId.length > 160) return null;
  if (!Array.isArray(input.points) || input.points.length === 0 || input.points.length > 6000) return null;
  if (input.points.some(point => !isRecord(point))) return null;

  const points = input.points.map((point) => ({
    x: finiteNumber(point.x),
    y: finiteNumber(point.y),
    pressure: finiteNumber(point.pressure, 1),
    timestamp: finiteNumber(point.timestamp, 0) ?? 0,
    tilt: finiteNumber(point.tilt, 0) ?? 0,
    orientation: finiteNumber(point.orientation, 0) ?? 0
  }));

  if (points.some((point) => point.x === null || point.y === null || point.pressure === null)) {
    return null;
  }

  const toolConfig: Partial<ToolConfig> = input.toolConfig ?? {};
  return {
    id: input.id,
    userId: input.userId,
    points: points as StrokePoint[],
    toolConfig: {
      type: stringOr(toolConfig.type, "PEN").slice(0, 40),
      style: stringOr(toolConfig.style, "SOLID").slice(0, 40),
      colorArgb: finiteNumber(toolConfig.colorArgb, 0xff000000) ?? 0xff000000,
      baseWidth: Math.min(200, Math.max(.1, finiteNumber(toolConfig.baseWidth, 4) ?? 4)),
      opacity: Math.min(1, Math.max(0, finiteNumber(toolConfig.opacity, 1) ?? 1)),
      usePressure: Boolean(toolConfig.usePressure),
      settings: sanitizePenSettings(toolConfig.settings)
    },
    createdAt: finiteNumber(input.createdAt, Date.now()) ?? Date.now(),
    pageId: typeof input.pageId === "string" && input.pageId.length <= 160 ? input.pageId : "page-1"
  };
}

function sanitizePenSettings(input: Record<string, unknown> | undefined): Record<string, unknown> | undefined {
  if (!input || typeof input !== "object") return undefined;
  return {
    type: stringOr(input.type, "PEN").slice(0, 40),
    style: stringOr(input.style, "SOLID").slice(0, 40),
    color: finiteNumber(input.color, 0xff000000) ?? 0xff000000,
    width: Math.min(200, Math.max(0.1, finiteNumber(input.width, 4) ?? 4)),
    opacity: Math.min(1, Math.max(0, finiteNumber(input.opacity, 1) ?? 1)),
    pressure: input.pressure !== false,
    sensitivity: Math.min(1, Math.max(0, finiteNumber(input.sensitivity, 1) ?? 1)),
    smoothing: Math.min(1, Math.max(0, finiteNumber(input.smoothing, 0.55) ?? 0.55)),
    nibAngle: Math.min(180, Math.max(0, finiteNumber(input.nibAngle, 45) ?? 45)),
    eraserMode: input.eraserMode === "STROKE" ? "STROKE" : "SEGMENT",
    highlighterOnly: input.highlighterOnly === true,
    shape: stringOr(input.shape, "LINE").slice(0, 40),
    constrain: input.constrain === true
  };
}

function sanitizeCanvasState(input: RemoteCanvasState): RemoteCanvasState | null {
  if (!input || typeof input.userId !== "string" || input.userId.length > 160) return null;
  if (!Array.isArray(input.strokes) || input.strokes.length > MAX_STORED_STROKES) return null;

  const strokes = input.strokes.map(sanitizeStroke);
  if (strokes.some((stroke) => stroke === null)) return null;

  return {
    userId: input.userId,
    strokes: strokes as RemoteStroke[],
    updatedAt: finiteNumber(input.updatedAt, Date.now()) ?? Date.now()
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

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function validRoomId(value: string): boolean {
  return /^[a-zA-Z0-9_-]{1,128}$/.test(value);
}

function decodeRoomId(value: string): string | null {
  try {
    const decoded = decodeURIComponent(value).trim();
    return validRoomId(decoded) ? decoded : null;
  } catch { return null; }
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" }
  });
}

function generateRoomId(): string {
  const bytes = new Uint8Array(8);
  crypto.getRandomValues(bytes);
  return Array.from(bytes)
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
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
