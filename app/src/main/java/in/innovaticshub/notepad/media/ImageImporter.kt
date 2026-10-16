package `in`.innovaticshub.notepad.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * Copies a picked/captured image into app storage under `images/`, downscaled
 * to at most [MAX_DIMENSION] px on the long edge to bound memory and disk.
 */
object ImageImporter {

    private const val MAX_DIMENSION = 2048

    /**
     * Returns the stored payload-relative path plus the decoded dimensions,
     * or null when the source cannot be read.
     */
    suspend fun import(
        context: Context,
        source: Uri
    ): Pair<String, ImageDimensions>? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(source)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            val sample = computeInSampleSize(bounds.outWidth, bounds.outHeight)
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = context.contentResolver.openInputStream(source)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return@runCatching null

            val fileName = "images/${UUID.randomUUID()}.jpg"
            val target = File(context.filesDir, fileName)
            target.parentFile?.mkdirs()
            target.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            val width = bitmap.width
            val height = bitmap.height
            bitmap.recycle()
            fileName to ImageDimensions(width, height)
        }.getOrNull()
    }

    private fun computeInSampleSize(width: Int, height: Int): Int {
        var sample = 1
        val longest = max(width, height)
        while (longest / (sample * 2) >= MAX_DIMENSION / 2) sample *= 2
        return sample
    }
}

data class ImageDimensions(val width: Int, val height: Int)
