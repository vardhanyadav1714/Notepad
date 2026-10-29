package `in`.innovaticshub.notepad.ui.canvas.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import `in`.innovaticshub.notepad.media.HandwritingRecognition
import `in`.innovaticshub.notepad.ui.canvas.drawing.renderStroke
import `in`.innovaticshub.notepad.ui.canvas.model.*
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import kotlinx.coroutines.launch
import kotlin.math.sin

@Composable
internal fun InkPreview(tool:ToolConfig,modifier:Modifier=Modifier) {
    val appearance=`in`.innovaticshub.notepad.ui.theme.LocalNoteAppearance.current
    val stroke=remember(tool) { StrokeBuilder(if(tool.isEraser) tool.copy(type=ToolType.PEN,color=Color(0xFF93A397),usePressure=false) else tool,0f).apply {
        for(i in 0..100) addPoint(StrokePoint(i*3f,35f+sin(i/12f)*16f,0.12f+sin(i/100f*Math.PI).toFloat()*0.88f,i.toLong()*8))
    }.build() }
    Canvas(modifier.fillMaxWidth().height(82.dp)) {
        val inset=16.dp.toPx(); val scale=(size.width-2*inset)/300f; val top=size.height/2-35f
        withTransform({ translate(inset,top); scale(scale,1f,Offset.Zero) }) { renderStroke(appearance.stroke(stroke),darkPaper=appearance.dark) }
    }
}

@Composable
internal fun PenSettings(viewModel:DrawingViewModel) {
    val state by viewModel.uiState.collectAsState()
    val tool=state.currentTool
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(when(tool.type) {ToolType.ERASER->"Eraser";ToolType.SHAPE->"Shape settings";ToolType.LASSO->"Select and transform";else->"Your pen, your way"},style=MaterialTheme.typography.headlineMedium,modifier=Modifier.weight(1f))
            IconButton(onClick=viewModel::addFavourite,enabled=!tool.isEraser && tool.type!=ToolType.LASSO && tool.type!=ToolType.SHAPE) {
                Icon(if(tool in state.favourites) Icons.Outlined.Star else Icons.Outlined.StarBorder,"Save favourite pen")
            }
        }
        Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant) { InkPreview(tool) }
        if(tool.type==ToolType.PEN) {
            Text("PEN STYLE",style=MaterialTheme.typography.labelSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=tool.style==DrawingStyle.SOLID && !tool.usePressure,onClick={viewModel.setToolConfig(tool.copy(style=DrawingStyle.SOLID,usePressure=false))},label={Text("Fineliner")})
                listOf(DrawingStyle.SOLID to "Ballpoint",DrawingStyle.FOUNTAIN to "Fountain",DrawingStyle.DOTTED to "Dotted",DrawingStyle.DASHED to "Dashed",DrawingStyle.GLOW to "Glow").forEach { (style,label) ->
                    FilterChip(selected=tool.style==style && (style!=DrawingStyle.SOLID || tool.usePressure),onClick={viewModel.setToolConfig(tool.copy(style=style,usePressure=if(style==DrawingStyle.SOLID) true else tool.usePressure))},label={Text(label)})
                }
            }
        }
        if(tool.isEraser) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                EraserMode.entries.forEach { mode -> FilterChip(selected=tool.eraserMode==mode,onClick={viewModel.setToolConfig(tool.copy(eraserMode=mode))},label={Text(if(mode==EraserMode.SEGMENT) "Erase by area" else "Erase whole strokes")}) }
            }
            InkToggle("Erase highlighter only",tool.eraseHighlighterOnly) { viewModel.setToolConfig(tool.copy(eraseHighlighterOnly=it)) }
        }
        if(tool.type==ToolType.PENCIL) Text("Graphite grain responds to pressure and pen tilt.",style=MaterialTheme.typography.bodySmall)
        if(tool.type==ToolType.HIGHLIGHTER) InkToggle("Straight-line highlighter",tool.constrainShape) { viewModel.setToolConfig(tool.copy(constrainShape=it)) }
        if(tool.type==ToolType.LASSO) Text("Draw around ink or tap a stroke to select it. Drag to move; use the corner handles to resize. Copy, recolour, rotate, and layer controls appear with your selection.")
        else InkSlider("Size",tool.baseWidth,if(tool.isEraser) 4f..100f else if(tool.type==ToolType.HIGHLIGHTER) 2f..30f else 0.5f..30f,viewModel::setStrokeWidth)
        if(tool.type==ToolType.SHAPE) InkToggle("Snap angles / equal sides",tool.constrainShape) {viewModel.setToolConfig(tool.copy(constrainShape=it))}
        if(!tool.isEraser && tool.type!=ToolType.LASSO) {
            InkSlider("Opacity",tool.opacity,0.05f..1f,viewModel::setOpacity,percentage=true)
        }
        if(!tool.isEraser && tool.type!=ToolType.LASSO && tool.type!=ToolType.SHAPE) {
            InkToggle("Pressure sensitivity",tool.usePressure) { viewModel.setToolConfig(tool.copy(usePressure=it)) }
            if(tool.usePressure) InkSlider("Pressure response",tool.pressureSensitivity,0f..1f,{viewModel.setToolConfig(tool.copy(pressureSensitivity=it))},percentage=true)
            InkSlider("Smoothing",tool.smoothing,0f..1f,{viewModel.setToolConfig(tool.copy(smoothing=it))},percentage=true)
            if(tool.type==ToolType.CALLIGRAPHY || tool.type==ToolType.MARKER || tool.style==DrawingStyle.FOUNTAIN)
                InkSlider("Nib angle",tool.nibAngle,0f..180f,{viewModel.setToolConfig(tool.copy(nibAngle=it))})
        }
        HorizontalDivider()
        InkToggle("Draw with your finger",state.fingerDrawing,description="Turn off for pen-only ink and reliable palm rejection. Fingers pan; two fingers zoom.",onChange=viewModel::setFingerDrawing)
        InkToggle("Auto-correct shapes",state.autoShape,description="Straightens confident lines, rectangles and ellipses when you lift the pen.",onChange=viewModel::setAutoShape)
        if(state.favourites.isNotEmpty()) {
            Text("FAVOURITE PENS",style=MaterialTheme.typography.labelSmall)
            state.favourites.forEach { favourite -> Row(verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={viewModel.setToolConfig(favourite)},modifier=Modifier.weight(1f)) {
                    Box(Modifier.size(18.dp).background(favourite.color,CircleShape)); Spacer(Modifier.width(12.dp))
                    Text("${favourite.type.label()} · ${favourite.style.name.lowercase()} · ${favourite.baseWidth.toInt()}")
                }
                IconButton(onClick={viewModel.removeFavourite(favourite)}) { Icon(Icons.Outlined.Close,"Remove favourite") }
            } }
        }
    }
}

