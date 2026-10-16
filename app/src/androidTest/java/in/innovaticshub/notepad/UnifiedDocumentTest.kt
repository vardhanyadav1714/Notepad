package `in`.innovaticshub.notepad

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import `in`.innovaticshub.notepad.data.content.*
import `in`.innovaticshub.notepad.feature.editor.seedPageDocument
import `in`.innovaticshub.notepad.ui.canvas.model.*
import `in`.innovaticshub.notepad.ui.canvas.screens.DrawingStudioScreen
import `in`.innovaticshub.notepad.ui.canvas.storage.*
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import `in`.innovaticshub.notepad.ui.theme.NotepadTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class UnifiedDocumentTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun keyboardHeightChangesKeepThePageOriginAnchored() {
        val zoom=`in`.innovaticshub.notepad.ui.canvas.zoom.ZoomState()
        zoom.updateViewportSize(1080f,2100f);zoom.setScale(1.2f);zoom.setOffset(Offset(24f,40f))
        val origin=zoom.offset
        zoom.updateViewportSize(1080f,1200f)
        assertEquals(origin,zoom.offset)
        zoom.updateViewportSize(1080f,2100f)
        assertEquals(origin,zoom.offset)
    }

    @Test fun magnifiedPadWritesIntoTheHighlightedDocumentRegion() {
        val model=DrawingViewModel()
        compose.setContent {NotepadTheme {DrawingStudioScreen(model,onBack={},onSaveText={})}}
        compose.onNodeWithContentDescription("Handwriting tools").performClick()
        compose.onNodeWithText("Easy writing pad").performClick()
        compose.waitUntil(5000) {compose.onAllNodesWithTag("ink-canvas").fetchSemanticsNodes().size==2}
        compose.onAllNodesWithTag("ink-canvas")[1].performTouchInput {swipe(Offset(100f,80f),Offset(300f,110f),300)}
        compose.runOnIdle {
            val stroke=model.uiState.value.strokes.single()
            assertTrue(stroke.points.all {it.x in 48f..400f && it.y in 180f..400f})
            assertEquals(model.uiState.value.currentPageId,stroke.pageId)
        }
        compose.onNodeWithContentDescription("Close writing pad").performClick()
        compose.onAllNodesWithTag("ink-canvas").assertCountEquals(1)
    }

    @Test fun straightenAndImageTransformsAreUndoableDocumentChanges() {
        val model=DrawingViewModel()
        for(i in 0..2) {model.startStroke(100f+i*100f,100f+i*25f);model.addStrokePoint(100f+i*100f,150f+i*25f);model.endStroke()}
        val before=model.uiState.value.strokes.map {it.points}
        model.selectAll();model.straightenSelection()
        val bottoms=model.uiState.value.strokes.map {it.bounds.bottom}
        assertTrue(bottoms.max()-bottoms.min()<2f)
        model.undo();assertEquals(before,model.uiState.value.strokes.map {it.points})
        model.insertImage("images/example.jpg",400,200)
        val image=model.uiState.value.images.single()
        model.beginImageTransform();model.moveImage(image.id,30f,50f);model.resizeImage(image.id,0.5f);model.endImageTransform()
        assertEquals(image.width/2,model.uiState.value.images.single().width,0.01f)
        model.undo();assertEquals(image,model.uiState.value.images.single())
        model.deleteImage(image.id);assertTrue(model.uiState.value.images.isEmpty())
        model.undo();assertEquals(image,model.uiState.value.images.single())
    }

    @Test fun textAndInkShareThePageWhileSwitchingModesAndReloading() {
        val model=DrawingViewModel()
        compose.setContent {NotepadTheme {DrawingStudioScreen(model,onBack={},onSaveText={},startInTextMode=true)}}
        compose.waitUntil(5000) {compose.onAllNodesWithTag("page-text-editor").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("page-text-editor").assertIsDisplayed().performTextInput("Same page")
        compose.onNodeWithContentDescription("Handwriting mode").performClick()
        compose.waitUntil(5000) {
            val canvas=compose.onNodeWithTag("ink-canvas").fetchSemanticsNode().boundsInRoot
            val root=compose.onRoot().fetchSemanticsNode().boundsInRoot
            canvas.height/root.height>0.8f
        }
        compose.onNodeWithTag("ink-canvas").performTouchInput {swipe(Offset(100f,240f),Offset(300f,280f),300)}
        compose.runOnIdle {assertEquals("Same page",model.uiState.value.texts.single().content.text);assertEquals(1,model.uiState.value.strokes.size)}
        compose.onNodeWithContentDescription("Text mode").performClick()
        compose.onNodeWithTag("page-text-editor").assertTextContains("Same page")
        compose.onNodeWithTag("page-text-editor").performTextInput(" together")
        compose.runOnIdle {
            model.finishTextEditing()
            val saved=DrawingStorage.decodeSession(DrawingStorage.encodeSession(model.snapshot()))
            assertEquals("Same page together",saved.texts.single().content.text)
            assertEquals(saved.strokes.single().pageId,saved.texts.single().pageId)
            val reopened=DrawingViewModel();reopened.restoreSession(saved)
            assertEquals(saved,reopened.snapshot())
            model.editTextAt(48f,1500f)
        }
        compose.onNodeWithTag("page-text-editor").assertIsDisplayed()
    }

    @Test fun migrationIsIdempotentAndRichTextAndPageContentsSurviveHistory() {
        val bold=RichTextData("Legacy text",listOf(RichSpan(0,6,SpanKind.BOLD)))
        val blocks=listOf(NoteBlock("body",BlockType.TEXT,0,richText=bold),NoteBlock("check",BlockType.CHECKLIST_ITEM,1,richText=RichTextData("Check me",emptyList()),isChecked=true))
        val seeded=seedPageDocument(context,SavedSession(emptyList(),Color.White,null),blocks,"drawing")
        assertEquals(seeded,seedPageDocument(context,seeded,blocks,"drawing"))
        assertEquals(bold,DrawingStorage.decodeSession(DrawingStorage.encodeSession(seeded)).texts.first().content)
        val model=DrawingViewModel();model.restoreSession(seeded)
        model.duplicatePage("page-1")
        assertEquals(4,model.uiState.value.texts.size)
        assertEquals(2,model.uiState.value.visibleTexts.size)
        model.deletePage(model.uiState.value.currentPageId);assertEquals(2,model.uiState.value.texts.size)
        model.undo();assertEquals(4,model.uiState.value.texts.size)
        model.toggleChecklist(model.uiState.value.visibleTexts.single {it.checked!=null}.id)
        assertFalse(model.uiState.value.visibleTexts.single {it.checked!=null}.checked!!)
        model.undo();assertTrue(model.uiState.value.visibleTexts.single {it.checked!=null}.checked!!)
    }

    @Test fun exportedPdfContainsTextImageAndInkAtTheirDocumentPositions() {
        val imagePath="images/document-test-${System.nanoTime()}.png";val file=File(context.filesDir,imagePath);file.parentFile?.mkdirs()
        val blue=Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888);blue.eraseColor(android.graphics.Color.BLUE)
        file.outputStream().use {blue.compress(Bitmap.CompressFormat.PNG,100,it)};blue.recycle()
        val exported=File(context.cacheDir,"mixed-document-test.pdf")
        try {
            val text=PageText(x=80f,y=80f,width=700f,content=RichTextData("Readable text",listOf(RichSpan(0,8,SpanKind.BOLD))),fontSize=60f)
            val image=PageImage(path=imagePath,x=80f,y=300f,width=160f,height=160f)
            val ink=StrokeBuilder(ToolConfig.MEDIUM_PEN.copy(color=Color.Red,baseWidth=16f,usePressure=false),0f).apply {addPoint(StrokePoint(80f,600f));addPoint(StrokePoint(800f,600f))}.build()
            val session=SavedSession(listOf(ink),Color.White,null,texts=listOf(text),images=listOf(image),assets=setOf(imagePath),audio=listOf(PageAudio(path="recordings/sample.m4a",durationMs=1234)))
            val decoded=DrawingStorage.decodeSession(DrawingStorage.encodeSession(session))
            assertEquals(session.texts,decoded.texts);assertEquals(session.images,decoded.images);assertEquals(session.audio,decoded.audio);assertEquals(session.strokes.single().points,decoded.strokes.single().points)
            exported.outputStream().use {CanvasExporter.pdf(context,decoded,it)}
            PdfRenderer(ParcelFileDescriptor.open(exported,ParcelFileDescriptor.MODE_READ_ONLY)).use {renderer->renderer.openPage(0).use {page->
                val bitmap=Bitmap.createBitmap(page.width,page.height,Bitmap.Config.ARGB_8888)
                try {
                    page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val bluePixel=bitmap.getPixel(80,210);assertTrue(android.graphics.Color.blue(bluePixel)>220 && android.graphics.Color.red(bluePixel)<40)
                    val redPixel=bitmap.getPixel(180,357);assertTrue(android.graphics.Color.red(redPixel)>220 && android.graphics.Color.green(redPixel)<40)
                    var textPixels=0;for(y in 45..90) for(x in 45..300) if(android.graphics.Color.red(bitmap.getPixel(x,y))<100) textPixels++
                    assertTrue("Exported text must be drawn, not just retained as metadata",textPixels>100)
                } finally {bitmap.recycle()}
            } }
        } finally {file.delete();exported.delete()}
    }
}
