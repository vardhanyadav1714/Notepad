package `in`.innovaticshub.notepad.ui.canvas.collab

import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

class CollaborationRepository {
    private val projectId = "notepad-d4681"
    private val apiKey = "AIzaSyB0nBpBk1hO1Tr4Na1dmELoke0f68nmnZs"
    private val baseUrl = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val currentUserId: String = "local-${UUID.randomUUID()}"

    suspend fun createRoom(): String {
        val roomCode = System.currentTimeMillis().toString()
        writeDocument(
            path = "canvasRooms/${roomCode.urlEncoded()}",
            body = documentJson(
                "ownerId" to stringValue(currentUserId),
                "createdAt" to integerValue(System.currentTimeMillis()),
                "updatedAt" to integerValue(System.currentTimeMillis()),
                "active" to booleanValue(true)
            )
        )
        return roomCode
    }

    suspend fun joinRoom(roomCode: String): String {
        val normalized = normalizeRoomCode(roomCode)
        writeDocument(
            path = "canvasRooms/${normalized.urlEncoded()}/participants/${currentUserId.urlEncoded()}",
            body = documentJson(
                "userId" to stringValue(currentUserId),
                "joinedAt" to integerValue(System.currentTimeMillis()),
                "lastSeen" to integerValue(System.currentTimeMillis())
            )
        )
        return normalized
    }

    suspend fun uploadStroke(roomCode: String, stroke: RemoteStroke) {
        if (stroke.id.isBlank() || roomCode.isBlank()) return
        writeDocument(
            path = "canvasRooms/${roomCode.urlEncoded()}/strokes/${stroke.id.urlEncoded()}",
            body = stroke.toDocumentJson()
        )
    }