@Composable
internal fun InkToggle(label:String,value:Boolean,description:String?=null,enabled:Boolean=true,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end=16.dp)) { Text(label,style=MaterialTheme.typography.titleMedium)
            description?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
        Switch(value,onCheckedChange=onChange,enabled=enabled)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun InkSlider(label:String,value:Float,range:ClosedFloatingPointRange<Float>,onChange:(Float)->Unit,percentage:Boolean=false) {
    Column { Row { Text(label,modifier=Modifier.weight(1f)); Text(if(percentage) "${(value*100).toInt()}%" else "${value.toInt()}",color=MaterialTheme.colorScheme.primary) }
        val accent=MaterialTheme.colorScheme.primary
        val trackColor=MaterialTheme.colorScheme.outlineVariant
        Slider(value.coerceIn(range),onValueChange=onChange,valueRange=range,
            thumb={Box(Modifier.size(18.dp).background(accent,CircleShape))},
            track={Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                val start=Offset(0f,size.height/2);val end=Offset(size.width,size.height/2)
                drawLine(trackColor,start,end,4.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round)
                val fraction=if(range.endInclusive>range.start) ((value-range.start)/(range.endInclusive-range.start)).coerceIn(0f,1f) else 0f
                drawLine(accent,start,Offset(size.width*fraction,size.height/2),4.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round)
            }}) }
}

@Composable
internal fun InkColourSheet(color:Color,onPicked:(Color)->Unit) {
    var hex by remember(color) { mutableStateOf(String.format("%06X",color.toArgb() and 0xffffff)) }
    val valid=hex.removePrefix("#").let { it.length==6 && it.toLongOrNull(16)!=null }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("A colour for every thought",style=MaterialTheme.typography.headlineMedium)
        val palette=listOf(0xFF242C29,0xFF176B5B,0xFF397DAB,0xFF5B52A6,0xFF9B6096,0xFFB54E56,0xFFCD7939,0xFFE8B541,0xFF6F8E3F,0xFF8C6D55,0xFF88969D,0xFFFFFFFF)
        palette.chunked(6).forEach { row -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
            row.forEach { argb -> val swatch=Color(argb.toInt())
                IconButton(onClick={onPicked(swatch)}) { Box(Modifier.size(30.dp).background(swatch,CircleShape).border(if(color.toArgb()==swatch.toArgb()) 3.dp else 1.dp,MaterialTheme.colorScheme.outline,CircleShape)) }
            }
        } }
        OutlinedTextField(hex,{hex=it.uppercase().take(7)},label={Text("Hex colour")},singleLine=true,isError=!valid,
            trailingIcon={IconButton(enabled=valid,onClick={onPicked(Color((0xff000000L or hex.removePrefix("#").toLong(16)).toInt()))}) { Icon(Icons.Outlined.Check,"Apply hex colour") }},modifier=Modifier.fillMaxWidth())
        InkSlider("Red",color.red,0f..1f,{onPicked(color.copy(red=it))},true)
        InkSlider("Green",color.green,0f..1f,{onPicked(color.copy(green=it))},true)
        InkSlider("Blue",color.blue,0f..1f,{onPicked(color.copy(blue=it))},true)
    }
}

