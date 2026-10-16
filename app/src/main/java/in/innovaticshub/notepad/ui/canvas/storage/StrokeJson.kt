package `in`.innovaticshub.notepad.ui.canvas.storage

import `in`.innovaticshub.notepad.ui.canvas.model.Stroke
import `in`.innovaticshub.notepad.ui.canvas.model.StrokeBuilder
import `in`.innovaticshub.notepad.ui.canvas.model.StrokePoint
import `in`.innovaticshub.notepad.ui.canvas.model.ToolConfig
import `in`.innovaticshub.notepad.ui.canvas.model.ToolType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single source of truth for stroke-list serialization. The wire format is
 * identical to the legacy canvas autosave: one object per stroke with
 * id/type/color/width/opacity/pressure plus `[[x,y,pressure,timestamp],...]` points,
 * with full floating-point coordinates, pressure, tilt, and orientation.
 */
object StrokeJson {

    fun toJSONArray(strokes: List<Stroke>): JSONArray = JSONArray().apply {
        strokes.forEach { stroke ->
            put(
                JSONObject().apply {
                    put("id", stroke.id)
                    put("type", stroke.toolConfig.type.name)
                    put("color", argb(stroke.toolConfig.color))
                    put("width", stroke.toolConfig.baseWidth.toDouble())
                    put("opacity", stroke.toolConfig.opacity.toDouble())
                    put("pressure", stroke.toolConfig.usePressure)
                    put("tool", ToolConfigJson.encode(stroke.toolConfig))
                    put("pageId", stroke.pageId)
                    put("points", pointsToJson(stroke.points))
                }
            )
        }
    }

    fun fromJSONArray(array: JSONArray): List<Stroke> =
        (0 until array.length()).mapNotNull { index ->
            runCatching { parseStroke(array.getJSONObject(index)) }.getOrNull()
        }

    fun strokesToJson(strokes: List<Stroke>): String = toJSONArray(strokes).toString()

    fun strokesFromJson(json: String): List<Stroke> =
        runCatching { fromJSONArray(if (json.trimStart().startsWith("[")) JSONArray(json) else JSONObject(json).getJSONArray("strokes")) }.getOrDefault(emptyList())

    private fun parseStroke(item: JSONObject): Stroke? {
        val type = ToolType.valueOf(item.getString("type"))
        val base = ToolConfig.defaultForType(type)
        val tool = item.optJSONObject("tool")?.let(ToolConfigJson::decode) ?: base.copy(
            color = optColor(item, "color") ?: base.color,
            baseWidth = item.optDouble("width", base.baseWidth.toDouble()).toFloat(),
            opacity = item.optDouble("opacity", base.opacity.toDouble())
                .toFloat()
                .coerceIn(0.05f, 1f),
            usePressure = item.optBoolean("pressure", base.usePressure)
        )
        val builder = StrokeBuilder(tool, minPointDistance = 0f)
        val points = item.optJSONArray("points") ?: JSONArray()
        for (p in 0 until points.length()) {
            val coords = points.getJSONArray(p)
            builder.addPoint(
                StrokePoint(
                    x = coords.getDouble(0).toFloat(),
                    y = coords.getDouble(1).toFloat(),
                    pressure = coords.getDouble(2).toFloat(),
                    timestamp = coords.optLong(3, 0L),
                    tilt = coords.optDouble(4, 0.0).toFloat(),
                    orientation = coords.optDouble(5, 0.0).toFloat()
                )
            )
        }
        if (builder.isEmpty) return null
        return builder.build().copy(id = item.optString("id").ifBlank { Stroke.generateId() }, pageId = item.optString("pageId","page-1"))
    }

    private fun pointsToJson(points: List<StrokePoint>): JSONArray = JSONArray().apply {
        points.forEach { point ->
            put(
                JSONArray().apply {
                    put(point.x.toDouble())
                    put(point.y.toDouble())
                    put(point.pressure.toDouble())
                    put(point.timestamp)
                    put(point.tilt.toDouble())
                    put(point.orientation.toDouble())
                }
            )
        }
    }

    private fun argb(color: Color): Long = color.toArgb().toLong() and 0xFFFFFFFFL

    private fun optColor(obj: JSONObject, key: String): Color? =
        if (obj.has(key)) Color(obj.getLong(key).toInt()) else null

}
