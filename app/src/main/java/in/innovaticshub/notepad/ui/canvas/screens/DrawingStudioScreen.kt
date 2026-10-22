package `in`.innovaticshub.notepad.ui.canvas.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import `in`.innovaticshub.notepad.media.PdfImporter
import `in`.innovaticshub.notepad.media.PdfPageImages
import `in`.innovaticshub.notepad.ui.canvas.drawing.ZoomableCanvas
import `in`.innovaticshub.notepad.ui.canvas.engine.InkClipboard
import `in`.innovaticshub.notepad.ui.canvas.model.*
import `in`.innovaticshub.notepad.ui.canvas.storage.CanvasExporter
import `in`.innovaticshub.notepad.ui.canvas.storage.SavedSession
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import `in`.innovaticshub.notepad.feature.editor.RichTextState
import `in`.innovaticshub.notepad.media.ImageImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Drawing controls expose real engine capabilities; the canvas stays in one coordinate system. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingStudioScreen(viewModel:DrawingViewModel,onBack:()->Unit,onSaveText:suspend (String)->Unit,onTextMode:()->Unit=onBack,
    title:String?=null,onTitleChange:((String)->Unit)?=null,startInTextMode:Boolean=false,noteMenu:(@Composable (()->Unit)->Unit)?=null) {
    val state by viewModel.uiState.collectAsState()
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val snackbar=remember { SnackbarHostState() }
    LaunchedEffect(state.contentError) {state.contentError?.let {snackbar.showSnackbar(it);viewModel.dismissContentError()}}
    var menu by remember { mutableStateOf(false) }
    var menuSection by remember { mutableStateOf<String?>(null) }
    var insertMenu by remember {mutableStateOf(false)}
    var textMode by rememberSaveable {mutableStateOf(startInTextMode)}
    var writingPad by rememberSaveable {mutableStateOf(false)}
    var padRegion by remember {mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)}
    var padOrigin by remember(state.currentPageId) {mutableStateOf(Offset(48f,180f))}
    val keyboard=LocalSoftwareKeyboardController.current
    val focusManager=LocalFocusManager.current
    val activeText=state.texts.firstOrNull {it.id==state.activeTextId}
    val rich=remember(state.activeTextId) {activeText?.let {RichTextState(it.content)}}
    fun write() {viewModel.finishTextEditing();viewModel.selectImage(null);textMode=false;focusManager.clearFocus();keyboard?.hide()}
    fun type() {writingPad=false;padRegion=null;textMode=true;viewModel.editFirstText();keyboard?.show()}
    LaunchedEffect(Unit) {if(startInTextMode) viewModel.editFirstText()}
    var sheet by remember { mutableStateOf<String?>(null) }
    var pan by remember { mutableStateOf(false) }
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    var clearConfirm by remember { mutableStateOf(false) }
    var deletingPage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var pdfImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var pdfFailed by remember { mutableStateOf(false) }
    var colourPicking by remember { mutableStateOf(false) }
    var exportSnapshot by remember { mutableStateOf<SavedSession?>(null) }
    val page=state.currentPage
    val pageIndex=state.pages.indexOfFirst { it.id==state.currentPageId }.coerceAtLeast(0)
    val stack=remember(state.pages) {PageStackLayout(state.pages)}
    val pageOrigin=stack.origin(page.id)
    var fitScale by remember {mutableFloatStateOf(0f)}
    val landscape=androidx.compose.ui.platform.LocalConfiguration.current.let {it.screenWidthDp>it.screenHeightDp}
    val tablet=androidx.compose.ui.platform.LocalConfiguration.current.smallestScreenWidthDp>=600
    EditorSystemBars(hideStatusBar=landscape || fullScreen)
    BackHandler(fullScreen) {fullScreen=false}
    var pageRail by rememberSaveable {mutableStateOf(false)}
    fun navigatePage(id:String) {
        if(viewModel.uiState.value.currentPageId!=id) viewModel.openPage(id,resetViewport=false)
        val layout=PageStackLayout(viewModel.uiState.value.pages)
        val origin=layout.origin(id)
        val scale=viewModel.zoomState.scale
        viewModel.zoomState.setOffset(Offset((size.width-layout.width*scale)/2,-origin.y*scale))
    }
    fun addPage(landscapePage:Boolean=false) {write();viewModel.addPage(landscapePage,matchLastPage=!landscapePage);navigatePage(viewModel.uiState.value.currentPageId)}
    fun message(text:String) { scope.launch { snackbar.showSnackbar(text) } }
    fun fitPage() {
        if(size.width>0 && size.height>0) {
            val scale=size.width.toFloat()/stack.width
            fitScale=scale
            viewModel.zoomState.setLimits(scale*0.75f,scale*6f)
            viewModel.zoomState.setScale(scale)
            navigatePage(page.id)
        }
    }
    LaunchedEffect(size.width,stack.width) {
        if(size.width>0) {
            val newFit=size.width.toFloat()/stack.width
            if(fitScale==0f) {
                if(state.pages.size==1 && page.width<=page.height && state.strokes.isEmpty() && state.texts.all {it.content.text.isBlank()} && state.images.isEmpty())
                    viewModel.fitBlankPaperHeight(size.height/newFit)
                fitPage()
            } else {
                val zoom=viewModel.zoomState
                val anchor=zoom.screenToCanvas(Offset(size.width/2f,0f))
                val relative=zoom.scale/fitScale
                fitScale=newFit;zoom.setLimits(newFit*0.75f,newFit*6f);zoom.setScale(newFit*relative)
                zoom.setOffset(Offset(size.width/2f,0f)-anchor*zoom.scale)
            }
        }
    }
    var previousHeight by remember {mutableFloatStateOf(stack.height)}
    LaunchedEffect(stack.height) {
        if(stack.height<previousHeight) viewModel.zoomState.constrainDocument(stack.width,stack.height,0f,0f)
        previousHeight=stack.height
    }
    LaunchedEffect(size.height) {
        if(fitScale>0f && state.pages.size==1 && page.width<=page.height && state.strokes.isEmpty() && state.texts.all {it.content.text.isBlank()} && state.images.isEmpty())
            viewModel.fitBlankPaperHeight(size.height/fitScale)
    }
    LaunchedEffect(textMode,state.activeTextId,size.height) {
        if(textMode && activeText!=null && size.height>0) {
            val top=viewModel.zoomState.canvasToScreen(pageOrigin+Offset(activeText.x,activeText.y)).y
            val inset=16f*context.resources.displayMetrics.density
            val bottom=(size.height-96f*context.resources.displayMetrics.density).coerceAtLeast(inset)
            if(top>bottom) viewModel.zoomState.panBy(Offset(0f,bottom-top))
            else if(top<0) viewModel.zoomState.panBy(Offset(0f,inset-top))
        }
    }
    LaunchedEffect(page.pdfPath,page.pdfPageIndex) {
        pdfImage=null; pdfFailed=false
        if(page.pdfPath!=null) runCatching { withContext(Dispatchers.IO) { PdfPageImages.render(context,page.pdfPath,page.pdfPageIndex).asImageBitmap() } }
            .onSuccess { pdfImage=it }.onFailure { pdfFailed=true; snackbar.showSnackbar("Couldn’t render this PDF page. Ink is still saved.") }
    }
    val importPdf=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) scope.launch {
            busy=true
            try {
                val payload=PdfImporter.import(context,uri) ?: error("Couldn’t read this PDF")
                val dimensions=withContext(Dispatchers.IO) { PdfPageImages.sizes(context,payload.file) }
                viewModel.appendPdf(payload.file,dimensions)
                navigatePage(viewModel.uiState.value.currentPageId)
                message("PDF imported · write or highlight on any page")
            } catch(error:Exception) { message(error.message ?: "Couldn’t import this PDF") }
            finally { busy=false }
        }
    }
    val importImage=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {uri->if(uri!=null) scope.launch {
        busy=true
        try {val image=ImageImporter.import(context,uri) ?: error("Could not open this image");viewModel.insertImage(image.first,image.second.width,image.second.height);textMode=true}
        catch(error:Exception) {message("Could not import this image")}
        finally {busy=false}
    } }
    val exportPng=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val snapshot=exportSnapshot
        if(uri!=null && snapshot!=null) scope.launch {
            busy=true; runCatching { CanvasExporter.write(context,uri,snapshot,false) }
                .onSuccess { message("Page exported as PNG") }.onFailure { message("Couldn’t export the image") }; busy=false
        }
    }
    val exportPdf=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val snapshot=exportSnapshot
        if(uri!=null && snapshot!=null) scope.launch {
            busy=true; runCatching { CanvasExporter.write(context,uri,snapshot,true) }
                .onSuccess { message("Notebook exported as PDF") }.onFailure { message("Couldn’t export the PDF") }; busy=false
        }
    }
    fun invite() {
        val room=state.collaborationRoomCode
        if(room==null) { viewModel.createCollaborationRoom(); sheet="collaboration" }
        else context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain";putExtra(Intent.EXTRA_TEXT,"Join my Drawly canvas: ${`in`.innovaticshub.notepad.ui.canvas.collab.CollaborationEndpoint.shareLink(room)}") },"Invite to canvas"))
    }
    @Composable fun InkRow() {
        Surface(color=MaterialTheme.colorScheme.surface) {Column {
        CompactInkToolbar(viewModel,pan,onWrite={write();pan=false},onKeyboard={type();pan=false},
            onAdvanced={if(it=="writing-pad") {writingPad=!writingPad;if(!writingPad) padRegion=null} else if(it=="pages" && tablet && !fullScreen) pageRail=!pageRail else sheet=it},onClear={clearConfirm=true})
        }}
    }
    Scaffold(modifier=Modifier.background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
        containerColor=MaterialTheme.colorScheme.background,contentWindowInsets=WindowInsets.safeDrawing,snackbarHost={SnackbarHost(snackbar)},
        topBar={ if(!fullScreen) Column {Surface(modifier=Modifier.testTag("editor-header"),color=MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(48.dp).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={viewModel.finishTextEditing();onBack()}) {Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Save and return",Modifier.size(23.dp))}
                BasicTextField(title ?: state.documentTitle,{viewModel.setDocumentTitle(it);onTitleChange?.invoke(it)},singleLine=true,
                    textStyle=MaterialTheme.typography.titleLarge.copy(color=MaterialTheme.colorScheme.onSurface),cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),
                    modifier=Modifier.weight(1f).padding(horizontal=8.dp).testTag("note-title-editor"),
                    decorationBox={field->Box {if((title ?: state.documentTitle).isBlank()) Text("Title",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.onSurfaceVariant);field()}})
                if(landscape || tablet) IconButton(onClick={if(tablet) pageRail=!pageRail else sheet="pages"}) {Icon(Icons.Outlined.Layers,"Pages")}
                RoomMembersButton(viewModel, state.collaborationConnected, onClick={sheet="collaboration"})
                IconButton(onClick={write();pan=!pan}) {Icon(if(pan) Icons.Outlined.Edit else Icons.Outlined.MenuBook,if(pan) "Edit note" else "Reading mode",Modifier.size(23.dp))}
                Box {
                    IconButton(onClick={insertMenu=true}) {Icon(Icons.Outlined.Add,"Insert",Modifier.size(27.dp))}
                    DropdownMenu(insertMenu,{insertMenu=false}) {
                        StudioMenuItem("Add page",Icons.Outlined.NoteAdd) {insertMenu=false;addPage()}
                        StudioMenuItem("Add landscape page",Icons.Outlined.CropLandscape) {insertMenu=false;addPage(true)}
                        StudioMenuItem("Text box",Icons.Outlined.TextFields) {insertMenu=false;textMode=true;viewModel.editTextAt(48f,(state.visibleTexts.maxOfOrNull {it.y+it.height+32f} ?: 64f))}
                        StudioMenuItem("Checklist",Icons.Outlined.Checklist) {insertMenu=false;textMode=true;viewModel.editTextAt(48f,(state.visibleTexts.maxOfOrNull {it.y+it.height+32f} ?: 64f),true)}
                        StudioMenuItem("Image",Icons.Outlined.Image) {insertMenu=false;importImage.launch("image/*")}
                        StudioMenuItem("PDF",Icons.Outlined.PictureAsPdf) {insertMenu=false;write();importPdf.launch(arrayOf("application/pdf"))}
                        StudioMenuItem("Voice recording",Icons.Outlined.Mic) {insertMenu=false;sheet="audio"}
                    }
                }
                Box {
                    IconButton(onClick={menuSection=null;menu=true}) {Icon(Icons.Outlined.MoreVert,"Drawing options",Modifier.size(23.dp))}
                    DropdownMenu(menu,{menu=false}) {
                        if(menuSection!=null) {
                            StudioMenuItem("Back",Icons.AutoMirrored.Outlined.ArrowBack) {menuSection=null}
                            HorizontalDivider()
                        }
                        if(menuSection=="tools") {
                            StudioMenuItem("Pen settings",Icons.Outlined.Tune) {menu=false;sheet="pen"}
                            StudioMenuItem("Pick colour from page",Icons.Outlined.Colorize) {menu=false;colourPicking=true}
                            StudioMenuItem("Import PDF to annotate",Icons.Outlined.PictureAsPdf) {menu=false;importPdf.launch(arrayOf("application/pdf"))}
                            StudioMenuItem("Convert handwriting to text",Icons.Outlined.TextFields) {menu=false;sheet="recognize"}
                            StudioMenuItem("Select all on this page",Icons.Outlined.SelectAll) {menu=false;viewModel.selectAll()}
                            StudioMenuItem("Paste",Icons.Outlined.ContentPaste,!InkClipboard.isEmpty) {menu=false;viewModel.paste()}
                        }
                        if(menuSection=="export") {
                            StudioMenuItem("Export page as PNG",Icons.Outlined.Image) {menu=false;exportSnapshot=viewModel.snapshot();exportPng.launch("Drawly-page-${pageIndex+1}.png")}
                            StudioMenuItem("Export all pages as PDF",Icons.Outlined.PictureAsPdf) {menu=false;exportSnapshot=viewModel.snapshot();exportPdf.launch("Drawly-notebook.pdf")}
                        }
                        if(menuSection=="share") {
                            StudioMenuItem("Share page image",Icons.Outlined.Share) {
                                menu=false; scope.launch { runCatching {
                                    val file=CanvasExporter.shareImage(context,viewModel.snapshot())
                                    val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {type="image/png";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"Share drawing"))
                                }.onFailure {message("Couldn’t share this page")} }
                            }
                            StudioMenuItem("Share text",Icons.Outlined.TextFields) {
                                menu=false
                                val text=state.documentTitle+"\n\n"+state.texts.joinToString("\n") {it.content.text}
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {type="text/plain";putExtra(Intent.EXTRA_TEXT,text)},"Share note"))
                            }
                        }
                        if(menuSection=="tools") {
                            StudioMenuItem(if(state.collaborationRoomCode==null) "Create collaboration room" else "Invite to room",Icons.Outlined.GroupAdd) {menu=false;invite()}
                            StudioMenuItem("Clear page ink",Icons.Outlined.DeleteOutline,state.visibleStrokes.isNotEmpty()) {menu=false;clearConfirm=true}
                            StudioMenuItem("Fit to width",Icons.Outlined.FitScreen) {menu=false;fitPage()}
                            StudioMenuItem(if(state.autoShape) "Auto-fix shapes: on" else "Auto-fix shapes: off",Icons.Outlined.AutoFixHigh) {menu=false;viewModel.setAutoShape(!state.autoShape)}
                        }
                        if(menuSection==null) {
                            StudioMenuItem("Share",Icons.Outlined.Share) {menuSection="share"}
                            StudioMenuItem("Save as file",Icons.Outlined.SaveAlt) {menuSection="export"}
                            HorizontalDivider()
                            StudioMenuItem("Page template",Icons.Outlined.GridOn) {menu=false;sheet="paper"}
                            StudioMenuItem("Pages",Icons.Outlined.Layers) {menu=false;if(tablet) pageRail=true else sheet="pages"}
                            StudioMenuItem(if(state.fingerDrawing) "Finger drawing: on" else "Finger drawing: off",Icons.Outlined.TouchApp) {menu=false;viewModel.setFingerDrawing(!state.fingerDrawing)}
                            StudioMenuItem("Full screen",Icons.Outlined.Fullscreen) {menu=false;fullScreen=true}
                            StudioMenuItem("More tools",Icons.Outlined.Tune) {menuSection="tools"}
                            if(noteMenu!=null) {HorizontalDivider();noteMenu {menu=false}}
                        }
                    }
                }
            }
        };if(tablet && !pan && !textMode) InkRow()} },
        bottomBar={ if(!pan) Column(Modifier.background(MaterialTheme.colorScheme.surface).imePadding().navigationBarsPadding()) {
            if(state.hasSelection) InkSelectionBar(viewModel,onColour={sheet="selection-colour"},onRecognize={sheet="recognize"})
            if(writingPad && !textMode) EasyWritingPad(viewModel,pdfImage,padOrigin,{padOrigin=it},onRegion={padRegion=it},onClose={writingPad=false;padRegion=null})
            if(state.activeImageId!=null) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.SpaceEvenly) {
                TextButton(onClick={viewModel.beginImageTransform();viewModel.resizeImage(state.activeImageId!!,0.85f);viewModel.endImageTransform()}) {Text("Smaller")}
                TextButton(onClick={viewModel.beginImageTransform();viewModel.resizeImage(state.activeImageId!!,1.15f);viewModel.endImageTransform()}) {Text("Larger")}
                IconButton(onClick={viewModel.rotateImage(state.activeImageId!!,90f)}) {Icon(Icons.Outlined.RotateRight,"Rotate image")}
                IconButton(onClick={viewModel.beginImageTransform();sheet="image"}) {Icon(Icons.Outlined.Crop,"Crop image")}
                TextButton(onClick={viewModel.deleteImage(state.activeImageId!!)}) {Text("Delete")}
                TextButton(onClick={viewModel.selectImage(null)}) {Text("Done")}
            }
            if(textMode) PageTextToolbar(viewModel,rich) {write();pan=false}
            else if(!tablet || fullScreen) InkRow()
        } }) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
        if(tablet && pageRail && !fullScreen) PageRail(viewModel,onOpen={navigatePage(it)},onAdd={addPage()},onDelete={deletingPage=it},onClose={pageRail=false})
        Box(Modifier.weight(1f).fillMaxHeight()) {
            ZoomableCanvas(viewModel,Modifier.fillMaxSize().onSizeChanged {size=it},state.canvasBackgroundColor,viewModel.zoomState,isPanMode=pan,pdfBackground=pdfImage,
                onAppendPage=if(pan || colourPicking) null else ({viewModel.addPage(matchLastPage=true)}),
                showDocumentTitle=false,continuousPages=true,blockInkAt={point->
                    val local=point-pageOrigin
                    (writingPad && padRegion?.contains(local)==true) || state.images.firstOrNull {it.id==state.activeImageId}?.bounds()?.let {local.x in it.left..it.right && local.y in it.top..it.bottom}==true
                },isTextMode=textMode,onDocumentTap={point->viewModel.editTextAt(point.x,point.y)},colourPicking=colourPicking,onPickColour={point -> scope.launch {
                    runCatching {CanvasExporter.sample(context,viewModel.snapshot(),point)}.onSuccess {viewModel.setColor(it);colourPicking=false}.onFailure {message(it.message ?: "Couldn’t pick this colour")}
                }},overlay={
            if(textMode || state.activeImageId!=null) PageTextEditorOverlay(viewModel,if(textMode) rich else null,pageOrigin)
            if(writingPad) padRegion?.let {region->
                val topLeft=viewModel.zoomState.canvasToScreen(pageOrigin+region.topLeft)
                val density=LocalDensity.current
                Box(Modifier.offset {IntOffset(topLeft.x.toInt(),topLeft.y.toInt())}.testTag("writing-area")
                    .size(with(density) {(region.width*viewModel.zoomState.scale).toDp()},with(density) {(region.height*viewModel.zoomState.scale).toDp()})
                    .pointerInput(state.currentPageId) {detectDragGestures {change,delta->change.consume()
                        padOrigin=Offset((padOrigin.x+delta.x/viewModel.zoomState.scale).coerceIn(0f,(state.currentPage.width-region.width).coerceAtLeast(0f)),(padOrigin.y+delta.y/viewModel.zoomState.scale).coerceAtLeast(0f))
                    }})
                Canvas(Modifier.fillMaxSize()) {
                val topLeft=viewModel.zoomState.canvasToScreen(pageOrigin+region.topLeft)
                drawRect(Color(0xFF5D82C6).copy(alpha=0.08f),topLeft,region.size*viewModel.zoomState.scale)
                drawRect(Color(0xFF5D82C6),topLeft,region.size*viewModel.zoomState.scale,style=androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            } }
            PageStackFooter(viewModel,stack)
            })
            ZoomIndicator(viewModel,fitScale,onFit={fitPage()},Modifier.align(Alignment.TopEnd).padding(top=if(fullScreen) 60.dp else 0.dp))
            if(busy || (page.pdfPath!=null && pdfImage==null && !pdfFailed)) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            if(fullScreen) FilledIconButton(onClick={fullScreen=false},modifier=Modifier.align(Alignment.TopEnd).padding(12.dp),
                colors=IconButtonDefaults.filledIconButtonColors(containerColor=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.94f),contentColor=MaterialTheme.colorScheme.onSurface)) {Icon(Icons.Outlined.FullscreenExit,"Exit full screen")}
            if(colourPicking) Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.align(Alignment.TopCenter).padding(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(start=16.dp)) {Text("Tap a colour on this page");IconButton(onClick={colourPicking=false}) {Icon(Icons.Outlined.Close,"Cancel colour picker")}}
            }
        }}
    }
    sheet?.let { active -> ModalBottomSheet(onDismissRequest={if(active=="image") viewModel.endImageTransform();sheet=null},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp) {
        when(active) {
            "image" -> ImageEditSheet(viewModel) {viewModel.endImageTransform();sheet=null}
            "pen" -> PenSettings(viewModel)
            "colour" -> InkColourSheet(state.currentTool.color,viewModel::setColor)
            "background-colour" -> InkColourSheet(state.canvasBackgroundColor,viewModel::setBackground)
            "selection-colour" -> InkColourSheet(state.strokes.firstOrNull {it.id in state.selectedStrokeIds}?.toolConfig?.color ?: state.currentTool.color) {viewModel.styleSelection(color=it)}
            "recognize" -> HandwritingSheet(if(state.selectedStrokeIds.isEmpty()) state.visibleStrokes else state.visibleStrokes.filter {it.id in state.selectedStrokeIds}) {text->
                viewModel.editTextAt(48f,(state.visibleTexts.maxOfOrNull {it.y+it.height+32f} ?: 64f));viewModel.uiState.value.activeTextId?.let {viewModel.updateText(it,`in`.innovaticshub.notepad.data.content.RichTextData(text,emptyList()))};viewModel.finishTextEditing()
            }
            "audio" -> DocumentAudioSheet(viewModel)
            "paper" -> PageTemplatePicker(state.paperStyle,state.canvasBackgroundColor,viewModel::setPaper,viewModel::setBackground) {sheet="background-colour"}
            "pages" -> PageOverview(viewModel,onOpen={navigatePage(it);sheet=null},onDelete={deletingPage=it},onAdd={addPage(it);sheet=null})
            "collaboration" -> CollaborationSheet(viewModel)
        }
    } }
    if(clearConfirm) AlertDialog(onDismissRequest={clearConfirm=false},title={Text("Clear ink from this page?")},text={Text("The paper and imported PDF remain. You can undo this action.")},
        confirmButton={TextButton(onClick={viewModel.clearSelection();viewModel.clearCanvas();clearConfirm=false}) {Text("Clear page")}},dismissButton={TextButton(onClick={clearConfirm=false}) {Text("Keep ink")}})
    deletingPage?.let { id -> AlertDialog(onDismissRequest={deletingPage=null},title={Text("Delete this page?")},text={Text("This removes the page and its annotations. You can undo the deletion.")},
        confirmButton={TextButton(onClick={viewModel.deletePage(id);navigatePage(viewModel.uiState.value.currentPageId);deletingPage=null}) {Text("Delete page")}},dismissButton={TextButton(onClick={deletingPage=null}) {Text("Keep page")}}) }
}

