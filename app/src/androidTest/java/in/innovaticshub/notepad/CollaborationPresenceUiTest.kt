package `in`.innovaticshub.notepad

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import `in`.innovaticshub.notepad.ui.canvas.screens.DrawingStudioScreen
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import `in`.innovaticshub.notepad.ui.theme.NotepadTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class CollaborationPresenceUiTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun capture(name: String) {
        val target = File(instrumentation.targetContext.getExternalFilesDir("presence-review"), name)
        target.parentFile!!.mkdirs()
        compose.waitForIdle()
        target.outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun artistsAppearInHeaderAndRoomSheetInLightAndDarkThemes() {
        CollaborationNetworkTest.Relay(presence = true).use { relay ->
            val model = DrawingViewModel(relay.repository())
            val peer = DrawingViewModel(relay.repository())
            val dark = mutableStateOf(false)
            try {
                main { model.createCollaborationRoom() }
                compose.waitUntil(15000) { model.uiState.value.collaborationConnected }
                main { peer.joinCollaborationRoom(model.uiState.value.collaborationRoomCode!!) }
                compose.waitUntil(15000) { model.presence.value.members.size == 2 }
                compose.setContent {
                    NotepadTheme(darkTheme = dark.value) {
                        DrawingStudioScreen(model, onBack = {}, onSaveText = {})
                    }
                }
                compose.onNodeWithTag("room-members-button").assertIsDisplayed()
                main { peer.startStroke(180f,240f) }
                compose.waitUntil(15000) { model.presence.value.previews.isNotEmpty() }
                capture("presence-canvas-light.png")
                // A peer's marker must not move left when its label is clamped
                // against the viewport edge.
                main { peer.cancelStroke() }
                compose.waitUntil(15000) { model.presence.value.previews.isEmpty() }
                val edge = model.uiState.value.currentPage.width - 5f
                main { peer.startStroke(edge, 240f) }
                compose.waitUntil(15000) { model.presence.value.members.any { !it.isSelf && it.cursor?.x == edge } }
                compose.waitForIdle()
                val marker = model.zoomState.canvasToScreen(Offset(edge, 240f))
                val pixels = compose.onNodeWithTag("ink-canvas").captureToImage().toPixelMap()
                val color = pixels[marker.x.toInt(), marker.y.toInt()]
                assertTrue("Peer marker must stay at the real ink position", color.blue > .6f && color.red < .4f)
                capture("presence-canvas-edge.png")
                main { peer.addStrokePoint(340f,360f); peer.endStroke() }
                compose.waitUntil(15000) { model.uiState.value.strokes.size == 1 }
                compose.onNodeWithTag("room-members-button").performClick()
                compose.onNodeWithText("In this room · 2").assertIsDisplayed()
                compose.onNodeWithText("You").assertIsDisplayed()
                capture("presence-members-light.png")
                compose.runOnIdle { dark.value = true }
                capture("presence-members-dark.png")
                assertEquals(1, model.snapshot().strokes.size)
            } finally { main { model.leaveCollaborationRoom(); peer.leaveCollaborationRoom() } }
        }
    }
}
