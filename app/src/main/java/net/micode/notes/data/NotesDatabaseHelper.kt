/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.micode.notes.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import net.micode.notes.data.Notes.DataConstants
import net.micode.notes.data.Notes.NoteColumns

class NotesDatabaseHelper(context: Context?) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    interface TABLE {
        companion object {
            const val NOTE: String = "note"

            const val DATA: String = "data"
        }
    }

    fun createNoteTable(db: SQLiteDatabase) {
        db.execSQL(CREATE_NOTE_TABLE_SQL)
        reCreateNoteTableTriggers(db)
        createSystemFolder(db)
        Log.d(TAG, "note table has been created")
    }

    private fun reCreateNoteTableTriggers(db: SQLiteDatabase) {
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_update")
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_update")
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_delete")
        db.execSQL("DROP TRIGGER IF EXISTS delete_data_on_delete")
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_insert")
        db.execSQL("DROP TRIGGER IF EXISTS folder_delete_notes_on_delete")
        db.execSQL("DROP TRIGGER IF EXISTS folder_move_notes_on_trash")

        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER)
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER)
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER)
        db.execSQL(NOTE_DELETE_DATA_ON_DELETE_TRIGGER)
        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER)
        db.execSQL(FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER)
        db.execSQL(FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER)
    }

    private fun createSystemFolder(db: SQLiteDatabase) {
        val values = ContentValues()

        /**
         * call record foler for call notes
         */
        values.put(NoteColumns.Companion.ID, Notes.ID_CALL_RECORD_FOLDER)
        values.put(NoteColumns.Companion.TYPE, Notes.TYPE_SYSTEM)
        db.insert(TABLE.Companion.NOTE, null, values)

        /**
         * root folder which is default folder
         */
        values.clear()
        values.put(NoteColumns.Companion.ID, Notes.ID_ROOT_FOLDER)
        values.put(NoteColumns.Companion.TYPE, Notes.TYPE_SYSTEM)
        db.insert(TABLE.Companion.NOTE, null, values)

        /**
         * temporary folder which is used for moving note
         */
        values.clear()
        values.put(NoteColumns.Companion.ID, Notes.ID_TEMPARAY_FOLDER)
        values.put(NoteColumns.Companion.TYPE, Notes.TYPE_SYSTEM)
        db.insert(TABLE.Companion.NOTE, null, values)

        /**
         * create trash folder
         */
        values.clear()
        values.put(NoteColumns.Companion.ID, Notes.ID_TRASH_FOLER)
        values.put(NoteColumns.Companion.TYPE, Notes.TYPE_SYSTEM)
        db.insert(TABLE.Companion.NOTE, null, values)
    }

    fun createDataTable(db: SQLiteDatabase) {
        db.execSQL(CREATE_DATA_TABLE_SQL)
        reCreateDataTableTriggers(db)
        db.execSQL(CREATE_DATA_NOTE_ID_INDEX_SQL)
        Log.d(TAG, "data table has been created")
    }

    private fun reCreateDataTableTriggers(db: SQLiteDatabase) {
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_insert")
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_update")
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_delete")

        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER)
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER)
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createNoteTable(db)
        createDataTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        var oldVersion = oldVersion
        var reCreateTriggers = false
        var skipV2 = false

        if (oldVersion == 1) {
            upgradeToV2(db)
            skipV2 = true // this upgrade including the upgrade from v2 to v3
            oldVersion++
        }

        if (oldVersion == 2 && !skipV2) {
            upgradeToV3(db)
            reCreateTriggers = true
            oldVersion++
        }

        if (oldVersion == 3) {
            upgradeToV4(db)
            oldVersion++
        }

        if (reCreateTriggers) {
            reCreateNoteTableTriggers(db)
            reCreateDataTableTriggers(db)
        }

        check(oldVersion == newVersion) {
            ("Upgrade notes database to version " + newVersion
                    + "fails")
        }
    }

    private fun upgradeToV2(db: SQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.Companion.NOTE)
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.Companion.DATA)
        createNoteTable(db)
        createDataTable(db)
    }

    private fun upgradeToV3(db: SQLiteDatabase) {
        // drop unused triggers
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_insert")
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_delete")
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_update")
        // add a column for gtask id
        db.execSQL(
            ("ALTER TABLE " + TABLE.Companion.NOTE + " ADD COLUMN " + NoteColumns.Companion.GTASK_ID
                    + " TEXT NOT NULL DEFAULT ''")
        )
        // add a trash system folder
        val values = ContentValues()
        values.put(NoteColumns.Companion.ID, Notes.ID_TRASH_FOLER)
        values.put(NoteColumns.Companion.TYPE, Notes.TYPE_SYSTEM)
        db.insert(TABLE.Companion.NOTE, null, values)
    }

    private fun upgradeToV4(db: SQLiteDatabase) {
        db.execSQL(
            ("ALTER TABLE " + TABLE.Companion.NOTE + " ADD COLUMN " + NoteColumns.Companion.VERSION
                    + " INTEGER NOT NULL DEFAULT 0")
        )
    }

    companion object {
        private const val DB_NAME = "note.db"

        private const val DB_VERSION = 4

        private const val TAG = "NotesDatabaseHelper"

        private var mInstance: NotesDatabaseHelper? = null

        private val CREATE_NOTE_TABLE_SQL = "CREATE TABLE " + TABLE.Companion.NOTE + "(" +
                NoteColumns.Companion.ID + " INTEGER PRIMARY KEY," +
                NoteColumns.Companion.PARENT_ID + " INTEGER NOT NULL DEFAULT 0," +
                NoteColumns.Companion.ALERTED_DATE + " INTEGER NOT NULL DEFAULT 0," +
                NoteColumns.Companion.BG_COLOR_ID + " INTEGER NOT NULL DEFAULT 0," +
                NoteColumns.Companion.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
                NoteColumns.Companion.HAS_ATTACHMENT + " INTEGER NOT NULL DEFAULT 0," +
                NoteColumns.Companion.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
                NoteColumns.Companion.NOTES_COUNT + " INTEGER NOT NULL DEFAULT 0," +
                NoteColumns.Companion.SNIPPET + " TEXT NOT NULL DEFAULT ''," +
                NoteColumns.Companion.TYPE + " INTEGER NOT NULL DEFAULT 0," +
                NoteColumns.Companion.WIDGET_ID + " INTEGER NOT NULL DEFAULT 0," +
                NoteColumns.Companion.WIDGET_TYPE + " INTEGER NOT NULL DEFAULT -1," +
                NoteColumns.Companion.SYNC_ID + " INTEGER NOT NULL DEFAULT 0," +
                NoteColumns.Companion.LOCAL_MODIFIED + " INTEGER NOT NULL DEFAULT 0," +
                NoteColumns.Companion.ORIGIN_PARENT_ID + " INTEGER NOT NULL DEFAULT 0," +
                NoteColumns.Companion.GTASK_ID + " TEXT NOT NULL DEFAULT ''," +
                NoteColumns.Companion.VERSION + " INTEGER NOT NULL DEFAULT 0" +
                ")"

        private val CREATE_DATA_TABLE_SQL = "CREATE TABLE " + TABLE.Companion.DATA + "(" +
                Notes.DataColumns.Companion.ID + " INTEGER PRIMARY KEY," +
                Notes.DataColumns.Companion.MIME_TYPE + " TEXT NOT NULL," +
                Notes.DataColumns.Companion.NOTE_ID + " INTEGER NOT NULL DEFAULT 0," +
                NoteColumns.Companion.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
                NoteColumns.Companion.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
                Notes.DataColumns.Companion.CONTENT + " TEXT NOT NULL DEFAULT ''," +
                Notes.DataColumns.Companion.DATA1 + " INTEGER," +
                Notes.DataColumns.Companion.DATA2 + " INTEGER," +
                Notes.DataColumns.Companion.DATA3 + " TEXT NOT NULL DEFAULT ''," +
                Notes.DataColumns.Companion.DATA4 + " TEXT NOT NULL DEFAULT ''," +
                Notes.DataColumns.Companion.DATA5 + " TEXT NOT NULL DEFAULT ''" +
                ")"

        private val CREATE_DATA_NOTE_ID_INDEX_SQL = "CREATE INDEX IF NOT EXISTS note_id_index ON " +
                TABLE.Companion.DATA + "(" + Notes.DataColumns.Companion.NOTE_ID + ");"

        /**
         * Increase folder's note count when move note to the folder
         */
        private val NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
            "CREATE TRIGGER increase_folder_count_on_update " +
                    " AFTER UPDATE OF " + NoteColumns.Companion.PARENT_ID + " ON " + TABLE.Companion.NOTE +
                    " BEGIN " +
                    "  UPDATE " + TABLE.Companion.NOTE +
                    "   SET " + NoteColumns.Companion.NOTES_COUNT + "=" + NoteColumns.Companion.NOTES_COUNT + " + 1" +
                    "  WHERE " + NoteColumns.Companion.ID + "=new." + NoteColumns.Companion.PARENT_ID + ";" +
                    " END"

        /**
         * Decrease folder's note count when move note from folder
         */
        private val NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
            "CREATE TRIGGER decrease_folder_count_on_update " +
                    " AFTER UPDATE OF " + NoteColumns.Companion.PARENT_ID + " ON " + TABLE.Companion.NOTE +
                    " BEGIN " +
                    "  UPDATE " + TABLE.Companion.NOTE +
                    "   SET " + NoteColumns.Companion.NOTES_COUNT + "=" + NoteColumns.Companion.NOTES_COUNT + "-1" +
                    "  WHERE " + NoteColumns.Companion.ID + "=old." + NoteColumns.Companion.PARENT_ID +
                    "  AND " + NoteColumns.Companion.NOTES_COUNT + ">0" + ";" +
                    " END"

        /**
         * Increase folder's note count when insert new note to the folder
         */
        private val NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER =
            "CREATE TRIGGER increase_folder_count_on_insert " +
                    " AFTER INSERT ON " + TABLE.Companion.NOTE +
                    " BEGIN " +
                    "  UPDATE " + TABLE.Companion.NOTE +
                    "   SET " + NoteColumns.Companion.NOTES_COUNT + "=" + NoteColumns.Companion.NOTES_COUNT + " + 1" +
                    "  WHERE " + NoteColumns.Companion.ID + "=new." + NoteColumns.Companion.PARENT_ID + ";" +
                    " END"

        /**
         * Decrease folder's note count when delete note from the folder
         */
        private val NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER =
            "CREATE TRIGGER decrease_folder_count_on_delete " +
                    " AFTER DELETE ON " + TABLE.Companion.NOTE +
                    " BEGIN " +
                    "  UPDATE " + TABLE.Companion.NOTE +
                    "   SET " + NoteColumns.Companion.NOTES_COUNT + "=" + NoteColumns.Companion.NOTES_COUNT + "-1" +
                    "  WHERE " + NoteColumns.Companion.ID + "=old." + NoteColumns.Companion.PARENT_ID +
                    "  AND " + NoteColumns.Companion.NOTES_COUNT + ">0;" +
                    " END"

        /**
         * Update note's content when insert data with type [DataConstants.NOTE]
         */
        private val DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER =
            "CREATE TRIGGER update_note_content_on_insert " +
                    " AFTER INSERT ON " + TABLE.Companion.DATA +
                    " WHEN new." + Notes.DataColumns.Companion.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
                    " BEGIN" +
                    "  UPDATE " + TABLE.Companion.NOTE +
                    "   SET " + NoteColumns.Companion.SNIPPET + "=new." + Notes.DataColumns.Companion.CONTENT +
                    "  WHERE " + NoteColumns.Companion.ID + "=new." + Notes.DataColumns.Companion.NOTE_ID + ";" +
                    " END"

        /**
         * Update note's content when data with [DataConstants.NOTE] type has changed
         */
        private val DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER =
            "CREATE TRIGGER update_note_content_on_update " +
                    " AFTER UPDATE ON " + TABLE.Companion.DATA +
                    " WHEN old." + Notes.DataColumns.Companion.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
                    " BEGIN" +
                    "  UPDATE " + TABLE.Companion.NOTE +
                    "   SET " + NoteColumns.Companion.SNIPPET + "=new." + Notes.DataColumns.Companion.CONTENT +
                    "  WHERE " + NoteColumns.Companion.ID + "=new." + Notes.DataColumns.Companion.NOTE_ID + ";" +
                    " END"

        /**
         * Update note's content when data with [DataConstants.NOTE] type has deleted
         */
        private val DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER =
            "CREATE TRIGGER update_note_content_on_delete " +
                    " AFTER delete ON " + TABLE.Companion.DATA +
                    " WHEN old." + Notes.DataColumns.Companion.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
                    " BEGIN" +
                    "  UPDATE " + TABLE.Companion.NOTE +
                    "   SET " + NoteColumns.Companion.SNIPPET + "=''" +
                    "  WHERE " + NoteColumns.Companion.ID + "=old." + Notes.DataColumns.Companion.NOTE_ID + ";" +
                    " END"

        /**
         * Delete datas belong to note which has been deleted
         */
        private val NOTE_DELETE_DATA_ON_DELETE_TRIGGER = "CREATE TRIGGER delete_data_on_delete " +
                " AFTER DELETE ON " + TABLE.Companion.NOTE +
                " BEGIN" +
                "  DELETE FROM " + TABLE.Companion.DATA +
                "   WHERE " + Notes.DataColumns.Companion.NOTE_ID + "=old." + NoteColumns.Companion.ID + ";" +
                " END"

        /**
         * Delete notes belong to folder which has been deleted
         */
        private val FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER =
            "CREATE TRIGGER folder_delete_notes_on_delete " +
                    " AFTER DELETE ON " + TABLE.Companion.NOTE +
                    " BEGIN" +
                    "  DELETE FROM " + TABLE.Companion.NOTE +
                    "   WHERE " + NoteColumns.Companion.PARENT_ID + "=old." + NoteColumns.Companion.ID + ";" +
                    " END"

        /**
         * Move notes belong to folder which has been moved to trash folder
         */
        private val FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER =
            "CREATE TRIGGER folder_move_notes_on_trash " +
                    " AFTER UPDATE ON " + TABLE.Companion.NOTE +
                    " WHEN new." + NoteColumns.Companion.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
                    " BEGIN" +
                    "  UPDATE " + TABLE.Companion.NOTE +
                    "   SET " + NoteColumns.Companion.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
                    "  WHERE " + NoteColumns.Companion.PARENT_ID + "=old." + NoteColumns.Companion.ID + ";" +
                    " END"

        @Synchronized
        fun getInstance(context: Context?): NotesDatabaseHelper {
            if (mInstance == null) {
                mInstance = NotesDatabaseHelper(context)
            }
            return mInstance!!
        }
    }
}
