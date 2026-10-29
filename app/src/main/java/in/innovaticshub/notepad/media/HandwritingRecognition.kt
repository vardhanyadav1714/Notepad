package `in`.innovaticshub.notepad.media

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.*
import `in`.innovaticshub.notepad.ui.canvas.model.Stroke
import `in`.innovaticshub.notepad.ui.canvas.model.ToolType
import kotlinx.coroutines.tasks.await

/** Ink stays on-device. Model downloads and SDK diagnostics use Google's network services. */
object HandwritingRecognition {
    @Volatile private var initialized = false
    @Synchronized fun initializeAfterApproval(context: android.content.Context) {
        if (!initialized) {
            com.google.mlkit.common.MlKit.initialize(context.applicationContext)
            initialized = true
        }
    }
    private fun model(language:String):DigitalInkRecognitionModel {
        check(initialized) { "An adult must approve handwriting recognition before the SDK starts." }
        val identifier=DigitalInkRecognitionModelIdentifier.fromLanguageTag(language)
            ?: error("No handwriting model is available for this language")
        return DigitalInkRecognitionModel.builder(identifier).build()
    }
    suspend fun downloaded(language:String):Boolean = RemoteModelManager.getInstance().isModelDownloaded(model(language)).await()
    suspend fun download(language:String) { RemoteModelManager.getInstance().download(model(language),DownloadConditions.Builder().build()).await() }
    suspend fun recognize(strokes:List<Stroke>,language:String):List<String> {
        val handwriting=strokes.filter { !it.toolConfig.isEraser && it.toolConfig.type!=ToolType.LASSO && it.toolConfig.type!=ToolType.HIGHLIGHTER }
        require(handwriting.isNotEmpty()) { "Write something or select handwriting first" }
        check(downloaded(language)) { "Download the language model first" }
        val ink=Ink.builder()
        handwriting.forEach { stroke ->
            val builder=Ink.Stroke.builder()
            stroke.points.forEach { point -> builder.addPoint(Ink.Point.create(point.x,point.y,point.timestamp)) }
            ink.addStroke(builder.build())
        }
        val recognizer=DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model(language)).build())
        return try { recognizer.recognize(ink.build()).await().candidates.map { it.text }.filter { it.isNotBlank() }.distinct() }
        finally { recognizer.close() }
    }
}
