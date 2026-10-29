package `in`.innovaticshub.notepad.ui.canvas.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import android.content.Context
import androidx.compose.ui.geometry.Offset
import `in`.innovaticshub.notepad.ui.canvas.model.*
import `in`.innovaticshub.notepad.ui.canvas.engine.InkGeometry
import `in`.innovaticshub.notepad.ui.canvas.engine.InkClipboard
import `in`.innovaticshub.notepad.ui.canvas.engine.ShapeGeometry
import `in`.innovaticshub.notepad.ui.canvas.storage.DrawingPreferences
import `in`.innovaticshub.notepad.ui.canvas.storage.SavedSession
import kotlin.math.*
import java.util.UUID
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.innovaticshub.notepad.ui.canvas.collab.CollaborationRepository
import `in`.innovaticshub.notepad.ui.canvas.collab.CollaborationSubscription
import `in`.innovaticshub.notepad.ui.canvas.collab.RemoteCanvasState
import `in`.innovaticshub.notepad.ui.canvas.collab.MemberCursor
import `in`.innovaticshub.notepad.ui.canvas.collab.RoomPresenceState
import `in`.innovaticshub.notepad.ui.canvas.collab.PeerInkPreview
import `in`.innovaticshub.notepad.ui.canvas.collab.toLocalStroke
import `in`.innovaticshub.notepad.ui.canvas.collab.toRemoteStroke
import `in`.innovaticshub.notepad.ui.canvas.engine.CanvasHistory
import `in`.innovaticshub.notepad.ui.canvas.engine.StrokeEraser
import `in`.innovaticshub.notepad.ui.canvas.model.RectF
import `in`.innovaticshub.notepad.ui.canvas.model.Stroke
import `in`.innovaticshub.notepad.ui.canvas.model.StrokeBuilder
import `in`.innovaticshub.notepad.ui.canvas.model.StrokePoint
import `in`.innovaticshub.notepad.ui.canvas.model.ToolConfig
import `in`.innovaticshub.notepad.ui.canvas.model.ToolType
import `in`.innovaticshub.notepad.ui.canvas.model.ToolConfig.Companion.defaultForType
import `in`.innovaticshub.notepad.ui.canvas.zoom.ZoomState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DrawingUiState(
    val strokes: List<Stroke> = emptyList(),
    val currentTool: ToolConfig = ToolConfig.MEDIUM_PEN,
    val isDrawing: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val canvasBackgroundColor: Color = Color(0xFFFAFAFA),
    val showGrid: Boolean = false,
    val recentColors: List<Color> = defaultRecentColors(),
    val showToolSettings: Boolean = false,
    val selectedStrokeIds: Set<String> = emptySet(),
    val selectedTextIds:Set<String> = emptySet(),val selectedImageIds:Set<String> = emptySet(),val includePageObjects:Boolean=true,
    val collaborationRoomCode: String? = null,
    val collaborationStatus: String? = null,
    val isCollaborationBusy: Boolean = false,
    val collaborationConnected:Boolean=false,
    val sharingApproved:Boolean=false,
    val fingerDrawing: Boolean = true,
    val autoShape: Boolean = false,
    val selectionShape: SelectionShape = SelectionShape.LASSO,
    val includePartialSelection: Boolean = true,
    val zoomLocked:Boolean=false,val contentError:String?=null,
    val paperStyle: PaperStyle = PaperStyle.PLAIN,
    val favourites: List<ToolConfig> = emptyList(),
    val pages: List<CanvasPage> = listOf(CanvasPage()),
    val currentPageId: String = "page-1",
    val assets: Set<String> = emptySet(),
    val texts:List<PageText> = emptyList(),val images:List<PageImage> = emptyList(),val audio:List<PageAudio> = emptyList(),
    val importedBlocks:Set<String> = emptySet(),val activeTextId:String?=null,val activeImageId:String?=null,val documentTitle:String=""
) {
    val visibleStrokes: List<Stroke> get() = strokes.filter { it.pageId == currentPageId }
    val currentPage: CanvasPage get() = pages.firstOrNull { it.id == currentPageId } ?: pages.first()
    val visibleTexts get()=texts.filter {it.pageId==currentPageId}
    val visibleImages get()=images.filter {it.pageId==currentPageId}
    val hasSelection get()=selectedStrokeIds.isNotEmpty() || selectedTextIds.isNotEmpty() || selectedImageIds.isNotEmpty()
    fun selectionBounds():RectF? {
        val boxes=visibleStrokes.filter {it.id in selectedStrokeIds}.map {it.bounds}+
            visibleTexts.filter {it.id in selectedTextIds}.map {RectF(it.x,it.y,it.x+it.width,it.y+it.height)}+
            visibleImages.filter {it.id in selectedImageIds}.map {it.bounds()}
        if(boxes.isEmpty()) return null
        return RectF(boxes.minOf {it.left},boxes.minOf {it.top},boxes.maxOf {it.right},boxes.maxOf {it.bottom})
    }
    fun savedSession() = SavedSession(strokes,canvasBackgroundColor,currentTool,paperStyle,pages,currentPageId,autoShape,assets,texts,images,audio,importedBlocks,documentTitle,collaborationRoomCode)
    companion object {
        fun defaultRecentColors() = listOf(
            Color(0xFF1C1C1E),
            Color(0xFF007AFF),
            Color(0xFFFF3B30),
            Color(0xFF34C759),
            Color(0xFFFF9500)
        )
    }
}

class DrawingViewModel(private val collaborationRepository:CollaborationRepository=CollaborationRepository()) : ViewModel() {
    private var collaborationJob: kotlinx.coroutines.Job? = null
    private var sharingSafety: `in`.innovaticshub.notepad.ui.canvas.collab.SharingSafetyStore? = null
    /** Called by the parental approval UI after successful PIN verification. */
    fun approveSharingForSession() { _uiState.update { it.copy(sharingApproved = true) } }
    fun pauseCollaboration() {
        val room = _uiState.value.collaborationRoomCode
        leaveCollaborationRoom()
        _uiState.update { it.copy(collaborationRoomCode = room, collaborationStatus = "Sharing paused. Ask an adult to approve reconnecting.") }
    }
    private fun sharingAllowed(): Boolean {
        val allowed = _uiState.value.sharingApproved && (sharingSafety?.enabled != false)
        if (!allowed) _uiState.update { it.copy(collaborationStatus = "Ask an adult to approve sharing first.") }
        return allowed
    }
    fun offerCollaborationInvite(room: String) {
        if (_uiState.value.collaborationRoomCode != room) pauseCollaboration()
        _uiState.update { it.copy(collaborationRoomCode = room, collaborationStatus = "Invite ready. Ask an adult to approve joining from Collaboration.") }
    }
    fun canEndCollaboration() = _uiState.value.collaborationRoomCode?.let(collaborationRepository::canEndRoom) == true
    fun endCollaborationRoom() {
        val room = _uiState.value.collaborationRoomCode ?: return
        if (!sharingAllowed()) return
        viewModelScope.launch {
            runCatching { collaborationRepository.endRoom(room) }.onSuccess { leaveCollaborationRoom() }
                .onFailure { error -> _uiState.update { it.copy(collaborationStatus = error.message) } }
        }
    }
    fun blockCollaborationRoom() {
        val room = _uiState.value.collaborationRoomCode ?: return
        leaveCollaborationRoom()
        viewModelScope.launch {
            runCatching { collaborationRepository.blockRoom(room) }.onSuccess {
                _uiState.update { it.copy(collaborationStatus = "Room blocked on this device. Invites to it cannot reconnect.") }
            }.onFailure { error -> _uiState.update { it.copy(collaborationStatus = error.message) } }
        }
    }
    fun reportCollaborationRoom(reason: String) {
        val room = _uiState.value.collaborationRoomCode ?: return
        // A child can report or stop sharing without a PIN. No message or email
        // address is requested, and a report is acknowledged only after storage.
        pauseCollaboration()
        viewModelScope.launch {
            runCatching { collaborationRepository.reportRoom(room, reason) }.onSuccess { report ->
                _uiState.update { it.copy(collaborationStatus = "Report saved: $report. Sharing is paused. Ask an adult for help.") }
            }.onFailure { _uiState.update { it.copy(collaborationStatus = "Report was not saved. Sharing is paused; retry or ask an adult to contact support.") } }
        }
    }
    private val _presence = MutableStateFlow(RoomPresenceState())
    val presence: StateFlow<RoomPresenceState> = _presence.asStateFlow()
    private var lastLiveUpdateNanos = 0L
    private var presenceExpiry: kotlinx.coroutines.Job? = null

