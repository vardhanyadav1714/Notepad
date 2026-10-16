package `in`.innovaticshub.notepad.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFCF5D3B), onPrimary = Color.White,
    primaryContainer = Color(0xFFF8E5DE), onPrimaryContainer = Color(0xFF80351F),
    secondary = Color(0xFF99733C), secondaryContainer = Color(0xFFF2E7D4),
    tertiary = Color(0xFF76658D),
    background = Color(0xFFFCFCFC), onBackground = Color(0xFF252525),
    surface = Color.White, onSurface = Color(0xFF191919),
    surfaceVariant = Color(0xFFEDEDEF), onSurfaceVariant = Color(0xFF737378),
    outline = Color(0xFF98989D), outlineVariant = Color(0xFFE3E3E6)
)
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFA585), onPrimary = Color(0xFF54200E),
    primaryContainer = Color(0xFF603423), onPrimaryContainer = Color(0xFFFFDBCC),
    secondary = Color(0xFFE5C18D), secondaryContainer = Color(0xFF4F402C),
    tertiary = Color(0xFFD2BCE9),
    background = Color(0xFF080808), onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF171719), onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF2B2B2E), onSurfaceVariant = Color(0xFFB1B1B8),
    outline = Color(0xFF929299), outlineVariant = Color(0xFF343438)
)

@Composable
fun NotepadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val scheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(LocalContext.current)
        else dynamicLightColorScheme(LocalContext.current)
    } else if (darkTheme) DarkColorScheme else LightColorScheme
    androidx.compose.runtime.CompositionLocalProvider(LocalNoteAppearance provides NoteAppearance(darkTheme)) {
        MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
    }
}