@Composable
internal fun HandwritingSheet(strokes:List<Stroke>,onSave:suspend (String)->Unit) {
    val scope=rememberCoroutineScope()
    val context=androidx.compose.ui.platform.LocalContext.current
    var sdkApproved by remember { mutableStateOf(false) }
    var approvalDialog by remember { mutableStateOf(false) }
    val clipboard=LocalClipboardManager.current
    var language by remember { mutableStateOf("en-US") }
    var available by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var candidates by remember { mutableStateOf(emptyList<String>()) }
    var result by remember { mutableStateOf("") }
    LaunchedEffect(language,sdkApproved) { available=if(sdkApproved) runCatching { HandwritingRecognition.downloaded(language) }.getOrDefault(false) else false; candidates=emptyList(); result="" }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("Handwriting to text",style=MaterialTheme.typography.headlineMedium)
        Text("Select handwriting with the lasso to convert a section, or convert the current page. Your original ink is kept.",style=MaterialTheme.typography.bodyMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("en-US" to "English","hi-IN" to "Hindi","es-ES" to "Spanish","fr-FR" to "French","de-DE" to "German").forEach { (tag,label) ->
                FilterChip(selected=language==tag,onClick={language=tag},enabled=!busy,label={Text(label)})
            }
        }
        if(!available) Text("Download the selected language once (about 20 MB). Recognition then works on this device without uploading your handwriting.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Button(enabled=!busy && strokes.isNotEmpty(),onClick={if(!sdkApproved) approvalDialog=true else scope.launch {
            busy=true; status=if(available) "Reading your handwriting…" else "Downloading language model…"
            try {
                if(!available) { HandwritingRecognition.download(language); available=true; status="Reading your handwriting…" }
                candidates=HandwritingRecognition.recognize(strokes,language); result=candidates.firstOrNull().orEmpty()
                status=if(result.isBlank()) "No text was found. Try selecting one line of handwriting." else null
            } catch(error:Exception) {
                if(error is kotlinx.coroutines.CancellationException) throw error
                status=if(!available) "Couldn’t download the language model. Check your internet connection and try again."
                    else "Couldn’t recognize this handwriting. Try selecting a line and converting again."
            }
            finally { busy=false }
        }}) { if(busy) { CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp); Spacer(Modifier.width(10.dp)) }; Text(if(available) "Convert handwriting" else "Download and convert") }
        status?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
        if(candidates.size>1) Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            candidates.take(5).forEach { candidate -> SuggestionChip(onClick={result=candidate},label={Text(candidate,maxLines=1)}) }
        }
        if(result.isNotBlank()) {
            OutlinedTextField(result,{result=it},label={Text("Recognized text · edit if needed")},modifier=Modifier.fillMaxWidth(),minLines=2)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick={clipboard.setText(AnnotatedString(result));status="Copied"}) { Text("Copy text") }
                Button(enabled=!busy,onClick={scope.launch { busy=true; runCatching { onSave(result) }.onSuccess {status="Added to your notes"}.onFailure {status="Couldn’t save this text"};busy=false }}) { Text("Add to note") }
            }
        }
    }
    if(approvalDialog) SharingApprovalDialog(forRecognition=true,onDismiss={approvalDialog=false},onApproved={
        approvalDialog=false
        scope.launch {
            runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { HandwritingRecognition.initializeAfterApproval(context) } }
                .onSuccess { sdkApproved=true;status="Recognition approved. Tap Download and convert to continue." }
                .onFailure {status="Could not start handwriting recognition. Your ink is preserved."}
        }
    })
}

internal fun ToolType.label():String=when(this) { ToolType.PEN->"Pen";ToolType.PENCIL->"Pencil";ToolType.MARKER->"Marker";ToolType.HIGHLIGHTER->"Highlight";ToolType.BRUSH->"Brush";ToolType.CALLIGRAPHY->"Calligraphy";ToolType.ERASER->"Eraser";ToolType.LASSO->"Select";ToolType.SHAPE->"Shapes";ToolType.TEXT->"Text" }