    private fun publishLiveStroke(force: Boolean = false) {
        val state = _uiState.value
        val room = state.collaborationRoomCode ?: return
        if (!state.collaborationConnected) return
        val now = System.nanoTime()
        if (!force && now - lastLiveUpdateNanos < 120_000_000L) return
        lastLiveUpdateNanos = now
        val stroke = currentStroke ?: return
        val point = stroke.points.lastOrNull() ?: return
        val activity = when { stroke.toolConfig.isEraser -> "erasing"; stroke.toolConfig.type == ToolType.LASSO -> "selecting"; else -> "drawing" }
        val preview = if (activity != "drawing") null else {
            val points = if (stroke.points.size <= 128) stroke.points else (0 until 128).map { stroke.points[it * (stroke.points.size - 1) / 127] }
            stroke.copy(points = points).toRemoteStroke(collaborationRepository.currentUserId)
        }
        collaborationRepository.sendLiveUpdate(room, MemberCursor(point.x, point.y, stroke.pageId), activity, preview)
    }

    private fun stopLiveStroke(cancelled: Boolean) {
        _uiState.value.collaborationRoomCode?.let { room ->
            collaborationRepository.sendLiveUpdate(room, null, "idle", null, sendPreview = cancelled)
        }
        lastLiveUpdateNanos = 0
    }
    private var preferences: DrawingPreferences? = null
    private var appContext:Context?=null
    private val rememberedTools = mutableMapOf<ToolType, ToolConfig>()
    private val rememberedPens = mutableMapOf<String, ToolConfig>()
    private fun penKey(tool:ToolConfig) = "${tool.type}:${tool.style}"

    fun loadPreferences(context: Context) {
        sharingSafety = `in`.innovaticshub.notepad.ui.canvas.collab.SharingSafetyStore(context)
        collaborationRepository.initializeSafety(context)
        appContext=context.applicationContext
        preferences = DrawingPreferences(context).also { store ->
            rememberedTools.putAll(store.rememberedTools())
            (rememberedTools.values+store.penPresets()).forEach {rememberedPens[penKey(it)]=it}
            _uiState.update { it.copy(favourites = store.favourites(), fingerDrawing = store.fingerDrawing,zoomLocked=store.zoomLocked) }
        }
    }

    fun snapshot(): SavedSession = _uiState.value.savedSession()

    fun restoreSession(session: SavedSession) {
        restoreSession(session.strokes,session.canvasBackgroundColor,session.tool)
        _uiState.update { it.copy(paperStyle=session.paperStyle,pages=session.pages,currentPageId=session.currentPageId,autoShape=session.autoShape,assets=session.assets,
            texts=session.texts,images=session.images,audio=session.audio,importedBlocks=session.importedBlocks,documentTitle=session.title,collaborationRoomCode=session.collaborationRoom,collaborationConnected=false) }
    }

    fun setFingerDrawing(value:Boolean) { _uiState.update { it.copy(fingerDrawing=value) }; preferences?.fingerDrawing=value }
    fun setAutoShape(value:Boolean) { _uiState.update { it.copy(autoShape=value) } }
    fun setDocumentTitle(title:String) {_uiState.update {it.copy(documentTitle=title)}}
    fun setZoomLocked(value:Boolean) {_uiState.update {it.copy(zoomLocked=value)};preferences?.zoomLocked=value}
    fun dismissContentError() {_uiState.update {it.copy(contentError=null)}}
    fun extendPaper(height:Float) {_uiState.update {state->state.copy(pages=state.pages.map {if(it.id==state.currentPageId && it.pdfPath==null) it.copy(height=maxOf(it.height,height)) else it})}}
    fun fitBlankPaperHeight(height:Float) {
        val state=_uiState.value;val page=state.currentPage
        if(page.pdfPath!=null || state.visibleStrokes.isNotEmpty() || !height.isFinite() || height<=page.height) return
        _uiState.update {it.copy(pages=it.pages.map {p->if(p.id==page.id) p.copy(height=height) else p})}
    }
    fun setSelectionShape(value:SelectionShape) { clearSelection(); _uiState.update {it.copy(selectionShape=value)} }
    fun setIncludePartialSelection(value:Boolean) { _uiState.update {it.copy(includePartialSelection=value)} }
    fun setIncludePageObjects(value:Boolean) {clearSelection();_uiState.update {it.copy(includePageObjects=value)}}
    fun setPaper(style:PaperStyle) {
        if(_uiState.value.paperStyle==style) return
        history.pushBeforeChange(snapshot()); _uiState.update {it.copy(paperStyle=style,showGrid=false,canUndo=history.canUndo,canRedo=history.canRedo)}
    }
    fun setBackground(color:Color) {
        if(_uiState.value.canvasBackgroundColor==color) return
        history.pushBeforeChange(snapshot()); _uiState.update {it.copy(canvasBackgroundColor=color,canUndo=history.canUndo,canRedo=history.canRedo)}
    }
    fun addFavourite() {
        val state=_uiState.value
        if(state.currentTool in state.favourites) return
        val next=state.favourites+state.currentTool
        _uiState.update { it.copy(favourites=next) }; preferences?.saveFavourites(next)
    }
    fun removeFavourite(tool:ToolConfig) { val next=_uiState.value.favourites-tool; _uiState.update { it.copy(favourites=next) }; preferences?.saveFavourites(next) }
    fun addPage(landscape:Boolean=false,matchLastPage:Boolean=false) {
        finishTextEditing()
        history.pushBeforeChange(snapshot())
        val last=_uiState.value.pages.last()
        val page=CanvasPage(id=UUID.randomUUID().toString(),width=if(matchLastPage) last.width else if(landscape) 1414f else 1000f,
            height=if(matchLastPage) last.height else if(landscape) 1000f else 1414f)
        _uiState.update { it.copy(pages=it.pages+page,currentPageId=page.id,selectedStrokeIds=emptySet(),selectedTextIds=emptySet(),selectedImageIds=emptySet(),canUndo=history.canUndo,canRedo=history.canRedo) }
    }
    fun duplicatePage(id:String) {
        finishTextEditing()
        val state=_uiState.value; val source=state.pages.firstOrNull {it.id==id} ?: return
        history.pushBeforeChange(snapshot())
        val page=source.copy(id=UUID.randomUUID().toString())
        val pages=state.pages.toMutableList().apply {add(indexOf(source)+1,page)}
        val strokes=state.strokes.filter {it.pageId==id}.map {it.copy(id=Stroke.generateId(),pageId=page.id)}
        _uiState.update {it.copy(pages=pages,strokes=it.strokes+strokes,currentPageId=page.id,selectedStrokeIds=emptySet(),selectedTextIds=emptySet(),selectedImageIds=emptySet(),canUndo=history.canUndo,canRedo=history.canRedo,
            texts=it.texts+it.texts.filter {t->t.pageId==id}.map {t->t.copy(id=UUID.randomUUID().toString(),pageId=page.id)},
            images=it.images+it.images.filter {t->t.pageId==id}.map {t->t.copy(id=UUID.randomUUID().toString(),pageId=page.id)},activeTextId=null,activeImageId=null)}
    }
    fun movePage(id:String,offset:Int) {
        val state=_uiState.value; val index=state.pages.indexOfFirst {it.id==id}; val target=index+offset
        if(index<0 || target !in state.pages.indices) return
        history.pushBeforeChange(snapshot())
        val pages=state.pages.toMutableList(); val page=pages.removeAt(index);pages.add(target,page)
        _uiState.update {it.copy(pages=pages,canUndo=history.canUndo,canRedo=history.canRedo)}
    }
    fun deletePage(id:String) {
        finishTextEditing()
        val state=_uiState.value
        if(state.pages.size<=1 || state.pages.none {it.id==id}) return
        history.pushBeforeChange(snapshot())
        val pages=state.pages.filterNot {it.id==id}
        _uiState.update {it.copy(pages=pages,strokes=it.strokes.filterNot {stroke->stroke.pageId==id},currentPageId=if(state.currentPageId==id) pages.first().id else state.currentPageId,
            selectedStrokeIds=emptySet(),selectedTextIds=emptySet(),selectedImageIds=emptySet(),canUndo=history.canUndo,canRedo=history.canRedo,
            texts=it.texts.filterNot {t->t.pageId==id},images=it.images.filterNot {t->t.pageId==id},activeTextId=null,activeImageId=null)}
    }
    fun openPage(id:String,resetViewport:Boolean=true) {
        finishTextEditing()
        if(_uiState.value.pages.none { it.id==id }) return
        cancelStroke(); _uiState.update { it.copy(currentPageId=id,selectedStrokeIds=emptySet(),selectedTextIds=emptySet(),selectedImageIds=emptySet(),activeTextId=null,activeImageId=null) }; if(resetViewport) zoomState.reset()
    }
    fun activatePage(id:String) {if(id!=_uiState.value.currentPageId) openPage(id,false)}
    fun appendPdf(path:String, sizes:List<Pair<Int,Int>>) {
        val imported=sizes.mapIndexed { index,size -> CanvasPage(UUID.randomUUID().toString(),path,index,1000f,1000f*size.second/size.first.coerceAtLeast(1)) }
        if(imported.isEmpty()) return
        history.pushBeforeChange(snapshot())
        _uiState.update { state ->
            val existing=if(state.pages.size==1 && state.strokes.isEmpty() && state.texts.all {it.content.text.isBlank()} && state.images.isEmpty() && state.pages.first().pdfPath==null) emptyList() else state.pages
            state.copy(pages=existing+imported,currentPageId=imported.first().id,selectedStrokeIds=emptySet(),selectedTextIds=emptySet(),selectedImageIds=emptySet(),canUndo=history.canUndo,canRedo=history.canRedo,assets=state.assets+path)
        }
        zoomState.reset()
    }

