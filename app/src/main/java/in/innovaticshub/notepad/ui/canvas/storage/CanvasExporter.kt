package `in`.innovaticshub.notepad.ui.canvas.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.*
import `in`.innovaticshub.notepad.media.PdfPageImages
import `in`.innovaticshub.notepad.ui.canvas.drawing.renderPaper
import `in`.innovaticshub.notepad.ui.canvas.drawing.renderStroke
import `in`.innovaticshub.notepad.ui.canvas.drawing.renderPageContent
import `in`.innovaticshub.notepad.ui.canvas.drawing.loadPageImageBitmaps
import `in`.innovaticshub.notepad.ui.canvas.drawing.isDarkPaper
import `in`.innovaticshub.notepad.ui.canvas.model.CanvasPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*

object CanvasExporter {
    suspend fun sample(context:Context,session:SavedSession,point:Offset):Color=withContext(Dispatchers.IO) {
        val page=session.pages.first {it.id==session.currentPageId}
        require(point.x in 0f..page.width && point.y in 0f..page.height) {"Tap inside the paper"}
        val bitmap=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888)
        val pdf=page.pdfPath?.let {PdfPageImages.render(context,it,page.pdfPageIndex)}
        val images=loadPageImageBitmaps(context,session.images.filter {it.pageId==page.id})
        try {
            CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(android.graphics.Canvas(bitmap)),Size(1f,1f)) {
                withTransform({translate(-point.x,-point.y)}) {
                    renderPaper(page,session.canvasBackgroundColor ?: Color.White,session.paperStyle,pdf?.asImageBitmap())
                    renderPageContent(session.texts.filter {it.pageId==page.id},session.images.filter {it.pageId==page.id},images,title=if(page.id==session.pages.first().id) session.title else null,titleColor=`in`.innovaticshub.notepad.ui.canvas.drawing.pageTextColor(session.canvasBackgroundColor ?: Color.White))
                    session.strokes.filter {it.pageId==page.id && point.x in it.bounds.left..it.bounds.right && point.y in it.bounds.top..it.bounds.bottom}.forEach {renderStroke(it,darkPaper=isDarkPaper(page,session.canvasBackgroundColor ?: Color.White))}
                }
            }
            Color(bitmap.getPixel(0,0))
        } finally {bitmap.recycle();pdf?.recycle();images.values.forEach {it.asAndroidBitmap().recycle()}}
    }
    suspend fun write(context:Context,uri:Uri,session:SavedSession,pdf:Boolean)=withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { output -> if(pdf) pdf(context,session,output) else png(context,session,output) }
            ?: error("Couldn’t open the export destination")
    }
    suspend fun shareImage(context:Context,session:SavedSession):File=withContext(Dispatchers.IO) {
        val target=File(context.cacheDir,"exports/Drawly-${System.currentTimeMillis()}.png")
        target.parentFile?.mkdirs(); target.outputStream().use { png(context,session,it) }; target
    }
    fun png(context:Context,session:SavedSession,output:OutputStream) {
        val page=session.pages.firstOrNull { it.id==session.currentPageId } ?: session.pages.first()
        val scale=minOf(1600f/page.width,2400f/page.height)
        val bitmap=Bitmap.createBitmap((page.width*scale).toInt().coerceAtLeast(1),(page.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
        try {
            render(context,session,page,Canvas(android.graphics.Canvas(bitmap)),bitmap.width.toFloat(),bitmap.height.toFloat())
            check(bitmap.compress(Bitmap.CompressFormat.PNG,100,output)) { "Couldn’t encode the image" }
        } finally { bitmap.recycle() }
    }
    fun pdf(context:Context,session:SavedSession,output:OutputStream) {
        val document=PdfDocument()
        try {
            session.pages.forEachIndexed { index,page ->
                val width=595; val height=(width*page.height/page.width).toInt().coerceAtLeast(1)
                val pdfPage=document.startPage(PdfDocument.PageInfo.Builder(width,height,index+1).create())
                render(context,session,page,Canvas(pdfPage.canvas),width.toFloat(),height.toFloat())
                document.finishPage(pdfPage)
            }
            document.writeTo(output)
        } finally { document.close() }
    }
    private fun render(context:Context,session:SavedSession,page:CanvasPage,canvas:Canvas,width:Float,height:Float) {
        val pdfBitmap=page.pdfPath?.let { PdfPageImages.render(context,it,page.pdfPageIndex) }
        val images=loadPageImageBitmaps(context,session.images.filter {it.pageId==page.id})
        try {
            CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,canvas,Size(width,height)) {
                withTransform({ scale(width/page.width,height/page.height,Offset.Zero) }) {
                    renderPaper(page,session.canvasBackgroundColor ?: Color.White,session.paperStyle,pdfBitmap?.asImageBitmap())
                    renderPageContent(session.texts.filter {it.pageId==page.id},session.images.filter {it.pageId==page.id},images,title=if(page.id==session.pages.first().id) session.title else null,titleColor=`in`.innovaticshub.notepad.ui.canvas.drawing.pageTextColor(session.canvasBackgroundColor ?: Color.White))
                    session.strokes.filter { it.pageId==page.id }.forEach { renderStroke(it,darkPaper=isDarkPaper(page,session.canvasBackgroundColor ?: Color.White)) }
                }
            }
        } finally { pdfBitmap?.recycle();images.values.forEach {it.asAndroidBitmap().recycle()} }
    }
}
