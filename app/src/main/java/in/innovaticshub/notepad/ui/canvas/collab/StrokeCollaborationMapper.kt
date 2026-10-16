package `in`.innovaticshub.notepad.ui.canvas.collab

import androidx.compose.ui.graphics.Color
import `in`.innovaticshub.notepad.ui.canvas.model.DrawingStyle
import `in`.innovaticshub.notepad.ui.canvas.model.Stroke
import `in`.innovaticshub.notepad.ui.canvas.model.StrokeBuilder
import `in`.innovaticshub.notepad.ui.canvas.model.StrokePoint
import `in`.innovaticshub.notepad.ui.canvas.model.ToolConfig
import `in`.innovaticshub.notepad.ui.canvas.model.ToolType
import `in`.innovaticshub.notepad.ui.canvas.storage.ToolConfigJson
import org.json.JSONObject

fun Stroke.toRemoteStroke(userId: String): RemoteStroke {
    return RemoteStroke(
        // Stroke IDs are UUIDs already. Keep received legacy IDs too; repeatedly prefixing
        // them on canvas updates breaks deduplication and per-participant undo history.
        id = id,
        userId = userId,
        points = points.map {
            RemoteStrokePoint(
                x = it.x.toDouble(),
                y = it.y.toDouble(),
                pressure = it.pressure.toDouble(),
                timestamp=it.timestamp,tilt=it.tilt.toDouble(),orientation=it.orientation.toDouble()
            )
        },
        toolConfig = toolConfig.toRemoteToolConfig(),
        createdAt = System.currentTimeMillis(),pageId=pageId
    )
}

fun RemoteStroke.toLocalStroke(): Stroke {
    val tool = toolConfig.toLocalToolConfig()
    val builder = StrokeBuilder(tool, minPointDistance = 0f)
    points.forEach {
        builder.addPoint(
            StrokePoint(
                x = it.x.toFloat(),
                y = it.y.toFloat(),
                pressure = it.pressure.toFloat(),timestamp=it.timestamp,tilt=it.tilt.toFloat(),orientation=it.orientation.toFloat()
            )
        )
    }
    return builder.build().copy(id = id,pageId=pageId)
}

private fun ToolConfig.toRemoteToolConfig(): RemoteToolConfig {
    return RemoteToolConfig(
        type = type.name,
        style = style.name,
        colorArgb = color.toArgbLong(),
        baseWidth = baseWidth.toDouble(),
        opacity = opacity.toDouble(),
        usePressure = usePressure,settingsJson=ToolConfigJson.encode(this).toString()
    )
}

private fun RemoteToolConfig.toLocalToolConfig(): ToolConfig {
    settingsJson?.let { json -> runCatching { ToolConfigJson.decode(JSONObject(json)) }.getOrNull()?.let {return it} }
    return ToolConfig(
        type = enumValueOrDefault(type, ToolType.PEN),
        style = enumValueOrDefault(style, DrawingStyle.SOLID),
        color = colorArgb.toColor(),
        baseWidth = baseWidth.toFloat(),
        opacity = opacity.toFloat(),
        usePressure = usePressure
    )
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(
    name: String,
    fallback: T
): T {
    return runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
}

private fun Color.toArgbLong(): Long {
    val alpha = (alpha * 255).toInt().coerceIn(0, 255)
    val red = (red * 255).toInt().coerceIn(0, 255)
    val green = (green * 255).toInt().coerceIn(0, 255)
    val blue = (blue * 255).toInt().coerceIn(0, 255)
    return ((alpha shl 24) or (red shl 16) or (green shl 8) or blue).toLong()
}

private fun Long.toColor(): Color {
    val value = toInt()
    val alpha = (value ushr 24 and 0xFF) / 255f
    val red = (value ushr 16 and 0xFF) / 255f
    val green = (value ushr 8 and 0xFF) / 255f
    val blue = (value and 0xFF) / 255f
    return Color(red, green, blue, alpha)
}
