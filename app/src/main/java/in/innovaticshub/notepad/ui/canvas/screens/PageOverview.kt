package `in`.innovaticshub.notepad.ui.canvas.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `in`.innovaticshub.notepad.ui.canvas.drawing.*
import `in`.innovaticshub.notepad.ui.canvas.model.CanvasPage
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import `in`.innovaticshub.notepad.media.PdfPageImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import `in`.innovaticshub.notepad.ui.theme.LocalNoteAppearance

/** Actual miniature pages make navigation possible without covering the writing canvas. */
@Composable
internal fun PageOverview(model:DrawingViewModel,onOpen:(String)->Unit,onDelete:(String)->Unit,onAdd:(Boolean)->Unit) {
    val state by model.uiState.collectAsState()
    Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).padding(bottom=24.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("Your pages",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
            TextButton(onClick={onAdd(false)}) {Icon(Icons.Outlined.Add,null);Text("Add page")}
            TextButton(onClick={onAdd(true)}) {Text("Landscape")}
        }
        LazyVerticalGrid(columns=GridCells.Adaptive(140.dp),modifier=Modifier.fillMaxWidth().heightIn(max=440.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            itemsIndexed(state.pages,key={_,page->page.id}) {index,page->
                Column {
                    Surface(onClick={onOpen(page.id)},shape=RoundedCornerShape(10.dp),
                        border=androidx.compose.foundation.BorderStroke(if(page.id==state.currentPageId) 2.dp else 1.dp,
                            if(page.id==state.currentPageId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(8.dp)) {
                            PagePreview(model,page)
                            Text("Page ${index+1}",style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(top=8.dp))
                        }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        IconButton(onClick={model.movePage(page.id,-1)},enabled=index>0,modifier=Modifier.size(32.dp)) {Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Move page ${index+1} earlier",Modifier.size(18.dp))}
                        IconButton(onClick={model.movePage(page.id,1)},enabled=index<state.pages.lastIndex,modifier=Modifier.size(32.dp)) {Icon(Icons.AutoMirrored.Outlined.ArrowForward,"Move page ${index+1} later",Modifier.size(18.dp))}
                        IconButton(onClick={model.duplicatePage(page.id);onOpen(model.uiState.value.currentPageId)},modifier=Modifier.size(32.dp)) {Icon(Icons.Outlined.ContentCopy,"Duplicate page ${index+1}",Modifier.size(18.dp))}
                        IconButton(onClick={onDelete(page.id)},enabled=state.pages.size>1,modifier=Modifier.size(32.dp)) {Icon(Icons.Outlined.DeleteOutline,"Delete page ${index+1}",Modifier.size(18.dp))}
                    }
                }
            }
        }
    }
}

@Composable
internal fun PagePreview(model:DrawingViewModel,page:CanvasPage) {
    val state by model.uiState.collectAsState();val context=LocalContext.current
    val appearance=LocalNoteAppearance.current
    val paper=appearance.paper(state.canvasBackgroundColor,page.pdfPath!=null)
    val strokes=remember(state.strokes,page.id) {state.strokes.filter {it.pageId==page.id}}
    val texts=remember(state.texts,page.id) {state.texts.filter {it.pageId==page.id}}
    val images=remember(state.images,page.id) {state.images.filter {it.pageId==page.id}}
    val displayTexts=remember(texts,appearance,page.pdfPath) {texts.map {appearance.text(it,page.pdfPath!=null)}}
    val displayStrokes=remember(strokes,appearance,page.pdfPath) {strokes.map {appearance.stroke(it,page.pdfPath!=null)}}
    var bitmaps by remember {mutableStateOf<Map<String,ImageBitmap>>(emptyMap())}
    var pdf by remember {mutableStateOf<ImageBitmap?>(null)}
    LaunchedEffect(images.map {it.path}) {bitmaps=withContext(Dispatchers.IO) {loadPageImageBitmaps(context,images,360)}}
    LaunchedEffect(page.pdfPath,page.pdfPageIndex) {pdf=page.pdfPath?.let {withContext(Dispatchers.IO) {runCatching {PdfPageImages.render(context,it,page.pdfPageIndex,360).asImageBitmap()}.getOrNull()}}}
    Canvas(Modifier.fillMaxWidth().height(155.dp)) {
        val scale=minOf(size.width/page.width,size.height/page.height)
        withTransform({translate((size.width-page.width*scale)/2,0f);scale(scale,scale,Offset.Zero)}) {
            renderPaper(page,paper,state.paperStyle,pdf)
            clipRect(0f,0f,page.width,page.height) {
                renderPageContent(displayTexts,images,bitmaps)
                displayStrokes.forEach {renderStroke(it,darkPaper=isDarkPaper(page,paper))}
            }
        }
    }
}
