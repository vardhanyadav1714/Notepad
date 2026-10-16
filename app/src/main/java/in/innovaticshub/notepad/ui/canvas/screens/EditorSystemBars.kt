package `in`.innovaticshub.notepad.ui.canvas.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** The editor owns status-bar visibility only while it is on screen. */
@Composable
internal fun EditorSystemBars(hideStatusBar:Boolean) {
    val context=LocalContext.current
    val view=LocalView.current
    val window=context.editorActivity()?.window ?: return
    DisposableEffect(window,view) {
        val controller=WindowCompat.getInsetsController(window,view)
        val previousBehavior=controller.systemBarsBehavior
        controller.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller.show(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior=previousBehavior
        }
    }
    LaunchedEffect(window,view,hideStatusBar) {
        val controller=WindowCompat.getInsetsController(window,view)
        if(hideStatusBar) controller.hide(WindowInsetsCompat.Type.statusBars())
        else controller.show(WindowInsetsCompat.Type.statusBars())
    }
}

private tailrec fun Context.editorActivity():Activity? = when(this) {
    is Activity -> this
    is ContextWrapper -> if(baseContext===this) null else baseContext.editorActivity()
    else -> null
}
