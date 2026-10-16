package `in`.innovaticshub.notepad.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

/** PDF access stays on IO; each renderer owns and closes its file descriptor. */
object PdfPageImages {
    fun sizes(context:Context,path:String):List<Pair<Int,Int>> =
        PdfRenderer(ParcelFileDescriptor.open(File(context.filesDir,path),ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            (0 until renderer.pageCount).map { index -> renderer.openPage(index).use { it.width to it.height } }
        }
    fun render(context:Context,path:String,index:Int,maxWidth:Int=1400):Bitmap =
        PdfRenderer(ParcelFileDescriptor.open(File(context.filesDir,path),ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            renderer.openPage(index).use { page ->
                val scale=minOf(maxWidth.toFloat()/page.width,2200f/page.height)
                val bitmap=Bitmap.createBitmap((page.width*scale).toInt().coerceAtLeast(1),(page.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
                try { bitmap.eraseColor(Color.WHITE); page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); bitmap }
                catch(error:Throwable) { bitmap.recycle(); throw error }
            }
        }
}
