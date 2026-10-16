package `in`.innovaticshub.notepad.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import `in`.innovaticshub.notepad.data.content.SpanKind
import `in`.innovaticshub.notepad.ui.canvas.model.*

/** Display-only paper/ink adaptation. Stored notes, exports and shared strokes stay unchanged. */
data class NoteAppearance(val dark:Boolean) {
    private fun neutral(color:Color)=maxOf(color.red,color.green,color.blue)-minOf(color.red,color.green,color.blue)<0.035f
    fun paper(source:Color,pdf:Boolean=false):Color {
        if(pdf || !neutral(source)) return source
        val level=(source.red+source.green+source.blue)/3
        return if(level<0.22f || level>0.88f) if(dark) Color.Black else Color.White else source
    }
    fun ink(source:Color,pdf:Boolean=false):Color {
        if(pdf || !neutral(source)) return source
        val level=(source.red+source.green+source.blue)/3
        return when {
            dark && level<0.25f -> Color(0xFFECECEF).copy(alpha=source.alpha)
            !dark && level>0.85f -> Color(0xFF202124).copy(alpha=source.alpha)
            else -> source
        }
    }
    fun stroke(source:Stroke,pdf:Boolean=false):Stroke {
        val color=ink(source.toolConfig.color,pdf)
        return if(color==source.toolConfig.color) source else source.copy(toolConfig=source.toolConfig.copy(color=color))
    }
    fun text(source:PageText,pdf:Boolean=false):PageText {
        if(pdf) return source
        val spans=source.content.spans.map {span->if(span.kind==SpanKind.COLOR && span.argb!=null) span.copy(argb=ink(Color(span.argb)).toArgb()) else span}
        val color=ink(Color(source.color)).toArgb()
        return if(color==source.color && spans==source.content.spans) source else source.copy(color=color,content=source.content.copy(spans=spans))
    }
    val surround:Color get()=if(dark) Color(0xFF303033) else Color(0xFFEDEDEF)
}

val LocalNoteAppearance=staticCompositionLocalOf {NoteAppearance(false)}
