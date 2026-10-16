package `in`.innovaticshub.notepad

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import `in`.innovaticshub.notepad.ui.canvas.collab.*
import `in`.innovaticshub.notepad.ui.canvas.drawing.renderStroke
import `in`.innovaticshub.notepad.ui.canvas.engine.StrokeEraser
import `in`.innovaticshub.notepad.ui.canvas.engine.ShapeGeometry
import `in`.innovaticshub.notepad.ui.canvas.model.*
import `in`.innovaticshub.notepad.ui.canvas.storage.*
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class InkEngineInstrumentedTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    @Test fun eachNibRetainsItsOwnSettingsAfterRestart() {
        val ballpoint=ToolConfig.MEDIUM_PEN.copy(color=Color.Red,baseWidth=3f)
        val fountain=ToolConfig.MEDIUM_PEN.copy(style=DrawingStyle.FOUNTAIN,color=Color.Blue,baseWidth=9f)
        val model=DrawingViewModel().apply {loadPreferences(context)}
        model.setToolConfig(ballpoint)
        model.selectPen(ToolConfig.MEDIUM_PEN.copy(style=DrawingStyle.FOUNTAIN))
        model.setToolConfig(fountain)
        model.selectPen(ToolConfig.MEDIUM_PEN)
        assertEquals(ballpoint,model.uiState.value.currentTool)
        model.selectPen(ToolConfig.MEDIUM_PEN.copy(style=DrawingStyle.FOUNTAIN))
        assertEquals(fountain,model.uiState.value.currentTool)
        val reopened=DrawingViewModel().apply {loadPreferences(context)}
        reopened.selectPen(ToolConfig.MEDIUM_PEN)
        assertEquals(ballpoint,reopened.uiState.value.currentTool)
        reopened.selectPen(ToolConfig.MEDIUM_PEN.copy(style=DrawingStyle.FOUNTAIN))
        assertEquals(fountain,reopened.uiState.value.currentTool)
    }
    @Test fun libraryPreviewKeepsFirstPageCoordinatesEvenWhenAnotherPageIsOpen() {
        val key="cover-test-${System.nanoTime()}"
        val path="drawings/$key.json"
        val red=stroke(ToolConfig.MEDIUM_PEN.copy(color=Color.Red,baseWidth=20f,usePressure=false),listOf(StrokePoint(100f,600f),StrokePoint(900f,600f)))
        val blue=red.copy(id="second-stroke",pageId="page-2",toolConfig=red.toolConfig.copy(color=Color.Blue))
        val session=SavedSession(listOf(red,blue),Color.White,null,pages=listOf(CanvasPage(),CanvasPage(id="page-2")),currentPageId="page-2")
        var thumbnail:File?=null
        try {
            DrawingStorage.saveDocument(context,path,session)
            thumbnail=File(`in`.innovaticshub.notepad.ui.canvas.drawing.NoteThumbnailRenderer.render(context,key,
                `in`.innovaticshub.notepad.data.content.DrawingPayload(path,android.graphics.Color.WHITE),cacheOnly=true)!!)
            val bitmap=android.graphics.BitmapFactory.decodeFile(thumbnail.path)
            try {
                assertEquals(480,bitmap.width);assertEquals(678,bitmap.height)
                assertEquals(android.graphics.Color.WHITE,bitmap.getPixel(20,20))
                assertEquals(android.graphics.Color.RED,bitmap.getPixel(240,288))
            } finally {bitmap.recycle()}
        } finally {File(context.filesDir,path).delete();thumbnail?.delete()}
    }
    private fun stroke(tool:ToolConfig=ToolConfig.MEDIUM_PEN,points:List<StrokePoint> = listOf(StrokePoint(10f,50f),StrokePoint(190f,50f))) =
        StrokeBuilder(tool,0f).apply {points.forEach(::addPoint)}.build()
    private fun bitmap(stroke:Stroke):Bitmap {
        val bitmap=Bitmap.createBitmap(220,120,Bitmap.Config.ARGB_8888)
        val canvas=android.graphics.Canvas(bitmap);canvas.drawColor(android.graphics.Color.WHITE)
        CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(canvas),Size(220f,120f)) {renderStroke(stroke)}
        return bitmap
    }
    @Test fun pressureChangesTheActualRenderedWidth() {
        val points=listOf(StrokePoint(10f,50f,0.1f),StrokePoint(80f,50f,0.1f),StrokePoint(130f,50f,1f),StrokePoint(200f,50f,1f))
        val image=bitmap(stroke(ToolConfig.MEDIUM_PEN.copy(baseWidth=20f,smoothing=0f),points))
        try {
            fun inkAt(x:Int)=(0 until image.height).count {image.getPixel(x,it)!=android.graphics.Color.WHITE}
            assertTrue("Pressure should change pixels, not just a setting",inkAt(160)>inkAt(40)*2)
        } finally {image.recycle()}
    }
    @Test fun highlighterKeepsBlackInkReadable() {
        val highlight=stroke(ToolConfig.HIGHLIGHTER_YELLOW.copy(baseWidth=10f),listOf(StrokePoint(10f,60f),StrokePoint(200f,60f)))
        val image=Bitmap.createBitmap(220,120,Bitmap.Config.ARGB_8888)
        val canvas=android.graphics.Canvas(image);canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawRect(90f,40f,110f,80f,android.graphics.Paint().apply {color=android.graphics.Color.BLACK})
        try {
            CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(canvas),Size(220f,120f)) {renderStroke(highlight)}
            assertEquals(android.graphics.Color.BLACK,image.getPixel(100,60))
            assertNotEquals(android.graphics.Color.WHITE,image.getPixel(50,60))
        } finally {image.recycle()}
    }
    @Test fun highlighterIsVisibleOnDarkPaperInLiveRenderingAndExport() {
        val highlight=stroke(ToolConfig.HIGHLIGHTER_YELLOW.copy(baseWidth=16f),listOf(StrokePoint(10f,60f),StrokePoint(200f,60f)))
        val image=Bitmap.createBitmap(220,120,Bitmap.Config.ARGB_8888)
        val canvas=android.graphics.Canvas(image);canvas.drawColor(android.graphics.Color.BLACK)
        try {
            CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(canvas),Size(220f,120f)) {renderStroke(highlight,darkPaper=true)}
            assertTrue(android.graphics.Color.red(image.getPixel(100,60))>40)
        } finally {image.recycle()}
        val output=java.io.ByteArrayOutputStream()
        val session=`in`.innovaticshub.notepad.ui.canvas.storage.SavedSession(listOf(highlight),Color.Black,null)
        `in`.innovaticshub.notepad.ui.canvas.storage.CanvasExporter.png(InstrumentationRegistry.getInstrumentation().targetContext,session,output)
        val bytes=output.toByteArray();val exported=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size)
        try {assertTrue(android.graphics.Color.red(exported.getPixel(160,96))>40)} finally {exported.recycle()}
    }
    @Test fun stylesProduceDifferentRasterOutputAndSurviveSaveAndCollaboration() {
        val hashes=mutableSetOf<Int>()
        for(style in listOf(DrawingStyle.SOLID,DrawingStyle.FOUNTAIN,DrawingStyle.DOTTED,DrawingStyle.DASHED,DrawingStyle.SKETCHY)) {
            val tool=ToolConfig.MEDIUM_PEN.copy(style=style,baseWidth=12f,pressureSensitivity=0.7f,smoothing=0.8f,nibAngle=23f)
            val original=stroke(tool,listOf(StrokePoint(10f,50f,0.1f,10,0.3f,0.2f),StrokePoint(100f,50f,0.7f,20),StrokePoint(200f,50f,1f,30))).copy(pageId="second")
            val decoded=StrokeJson.strokesFromJson(StrokeJson.strokesToJson(listOf(original))).single()
            assertEquals(tool,decoded.toolConfig);assertEquals(original.points,decoded.points);assertEquals("second",decoded.pageId)
            val remote=original.toRemoteStroke("test").toLocalStroke()
            assertEquals(original.id,remote.id)
            assertEquals(original.id,remote.toRemoteStroke("other-participant").id)
            assertEquals(tool,remote.toolConfig);assertEquals(original.points,remote.points);assertEquals("second",remote.pageId)
            val image=bitmap(decoded)
            try {val pixels=IntArray(image.width*image.height);image.getPixels(pixels,0,image.width,0,0,image.width,image.height);hashes+=pixels.contentHashCode()}
            finally {image.recycle()}
        }
        assertEquals(5,hashes.size)
    }
    @Test fun vectorEraserClipsSparseStrokesAndRespectsItsModes() {
        val line=stroke(ToolConfig.MEDIUM_PEN.copy(usePressure=false,smoothing=0f),listOf(StrokePoint(0f,50f),StrokePoint(200f,50f)))
        val eraser=stroke(ToolConfig.MEDIUM_ERASER.copy(baseWidth=20f),listOf(StrokePoint(100f,0f),StrokePoint(100f,100f)))
        val split=StrokeEraser.erase(listOf(line),eraser)
        assertEquals(2,split.size);assertEquals(88f,split.first().points.last().x,0.1f);assertEquals(112f,split.last().points.first().x,0.1f)
        assertTrue(StrokeEraser.erase(listOf(line),eraser.copy(toolConfig=eraser.toolConfig.copy(eraserMode=EraserMode.STROKE))).isEmpty())
        assertEquals(listOf(line),StrokeEraser.erase(listOf(line),eraser.copy(toolConfig=eraser.toolConfig.copy(eraseHighlighterOnly=true))))
        val missed=eraser.copy(points=eraser.points.map {it.copy(x=400f)})
        assertSame(line,StrokeEraser.erase(listOf(line),missed).single())
    }
    @Test fun dotsAndLegacyThreeCoordinateStrokesRemainLoadable() {
        val dot=stroke(points=listOf(StrokePoint(30f,50f,0.37f,12)))
        assertFalse(dot.isEmpty)
        assertEquals(dot.points,StrokeJson.strokesFromJson(StrokeJson.strokesToJson(listOf(dot))).single().points)
        val legacy=JSONArray("""[{"id":"old","type":"PEN","points":[[10,20,0.5],[30,40,1]]}]""")
        val loaded=StrokeJson.fromJSONArray(legacy).single()
        assertEquals("old",loaded.id);assertEquals(2,loaded.points.size);assertEquals(0L,loaded.points.first().timestamp)
    }
    @Test fun documentPagesAndImportedPdfAnnotationsExportAndReopen() {
        val first=CanvasPage();val second=CanvasPage(id="second")
        val ink=stroke().copy(pageId="second")
        val source=SavedSession(listOf(ink),Color.White,ToolConfig.INK_BRUSH,PaperStyle.RULED,listOf(first,second),"second",true)
        val decoded=DrawingStorage.decodeSession(DrawingStorage.encodeSession(source))
        assertEquals(source.pages,decoded.pages);assertEquals(source.currentPageId,decoded.currentPageId)
        assertEquals(source.strokes.single().points,decoded.strokes.single().points);assertEquals(source.tool,decoded.tool)
        val pdf=File(context.cacheDir,"ink-export-test.pdf")
        try {
            pdf.outputStream().use {CanvasExporter.pdf(context,decoded,it)}
            PdfRenderer(ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY)).use {renderer -> assertEquals(2,renderer.pageCount)}
            val output=ByteArrayOutputStream();CanvasExporter.png(context,decoded,output)
            assertTrue(output.size()>1000);assertEquals(0x89,output.toByteArray()[0].toInt() and 255)
        } finally {pdf.delete()}
    }
    @Test fun pageSelectionEditingAndUndoRestoreTheWholeDocument() {
        instrumentation.runOnMainSync {
            val model=DrawingViewModel()
            val line=stroke();model.restoreSession(listOf(line),Color.White)
            model.selectAll();model.copySelection();model.addPage()
            val second=model.uiState.value.currentPageId
            model.paste();assertEquals(1,model.uiState.value.visibleStrokes.size)
            assertEquals(second,model.uiState.value.visibleStrokes.single().pageId)
            model.rotateSelection(90f);model.styleSelection(Color.Red,8f)
            assertEquals(Color.Red,model.uiState.value.visibleStrokes.single().toolConfig.color)
            model.deletePage(second);assertEquals(1,model.uiState.value.pages.size)
            model.undo();assertEquals(2,model.uiState.value.pages.size);assertEquals(second,model.uiState.value.currentPageId)
            assertEquals(Color.Red,model.uiState.value.visibleStrokes.single().toolConfig.color)
            model.redo();assertEquals(1,model.uiState.value.pages.size)
        }
    }
    @Test fun importedPdfArtworkAndInkBothSurvivePdfExport() {
        val relative="pdfs/ink-test-${System.nanoTime()}.pdf"
        val source=File(context.filesDir,relative);source.parentFile?.mkdirs()
        val exported=File(context.cacheDir,"annotated-test.pdf")
        val pdf=android.graphics.pdf.PdfDocument()
        try {
            val page=pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(500,500,1).create())
            page.canvas.drawColor(android.graphics.Color.WHITE)
            page.canvas.drawRect(50f,50f,100f,100f,android.graphics.Paint().apply {color=android.graphics.Color.BLACK})
            pdf.finishPage(page);source.outputStream().use {pdf.writeTo(it)}
        } finally {pdf.close()}
        try {
            val ink=stroke(ToolConfig.MEDIUM_PEN.copy(color=Color.Red,baseWidth=20f,usePressure=false),listOf(StrokePoint(100f,300f),StrokePoint(800f,300f)))
            val session=SavedSession(listOf(ink),Color.White,null,pages=listOf(CanvasPage(pdfPath=relative,width=1000f,height=1000f)))
            exported.outputStream().use {CanvasExporter.pdf(context,session,it)}
            PdfRenderer(ParcelFileDescriptor.open(exported,ParcelFileDescriptor.MODE_READ_ONLY)).use {renderer ->
                renderer.openPage(0).use {page ->
                    val image=Bitmap.createBitmap(595,595,Bitmap.Config.ARGB_8888)
                    try {
                        image.eraseColor(android.graphics.Color.WHITE);page.render(image,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        assertEquals(android.graphics.Color.BLACK,image.getPixel(80,80))
                        val red=image.getPixel(200,179)
                        assertTrue(android.graphics.Color.red(red)>220 && android.graphics.Color.green(red)<50)
                    } finally {image.recycle()}
                }
            }
        } finally {source.delete();exported.delete()}
    }
    @Test fun penFavouritesKeepTheirFullSettingsInANewStore() {
        val store=DrawingPreferences(context);val previous=store.favourites();val previousTools=store.rememberedTools()
        val fountain=ToolConfig.MEDIUM_PEN.copy(style=DrawingStyle.FOUNTAIN,nibAngle=27f,pressureSensitivity=0.6f,smoothing=0.8f,color=Color.Red)
        try {
            store.saveFavourites(listOf(fountain));store.saveTools(listOf(fountain))
            assertEquals(listOf(fountain),DrawingPreferences(context).favourites())
            assertEquals(fountain,DrawingPreferences(context).rememberedTools()[ToolType.PEN])
        } finally {store.saveFavourites(previous);store.saveTools(previousTools.values)}
    }
    @Test fun shapeAssistSnapsALineWithoutReplacingOpenHandwriting() {
        val almostStraight=stroke(points=(0..40).map {StrokePoint(it*5f,50f+kotlin.math.sin(it.toFloat())*0.3f)})
        assertEquals(2,ShapeGeometry.recognize(almostStraight).points.size)
        val scribble=stroke(points=(0..60).map {StrokePoint(it*3f,50f+kotlin.math.sin(it/4f)*30f)})
        assertSame(scribble,ShapeGeometry.recognize(scribble))
    }
}
