package `in`.innovaticshub.notepad.ui.canvas.engine


/**
 * Bounded undo/redo snapshots; document owners include pages and ink together.
 */
class CanvasHistory<T>(private val maxSteps: Int = 80) {
    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun pushBeforeChange(before: T) {
        undoStack.addLast(before)
        if (undoStack.size > maxSteps) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun undo(current: T): T? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(current)
        return undoStack.removeAt(undoStack.lastIndex)
    }

    fun redo(current: T): T? {
        if (redoStack.isEmpty()) return null
        undoStack.addLast(current)
        return redoStack.removeAt(redoStack.lastIndex)
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    /** Rebase external changes so undo/redo cannot discard another participant's new ink. */
    fun mapSnapshots(transform:(T)->T) {
        val undo=undoStack.map(transform);val redo=redoStack.map(transform)
        undoStack.clear();undoStack.addAll(undo);redoStack.clear();redoStack.addAll(redo)
    }

}
