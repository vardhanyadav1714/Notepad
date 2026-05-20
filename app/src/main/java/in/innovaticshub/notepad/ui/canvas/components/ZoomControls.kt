package `in`.innovaticshub.notepad.ui.canvas.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.innovaticshub.notepad.ui.canvas.zoom.ZoomState

/**
 * Zoom control panel with zoom in/out, reset, and mode toggle buttons.
 */
@Composable
fun ZoomControls(
    zoomState: ZoomState,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Zoom level display
            Text(
                text = "${(zoomState.scale * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Zoom in button
            ZoomControlButton(
                onClick = { zoomState.zoomBy(1.2f) },
                icon = { Icon(Icons.Default.Add, "Zoom in") }
            )

            // Zoom out button
            ZoomControlButton(
                onClick = { zoomState.zoomBy(0.8f) },
                icon = { Icon(Icons.Default.Remove, "Zoom out") }
            )

            // Reset button
            ZoomControlButton(
                onClick = { zoomState.reset() },
                icon = { Icon(Icons.Default.CenterFocusStrong, "Reset zoom") }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Mode toggle button
            ModeToggleButton(
                zoomState = zoomState
            )
        }
    }
}

/**
 * A single zoom control button.
 */
@Composable
private fun ZoomControlButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(2.dp)
    ) {
        icon()
    }
}

/**
 * Button to toggle between DRAW and TRANSFORM modes.
 */
@Composable
private fun ModeToggleButton(
    zoomState: ZoomState
) {
    val isTransformMode = zoomState.isTransformMode

    FloatingActionButton(
        onClick = { zoomState.toggleMode() },
        modifier = Modifier.size(40.dp),
        containerColor = if (isTransformMode) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(2.dp)
    ) {
        Icon(
            if (isTransformMode) Icons.Default.Close else Icons.Default.CenterFocusStrong,
            contentDescription = if (isTransformMode) "Switch to draw mode" else "Switch to transform mode"
        )
    }
}

/**
 * Simple zoom control buttons in a row.
 */
@Composable
fun ZoomControlRow(
    zoomState: ZoomState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Zoom out
        IconButton(onClick = { zoomState.zoomBy(0.8f) }) {
            Icon(Icons.Default.Remove, "Zoom out")
        }

        // Zoom level
        Text(
            text = "${(zoomState.scale * 100).toInt()}%",
            modifier = Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Zoom in
        IconButton(onClick = { zoomState.zoomBy(1.2f) }) {
            Icon(Icons.Default.Add, "Zoom in")
        }

        // Reset
        IconButton(onClick = { zoomState.reset() }) {
            Icon(Icons.Default.CenterFocusStrong, "Reset")
        }
    }
}
