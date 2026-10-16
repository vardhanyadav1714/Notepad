package `in`.innovaticshub.notepad.ui.canvas.storage

import `in`.innovaticshub.notepad.data.content.RichTextJson
import `in`.innovaticshub.notepad.data.content.TextAlign
import `in`.innovaticshub.notepad.ui.canvas.model.*
import org.json.JSONArray
import org.json.JSONObject

internal object PageContentJson {
    fun texts(items:List<PageText>)=JSONArray().apply {items.forEach {t->put(JSONObject().apply {
        put("id",t.id);put("page",t.pageId);put("x",t.x.toDouble());put("y",t.y.toDouble());put("width",t.width.toDouble());put("height",t.height.toDouble())
        put("rich",JSONObject(RichTextJson.toJson(t.content)));put("font",t.fontSize.toDouble());put("color",t.color);put("align",t.alignment.name)
        t.checked?.let {put("checked",it)}
    })} }
    fun images(items:List<PageImage>)=JSONArray().apply {items.forEach {t->put(JSONObject().apply {
        put("id",t.id);put("page",t.pageId);put("path",t.path);put("x",t.x.toDouble());put("y",t.y.toDouble());put("width",t.width.toDouble());put("height",t.height.toDouble())
        put("rotation",t.rotation.toDouble());put("cropLeft",t.cropLeft.toDouble());put("cropTop",t.cropTop.toDouble());put("cropRight",t.cropRight.toDouble());put("cropBottom",t.cropBottom.toDouble())
    })} }
    fun audios(items:List<PageAudio>)=JSONArray().apply {items.forEach {t->put(JSONObject().apply {put("id",t.id);put("path",t.path);put("duration",t.durationMs)})} }
    private fun JSONObject.number(key:String,fallback:Float,min:Float=0f,max:Float=100000f):Float =
        optDouble(key,fallback.toDouble()).toFloat().let {if(it.isFinite()) it.coerceIn(min,max) else fallback}
    fun readTexts(a:JSONArray?)=if(a==null) emptyList() else (0 until a.length()).map {index->a.getJSONObject(index).let {t->
        PageText(t.getString("id"),t.optString("page","page-1"),t.number("x",48f,-100000f),t.number("y",64f,-100000f),t.number("width",904f,40f,10000f),t.number("height",56f,20f),
            RichTextJson.fromJson(t.optJSONObject("rich")?.toString()),t.number("font",40f,12f,200f),t.optInt("color",0xFF202124.toInt()),
            TextAlign.entries.firstOrNull {it.name==t.optString("align")} ?: TextAlign.LEFT,if(t.has("checked")) t.getBoolean("checked") else null)
    } }.distinctBy {it.id}
    fun readImages(a:JSONArray?)=if(a==null) emptyList() else (0 until a.length()).map {index->a.getJSONObject(index).let {t->
        val left=t.number("cropLeft",0f,0f,0.95f);val top=t.number("cropTop",0f,0f,0.95f)
        PageImage(t.getString("id"),t.optString("page","page-1"),t.getString("path"),t.number("x",48f,-100000f),t.number("y",64f,-100000f),t.number("width",600f,20f,10000f),t.number("height",400f,20f,10000f),
            t.number("rotation",0f,-360f,360f),left,top,t.number("cropRight",1f,left+0.05f,1f),t.number("cropBottom",1f,top+0.05f,1f))
    } }.distinctBy {it.id}
    fun readAudios(a:JSONArray?)=if(a==null) emptyList() else (0 until a.length()).map {index->a.getJSONObject(index).let {PageAudio(it.getString("id"),it.getString("path"),it.optLong("duration"))} }.distinctBy {it.id}
}
