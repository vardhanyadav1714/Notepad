package `in`.innovaticshub.notepad.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.util.UUID

/**
 * Records voice memos to `recordings/<uuid>.m4a` (AAC in MPEG-4), mirroring
 * Samsung Notes' in-note audio attachments.
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMs: Long = 0

    /** Starts recording; returns the target file or null on failure. */
    fun start(): File? {
        if (recorder != null) return null
        return runCatching {
            val directory = File(context.filesDir, "recordings")
            directory.mkdirs()
            val file = File(directory, "${UUID.randomUUID()}.m4a")
            outputFile = file
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            // Own resources before setup so permission/prepare failures release them.
            recorder = mediaRecorder
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioEncodingBitRate(128_000)
            mediaRecorder.setAudioSamplingRate(44_100)
            mediaRecorder.setOutputFile(file.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
            startedAtMs = System.currentTimeMillis()
            file
        }.getOrElse {
            release()
            null
        }
    }

    /** Stops and returns (relativePath, durationMs), or null when not recording. */
    fun stop(): Pair<String, Long>? {
        val mediaRecorder = recorder ?: return null
        val file = outputFile ?: return null
        return runCatching {
            mediaRecorder.stop()
            val duration = System.currentTimeMillis() - startedAtMs
            "recordings/${file.name}" to duration
        }.getOrElse {
            file.delete()
            null
        }.also {
            runCatching { mediaRecorder.release() }
            recorder = null
            outputFile = null
        }
    }

    fun release() {
        runCatching {
            recorder?.stop()
        }
        runCatching { recorder?.release() }
        outputFile?.delete()
        recorder = null
        outputFile = null
    }
}