    fun restoreSession(strokes: List<Stroke>, background: Color?, tool: ToolConfig? = null) {
        history.clear()
        _uiState.update { it.copy(strokes = strokes, canvasBackgroundColor = background ?: Color(0xFFFAFAFA),
            currentTool = tool ?: ToolConfig.MEDIUM_PEN, canUndo = false, canRedo = false,
            selectedStrokeIds = emptySet(),selectedTextIds=emptySet(),selectedImageIds=emptySet()) }
    }

    val zoomState = ZoomState()
    private val history = CanvasHistory<SavedSession>()
    private var collaborationDirty=false

    private val _uiState = MutableStateFlow(DrawingUiState())
    val uiState: StateFlow<DrawingUiState> = _uiState.asStateFlow()

    private var currentStrokeBuilder: StrokeBuilder? = null
    private var currentStroke: Stroke? = null
    private var strokeListener: CollaborationSubscription? = null
    private var selectionTransformActive = false
    private var selectionTransformBefore: SavedSession? = null
    private var textEditBefore:SavedSession?=null
    private var imageEditBefore:SavedSession?=null

    fun editTextAt(x:Float=48f,y:Float=64f,checklist:Boolean=false) {
        val state=_uiState.value;val page=state.currentPage
        val image=state.visibleImages.lastOrNull {val b=it.bounds();x in b.left..b.right && y in b.top..b.bottom}
        if(image!=null && !checklist) {finishTextEditing();_uiState.update {it.copy(activeImageId=image.id)};return}
        val hit=if(checklist) null else state.visibleTexts.lastOrNull {x in it.x-12..it.x+it.width && y in it.y-12..it.y+it.height+24}
        if(hit?.checked!=null && x<hit.x+36) {toggleChecklist(hit.id);return}
        if(hit!=null && hit.id==state.activeTextId) return
        finishTextEditing()
        textEditBefore=snapshot()
        val item=hit ?: PageText(pageId=page.id,x=x.coerceIn(24f,(page.width-160f).coerceAtLeast(24f)),y=y.coerceAtLeast(24f),
            width=(page.width-x.coerceAtLeast(24f)-40f).coerceAtLeast(120f),checked=if(checklist) false else null,
            color=if(state.canvasBackgroundColor.red+state.canvasBackgroundColor.green+state.canvasBackgroundColor.blue<1.5f) 0xFFECECEF.toInt() else 0xFF202124.toInt())
        _uiState.update {it.copy(texts=if(hit==null) it.texts+item else it.texts,activeTextId=item.id,activeImageId=null,selectedStrokeIds=emptySet(),selectedTextIds=emptySet(),selectedImageIds=emptySet(),importedBlocks=it.importedBlocks+item.id)}
    }
    fun editFirstText() {
        val item=_uiState.value.visibleTexts.firstOrNull()
        if(item==null) editTextAt() else editTextAt(item.x+48f,item.y+4f)
    }
    fun updateText(id:String,content:`in`.innovaticshub.notepad.data.content.RichTextData,alignment:`in`.innovaticshub.notepad.data.content.TextAlign?=null) {
        if(textEditBefore==null) textEditBefore=snapshot()
        _uiState.update {state->
            val texts=state.texts.map {item->if(item.id==id) {
                val changed=item.copy(content=content,alignment=alignment ?: item.alignment)
                changed.copy(height=`in`.innovaticshub.notepad.ui.canvas.drawing.textLayout(changed).height.toFloat().coerceAtLeast(56f))
            } else item}
            val text=texts.firstOrNull {it.id==id}
            state.copy(texts=texts,pages=state.pages.map {p->if(text!=null && p.id==text.pageId && p.pdfPath==null) p.copy(height=maxOf(p.height,text.y+text.height+64)) else p})
        }
    }
    fun finishTextEditing() {
        val before=textEditBefore;textEditBefore=null
        if(before!=null && before!=snapshot()) history.pushBeforeChange(before)
        _uiState.update {it.copy(activeTextId=null,canUndo=history.canUndo,canRedo=history.canRedo)}
    }
    fun toggleChecklist(id:String) {
        finishTextEditing();history.pushBeforeChange(snapshot())
        _uiState.update {it.copy(texts=it.texts.map {t->if(t.id==id && t.checked!=null) t.copy(checked=!t.checked) else t},canUndo=history.canUndo,canRedo=history.canRedo)}
    }
    fun deleteText(id:String) {
        finishTextEditing();history.pushBeforeChange(snapshot())
        _uiState.update {it.copy(texts=it.texts.filterNot {t->t.id==id},activeTextId=null,canUndo=history.canUndo,canRedo=history.canRedo,importedBlocks=it.importedBlocks+id)}
    }
    fun indentText(id:String,level:Int) {
        _uiState.update {state->state.copy(texts=state.texts.map {t->if(t.id==id) {val x=48f+level.coerceIn(0,2)*64;t.copy(x=x,width=state.currentPage.width-x-48f)} else t})}
    }
    fun insertImage(path:String,width:Int,height:Int) {
        finishTextEditing();history.pushBeforeChange(snapshot())
        val page=_uiState.value.currentPage
        val w=minOf(page.width-96f,700f);val h=w*height/width.coerceAtLeast(1)
        val y=(_uiState.value.visibleTexts.maxOfOrNull {it.y+it.height+32} ?: 80f)
        val image=PageImage(pageId=page.id,path=path,width=w,height=h,y=y)
        _uiState.update {it.copy(images=it.images+image,assets=it.assets+path,activeImageId=image.id,canUndo=history.canUndo,canRedo=history.canRedo,importedBlocks=it.importedBlocks+image.id,
            pages=it.pages.map {p->if(p.id==page.id && p.pdfPath==null) p.copy(height=maxOf(p.height,y+h+48)) else p})}
    }
    fun selectImage(id:String?) {finishTextEditing();clearSelection();_uiState.update {it.copy(activeImageId=id,activeTextId=null)}}
    fun beginImageTransform() {if(imageEditBefore==null) imageEditBefore=snapshot()}
    fun moveImage(id:String,dx:Float,dy:Float) {
        _uiState.update {state->state.copy(images=state.images.map {image->if(image.id==id) image.copy(x=(image.x+dx).coerceIn(0f,(state.currentPage.width-image.width).coerceAtLeast(0f)),y=(image.y+dy).coerceAtLeast(0f)) else image})}
    }
    fun resizeImage(id:String,factor:Float) {
        _uiState.update {state->state.copy(images=state.images.map {image->if(image.id==id) {
            val w=(image.width*factor).coerceIn(60f,(state.currentPage.width-image.x).coerceAtLeast(60f));image.copy(width=w,height=image.height*w/image.width)
        } else image})}
    }
    fun endImageTransform() {
        _uiState.update {state->state.copy(pages=state.pages.map {p->if(p.pdfPath==null) p.copy(height=maxOf(p.height,(state.images.filter {it.pageId==p.id}.maxOfOrNull {it.bounds().bottom+48f} ?: p.height))) else p})}
        val before=imageEditBefore;imageEditBefore=null
        if(before!=null && before!=snapshot()) history.pushBeforeChange(before)
        _uiState.update {it.copy(canUndo=history.canUndo,canRedo=history.canRedo)}
    }
    fun rotateImage(id:String,degrees:Float) {
        if(!degrees.isFinite()) return
        beginImageTransform()
        _uiState.update {it.copy(images=it.images.map {image->if(image.id==id) image.copy(rotation=((image.rotation+degrees)%360+360)%360) else image})}
        endImageTransform()
    }
    fun cropImage(id:String,left:Float,top:Float,right:Float,bottom:Float) {
        if(listOf(left,top,right,bottom).any {!it.isFinite()}) return
        val l=left.coerceIn(0f,0.95f);val t=top.coerceIn(0f,0.95f)
        _uiState.update {it.copy(images=it.images.map {image->if(image.id==id) {
            val r=right.coerceIn(l+0.05f,1f);val b=bottom.coerceIn(t+0.05f,1f)
            // Keep the displayed image's aspect ratio consistent with the retained pixels.
            val aspect=image.height/image.width*(image.cropRight-image.cropLeft)/(image.cropBottom-image.cropTop)
            image.copy(cropLeft=l,cropTop=t,cropRight=r,cropBottom=b,height=image.width*aspect*(b-t)/(r-l))
        } else image})}
    }
    fun deleteImage(id:String) {
        history.pushBeforeChange(snapshot());_uiState.update {it.copy(images=it.images.filterNot {image->image.id==id},activeImageId=null,canUndo=history.canUndo,canRedo=history.canRedo)}
    }
    fun addAudio(path:String,duration:Long) {history.pushBeforeChange(snapshot());_uiState.update {it.copy(audio=it.audio+PageAudio(path=path,durationMs=duration),assets=it.assets+path,canUndo=history.canUndo,canRedo=history.canRedo)}}
    private val appliedRemoteStrokeIds = mutableSetOf<String>()

