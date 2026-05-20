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
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class CollaborationRepository {
    private val websocketBaseUrl = "wss://notepad-collab.vinayyadav010010001.workers.dev/ws"
    private val client = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val socketLock = Any()

    private var webSocket: WebSocket? = null
    private var socketOpen = false
    private var activeRoomCode: String? = null

    val currentUserId: String = "local-${UUID.randomUUID()}"

    suspend fun createRoom(): String {
        return System.currentTimeMillis().toString()
    }

    suspend fun joinRoom(roomCode: String): String {
        return normalizeRoomCode(roomCode)
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
                if (socket != null && socketOpen) {
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
        onConnected: () -> Unit,
        onError: (Throwable) -> Unit
    ): CollaborationSubscription {
        val normalized = normalizeRoomCode(roomCode)
        val closedByUser = AtomicBoolean(false)

        synchronized(socketLock) {
            webSocket?.close(1000, "Switching rooms")
            webSocket = null
            socketOpen = false
            activeRoomCode = normalized
        }

        fun connect(): WebSocket {
            val request = Request.Builder()
                .url("$websocketBaseUrl/${normalized.urlEncoded()}")
                .build()

            return client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    synchronized(socketLock) {
                        this@CollaborationRepository.webSocket = webSocket
                        socketOpen = true
                    }
                    webSocket.send(
                        JSONObject()
                            .put("type", "join")
                            .put("userId", currentUserId)
                            .toString()
                    )
                    scope.launch(Dispatchers.Main) {
                        onConnected()
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    runCatching {
                        text.toRemoteStrokes()
                    }.onSuccess { strokes ->
                        if (strokes.isNotEmpty()) {
                            scope.launch(Dispatchers.Main) {
                                onStrokes(strokes)
                            }
                        }
                    }.onFailure { error ->
                        scope.launch(Dispatchers.Main) {
                            onError(error)
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val shouldReconnect = synchronized(socketLock) {
                        if (this@CollaborationRepository.webSocket == webSocket) {
                            socketOpen = false
                        }
                        !closedByUser.get() && activeRoomCode == normalized
                    }

                    if (shouldReconnect) {
                        scope.launch {
                            kotlinx.coroutines.delay(1200)
                            if (!closedByUser.get()) {
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
                        onError(t)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    val shouldReconnect = synchronized(socketLock) {
                        if (this@CollaborationRepository.webSocket == webSocket) {
                            this@CollaborationRepository.webSocket = null
                            socketOpen = false
                        }
                        !closedByUser.get() && code != 1000 && activeRoomCode == normalized
                    }

                    if (shouldReconnect) {
                        scope.launch {
                            kotlinx.coroutines.delay(1200)
                            if (!closedByUser.get()) {
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
            )
        }

        val socket = connect()

        synchronized(socketLock) {
            webSocket = socket
        }

        return CollaborationSubscription {
            closedByUser.set(true)
            val socketToClose = synchronized(socketLock) {
                val current = webSocket
                webSocket = null
                socketOpen = false
                activeRoomCode = null
                current
            }
            socketToClose?.close(1000, "Leaving room")
        }
    }

    private fun String.toRemoteStrokes(): List<RemoteStroke> {
        val json = JSONObject(this)
        return when (json.optString("type")) {
            "room_snapshot" -> {
                val strokes = json.optJSONArray("strokes") ?: JSONArray()
                buildList {
                    repeat(strokes.length()) { index ->
                        strokes.optJSONObject(index)
                            ?.toRemoteStrokeOrNull()
                            ?.let(::add)
                    }
                }
            }
            "stroke" -> listOfNotNull(json.optJSONObject("stroke")?.toRemoteStrokeOrNull())
            "error" -> error(json.optString("message", "Collaboration server error."))
            else -> emptyList()
        }
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
            )
            .put("createdAt", createdAt)
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
                                x = point.optDouble("x"),
                                y = point.optDouble("y"),
                                pressure = point.optDouble("pressure", 1.0)
                            )
                        )
                    }
                }
            },
            toolConfig = RemoteToolConfig(
                type = tool.optString("type", "PEN"),
                style = tool.optString("style", "SOLID"),
                colorArgb = tool.optLong("colorArgb", 0xFF000000L),
                baseWidth = tool.optDouble("baseWidth", 4.0),
                opacity = tool.optDouble("opacity", 1.0),
                usePressure = tool.optBoolean("usePressure", true)
            ),
            createdAt = optLong("createdAt", 0L)
        )
    }

    private fun normalizeRoomCode(input: String): String {
        return input
            .substringAfter("room=", input)
            .substringBefore("&")
            .substringAfterLast("/")
            .trim()
    }

    private fun String.urlEncoded(): String {
        return URLEncoder.encode(this, "UTF-8").replace("+", "%20")
    }
}
