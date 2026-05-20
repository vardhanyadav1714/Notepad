package `in`.innovaticshub.notepad

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.innovaticshub.notepad.ui.canvas.screens.ModernDrawingScreen
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import `in`.innovaticshub.notepad.ui.theme.NotepadTheme

class MainActivity : ComponentActivity() {
    private val incomingRoomCode = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingRoomCode.value = intent.collaborationRoomCode()
        setContent {
            val isDark = isSystemInDarkTheme()
            val roomCode = incomingRoomCode.value
            NotepadTheme {
                val viewModel: DrawingViewModel = viewModel()
                LaunchedEffect(roomCode) {
                    if (!roomCode.isNullOrBlank()) {
                        viewModel.joinCollaborationRoom(roomCode)
                    }
                }
                ModernDrawingScreen(
                    viewModel = viewModel,
                    onBackClick = { finish() },
                    modifier = Modifier.fillMaxSize(),
                    isDark = isDark
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingRoomCode.value = intent.collaborationRoomCode()
    }

    private fun Intent?.collaborationRoomCode(): String? {
        val uri = this?.data ?: return null
        return uri.getQueryParameter("room") ?: uri.lastPathSegment
    }
}
