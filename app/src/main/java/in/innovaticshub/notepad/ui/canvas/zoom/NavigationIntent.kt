package `in`.innovaticshub.notepad.ui.canvas.zoom

import kotlin.math.abs

/** Finger spacing naturally drifts during a scroll. Require sustained span motion to zoom. */
internal class NavigationIntent(private val slop:Float) {
    private var evidence=0f
    var zooming=false; private set
    fun update(spanDelta:Float,panDistance:Float):Boolean {
        evidence=(evidence+abs(spanDelta)*0.5f-panDistance*0.35f).coerceAtLeast(0f)
        if(evidence>slop) zooming=true
        return zooming
    }
}
