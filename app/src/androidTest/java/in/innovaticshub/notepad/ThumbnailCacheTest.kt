package `in`.innovaticshub.notepad

import androidx.compose.ui.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import `in`.innovaticshub.notepad.data.content.DrawingPayload
import `in`.innovaticshub.notepad.ui.canvas.drawing.NoteThumbnailRenderer
import `in`.innovaticshub.notepad.ui.canvas.model.*
import `in`.innovaticshub.notepad.ui.canvas.storage.*
import `in`.innovaticshub.notepad.ui.theme.NoteAppearance
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class ThumbnailCacheTest {
    @Test fun libraryReusesFreshPreviewAndInvalidatesItAfterDocumentSave() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val key="preview-cache-${UUID.randomUUID()}"
        val path="drawings/$key.json"
        val source=File(context.filesDir,path)
        var cached:File?=null
        fun session(color:Color):SavedSession {
            val stroke=StrokeBuilder(ToolConfig.MEDIUM_PEN.copy(color=color,baseWidth=20f)).apply {
                addPoint(StrokePoint(200f,200f));addPoint(StrokePoint(600f,200f))
            }.build()
            return SavedSession(listOf(stroke),Color.White,null)
        }
        try {
            DrawingStorage.saveDocument(context,path,session(Color.Red))
            val payload=DrawingPayload(strokesFile=path,backgroundArgb=android.graphics.Color.WHITE)
            cached=File(NoteThumbnailRenderer.render(context,key,payload,cacheOnly=true,appearance=NoteAppearance(false))!!)
            val marker=source.lastModified()+10000
            assertTrue(cached.setLastModified(marker))
            NoteThumbnailRenderer.render(context,key,payload,cacheOnly=true,appearance=NoteAppearance(false))
            assertEquals("Scrolling back to a fresh preview must not encode it again",marker,cached.lastModified())
            DrawingStorage.saveDocument(context,path,session(Color.Blue))
            assertTrue(source.setLastModified(marker+1000))
            NoteThumbnailRenderer.render(context,key,payload,cacheOnly=true,appearance=NoteAppearance(false))
            val bitmap=android.graphics.BitmapFactory.decodeFile(cached.path)
            try {
                val pixel=bitmap.getPixel(192,96)
                assertTrue("Saved edits must invalidate the old red preview",android.graphics.Color.blue(pixel)>200 && android.graphics.Color.red(pixel)<50)
            } finally {bitmap.recycle()}
        } finally {source.delete();cached?.delete()}
    }
}