    fun startStroke(
        x: Float,
        y: Float,
        pressure: Float = 1f,
        minPointDistance: Float = 0.35f,
        toolOverride: ToolConfig? = null,
        timestamp: Long = System.currentTimeMillis(),
        tilt: Float = 0f,
        orientation: Float = 0f
    ) {
        currentStrokeBuilder = StrokeBuilder(toolOverride ?: _uiState.value.currentTool, minPointDistance)
        currentStrokeBuilder?.addPoint(StrokePoint(x, y, pressure,timestamp,tilt,orientation))
        currentStroke = currentStrokeBuilder?.buildPreview()?.copy(pageId=_uiState.value.currentPageId)
        _uiState.update { it.copy(isDrawing = true) }
        publishLiveStroke(force = true)
    }

    fun addStrokePoint(x: Float, y: Float, pressure: Float = 1f, timestamp:Long=System.currentTimeMillis(),tilt:Float=0f,orientation:Float=0f): Stroke? {
        return addStrokePoints(listOf(StrokePoint(x,y,pressure,timestamp,tilt,orientation)))
    }

    fun addStrokePoints(points:List<StrokePoint>):Stroke? {
        points.forEach {currentStrokeBuilder?.addPoint(it)}
        currentStroke = currentStrokeBuilder?.buildPreview()?.copy(pageId=_uiState.value.currentPageId)
        if(currentStroke?.toolConfig?.type==ToolType.LASSO && _uiState.value.selectionShape==SelectionShape.RECTANGLE) {
            val stroke=currentStroke!!
            val first=stroke.points.first();val last=stroke.points.last()
            if(hypot(first.x-last.x,first.y-last.y)>4f/zoomState.scale) {
                val points=listOf(first,first.copy(x=last.x),last,last.copy(x=first.x),first)
                currentStroke=rebuildStroke(stroke,points)
            }
        }
        publishLiveStroke()
        return currentStroke
    }

    fun endStroke() {
        var stroke = currentStroke ?: run { cancelStroke(); return }
        if (stroke.isEmpty) {
            cancelStroke()
            return
        }

        val before = _uiState.value.strokes

        if (stroke.toolConfig.type == ToolType.LASSO) {
            selectStrokesWithLasso(stroke)
            stopLiveStroke(cancelled = true)
            currentStrokeBuilder = null
            currentStroke = null
            _uiState.update { it.copy(isDrawing = false) }
            return
        }
        if(_uiState.value.autoShape && !stroke.toolConfig.isEraser && _uiState.value.currentTool.type!=ToolType.SHAPE && stroke.toolConfig.type!=ToolType.HIGHLIGHTER)
            stroke=ShapeGeometry.recognize(stroke)

        val after = if (stroke.toolConfig.isEraser) {
            val onPage=before.filter { it.pageId==stroke.pageId }
            val remaining=StrokeEraser.erase(onPage,stroke)
            if(remaining==onPage) before else before.filterNot { it.pageId==stroke.pageId }+remaining
        } else {
            before + stroke
        }

        if(after==before) { cancelStroke(); return }
        history.pushBeforeChange(snapshot())

        applyStrokes(after)
        uploadCollaborativeStroke(stroke)
        stopLiveStroke(cancelled = false)
        currentStrokeBuilder = null
        currentStroke = null
        _uiState.update { it.copy(isDrawing = false) }
    }

