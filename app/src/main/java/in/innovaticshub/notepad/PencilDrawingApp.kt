package `in`.innovaticshub.notepad

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

// Primary Color Constant for Consistency
val PrimaryPurple = Color(0xFF8B5CF6)

// Enum for Drawing Tools
enum class DrawingTool(val toolName: String, val stroke: Float, val alpha: Float = 1f) {
    Pencil("Pencil", 3f, 1f),
    Brush("Brush", 8f, 0.8f),
    Highlighter("Highlighter", 15f, 0.5f),
    Eraser("Eraser", 20f, 1f)
}

// Data class for drawing properties
data class DrawProperties(
    val color: Color,
    val strokeWidth: Float,
    val alpha: Float
)

// region State and Savers

@Immutable
data class DrawingState(
    val paths: List<Pair<List<Offset>, DrawProperties>> = emptyList(),
    val undonePaths: List<Pair<List<Offset>, DrawProperties>> = emptyList()
)

data class Page(
    var drawingState: DrawingState = DrawingState(),
    var name: String = "Page"
)

val OffsetSaver = listSaver<Offset, Float>(
    save = { listOf(it.x, it.y) },
    restore = { Offset(it[0], it[1]) }
)

val DrawPropertiesSaver = listSaver<DrawProperties, Any>(
    save = { listOf(it.color.value.toLong(), it.strokeWidth, it.alpha) },
    restore = { DrawProperties(Color(it[0] as Long), it[1] as Float, it[2] as Float) }
)

val PathDataSaver = listSaver<Pair<List<Offset>, DrawProperties>, Any>(
    save = { (points, properties) ->
        listOf(
            points.map { with(OffsetSaver) { save(it)!! } },
            with(DrawPropertiesSaver) { save(properties)!! }
        )
    },
    restore = { saved ->
        val points = (saved[0] as List<List<Float>>).map { OffsetSaver.restore(it)!! }
        val properties = DrawPropertiesSaver.restore(saved[1] as List<Any>)!!
        points to properties
    }
)

val DrawingStateSaver = listSaver<DrawingState, Any>(
    save = { state ->
        listOf(
            state.paths.map { with(PathDataSaver) { save(it)!! } },
            state.undonePaths.map { with(PathDataSaver) { save(it)!! } }
        )
    },
    restore = { saved ->
        DrawingState(
            paths = (saved[0] as List<List<Any>>).map { PathDataSaver.restore(it)!! },
            undonePaths = (saved[1] as List<List<Any>>).map { PathDataSaver.restore(it)!! }
        )
    }
)

val PageSaver = listSaver<Page, Any>(
    save = { page ->
        listOf(with(DrawingStateSaver) { save(page.drawingState)!! }, page.name)
    },
    restore = { saved ->
        Page(
            drawingState = DrawingStateSaver.restore(saved[0] as List<Any>)!!,
            name = saved[1] as String
        )
    }
)

val PageListSaver = listSaver<List<Page>, Any>(
    save = { pages -> pages.map { with(PageSaver) { save(it)!! } } },
    restore = { savedPages ->
        (savedPages as List<List<Any>>).map { PageSaver.restore(it)!! }
    }
)

class AppThemeState {
    var isDarkMode by mutableStateOf(false)
}

val LocalAppTheme = compositionLocalOf { AppThemeState() }

// Extension function for distance calculation
fun Offset.getDistance(other: Offset): Float {
    return sqrt((x - other.x).pow(2) + (y - other.y).pow(2))
}

// Function to check if a point is near a path segment
fun isPointNearPath(point: Offset, pathPoints: List<Offset>, tolerance: Float): Boolean {
    if (pathPoints.size < 2) return false

    for (i in 0 until pathPoints.size - 1) {
        val start = pathPoints[i]
        val end = pathPoints[i + 1]

        // Check distance from point to line segment
        if (isPointNearLineSegment(point, start, end, tolerance)) {
            return true
        }
    }
    return false
}

// Function to check if a point is near a line segment
fun isPointNearLineSegment(point: Offset, start: Offset, end: Offset, tolerance: Float): Boolean {
    val lineLength = start.getDistance(end)
    if (lineLength == 0f) return point.getDistance(start) <= tolerance

    val t = ((point.x - start.x) * (end.x - start.x) + (point.y - start.y) * (end.y - start.y)) / (lineLength * lineLength)
    val tClamped = t.coerceIn(0f, 1f)

    val projection = Offset(
        start.x + tClamped * (end.x - start.x),
        start.y + tClamped * (end.y - start.y)
    )

    return point.getDistance(projection) <= tolerance
}

