package `in`.innovaticshub.notepad

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import `in`.innovaticshub.notepad.media.ImageImporter
import `in`.innovaticshub.notepad.media.PdfImporter
import `in`.innovaticshub.notepad.media.PdfPageImages
import `in`.innovaticshub.notepad.ui.canvas.storage.CanvasExporter
import `in`.innovaticshub.notepad.ui.canvas.storage.SavedSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ProductionMediaTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun imageImportCopiesAndBoundsLargeImages() = runBlocking {
        val source = File.createTempFile("qa-image", ".png", context.cacheDir)
        var imported: File? = null
        try {
            val bitmap = Bitmap.createBitmap(3000, 300, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(android.graphics.Color.BLUE)
                source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
            val result = ImageImporter.import(context, Uri.fromFile(source))
            assertNotNull(result)
            imported = File(context.filesDir, result!!.first)
            assertTrue(imported!!.isFile)
            assertTrue(result.second.width in 1..2048)
            assertTrue(result.second.height in 1..2048)
            assertTrue(source.delete())
            assertTrue(imported!!.length() > 0)
        } finally { source.delete(); imported?.delete() }
    }

    @Test fun validPdfImportsAllPagesAndBadInputsLeaveNoPdfPayload() = runBlocking {
        val source = File.createTempFile("qa-pdf", ".pdf", context.cacheDir)
        var imported: File? = null
        try {
            val document = PdfDocument()
            try {
                repeat(2) { index ->
                    val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                    page.canvas.drawColor(android.graphics.Color.WHITE)
                    document.finishPage(page)
                }
                source.outputStream().use(document::writeTo)
            } finally { document.close() }
            val payload = PdfImporter.import(context, Uri.fromFile(source))
            assertNotNull(payload)
            imported = File(context.filesDir, payload!!.file)
            assertEquals(2, payload.pageCount)
            assertEquals(2, PdfPageImages.sizes(context, payload.file).size)
            PdfPageImages.render(context, payload.file, 1).recycle()
            val before = File(context.filesDir, "pdfs").list()?.toSet().orEmpty()
            source.writeText("This is not a PDF")
            assertNull(PdfImporter.import(context, Uri.fromFile(source)))
            assertEquals(before, File(context.filesDir, "pdfs").list()?.toSet().orEmpty())
            assertNull(ImageImporter.import(context, Uri.fromFile(source)))
        } finally { source.delete(); imported?.delete() }
    }

    @Test fun exportToAnUnavailableDestinationReportsFailure() = runBlocking {
        val result = runCatching {
            CanvasExporter.write(context, Uri.parse("content://invalid.drawly.qa/missing"),
                SavedSession(emptyList(), null, null), pdf = true)
        }
        assertTrue(result.isFailure)
    }
}