    fun cancelStroke() {
        stopLiveStroke(cancelled = true)
        currentStrokeBuilder = null
        currentStroke = null
        _uiState.update { it.copy(isDrawing = false) }
    }

    fun undo() {
        finishTextEditing()
        val restored = history.undo(snapshot()) ?: return
        restoreContent(restored)
        clearSelection()
        syncCollaborativeCanvasState()
    }

    fun redo() {
        finishTextEditing()
        val restored = history.redo(snapshot()) ?: return
        restoreContent(restored)
        clearSelection()
        syncCollaborativeCanvasState()
    }

    fun clearCanvas() {
        val state = _uiState.value
        if (state.selectedStrokeIds.isNotEmpty()) {
            deleteSelectedStrokes()
            return
        }
        if (state.visibleStrokes.isEmpty()) return
        history.pushBeforeChange(snapshot())
        applyStrokes(state.strokes.filterNot { it.pageId==state.currentPageId })
        clearSelection()
        syncCollaborativeCanvasState()
    }

    fun setTool(type: ToolType) {
        finishTextEditing()
        if(type!=ToolType.LASSO) clearSelection()
        rememberedTools[_uiState.value.currentTool.type]=_uiState.value.currentTool
        val config = rememberedTools[type] ?: defaultForType(type)
        _uiState.update {
            it.copy(
                currentTool = config,
                showToolSettings = type != ToolType.LASSO && type != ToolType.SHAPE && type != ToolType.TEXT,
                selectedStrokeIds = if (type == ToolType.LASSO) it.selectedStrokeIds else emptySet()
            )
        }
    }

    fun setToolConfig(toolConfig: ToolConfig) {
        _uiState.update { it.copy(currentTool = toolConfig) }
        rememberedTools[toolConfig.type]=toolConfig
        rememberedPens[penKey(toolConfig)]=toolConfig
        preferences?.saveTools(rememberedTools.values)
        preferences?.savePenPresets(rememberedPens.values)
    }

    /** Each nib has its own colour, width and pressure settings, including the two PEN styles. */
    fun selectPen(preset:ToolConfig) {
        finishTextEditing()
        val chosen=rememberedPens[penKey(preset)] ?: preset
        clearSelection()
        setToolConfig(chosen)
    }

    fun setColor(color: Color) {
        addRecentColor(color)
        setToolConfig(_uiState.value.currentTool.copy(color=color))
    }

    fun setStrokeWidth(width: Float) {
        setToolConfig(_uiState.value.currentTool.copy(baseWidth=width.coerceIn(0.1f,200f)))
    }

    fun setOpacity(opacity: Float) {
        setToolConfig(_uiState.value.currentTool.copy(opacity=opacity.coerceIn(0.05f,1f)))
    }

    fun toggleToolSettings() {
        _uiState.update { it.copy(showToolSettings = !it.showToolSettings) }
    }

    fun dismissToolSettings() {
        _uiState.update { it.copy(showToolSettings = false) }
    }

    fun toggleDarkCanvas() {
        _uiState.update { current ->
            val newBg = if (current.canvasBackgroundColor == Color(0xFFFAFAFA)) {
                Color(0xFF1C1C1E)
            } else {
                Color(0xFFFAFAFA)
            }
            current.copy(canvasBackgroundColor = newBg)
        }
    }

    fun beginSelectionTransform() {
        val state = _uiState.value
        if (selectionTransformActive || !state.hasSelection) return
        selectionTransformBefore=snapshot()
        selectionTransformActive = true
    }

    fun endSelectionTransform() {
        val before=selectionTransformBefore
        val changed=selectionTransformActive && before!=null && before!=snapshot()
        if(changed) history.pushBeforeChange(before!!)
        selectionTransformBefore=null
        selectionTransformActive = false
        _uiState.update {
            it.copy(
                canUndo = history.canUndo,
                canRedo = history.canRedo
            )
        }
        if (changed) {
            syncCollaborativeCanvasState()
        }
    }
    fun cancelSelectionTransform() {
        val before=selectionTransformBefore ?: return
        val selected=_uiState.value
        selectionTransformBefore=null;selectionTransformActive=false
        restoreContent(before)
        _uiState.update {it.copy(selectedStrokeIds=selected.selectedStrokeIds,selectedTextIds=selected.selectedTextIds,selectedImageIds=selected.selectedImageIds)}
    }

    fun moveSelectedStrokesBy(dx: Float, dy: Float) {
        if (!dx.isFinite() || !dy.isFinite() || (dx == 0f && dy == 0f)) return
        transformSelectedStrokes { point ->
            point.copy(x = point.x + dx, y = point.y + dy)
        }
        _uiState.update {s->s.copy(texts=s.texts.map {if(it.id in s.selectedTextIds) it.copy(x=it.x+dx,y=it.y+dy) else it},
            images=s.images.map {if(it.id in s.selectedImageIds) it.copy(x=it.x+dx,y=it.y+dy) else it})}
    }

    fun scaleSelectedStrokesBy(scale: Float, pivotX: Float, pivotY: Float) {
        if(!scale.isFinite() || !pivotX.isFinite() || !pivotY.isFinite()) return
        val safeScale = scale.coerceIn(0.08f, 12f)
        if (safeScale == 1f) return
        transformSelectedStrokes { point ->
            point.copy(
                x = pivotX + (point.x - pivotX) * safeScale,
                y = pivotY + (point.y - pivotY) * safeScale
            )
        }
        val state=_uiState.value
        applyStrokes(state.strokes.map {stroke -> if(stroke.id in state.selectedStrokeIds)
            stroke.copy(toolConfig=stroke.toolConfig.copy(baseWidth=(stroke.toolConfig.baseWidth*safeScale).coerceIn(0.1f,200f))) else stroke})
        _uiState.update {s->s.copy(texts=s.texts.map {if(it.id in s.selectedTextIds) it.copy(x=pivotX+(it.x-pivotX)*safeScale,y=pivotY+(it.y-pivotY)*safeScale,
            width=(it.width*safeScale).coerceIn(40f,10000f),height=(it.height*safeScale).coerceAtLeast(20f),fontSize=(it.fontSize*safeScale).coerceIn(12f,200f)) else it},
            images=s.images.map {if(it.id in s.selectedImageIds) it.copy(x=pivotX+(it.x-pivotX)*safeScale,y=pivotY+(it.y-pivotY)*safeScale,width=(it.width*safeScale).coerceIn(20f,10000f),height=(it.height*safeScale).coerceIn(20f,10000f)) else it})}
    }

    fun nudgeSelection(dx: Float, dy: Float) {
        if (!_uiState.value.hasSelection) return
        beginSelectionTransform()
        moveSelectedStrokesBy(dx, dy)
        endSelectionTransform()
    }

    fun scaleSelectionFromCenter(scale: Float) {
        val bounds = selectedBounds() ?: return
        beginSelectionTransform()
        scaleSelectedStrokesBy(
            scale = scale,
            pivotX = (bounds.left + bounds.right) / 2f,
            pivotY = (bounds.top + bounds.bottom) / 2f
        )
        endSelectionTransform()
    }

    fun createCollaborationRoom() {
        if (!sharingAllowed()) return
        if (_uiState.value.isCollaborationBusy) return
        collaborationJob = viewModelScope.launch {
            setCollaborationBusy("Creating room...")
            runCatching {
                val roomCode = collaborationRepository.createRoom()
                check(sharingAllowed()) { "Sharing approval ended." }
                collaborationDirty=false
                _uiState.update {it.copy(collaborationRoomCode=roomCode,collaborationConnected=false)}
                startListeningToRoom(roomCode,publishExisting=true)
                roomCode
            }.onSuccess { roomCode ->
                _uiState.update {
                    it.copy(
                        collaborationRoomCode = roomCode,
                        collaborationStatus = "Connecting to room $roomCode..."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        collaborationStatus = error.message ?: "Could not create room.",
                        isCollaborationBusy = false
                    )
                }
            }
        }
    }

