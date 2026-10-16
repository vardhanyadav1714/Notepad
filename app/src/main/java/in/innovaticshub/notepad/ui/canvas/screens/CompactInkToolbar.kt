package `in`.innovaticshub.notepad.ui.canvas.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.clipRect
import `in`.innovaticshub.notepad.ui.canvas.drawing.renderStroke
import kotlin.math.sin
import `in`.innovaticshub.notepad.ui.canvas.model.*
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel

/** One handwriting row. Tapping an active tool opens its settings above the row. */
@Composable
internal fun CompactInkToolbar(model:DrawingViewModel,pan:Boolean,onWrite:()->Unit,onKeyboard:()->Unit,
    onAdvanced:(String)->Unit,onClear:()->Unit) {
    val state by model.uiState.collectAsState()
    val expanded=androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp>=600
    val appearance=`in`.innovaticshub.notepad.ui.theme.LocalNoteAppearance.current
    var popup by remember { mutableStateOf<String?>(null) }
    val penTypes=listOf(ToolType.PEN,ToolType.PENCIL,ToolType.MARKER,ToolType.BRUSH,ToolType.CALLIGRAPHY)
    var lastPen by remember { mutableStateOf(ToolType.PEN) }
    LaunchedEffect(state.currentTool.type) {if(state.currentTool.type in penTypes) lastPen=state.currentTool.type}
    fun choose(type:ToolType,key:String) {
        onWrite()
        if(state.currentTool.type==type) popup=if(popup==key) null else key
        else {model.setTool(type);popup=null}
    }
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.45f))
    Row(Modifier.fillMaxWidth().height(48.dp).testTag("handwriting-toolbar").padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { InkToolButton("Text mode",false,onKeyboard) {Icon(Icons.Outlined.Keyboard,null,Modifier.size(22.dp))} }
        Box(Modifier.weight(1f)) {
            val penSelected=state.currentTool.type in penTypes && !pan
            InkToolButton("Pen",penSelected,{onWrite();if(penSelected) popup=if(popup=="pen") null else "pen" else {model.setTool(lastPen);popup=null}}) {
                PenNib((if(penSelected) state.currentTool else ToolConfig.MEDIUM_PEN).let {it.copy(color=appearance.ink(it.color))},Modifier.size(26.dp))
            }
            InkPopover(popup=="pen",{popup=null}) { CompactPenSettings(model,{popup=null},{popup=null;onAdvanced(it)}) }
        }
        Box(Modifier.weight(1f)) {
            InkToolButton("Highlighter",state.currentTool.type==ToolType.HIGHLIGHTER && !pan,{choose(ToolType.HIGHLIGHTER,"highlighter")}) {PenNib(ToolConfig.HIGHLIGHTER_YELLOW,Modifier.size(26.dp))}
            InkPopover(popup=="highlighter",{popup=null}) {CompactPenSettings(model,{popup=null},{popup=null;onAdvanced(it)})}
        }
        Box(Modifier.weight(1f)) {
            InkToolButton("Eraser",state.currentTool.isEraser && !pan,{choose(ToolType.ERASER,"eraser")}) {EraserGlyph()}
            InkPopover(popup=="eraser",{popup=null}) {
                Column(Modifier.padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    PopoverHeading("Handwriting eraser") {popup=null}
                    EraserMode.entries.reversed().forEach {mode -> Row(Modifier.fillMaxWidth().clickable {model.setToolConfig(state.currentTool.copy(eraserMode=mode))},verticalAlignment=Alignment.CenterVertically) {
                        RadioButton(state.currentTool.eraserMode==mode,{model.setToolConfig(state.currentTool.copy(eraserMode=mode))})
                        Text(if(mode==EraserMode.STROKE) "Stroke eraser" else "Area eraser",style=MaterialTheme.typography.bodyMedium)
                    } }
                    if(state.currentTool.eraserMode==EraserMode.SEGMENT) InkSlider("Size",state.currentTool.baseWidth,4f..100f,model::setStrokeWidth)
                    InkToggle("Erase highlighter only",state.currentTool.eraseHighlighterOnly) {model.setToolConfig(state.currentTool.copy(eraseHighlighterOnly=it))}
                    HorizontalDivider()
                    TextButton(onClick={popup=null;onClear()},modifier=Modifier.fillMaxWidth()) {Text("Erase all handwriting")}
                }
            }
        }
        Box(Modifier.weight(1f)) {
            InkToolButton("Select handwriting",state.currentTool.type==ToolType.LASSO && !pan,{choose(ToolType.LASSO,"selection")}) {Icon(Icons.Outlined.Gesture,null,Modifier.size(23.dp))}
            InkPopover(popup=="selection",{popup=null}) {
                Column(Modifier.padding(horizontal=16.dp)) {
                    PopoverHeading("Selection mode") {popup=null}
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        FilterChip(state.selectionShape==SelectionShape.LASSO,{model.setSelectionShape(SelectionShape.LASSO)},label={Text("Lasso")})
                        FilterChip(state.selectionShape==SelectionShape.RECTANGLE,{model.setSelectionShape(SelectionShape.RECTANGLE)},label={Text("Rectangle")})
                    }
                    InkToggle("Include partially selected ink",state.includePartialSelection, onChange=model::setIncludePartialSelection)
                    InkToggle("Include text and images",state.includePageObjects,onChange=model::setIncludePageObjects)
                }
            }
        }
        Box(Modifier.weight(1f)) {
            InkToolButton("Handwriting tools",state.autoShape || state.currentTool.type==ToolType.SHAPE,{popup=if(popup=="tools") null else "tools"}) {Icon(Icons.Outlined.AutoAwesome,null,Modifier.size(22.dp))}
            DropdownMenu(popup=="tools",{popup=null},modifier=Modifier.widthIn(min=240.dp),containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp) {
                DropdownMenuItem(text={Text("Ink colour")},leadingIcon={Icon(Icons.Outlined.Palette,null)},onClick={popup=null;onAdvanced("colour")})
                DropdownMenuItem(text={Text("Pages")},leadingIcon={Icon(Icons.Outlined.Layers,null)},onClick={popup=null;onAdvanced("pages")})
                HorizontalDivider()
                DropdownMenuItem(text={Text("Convert to text")},leadingIcon={Icon(Icons.Outlined.TextFields,null)},onClick={popup=null;onAdvanced("recognize")})
                DropdownMenuItem(text={Text("Straighten handwriting")},leadingIcon={Icon(Icons.Outlined.FormatAlignLeft,null)},onClick={popup=null;model.selectAll();model.straightenSelection()})
                DropdownMenuItem(text={Text("Easy writing pad")},leadingIcon={Icon(Icons.Outlined.EditNote,null)},onClick={popup=null;onWrite();if(state.currentTool.type in listOf(ToolType.ERASER,ToolType.LASSO,ToolType.SHAPE,ToolType.TEXT)) model.setTool(lastPen);onAdvanced("writing-pad")})
                DropdownMenuItem(text={Text("Zoom lock")},leadingIcon={Icon(Icons.Outlined.Lock,null)},trailingIcon={Checkbox(state.zoomLocked,null)},onClick={model.setZoomLocked(!state.zoomLocked);popup=null})
                DropdownMenuItem(text={Text("Shapes")},leadingIcon={Icon(Icons.Outlined.Category,null)},onClick={onWrite();model.setTool(ToolType.SHAPE);popup="shapes"})
                DropdownMenuItem(text={Text("Auto-fix shapes")},leadingIcon={Icon(Icons.Outlined.AutoFixHigh,null)},trailingIcon={Checkbox(state.autoShape,null)},onClick={model.setAutoShape(!state.autoShape);popup=null})
                DropdownMenuItem(text={Text("Favourite pens")},leadingIcon={Icon(Icons.Outlined.StarBorder,null)},onClick={popup="pen";model.setTool(lastPen);onWrite()})
            }
            InkPopover(popup=="shapes",{popup=null}) {
                Column(Modifier.padding(horizontal=16.dp)) {
                    PopoverHeading("Shapes") {popup=null}
                    ShapeKind.entries.chunked(3).forEach {row -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        row.forEach {shape -> FilterChip(state.currentTool.shape==shape,{model.setToolConfig(state.currentTool.copy(shape=shape));popup=null},label={Text(shape.name.lowercase().replaceFirstChar {it.uppercase()})})}
                    } }
                    InkToggle("Snap angles / equal sides",state.currentTool.constrainShape) {model.setToolConfig(state.currentTool.copy(constrainShape=it))}
                }
            }
        }
        if(expanded) {
            Box(Modifier.weight(1f)) {InkToolButton("Ink colour",false,{onAdvanced("colour")}) {Box(Modifier.size(22.dp).background(appearance.ink(state.currentTool.color),CircleShape).border(1.dp,MaterialTheme.colorScheme.outline,CircleShape))}}
            Box(Modifier.weight(1f)) {InkToolButton("Easy writing pad",false,{onWrite();if(state.currentTool.type !in penTypes && state.currentTool.type!=ToolType.HIGHLIGHTER) model.setTool(lastPen);onAdvanced("writing-pad")}) {Icon(Icons.Outlined.EditNote,null,Modifier.size(23.dp))}}
            Box(Modifier.weight(1f)) {InkToolButton("Auto-fix shapes",state.autoShape,{model.setAutoShape(!state.autoShape)}) {Icon(Icons.Outlined.AutoFixHigh,null,Modifier.size(22.dp))}}
            Box(Modifier.weight(1f)) {InkToolButton("Zoom lock",state.zoomLocked,{model.setZoomLocked(!state.zoomLocked)}) {Icon(if(state.zoomLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,null,Modifier.size(22.dp))}}
            Box(Modifier.weight(1f)) {InkToolButton("Pages",false,{onAdvanced("pages")}) {Icon(Icons.Outlined.Layers,null,Modifier.size(22.dp))}}
        }
            Box(Modifier.weight(1.35f)) {
                TextButton(onClick={model.setFingerDrawing(!state.fingerDrawing)},modifier=Modifier.fillMaxWidth().height(48.dp)
                    .semantics {contentDescription=if(state.fingerDrawing) "Finger drawing: on" else "Finger drawing: off";selected=state.fingerDrawing},
                    contentPadding=PaddingValues(0.dp)) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        Icon(if(state.fingerDrawing) Icons.Outlined.TouchApp else Icons.Outlined.PanTool,null,Modifier.size(20.dp))
                        Text(if(state.fingerDrawing) "Write" else "Scroll",style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
        Box(Modifier.weight(1f)) {InkToolButton("Undo",false,model::undo,state.canUndo) {Icon(Icons.AutoMirrored.Outlined.Undo,null,Modifier.size(22.dp))}}
        Box(Modifier.weight(1f)) {InkToolButton("Redo",false,model::redo,state.canRedo) {Icon(Icons.AutoMirrored.Outlined.Redo,null,Modifier.size(22.dp))}}
    }
}

@Composable
private fun InkToolButton(label:String,active:Boolean,onClick:()->Unit,enabled:Boolean=true,content:@Composable ()->Unit) {
    IconButton(onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().height(48.dp).semantics {contentDescription=label;selected=active}) {
        Box(Modifier.size(34.dp).background(if(active) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,CircleShape),contentAlignment=Alignment.Center) {content()}
    }
}

@Composable
private fun InkPopover(visible:Boolean,onDismiss:()->Unit,content:@Composable ColumnScope.()->Unit) {
    DropdownMenu(visible,onDismiss,modifier=Modifier.width(336.dp).heightIn(max=510.dp),shape=RoundedCornerShape(22.dp),
        containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp,shadowElevation=8.dp,content=content)
}

@Composable
private fun PopoverHeading(title:String,onClose:()->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(title,style=MaterialTheme.typography.titleSmall,modifier=Modifier.weight(1f))
        IconButton(onClose,Modifier.size(40.dp)) {Icon(Icons.Outlined.Close,"Close tool settings",Modifier.size(19.dp))}
    }
}

