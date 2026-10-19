package `in`.innovaticshub.notepad.ui.canvas.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.AtomicFile
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import `in`.innovaticshub.notepad.ui.canvas.drawing.renderStroke
import `in`.innovaticshub.notepad.ui.canvas.model.Stroke
import `in`.innovaticshub.notepad.ui.canvas.model.ToolConfig
import `in`.innovaticshub.notepad.ui.canvas.model.ToolType
import `in`.innovaticshub.notepad.ui.canvas.model.CanvasPage
import `in`.innovaticshub.notepad.ui.canvas.model.PaperStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local persistence for drawing sessions.
 *
 * - [save]/[load]: JSON autosave in app-private storage so art survives process death.
 * - [exportToGallery]: renders the strokes into a PNG and saves it to Pictures/Drawly.
 */
data class SavedSession(
    val strokes: List<Stroke>,
    val canvasBackgroundColor: Color?,
    val tool: ToolConfig?,
    val paperStyle: PaperStyle = PaperStyle.PLAIN,
    val pages: List<CanvasPage> = listOf(CanvasPage()),
    val currentPageId: String = "page-1",
    val autoShape: Boolean = false,
    val assets: Set<String> = pages.mapNotNull {it.pdfPath}.toSet(),
    val texts:List<`in`.innovaticshub.notepad.ui.canvas.model.PageText> = emptyList(),
    val images:List<`in`.innovaticshub.notepad.ui.canvas.model.PageImage> = emptyList(),
    val audio:List<`in`.innovaticshub.notepad.ui.canvas.model.PageAudio> = emptyList(),
    val importedBlocks:Set<String> = emptySet(),val title:String="",val collaborationRoom:String?=null
)

object DrawingStorage {

    private const val FILE_NAME = "drawly_autosave.json"
    private const val GALLERY_FOLDER = "Drawly"

    //region Autosave

    fun save(context: Context, strokes: List<Stroke>, background: Color, tool: ToolConfig) {
        saveDocument(context, FILE_NAME, SavedSession(strokes, background, tool))
    }

    @Synchronized
    fun saveDocument(context:Context,path:String,session:SavedSession) {
        val target=File(context.filesDir,path)
        target.parentFile?.mkdirs()
        val bytes = encodeSession(session).toByteArray(Charsets.UTF_8)
        val atomic = AtomicFile(target)
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (failure: Throwable) {
            atomic.failWrite(stream)
            throw failure
        }
    }

    @Synchronized
    fun loadDocument(context:Context,path:String):SavedSession? {
        val file=File(context.filesDir,path)
        if (!file.exists() && !File(file.path + ".bak").exists()) return null
        return AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { decodeSession(it.readText()) }
    }

    fun saveSession(context:Context,session:SavedSession)=saveDocument(context,FILE_NAME,session)

    fun encodeSession(session:SavedSession):String = JSONObject().apply {
        put("version",4); put("background",argb(session.canvasBackgroundColor ?: Color(0xFFFAFAFA)))
        session.collaborationRoom?.let {put("collaborationRoom",it)}
        session.tool?.let { put("tool",ToolConfigJson.encode(it)) }
        put("strokes",StrokeJson.toJSONArray(session.strokes)); put("paper",session.paperStyle.name)
        put("currentPageId",session.currentPageId); put("autoShape",session.autoShape)
        put("assets",JSONArray(session.assets.toList()))
        put("texts",PageContentJson.texts(session.texts));put("images",PageContentJson.images(session.images));put("audio",PageContentJson.audios(session.audio))
        put("importedBlocks",JSONArray(session.importedBlocks.toList()))
        put("title",session.title)
        put("pages",JSONArray().apply { session.pages.forEach { page -> put(JSONObject().apply {
            put("id",page.id); page.pdfPath?.let { put("pdfPath",it) }; put("pdfPageIndex",page.pdfPageIndex)
            put("width",page.width.toDouble()); put("height",page.height.toDouble())
        }) } })
    }.toString()