    fun joinCollaborationRoom(roomCode: String) {
        if (!sharingAllowed()) return
        if (_uiState.value.isCollaborationBusy) return
        collaborationJob = viewModelScope.launch {
            setCollaborationBusy("Joining room...")
            runCatching {
                val joinedRoomCode = collaborationRepository.joinRoom(roomCode)
                check(sharingAllowed()) { "Sharing approval ended." }
                if(_uiState.value.collaborationRoomCode!=joinedRoomCode) collaborationDirty=false
                _uiState.update {it.copy(collaborationRoomCode=joinedRoomCode,collaborationConnected=false)}
                startListeningToRoom(joinedRoomCode)
                joinedRoomCode
            }.onSuccess { joinedRoomCode ->
                _uiState.update {
                    it.copy(
                        collaborationRoomCode = joinedRoomCode,
                        collaborationStatus = "Connecting to room $joinedRoomCode..."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        collaborationStatus = error.message ?: "Could not join room.",
                        isCollaborationBusy = false
                    )
                }
            }
        }
    }

    fun leaveCollaborationRoom() {
        collaborationJob?.cancel()
        collaborationJob = null
        strokeListener?.remove()
        strokeListener = null
        appliedRemoteStrokeIds.clear()
        collaborationDirty=false
        presenceExpiry?.cancel()
        _presence.value = RoomPresenceState()
        _uiState.update {
            it.copy(
                collaborationRoomCode = null,
                collaborationConnected=false,
                sharingApproved=false,
                collaborationStatus = "Left collaboration room.",
                isCollaborationBusy = false
            )
        }
    }

    private fun applyStrokes(strokes: List<Stroke>) {
        val ids = strokes.mapTo(mutableSetOf()) { it.id }
        _uiState.update {
            it.copy(
                strokes = strokes,
                selectedStrokeIds = it.selectedStrokeIds.filterTo(mutableSetOf()) { id -> id in ids },
                canUndo = history.canUndo,
                canRedo = history.canRedo
            )
        }
    }

    private fun restoreContent(session:SavedSession) {
        _uiState.update {it.copy(strokes=session.strokes,pages=session.pages,currentPageId=session.currentPageId,
            canvasBackgroundColor=session.canvasBackgroundColor ?: Color(0xFFFAFAFA),paperStyle=session.paperStyle,
            selectedStrokeIds=emptySet(),selectedTextIds=emptySet(),selectedImageIds=emptySet(),canUndo=history.canUndo,canRedo=history.canRedo,assets=it.assets+session.assets,
            texts=session.texts,images=session.images,audio=session.audio,importedBlocks=session.importedBlocks,activeTextId=null,activeImageId=null)}
    }

    private fun transformSelectedStrokes(transform: (StrokePoint) -> StrokePoint) {
        val selectedIds = _uiState.value.selectedStrokeIds
        if (selectedIds.isEmpty()) return

        val transformed = _uiState.value.strokes.map { stroke ->
            if (stroke.id !in selectedIds) {
                stroke
            } else {
                rebuildStroke(stroke, stroke.points.map(transform))
            }
        }
        applyStrokes(transformed)
    }

    private fun rebuildStroke(stroke: Stroke, points: List<StrokePoint>): Stroke {
        val builder = StrokeBuilder(stroke.toolConfig, minPointDistance = 0f)
        points.forEach(builder::addPoint)
        return builder.build().copy(id = stroke.id,pageId=stroke.pageId)
    }

