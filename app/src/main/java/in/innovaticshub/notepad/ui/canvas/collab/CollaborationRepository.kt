package `in`.innovaticshub.notepad.ui.canvas.collab

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class CollaborationRepository(
    private val websocketBaseUrl:String=CollaborationEndpoint.websocketUrl,
    private val client:WebSocket.Factory=OkHttpClient.Builder().pingInterval(25,TimeUnit.SECONDS).retryOnConnectionFailure(true).build()
) {
    private val roomRandom = SecureRandom()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val socketLock = Any()

    private var webSocket: WebSocket? = null
    private var socketOpen = false
    private var activeRoomCode: String? = null
    private var listenerGeneration=0L
    private var liveUpdatesSupported = false
    private var currentMemberId: String? = null

    val currentUserId: String = "local-${UUID.randomUUID()}"

    /** Best effort only: never queue cursors or unfinished ink while offline. */
    fun sendLiveUpdate(roomCode: String, cursor: MemberCursor?, activity: String, preview: RemoteStroke?, sendPreview: Boolean = true) {
        synchronized(socketLock) {
            if (!socketOpen || !liveUpdatesSupported || activeRoomCode != normalizeRoomCode(roomCode)) return
            val socket = webSocket ?: return
            // A slow peer must not allow ephemeral packets to delay saved ink.
            if (socket.queueSize() > 128_000) return
            val position = cursor?.let { JSONObject().put("x", it.x).put("y", it.y).put("pageId", it.pageId) }
            socket.send(JSONObject().put("type", "presence").put("cursor", position ?: JSONObject.NULL).put("activity", activity).toString())
            if (sendPreview) socket.send(JSONObject().put("type", "stroke_preview").put("stroke", preview?.toJson() ?: JSONObject.NULL).toString())
        }
    }

    suspend fun createRoom(): String {
        val bytes = ByteArray(8)
        roomRandom.nextBytes(bytes)
        return bytes.joinToString(separator = "") { byte ->
            "%02x".format(Locale.US, byte.toInt() and 0xFF)
        }
    }

    suspend fun joinRoom(roomCode: String): String {
        return normalizeRoomCode(roomCode).also {require(it.isNotBlank() && it.length<=128) {"Enter a valid room code or invite link."}}
    }

    suspend fun uploadStroke(roomCode: String, stroke: RemoteStroke) {
        if (stroke.id.isBlank() || roomCode.isBlank()) return
        val message = JSONObject()
            .put("type", "stroke")
            .put("stroke", stroke.toJson())
            .toString()

        withContext(Dispatchers.IO) {
            val sent = synchronized(socketLock) {
                val socket = webSocket
                if (socket != null && socketOpen && activeRoomCode==normalizeRoomCode(roomCode)) {
                    socket.send(message)
                } else {
                    false
                }
            }

            if (!sent) {
                error("Collaboration is not connected yet. Please wait and try again.")
            }
        }
    }

    suspend fun uploadCanvasState(roomCode: String, state: RemoteCanvasState) {
        if (roomCode.isBlank()) return
        val message = JSONObject()
            .put("type", "canvas_state")
            .put("state", state.toJson())
            .toString()

        withContext(Dispatchers.IO) {
            val sent = synchronized(socketLock) {
                val socket = webSocket
                if (socket != null && socketOpen && activeRoomCode==normalizeRoomCode(roomCode)) {
                    socket.send(message)
                } else {
                    false
                }
            }

            if (!sent) {
                error("Collaboration is not connected yet. Please wait and try again.")
            }
        }
    }

    fun listenToStrokes(
        roomCode: String,
        onStrokes: (List<RemoteStroke>) -> Unit,
        onCanvasState: (RemoteCanvasState) -> Unit,
        onConnected: () -> Unit,
        onError: (Throwable) -> Unit,
        onMembers: (List<RoomMember>) -> Unit = {},
        onMemberUpdate: (RoomMember) -> Unit = {},
        onPreview: (LiveStrokePreview) -> Unit = {}
    ): CollaborationSubscription {
        val normalized = normalizeRoomCode(roomCode)
        val closedByUser = AtomicBoolean(false)

        val generation=synchronized(socketLock) {
            listenerGeneration++
            webSocket?.close(1000, "Switching rooms")
            webSocket = null
            socketOpen = false
            liveUpdatesSupported = false
            currentMemberId = null
            activeRoomCode = normalized
            listenerGeneration
        }
        fun valid()=synchronized(socketLock) {!closedByUser.get() && listenerGeneration==generation && activeRoomCode==normalized}

        fun connect(): WebSocket {
            val request = Request.Builder()
                .url("$websocketBaseUrl/${normalized.urlEncoded()}")
                .build()

            return synchronized(socketLock) {client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    synchronized(socketLock) {
                        if(!valid()) {webSocket.close(1000,"Stale subscription");return}
                        this@CollaborationRepository.webSocket = webSocket
                        socketOpen = true
                        liveUpdatesSupported = false
                        currentMemberId = null
                    }
                    webSocket.send(
                        JSONObject()
                            .put("type", "join")
                            .put("userId", currentUserId)
                            .toString()
                    )
                    scope.launch(Dispatchers.Main) {
                        if(valid()) onConnected()
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if(!valid()) return
                    runCatching {
                        text.toCollaborationMessage()
                    }.onSuccess { message ->
                        message.members?.let { members -> scope.launch(Dispatchers.Main) { if(valid()) onMembers(members) } }
                        message.member?.let { member -> scope.launch(Dispatchers.Main) { if(valid()) onMemberUpdate(member) } }
                        message.preview?.let { preview -> scope.launch(Dispatchers.Main) { if(valid()) onPreview(preview) } }
                        if (message.strokes.isNotEmpty()) {
                            scope.launch(Dispatchers.Main) {
                                if(valid()) onStrokes(message.strokes)
                            }
                        }
                        message.canvasState?.let { state ->
                            scope.launch(Dispatchers.Main) {
                                if(valid()) onCanvasState(state)
                            }
                        }
                    }.onFailure { error ->
                        scope.launch(Dispatchers.Main) {
                            if(valid()) onError(error)
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val error = response?.toCollaborationError() ?: t
                    val shouldReconnect = synchronized(socketLock) {
                        if (this@CollaborationRepository.webSocket == webSocket) {
                            socketOpen = false
                        }
                        valid() && response?.code != 429 && this@CollaborationRepository.webSocket==webSocket
                    }

                    if (shouldReconnect) {
                        scope.launch {
                            kotlinx.coroutines.delay(1200)
                            if (valid()) {
                                connect()
                            }
                        }
                    }

                    synchronized(socketLock) {
                        if (this@CollaborationRepository.webSocket == webSocket) {
                            socketOpen = false
                        }
                    }
                    scope.launch(Dispatchers.Main) {
                        if(valid()) onError(error)
                    }
                }

                override fun onClosing(webSocket:WebSocket,code:Int,reason:String) {webSocket.close(code,reason)}

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    val shouldReconnect = synchronized(socketLock) {
                        val current=this@CollaborationRepository.webSocket==webSocket
                        if (this@CollaborationRepository.webSocket == webSocket) {
                            this@CollaborationRepository.webSocket = null
                            socketOpen = false
                        }
                        valid() && current && code != 1000
                    }
                    scope.launch(Dispatchers.Main) {if(valid()) onError(IllegalStateException(if(shouldReconnect) "Connection interrupted. Reconnecting..." else "Connection closed. Tap Reconnect to join again."))}

                    if (shouldReconnect) {
                        scope.launch {
                            kotlinx.coroutines.delay(1200)
                            if (valid()) {
                                connect()
                            }
                        }
                    }

                    synchronized(socketLock) {
                        if (this@CollaborationRepository.webSocket == webSocket) {
                            this@CollaborationRepository.webSocket = null
                            socketOpen = false
                        }
                    }
                }
            }
            ).also {socket->if(valid()) webSocket=socket else socket.close(1000,"Stale subscription")}}
        }

        val socket = connect()

        synchronized(socketLock) {
            if(valid()) webSocket = socket else socket.close(1000,"Stale subscription")
        }

        return CollaborationSubscription {
            closedByUser.set(true)
            val socketToClose = synchronized(socketLock) {
                if(listenerGeneration!=generation) return@synchronized null
                val current = webSocket
                webSocket = null
                socketOpen = false
                liveUpdatesSupported = false
                currentMemberId = null
                activeRoomCode = null
                current
            }
            socketToClose?.close(1000, "Leaving room")
        }
    }

    private data class CollaborationMessage(
        val strokes: List<RemoteStroke> = emptyList(),
        val canvasState: RemoteCanvasState? = null,
        val members: List<RoomMember>? = null,
        val member: RoomMember? = null,
        val preview: LiveStrokePreview? = null
    )

    private fun String.toCollaborationMessage(): CollaborationMessage {
        val json = JSONObject(this)
        return when (json.optString("type")) {
            "joined" -> {
                synchronized(socketLock) {
                    currentMemberId = json.optString("memberId").takeIf { it.isNotBlank() }
                    liveUpdatesSupported = json.optJSONArray("capabilities")?.let { caps ->
                        (0 until caps.length()).any { caps.optString(it) == "stroke_preview" }
                    } == true
                }
                CollaborationMessage()
            }
            "presence" -> {
                val array = json.optJSONArray("members") ?: JSONArray()
                CollaborationMessage(members = (0 until minOf(array.length(), 20)).mapNotNull { array.optJSONObject(it)?.toRoomMember() })
            }
            "presence_update" -> CollaborationMessage(member = json.optJSONObject("member")?.toRoomMember())
            "stroke_preview" -> {
                val id = json.optString("memberId")
                val stroke = json.optJSONObject("stroke")?.takeIf { (it.optJSONArray("points")?.length() ?: 0) <= 128 }?.toRemoteStrokeOrNull()
                if (id.isBlank() || id.length > 160) CollaborationMessage()
                else CollaborationMessage(preview = LiveStrokePreview(id, stroke))
            }
            "room_snapshot" -> {
                val strokes = json.optJSONArray("strokes") ?: JSONArray()
                CollaborationMessage(strokes = buildList {
                    repeat(strokes.length()) { index ->
                        strokes.optJSONObject(index)
                            ?.toRemoteStrokeOrNull()
                            ?.let(::add)
                    }
                })
            }
            "stroke" -> CollaborationMessage(
                strokes = listOfNotNull(json.optJSONObject("stroke")?.toRemoteStrokeOrNull())
            )
            "canvas_state" -> CollaborationMessage(
                canvasState = json.optJSONObject("state")?.toRemoteCanvasStateOrNull()
            )
            "error" -> error(json.optString("message", "Collaboration server error."))
            else -> CollaborationMessage()
        }
    }

    private fun JSONObject.toRoomMember(): RoomMember? {
        val id = optString("id").takeIf { it.isNotBlank() && it.length <= 160 } ?: return null
        val position = optJSONObject("cursor")?.let {
            val x = it.optDouble("x"); val y = it.optDouble("y"); val page = it.optString("pageId")
            if (x.isFinite() && y.isFinite() && kotlin.math.abs(x) <= 1_000_000 && kotlin.math.abs(y) <= 1_000_000 && page.isNotBlank() && page.length <= 160)
                MemberCursor(x.toFloat(), y.toFloat(), page) else null
        }
        // Only accept generated labels. A relay must not inject arbitrary profile text.
        val label = optString("label").takeIf { it.matches(Regex("Artist [A-Fa-f0-9]{4}")) } ?: "Artist"
        return RoomMember(id, optString("userId").take(160), label, optLong("colorArgb", 0xFF2563EB),
            optString("activity").takeIf { it in listOf("drawing", "erasing", "selecting") } ?: "idle",
            position, optLong("updatedAt"), isSelf = id == synchronized(socketLock) { currentMemberId })
    }

    private fun RemoteStroke.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("userId", userId)
            .put(
                "points",
                JSONArray().apply {
                    points.forEach { point ->
                        put(
                            JSONObject()
                                .put("x", point.x)
                                .put("y", point.y)
                                .put("pressure", point.pressure)
                                .put("timestamp",point.timestamp).put("tilt",point.tilt).put("orientation",point.orientation)
                        )
                    }
                }
            )
            .put(
                "toolConfig",
                JSONObject()
                    .put("type", toolConfig.type)
                    .put("style", toolConfig.style)
                    .put("colorArgb", toolConfig.colorArgb)
                    .put("baseWidth", toolConfig.baseWidth)
                    .put("opacity", toolConfig.opacity)
                    .put("usePressure", toolConfig.usePressure)
                    .apply {toolConfig.settingsJson?.let {put("settings",JSONObject(it))}}
            )
            .put("createdAt", createdAt)
            .put("pageId",pageId)
    }

    private fun RemoteCanvasState.toJson(): JSONObject {
        return JSONObject()
            .put("userId", userId)
            .put(
                "strokes",
                JSONArray().apply {
                    strokes.forEach { stroke -> put(stroke.toJson()) }
                }
            )
            .put("updatedAt", updatedAt)
    }

    private fun JSONObject.toRemoteStrokeOrNull(): RemoteStroke? {
        val pointsArray = optJSONArray("points") ?: JSONArray()
        val tool = optJSONObject("toolConfig") ?: JSONObject()
        return RemoteStroke(
            id = optString("id").takeUnless { it.isBlank() } ?: return null,
            userId = optString("userId"),
            points = buildList {
                repeat(pointsArray.length()) { index ->
                    val point = pointsArray.optJSONObject(index)
                    if (point != null) {
                        add(
                            RemoteStrokePoint(
                                x = point.finiteDouble("x", 0.0),
                                y = point.finiteDouble("y", 0.0),
                                pressure = point.finiteDouble("pressure", 1.0).coerceIn(0.0, 1.0),timestamp=point.optLong("timestamp"),
                                tilt=point.finiteDouble("tilt", 0.0),orientation=point.finiteDouble("orientation", 0.0)
                            )
                        )
                    }
                }
            },
            toolConfig = RemoteToolConfig(
                type = tool.optString("type", "PEN"),
                style = tool.optString("style", "SOLID"),
                colorArgb = tool.optLong("colorArgb", 0xFF000000L),
                baseWidth = tool.finiteDouble("baseWidth", 4.0).coerceIn(0.1, 200.0),
                opacity = tool.finiteDouble("opacity", 1.0).coerceIn(0.0, 1.0),
                usePressure = tool.optBoolean("usePressure", true),settingsJson=tool.optJSONObject("settings")?.toString()
            ),
            createdAt = optLong("createdAt", 0L),pageId=optString("pageId","page-1")
        )
    }

    private fun JSONObject.toRemoteCanvasStateOrNull(): RemoteCanvasState? {
        val strokesArray = optJSONArray("strokes") ?: JSONArray()
        return RemoteCanvasState(
            userId = optString("userId").takeUnless { it.isBlank() } ?: return null,
            strokes = buildList {
                repeat(strokesArray.length()) { index ->
                    strokesArray.optJSONObject(index)
                        ?.toRemoteStrokeOrNull()
                        ?.let(::add)
                }
            },
            updatedAt = optLong("updatedAt", System.currentTimeMillis())
        )
    }

    // Older relays omit stylus metadata. optDouble without a fallback produces NaN,
    // which renders initially but makes the received document impossible to save.
    private fun JSONObject.finiteDouble(key: String, fallback: Double): Double =
        optDouble(key, fallback).takeIf { it.isFinite() } ?: fallback

    private fun normalizeRoomCode(input: String): String {
        return input
            .trim()
            .substringAfter("room=", input)
            .substringBefore("&")
            .substringAfterLast("/")
            .substringBefore("?").substringBefore("#")
            .trim()
    }

    private fun String.urlEncoded(): String {
        return URLEncoder.encode(this, "UTF-8").replace("+", "%20")
    }

    private fun Response.toCollaborationError(): Throwable {
        return when (code) {
            429 -> IllegalStateException("Sorry, this room is full. Please create a new room.")
            403 -> IllegalStateException("You do not have permission to join this room.")
            404 -> IllegalStateException("This room could not be found.")
            else -> IllegalStateException("Could not connect to collaboration room. Please try again.")
        }
    }
}