@Composable
private fun CompactPenSettings(model:DrawingViewModel,onClose:()->Unit,onAdvanced:(String)->Unit) {
    val state by model.uiState.collectAsState()
    val tool=state.currentTool
    val appearance=`in`.innovaticshub.notepad.ui.theme.LocalNoteAppearance.current
    Column(Modifier.padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        PopoverHeading(if(tool.type==ToolType.HIGHLIGHTER) "Highlighter" else "Pen",onClose)
        if(tool.type!=ToolType.HIGHLIGHTER) {
            val presets=listOf(ToolConfig.MEDIUM_PEN,ToolConfig.MEDIUM_PEN.copy(style=DrawingStyle.FOUNTAIN),ToolConfig.GRAPHITE_PENCIL,
                ToolConfig.defaultForType(ToolType.BRUSH),ToolConfig.defaultForType(ToolType.CALLIGRAPHY),ToolConfig.defaultForType(ToolType.MARKER))
            val labels=listOf("Ballpoint","Fountain","Pencil","Brush","Calligraphy","Marker")
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                presets.forEachIndexed {index,preset ->
                    val active=tool.type==preset.type && (tool.type!=ToolType.PEN || tool.style==preset.style)
                    Surface(onClick={model.selectPen(preset)},
                        color=if(active) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,shape=RoundedCornerShape(10.dp),
                        modifier=Modifier.size(44.dp,64.dp).semantics {contentDescription=labels[index];selected=active}) {
                        Box(contentAlignment=Alignment.Center) {PenNib(preset.copy(color=tool.color),Modifier.size(28.dp,48.dp))}
                    }
                }
            }
        }
        // This uses the same pressure envelope and renderer as ink on the page.
        Canvas(Modifier.fillMaxWidth().height(54.dp).testTag("pen-stroke-preview")) {
            val points=(0..100).map {i->val t=i/100f
                StrokePoint(12.dp.toPx()+t*(size.width-24.dp.toPx()),size.height/2+sin(t*6.28f)*size.height*0.2f,
                    pressure=0.15f+0.85f*sin(t*3.14159f),timestamp=i*8L)
            }
            val builder=StrokeBuilder(tool.copy(baseWidth=tool.baseWidth*density,color=appearance.ink(tool.color)))
            points.forEach(builder::addPoint)
            clipRect {renderStroke(builder.build(),darkPaper=appearance.dark)}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
            listOf(1f,2f,4f,8f,14f).forEach {width ->
                IconButton(onClick={model.setStrokeWidth(width)},modifier=Modifier.size(42.dp).semantics {contentDescription="Thickness $width";selected=tool.baseWidth==width}) {
                    Box(Modifier.size((width/2+3).dp).background(MaterialTheme.colorScheme.onSurface,CircleShape))
                }
            }
        }
        InkSlider("Thickness",tool.baseWidth,0.5f..30f,model::setStrokeWidth)
        val colours=listOf(0xFF202124,0xFF797B80,0xFFD85850,0xFFE8A347,0xFFF1D14D,0xFF5F9D77,0xFF5486C6,0xFF7779BD,0xFFAD76AC,0xFFCD91B4)
        colours.chunked(5).forEach {row -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            row.forEach {argb -> val colour=Color(argb.toInt())
                IconButton(onClick={model.setColor(colour)},modifier=Modifier.size(44.dp)) {
                    Box(Modifier.size(25.dp).background(colour,CircleShape).border(if(tool.color==colour) 3.dp else 0.dp,MaterialTheme.colorScheme.onSurface,CircleShape),contentAlignment=Alignment.Center) {
                        if(tool.color==colour) Icon(Icons.Outlined.Check,null,Modifier.size(16.dp),tint=Color.White)
                    }
                }
            }
        } }
        if(state.favourites.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            state.favourites.forEachIndexed {index,favourite -> IconButton(onClick={model.setToolConfig(favourite)},modifier=Modifier.size(44.dp)) {PenNib(favourite,Modifier.size(25.dp).semantics {contentDescription="Favourite pen ${index+1}"})} }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={if(tool in state.favourites) model.removeFavourite(tool) else model.addFavourite()}) {Icon(if(tool in state.favourites) Icons.Outlined.Star else Icons.Outlined.StarBorder,if(tool in state.favourites) "Remove favourite pen" else "Save favourite pen",Modifier.size(22.dp))}
            TextButton(onClick={onAdvanced("colour")}) {Text("Colour")}
            TextButton(onClick={onAdvanced("pen")}) {Text("More settings")}
        }
    }
}

