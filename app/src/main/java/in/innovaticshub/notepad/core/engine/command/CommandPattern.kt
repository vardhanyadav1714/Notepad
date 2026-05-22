package `in`.innovaticshub.notepad.core.engine.command

import `in`.innovaticshub.notepad.core.domain.model.CanvasElement
import `in`.innovaticshub.notepad.core.domain.model.Stroke
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Command pattern implementation for undo/redo functionality.
 * All canvas mutations should go through commands.
 *
 * Benefits:
 * - Automatic undo/redo support
 * - Command history for replay
 * - Collaborative sync support
 * - Macro/automation support
 */
interface Command {
    val id: String
    val description: String
    val timestamp: Long

    /**
     * Execute the command on the given canvas state.
     * Returns the new canvas state.
     */
    fun execute(state: CanvasState): CanvasState

    /**
     * Undo the command, returning to previous state.
     */
    fun undo(state: CanvasState): CanvasState

    /**
     * Check if command can be merged with another (for optimization).
     * Used to merge continuous strokes into a single command.
     */
    fun canMergeWith(other: Command): Boolean = false

    /**
     * Merge with another command (if canMergeWith returns true).
     */
    fun mergeWith(other: Command): Command? = null

    /**
     * Check if command is idempotent (produces same result if executed multiple times).
     */
    val isIdempotent: Boolean get() = false
}

/**
 * Canvas state snapshot.
 */
data class CanvasState(
    val elements: List<CanvasElement> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val activeLayerId: String = CanvasElement.DEFAULT_LAYER_ID,
    val version: Int = 0
) {
    fun withElements(newElements: List<CanvasElement>) = copy(
        elements = newElements,
        version = version + 1
    )

    fun withElementAdded(element: CanvasElement) = copy(
        elements = elements + element,
        version = version + 1
    )

    fun withElementRemoved(id: String) = copy(
        elements = elements.filterNot { it.id == id },
        selectedIds = selectedIds - id,
        version = version + 1
    )

    fun withElementUpdated(element: CanvasElement) = copy(
        elements = elements.map { if (it.id == element.id) element else it },
        version = version + 1
    )

    fun getElementById(id: String): CanvasElement? = elements.find { it.id == id }
}

/**
 * Command history manager for undo/redo.
 */
class CommandHistory(
    private val maxHistorySize: Int = DEFAULT_MAX_HISTORY
) {
    private val undoStack: MutableList<Command> = mutableListOf()
    private val redoStack: MutableList<Command> = mutableListOf()

    private val mutableCanUndo: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val mutableCanRedo: MutableStateFlow<Boolean> = MutableStateFlow(false)

    val canUndo: StateFlow<Boolean> = mutableCanUndo.asStateFlow()
    val canRedo: StateFlow<Boolean> = mutableCanRedo.asStateFlow()

    private val mutableEvents: MutableSharedFlow<CommandEvent> = MutableSharedFlow()
    val events = mutableEvents.asSharedFlow()

    /**
     * Execute a command and add it to history.
     */
    fun execute(command: Command, state: CanvasState): CanvasState {
        // Try to merge with last command for optimization
        val commandToExecute = if (undoStack.isNotEmpty()) {
            val lastCommand = undoStack.last()
            if (lastCommand.canMergeWith(command)) {
                lastCommand.mergeWith(command) ?: command
            } else {
                command
            }
        } else {
            command
        }

        val newState = commandToExecute.execute(state)
        undoStack.add(commandToExecute)
        redoStack.clear()

        // Trim history if needed
        while (undoStack.size > maxHistorySize) {
            undoStack.removeAt(0)
        }

        updateFlags()
        emitEvent(CommandEvent.Executed(commandToExecute))

        return newState
    }

    /**
     * Undo the last command.
     */
    fun undo(state: CanvasState): CanvasState {
        if (undoStack.isEmpty()) return state

        val command = undoStack.removeAt(undoStack.lastIndex)
        val newState = command.undo(state)
        redoStack.add(command)

        updateFlags()
        emitEvent(CommandEvent.Undone(command))

        return newState
    }

    /**
     * Redo the last undone command.
     */
    fun redo(state: CanvasState): CanvasState {
        if (redoStack.isEmpty()) return state

        val command = redoStack.removeAt(redoStack.lastIndex)
        val newState = command.execute(state)
        undoStack.add(command)

        updateFlags()
        emitEvent(CommandEvent.Redone(command))

        return newState
    }

    /**
     * Clear all history.
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        updateFlags()
    }

    /**
     * Get a snapshot of current history for serialization.
     */
    fun getSnapshot(): List<Command> = undoStack.toList()

    /**
     * Restore history from snapshot.
     */
    fun restoreSnapshot(commands: List<Command>) {
        undoStack.clear()
        redoStack.clear()
        undoStack.addAll(commands.take(maxHistorySize))
        updateFlags()
    }

    private fun updateFlags() {
        mutableCanUndo.value = undoStack.isNotEmpty()
        mutableCanRedo.value = redoStack.isNotEmpty()
    }

    private fun emitEvent(event: CommandEvent) {
        mutableEvents.tryEmit(event)
    }

    companion object {
        const val DEFAULT_MAX_HISTORY = 100
    }
}

/**
 * Events emitted by command history.
 */
sealed class CommandEvent {
    data class Executed(val command: Command) : CommandEvent()
    data class Undone(val command: Command) : CommandEvent()
    data class Redone(val command: Command) : CommandEvent()
    data class BatchExecuted(val commands: List<Command>) : CommandEvent()
}

