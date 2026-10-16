package `in`.innovaticshub.notepad.ui.canvas.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `in`.innovaticshub.notepad.ui.canvas.drawing.*
import `in`.innovaticshub.notepad.ui.canvas.model.*
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ImageEditSheet(model:DrawingViewModel,onDone:()->Unit) {
    val state by model.uiState.collectAsState()
    val image=state.images.firstOrNull {it.id==state.activeImageId} ?: return
    val context=LocalContext.current
    var bitmaps by remember(image.path) {mutableStateOf<Map<String,ImageBitmap>>(emptyMap())}
    LaunchedEffect(image.path) {bitmaps=withContext(Dispatchers.IO) {loadPageImageBitmaps(context,listOf(image),1000)}}
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp).padding(bottom=24.dp)) {
        Text("Crop image",style=MaterialTheme.typography.titleLarge)
        Canvas(Modifier.fillMaxWidth().height(180.dp).padding(vertical=16.dp)) {
            val bounds=image.bounds()
            val scale=minOf(size.width/(bounds.right-bounds.left),size.height/(bounds.bottom-bounds.top))
            withTransform({translate((size.width-(bounds.right-bounds.left)*scale)/2,(size.height-(bounds.bottom-bounds.top)*scale)/2);scale(scale,scale,Offset.Zero);translate(-bounds.left,-bounds.top)}) {
                renderPageContent(emptyList(),listOf(image),bitmaps)
            }
        }
        fun crop(l:Float=image.cropLeft,t:Float=image.cropTop,r:Float=image.cropRight,b:Float=image.cropBottom) {model.cropImage(image.id,l,t,r,b)}
        InkSlider("Left",image.cropLeft,0f..(image.cropRight-0.05f),{crop(l=it)},percentage=true)
        InkSlider("Right",1f-image.cropRight,0f..(0.95f-image.cropLeft),{crop(r=1f-it)},percentage=true)
        InkSlider("Top",image.cropTop,0f..(image.cropBottom-0.05f),{crop(t=it)},percentage=true)
        InkSlider("Bottom",1f-image.cropBottom,0f..(0.95f-image.cropTop),{crop(b=1f-it)},percentage=true)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={crop(0f,0f,1f,1f)}) {Text("Reset crop")}
            TextButton(onClick=onDone) {Text("Done")}
        }
    }
}
