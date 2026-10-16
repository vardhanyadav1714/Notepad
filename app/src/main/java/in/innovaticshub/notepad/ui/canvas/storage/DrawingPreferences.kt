package `in`.innovaticshub.notepad.ui.canvas.storage

import android.content.Context
import `in`.innovaticshub.notepad.ui.canvas.model.*
import org.json.JSONArray

class DrawingPreferences(context:Context) {
    private val prefs=context.applicationContext.getSharedPreferences("drawing_tools",Context.MODE_PRIVATE)
    fun favourites():List<ToolConfig> = runCatching {
        val array=JSONArray(prefs.getString("favourites","[]"))
        (0 until array.length()).map { ToolConfigJson.decode(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())
    fun saveFavourites(tools:List<ToolConfig>) { prefs.edit().putString("favourites",JSONArray().apply { tools.forEach { put(ToolConfigJson.encode(it)) } }.toString()).apply() }
    fun rememberedTools():Map<ToolType,ToolConfig> = runCatching {
        val array=JSONArray(prefs.getString("tools","[]"))
        (0 until array.length()).map { ToolConfigJson.decode(array.getJSONObject(it)) }.associateBy { it.type }
    }.getOrDefault(emptyMap())
    fun saveTools(tools:Collection<ToolConfig>) { prefs.edit().putString("tools",JSONArray().apply { tools.forEach { put(ToolConfigJson.encode(it)) } }.toString()).apply() }
    fun penPresets():List<ToolConfig> = runCatching {
        val array=JSONArray(prefs.getString("pen_presets","[]"))
        (0 until array.length()).map { ToolConfigJson.decode(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())
    fun savePenPresets(tools:Collection<ToolConfig>) { prefs.edit().putString("pen_presets",JSONArray().apply {tools.forEach {put(ToolConfigJson.encode(it))}}.toString()).apply() }
    var fingerDrawing:Boolean
        get()=prefs.getBoolean("fingerDrawing",true)
        set(value) { prefs.edit().putBoolean("fingerDrawing",value).apply() }
    var zoomLocked:Boolean
        get()=prefs.getBoolean("zoomLocked",false)
        set(value) {prefs.edit().putBoolean("zoomLocked",value).apply()}
}
