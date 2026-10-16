package `in`.innovaticshub.notepad.di

import android.content.Context
import `in`.innovaticshub.notepad.data.FolderRepository
import `in`.innovaticshub.notepad.data.NoteRepository
import `in`.innovaticshub.notepad.data.TagRepository
import `in`.innovaticshub.notepad.data.db.DrawlyDatabase
import `in`.innovaticshub.notepad.data.prefs.SettingsRepository

/**
 * Manual dependency container (no DI framework in this project). Everything
 * is lazily constructed and process-scoped.
 */
class AppContainer(private val context: Context) {
    val applicationScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    val appContext: Context get() = context.applicationContext

    private val database: DrawlyDatabase by lazy { DrawlyDatabase.build(appContext) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    val noteRepository: NoteRepository by lazy { NoteRepository(database, appContext) }

    val folderRepository: FolderRepository by lazy {
        FolderRepository(database, noteRepository)
    }

    val tagRepository: TagRepository by lazy { TagRepository(database) }

    /**
     * Notes unlocked for the current process lifetime. Session-scoped on
     * purpose: re-launching the app re-locks locked notes.
     */
    val unlockedNoteIds: MutableSet<String> = mutableSetOf()
}
