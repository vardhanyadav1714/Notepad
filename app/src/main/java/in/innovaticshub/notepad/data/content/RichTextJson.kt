package `in`.innovaticshub.notepad.data.content

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON serialization for [RichTextData]. Wire format:
 *
 * ```
 * {"text":"Hello","spans":[{"s":0,"e":5,"t":"BOLD"},
 *                          {"s":0,"e":5,"t":"COLOR","a":-65536},
 *                          {"s":0,"e":5,"t":"SIZE","px":18}]}
 * ```
 *
 * Spans are the authoritative model; readers clamp out-of-range values so a
 * malformed file can never crash the editor.
 */
object RichTextJson {

    fun toJson(data: RichTextData): String {
        val spans = JSONArray()
        data.spans.forEach { span ->
            spans.put(
                JSONObject().apply {
                    put("s", span.start)
                    put("e", span.end)
                    put("t", span.kind.name)
                    span.argb?.let { put("a", it) }
                    span.sizePx?.let { put("px", it) }
                }
            )
        }
        return JSONObject().apply {
            put("text", data.text)
            put("spans", spans)
        }.toString()
    }

    fun fromJson(json: String?): RichTextData {
        if (json.isNullOrBlank()) return RichTextData.EMPTY
        return runCatching {
            val root = JSONObject(json)
            val text = root.optString("text")
            val spans = mutableListOf<RichSpan>()
            val array = root.optJSONArray("spans") ?: JSONArray()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val kind = runCatching { SpanKind.valueOf(item.getString("t")) }.getOrNull()
                    ?: continue
                spans.add(
                    RichSpan(
                        start = item.optInt("s").coerceIn(0, text.length),
                        end = item.optInt("e").coerceIn(0, text.length),
                        kind = kind,
                        argb = if (item.has("a")) item.getInt("a") else null,
                        sizePx = if (item.has("px")) item.getInt("px") else null
                    )
                )
            }
            RichTextData(text, spans)
        }.getOrDefault(RichTextData.EMPTY)
    }
}