// endregion

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnrememberedMutableState")
@Preview
@Composable
fun PencilDrawingApp() {
    val appTheme = remember { AppThemeState() }

    var pages by rememberSaveable(stateSaver = PageListSaver) {
        mutableStateOf(listOf(Page(name = "Page 1")))
    }
    var currentPageIndex by rememberSaveable { mutableStateOf(0) }

    val currentPage by derivedStateOf { pages[currentPageIndex] }

    var paths by rememberSaveable(stateSaver = listSaver(save = { it.map { p -> with(PathDataSaver) { save(p)!! } } }, restore = { (it as List<List<Any>>).map { l -> PathDataSaver.restore(l)!! } })) { mutableStateOf(currentPage.drawingState.paths) }
    var undonePaths by rememberSaveable(stateSaver = listSaver(save = { it.map { p -> with(PathDataSaver) { save(p)!! } } }, restore = { (it as List<List<Any>>).map { l -> PathDataSaver.restore(l)!! } })) { mutableStateOf(currentPage.drawingState.undonePaths) }

    // Fullscreen Mode
    var isFullScreen by rememberSaveable { mutableStateOf(false) }

    // Save currentColor state properly with rememberSaveable
    var currentColor by rememberSaveable(stateSaver = listSaver(
        save = { listOf(it.value.toLong()) },
        restore = { Color((it[0] as Long)) }
    )) { mutableStateOf(PrimaryPurple) }

    // Save other UI states properly
    var currentTool by rememberSaveable { mutableStateOf(DrawingTool.Pencil) }
    var isColorPaletteExpanded by rememberSaveable { mutableStateOf(false) }
    var showThicknessPopup by rememberSaveable { mutableStateOf(false) }
    var strokeWidth by rememberSaveable { mutableStateOf(3f) }

    LaunchedEffect(currentPageIndex) {
        val newPage = pages[currentPageIndex]
        paths = newPage.drawingState.paths
        undonePaths = newPage.drawingState.undonePaths
    }

    LaunchedEffect(paths, undonePaths) {
        pages = pages.toMutableList().also {
            if (it.isNotEmpty()) {
                it[currentPageIndex] = it[currentPageIndex].copy(
                    drawingState = DrawingState(paths, undonePaths)
                )
            }
        }
    }

    CompositionLocalProvider(LocalAppTheme provides appTheme) {
        val isDarkMode = appTheme.isDarkMode
        val coroutineScope = rememberCoroutineScope()

        val bgGradient = if (isDarkMode) {
            Brush.verticalGradient(listOf(Color(0xFF0F0C29), Color(0xFF302B63), Color(0xFF24243e)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFFEEF2FF), Color(0xFFE0E7FF), Color(0xFFF5F3FF)))
        }

        var currentPathPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
        var eraserPathPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

        fun undo() {
            if (paths.isNotEmpty()) {
                val lastPath = paths.last()
                paths = paths.dropLast(1)
                undonePaths = undonePaths + lastPath
            }
        }

        fun redo() {
            if (undonePaths.isNotEmpty()) {
                val lastUndonePath = undonePaths.last()
                undonePaths = undonePaths.dropLast(1)
                paths = paths + lastUndonePath
            }
        }

        fun clearCanvas() {
            paths = emptyList()
            undonePaths = emptyList()
        }

        val canUndo = paths.isNotEmpty()
        val canRedo = undonePaths.isNotEmpty()

        // State for horizontal scroll
        val toolbarScrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGradient)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Modern Floating Toolbar
                AnimatedVisibility(
                    visible = !isFullScreen,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(toolbarScrollState)
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    if (isDarkMode)
                                        Color.White.copy(alpha = 0.1f)
                                    else
                                        Color.White.copy(alpha = 0.9f)
                                )
                                .border(
                                    1.dp,
                                    if (isDarkMode) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.05f),
                                    RoundedCornerShape(24.dp)
                                )
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Full-Screen Toggle Icon
                            ModernIconButton(
                                icon = R.drawable.flscrexit,
                                isEnabled = true,
                                onClick = { isFullScreen = true },
                                isDarkMode = isDarkMode,
                                contentDescription = "Full Screen"
                            )

                            Spacer(Modifier.width(8.dp))

                            // Undo, Redo, Clear group
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ModernIconButton(R.drawable.baseline_undo_24, canUndo, { undo() }, isDarkMode, "Undo")
                                ModernIconButton(R.drawable.baseline_redo_24, canRedo, { redo() }, isDarkMode, "Redo")
                                ModernIconButton(R.drawable.baseline_delete_24, paths.isNotEmpty(), { clearCanvas() }, isDarkMode, "Clear")
                            }

                            Spacer(Modifier.width(8.dp))

                            // Tool selection group
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DrawingTool.entries.forEach { tool ->
                                    ModernToolButton(tool, tool == currentTool, {
                                        currentTool = tool
                                        if (tool != DrawingTool.Eraser) {
                                            strokeWidth = tool.stroke
                                            showThicknessPopup = true
                                        } else {
                                            strokeWidth = tool.stroke
                                        }
                                    }, isDarkMode)
                                }
                            }

                            // Color Picker Button
                            AnimatedVisibility(visible = currentTool != DrawingTool.Eraser) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    currentColor.copy(alpha = 0.3f),
                                                    currentColor.copy(alpha = 0.1f)
                                                )
                                            )
                                        )
                                        .clickable { isColorPaletteExpanded = !isColorPaletteExpanded }
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(currentColor)
                                            .border(3.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }

                // Canvas Area
                val canvasPadding = if (isFullScreen) 0.dp else 16.dp
                val canvasShape = if (isFullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(32.dp)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = canvasPadding)
                        .clip(canvasShape)
                        .background(
                            if (isDarkMode)
                                Color(0xFF1E1B4B).copy(alpha = 0.5f)
                            else
                                Color.White.copy(alpha = 0.95f)
                        )
                        .border(
                            1.dp,
                            if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.05f),
                            canvasShape
                        )
                        .clipToBounds()
                        .pointerInput(currentTool, strokeWidth) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (currentTool == DrawingTool.Eraser) {
                                        eraserPathPoints = listOf(offset)
                                        // Remove paths that are near the eraser start point
                                        paths = paths.filterNot { (pathPoints, _) ->
                                            isPointNearPath(offset, pathPoints, strokeWidth / 2)
                                        }
                                    } else {
                                        currentPathPoints = listOf(offset)
                                        coroutineScope.launch { undonePaths = emptyList() }
                                    }
                                },
                                onDrag = { change, _ ->
                                    if (currentTool == DrawingTool.Eraser) {
                                        eraserPathPoints = eraserPathPoints + change.position
                                        // Remove paths that are near the current eraser position
                                        paths = paths.filterNot { (pathPoints, _) ->
                                            isPointNearPath(change.position, pathPoints, strokeWidth / 2)
                                        }
                                    } else {
                                        currentPathPoints = currentPathPoints + change.position
                                    }
                                },
                                onDragEnd = {
                                    if (currentTool == DrawingTool.Eraser) {
                                        eraserPathPoints = emptyList()
                                    } else if (currentPathPoints.isNotEmpty()) {
                                        paths = paths + (currentPathPoints to DrawProperties(
                                            color = currentColor,
                                            strokeWidth = strokeWidth,
                                            alpha = currentTool.alpha
                                        ))
                                        currentPathPoints = emptyList()
                                    }
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Draw all saved paths
                        paths.forEach { (points, props) ->
                            if (points.size > 1) {
                                drawPath(
                                    Path().apply {
                                        moveTo(points.first().x, points.first().y)
                                        points.drop(1).forEach { lineTo(it.x, it.y) }
                                    },
                                    color = props.color.copy(alpha = props.alpha),
                                    style = Stroke(width = props.strokeWidth, cap = StrokeCap.Round)
                                )
                            }
                        }

                        // Draw current drawing path (preview)
                        if (currentPathPoints.size > 1) {
                            drawPath(
                                Path().apply {
                                    moveTo(currentPathPoints.first().x, currentPathPoints.first().y)
                                    currentPathPoints.drop(1).forEach { lineTo(it.x, it.y) }
                                },
                                color = currentColor.copy(alpha = currentTool.alpha),
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }

                        // Draw eraser preview
                        if (currentTool == DrawingTool.Eraser && eraserPathPoints.isNotEmpty()) {
                            // Draw eraser circle at current position
                            val lastPoint = eraserPathPoints.last()
                            drawCircle(
                                color = if (isDarkMode) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f),
                                radius = strokeWidth / 2,
                                center = lastPoint
                            )

                            // Draw eraser path
                            if (eraserPathPoints.size > 1) {
                                drawPath(
                                    Path().apply {
                                        moveTo(eraserPathPoints.first().x, eraserPathPoints.first().y)
                                        eraserPathPoints.drop(1).forEach { lineTo(it.x, it.y) }
                                    },
                                    color = if (isDarkMode) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.2f),
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                            }
                        }
                    }
                }

                // Modern Bottom Bar
                AnimatedVisibility(
                    visible = !isFullScreen,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    if (isDarkMode)
                                        Color.White.copy(alpha = 0.1f)
                                    else
                                        Color.White.copy(alpha = 0.9f)
                                )
                                .border(
                                    1.dp,
                                    if (isDarkMode) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.05f),
                                    RoundedCornerShape(24.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ModernIconButton(
                                    R.drawable.outline_arrow_back_24,
                                    currentPageIndex > 0,
                                    { if (currentPageIndex > 0) currentPageIndex-- },
                                    isDarkMode,
                                    "Previous"
                                )

                                // Page Counter
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (isDarkMode)
                                                PrimaryPurple.copy(alpha = 0.2f)
                                            else
                                                PrimaryPurple.copy(alpha = 0.1f)
                                        )
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        "Page ${currentPageIndex + 1}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isDarkMode) Color.White else Color(0xFF1F2937)
                                    )
                                }

                                ModernIconButton(
                                    R.drawable.outline_arrow_forward_24,
                                    currentPageIndex < pages.size - 1,
                                    { if (currentPageIndex < pages.size - 1) currentPageIndex++ },
                                    isDarkMode,
                                    "Next"
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ModernIconButton(
                                    R.drawable.outline_add_24,
                                    true,
                                    {
                                        pages = pages + Page(name = "Page ${pages.size + 1}")
                                        currentPageIndex = pages.size - 1
                                    },
                                    isDarkMode,
                                    "Add Page"
                                )

                                ModernThemeToggle(
                                    isDarkMode = isDarkMode,
                                    onToggle = { appTheme.isDarkMode = !appTheme.isDarkMode }
                                )
                            }
                        }
                    }
                }
            }

            // Full-Screen Exit Icon Overlay
            AnimatedVisibility(
                visible = isFullScreen,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                ModernIconButton(
                    icon = R.drawable.flscr,
                    isEnabled = true,
                    onClick = { isFullScreen = false },
                    isDarkMode = isDarkMode,
                    contentDescription = "Exit Full Screen"
                )
            }

            // Overlays
            if (isColorPaletteExpanded) {
                ModernColorPalette(
                    isDarkMode = isDarkMode,
                    currentColor = currentColor,
                    onColorSelected = { currentColor = it; isColorPaletteExpanded = false },
                    onDismiss = { isColorPaletteExpanded = false }
                )
            }

            if (showThicknessPopup) {
                ModernThicknessPopup(
                    isDarkMode = isDarkMode,
                    strokeWidth = strokeWidth,
                    currentColor = currentColor,
                    onThicknessChange = { strokeWidth = it },
                    onDismiss = { showThicknessPopup = false }
                )
            }
        }
    }
}

