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

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import net.micode.notes.R
import net.micode.notes.data.Notes.NoteColumns
import net.micode.notes.data.Notes.TextNote
import net.micode.notes.data.NotesDatabaseHelper.TABLE


class NotesProvider : ContentProvider() {
    private var mHelper: NotesDatabaseHelper? = null

    override fun onCreate(): Boolean {
        mHelper = NotesDatabaseHelper.Companion.getInstance(getContext())
        return true
    }

    override fun query(
        uri: Uri, projection: Array<String?>?, selection: String, selectionArgs: Array<String?>?,
        sortOrder: String?
    ): Cursor? {
        var c: Cursor? = null
        val db = mHelper!!.getReadableDatabase()
        var id: String? = null
        when (mMatcher.match(uri)) {
            URI_NOTE -> c = db.query(
                TABLE.Companion.NOTE, projection, selection, selectionArgs, null, null,
                sortOrder
            )

            URI_NOTE_ITEM -> {
                id = uri.getPathSegments().get(1)
                c = db.query(
                    TABLE.Companion.NOTE, projection, (NoteColumns.Companion.ID + "=" + id
                            + parseSelection(selection)), selectionArgs, null, null, sortOrder
                )
            }

            URI_DATA -> c = db.query(
                TABLE.Companion.DATA, projection, selection, selectionArgs, null, null,
                sortOrder
            )

            URI_DATA_ITEM -> {
                id = uri.getPathSegments().get(1)
                c = db.query(
                    TABLE.Companion.DATA, projection, (Notes.DataColumns.Companion.ID + "=" + id
                            + parseSelection(selection)), selectionArgs, null, null, sortOrder
                )
            }

            URI_SEARCH, URI_SEARCH_SUGGEST -> {
                require(!(sortOrder != null || projection != null)) { "do not specify sortOrder, selection, selectionArgs, or projection" + "with this query" }

                val searchString: String? = null
                if (mMatcher.match(uri) == URI_SEARCH_SUGGEST) {
                    if (uri.getPathSegments().size > 1) {
                        searchString = uri.getPathSegments().get(1)
                    }
                } else {
                    searchString = uri.getQueryParameter("pattern")
                }

                if (TextUtils.isEmpty(searchString)) {
                    return null
                }

                try {
                    searchString = String.format("%%%s%%", searchString)
                    c = db.rawQuery(
                        NOTES_SNIPPET_SEARCH_QUERY,
                        arrayOf<String>(searchString)
                    )
                } catch (ex: IllegalStateException) {
                    Log.e(TAG, "got exception: " + ex.toString())
                }
            }

            else -> throw IllegalArgumentException("Unknown URI " + uri)
        }
        if (c != null) {
            c.setNotificationUri(getContext()!!.getContentResolver(), uri)
        }
        return c
    }

    override fun insert(uri: Uri, values: ContentValues): Uri? {
        val db = mHelper!!.getWritableDatabase()
        var dataId: Long = 0
        var noteId: Long = 0
        var insertedId: Long = 0
        when (mMatcher.match(uri)) {
            URI_NOTE -> {
                noteId = db.insert(TABLE.Companion.NOTE, null, values)
                insertedId = noteId
            }

            URI_DATA -> {
                if (values.containsKey(Notes.DataColumns.Companion.NOTE_ID)) {
                    noteId = values.getAsLong(Notes.DataColumns.Companion.NOTE_ID)
                } else {
                    Log.d(TAG, "Wrong data format without note id:" + values.toString())
                }
                run {
                    dataId = db.insert(TABLE.Companion.DATA, null, values)
                    insertedId = dataId
                }
            }

            else -> throw IllegalArgumentException("Unknown URI " + uri)
        }
        // Notify the note uri
        if (noteId > 0) {
            getContext()!!.getContentResolver().notifyChange(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), null
            )
        }

