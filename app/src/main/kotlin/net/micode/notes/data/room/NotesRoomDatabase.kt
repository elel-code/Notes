package net.micode.notes.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.micode.notes.data.NotesDatabaseHelper

@Database(
    entities = [RoomNoteEntity::class, RoomDataEntity::class],
    version = 4,
    exportSchema = false
)
abstract class NotesRoomDatabase : RoomDatabase() {
    abstract fun notesDao(): NotesRoomDao

    companion object {
        private const val DB_NAME = "note.db"
        private const val ROOM_IDENTITY_HASH = "158d33edb30578c1c7990a178f255c4a"

        @Volatile
        private var instance: NotesRoomDatabase? = null

        operator fun invoke(context: Context): NotesRoomDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context.applicationContext).also { db ->
                    instance = db
                }
            }
        }

        private fun buildDatabase(context: Context): NotesRoomDatabase {
            // Bootstrap the legacy schema, triggers, and initial folders once,
            // then let Room own the same database file afterwards.
            val bootstrapHelper = NotesDatabaseHelper(context)
            try {
                bootstrapHelper.writableDatabase.apply {
                    execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                    execSQL(
                        "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                            "VALUES(42, '$ROOM_IDENTITY_HASH')"
                    )
                }
            } finally {
                bootstrapHelper.close()
            }

            return Room.databaseBuilder(context, NotesRoomDatabase::class.java, DB_NAME)
                .build()
        }
    }
}
