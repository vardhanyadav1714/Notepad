package `in`.innovaticshub.notepad.ui.canvas.drawing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke as ComposeStroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import `in`.innovaticshub.notepad.media.PdfPageImages
import `in`.innovaticshub.notepad.ui.canvas.model.*
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import `in`.innovaticshub.notepad.ui.canvas.zoom.ZoomState
import kotlin.math.ceil
import kotlin.math.floor
import `in`.innovaticshub.notepad.ui.theme.LocalNoteAppearance
import `in`.innovaticshub.notepad.ui.canvas.zoom.PageEndPull
import `in`.innovaticshub.notepad.ui.canvas.zoom.NavigationIntent

private enum class SelectionDragMode { Move, Scale }
private data class SelectionHandle(val center:Offset,val pivot:Offset)

/** One gesture owner covers the page AND editable overlays. A multitouch sequence never commits ink. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ZoomableCanvas(viewModel:DrawingViewModel,modifier:Modifier=Modifier,backgroundColor:Color=Color.White,
    zoomState:ZoomState,isPanMode:Boolean=false,onZoomChanged:()->Unit={},onDoubleTap:(()->Unit)?=null,
    pdfBackground:ImageBitmap?=null,colourPicking:Boolean=false,onPickColour:((Offset)->Unit)?=null,
    isTextMode:Boolean=false,onDocumentTap:((Offset)->Unit)?=null,showDocumentTitle:Boolean=true,onStrokeFinished:(Stroke)->Unit={},
    continuousPages:Boolean=false,blockInkAt:((Offset)->Boolean)?=null,onAppendPage:(()->Unit)?=null,overlay:@Composable BoxScope.()->Unit={}) {
    val uiState by viewModel.uiState.collectAsState()
    val appearance=LocalNoteAppearance.current
    var currentStroke by remember {mutableStateOf<Stroke?>(null)}
    var eraserPreview by remember {mutableStateOf<Offset?>(null)}
    var eraserWidth by remember {mutableFloatStateOf(20f)}
    var appendPreview by remember {mutableStateOf(false)}
    val motionScope=rememberCoroutineScope()
    var motionJob by remember {mutableStateOf<Job?>(null)}
    val hardware=remember {HardwareInkSamples()}
    val latestPan by rememberUpdatedState(isPanMode)
    val latestText by rememberUpdatedState(isTextMode)
    val latestPicking by rememberUpdatedState(colourPicking)
    val latestPick by rememberUpdatedState(onPickColour)
    val latestTap by rememberUpdatedState(onDocumentTap)
    val latestZoom by rememberUpdatedState(onZoomChanged)
    val latestFinished by rememberUpdatedState(onStrokeFinished)
    val latestBlock by rememberUpdatedState(blockInkAt)
    val latestAppend by rememberUpdatedState(onAppendPage)
    val pageList=if(continuousPages) uiState.pages else listOf(uiState.currentPage)
    val layout=remember(pageList) {PageStackLayout(pageList)}
    val latestLayout by rememberUpdatedState(layout)
    val visiblePages by remember(layout,zoomState) {derivedStateOf {
        val top=zoomState.screenToCanvas(Offset.Zero).y
        val bottom=zoomState.screenToCanvas(Offset(0f,zoomState.viewportHeight)).y
        val intersect=layout.pages.indices.filter {i->val f=layout.frames.getValue(layout.pages[i].id);f.bottom>=top && f.top<=bottom}
        if(intersect.isEmpty()) listOfNotNull(layout.nearestPage(top))
        else layout.pages.subList((intersect.first()-1).coerceAtLeast(0),(intersect.last()+2).coerceAtMost(layout.pages.size))
    } }
    Box(modifier.fillMaxSize().clipToBounds().testTag("ink-canvas")
        .onSizeChanged {zoomState.updateViewportSize(it.width.toFloat(),it.height.toFloat())}
        .motionEventSpy(hardware::observe)
        .pointerInput(viewModel,zoomState,continuousPages) {
            awaitEachGesture {
                val first=awaitFirstDown(requireUnconsumed=false)
                motionJob?.cancel()
                val initial=viewModel.uiState.value
                var pen=first.type==PointerType.Stylus || first.type==PointerType.Eraser
                var penFinished=false
                var navigation=hardware.multiTouchGesture && !pen
                var started=false
                var blocked=false
                var selectionMode:SelectionDragMode?=null
                var pivot=Offset.Zero
                var pageId=initial.currentPageId
                var pageOrigin=latestLayout.origin(pageId)
                var last=first.position
                var lastLocal=zoomState.screenToCanvas(first.position)-pageOrigin
                var moved=0f
                var distance=0f
                var pinchStartDistance=0f
                var centroid=Offset.Zero
                var contactIds:Set<PointerId> = emptySet()
                var intent=NavigationIntent(viewConfiguration.touchSlop)
                var pinched=false
                val velocity=VelocityTracker()
                var trackedPosition=Offset.Zero
                var lastPanTime=first.uptimeMillis
                var lastEventTime=first.uptimeMillis
                var panned=false
                velocity.addPosition(first.uptimeMillis,trackedPosition)
                fun trackPan(delta:Offset,time:Long) {
                    trackedPosition+=delta;velocity.addPosition(time,trackedPosition)
                    if(delta!=Offset.Zero) {lastPanTime=time;panned=true}
                }
                val pullThreshold=40.dp.toPx()
                val endPull=PageEndPull(pullThreshold,zoomState.scale)
                var appended=false
                var navigationLayout=latestLayout
                fun boundNavigation(panY:Float,previousBottom:Float,zoomAttempt:Boolean=false) {
                    if(!continuousPages) return
                    if(latestAppend!=null) endPull.update(panY,previousBottom,zoomState.viewportHeight,zoomState.scale,zoomAttempt)
                    val nextPreview=latestAppend!=null && endPull.pullDistance>0f
                    appendPreview=nextPreview
                    val pages=viewModel.uiState.value.pages
                    if(pages!==navigationLayout.pages) navigationLayout=PageStackLayout(pages)
                    val extra=if(appendPreview) navigationLayout.pages.last().height+navigationLayout.gap else 0f
                    zoomState.constrainDocument(navigationLayout.width,navigationLayout.height+extra,0f,0f)
                }
                var tool=initial.currentTool
                val overlayHandled=first.isConsumed || latestBlock?.invoke(zoomState.screenToCanvas(first.position))==true
                fun cancel() {
                    if(started) viewModel.cancelStroke()
                    if(selectionMode!=null) viewModel.cancelSelectionTransform()
                    started=false;selectionMode=null;currentStroke=null;eraserPreview=null
                }
                fun locate(position:Offset):Offset? {
                    val world=zoomState.screenToCanvas(position)
                    val page=latestLayout.pageAt(world) ?: return null
                    pageId=page.id;pageOrigin=latestLayout.origin(page.id)
                    viewModel.activatePage(page.id)
                    return world-pageOrigin
                }
                fun begin(position:Offset,pressure:Float,time:Long,eraser:Boolean=false) {
                    val point=locate(position) ?: return
                    tool=if(eraser) ToolConfig.MEDIUM_ERASER.copy(eraserMode=initial.currentTool.eraserMode) else initial.currentTool
                    val bounds=viewModel.uiState.value.selectionBounds()
                    lastLocal=point
                    if(tool.type==ToolType.LASSO && bounds!=null) {
                        val radius=28f/zoomState.scale
                        val handle=bounds.selectionHandles(zoomState.scale).minByOrNull {(it.center-point).getDistance()}?.takeIf {(it.center-point).getDistance()<=radius}
                        if(handle!=null || bounds.expanded(12f/zoomState.scale).contains(point)) {
                            selectionMode=if(handle!=null) SelectionDragMode.Scale else SelectionDragMode.Move
                            pivot=handle?.pivot ?: bounds.center;viewModel.beginSelectionTransform();return
                        }
                    }
                    val raw=if(pen) hardware.samples.lastOrNull() else null
                    viewModel.startStroke(point.x,point.y,pressure,minPointDistance=(0.6f/zoomState.scale).coerceIn(0.05f,1.5f),
                        toolOverride=tool,timestamp=time,tilt=raw?.tilt ?: 0f,orientation=raw?.orientation ?: 0f)
                    started=true
                    currentStroke=viewModel.addStrokePoint(point.x,point.y,pressure,time,raw?.tilt ?: 0f,raw?.orientation ?: 0f)
                    eraserWidth=tool.strokeWidth()
                }
                fun finishStroke() {
                    if(started) {val finished=currentStroke;viewModel.endStroke();finished?.let(latestFinished)}
                    started=false;currentStroke=null;eraserPreview=null
                }
                if(latestPicking) {
                    locate(first.position)?.let {latestPick?.invoke(it)}
                    do {val e=awaitPointerEvent();e.changes.forEach {it.consume()}} while(e.changes.any {it.pressed})
                    return@awaitEachGesture
                }
                if(!navigation && !latestText && !latestPan && !overlayHandled && (pen || (initial.fingerDrawing && !hardware.stylusNearby)))
                    begin(first.position,if(pen) first.pressure else 1f,first.uptimeMillis,first.type==PointerType.Eraser || (pen && hardware.barrelPressed))
                try {
                    do {
                        val event=awaitPointerEvent()
                        lastEventTime=event.changes.maxOf {it.uptimeMillis}
                        val pressed=event.changes.filter {it.pressed}
                        val stylus=event.changes.firstOrNull {(it.type==PointerType.Stylus || it.type==PointerType.Eraser) && (it.pressed || it.previousPressed)}
                        if(hardware.cancelled) {cancel();blocked=true}
                        if(viewModel.uiState.value.currentPageId!=pageId && (started || selectionMode!=null)) {cancel();blocked=true}
                        if(stylus!=null && !penFinished && !blocked) {
                            if(!pen) {
                                cancel();pen=true;navigation=false
                                if(!latestText && !latestPan) begin(stylus.position,stylus.pressure,stylus.uptimeMillis,stylus.type==PointerType.Eraser || hardware.barrelPressed)
                            }
                            val point=zoomState.screenToCanvas(stylus.position)-pageOrigin
                            if(selectionMode!=null) {
                                if(selectionMode==SelectionDragMode.Move) {val delta=point-lastLocal;viewModel.moveSelectedStrokesBy(delta.x,delta.y)}
                                else {val previous=(lastLocal-pivot).getDistance();if(previous>0.01f) viewModel.scaleSelectedStrokesBy((point-pivot).getDistance()/previous,pivot.x,pivot.y)}
                                lastLocal=point
                            } else if(latestPan) {zoomState.panBy(stylus.positionChange());latestZoom()}
                            else if(started) {
                                val samples=hardware.samples.ifEmpty {listOf(StrokePoint(stylus.position.x,stylus.position.y,stylus.pressure,stylus.uptimeMillis))}
                                currentStroke=viewModel.addStrokePoints(samples.map {raw->val p=zoomState.screenToCanvas(Offset(raw.x,raw.y))-pageOrigin;raw.copy(x=p.x,y=p.y)})
                                if(tool.isEraser) eraserPreview=stylus.position
                            }
                            event.changes.forEach {it.consume()}
                            if(!stylus.pressed) {finishStroke();if(selectionMode!=null) viewModel.endSelectionTransform();selectionMode=null;penFinished=true}
                        } else if(pen || penFinished || blocked) {
                            event.changes.forEach {it.consume()}
                        } else if(pressed.size>=2 || hardware.multiTouchGesture) {
                            if(!navigation) {cancel();navigation=true}
                            if(pressed.size>=2) {
                                val a=pressed[0];val b=pressed[1]
                                val d=(a.position-b.position).getDistance();val c=(a.position+b.position)/2f
                                val ids=setOf(a.id,b.id)
                                if(ids!=contactIds) {
                                    pinchStartDistance=d;intent=NavigationIntent(viewConfiguration.touchSlop)
                                    velocity.resetTracking();trackedPosition=Offset.Zero
                                    velocity.addPosition(a.uptimeMillis,trackedPosition)
                                }
                                val previousBottom=navigationLayout.height*zoomState.scale+zoomState.offset.y
                                if(ids==contactIds && distance>=8.dp.toPx() && d>=8.dp.toPx()) {
                                    val wasZooming=intent.zooming
                                    val zooming=intent.update(d-distance,(c-centroid).getDistance())
                                    val factor=if(!zooming || viewModel.uiState.value.zoomLocked) 1f else d/(if(wasZooming) distance else pinchStartDistance)
                                    pinched=pinched || zooming
                                    zoomState.transform(c,factor,c-centroid)
                                    trackPan(c-centroid,a.uptimeMillis)
                                    boundNavigation((c-centroid).y,previousBottom,zooming)
                                }
                                distance=d;centroid=c;contactIds=ids
                                latestZoom()
                            } else if(pressed.size==1) {
                                // Continue navigation when one finger lifts; never restart ink.
                                val change=pressed.single();val delta=change.positionChange()
                                val previousBottom=navigationLayout.height*zoomState.scale+zoomState.offset.y
                                zoomState.panBy(delta);trackPan(delta,change.uptimeMillis)
                                boundNavigation(delta.y,previousBottom,pinched);latestZoom()
                                contactIds=emptySet()
                            }
                            event.changes.forEach {it.consume()}
                        } else if(!navigation) {
                            val change=event.changes.firstOrNull {it.id==first.id} ?: event.changes.first()
                            val delta=change.position-last;last=change.position;moved+=delta.getDistance()
                            if(selectionMode!=null) {
                                val point=zoomState.screenToCanvas(change.position)-pageOrigin
                                if(selectionMode==SelectionDragMode.Move) {val shift=point-lastLocal;viewModel.moveSelectedStrokesBy(shift.x,shift.y)}
                                else {val previous=(lastLocal-pivot).getDistance();if(previous>0.01f) viewModel.scaleSelectedStrokesBy((point-pivot).getDistance()/previous,pivot.x,pivot.y)}
                                lastLocal=point;change.consume()
                            } else if(overlayHandled || change.isConsumed) {
                                cancel()
                            } else if(latestPan || !initial.fingerDrawing || hardware.stylusNearby || (latestText && moved>viewConfiguration.touchSlop)) {
                                cancel()
                                val previousBottom=navigationLayout.height*zoomState.scale+zoomState.offset.y
                                zoomState.panBy(delta);boundNavigation(delta.y,previousBottom)
                                trackPan(delta,change.uptimeMillis)
                                latestZoom();change.consume()
                            } else if(started) {
                                val samples=change.historical.map {h->val p=zoomState.screenToCanvas(h.position)-pageOrigin;StrokePoint(p.x,p.y,1f,h.uptimeMillis)}
                                val p=zoomState.screenToCanvas(change.position)-pageOrigin
                                currentStroke=viewModel.addStrokePoints(samples+StrokePoint(p.x,p.y,1f,change.uptimeMillis))
                                if(tool.isEraser) eraserPreview=change.position
                                change.consume()
                            }
                        }
                    } while(event.changes.any {it.pressed})
                    if(navigation || hardware.cancelled) cancel()
                    else {
                        finishStroke()
                        if(selectionMode!=null) {viewModel.endSelectionTransform();selectionMode=null}
                        if(latestText && !overlayHandled && moved<=viewConfiguration.touchSlop) locate(last)?.let {latestTap?.invoke(it)}
                    }
                    // A page is committed only after lift. A late second finger can still turn
                    // an end pull into a pinch without changing document history or collaboration.
                    if(!hardware.cancelled && endPull.canCommit && latestAppend!=null) {latestAppend?.invoke();appended=true}
                    if(continuousPages && !viewModel.uiState.value.hasSelection && viewModel.uiState.value.activeTextId==null && (navigation || latestPan || !initial.fingerDrawing)) {
                        val finalLayout=if(appended) PageStackLayout(viewModel.uiState.value.pages) else latestLayout
                        finalLayout.nearestPage(zoomState.screenToCanvas(Offset(0f,zoomState.viewportHeight*0.35f)).y)?.let {viewModel.activatePage(it.id)}
                    }
                    if(continuousPages && panned && !pinched && !hardware.cancelled && (!appendPreview || appended) &&
                        lastEventTime-lastPanTime<100L) {
                        val speed=velocity.calculateVelocity()
                        if(Offset(speed.x,speed.y).getDistance()>80.dp.toPx()) motionJob=motionScope.launch {
                            var v=Offset(speed.x,speed.y)
                            var previous=withFrameNanos {it}
                            val startedAt=previous
                            while(v.getDistance()>20.dp.toPx()) {
                                val now=withFrameNanos {it}
                                val dt=((now-previous)/1_000_000_000f).coerceAtMost(0.05f);previous=now
                                val before=zoomState.offset
                                val decay=kotlin.math.exp(-5f*dt)
                                zoomState.panBy(v*((1f-decay)/5f))
                                val bounds=latestLayout
                                // Momentum may browse existing pages, but cannot create blank pages.
                                zoomState.constrainDocument(bounds.width,bounds.height,0f,0f)
                                val actual=zoomState.offset-before
                                v=Offset(if(kotlin.math.abs(actual.x)<0.01f) 0f else v.x*decay,if(kotlin.math.abs(actual.y)<0.01f) 0f else v.y*decay)
                                if(now-startedAt>2_000_000_000L) break
                            }
                        }
                    }
                } finally {
                    if(viewModel.uiState.value.isDrawing) viewModel.cancelStroke()
                    if(selectionMode!=null) viewModel.cancelSelectionTransform()
                    if(appendPreview) {
                        appendPreview=false
                        if(!appended) {
                            val from=zoomState.offset
                            zoomState.constrainDocument(navigationLayout.width,navigationLayout.height,0f,0f)
                            val target=zoomState.offset
                            zoomState.setOffset(from)
                            motionJob=motionScope.launch {animate(0f,1f,animationSpec=tween(180)) {fraction,_->zoomState.setOffset(from+(target-from)*fraction)}}
                        }
                    }
                    currentStroke=null;eraserPreview=null
                }
            }
        }) {
        Canvas(Modifier.fillMaxSize()) {drawRect(appearance.surround)}
        visiblePages.forEach {page->key(page.id) {
            RecordedPageLayer(page,layout.origin(page.id),viewModel,zoomState,backgroundColor,
                if(page.id==uiState.currentPageId) pdfBackground else null,
                if(isTextMode) uiState.activeTextId else null,
                if(showDocumentTitle && page.id==uiState.pages.first().id) uiState.documentTitle else null,
                if(currentStroke?.pageId==page.id && currentStroke?.toolConfig?.isEraser==true) currentStroke else null)
        } }
        CollaborationOverlay(viewModel, zoomState, layout, backgroundColor)
        if(appendPreview) {
            val lastPage=uiState.pages.last()
            val preview=remember(lastPage) {lastPage.copy(id="preview-next-page",pdfPath=null,pdfPageIndex=0)}
            RecordedPageLayer(preview,Offset((layout.width-preview.width)/2,layout.height+layout.gap),viewModel,zoomState,backgroundColor,null,null,null,null)
        }
        Canvas(Modifier.fillMaxSize()) {
            val active=currentStroke
            val origin=layout.origin(active?.pageId ?: uiState.currentPageId)
            withTransform({translate(zoomState.offset.x+origin.x*zoomState.scale,zoomState.offset.y+origin.y*zoomState.scale);scale(zoomState.scale,zoomState.scale,Offset.Zero)}) {
                if(active!=null && !active.toolConfig.isEraser) renderStroke(active,darkPaper=isDarkPaper(uiState.currentPage,appearance.paper(backgroundColor)),inkColor=appearance.ink(active.toolConfig.color,uiState.currentPage.pdfPath!=null))
            }
            uiState.selectionBounds()?.let {bounds->val selectedOrigin=layout.origin(uiState.currentPageId)
                withTransform({translate(zoomState.offset.x+selectedOrigin.x*zoomState.scale,zoomState.offset.y+selectedOrigin.y*zoomState.scale);scale(zoomState.scale,zoomState.scale,Offset.Zero)}) {drawSelectionOverlay(bounds,zoomState.scale)}
            }
            eraserPreview?.let {center->val radius=eraserWidth/2*zoomState.scale;drawCircle(Color.White.copy(alpha=0.4f),radius,center);drawCircle(Color(0xFF5D82C6),radius,center,style=ComposeStroke(1.5.dp.toPx()))}
        }
        overlay()
    }
}

/** Static page commands are retained in a render layer. Pinch changes only its transform. */
@Composable
private fun RecordedPageLayer(page:CanvasPage,origin:Offset,model:DrawingViewModel,zoom:ZoomState,background:Color,pdf:ImageBitmap?,editing:String?,title:String?,eraser:Stroke?) {
    val state by model.uiState.collectAsState()
    val appearance=LocalNoteAppearance.current
    val paper=appearance.paper(background,page.pdfPath!=null)
    val context=LocalContext.current
    val strokes=remember(state.strokes,page.id) {state.strokes.filter {it.pageId==page.id}}
    val texts=remember(state.texts,page.id) {state.texts.filter {it.pageId==page.id}}
    val images=remember(state.images,page.id) {state.images.filter {it.pageId==page.id}}
    val displayTexts=remember(texts,appearance,page.pdfPath) {texts.map {appearance.text(it,page.pdfPath!=null)}}
    var bitmaps by remember(page.id) {mutableStateOf<Map<String,ImageBitmap>>(emptyMap())}
    var renderedPdf by remember(page.pdfPath,page.pdfPageIndex) {mutableStateOf<ImageBitmap?>(null)}
    LaunchedEffect(images.map {it.path}) {bitmaps=withContext(Dispatchers.IO) {loadPageImageBitmaps(context,images)}}
    LaunchedEffect(page.pdfPath,page.pdfPageIndex,pdf) {
        renderedPdf=pdf ?: page.pdfPath?.let {path->withContext(Dispatchers.IO) {runCatching {PdfPageImages.render(context,path,page.pdfPageIndex).asImageBitmap()}.getOrNull()}}
    }
    Canvas(Modifier.fillMaxSize().graphicsLayer {
        translationX=zoom.offset.x+origin.x*zoom.scale;translationY=zoom.offset.y+origin.y*zoom.scale
        scaleX=zoom.scale;scaleY=zoom.scale;transformOrigin=TransformOrigin(0f,0f);clip=false
    }) {
        renderPaper(page,paper,state.paperStyle,renderedPdf)
        clipRect(0f,0f,page.width,page.height) {
            renderPageContent(displayTexts,images,bitmaps,editing,title,pageTextColor(paper))
            val visible=if(eraser!=null) `in`.innovaticshub.notepad.ui.canvas.engine.StrokeEraser.erase(strokes,eraser) else strokes
            visible.forEach {renderStroke(it,darkPaper=isDarkPaper(page,paper),inkColor=appearance.ink(it.toolConfig.color,page.pdfPath!=null))}
        }
    }
}

