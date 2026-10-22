package `in`.innovaticshub.notepad.ui.canvas.collab

data class RemoteStrokePoint(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val pressure: Double = 1.0,
    val timestamp: Long = 0,
    val tilt: Double = 0.0,
    val orientation: Double = 0.0
)

data class RemoteToolConfig(
    val type: String = "PEN",
    val style: String = "SOLID",
    val colorArgb: Long = 0xFF000000L,
    val baseWidth: Double = 4.0,
    val opacity: Double = 1.0,
    val usePressure: Boolean = true,
    val settingsJson: String? = null
)

data class RemoteStroke(
    val id: String = "",
    val userId: String = "",
    val points: List<RemoteStrokePoint> = emptyList(),
    val toolConfig: RemoteToolConfig = RemoteToolConfig(),
    val createdAt: Long = 0L,
    val pageId: String = "page-1"
)

data class RemoteCanvasState(
    val userId: String = "",
    val strokes: List<RemoteStroke> = emptyList(),
    val updatedAt: Long = 0L
)

/** Session-only identity. Never stored in the drawing or used as an account. */
data class RoomMember(
    val id: String,
    val userId: String,
    val label: String,
    val colorArgb: Long,
    val activity: String = "idle",
    val cursor: MemberCursor? = null,
    val updatedAt: Long = 0L,
    val isSelf: Boolean = false,
    val receivedAt: Long = System.currentTimeMillis()
)

data class MemberCursor(val x: Float, val y: Float, val pageId: String)

data class LiveStrokePreview(val memberId: String, val stroke: RemoteStroke?, val receivedAt: Long = System.currentTimeMillis())
