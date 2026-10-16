package `in`.innovaticshub.notepad.ui.canvas.engine

import `in`.innovaticshub.notepad.ui.canvas.model.Stroke

/** App-local vector clipboard, so ink can be pasted into a different note. */
object InkClipboard {
    var strokes: List<Stroke> = emptyList()
    var texts:List<`in`.innovaticshub.notepad.ui.canvas.model.PageText> = emptyList()
    var images:List<`in`.innovaticshub.notepad.ui.canvas.model.PageImage> = emptyList()
    val isEmpty get()=strokes.isEmpty() && texts.isEmpty() && images.isEmpty()
}
