package `in`.innovaticshub.notepad.boot

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import `in`.innovaticshub.notepad.data.NoteRepository
import `in`.innovaticshub.notepad.data.content.BlockType
import `in`.innovaticshub.notepad.data.content.NoteBlock
import `in`.innovaticshub.notepad.data.prefs.SettingsRepository
import `in`.innovaticshub.notepad.ui.canvas.storage.DrawingStorage
import `in`.innovaticshub.notepad.ui.canvas.storage.StrokeJson
import java.io.File
import java.util.UUID

/**
 * One-time import of the pre-notes-era canvas autosave
 * (`drawly_autosave.json`) into the Room world as a drawing note, so the
 * user's existing artwork appears in the new list on first launch. The old
 * file is left on disk; the DataStore flag keeps this idempotent.
 */
object LegacyMigration {

    suspend fun migrateIfNeeded(
        context: Context,
        settings: SettingsRepository,
        notes: NoteRepository
    ) {
        if (settings.isLegacyMigrated()) return
        run {
            val saved = DrawingStorage.load(context)
            if (saved != null && saved.strokes.isNotEmpty()) {
                val blockId = UUID.randomUUID().toString()
                val relativePath = "drawings/$blockId.json"
                val target = File(context.filesDir, relativePath)
                target.parentFile?.mkdirs()
                target.writeText(StrokeJson.strokesToJson(saved.strokes))

                val note = notes.createNote(
                    title = "My drawing",
                    initialBlocks = listOf(
                        NoteBlock(
                            id = blockId,
                            type = BlockType.DRAWING,
                            position = 0
                        )
                    )
                )
                val background = saved.canvasBackgroundColor
                notes.addDrawingBlock(
                    noteId = note.id,
                    blockId = blockId,
                    strokesFile = relativePath,
                    backgroundArgb = background?.toArgb() ?: 0xFFFAFAFA.toInt()
                )
            }
        }
        settings.markLegacyMigrated()
    }
}