        // Notify the data uri
        if (dataId > 0) {
            getContext()!!.getContentResolver().notifyChange(
                ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId), null
            )
        }

        return ContentUris.withAppendedId(uri, insertedId)
    }

    override fun delete(uri: Uri, selection: String, selectionArgs: Array<String?>?): Int {
        var selection = selection
        var count = 0
        var id: String? = null
        val db = mHelper!!.getWritableDatabase()
        var deleteData = false
        when (mMatcher.match(uri)) {
            URI_NOTE -> {
                selection = "(" + selection + ") AND " + NoteColumns.Companion.ID + ">0 "
                count = db.delete(TABLE.Companion.NOTE, selection, selectionArgs)
            }

            URI_NOTE_ITEM -> {
                id = uri.getPathSegments().get(1)
                /**
                 * ID that smaller than 0 is system folder which is not allowed to
                 * trash
                 */
                val noteId = id.toLong()
                if (noteId <= 0) {
                    break
                }
                count = db.delete(
                    TABLE.Companion.NOTE,
                    NoteColumns.Companion.ID + "=" + id + parseSelection(selection), selectionArgs
                )
            }

            URI_DATA -> {
                count = db.delete(TABLE.Companion.DATA, selection, selectionArgs)
                deleteData = true
            }

            URI_DATA_ITEM -> {
                id = uri.getPathSegments().get(1)
                count = db.delete(
                    TABLE.Companion.DATA,
                    Notes.DataColumns.Companion.ID + "=" + id + parseSelection(selection),
                    selectionArgs
                )
                deleteData = true
            }

            else -> throw IllegalArgumentException("Unknown URI " + uri)
        }
        if (count > 0) {
            if (deleteData) {
                getContext()!!.getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null)
            }
            getContext()!!.getContentResolver().notifyChange(uri, null)
        }
        return count
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String,
        selectionArgs: Array<String>
    ): Int {
        var count = 0
        var id: String? = null
        val db = mHelper!!.getWritableDatabase()
        var updateData = false
        when (mMatcher.match(uri)) {
            URI_NOTE -> {
                increaseNoteVersion(-1, selection, selectionArgs)
                count = db.update(TABLE.Companion.NOTE, values, selection, selectionArgs)
            }

            URI_NOTE_ITEM -> {
                id = uri.getPathSegments().get(1)
                increaseNoteVersion(id.toLong(), selection, selectionArgs)
                count = db.update(
                    TABLE.Companion.NOTE, values, (NoteColumns.Companion.ID + "=" + id
                            + parseSelection(selection)), selectionArgs
                )
            }

            URI_DATA -> {
                count = db.update(TABLE.Companion.DATA, values, selection, selectionArgs)
                updateData = true
            }

            URI_DATA_ITEM -> {
                id = uri.getPathSegments().get(1)
                count = db.update(
                    TABLE.Companion.DATA, values, (Notes.DataColumns.Companion.ID + "=" + id
                            + parseSelection(selection)), selectionArgs
                )
                updateData = true
            }

            else -> throw IllegalArgumentException("Unknown URI " + uri)
        }

        if (count > 0) {
            if (updateData) {
                getContext()!!.getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null)
            }
            getContext()!!.getContentResolver().notifyChange(uri, null)
        }
        return count
    }

    private fun parseSelection(selection: String): String {
        return (if (!TextUtils.isEmpty(selection)) " AND (" + selection + ')' else "")
    }

    private fun increaseNoteVersion(id: Long, selection: String?, selectionArgs: Array<String>) {
        val sql = StringBuilder(120)
        sql.append("UPDATE ")
        sql.append(TABLE.Companion.NOTE)
        sql.append(" SET ")
        sql.append(NoteColumns.Companion.VERSION)
        sql.append("=" + NoteColumns.Companion.VERSION + "+1 ")

        if (id > 0 || !TextUtils.isEmpty(selection)) {
            sql.append(" WHERE ")
        }
        if (id > 0) {
            sql.append(NoteColumns.Companion.ID + "=" + id.toString())
        }
        if (!TextUtils.isEmpty(selection)) {
            var selectString: String = (if (id > 0) parseSelection(selection) else selection)!!
            for (args in selectionArgs) {
                selectString = selectString.replaceFirst("\\?".toRegex(), args)
            }
            sql.append(selectString)
        }

        mHelper!!.getWritableDatabase().execSQL(sql.toString())
    }

    override fun getType(uri: Uri?): String? {
        // TODO Auto-generated method stub
        return null
    }

    companion object {
        private val mMatcher: UriMatcher

        private const val TAG = "NotesProvider"

        private const val URI_NOTE = 1
        private const val URI_NOTE_ITEM = 2
        private const val URI_DATA = 3
        private const val URI_DATA_ITEM = 4

        private const val URI_SEARCH = 5
        private const val URI_SEARCH_SUGGEST = 6

        init {
            mMatcher = UriMatcher(UriMatcher.NO_MATCH)
            mMatcher.addURI(Notes.AUTHORITY, "note", URI_NOTE)
            mMatcher.addURI(Notes.AUTHORITY, "note/#", URI_NOTE_ITEM)
            mMatcher.addURI(Notes.AUTHORITY, "data", URI_DATA)
            mMatcher.addURI(Notes.AUTHORITY, "data/#", URI_DATA_ITEM)
            mMatcher.addURI(Notes.AUTHORITY, "search", URI_SEARCH)
            mMatcher.addURI(
                Notes.AUTHORITY,
                SearchManager.SUGGEST_URI_PATH_QUERY,
                URI_SEARCH_SUGGEST
            )
            mMatcher.addURI(
                Notes.AUTHORITY,
                SearchManager.SUGGEST_URI_PATH_QUERY + "/*",
                URI_SEARCH_SUGGEST
            )
        }

        /**
         * x'0A' represents the '\n' character in sqlite. For title and content in the search result,
         * we will trim '\n' and white space in order to show more information.
         */
        private val NOTES_SEARCH_PROJECTION: String = (NoteColumns.Companion.ID + ","
                + NoteColumns.Companion.ID + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA + ","
                + "TRIM(REPLACE(" + NoteColumns.Companion.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 + ","
                + "TRIM(REPLACE(" + NoteColumns.Companion.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_2 + ","
                + R.drawable.search_result + " AS " + SearchManager.SUGGEST_COLUMN_ICON_1 + ","
                + "'" + Intent.ACTION_VIEW + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_ACTION + ","
                + "'" + TextNote.CONTENT_TYPE + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA)

        private val NOTES_SNIPPET_SEARCH_QUERY = ("SELECT " + NOTES_SEARCH_PROJECTION
                + " FROM " + TABLE.Companion.NOTE
                + " WHERE " + NoteColumns.Companion.SNIPPET + " LIKE ?"
                + " AND " + NoteColumns.Companion.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER
                + " AND " + NoteColumns.Companion.TYPE + "=" + Notes.TYPE_NOTE)
    }
}