@Composable
fun ModernIconButton(
    icon: Int,
    isEnabled: Boolean,
    onClick: () -> Unit,
    isDarkMode: Boolean,
    contentDescription: String
) {
    val scale by animateFloatAsState(if (isEnabled) 1f else 0.95f, label = "scale")
    val alpha by animateFloatAsState(if (isEnabled) 1f else 0.5f, label = "alpha")

    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                if (isEnabled) {
                    if (isDarkMode)
                        PrimaryPurple.copy(alpha = 0.2f)
                    else
                        PrimaryPurple.copy(alpha = 0.1f)
                } else {
                    Color.Gray.copy(alpha = 0.1f)
                }
            )
            .clickable(enabled = isEnabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(icon),
            contentDescription = contentDescription,
            tint = if (isEnabled) {
                if (isDarkMode) Color.White.copy(alpha = 0.8f) else PrimaryPurple
            } else {
                Color.Gray.copy(alpha = 0.5f)
            },
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer(alpha = alpha)
        )
    }
}

@Composable
fun ModernToolButton(
    tool: DrawingTool,
    isSelected: Boolean,
    onClick: () -> Unit,
    isDarkMode: Boolean
) {
    val icon = when (tool) {
        DrawingTool.Pencil -> R.drawable.img_2
        DrawingTool.Brush -> R.drawable.img_1
        DrawingTool.Highlighter -> R.drawable.img
        DrawingTool.Eraser -> R.drawable.img_3
    }

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "toolScale"
    )

    val modifier = Modifier
        .size(48.dp)
        .scale(scale)
        .clip(CircleShape)
        .clickable(onClick = onClick)

    Box(
        modifier = if (isSelected) {
            modifier.background(
                brush = Brush.linearGradient(
                    listOf(
                        PrimaryPurple,
                        Color(0xFF6366F1)
                    )
                ),
                shape = CircleShape
            )
        } else {
            modifier.background(
                color = if (isDarkMode)
                    Color.White.copy(alpha = 0.08f)
                else
                    Color.Black.copy(alpha = 0.05f),
                shape = CircleShape
            )
        },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(icon),
            contentDescription = tool.toolName,
            tint = if (isSelected) {
                Color.White
            } else {
                if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color.Gray
            },
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun ModernThemeToggle(isDarkMode: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(
        targetValue = if (isDarkMode) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "rotation"
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    if (isDarkMode)
                        listOf(Color(0xFFFCD34D), Color(0xFFFBBF24))
                    else
                        listOf(PrimaryPurple, Color(0xFF6366F1))
                )
            )
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (isDarkMode) painterResource(R.drawable.img_6) else painterResource(R.drawable.img_5),
            contentDescription = "Toggle Theme",
            tint = Color.White,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer(rotationZ = rotation)
        )
    }
}

