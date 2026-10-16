package `in`.innovaticshub.notepad

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import androidx.core.view.WindowCompat
import `in`.innovaticshub.notepad.data.prefs.ThemeMode
import `in`.innovaticshub.notepad.feature.NotebookApp
import `in`.innovaticshub.notepad.ui.theme.NotepadTheme

class MainActivity : FragmentActivity() {
    private val incomingRoomCode = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingRoomCode.value = intent.collaborationRoomCode()
        val container = (application as DrawlyApplication).container
        setContent {
            val mode by container.settingsRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val isDark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !isDark
                    isAppearanceLightNavigationBars = !isDark
                }
            }
            NotepadTheme(darkTheme = isDark) {
                NotebookApp(container, incomingRoomCode.value, isDark) { incomingRoomCode.value = null }
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
        val valid = (uri.scheme == "notepad" && uri.host == "canvas") ||
            (uri.scheme == "https" && uri.host == "drawly.smartattendance.xyz" && uri.path?.startsWith("/r/") == true)
        if (!valid) return null
        return (uri.getQueryParameter("room") ?: uri.lastPathSegment)?.takeIf { it.isNotBlank() }
    }
}
