package `in`.innovaticshub.notepad.ui.canvas.zoom

import kotlin.math.abs

/** One intentional upward pull can append one page. Pinching never grows the document. */
internal class PageEndPull(private val threshold:Float,private val initialScale:Float) {
    var pullDistance=0f; private set
    private var zoomed=false
    private var appended=false
    val canCommit:Boolean get()=appended && pullDistance>=threshold && !zoomed
    fun update(panY:Float,pageBottom:Float,viewportHeight:Float,scale:Float,zoomAttempt:Boolean=false):Boolean {
        if(zoomAttempt || abs(scale/initialScale-1f)>0.03f) zoomed=true
        if(zoomed) {pullDistance=0f;return false}
        if(panY>=0f) {pullDistance=(pullDistance-panY).coerceAtLeast(0f);return false}
        val remaining=(pageBottom-viewportHeight).coerceAtLeast(0f)
        pullDistance+=(-panY-remaining).coerceAtLeast(0f)
        if(pullDistance<threshold || appended) return false
        appended=true
        return true
    }
}
