package `in`.innovaticshub.notepad.media

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import `in`.innovaticshub.notepad.data.content.PdfPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object PdfImporter {
    suspend fun import(context: Context, uri: Uri): PdfPayload? = withContext(Dispatchers.IO) {
        val relative = "pdfs/${UUID.randomUUID()}.pdf"
        val file = File(context.filesDir, relative)
        runCatching {
            file.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                ?: error("Cannot read PDF")
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                renderer.openPage(0).use { page -> PdfPayload(relative, renderer.pageCount, page.width.toFloat(), page.height.toFloat()) }
            }
        }.onFailure { file.delete() }.getOrNull()
    }
}
