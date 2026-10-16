package `in`.innovaticshub.notepad.ui.canvas.drawing

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import `in`.innovaticshub.notepad.data.content.DrawingPayload
import `in`.innovaticshub.notepad.ui.canvas.storage.StrokeJson
import `in`.innovaticshub.notepad.ui.canvas.storage.DrawingStorage
import `in`.innovaticshub.notepad.media.PdfPageImages
import java.io.File

/** Uses the interactive canvas renderer for stored note previews. Call on IO. */
object NoteThumbnailRenderer {
    fun render(context: Context, noteId: String, drawing: DrawingPayload, cacheOnly: Boolean = false,
        appearance:`in`.innovaticshub.notepad.ui.theme.NoteAppearance?=null): String? = runCatching {
        val source = File(context.filesDir, drawing.strokesFile)
        if (!source.exists()) return@runCatching null
        val target = File(if (cacheOnly) context.cacheDir else context.filesDir, "thumbnails/$noteId.png")
        // The source file is atomically rewritten on save. Reuse a fresh themed
        // thumbnail when a grid item re-enters composition during scrolling.
        if(cacheOnly && target.exists() && target.length()>0 && target.lastModified()>=source.lastModified())
            return@runCatching target.absolutePath
        val document=DrawingStorage.decodeSession(source.readText())
        val page=document.pages.first()
        val sourcePaper=document.canvasBackgroundColor ?: Color(drawing.backgroundArgb)
        val paper=appearance?.paper(sourcePaper,page.pdfPath!=null) ?: sourcePaper
        val strokes = document.strokes.filter { !it.toolConfig.isEraser && it.pageId==page.id }
        val texts=document.texts.filter {it.pageId==page.id && it.content.text.isNotBlank()}+
            if(page.id==document.pages.first().id && document.title.isNotBlank()) listOf(`in`.innovaticshub.notepad.ui.canvas.model.PageText(id="document-title",y=28f,fontSize=60f,color=pageTextColor(document.canvasBackgroundColor ?: Color.White),content=`in`.innovaticshub.notepad.data.content.RichTextData(document.title,emptyList()))) else emptyList()
        val images=document.images.filter {it.pageId==page.id}
        val displayTexts=appearance?.let {a->texts.map {a.text(it,page.pdfPath!=null)}} ?: texts
        val displayStrokes=appearance?.let {a->strokes.map {a.stroke(it,page.pdfPath!=null)}} ?: strokes
        val bounds=strokes.map {it.bounds}+texts.map {`in`.innovaticshub.notepad.ui.canvas.model.RectF(it.x,it.y,it.x+it.width,it.y+it.height)}+images.map {`in`.innovaticshub.notepad.ui.canvas.model.RectF(it.x,it.y,it.x+it.width,it.y+it.height)}
        if (bounds.isEmpty() && page.pdfPath==null) return@runCatching null
        // The library shows a miniature first page, retaining its margins and text scale.
        val scale = 480f / page.width
        val thumbnailHeight = (page.height * scale).toInt().coerceIn(320, 960)
        val bitmap = ImageBitmap(480, thumbnailHeight)
        val pdf=page.pdfPath?.let { PdfPageImages.render(context,it,page.pdfPageIndex,480) }
        val imageBitmaps=loadPageImageBitmaps(context,images,480)
        try { CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(480f, thumbnailHeight.toFloat())) {
            drawRect(Color(drawing.backgroundArgb))
            withTransform({
                scale(scale, scale, Offset.Zero)
            }) {
                renderPaper(page,paper,document.paperStyle,pdf?.asImageBitmap())
                renderPageContent(displayTexts,images,imageBitmaps)
                displayStrokes.forEach { renderStroke(it,darkPaper=isDarkPaper(page,paper)) }
            }
        } } finally { pdf?.recycle();imageBitmaps.values.forEach {it.asAndroidBitmap().recycle()} }
        target.parentFile?.mkdirs()
        val androidBitmap = bitmap.asAndroidBitmap()
        try { target.outputStream().use { check(androidBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { androidBitmap.recycle() }
        target.absolutePath
    }.getOrNull()
}
