package `in`.innovaticshub.notepad

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import `in`.innovaticshub.notepad.data.content.RichTextData
import `in`.innovaticshub.notepad.feature.editor.NoteEditorViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class NotebookAutosaveTest {
    @Test fun contentAutosavesWithoutNavigatingAway() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val container = (instrumentation.targetContext.applicationContext as DrawlyApplication).container
        val note = container.noteRepository.createNote(title = "Autosave fixture")
        val store = ViewModelStore()
        lateinit var editor: NoteEditorViewModel
        try {
            instrumentation.runOnMainSync {
                editor = ViewModelProvider(store, NoteEditorViewModel.factory(note.id, null, container))[NoteEditorViewModel::class.java]
            }
            val loaded = withTimeout(10000) { editor.uiState.first { !it.isLoading } }
            instrumentation.runOnMainSync {
                editor.updateTitle("Autosaved title")
                editor.updateBlockText(loaded.blocks.single().id, RichTextData("Persisted without leaving", emptyList()))
            }
            withTimeout(10000) { container.noteRepository.observeNote(note.id).first { it?.title == "Autosaved title" } }
            assertEquals("Persisted without leaving", container.noteRepository.getBlocks(note.id).single().richText.text)
            withTimeout(10000) { editor.uiState.first { !it.hasUnsavedChanges && !it.isSaving } }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            container.noteRepository.purgeNote(note.id)
        }
    }

    @Test fun lockedNoteContentIsNotLoadedBeforeAuthentication() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val container = (instrumentation.targetContext.applicationContext as DrawlyApplication).container
        val note = container.noteRepository.createNote(title = "Private fixture", isLocked = true)
        val store = ViewModelStore()
        lateinit var editor: NoteEditorViewModel
        try {
            instrumentation.runOnMainSync {
                editor = ViewModelProvider(store, NoteEditorViewModel.factory(note.id, null, container))[NoteEditorViewModel::class.java]
            }
            val loaded = withTimeout(10000) { editor.uiState.first { !it.isLoading } }
            assertTrue(loaded.lockedGate)
            assertTrue(loaded.blocks.isEmpty())
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            container.noteRepository.purgeNote(note.id)
        }
    }
}
