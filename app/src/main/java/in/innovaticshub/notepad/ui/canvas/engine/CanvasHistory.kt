package `in`.innovaticshub.notepad.ui.canvas.engine

import `in`.innovaticshub.notepad.ui.canvas.model.Stroke

/**
 * Undo/redo history storing full stroke-list snapshots for draw and erase operations.
 */
class CanvasHistory(private val maxSteps: Int = 80) {
    private val undoStack = ArrayDeque<HistoryEntry>()
    private val redoStack = ArrayDeque<HistoryEntry>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun pushBeforeChange(before: List<Stroke>) {
        undoStack.addLast(HistoryEntry(before))
        if (undoStack.size > maxSteps) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun undo(current: List<Stroke>): List<Stroke>? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(HistoryEntry(current))
        return undoStack.removeAt(undoStack.lastIndex).strokes
    }

    fun redo(current: List<Stroke>): List<Stroke>? {
        if (redoStack.isEmpty()) return null
        undoStack.addLast(HistoryEntry(current))
        return redoStack.removeAt(redoStack.lastIndex).strokes
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    private data class HistoryEntry(val strokes: List<Stroke>)
}
