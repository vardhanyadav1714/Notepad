package `in`.innovaticshub.notepad.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import `in`.innovaticshub.notepad.data.db.dao.FolderDao
import `in`.innovaticshub.notepad.data.db.dao.NoteBlockDao
import `in`.innovaticshub.notepad.data.db.dao.NoteDao
import `in`.innovaticshub.notepad.data.db.dao.PdfPageDao
import `in`.innovaticshub.notepad.data.db.dao.TagDao
import `in`.innovaticshub.notepad.data.db.entity.FolderEntity
import `in`.innovaticshub.notepad.data.db.entity.NoteBlockEntity
import `in`.innovaticshub.notepad.data.db.entity.NoteEntity
import `in`.innovaticshub.notepad.data.db.entity.NoteFtsEntity
import `in`.innovaticshub.notepad.data.db.entity.NoteTagCrossRef
import `in`.innovaticshub.notepad.data.db.entity.PdfPageEntity
import `in`.innovaticshub.notepad.data.db.entity.TagEntity

@Database(
    entities = [
        NoteEntity::class,
        NoteBlockEntity::class,
        FolderEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class,
        PdfPageEntity::class,
        NoteFtsEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class DrawlyDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun noteBlockDao(): NoteBlockDao
    abstract fun folderDao(): FolderDao
    abstract fun tagDao(): TagDao
    abstract fun pdfPageDao(): PdfPageDao

    companion object {
        fun build(context: Context): DrawlyDatabase =
            Room.databaseBuilder(context, DrawlyDatabase::class.java, "drawly-notes.db")
                .build()
    }
}