/** Small original vector pen silhouettes; no reference artwork is bundled. */
@Composable
private fun PenNib(tool:ToolConfig,modifier:Modifier) {
    Canvas(modifier) {
        val w=size.width;val h=size.height
        val metal=Color(0xFFB9BCC1);val shine=Color(0xFFE4E5E7);val barrel=Color(0xFF45474C)
        fun polygon(vararg points:Pair<Float,Float>):Path=Path().apply {
            points.forEachIndexed {i,p->if(i==0) moveTo(p.first*w,p.second*h) else lineTo(p.first*w,p.second*h)};close()
        }
        val broad=tool.type==ToolType.HIGHLIGHTER || tool.type==ToolType.MARKER
        val bodyLeft=if(broad) 0.2f else 0.3f;val bodyWidth=1-2*bodyLeft
        drawRect(barrel,Offset(w*bodyLeft,h*0.64f),Size(w*bodyWidth,h*0.36f))
        drawRect(Color(0xFF73767B),Offset(w*(bodyLeft+0.02f),h*0.65f),Size(w*0.06f,h*0.35f))
        when {
            tool.type==ToolType.PENCIL -> {
                drawRect(Color(0xFFE8C38F),Offset(w*0.3f,h*0.44f),Size(w*0.4f,h*0.56f))
                drawPath(polygon(0.3f to 0.44f,0.5f to 0.03f,0.7f to 0.44f),Color(0xFFD5B995))
                drawPath(polygon(0.43f to 0.18f,0.5f to 0.03f,0.57f to 0.18f),barrel)
                drawLine(shine,Offset(w*0.42f,h*0.45f),Offset(w*0.42f,h),w*0.06f)
            }
            tool.style==DrawingStyle.FOUNTAIN -> {
                drawPath(polygon(0.39f to 0.65f,0.22f to 0.4f,0.5f to 0.02f,0.78f to 0.4f,0.61f to 0.65f),metal)
                drawPath(polygon(0.39f to 0.65f,0.28f to 0.4f,0.5f to 0.04f,0.5f to 0.64f),shine)
                drawLine(barrel,Offset(w*0.5f,h*0.04f),Offset(w*0.5f,h*0.42f),w*0.025f)
                drawCircle(barrel,w*0.045f,Offset(w*0.5f,h*0.42f))
            }
            tool.type==ToolType.BRUSH -> {
                val bristles=Path().apply {moveTo(w*0.3f,h*0.52f);cubicTo(w*0.13f,h*0.28f,w*0.51f,h*0.19f,w*0.5f,h*0.01f);cubicTo(w*0.63f,h*0.18f,w*0.85f,h*0.38f,w*0.7f,h*0.52f);close()}
                drawPath(bristles,Color(0xFF777A7E));drawRect(metal,Offset(w*0.3f,h*0.5f),Size(w*0.4f,h*0.16f))
            }
            broad -> {
                drawPath(polygon(0.3f to 0.22f,0.7f to 0.12f,0.7f to 0.38f,0.3f to 0.38f),tool.color)
                drawRect(shine,Offset(w*0.2f,h*0.36f),Size(w*0.6f,h*0.43f))
                drawRect(tool.color,Offset(w*0.2f,h*0.6f),Size(w*0.6f,h*0.12f))
                drawRect(metal,Offset(w*0.2f,h*0.36f),Size(w*0.6f,h*0.025f))
            }
            tool.type==ToolType.CALLIGRAPHY -> {
                drawPath(polygon(0.3f to 0.64f,0.24f to 0.12f,0.69f to 0.05f,0.7f to 0.64f),metal)
                drawLine(barrel,Offset(w*0.25f,h*0.12f),Offset(w*0.69f,h*0.05f),h*0.035f)
                drawLine(shine,Offset(w*0.35f,h*0.2f),Offset(w*0.4f,h*0.59f),w*0.07f)
            }
            else -> {
                drawRect(metal,Offset(w*0.3f,h*0.42f),Size(w*0.4f,h*0.24f))
                drawPath(polygon(0.33f to 0.42f,0.5f to 0.04f,0.67f to 0.42f),shine)
                drawLine(barrel,Offset(w*0.5f,h*0.04f),Offset(w*0.5f,h*0.15f),w*0.06f)
                drawLine(shine,Offset(w*0.38f,h*0.43f),Offset(w*0.38f,h*0.63f),w*0.055f)
            }
        }
        drawRect(tool.color,Offset(w*bodyLeft,h*0.88f),Size(w*bodyWidth,h*0.045f))
    }
}

