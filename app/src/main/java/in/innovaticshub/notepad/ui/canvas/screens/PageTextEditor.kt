package `in`.innovaticshub.notepad.ui.canvas.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.*
import `in`.innovaticshub.notepad.data.content.*
import `in`.innovaticshub.notepad.feature.editor.*
import `in`.innovaticshub.notepad.ui.canvas.model.*
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import `in`.innovaticshub.notepad.ui.theme.LocalNoteAppearance

@Composable
internal fun PageTitleEditor(model:DrawingViewModel,title:String,onChange:(String)->Unit) {
    val state by model.uiState.collectAsState();val zoom=model.zoomState;val density=LocalDensity.current
    val point=zoom.canvasToScreen(androidx.compose.ui.geometry.Offset(48f,28f))
    val background=state.canvasBackgroundColor
    val ink=if(background.red+background.green+background.blue<1.5f) Color(0xFFECECEF) else Color(0xFF202124)
    val style=TextStyle(color=ink,fontFamily=FontFamily.SansSerif,fontSize=with(density) {(60f*zoom.scale).toSp()},platformStyle=PlatformTextStyle(includeFontPadding=false))
    Box(Modifier.fillMaxSize().clipToBounds()) {
        BasicTextField(title,onChange,singleLine=true,textStyle=style,cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),
            modifier=Modifier.offset {IntOffset(point.x.toInt(),point.y.toInt())}.width(with(density) {((state.currentPage.width-96f)*zoom.scale).toDp()}).testTag("note-title-editor"),
            decorationBox={field->Box {if(title.isEmpty()) Text("Title",style=style.copy(color=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f)));field()}})
    }
}

@Composable
internal fun PageTextEditorOverlay(model:DrawingViewModel,rich:RichTextState?,pageOrigin:androidx.compose.ui.geometry.Offset=androidx.compose.ui.geometry.Offset.Zero) {
    val state by model.uiState.collectAsState();val density=LocalDensity.current;val zoom=model.zoomState
    val appearance=LocalNoteAppearance.current
    val pdf=state.currentPage.pdfPath!=null
    val item=state.texts.firstOrNull {it.id==state.activeTextId}
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        if(item!=null && rich!=null) {
            val point=zoom.canvasToScreen(pageOrigin+androidx.compose.ui.geometry.Offset(item.x+if(item.checked!=null) 48 else 0,item.y))
            val focus=remember(item.id) {FocusRequester()}
            LaunchedEffect(item.id) {rich.onValueChange(rich.textFieldValue.copy(selection=TextRange(rich.text.length)));focus.requestFocus()}
            val annotated=buildAnnotatedString {
                append(rich.text)
                rich.spans.forEach {span->val start=span.start.coerceIn(0,length);val end=span.end.coerceIn(start,length)
                    if(end>start) addStyle(when(span.kind) {
                        SpanKind.BOLD->SpanStyle(fontWeight=FontWeight.Bold)
                        SpanKind.ITALIC->SpanStyle(fontStyle=FontStyle.Italic)
                        SpanKind.UNDERLINE->SpanStyle(textDecoration=TextDecoration.Underline)
                        SpanKind.STRIKE->SpanStyle(textDecoration=TextDecoration.LineThrough)
                        SpanKind.COLOR->SpanStyle(color=appearance.ink(Color(span.argb ?: item.color),pdf))
                        SpanKind.HIGHLIGHT->SpanStyle(background=Color(span.argb ?: 0xFFFFFF00.toInt()))
                        SpanKind.SIZE->SpanStyle(fontSize=with(density) {((span.sizePx ?: 16)*2.5f*zoom.scale).toSp()})
                    },start,end)
                }
            }
            BasicTextField(value=rich.textFieldValue,onValueChange={rich.onValueChange(it);model.updateText(item.id,rich.toRichTextData())},
                textStyle=TextStyle(color=appearance.ink(Color(item.color),pdf),fontFamily=FontFamily.SansSerif,fontSize=with(density) {(item.fontSize*zoom.scale).toSp()},
                    platformStyle=PlatformTextStyle(includeFontPadding=false),
                    textAlign=when(item.alignment) {TextAlign.LEFT->androidx.compose.ui.text.style.TextAlign.Left;TextAlign.CENTER->androidx.compose.ui.text.style.TextAlign.Center;TextAlign.RIGHT->androidx.compose.ui.text.style.TextAlign.Right}),
                visualTransformation=VisualTransformation {TransformedText(annotated,OffsetMapping.Identity)},cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),
                modifier=Modifier.offset {IntOffset(point.x.toInt(),point.y.toInt())}
                    .width(with(density) {((item.width-if(item.checked!=null) 48 else 0)*zoom.scale).toDp()})
                    .heightIn(min=with(density) {(56f*zoom.scale).toDp()},max=(maxHeight-with(density) {point.y.toDp()}).coerceAtLeast(56.dp))
                    .focusRequester(focus).testTag("page-text-editor"),
                decorationBox={field->Box {if(rich.text.isEmpty()) Text("Start writing",color=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.45f),fontSize=with(density) {(item.fontSize*zoom.scale).toSp()});field()}})
        }
        val image=state.images.firstOrNull {it.id==state.activeImageId}
        if(image!=null) {
            val bounds=image.bounds()
            val point=zoom.canvasToScreen(pageOrigin+androidx.compose.ui.geometry.Offset(bounds.left,bounds.top))
            Box(Modifier.offset {IntOffset(point.x.toInt(),point.y.toInt())}.size(with(density) {((bounds.right-bounds.left)*zoom.scale).toDp()},with(density) {((bounds.bottom-bounds.top)*zoom.scale).toDp()})
                .border(2.dp,MaterialTheme.colorScheme.primary).pointerInput(image.id) {
                    detectDragGestures(onDragStart={model.beginImageTransform()},onDragEnd=model::endImageTransform,onDragCancel=model::endImageTransform) {change,delta->change.consume();model.moveImage(image.id,delta.x/zoom.scale,delta.y/zoom.scale)}
                }) {
                Icon(Icons.Outlined.OpenWith,"Move image",Modifier.align(Alignment.Center).background(MaterialTheme.colorScheme.surface.copy(alpha=0.8f)).padding(8.dp))
            }
        }
    }
}