@Composable
private fun ZoomIndicator(model:DrawingViewModel,fitScale:Float,onFit:()->Unit,modifier:Modifier=Modifier) {
    val zoom=model.zoomState
    val scale=zoom.scale
    var visible by remember {mutableStateOf(false)}
    var observed by remember {mutableStateOf(false)}
    LaunchedEffect(scale) {
        if(observed) {visible=true;kotlinx.coroutines.delay(1500);visible=false} else observed=true
    }
    if(!visible) return
    Surface(shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=0.94f),
        modifier=modifier.padding(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("${if(fitScale>0f) (zoom.scale/fitScale*100).toInt() else 100}%",style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(start=16.dp,end=8.dp))
            IconButton(onClick=onFit,modifier=Modifier.size(40.dp)) {Icon(Icons.Outlined.FitScreen,"Fit to width",Modifier.size(21.dp))}
        }
    }
}

@Composable
private fun PageStackFooter(model:DrawingViewModel,stack:PageStackLayout) {
    val zoom=model.zoomState;val density=LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        stack.pages.forEachIndexed {index,page->
            val frame=stack.frames.getValue(page.id)
            val point=zoom.canvasToScreen(Offset(frame.right,frame.bottom))
            if(point.y in 0f..zoom.viewportHeight) Text("${index+1}/${stack.pages.size}",style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.55f),modifier=Modifier.offset {IntOffset(point.x.toInt()-50,point.y.toInt()-28)})
        }
    }
}

@Composable
private fun StudioAction(label:String,icon:ImageVector,onClick:()->Unit) {TextButton(onClick=onClick) {Icon(icon,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(label)}}
@Composable
private fun StudioMenuItem(label:String,icon:ImageVector,enabled:Boolean=true,onClick:()->Unit) {DropdownMenuItem(text={Text(label)},leadingIcon={Icon(icon,null)},enabled=enabled,onClick=onClick)}
private fun ToolType.icon():ImageVector=when(this) {ToolType.PEN->Icons.Outlined.Edit;ToolType.PENCIL->Icons.Outlined.Create;ToolType.MARKER->Icons.Outlined.BorderColor;ToolType.HIGHLIGHTER->Icons.Outlined.Highlight;ToolType.BRUSH->Icons.Outlined.Brush;ToolType.CALLIGRAPHY->Icons.Outlined.Draw;ToolType.ERASER->Icons.Outlined.AutoFixNormal;ToolType.LASSO->Icons.Outlined.Gesture;ToolType.SHAPE->Icons.Outlined.Category;ToolType.TEXT->Icons.Outlined.TextFields}
