package `in`.innovaticshub.notepad.feature.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import `in`.innovaticshub.notepad.data.content.ListType
import `in`.innovaticshub.notepad.data.content.SpanKind
import `in`.innovaticshub.notepad.data.content.TextAlign

/** Inline text colors offered for font color / highlight, Samsung-style hues. */
val EditorColors = listOf(
    0xFF1C1C1E.toInt(),
    0xFFE74C3C.toInt(),
    0xFFE67E22.toInt(),
    0xFFF1C40F.toInt(),
    0xFF2ECC71.toInt(),
    0xFF1ABC9C.toInt(),
    0xFF3498DB.toInt(),
    0xFF9B59B6.toInt(),
    0xFFE91E63.toInt(),
    0xFF8D6E63.toInt()
)

/** Highlighter backgrounds (pale variants read better behind text). */
val HighlightColors = listOf(
    0xFFFFF176.toInt(),
    0xFFFFAB91.toInt(),
    0xFFA5D6A7.toInt(),
    0xFF90CAF9.toInt(),
    0xFFCE93D8.toInt()
)

/** Font sizes the size stepper cycles through. */
val FontSizes = listOf(14, 16, 18, 20, 24, 30)

/**
 * Bottom formatting bar above the IME: bold/italic/underline/strike,
 * color + highlight popovers, size, alignment, lists, indent, undo/redo.
 */
@Composable
fun EditorToolbar(
    formatting: TextFormatting,
    listType: ListType,
    textAlign: TextAlign,
    indentLevel: Int,
    canUndo: Boolean,
    canRedo: Boolean,
    onToggleStyle: (SpanKind) -> Unit,
    onFontColor: (Int) -> Unit,
    onHighlight: (Int) -> Unit,
    onCycleSize: () -> Unit,
    onAlign: (TextAlign) -> Unit,
    onListType: (ListType) -> Unit,
    onIndent: (Int) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalMinimumInteractiveComponentSize provides
        if(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp>=600) 40.dp() else 48.dp()) {
    Surface(color=MaterialTheme.colorScheme.surface,tonalElevation = 0.dp()) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
            }
            IconButton(onClick = { onToggleStyle(SpanKind.BOLD) }) {
                Icon(
                    Icons.Filled.FormatBold,
                    contentDescription = "Bold",
                    tint = if (formatting.isBold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = { onToggleStyle(SpanKind.ITALIC) }) {
                Icon(
                    Icons.Filled.FormatItalic,
                    contentDescription = "Italic",
                    tint = if (formatting.isItalic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = { onToggleStyle(SpanKind.UNDERLINE) }) {
                Icon(
                    Icons.Filled.FormatUnderlined,
                    contentDescription = "Underline",
                    tint = if (formatting.isUnderline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = { onToggleStyle(SpanKind.STRIKE) }) {
                Icon(
                    Icons.Filled.FormatStrikethrough,
                    contentDescription = "Strikethrough",
                    tint = if (formatting.isStrikethrough) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            ColorMenuButton(
                label = "Font color",
                icon = { Icon(Icons.Filled.FormatColorReset, contentDescription = "Font color") },
                colors = EditorColors,
                selected = formatting.colorArgb,
                onPicked = onFontColor
            )
            ColorMenuButton(
                label = "Highlight",
                icon = { Icon(Icons.Filled.Highlight, contentDescription = "Highlight") },
                colors = HighlightColors,
                selected = formatting.highlightArgb,
                onPicked = onHighlight
            )
            IconButton(onClick = onCycleSize) {
                Icon(Icons.Filled.FormatSize, contentDescription = "Font size")
            }
            IconButton(onClick = { onAlign(nextAlign(textAlign)) }) {
                Icon(
                    when (textAlign) {
                        TextAlign.LEFT -> Icons.Filled.FormatAlignLeft
                        TextAlign.CENTER -> Icons.Filled.FormatAlignCenter
                        TextAlign.RIGHT -> Icons.Filled.FormatAlignRight
                    },
                    contentDescription = "Align"
                )
            }
            IconButton(onClick = {
                onListType(if (listType == ListType.BULLET) ListType.NONE else ListType.BULLET)
            }) {
                Icon(
                    Icons.AutoMirrored.Filled.FormatListBulleted,
                    contentDescription = "Bulleted list",
                    tint = if (listType == ListType.BULLET) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = {
                onListType(if (listType == ListType.NUMBERED) ListType.NONE else ListType.NUMBERED)
            }) {
                Icon(
                    Icons.Filled.FormatListNumbered,
                    contentDescription = "Numbered list",
                    tint = if (listType == ListType.NUMBERED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = { onIndent(if (indentLevel < 2) indentLevel + 1 else indentLevel) }) {
                Icon(Icons.AutoMirrored.Filled.FormatIndentIncrease, contentDescription = "Indent")
            }
            IconButton(onClick = { onIndent(if (indentLevel > 0) indentLevel - 1 else indentLevel) }) {
                Icon(Icons.AutoMirrored.Filled.FormatIndentDecrease, contentDescription = "Outdent")
            }
        }
    }
}

}

@Composable
private fun ColorMenuButton(
    label: String,
    icon: @Composable () -> Unit,
    colors: List<Int>,
    selected: Int?,
    onPicked: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        icon()
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            colors.forEach { argb ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = {
                        Surface(
                            color = Color(argb),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            modifier = Modifier.size(22.dp())
                        ) {}
                    },
                    onClick = {
                        onPicked(argb)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun nextAlign(current: TextAlign): TextAlign = when (current) {
    TextAlign.LEFT -> TextAlign.CENTER
    TextAlign.CENTER -> TextAlign.RIGHT
    TextAlign.RIGHT -> TextAlign.LEFT
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())
