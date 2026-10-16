package `in`.innovaticshub.notepad.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

/**
 * External-content FTS4 index over note titles and denormalized search text.
 * Room generates the sync triggers against [NoteEntity]; rows are joined by
 * that entity's auto-generated [NoteEntity.rowId].
 */
@Fts4(contentEntity = NoteEntity::class)
@Entity(tableName = "notes_fts")
data class NoteFtsEntity(
    val title: String,
    @ColumnInfo(name = "searchText") val searchText: String
)
