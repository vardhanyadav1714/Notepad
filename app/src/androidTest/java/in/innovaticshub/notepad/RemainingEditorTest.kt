package `in`.innovaticshub.notepad

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import `in`.innovaticshub.notepad.data.content.RichTextData
import `in`.innovaticshub.notepad.ui.canvas.drawing.*
import `in`.innovaticshub.notepad.ui.canvas.engine.InkClipboard
import `in`.innovaticshub.notepad.ui.canvas.model.*
import `in`.innovaticshub.notepad.ui.canvas.screens.nextWritingPadOrigin
import `in`.innovaticshub.notepad.ui.canvas.storage.*
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RemainingEditorTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun lassoMovesResizesCopiesAndDeletesTextImagesAndInkTogether() {
        val text=PageText(id="text",x=100f,y=100f,width=200f,content=RichTextData("Together",emptyList()))
        val image=PageImage(id="image",path="images/test.png",x=100f,y=300f,width=200f,height=100f)
        val stroke=StrokeBuilder(ToolConfig.MEDIUM_PEN).apply {addPoint(StrokePoint(100f,500f));addPoint(StrokePoint(300f,500f))}.build()
        val model=DrawingViewModel();model.restoreSession(SavedSession(listOf(stroke),Color.White,null,texts=listOf(text),images=listOf(image)))
        model.setTool(ToolType.LASSO);model.setSelectionShape(SelectionShape.RECTANGLE)
        model.startStroke(40f,40f);model.addStrokePoint(950f,650f);model.endStroke()
        assertEquals(setOf("text"),model.uiState.value.selectedTextIds);assertEquals(setOf("image"),model.uiState.value.selectedImageIds)
        assertEquals(setOf(stroke.id),model.uiState.value.selectedStrokeIds)
        model.nudgeSelection(20f,30f)
        assertEquals(120f,model.uiState.value.texts.single().x,0.01f);assertEquals(330f,model.uiState.value.images.single().y,0.01f)
        model.undo();assertEquals(text,model.uiState.value.texts.single());assertEquals(image,model.uiState.value.images.single())
        model.selectAll();model.scaleSelectionFromCenter(1.5f)
        assertEquals(300f,model.uiState.value.images.single().width,0.01f);assertEquals(60f,model.uiState.value.texts.single().fontSize,0.01f)
        model.undo();model.selectAll()
        try {
            model.copySelection();model.paste()
            assertEquals(2,model.uiState.value.texts.size);assertEquals(2,model.uiState.value.images.size);assertEquals(2,model.uiState.value.strokes.size)
            model.deleteSelectedStrokes();assertEquals(1,model.uiState.value.images.size);assertEquals(1,model.uiState.value.texts.size)
            model.undo();assertEquals(2,model.uiState.value.images.size)
            val decoded=DrawingStorage.decodeSession(DrawingStorage.encodeSession(model.snapshot()))
            assertEquals(model.snapshot().texts,decoded.texts);assertEquals(model.snapshot().images,decoded.images)
        } finally {InkClipboard.strokes=emptyList();InkClipboard.texts=emptyList();InkClipboard.images=emptyList()}
    }
    @Test fun croppedPixelsRotationRoomAndLegacyDocumentsSurviveSaveAndUndo() {
        val path="images/crop-${System.nanoTime()}.png";val file=File(context.filesDir,path);file.parentFile?.mkdirs()
        val source=Bitmap.createBitmap(100,50,Bitmap.Config.ARGB_8888)
        val canvas=android.graphics.Canvas(source)
        canvas.drawColor(android.graphics.Color.BLUE)
        canvas.drawRect(0f,0f,50f,50f,android.graphics.Paint().apply {color=android.graphics.Color.RED})
        file.outputStream().use {source.compress(Bitmap.CompressFormat.PNG,100,it)};source.recycle()
        val original=PageImage(id="crop",path=path,x=100f,y=100f,width=200f,height=100f)
        val model=DrawingViewModel();model.restoreSession(SavedSession(emptyList(),Color.White,null,images=listOf(original),collaborationRoom="saved-room"))
        try {
            model.beginImageTransform();model.cropImage("crop",0.5f,0f,1f,1f);model.endImageTransform();model.rotateImage("crop",90f)
            val decoded=DrawingStorage.decodeSession(DrawingStorage.encodeSession(model.snapshot()))
            assertEquals("saved-room",decoded.collaborationRoom);assertEquals(90f,decoded.images.single().rotation,0f);assertEquals(0.5f,decoded.images.single().cropLeft,0f)
            val bitmaps=loadPageImageBitmaps(context,decoded.images)
            val result=Bitmap.createBitmap(500,500,Bitmap.Config.ARGB_8888)
            try {
                CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(android.graphics.Canvas(result)),Size(500f,500f)) {renderPageContent(emptyList(),decoded.images,bitmaps)}
                assertEquals(android.graphics.Color.BLUE,result.getPixel(150,150))
            } finally {result.recycle();bitmaps.values.forEach {it.asAndroidBitmap().recycle()}}
            model.undo();assertEquals(0f,model.uiState.value.images.single().rotation,0f)
            model.undo();assertEquals(original,model.uiState.value.images.single())
            val legacy=DrawingStorage.decodeSession("{\"version\":3,\"strokes\":[],\"images\":[{\"id\":\"old\",\"path\":\"test.png\"}]}")
            assertEquals(1f,legacy.images.single().cropRight,0f);assertEquals(0f,legacy.images.single().rotation,0f);assertNull(legacy.collaborationRoom)
        } finally {file.delete()}
    }
    @Test fun writingPadAdvancesAndWrapsToNextLine() {
        val first=nextWritingPadOrigin(Offset(48f,180f),300f,200f,1000f)
        assertEquals(Offset(288f,180f),first)
        val second=nextWritingPadOrigin(first,300f,200f,1000f)
        assertEquals(Offset(528f,180f),second)
        assertEquals(Offset(48f,340f),nextWritingPadOrigin(second,300f,200f,1000f))
    }
    @Test fun pastedImagesKeepTheirOwnFileWhenSourceIsDeleted() {
        val path="images/clipboard-${System.nanoTime()}.png";val source=File(context.filesDir,path);source.parentFile?.mkdirs()
        val bitmap=Bitmap.createBitmap(10,10,Bitmap.Config.ARGB_8888);bitmap.eraseColor(android.graphics.Color.BLUE)
        source.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        val original=DrawingViewModel().apply {loadPreferences(context);restoreSession(SavedSession(emptyList(),Color.White,null,images=listOf(PageImage(path=path))))}
        val destination=DrawingViewModel().apply {loadPreferences(context)}
        var copy:File?=null
        try {
            original.selectAll();original.copySelection();destination.paste()
            val image=destination.uiState.value.images.single();assertNotEquals(path,image.path)
            copy=File(context.filesDir,image.path);assertTrue(copy.exists());source.delete()
            assertTrue(loadPageImageBitmaps(context,listOf(image)).isNotEmpty())
        } finally {source.delete();copy?.delete();InkClipboard.strokes=emptyList();InkClipboard.texts=emptyList();InkClipboard.images=emptyList()}
    }
}