    fun decodeSession(json:String):SavedSession {
        if(json.trimStart().startsWith("[")) return SavedSession(StrokeJson.strokesFromJson(json),null,null)
        val root=JSONObject(json)
        val strokes=StrokeJson.fromJSONArray(root.getJSONArray("strokes"))
        val pageArray=root.optJSONArray("pages")
        val pages=if(pageArray!=null && pageArray.length()>0) (0 until pageArray.length()).map { index ->
            val p=pageArray.getJSONObject(index)
            CanvasPage(p.getString("id"),p.optString("pdfPath").takeIf { it.isNotBlank() && it!="null" },p.optInt("pdfPageIndex"),
                p.optDouble("width",1000.0).toFloat().coerceAtLeast(1f),p.optDouble("height",1414.0).toFloat().coerceAtLeast(1f))
        }.distinctBy { it.id } else listOf(CanvasPage())
        return SavedSession(strokes,optColor(root,"background"),root.optJSONObject("tool")?.let(ToolConfigJson::decode),
            PaperStyle.entries.firstOrNull { it.name==root.optString("paper") } ?: PaperStyle.PLAIN,pages,
            root.optString("currentPageId").takeIf { id -> pages.any { it.id==id } } ?: pages.first().id,root.optBoolean("autoShape"),
            pages.mapNotNull {it.pdfPath}.toSet()+root.optJSONArray("assets").let {array -> if(array==null) emptySet() else (0 until array.length()).map {array.getString(it)}.toSet()},
            PageContentJson.readTexts(root.optJSONArray("texts")),PageContentJson.readImages(root.optJSONArray("images")),PageContentJson.readAudios(root.optJSONArray("audio")),
            root.optJSONArray("importedBlocks").let {a->if(a==null) emptySet() else (0 until a.length()).map {a.getString(it)}.toSet()},root.optString("title"),
            root.optString("collaborationRoom").takeIf {it.isNotBlank() && it!="null"})
    }

    fun load(context: Context): SavedSession? = loadDocument(context, FILE_NAME)

    //endregion

    //region Gallery export

    suspend fun exportToGallery(
        context: Context,
        strokes: List<Stroke>,
        background: Color
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val drawable = strokes.filterNot {
                it.toolConfig.isEraser || it.toolConfig.type == ToolType.LASSO
            }
            if (drawable.isEmpty()) error("Draw something before saving.")

            var left = Float.MAX_VALUE
            var top = Float.MAX_VALUE
            var right = -Float.MAX_VALUE
            var bottom = -Float.MAX_VALUE
            drawable.forEach { stroke ->
                left = minOf(left, stroke.bounds.left)
                top = minOf(top, stroke.bounds.top)
                right = maxOf(right, stroke.bounds.right)
                bottom = maxOf(bottom, stroke.bounds.bottom)
            }

            val margin = 120f
            val contentWidth = (right - left + margin * 2f).coerceAtLeast(1f)
            val contentHeight = (bottom - top + margin * 2f).coerceAtLeast(1f)
            val longestSide = maxOf(contentWidth, contentHeight)
            var scale = 1f
            if (longestSide < 1600f) scale = 1600f / longestSide
            if (longestSide * scale > 4096f) scale = 4096f / longestSide

            val bitmap = Bitmap.createBitmap(
                (contentWidth * scale).toInt().coerceIn(1, 4096),
                (contentHeight * scale).toInt().coerceIn(1, 4096),
                Bitmap.Config.ARGB_8888
            )
            val canvas = AndroidCanvas(bitmap)
            canvas.drawColor(background.toArgb())
            canvas.scale(scale, scale)
            canvas.translate(margin - left, margin - top)

            CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(canvas),Size(contentWidth,contentHeight)) {
                drawable.forEach { renderStroke(it) }
            }

            val fileName = "Drawly_" +
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
            val savedPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, bitmap, fileName)
            } else {
                saveViaLegacyStorage(context, bitmap, fileName)
            }
            bitmap.recycle()
            savedPath
        }
    }

    private fun saveViaMediaStore(context: Context, bitmap: Bitmap, fileName: String): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$GALLERY_FOLDER")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create the image file.")
        resolver.openOutputStream(uri)?.use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                error("Could not write the image.")
            }
        } ?: error("Could not open the image file.")
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
            null,
            null
        )
        return "Pictures/$GALLERY_FOLDER/$fileName"
    }

    @Suppress("DEPRECATION")
    private fun saveViaLegacyStorage(context: Context, bitmap: Bitmap, fileName: String): String {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            GALLERY_FOLDER
        )
        if (!directory.exists() && !directory.mkdirs()) {
            error("Could not create the Pictures folder.")
        }
        val file = File(directory, fileName)
        FileOutputStream(file).use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                error("Could not write the image.")
            }
        }
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
        return file.absolutePath
    }

    //endregion

    //region JSON serialization

    private fun argb(color: Color): Long = color.toArgb().toLong() and 0xFFFFFFFFL

    private fun optColor(obj: JSONObject, key: String): Color? =
        if (obj.has(key)) Color(obj.getLong(key).toInt()) else null


    //endregion
}
