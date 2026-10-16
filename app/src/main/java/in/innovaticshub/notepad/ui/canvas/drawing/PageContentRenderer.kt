package `in`.innovaticshub.notepad.ui.canvas.drawing

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.drawscope.Stroke as OutlineStroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import `in`.innovaticshub.notepad.data.content.SpanKind
import `in`.innovaticshub.notepad.data.content.TextAlign
import `in`.innovaticshub.notepad.ui.canvas.model.*
import java.io.File

/** Layout in document units, shared by the live page, thumbnails and exports. */
private data class TextLayoutKey(val content:`in`.innovaticshub.notepad.data.content.RichTextData,val width:Float,val fontSize:Float,val color:Int,val alignment:TextAlign,val checklist:Boolean)
private val textLayouts=android.util.LruCache<TextLayoutKey,StaticLayout>(128)
internal fun textLayout(item:PageText):StaticLayout = synchronized(textLayouts) {
    val key=TextLayoutKey(item.content,item.width,item.fontSize,item.color,item.alignment,item.checked!=null)
    textLayouts.get(key) ?: buildTextLayout(item).also {textLayouts.put(key,it)}
}
private fun buildTextLayout(item:PageText):StaticLayout {
    val text=SpannableString(item.content.text)
    item.content.spans.forEach {span->
        val start=span.start.coerceIn(0,text.length);val end=span.end.coerceIn(start,text.length)
        if(end>start) text.setSpan(when(span.kind) {
            SpanKind.BOLD->StyleSpan(Typeface.BOLD)
            SpanKind.ITALIC->StyleSpan(Typeface.ITALIC)
            SpanKind.UNDERLINE->UnderlineSpan()
            SpanKind.STRIKE->StrikethroughSpan()
            SpanKind.COLOR->ForegroundColorSpan(span.argb ?: item.color)
            SpanKind.HIGHLIGHT->BackgroundColorSpan(span.argb ?: 0xFFFFFF00.toInt())
            SpanKind.SIZE->AbsoluteSizeSpan(((span.sizePx ?: 16)*2.5f).toInt())
        },start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    val paint=TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {color=item.color;textSize=item.fontSize;typeface=Typeface.create("sans-serif",Typeface.NORMAL)}
    return StaticLayout.Builder.obtain(text,0,text.length,paint,(item.width-if(item.checked!=null) 48f else 0f).toInt().coerceAtLeast(1))
        .setAlignment(when(item.alignment) {TextAlign.LEFT->Layout.Alignment.ALIGN_NORMAL;TextAlign.CENTER->Layout.Alignment.ALIGN_CENTER;TextAlign.RIGHT->Layout.Alignment.ALIGN_OPPOSITE})
        .setIncludePad(false).setLineSpacing(0f,1f).build()
}

internal fun pageTextColor(background:Color)=if(background.red+background.green+background.blue<1.5f) 0xFFECECEF.toInt() else 0xFF202124.toInt()

internal fun DrawScope.renderPageContent(texts:List<PageText>,images:List<PageImage>,bitmaps:Map<String,ImageBitmap>,editingTextId:String?=null,title:String?=null,titleColor:Int=0xFF202124.toInt()) {
    images.forEach {item->bitmaps[item.path]?.let {bitmap->
        val left=(bitmap.width*item.cropLeft).toInt().coerceIn(0,bitmap.width-1)
        val top=(bitmap.height*item.cropTop).toInt().coerceIn(0,bitmap.height-1)
        val right=(bitmap.width*item.cropRight).toInt().coerceIn(left+1,bitmap.width)
        val bottom=(bitmap.height*item.cropBottom).toInt().coerceIn(top+1,bitmap.height)
        rotate(item.rotation,Offset(item.x+item.width/2,item.y+item.height/2)) {
            drawImage(bitmap,IntOffset(left,top),IntSize(right-left,bottom-top),dstOffset=IntOffset(item.x.toInt(),item.y.toInt()),
                dstSize=IntSize(item.width.toInt().coerceAtLeast(1),item.height.toInt().coerceAtLeast(1)))
        }
    } }
    val all=if(title.isNullOrBlank()) texts else listOf(PageText(id="document-title",y=28f,fontSize=60f,color=titleColor,content=`in`.innovaticshub.notepad.data.content.RichTextData(title,emptyList())))+texts
    all.forEach {item->
        if(item.checked!=null) {
            drawRoundRect(Color(item.color).copy(alpha=0.6f),Offset(item.x,item.y+6f),Size(28f,28f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(4f),style=OutlineStroke(2f))
            if(item.checked) {drawLine(Color(item.color),Offset(item.x+5,item.y+20),Offset(item.x+12,item.y+27),3f);drawLine(Color(item.color),Offset(item.x+12,item.y+27),Offset(item.x+24,item.y+12),3f)}
        }
        if(item.id!=editingTextId && item.content.text.isNotEmpty()) drawIntoCanvas {canvas->
            val native=canvas.nativeCanvas;native.save();native.translate(item.x+if(item.checked!=null) 48f else 0f,item.y)
            textLayout(item).draw(native);native.restore()
        }
    }
}

internal fun loadPageImageBitmaps(context:Context,images:List<PageImage>,limit:Int=1800):Map<String,ImageBitmap> = images.map {it.path}.distinct().mapNotNull {path->
    val file=File(context.filesDir,path)
    val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,bounds)
    var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>limit) sample*=2
    BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply {inSampleSize=sample})?.asImageBitmap()?.let {path to it}
}.toMap()