    fun listenToStrokes(
        roomCode: String,
        onStrokes: (List<RemoteStroke>) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenerRegistration {
        val normalized = normalizeRoomCode(roomCode)
        val job = scope.launch {
            var lastDeliveredSignature = ""
            while (isActive) {
                runCatching {
                    listStrokes(normalized)
                }.onSuccess { strokes ->
                    val signature = strokes.joinToString("|") { it.id }
                    if (signature != lastDeliveredSignature) {
                        lastDeliveredSignature = signature
                        withContext(Dispatchers.Main) {
                            onStrokes(strokes)
                        }
                    }
                }.onFailure { error ->
                    withContext(Dispatchers.Main) {
                        onError(error)
                    }
                }
                delay(850)
            }
        }

        return ListenerRegistration { job.cancel() }
    }

    private suspend fun listStrokes(roomCode: String): List<RemoteStroke> {
        val response = request(
            method = "GET",
            path = "canvasRooms/${roomCode.urlEncoded()}/strokes",
            body = null
        )
        val documents = response.optJSONArray("documents") ?: JSONArray()
        return buildList {
            repeat(documents.length()) { index ->
                documents.optJSONObject(index)
                    ?.optJSONObject("fields")
                    ?.toRemoteStrokeOrNull()
                    ?.let(::add)
            }
        }.sortedBy { it.createdAt }
    }

    private suspend fun writeDocument(path: String, body: JSONObject) {
        request(
            method = "PATCH",
            path = path,
            body = body
        )
    }

    private suspend fun request(method: String, path: String, body: JSONObject?): JSONObject {
        return withContext(Dispatchers.IO) {
            val separator = if (path.contains("?")) "&" else "?"
            val url = URL("$baseUrl/$path${separator}key=$apiKey")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Content-Type", "application/json")
                if (body != null) {
                    doOutput = true
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(body.toString())
                    }
                }
            }

            val responseCode = connection.responseCode
            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            if (responseCode !in 200..299) {
                val message = runCatching {
                    JSONObject(responseText)
                        .optJSONObject("error")
                        ?.optString("message")
                }.getOrNull().takeUnless { it.isNullOrBlank() }
                    ?: "Firestore REST request failed with HTTP $responseCode."
                error(message)
            }

            if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
        }
    }

    private fun RemoteStroke.toDocumentJson(): JSONObject {
        return documentJson(
            "id" to stringValue(id),
            "userId" to stringValue(userId),
            "points" to arrayValue(
                points.map { point ->
                    mapValue(
                        "x" to doubleValue(point.x),
                        "y" to doubleValue(point.y),
                        "pressure" to doubleValue(point.pressure)
                    )
                }
            ),
            "toolConfig" to mapValue(
                "type" to stringValue(toolConfig.type),
                "style" to stringValue(toolConfig.style),
                "colorArgb" to integerValue(toolConfig.colorArgb),
                "baseWidth" to doubleValue(toolConfig.baseWidth),
                "opacity" to doubleValue(toolConfig.opacity),
                "usePressure" to booleanValue(toolConfig.usePressure)
            ),
            "createdAt" to integerValue(createdAt)
        )
    }

    private fun JSONObject.toRemoteStrokeOrNull(): RemoteStroke? {
        val tool = optJSONObject("toolConfig")
            ?.optJSONObject("mapValue")
            ?.optJSONObject("fields")
            ?: return null
        val pointsArray = optJSONObject("points")
            ?.optJSONObject("arrayValue")
            ?.optJSONArray("values")
            ?: JSONArray()

        return RemoteStroke(
            id = optStringValue("id") ?: return null,
            userId = optStringValue("userId") ?: "",
            points = buildList {
                repeat(pointsArray.length()) { index ->
                    val point = pointsArray
                        .optJSONObject(index)
                        ?.optJSONObject("mapValue")
                        ?.optJSONObject("fields")
                    if (point != null) {
                        add(
                            RemoteStrokePoint(
                                x = point.optDoubleValue("x"),
                                y = point.optDoubleValue("y"),
                                pressure = point.optDoubleValue("pressure", 1.0)
                            )
                        )
                    }
                }
            },
            toolConfig = RemoteToolConfig(
                type = tool.optStringValue("type") ?: "PEN",
                style = tool.optStringValue("style") ?: "SOLID",
                colorArgb = tool.optLongValue("colorArgb", 0xFF000000L),
                baseWidth = tool.optDoubleValue("baseWidth", 4.0),
                opacity = tool.optDoubleValue("opacity", 1.0),
                usePressure = tool.optBooleanValue("usePressure", true)
            ),
            createdAt = optLongValue("createdAt", 0L)
        )
    }

    private fun documentJson(vararg fields: Pair<String, JSONObject>): JSONObject {
        return JSONObject().put("fields", mapValue(*fields).getJSONObject("mapValue").getJSONObject("fields"))
    }

    private fun mapValue(vararg fields: Pair<String, JSONObject>): JSONObject {
        val jsonFields = JSONObject()
        fields.forEach { (key, value) -> jsonFields.put(key, value) }
        return JSONObject().put("mapValue", JSONObject().put("fields", jsonFields))
    }

    private fun arrayValue(values: List<JSONObject>): JSONObject {
        val array = JSONArray()
        values.forEach(array::put)
        return JSONObject().put("arrayValue", JSONObject().put("values", array))
    }

    private fun stringValue(value: String): JSONObject = JSONObject().put("stringValue", value)

    private fun booleanValue(value: Boolean): JSONObject = JSONObject().put("booleanValue", value)

    private fun integerValue(value: Long): JSONObject = JSONObject().put("integerValue", value.toString())

    private fun doubleValue(value: Double): JSONObject = JSONObject().put("doubleValue", value)

    private fun JSONObject.optStringValue(key: String): String? {
        return optJSONObject(key)?.optString("stringValue")?.takeUnless { it.isBlank() }
    }

    private fun JSONObject.optLongValue(key: String, fallback: Long): Long {
        val value = optJSONObject(key) ?: return fallback
        return value.optString("integerValue").toLongOrNull()
            ?: value.optDouble("doubleValue", fallback.toDouble()).toLong()
    }

    private fun JSONObject.optDoubleValue(key: String, fallback: Double = 0.0): Double {
        val value = optJSONObject(key) ?: return fallback
        return value.optDouble("doubleValue", Double.NaN).takeUnless { it.isNaN() }
            ?: value.optString("integerValue").toDoubleOrNull()
            ?: fallback
    }

    private fun JSONObject.optBooleanValue(key: String, fallback: Boolean): Boolean {
        return optJSONObject(key)?.optBoolean("booleanValue", fallback) ?: fallback
    }

    private fun normalizeRoomCode(input: String): String {
        return input
            .substringAfter("room=", input)
            .substringBefore("&")
            .trim()
    }

    private fun String.urlEncoded(): String {
        return URLEncoder.encode(this, "UTF-8").replace("+", "%20")
    }
}