@Composable
private fun EraserGlyph() {
    val colour=MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(24.dp)) {
        val p=Path().apply {moveTo(size.width*0.15f,size.height*0.48f);lineTo(size.width*0.48f,size.height*0.15f);quadraticTo(size.width*0.57f,size.height*0.07f,size.width*0.65f,size.height*0.15f);lineTo(size.width*0.9f,size.height*0.4f);quadraticTo(size.width*0.96f,size.height*0.48f,size.width*0.88f,size.height*0.56f);lineTo(size.width*0.55f,size.height*0.9f);lineTo(size.width*0.39f,size.height*0.9f);lineTo(size.width*0.14f,size.height*0.65f);close()}
        drawPath(p,colour,style=Stroke(1.5.dp.toPx()))
        drawLine(colour,Offset(size.width*0.29f,size.height*0.35f),Offset(size.width*0.72f,size.height*0.74f),1.2.dp.toPx())
    }
}

@Composable
internal fun InkSelectionBar(model:DrawingViewModel,onColour:()->Unit,onRecognize:()->Unit) {
    var more by remember {mutableStateOf(false)}
    val state by model.uiState.collectAsState()
    Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly) {
        TextButton(onClick=model::cutSelection) {Text("Cut")}
        TextButton(onClick=model::copySelection) {Text("Copy")}
        TextButton(onClick=model::deleteSelectedStrokes) {Text("Delete")}
        IconButton(onColour,enabled=state.selectedStrokeIds.isNotEmpty() || state.selectedTextIds.isNotEmpty()) {Icon(Icons.Outlined.Palette,"Change selection style")}
        Box {
            IconButton({more=true}) {Icon(Icons.Outlined.MoreVert,"Selection options")}
            DropdownMenu(more,{more=false}) {
                if(state.selectedImageIds.size==1 && state.selectedStrokeIds.isEmpty() && state.selectedTextIds.isEmpty()) {
                    DropdownMenuItem(text={Text("Edit image")},onClick={more=false;model.selectImage(state.selectedImageIds.single())})
                }
                DropdownMenuItem(text={Text("Convert to text")},enabled=state.selectedStrokeIds.isNotEmpty(),onClick={more=false;onRecognize()})
                DropdownMenuItem(text={Text("Straighten handwriting")},onClick={more=false;model.straightenSelection()})
                DropdownMenuItem(text={Text("Duplicate")},onClick={more=false;model.duplicateSelection()})
                DropdownMenuItem(text={Text("Rotate")},enabled=state.selectedTextIds.isEmpty() && state.selectedImageIds.isEmpty(),onClick={more=false;model.rotateSelection(15f)})
                DropdownMenuItem(text={Text("Flip")},enabled=state.selectedTextIds.isEmpty() && state.selectedImageIds.isEmpty(),onClick={more=false;model.flipSelection(true)})
                DropdownMenuItem(text={Text("Thicker")},onClick={more=false;model.styleSelection(width=(model.uiState.value.strokes.firstOrNull {it.id in model.uiState.value.selectedStrokeIds}?.toolConfig?.baseWidth ?: 4f)*1.2f)})
                DropdownMenuItem(text={Text("Move forward")},onClick={more=false;model.orderSelection(true)})
                DropdownMenuItem(text={Text("Move backward")},onClick={more=false;model.orderSelection(false)})
            }
        }
        IconButton(model::clearSelection) {Icon(Icons.Outlined.Close,"Deselect handwriting")}
    }
}
