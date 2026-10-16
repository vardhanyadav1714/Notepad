package `in`.innovaticshub.notepad.feature.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Note background swatches, modeled on Samsung Notes' palette (plus the
 * default white).
 */
val NoteBackgroundColors = listOf(
    0xFFFFFFFF.toInt(),
    0xFFFDE9E8.toInt(),
    0xFFFFF3E0.toInt(),
    0xFFFFF9C4.toInt(),
    0xFFE8F5E9.toInt(),
    0xFFE0F2F1.toInt(),
    0xFFE3F2FD.toInt(),
    0xFFEDE7F6.toInt(),
    0xFFFCE4EC.toInt(),
    0xFFEFEBE9.toInt(),
    0xFFEFEBE9.toInt(),
    0xFFD7CCC8.toInt(),
    0xFFCFD8DC.toInt(),
    0xFFECEFF1.toInt()
)

/** Row of tappable color swatches with a check on the selected one. */
@Composable
fun NoteColorPalette(
    selectedArgb: Int,
    onPicked: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NoteBackgroundColors.take(10).forEach { argb ->
            val isSelected = argb == selectedArgb
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(argb), CircleShape)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape
                    )
                    .clickable { onPicked(argb) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
