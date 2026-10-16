package `in`.innovaticshub.notepad.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Note list sort order (mirrors Samsung Notes: date modified/created/title). */
enum class NoteSort { MODIFIED, CREATED, TITLE;

    companion object {
        fun fromName(name: String?): NoteSort =
            entries.firstOrNull { it.name == name } ?: MODIFIED
    }
}

/** Note list layout. */
enum class NoteViewMode { GRID, LIST;

    companion object {
        fun fromName(name: String?): NoteViewMode =
            entries.firstOrNull { it.name == name } ?: GRID
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "drawly_settings"
)

/**
 * App preferences backed by DataStore: list layout, sort order, recent
 * searches, and one-time migration flags.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val SORT = stringPreferencesKey("note_sort")
        val VIEW_MODE = stringPreferencesKey("note_view_mode")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches_json")
        val LEGACY_MIGRATED = booleanPreferencesKey("legacy_autosave_imported")
        val THEME = stringPreferencesKey("theme")
    }

    private val dataStore get() = context.applicationContext.settingsDataStore

    val noteSort: Flow<NoteSort> = dataStore.data.map {
        NoteSort.fromName(it[Keys.SORT])
    }

    val viewMode: Flow<NoteViewMode> = dataStore.data.map {
        NoteViewMode.fromName(it[Keys.VIEW_MODE])
    }

    val recentSearches: Flow<List<String>> = dataStore.data.map {
        decodeSearches(it[Keys.RECENT_SEARCHES])
    }

    val legacyMigrated: Flow<Boolean> = dataStore.data.map {
        it[Keys.LEGACY_MIGRATED] ?: false
    }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[Keys.THEME] } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) { dataStore.edit { it[Keys.THEME] = mode.name } }

    suspend fun setNoteSort(sort: NoteSort) {
        dataStore.edit { it[Keys.SORT] = sort.name }
    }

    suspend fun setViewMode(mode: NoteViewMode) {
        dataStore.edit { it[Keys.VIEW_MODE] = mode.name }
    }

    suspend fun rememberSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        dataStore.edit { prefs ->
            val current = decodeSearches(prefs[Keys.RECENT_SEARCHES])
            prefs[Keys.RECENT_SEARCHES] =
                org.json.JSONArray((listOf(trimmed) + current.filter { it != trimmed }).take(8)).toString()
        }
    }

    suspend fun clearRecentSearches() {
        dataStore.edit { it.remove(Keys.RECENT_SEARCHES) }
    }

    suspend fun markLegacyMigrated() {
        dataStore.edit { it[Keys.LEGACY_MIGRATED] = true }
    }

    suspend fun isLegacyMigrated(): Boolean = dataStore.data.first()[Keys.LEGACY_MIGRATED] ?: false

    private fun decodeSearches(value: String?): List<String> = runCatching {
        val array = org.json.JSONArray(value ?: "[]")
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())
}