private fun computeSelectedBounds(strokes: List<Stroke>, selectedIds: Set<String>): RectF? {
    val selected = strokes.filter { it.id in selectedIds }
    if (selected.isEmpty()) return null
    return RectF(
        left = selected.minOf { it.bounds.left },
        top = selected.minOf { it.bounds.top },
        right = selected.maxOf { it.bounds.right },
        bottom = selected.maxOf { it.bounds.bottom }
    )
}

private val RectF.topLeft: Offset
    get() = Offset(left, top)

private val RectF.topRight: Offset
    get() = Offset(right, top)

private val RectF.bottomLeft: Offset
    get() = Offset(left, bottom)

private val RectF.bottomRight: Offset
    get() = Offset(right, bottom)

private val RectF.center: Offset
    get() = Offset((left + right) / 2f, (top + bottom) / 2f)

private fun RectF.expanded(amount: Float): RectF {
    return RectF(left - amount, top - amount, right + amount, bottom + amount)
}

private fun RectF.contains(point: Offset): Boolean {
    return point.x in left..right && point.y in top..bottom
}

private fun RectF.selectionHandles(scale: Float): List<SelectionHandle> {
    val padding = selectionPadding(scale)
    val padded = expanded(padding)
    return listOf(
        SelectionHandle(center = padded.topLeft, pivot = padded.bottomRight),
        SelectionHandle(center = padded.topRight, pivot = padded.bottomLeft),
        SelectionHandle(center = padded.bottomLeft, pivot = padded.topRight),
        SelectionHandle(center = padded.bottomRight, pivot = padded.topLeft)
    )
}

