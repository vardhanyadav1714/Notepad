package `in`.innovaticshub.notepad.data.content

import org.json.JSONObject

/**
 * Per-type payload serialization for `NoteBlockEntity.metadata`. Unknown or
 * malformed metadata parses to null so a bad row never crashes the editor.
 */
object BlockMetadataJson {

    fun imageToJson(payload: ImagePayload): String =
        JSONObject()
            .put("path", payload.path)
            .put("width", payload.width)
            .put("height", payload.height)
            .toString()

    fun imageFrom(json: String?): ImagePayload? = runCatching {
        val obj = JSONObject(json ?: return null)
        ImagePayload(
            path = obj.getString("path"),
            width = obj.optInt("width"),
            height = obj.optInt("height")
        )
    }.getOrNull()

    fun drawingToJson(payload: DrawingPayload): String =
        JSONObject()
            .put("strokesFile", payload.strokesFile)
            .put("backgroundArgb", payload.backgroundArgb)
            .apply { payload.collabRoom?.let { put("collabRoom", it) } }
            .toString()

    fun drawingFrom(json: String?): DrawingPayload? = runCatching {
        val obj = JSONObject(json ?: return null)
        DrawingPayload(
            strokesFile = obj.getString("strokesFile"),
            backgroundArgb = obj.optInt("backgroundArgb", 0xFFFFFFFF.toInt()),
            collabRoom = obj.optString("collabRoom").takeIf { it.isNotBlank() }
        )
    }.getOrNull()

    fun pdfToJson(payload: PdfPayload): String =
        JSONObject()
            .put("file", payload.file)
            .put("pageCount", payload.pageCount)
            .put("pageWidthPt", payload.pageWidthPt.toDouble())
            .put("pageHeightPt", payload.pageHeightPt.toDouble())
            .toString()

    fun pdfFrom(json: String?): PdfPayload? = runCatching {
        val obj = JSONObject(json ?: return null)
        PdfPayload(
            file = obj.getString("file"),
            pageCount = obj.optInt("pageCount"),
            pageWidthPt = obj.optDouble("pageWidthPt", 595.0).toFloat(),
            pageHeightPt = obj.optDouble("pageHeightPt", 842.0).toFloat()
        )
    }.getOrNull()

    fun recordingToJson(payload: RecordingPayload): String =
        JSONObject()
            .put("path", payload.path)
            .put("durationMs", payload.durationMs)
            .toString()

    fun recordingFrom(json: String?): RecordingPayload? = runCatching {
        val obj = JSONObject(json ?: return null)
        RecordingPayload(
            path = obj.getString("path"),
            durationMs = obj.optLong("durationMs")
        )
    }.getOrNull()
}