@Composable
internal fun PageTextToolbar(model:DrawingViewModel,rich:RichTextState?,onWrite:()->Unit) {
    val state by model.uiState.collectAsState();val item=state.texts.firstOrNull {it.id==state.activeTextId}
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        IconButton(onWrite) {Icon(Icons.Outlined.Draw,"Handwriting mode")}
        if(item!=null && rich!=null) {
            fun apply(action:()->Unit) {action();model.updateText(item.id,rich.toRichTextData())}
            val list=if(rich.text.startsWith("• ")) ListType.BULLET else if(Regex("^\\d+\\. ").containsMatchIn(rich.text)) ListType.NUMBERED else ListType.NONE
            Box(Modifier.weight(1f)) {EditorToolbar(rich.currentFormatting(),list,item.alignment,((item.x-48f)/64f).toInt().coerceIn(0,2),rich.undoStack.isNotEmpty(),rich.redoStack.isNotEmpty(),
                onToggleStyle={kind->apply {rich.toggleStyle(kind)}},onFontColor={argb->apply {rich.toggleStyle(SpanKind.COLOR,argb=argb)}},
                onHighlight={argb->apply {rich.toggleStyle(SpanKind.HIGHLIGHT,argb=argb)}},onCycleSize={apply {rich.toggleStyle(SpanKind.SIZE,sizePx=when(rich.currentFormatting().sizePx) {16->20;20->24;24->30;else->16})}},
                onAlign={model.updateText(item.id,rich.toRichTextData(),it)},onListType={type->apply {
                    val lines=rich.text.lines().map {it.replace(Regex("^(?:• |\\d+\\. )"),"")}
                    val text=lines.mapIndexed {index,line->when(type) {ListType.BULLET->"• $line";ListType.NUMBERED->"${index+1}. $line";else->line}}.joinToString("\n")
                    rich.onValueChange(rich.textFieldValue.copy(text=text,selection=TextRange(text.length)))
                }},onIndent={model.indentText(item.id,it)},onUndo={apply {rich.undo()}},onRedo={apply {rich.redo()}})}
        } else Text("Tap the page to type",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
