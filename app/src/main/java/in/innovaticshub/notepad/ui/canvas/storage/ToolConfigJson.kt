package `in`.innovaticshub.notepad.ui.canvas.storage

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import `in`.innovaticshub.notepad.ui.canvas.model.*
import org.json.JSONObject

/** One representation for pen settings in documents, favourites and collaboration. */
object ToolConfigJson {
    fun encode(tool: ToolConfig): JSONObject = JSONObject().apply {
        put("type",tool.type.name); put("style",tool.style.name)
        put("color",tool.color.toArgb().toLong() and 0xffffffffL)
        put("width",tool.baseWidth.toDouble()); put("opacity",tool.opacity.toDouble()); put("pressure",tool.usePressure)
        put("sensitivity",tool.pressureSensitivity.toDouble()); put("smoothing",tool.smoothing.toDouble())
        put("nibAngle",tool.nibAngle.toDouble()); put("eraserMode",tool.eraserMode.name)
        put("highlighterOnly",tool.eraseHighlighterOnly); put("shape",tool.shape.name); put("constrain",tool.constrainShape)
    }
    fun decode(json: JSONObject): ToolConfig {
        val base=ToolConfig.defaultForType(enumValue(json.optString("type"), ToolType.PEN))
        return base.copy(style=enumValue(json.optString("style"),base.style),
            color=if(json.has("color")) Color(json.optLong("color").toInt()) else base.color,
            baseWidth=json.optDouble("width",base.baseWidth.toDouble()).toFloat().finite(base.baseWidth,0.1f,200f),
            opacity=json.optDouble("opacity",base.opacity.toDouble()).toFloat().finite(base.opacity,0f,1f),
            usePressure=json.optBoolean("pressure",base.usePressure),
            pressureSensitivity=json.optDouble("sensitivity",1.0).toFloat().finite(1f,0f,1f),
            smoothing=json.optDouble("smoothing",0.55).toFloat().finite(0.55f,0f,1f),
            nibAngle=json.optDouble("nibAngle",45.0).toFloat().finite(45f,0f,180f),
            eraserMode=enumValue(json.optString("eraserMode"),EraserMode.SEGMENT),
            eraseHighlighterOnly=json.optBoolean("highlighterOnly"),
            shape=enumValue(json.optString("shape"),ShapeKind.LINE),constrainShape=json.optBoolean("constrain"))
    }
    private inline fun <reified T:Enum<T>> enumValue(name:String, fallback:T):T = enumValues<T>().firstOrNull { it.name==name } ?: fallback
    private fun Float.finite(fallback:Float,min:Float,max:Float)=if(isFinite()) coerceIn(min,max) else fallback
}