    fun deleteSelectedStrokes() {
        val state = _uiState.value
        if (!state.hasSelection) return
        history.pushBeforeChange(snapshot())
        applyStrokes(state.strokes.filterNot { it.id in state.selectedStrokeIds })
        _uiState.update {it.copy(texts=it.texts.filterNot {t->t.id in state.selectedTextIds},images=it.images.filterNot {i->i.id in state.selectedImageIds},
            importedBlocks=it.importedBlocks+state.selectedTextIds+state.selectedImageIds)}
        clearSelection()
        syncCollaborativeCanvasState()
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedStrokeIds = emptySet(),selectedTextIds=emptySet(),selectedImageIds=emptySet()) }
    }

    fun selectAll() { finishTextEditing();_uiState.update { it.copy(selectedStrokeIds=it.visibleStrokes.mapTo(mutableSetOf()) { stroke -> stroke.id },
        selectedTextIds=if(it.includePageObjects) it.visibleTexts.map {t->t.id}.toSet() else emptySet(),selectedImageIds=if(it.includePageObjects) it.visibleImages.map {i->i.id}.toSet() else emptySet(),currentTool=defaultForType(ToolType.LASSO)) } }
    fun copySelection() {
        val s=_uiState.value
        InkClipboard.strokes=s.strokes.filter {it.id in s.selectedStrokeIds}
        InkClipboard.texts=s.texts.filter {it.id in s.selectedTextIds}
        InkClipboard.images=s.images.filter {it.id in s.selectedImageIds}
    }
    fun cutSelection() { copySelection(); deleteSelectedStrokes() }
    fun paste() {
        val copied=InkClipboard.strokes
        if(InkClipboard.isEmpty) return
        val state=_uiState.value
        val pasted=copied.map { original -> rebuildStroke(original,original.points.map { it.copy(x=it.x+28f/zoomState.scale,y=it.y+28f/zoomState.scale) })
            .copy(id=Stroke.generateId(),pageId=state.currentPageId) }
        val offset=28f/zoomState.scale
        val texts=InkClipboard.texts.map {it.copy(id=UUID.randomUUID().toString(),pageId=state.currentPageId,x=it.x+offset,y=it.y+offset)}
        val created=mutableListOf<java.io.File>()
        val images=try {InkClipboard.images.map {image->
            val path=appContext?.let {context->
                val source=java.io.File(context.filesDir,image.path)
                val target=java.io.File(context.filesDir,"images/${UUID.randomUUID()}.${source.extension.ifBlank {"png"}}")
                target.parentFile?.mkdirs();created+=target;source.copyTo(target);target.relativeTo(context.filesDir).invariantSeparatorsPath
            } ?: image.path
            image.copy(id=UUID.randomUUID().toString(),pageId=state.currentPageId,path=path,x=image.x+offset,y=image.y+offset)
        }} catch(error:Exception) {created.forEach {it.delete()};_uiState.update {it.copy(contentError="Could not paste the image. Its source file may no longer be available.")};return}
        history.pushBeforeChange(snapshot()); applyStrokes(state.strokes+pasted)
        _uiState.update { it.copy(texts=it.texts+texts,images=it.images+images,assets=it.assets+images.map {i->i.path},
            selectedStrokeIds=pasted.mapTo(mutableSetOf()) { stroke -> stroke.id },selectedTextIds=texts.map {t->t.id}.toSet(),selectedImageIds=images.map {i->i.id}.toSet(),currentTool=defaultForType(ToolType.LASSO)) }
        syncCollaborativeCanvasState()
    }
    fun duplicateSelection() { copySelection(); paste() }
    fun rotateSelection(degrees:Float) {
        val b=selectedBounds() ?: return
        val cx=(b.left+b.right)/2; val cy=(b.top+b.bottom)/2; val angle=Math.toRadians(degrees.toDouble())
        beginSelectionTransform()
        transformSelectedStrokes { p -> val x=p.x-cx; val y=p.y-cy
            p.copy(x=cx+(x*cos(angle)-y*sin(angle)).toFloat(),y=cy+(x*sin(angle)+y*cos(angle)).toFloat()) }
        val state=_uiState.value
        applyStrokes(state.strokes.map {stroke -> if(stroke.id in state.selectedStrokeIds)
            stroke.copy(toolConfig=stroke.toolConfig.copy(nibAngle=((stroke.toolConfig.nibAngle+degrees)%180+180)%180)) else stroke})
        endSelectionTransform()
    }
    fun straightenSelection() {
        val strokes=_uiState.value.visibleStrokes.filter {it.id in _uiState.value.selectedStrokeIds}
        if(strokes.size<2) return
        val xs=strokes.map {(it.bounds.left+it.bounds.right)/2f};val ys=strokes.map {it.bounds.bottom}
        val mx=xs.average();val my=ys.average()
        val variance=xs.sumOf {(it-mx)*(it-mx)}
        if(variance<1.0) return
        val slope=xs.indices.sumOf {(xs[it]-mx)*(ys[it]-my)}/variance
        rotateSelection(Math.toDegrees(-atan(slope)).toFloat().coerceIn(-30f,30f))
    }
    fun flipSelection(horizontal:Boolean) {
        val b=selectedBounds() ?: return
        beginSelectionTransform()
        transformSelectedStrokes { p -> if(horizontal) p.copy(x=b.left+b.right-p.x) else p.copy(y=b.top+b.bottom-p.y) }
        endSelectionTransform()
    }
    fun styleSelection(color:Color?=null,width:Float?=null) {
        val state=_uiState.value
        if(state.selectedStrokeIds.isEmpty() && (color==null || state.selectedTextIds.isEmpty())) return
        history.pushBeforeChange(snapshot())
        if(color!=null) _uiState.update {it.copy(texts=it.texts.map {t->if(t.id in state.selectedTextIds) t.copy(color=color.toArgb()) else t})}
        applyStrokes(state.strokes.map { stroke -> if(stroke.id in state.selectedStrokeIds) {
            val tool=stroke.toolConfig.copy(color=color ?: stroke.toolConfig.color,baseWidth=width?.coerceIn(0.1f,200f) ?: stroke.toolConfig.baseWidth)
            stroke.copy(toolConfig=tool)
        } else stroke })
        syncCollaborativeCanvasState()
    }
    fun orderSelection(front:Boolean) {
        val state=_uiState.value
        if(state.selectedStrokeIds.isEmpty()) return
        val (selected,rest)=state.strokes.partition { it.id in state.selectedStrokeIds }
        history.pushBeforeChange(snapshot()); applyStrokes(if(front) rest+selected else selected+rest); syncCollaborativeCanvasState()
    }

    private fun selectedBounds(): RectF? {
        return _uiState.value.selectionBounds()
    }

    private fun selectStrokesWithLasso(lassoStroke: Stroke) {
        val polygon = lassoStroke.points
        if (polygon.size < 3) {
            val hit=_uiState.value.visibleStrokes.lastOrNull { candidate ->
                candidate.points.any { hypot(it.x-polygon.first().x,it.y-polygon.first().y)<=candidate.toolConfig.strokeWidth()/2+14f/zoomState.scale } ||
                    candidate.points.zipWithNext().any { (a,b) -> StrokeEraser.pointDistance(polygon.first(),a,b)<=candidate.toolConfig.strokeWidth()/2+14f/zoomState.scale }
            }
            val state=_uiState.value;val p=polygon.first()
            val text=if(hit==null && state.includePageObjects) state.visibleTexts.lastOrNull {p.x in it.x..(it.x+it.width) && p.y in it.y..(it.y+it.height)} else null
            val image=if(hit==null && text==null && state.includePageObjects) state.visibleImages.lastOrNull {val b=it.bounds();p.x in b.left..b.right && p.y in b.top..b.bottom} else null
            _uiState.update { it.copy(selectedStrokeIds=hit?.let { setOf(it.id) } ?: emptySet(),selectedTextIds=text?.let {setOf(it.id)} ?: emptySet(),selectedImageIds=image?.let {setOf(it.id)} ?: emptySet()) }
            return
        }

        val selected = _uiState.value.visibleStrokes
            .filterNot { it.toolConfig.isEraser || it.toolConfig.type == ToolType.LASSO }
            .filter { stroke ->
                if(!_uiState.value.includePartialSelection) stroke.points.all {pointInPolygon(it,polygon)}
                else stroke.points.any { point -> pointInPolygon(point, polygon) } ||
                    stroke.points.zipWithNext().any { (a,b) ->
                        polygon.indices.any { i -> segmentsCross(a,b,polygon[i],polygon[(i+1)%polygon.size]) }
                    }
            }
            .mapTo(mutableSetOf()) { it.id }

        fun selects(box:RectF):Boolean {
            val corners=listOf(StrokePoint(box.left,box.top),StrokePoint(box.right,box.top),StrokePoint(box.right,box.bottom),StrokePoint(box.left,box.bottom))
            if(!_uiState.value.includePartialSelection) return corners.all {pointInPolygon(it,polygon)}
            return corners.any {pointInPolygon(it,polygon)} || polygon.any {it.x in box.left..box.right && it.y in box.top..box.bottom} ||
                corners.indices.any {i->polygon.indices.any {j->segmentsCross(corners[i],corners[(i+1)%4],polygon[j],polygon[(j+1)%polygon.size])}}
        }
        _uiState.update {s->s.copy(selectedStrokeIds = selected,
            selectedTextIds=if(s.includePageObjects) s.visibleTexts.filter {selects(RectF(it.x,it.y,it.x+it.width,it.y+it.height))}.map {it.id}.toSet() else emptySet(),
            selectedImageIds=if(s.includePageObjects) s.visibleImages.filter {selects(it.bounds())}.map {it.id}.toSet() else emptySet())}
    }

    private fun segmentsCross(a:StrokePoint,b:StrokePoint,c:StrokePoint,d:StrokePoint):Boolean {
        fun cross(p:StrokePoint,q:StrokePoint,r:StrokePoint)=(q.x-p.x)*(r.y-p.y)-(q.y-p.y)*(r.x-p.x)
        return maxOf(a.x,b.x)>=minOf(c.x,d.x) && maxOf(c.x,d.x)>=minOf(a.x,b.x) &&
            maxOf(a.y,b.y)>=minOf(c.y,d.y) && maxOf(c.y,d.y)>=minOf(a.y,b.y) &&
            cross(a,b,c)*cross(a,b,d)<=0 && cross(c,d,a)*cross(c,d,b)<=0
    }

    private fun pointInPolygon(point: StrokePoint, polygon: List<StrokePoint>): Boolean {
        var inside = false
        var previous = polygon.last()
        polygon.forEach { current ->
            val crosses = (current.y > point.y) != (previous.y > point.y)
            if (crosses) {
                val xAtY = (previous.x - current.x) * (point.y - current.y) /
                    ((previous.y - current.y).takeIf { it != 0f } ?: 0.0001f) + current.x
                if (point.x < xAtY) inside = !inside
            }
            previous = current
        }
        return inside
    }

    private fun setCollaborationBusy(status: String) {
        _uiState.update {
            it.copy(
                collaborationStatus = status,
                isCollaborationBusy = true
            )
        }
    }

    private fun startListeningToRoom(roomCode: String,publishExisting:Boolean=false) {
        if (!sharingAllowed()) return
        strokeListener?.remove()
        _presence.value = RoomPresenceState()
        presenceExpiry?.cancel()
        presenceExpiry = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1_000)
                _presence.update { it.expire(System.currentTimeMillis()) }
            }
        }
        appliedRemoteStrokeIds.clear()
        var initialPending=publishExisting
        strokeListener = collaborationRepository.listenToStrokes(
            roomCode = roomCode,
            onStrokes = { remoteStrokes ->
                var addedCount = 0
                remoteStrokes.forEach { remoteStroke ->
                    _presence.update { state -> state.copy(previews = state.previews.filterValues { it.stroke.id != remoteStroke.id }) }
                    if (remoteStroke.id.isBlank() || !appliedRemoteStrokeIds.add(remoteStroke.id)) {
                        return@forEach
                    }
                    // Restored documents already contain these IDs. A room snapshot
                    // after reopening must not append the same ink a second time.
                    if (_uiState.value.strokes.any { it.id == remoteStroke.id }) return@forEach

                    val localStroke = remoteStroke.toLocalStroke()
                    val current = _uiState.value.strokes
                    val updated = if (localStroke.toolConfig.isEraser) {
                        current.filterNot {it.pageId==localStroke.pageId}+StrokeEraser.erase(current.filter {it.pageId==localStroke.pageId}, localStroke)
                    } else {
                        current + localStroke
                    }
                    rebaseRemoteInk(current,updated)
                    applyStrokes(updated)
                    ensureRemotePages(updated)
                    addedCount++
                }
                if (addedCount > 0) {
                    _uiState.update {
                        it.copy(collaborationStatus = "Synced $addedCount new stroke${if (addedCount == 1) "" else "s"}.")
                    }
                }
            },
            onCanvasState = { remoteState ->
                if (remoteState.userId != collaborationRepository.currentUserId) {
                    val localStrokes = remoteState.strokes.map { it.toLocalStroke() }
                    appliedRemoteStrokeIds.clear()
                    appliedRemoteStrokeIds.addAll(remoteState.strokes.map { it.id })
                    rebaseRemoteInk(_uiState.value.strokes,localStrokes)
                    applyStrokes(localStrokes)
                    ensureRemotePages(localStrokes)
                    _uiState.update {
                        it.copy(collaborationStatus = "Canvas updated by another participant.")
                    }
                }
            },
            onConnected = {
                _uiState.update {
                    it.copy(
                        collaborationStatus = "Connected to room $roomCode.",
                        collaborationConnected=true,
                        isCollaborationBusy = false
                    )
                }
                if(initialPending || collaborationDirty) {initialPending=false;collaborationDirty=false;syncCollaborativeCanvasState()}
            },
            onError = { error ->
                _presence.value = RoomPresenceState()
                _uiState.update {
                    it.copy(collaborationStatus = error.message ?: "Collaboration sync failed.",collaborationConnected=false,isCollaborationBusy=false)
                }
            },
            onMembers = { members ->
                val ids = members.map { it.id }.toSet()
                _presence.update { it.copy(members = members, previews = it.previews.filterKeys { id -> id in ids }) }
            },
            onMemberUpdate = { member ->
                _presence.update { state ->
                    if (state.members.any { it.id == member.id }) state.copy(members = state.members.map { if (it.id == member.id) member else it })
                    else state.copy(members = (state.members + member).take(20))
                }
            },
            onPreview = { preview ->
                if (preview.stroke?.userId != collaborationRepository.currentUserId) {
                    _presence.update { state ->
                        val remaining = state.previews - preview.memberId
                        val stroke = preview.stroke
                        if (stroke == null || _uiState.value.strokes.any { it.id == stroke.id } || stroke.toolConfig.type in listOf("ERASER", "LASSO")) state.copy(previews = remaining)
                        else state.copy(previews = (remaining + (preview.memberId to PeerInkPreview(stroke.toLocalStroke()))).entries.take(20).associate { it.toPair() })
                    }
                }
            }
        )
    }

    private fun ensureRemotePages(strokes:List<Stroke>) {
        _uiState.update { state ->
            val known=state.pages.map {it.id}.toSet()
            val extra=strokes.map {it.pageId}.distinct().filterNot {it in known}.map {CanvasPage(id=it)}
            if(extra.isEmpty()) state else state.copy(pages=state.pages+extra)
        }
    }

    private fun rebaseRemoteInk(before:List<Stroke>,after:List<Stroke>) {
        val old=before.associateBy {it.id};val updated=after.associateBy {it.id}
        val removed=old.keys-updated.keys
        val added=after.filter {it.id !in old}
        val changed=after.filter {s->old[s.id]?.let {it.points!=s.points || it.toolConfig!=s.toolConfig || it.pageId!=s.pageId}==true}.associateBy {it.id}
        fun rebase(session:SavedSession):SavedSession {
            val strokes=session.strokes.filterNot {it.id in removed}.map {changed[it.id] ?: it}+added.filter {s->session.strokes.none {it.id==s.id}}
            val known=session.pages.map {it.id}.toSet()
            val pages=strokes.map {it.pageId}.distinct().filterNot {it in known}.map {id->_uiState.value.pages.firstOrNull {it.id==id} ?: CanvasPage(id=id)}
            return session.copy(strokes=strokes,pages=session.pages+pages)
        }
        history.mapSnapshots(::rebase)
        textEditBefore=textEditBefore?.let(::rebase)
        imageEditBefore=imageEditBefore?.let(::rebase)
        selectionTransformBefore=selectionTransformBefore?.let(::rebase)
    }

    private fun uploadCollaborativeStroke(stroke: Stroke) {
        val roomCode = _uiState.value.collaborationRoomCode ?: return
        if(!_uiState.value.collaborationConnected) {collaborationDirty=true;return}
        val userId = collaborationRepository.currentUserId
        val remoteStroke = stroke.toRemoteStroke(userId)
        appliedRemoteStrokeIds.add(remoteStroke.id)
        viewModelScope.launch {
            runCatching {
                collaborationRepository.uploadStroke(roomCode, remoteStroke)
                remoteStroke
            }.onSuccess {
                _uiState.update {
                    it.copy(collaborationStatus = "Stroke synced to room $roomCode.")
                }
            }.onFailure { error ->
                collaborationDirty=true
                _uiState.update {
                    it.copy(collaborationStatus = error.message ?: "Could not sync stroke.")
                }
            }
        }
    }

    private fun uploadExistingStrokes(roomCode: String) {
        val userId = collaborationRepository.currentUserId
        _uiState.value.strokes.forEach { stroke ->
            val remoteStroke = stroke.toRemoteStroke(userId)
            appliedRemoteStrokeIds.add(remoteStroke.id)
            viewModelScope.launch {
                runCatching {
                    collaborationRepository.uploadStroke(roomCode, remoteStroke)
                }
            }
        }
    }

    private fun syncCollaborativeCanvasState() {
        val roomCode = _uiState.value.collaborationRoomCode ?: return
        if(!_uiState.value.collaborationConnected) {collaborationDirty=true;return}
        val userId = collaborationRepository.currentUserId
        val state = RemoteCanvasState(
            userId = userId,
            strokes = _uiState.value.strokes.map { stroke ->
                stroke.toRemoteStroke(userId)
            },
            updatedAt = System.currentTimeMillis()
        )
        appliedRemoteStrokeIds.clear()
        appliedRemoteStrokeIds.addAll(state.strokes.map { it.id })
        viewModelScope.launch {
            runCatching {
                collaborationRepository.uploadCanvasState(roomCode, state)
            }.onFailure { error ->
                collaborationDirty=true
                _uiState.update {
                    it.copy(collaborationStatus = error.message ?: "Could not sync canvas update.")
                }
            }
        }
    }

    private fun addRecentColor(color: Color) {
        _uiState.update { state ->
            val updated = (listOf(color) + state.recentColors.filter { it != color }).take(8)
            state.copy(recentColors = updated)
        }
    }

    override fun onCleared() {
        super.onCleared()
        strokeListener?.remove()
        history.clear()
    }
}
