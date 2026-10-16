package `in`.innovaticshub.notepad.ui.canvas.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import `in`.innovaticshub.notepad.ui.canvas.drawing.ZoomableCanvas
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import `in`.innovaticshub.notepad.ui.canvas.zoom.ZoomState

/** Magnified input writes directly into a smaller, highlighted region of the same page. */
@Composable
internal fun EasyWritingPad(model:DrawingViewModel,pdf:ImageBitmap?,origin:Offset,onOriginChange:(Offset)->Unit,onRegion:(Rect)->Unit,onClose:()->Unit) {
    val state by model.uiState.collectAsState();val zoom=remember {ZoomState()};val density=LocalDensity.current.density
    var size by remember {mutableStateOf(IntSize.Zero)}
    var automatic by rememberSaveable {mutableStateOf(true)}
    fun advance() {onOriginChange(nextWritingPadOrigin(origin,size.width/zoom.scale,size.height/zoom.scale,state.currentPage.width))}
    fun publish() {if(size.width>0 && size.height>0) onRegion(Rect(zoom.screenToCanvas(Offset.Zero),zoom.screenToCanvas(Offset(size.width.toFloat(),size.height.toFloat()))))}
    LaunchedEffect(size,origin,state.currentPageId) {
        if(size.width>0) {
            zoom.setScale(density*1.2f);zoom.setOffset(-origin*zoom.scale)
            model.extendPaper(origin.y+size.height/zoom.scale+48);publish()
        }
    }
    Surface(color=MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().height(216.dp)) {
            Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("Easy writing pad",style=MaterialTheme.typography.labelMedium,modifier=Modifier.weight(1f))
                IconButton(onClick={automatic=!automatic},modifier=Modifier.size(40.dp)) {Icon(Icons.Outlined.FastForward,if(automatic) "Automatic advance on" else "Automatic advance off",Modifier.size(20.dp),tint=if(automatic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
                IconButton(onClick=model::undo,enabled=state.canUndo,modifier=Modifier.size(40.dp)) {Icon(Icons.AutoMirrored.Outlined.Undo,"Undo pad stroke",Modifier.size(20.dp))}
                IconButton(onClick={onOriginChange(Offset(48f,origin.y+(size.height/zoom.scale)*0.8f))},modifier=Modifier.size(40.dp)) {Icon(Icons.Outlined.KeyboardReturn,"Next line",Modifier.size(20.dp))}
                IconButton(onClick={
                    advance()
                },modifier=Modifier.size(40.dp)) {Icon(Icons.Outlined.ChevronRight,"Advance writing area",Modifier.size(20.dp))}
                IconButton(onClick=onClose,modifier=Modifier.size(40.dp)) {Icon(Icons.Outlined.Close,"Close writing pad",Modifier.size(20.dp))}
            }
            ZoomableCanvas(model,Modifier.fillMaxWidth().weight(1f).onSizeChanged {size=it},state.canvasBackgroundColor,zoom,pdfBackground=pdf,onZoomChanged=::publish,onStrokeFinished={stroke->
                if(automatic && stroke.toolConfig.type !in listOf(`in`.innovaticshub.notepad.ui.canvas.model.ToolType.ERASER,`in`.innovaticshub.notepad.ui.canvas.model.ToolType.LASSO,`in`.innovaticshub.notepad.ui.canvas.model.ToolType.SHAPE) &&
                    (stroke.points.maxOfOrNull {it.x} ?: 0f)>=origin.x+size.width/zoom.scale*0.82f) advance()
            })
        }
    }
}

internal fun nextWritingPadOrigin(origin:Offset,width:Float,height:Float,pageWidth:Float):Offset {
    val x=origin.x+width*0.8f
    return if(x+width>pageWidth-24f) Offset(48f,origin.y+height*0.8f) else origin.copy(x=x)
}