// ============================================
// Concrete Commands
// ============================================

/**
 * Command to add an element.
 */
data class AddElementCommand(
    override val id: String,
    val element: CanvasElement,
    override val timestamp: Long = System.currentTimeMillis()
) : Command {
    override val description = "Add ${element::class.simpleName}"

    override fun execute(state: CanvasState): CanvasState {
        return state.withElementAdded(element)
    }

    override fun undo(state: CanvasState): CanvasState {
        return state.withElementRemoved(element.id)
    }

    override fun canMergeWith(other: Command): Boolean {
        return other is AddElementCommand &&
                other.element::class == element::class &&
                other is ContinuableStroke
    }

    override fun mergeWith(other: Command): Command? {
        if (other !is AddElementCommand) return null
        if (element !is Stroke || other.element !is Stroke) return null

        // Merge strokes by combining points
        val mergedStroke = (element as Stroke).let { stroke ->
            stroke.copy(
                points = stroke.points + (other.element as Stroke).points,
                pressures = stroke.pressures + (other.element as Stroke).pressures,
                timestamps = stroke.timestamps + (other.element as Stroke).timestamps
            )
        }

        return copy(element = mergedStroke)
    }
}

interface ContinuableStroke

/**
 * Command to remove an element.
 */
data class RemoveElementCommand(
    override val id: String,
    val elementId: String,
    val element: CanvasElement,
    override val timestamp: Long = System.currentTimeMillis()
) : Command {
    override val description = "Remove ${element::class.simpleName}"

    override fun execute(state: CanvasState): CanvasState {
        return state.withElementRemoved(elementId)
    }

    override fun undo(state: CanvasState): CanvasState {
        return state.withElementAdded(element)
    }
}

/**
 * Command to modify an element.
 */
data class ModifyElementCommand(
    override val id: String,
    val elementId: String,
    val oldElement: CanvasElement,
    val newElement: CanvasElement,
    override val timestamp: Long = System.currentTimeMillis()
) : Command {
    override val description = "Modify ${oldElement::class.simpleName}"

    override fun execute(state: CanvasState): CanvasState {
        return state.withElementUpdated(newElement)
    }

    override fun undo(state: CanvasState): CanvasState {
        return state.withElementUpdated(oldElement)
    }

    override val isIdempotent: Boolean = true
}

/**
 * Command to move elements.
 */
data class MoveElementsCommand(
    override val id: String,
    val elementIds: List<String>,
    val delta: `in`.innovaticshub.notepad.core.domain.geometry.PointF,
    override val timestamp: Long = System.currentTimeMillis()
) : Command {
    override val description = "Move ${elementIds.size} element(s)"

    private var previousStates: List<CanvasElement> = emptyList()
    private var newStates: List<CanvasElement> = emptyList()

    override fun execute(state: CanvasState): CanvasState {
        previousStates = elementIds.mapNotNull { state.getElementById(it) }
        newStates = previousStates.map { oldElement ->
            when (oldElement) {
                is Stroke -> oldElement.withPoints(
                    oldElement.points.map { it + delta }
                )
                is `in`.innovaticshub.notepad.core.domain.model.TextBlock -> oldElement.copy(
                    position = oldElement.position + delta
                )
                is `in`.innovaticshub.notepad.core.domain.model.ImageElement -> oldElement.copy(
                    position = oldElement.position + delta
                )
                else -> oldElement
            }
        }

        return state.copy(
            elements = state.elements.map { element ->
                newStates.find { it.id == element.id } ?: element
            }
        )
    }

    override fun undo(state: CanvasState): CanvasState {
        return state.copy(
            elements = state.elements.map { element ->
                previousStates.find { it.id == element.id } ?: element
            }
        )
    }

    override fun canMergeWith(other: Command): Boolean {
        return other is MoveElementsCommand &&
                other.elementIds == elementIds &&
                other.timestamp - timestamp < MERGE_TIMEOUT_MS
    }

    override fun mergeWith(other: Command): Command? {
        if (other !is MoveElementsCommand) return null
        return copy(
            delta = delta + other.delta,
            timestamp = other.timestamp
        )
    }

    companion object {
        const val MERGE_TIMEOUT_MS = 500L
    }
}

/**
 * Batch command for executing multiple commands atomically.
 */
data class BatchCommand(
    val commands: List<Command>,
    override val timestamp: Long = System.currentTimeMillis()
) : Command {
    override val id: String get() = "batch_${timestamp}"
    override val description: String get() = "Batch (${commands.size} commands)"

    override fun execute(state: CanvasState): CanvasState {
        var currentState = state
        commands.forEach { command ->
            currentState = command.execute(currentState)
        }
        return currentState
    }

    override fun undo(state: CanvasState): CanvasState {
        var currentState = state
        commands.reversed().forEach { command ->
            currentState = command.undo(currentState)
        }
        return currentState
    }

    fun withAddedCommand(command: Command): BatchCommand {
        return copy(commands = commands + command)
    }
}

/**
 * Transactional command executor for complex operations.
 */
class CommandTransaction {
    private val commands: MutableList<Command> = mutableListOf()

    fun add(command: Command) {
        commands.add(command)
    }

    fun build(): Command {
        return if (commands.size == 1) {
            commands.first()
        } else {
            BatchCommand(commands)
        }
    }

    val isEmpty: Boolean get() = commands.isEmpty()
    val size: Int get() = commands.size
}
