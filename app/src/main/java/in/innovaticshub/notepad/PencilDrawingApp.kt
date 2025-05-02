package `in`.innovaticshub.notepad

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup

@Composable
fun PencilDrawingApp() {
    var currentColor by remember { mutableStateOf(Color.Black) }
    var currentTool by remember { mutableStateOf<DrawingTool>(DrawingTool.Pencil) }
    var isColorPaletteExpanded by remember { mutableStateOf(false) }
    var showThicknessPopup by remember { mutableStateOf(false) }
    var strokeWidth by remember { mutableStateOf(currentTool.stroke) }

    val paths = remember { mutableStateListOf<Pair<List<Offset>, DrawProperties>>() }
    var currentPath by remember { mutableStateOf<List<Offset>>(emptyList()) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Drawing Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White)
                .border(1.dp, Color.LightGray)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPath = listOf(offset)
                        },
                        onDrag = { change, _ ->
                            if (currentTool == DrawingTool.Eraser) {
                                val touchPoint = change.position
                                paths.removeAll { (points, _) ->
                                    points.any { point ->
                                        (point.x - touchPoint.x) * (point.x - touchPoint.x) +
                                                (point.y - touchPoint.y) * (point.y - touchPoint.y) <
                                                currentTool.stroke * currentTool.stroke
                                    }
                                }
                            } else {
                                currentPath = currentPath + change.position
                            }
                        },
                        onDragEnd = {
                            if (currentTool != DrawingTool.Eraser) {
                                paths.add(
                                    currentPath to DrawProperties(
                                        color = currentColor,
                                        strokeWidth = strokeWidth,
                                        alpha = currentTool.alpha
                                    )
                                )
                                currentPath = emptyList()
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                for ((points, properties) in paths) {
                    drawSmoothPath(points, properties)
                }
                if (currentTool != DrawingTool.Eraser) {
                    drawSmoothPath(
                        currentPath,
                        DrawProperties(currentColor, strokeWidth, currentTool.alpha)
                    )
                }
            }
        }

         AnimatedVisibility(
            visible = showThicknessPopup,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(0.9f),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Set Thickness", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Thickness: ${strokeWidth.toInt()}")
                        Slider(
                            value = strokeWidth,
                            onValueChange = { strokeWidth = it },
                            valueRange = 1f..30f
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { showThicknessPopup = false }) {
                            Text("Done")
                        }
                    }
                }
            }
        }

        // Combined Tool and Color Selector Row
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        DrawingTool.Pencil,
                        DrawingTool.Brush,
                        DrawingTool.Highlighter,
                        DrawingTool.Eraser
                    ).forEach { tool ->
                        CompactToolButton(
                            tool = tool,
                            isSelected = tool == currentTool,
                            onClick = {
                                currentTool = tool
                                if (tool != DrawingTool.Eraser) {
                                    strokeWidth = tool.stroke
                                    showThicknessPopup = true
                                } else {
                                    showThicknessPopup = false
                                }
                            }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = currentTool != DrawingTool.Eraser,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { isColorPaletteExpanded = !isColorPaletteExpanded }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(currentColor)
                                    .border(1.dp, Color.Gray, CircleShape)
                            )
                            Icon(
                                imageVector = if (isColorPaletteExpanded) Icons.Default.KeyboardArrowUp
                                else Icons.Default.ArrowDropDown,
                                contentDescription = "Color palette",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isColorPaletteExpanded && currentTool != DrawingTool.Eraser,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    val colorRows = listOf(
                        listOf(Color.Black, Color.DarkGray, Color.Gray, Color.LightGray, Color.White),
                        listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Cyan),
                        listOf(Color.Magenta, Color(0xFFFFA500), Color(0xFF800080), Color(0xFF008000), Color(0xFF800000))
                    )

                    colorRows.forEach { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            rowColors.forEach { color ->
                                ColorCircle(
                                    color = color,
                                    isSelected = currentColor == color,
                                    onClick = {
                                        currentColor = color
                                        isColorPaletteExpanded = false
                                    },
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CompactToolButton(
    tool: DrawingTool,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val iconRes = when (tool) {
        DrawingTool.Pencil -> R.drawable.img_2
        DrawingTool.Brush -> R.drawable.img_1
        DrawingTool.Highlighter -> R.drawable.img
        DrawingTool.Eraser -> R.drawable.img_3
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = tool.name,
            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun ColorCircle(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color.Black else Color.Gray,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

fun DrawScope.drawSmoothPath(points: List<Offset>, properties: DrawProperties) {
    if (points.size < 2) return

    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            lineTo(points[i].x, points[i].y)
        }
    }

    drawPath(
        path = path,
        color = properties.color.copy(alpha = properties.alpha),
        style = Stroke(width = properties.strokeWidth, cap = StrokeCap.Round)
    )
}

data class DrawProperties(
    val color: Color,
    val strokeWidth: Float,
    val alpha: Float = 1f
)

sealed class DrawingTool(val name: String, val stroke: Float, val alpha: Float) {
    object Pencil : DrawingTool("Pencil", stroke = 3f, alpha = 1f)
    object Brush : DrawingTool("Brush", stroke = 8f, alpha = 1f)
    object Highlighter : DrawingTool("Highlighter", stroke = 15f, alpha = 0.3f)
    object Eraser : DrawingTool("Eraser", stroke = 20f, alpha = 1f)
}
