package net.micode.notes.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.micode.notes.data.Notes

@Database(
    entities = [RoomNoteEntity::class, RoomDataEntity::class],
    version = 4,
    exportSchema = false
)
abstract class NotesRoomDatabase : RoomDatabase() {
    abstract fun notesDao(): NotesRoomDao

    companion object {
        private const val DB_NAME = "note.db"
        private const val CREATE_NOTE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS note (
                _id INTEGER PRIMARY KEY,
                parent_id INTEGER NOT NULL DEFAULT 0,
                alert_date INTEGER NOT NULL DEFAULT 0,
                bg_color_id INTEGER NOT NULL DEFAULT 0,
                created_date INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
                has_attachment INTEGER NOT NULL DEFAULT 0,
                modified_date INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
                notes_count INTEGER NOT NULL DEFAULT 0,
                snippet TEXT NOT NULL DEFAULT '',
                type INTEGER NOT NULL DEFAULT 0,
                widget_id INTEGER NOT NULL DEFAULT 0,
                widget_type INTEGER NOT NULL DEFAULT -1,
                sync_id INTEGER NOT NULL DEFAULT 0,
                local_modified INTEGER NOT NULL DEFAULT 0,
                origin_parent_id INTEGER NOT NULL DEFAULT 0,
                gtask_id TEXT NOT NULL DEFAULT '',
                version INTEGER NOT NULL DEFAULT 0
            )
        """
        private const val CREATE_DATA_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS data (
                _id INTEGER PRIMARY KEY,
                mime_type TEXT NOT NULL,
                note_id INTEGER NOT NULL DEFAULT 0,
                created_date INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
                modified_date INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
                content TEXT NOT NULL DEFAULT '',
                data1 INTEGER,
                data2 INTEGER,
                data3 TEXT NOT NULL DEFAULT '',
                data4 TEXT NOT NULL DEFAULT '',
                data5 TEXT NOT NULL DEFAULT ''
            )
        """
        private const val CREATE_DATA_NOTE_ID_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS note_id_index ON data(note_id)"

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
            return Room.databaseBuilder(context, NotesRoomDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_1_4, MIGRATION_2_4, MIGRATION_3_4)
                .addCallback(SystemFoldersCallback)
                .build()
        }

        private val SystemFoldersCallback = object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                insertSystemFolders(db)
            }
        }

        private val MIGRATION_1_4 = object : Migration(1, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS note")
                db.execSQL("DROP TABLE IF EXISTS data")
                createSchema(db)
                insertSystemFolders(db)
            }
        }

        private val MIGRATION_2_4 = object : Migration(2, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE note ADD COLUMN gtask_id TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE note ADD COLUMN version INTEGER NOT NULL DEFAULT 0")
                db.execSQL(CREATE_DATA_NOTE_ID_INDEX_SQL)
                insertSystemFolders(db)
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE note ADD COLUMN version INTEGER NOT NULL DEFAULT 0")
                db.execSQL(CREATE_DATA_NOTE_ID_INDEX_SQL)
                insertSystemFolders(db)
            }
        }

        private fun createSchema(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_NOTE_TABLE_SQL)
            db.execSQL(CREATE_DATA_TABLE_SQL)
            db.execSQL(CREATE_DATA_NOTE_ID_INDEX_SQL)
        }

        private fun insertSystemFolders(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT OR IGNORE INTO note (_id, type) VALUES " +
                    "(${Notes.ID_CALL_RECORD_FOLDER}, ${Notes.TYPE_SYSTEM}), " +
                    "(${Notes.ID_ROOT_FOLDER}, ${Notes.TYPE_SYSTEM}), " +
                    "(${Notes.ID_TRASH_FOLER}, ${Notes.TYPE_SYSTEM})"
            )
        }
    }
}