@Composable
fun CurrentColorIndicator(currentColor: Color, isDarkMode: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    Brush.sweepGradient(
                        listOf(Color.Red, Color.Yellow, Color.Green, Color.Blue, PrimaryPurple, Color.Red)
                    )
                )
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(currentColor)
                    .border(3.dp, if(isDarkMode) Color(0xFF1E1B4B) else Color.White, CircleShape)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text("Current", fontSize = 12.sp, color = if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color.Gray)
    }
}

@Composable
fun ModernColorPalette(
    isDarkMode: Boolean,
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = remember {
        listOf(
            Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFFFBBF24), Color(0xFFA3E635),
            Color(0xFF10B981), Color(0xFF06B6D4), Color(0xFF3B82F6), Color(0xFF6366F1),
            PrimaryPurple, Color(0xFFD946EF), Color(0xFFEC4899), Color(0xFFF43F5E),
            Color(0xFF64748B), Color(0xFF475569), Color(0xFF1E293B)
        )
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(
                    if (isDarkMode)
                        Color(0xFF1E1B4B).copy(alpha = 0.98f)
                    else
                        Color.White.copy(alpha = 0.98f)
                )
                .border(
                    1.dp,
                    if (isDarkMode) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.05f),
                    RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                )
                .padding(vertical = 24.dp)
                .clickable(enabled = false, onClick = {}),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isDarkMode) Color.White.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.3f))
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "Select Brush Color",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isDarkMode) Color.White else Color(0xFF1F2937),
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CurrentColorIndicator(currentColor, isDarkMode)

                Spacer(modifier = Modifier.width(8.dp))

                colors.forEach { color ->
                    val isSelected = color == currentColor
                    val scale by animateFloatAsState(
                        if (isSelected) 1.1f else 1f,
                        label = "colorScale"
                    )

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .scale(scale)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 4.dp else 2.dp,
                                color = if (isSelected) Color.White else color.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                            .clickable { onColorSelected(color) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Text(
                    "Close",
                    color = PrimaryPurple,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ModernThicknessPopup(
    isDarkMode: Boolean,
    strokeWidth: Float,
    currentColor: Color,
    onThicknessChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    if (isDarkMode)
                        Color(0xFF1E1B4B).copy(alpha = 0.95f)
                    else
                        Color.White
                )
                .border(
                    1.dp,
                    if (isDarkMode) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.05f),
                    RoundedCornerShape(32.dp)
                )
                .clickable(enabled = false, onClick = {})
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Brush Thickness",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDarkMode) Color.White else Color(0xFF1F2937)
                )

                Spacer(Modifier.height(24.dp))

                Slider(
                    value = strokeWidth,
                    onValueChange = onThicknessChange,
                    valueRange = 1f..30f,
                    steps = 29,
                    colors = SliderDefaults.colors(
                        thumbColor = currentColor,
                        activeTrackColor = currentColor.copy(alpha = 0.7f),
                        inactiveTrackColor = (if (isDarkMode) Color.White else Color.Gray).copy(alpha = 0.3f)
                    )
                )

                Spacer(Modifier.height(16.dp))

                Canvas(modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(if (isDarkMode) Color(0xFF1E1B4B) else Color.White)) {
                    val center = Offset(size.width / 2, size.height / 2)
                    drawCircle(
                        color = currentColor,
                        radius = strokeWidth / 2f + 2.dp.toPx(),
                        center = center
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "${strokeWidth.toInt()} px",
                    color = if (isDarkMode) Color.White.copy(alpha = 0.7f) else Color.Gray,
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.height(24.dp))

                TextButton(onClick = onDismiss) {
                    Text("DONE", color = PrimaryPurple, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}