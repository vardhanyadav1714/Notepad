package `in`.innovaticshub.notepad.ui.canvas.drawing

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import `in`.innovaticshub.notepad.ui.canvas.collab.RoomMember
import `in`.innovaticshub.notepad.ui.canvas.model.PageStackLayout
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import `in`.innovaticshub.notepad.ui.canvas.zoom.ZoomState
import `in`.innovaticshub.notepad.ui.theme.LocalNoteAppearance
import kotlin.math.roundToInt

/** Receives pointer events passively: peers never steal local ink or gestures. */
@Composable
internal fun CollaborationOverlay(model: DrawingViewModel, zoom: ZoomState, layout: PageStackLayout, background: Color) {
    val presence by model.presence.collectAsState()
    val appearance = LocalNoteAppearance.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            presence.previews.values.forEach { preview ->
                val stroke = preview.stroke
                val page = layout.pages.firstOrNull { it.id == stroke.pageId } ?: return@forEach
                val origin = layout.origin(page.id)
                withTransform({ translate(zoom.offset.x + origin.x * zoom.scale, zoom.offset.y + origin.y * zoom.scale); scale(zoom.scale, zoom.scale, Offset.Zero) }) {
                    clipRect(0f, 0f, page.width, page.height) {
                        renderStroke(stroke, darkPaper = isDarkPaper(page, appearance.paper(background)), inkColor = appearance.ink(stroke.toolConfig.color, page.pdfPath != null))
                    }
                }
            }
        }
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        presence.members.filter { !it.isSelf && it.cursor != null && it.activity != "idle" }.forEach { member ->
            val cursor = member.cursor!!
            if (layout.pages.none { it.id == cursor.pageId }) return@forEach
            key(member.id, cursor.pageId) {
                PeerCursor(member, zoom, layout.origin(cursor.pageId), width, height)
            }
        }
    }
}

@Composable
private fun PeerCursor(member: RoomMember, zoom: ZoomState, pageOrigin: Offset, width: Float, height: Float) {
    val cursor = member.cursor ?: return
    val x by animateFloatAsState(cursor.x, tween(120, easing = LinearEasing), label = "peer-x")
    val y by animateFloatAsState(cursor.y, tween(120, easing = LinearEasing), label = "peer-y")
    val position = zoom.canvasToScreen(pageOrigin + Offset(x, y))
    if (position.x < 0 || position.y < 0 || position.x > width || position.y > height) return
    val density = LocalDensity.current
    val labelWidth = with(density) { 124.dp.toPx() }
    val labelHeight = with(density) { 32.dp.toPx() }
    val color = Color(member.colorArgb.toInt())
    // Clamp only the label: the pen marker must stay at the actual ink point,
    // including at the right/bottom edges of a zoomed or scrolled viewport.
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(Color.White, radius = 5.dp.toPx(), center = position)
        drawCircle(color, radius = 3.dp.toPx(), center = position)
    }
    val gap = with(density) { 8.dp.toPx() }
    Text(member.label, color = Color.White, style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.offset { IntOffset((position.x + gap).coerceIn(0f, (width - labelWidth).coerceAtLeast(0f)).roundToInt(),
            (position.y + gap).coerceIn(0f, (height - labelHeight).coerceAtLeast(0f)).roundToInt()) }
            .background(color, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 4.dp))
}