private fun selectionPadding(scale: Float): Float {
    return (12f / scale).coerceIn(4f, 18f)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAdaptiveGrid(
    scale: Float,
    offset: Offset,
    isDark: Boolean
) {
    var spacing = 40f
    while (spacing * scale < 24f) spacing *= 2f
    while (spacing * scale > 96f) spacing /= 2f

    val left = -offset.x / scale - spacing * 2f
    val top = -offset.y / scale - spacing * 2f
    val right = left + size.width / scale + spacing * 4f
    val bottom = top + size.height / scale + spacing * 4f
    val lineWidth = (1f / scale).coerceIn(0.2f, 1.5f)
    val color = if (isDark) {
        Color.White.copy(alpha = 0.09f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }

    var x = floor(left / spacing) * spacing
    val lastX = ceil(right / spacing) * spacing
    while (x <= lastX) {
        drawLine(color, Offset(x, top), Offset(x, bottom), lineWidth)
        x += spacing
    }
    var y = floor(top / spacing) * spacing
    val lastY = ceil(bottom / spacing) * spacing
    while (y <= lastY) {
        drawLine(color, Offset(left, y), Offset(right, y), lineWidth)
        y += spacing
    }
}

private fun Color.luminance(): Float {
    return red * 0.299f + green * 0.587f + blue * 0.114f
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSelectionOverlay(
    bounds: RectF,
    scale: Float
) {
    val padding = selectionPadding(scale)
    val left = bounds.left - padding
    val top = bounds.top - padding
    val right = bounds.right + padding
    val bottom = bounds.bottom + padding
    val handleRadius = (10f / scale).coerceIn(4f, 10f)
    val strokeWidth = (2f / scale).coerceIn(1.2f, 2.6f)

    drawRect(
        color = Color(0xFF007AFF).copy(alpha = 0.09f),
        topLeft = Offset(left, top),
        size = Size((right - left).coerceAtLeast(1f), (bottom - top).coerceAtLeast(1f))
    )
    drawRect(
        color = Color(0xFF007AFF),
        topLeft = Offset(left, top),
        size = Size((right - left).coerceAtLeast(1f), (bottom - top).coerceAtLeast(1f)),
        style = ComposeStroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f / scale, 8f / scale))
        )
    )

    listOf(
        Offset(left, top),
        Offset(right, top),
        Offset(left, bottom),
        Offset(right, bottom)
    ).forEachIndexed { index, center ->
        drawCircle(Color.White, radius = handleRadius * 1.25f, center = center)
        drawCircle(Color(0xFF007AFF), radius = handleRadius, center = center)
        if (index == 3) {
            drawCircle(
                color = Color.White,
                radius = handleRadius * 0.42f,
                center = center,
                style = ComposeStroke(width = strokeWidth)
            )
        }
    }
}
