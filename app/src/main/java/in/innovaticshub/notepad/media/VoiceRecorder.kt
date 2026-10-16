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
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioEncodingBitRate(128_000)
            mediaRecorder.setAudioSamplingRate(44_100)
            mediaRecorder.setOutputFile(file.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
            recorder = mediaRecorder
            outputFile = file
            startedAtMs = System.currentTimeMillis()
            file
        }.getOrNull()
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
            mediaRecorder.release()
            recorder = null
            outputFile = null
        }
    }

    fun release() {
        runCatching {
            recorder?.stop()
        }
        runCatching { recorder?.release() }
        recorder = null
        outputFile = null
    }
}
