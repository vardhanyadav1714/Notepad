package `in`.innovaticshub.notepad

import android.util.AtomicFile
import androidx.test.platform.app.InstrumentationRegistry
import `in`.innovaticshub.notepad.ui.canvas.storage.DrawingStorage
import `in`.innovaticshub.notepad.ui.canvas.storage.SavedSession
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DrawingStorageRecoveryTest {
    @Test fun interruptedSaveKeepsLastCompleteDrawingAndSupportsNextSave() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val path = "drawings/qa-${UUID.randomUUID()}.json"
        val target = File(context.filesDir, path)
        try {
            DrawingStorage.saveDocument(context, path, SavedSession(emptyList(), null, null, title = "Original"))
            // Model process death after starting a replacement but before finishWrite.
            AtomicFile(target).startWrite().use { it.write("{incomplete".toByteArray()) }
            assertEquals("Original", DrawingStorage.loadDocument(context, path)?.title)
            DrawingStorage.saveDocument(context, path, SavedSession(emptyList(), null, null, title = "Updated"))
            assertEquals("Updated", DrawingStorage.loadDocument(context, path)?.title)
        } finally { AtomicFile(target).delete() }
    }

    @Test fun concurrentLifecycleAndAutosaveWritesProduceCompleteDocument() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val path = "drawings/qa-${UUID.randomUUID()}.json"
        val pool = Executors.newFixedThreadPool(4)
        try {
            val titles = (1..20).map { "Revision $it " + "x".repeat(10000) }
            val writes = titles.map { title -> pool.submit {
                DrawingStorage.saveDocument(context, path, SavedSession(emptyList(), null, null, title = title))
                assertNotNull(DrawingStorage.loadDocument(context, path))
            } }
            writes.forEach { it.get(20, TimeUnit.SECONDS) }
            assertTrue(DrawingStorage.loadDocument(context, path)?.title in titles)
        } finally { pool.shutdownNow(); AtomicFile(File(context.filesDir, path)).delete() }
    }
}
