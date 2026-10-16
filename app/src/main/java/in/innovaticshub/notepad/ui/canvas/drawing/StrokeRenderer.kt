package `in`.innovaticshub.notepad.ui.canvas.drawing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as OutlineStroke
import `in`.innovaticshub.notepad.ui.canvas.model.*
import kotlin.math.*

/** Canonical ink renderer for live strokes, note thumbnails, PNG and PDF output. */
internal fun DrawScope.renderStroke(stroke: Stroke, selected: Boolean = false, darkPaper:Boolean=false,inkColor:Color?=null) {
    val tool = stroke.toolConfig
    if (tool.isEraser || stroke.points.isEmpty()) return
    if (tool.type == ToolType.LASSO) {
        drawPath(stroke.path, Color(0xFF176B5B), style = OutlineStroke(2f, cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))))
        return
    }
    val data = stroke.renderData
    // Colour adaptation must not copy the stroke and invalidate its cached geometry.
    val color = inkColor?.let {it.copy(alpha=it.alpha*tool.opacity.coerceIn(0f,1f))} ?: tool.effectiveColor()
    when {
        tool.style == DrawingStyle.DASHED || tool.style == DrawingStyle.DOTTED -> {
            val width = tool.strokeWidth().coerceAtLeast(0.5f)
            if (data.samples.size == 1) drawCircle(color, data.samples.single().radius, Offset(data.samples.single().x, data.samples.single().y))
            else drawPath(stroke.path, color, style = OutlineStroke(width, cap = StrokeCap.Round,
                join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(
                    if (tool.style == DrawingStyle.DOTTED) floatArrayOf(0.1f, width * 2.5f) else floatArrayOf(width*4, width*2))))
        }
        tool.type == ToolType.PENCIL || tool.style == DrawingStyle.SKETCHY -> {
            drawPath(data.outline, color.copy(alpha = color.alpha * 0.23f))
            // Stable grain: it never changes as the canvas repaints or the note reopens.
            val stride = (data.samples.size / 2200).coerceAtLeast(1)
            data.samples.forEachIndexed { index, sample ->
                if (index % stride == 0) {
                    val previous = data.samples[(index-1).coerceAtLeast(0)]
                    val angle = atan2(sample.y-previous.y, sample.x-previous.x) + PI.toFloat()/2
                    repeat(3) { lane ->
                        val hash = ((index * 734287 + lane * 912931) xor 0x5f3759df)
                        val noise = (hash and 1023) / 511.5f - 1f
                        val spread = sample.radius * noise
                        drawCircle(color.copy(alpha = color.alpha * (0.22f + sample.pressure.coerceIn(0f,1f)*0.45f)),
                            (sample.radius * 0.18f).coerceIn(0.16f, 1.7f),
                            Offset(sample.x + cos(angle)*spread, sample.y + sin(angle)*spread))
                    }
                }
            }
        }
        tool.type == ToolType.HIGHLIGHTER -> {
            // A single filled envelope prevents dark seams within one highlight stroke.
            drawPath(data.outline, color, blendMode = if(darkPaper) BlendMode.SrcOver else BlendMode.Multiply)
        }
        tool.style == DrawingStyle.GLOW -> {
            listOf(3f to 0.05f, 2f to 0.12f).forEach { (factor, alpha) ->
                drawPath(stroke.path, color.copy(alpha = color.alpha*alpha),
                    style = OutlineStroke(tool.strokeWidth()*factor, cap=StrokeCap.Round, join=StrokeJoin.Round))
            }
            drawPath(data.outline, color)
        }
        else -> drawPath(data.outline, color)
    }
    if (selected) {
        val b = stroke.bounds
        drawRect(Color(0xFF176B5B).copy(alpha=0.6f), Offset(b.left-6,b.top-6),
            Size((b.right-b.left+12).coerceAtLeast(1f),(b.bottom-b.top+12).coerceAtLeast(1f)),
            style=OutlineStroke(1.5f,pathEffect=PathEffect.dashPathEffect(floatArrayOf(8f,6f))))
    }
}
