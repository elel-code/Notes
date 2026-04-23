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
package net.micode.notes.tool

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.OperationApplicationException
import android.os.RemoteException
import android.util.Log
import net.micode.notes.data.Notes
import net.micode.notes.data.Notes.CallNote
import net.micode.notes.data.Notes.NoteColumns
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute

object DataUtils {
    const val TAG: String = "DataUtils"
    fun batchDeleteNotes(resolver: ContentResolver, ids: HashSet<Long?>?): Boolean {
        if (ids == null) {
            Log.d(TAG, "the ids is null")
            return true
        }
        if (ids.size == 0) {
            Log.d(TAG, "no id is in the hashset")
            return true
        }

        val operationList = ArrayList<ContentProviderOperation?>()
        for (id in ids) {
            if (id == Notes.ID_ROOT_FOLDER.toLong()) {
                Log.e(TAG, "Don't delete system folder root")
                continue
            }
            val builder = ContentProviderOperation
                .newDelete(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id!!))
            operationList.add(builder.build())
        }
        try {
            val results = resolver.applyBatch(Notes.AUTHORITY, operationList)
            if (results == null || results.size == 0 || results[0] == null) {
                Log.d(TAG, "delete notes failed, ids:" + ids.toString())
                return false
            }
            return true
        } catch (e: RemoteException) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.message))
        } catch (e: OperationApplicationException) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.message))
        }
        return false
    }

    fun moveNoteToFoler(resolver: ContentResolver, id: Long, srcFolderId: Long, desFolderId: Long) {
        val values = ContentValues()
        values.put(NoteColumns.Companion.PARENT_ID, desFolderId)
        values.put(NoteColumns.Companion.ORIGIN_PARENT_ID, srcFolderId)
        values.put(NoteColumns.Companion.LOCAL_MODIFIED, 1)
        resolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id), values, null, null)
    }

    fun batchMoveToFolder(
        resolver: ContentResolver, ids: HashSet<Long?>?,
        folderId: Long
    ): Boolean {
        if (ids == null) {
            Log.d(TAG, "the ids is null")
            return true
        }

        val operationList = ArrayList<ContentProviderOperation?>()
        for (id in ids) {
            val builder = ContentProviderOperation
                .newUpdate(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id!!))
            builder.withValue(NoteColumns.Companion.PARENT_ID, folderId)
            builder.withValue(NoteColumns.Companion.LOCAL_MODIFIED, 1)
            operationList.add(builder.build())
        }

        try {
            val results = resolver.applyBatch(Notes.AUTHORITY, operationList)
            if (results == null || results.size == 0 || results[0] == null) {
                Log.d(TAG, "delete notes failed, ids:" + ids.toString())
                return false
            }
            return true
        } catch (e: RemoteException) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.message))
        } catch (e: OperationApplicationException) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.message))
        }
        return false
    }

    /**
     * Get the all folder count except system folders [Notes.TYPE_SYSTEM]}
     */
    fun getUserFolderCount(resolver: ContentResolver): Int {
        val cursor = resolver.query(
            Notes.CONTENT_NOTE_URI,
            arrayOf<String>("COUNT(*)"),
            NoteColumns.Companion.TYPE + "=? AND " + NoteColumns.Companion.PARENT_ID + "<>?",
            arrayOf<String>(Notes.TYPE_FOLDER.toString(), Notes.ID_TRASH_FOLER.toString()),
            null
        )

        var count = 0
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                try {
                    count = cursor.getInt(0)
                } catch (e: IndexOutOfBoundsException) {
                    Log.e(TAG, "get folder count failed:" + e.toString())
                } finally {
                    cursor.close()
                }
            }
        }
        return count
    }

    fun visibleInNoteDatabase(resolver: ContentResolver, noteId: Long, type: Int): Boolean {
        val cursor = resolver.query(
            ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
            null,
            NoteColumns.Companion.TYPE + "=? AND " + NoteColumns.Companion.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER,
            arrayOf<String>(type.toString()),
            null
        )

        var exist = false
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true
            }
            cursor.close()
        }
        return exist
    }

    fun existInNoteDatabase(resolver: ContentResolver, noteId: Long): Boolean {
        val cursor = resolver.query(
            ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
            null, null, null, null
        )

        var exist = false
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true
            }
            cursor.close()
        }
        return exist
    }

    fun existInDataDatabase(resolver: ContentResolver, dataId: Long): Boolean {
        val cursor = resolver.query(
            ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId),
            null, null, null, null
        )

        var exist = false
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true
            }
            cursor.close()
        }
        return exist
    }

    fun checkVisibleFolderName(resolver: ContentResolver, name: String?): Boolean {
        val cursor = resolver.query(
            Notes.CONTENT_NOTE_URI, null,
            NoteColumns.Companion.TYPE + "=" + Notes.TYPE_FOLDER +
                    " AND " + NoteColumns.Companion.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER +
                    " AND " + NoteColumns.Companion.SNIPPET + "=?",
            arrayOf<String?>(name), null
        )
        var exist = false
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true
            }
            cursor.close()
        }
        return exist
    }

    fun getFolderNoteWidget(
        resolver: ContentResolver,
        folderId: Long
    ): HashSet<AppWidgetAttribute?>? {
        val c = resolver.query(
            Notes.CONTENT_NOTE_URI,
            arrayOf<String>(NoteColumns.Companion.WIDGET_ID, NoteColumns.Companion.WIDGET_TYPE),
            NoteColumns.Companion.PARENT_ID + "=?",
            arrayOf<String>(folderId.toString()),
            null
        )

        var set: HashSet<AppWidgetAttribute?>? = null
        if (c != null) {
            if (c.moveToFirst()) {
                set = HashSet<AppWidgetAttribute?>()
                do {
                    try {
                        val widget = AppWidgetAttribute()
                        widget.widgetId = c.getInt(0)
                        widget.widgetType = c.getInt(1)
                        set.add(widget)
                    } catch (e: IndexOutOfBoundsException) {
                        Log.e(TAG, e.toString())
                    }
                } while (c.moveToNext())
            }
            c.close()
        }
        return set
    }

    fun getCallNumberByNoteId(resolver: ContentResolver, noteId: Long): String? {
        val cursor = resolver.query(
            Notes.CONTENT_DATA_URI,
            arrayOf<String>(CallNote.PHONE_NUMBER),
            Notes.DataColumns.Companion.NOTE_ID + "=? AND " + Notes.DataColumns.Companion.MIME_TYPE + "=?",
            arrayOf<String>(noteId.toString(), CallNote.CONTENT_ITEM_TYPE),
            null
        )

        if (cursor != null && cursor.moveToFirst()) {
            try {
                return cursor.getString(0)
            } catch (e: IndexOutOfBoundsException) {
                Log.e(TAG, "Get call number fails " + e.toString())
            } finally {
                cursor.close()
            }
        }
        return ""
    }

    fun getNoteIdByPhoneNumberAndCallDate(
        resolver: ContentResolver,
        phoneNumber: String?,
        callDate: Long
    ): Long {
        val cursor = resolver.query(
            Notes.CONTENT_DATA_URI,
            arrayOf<String>(Notes.DataColumns.Companion.NOTE_ID),
            (CallNote.CALL_DATE + "=? AND " + Notes.DataColumns.Companion.MIME_TYPE + "=? AND PHONE_NUMBERS_EQUAL("
                    + CallNote.PHONE_NUMBER + ",?)"),
            arrayOf<String?>(callDate.toString(), CallNote.CONTENT_ITEM_TYPE, phoneNumber),
            null
        )

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                try {
                    return cursor.getLong(0)
                } catch (e: IndexOutOfBoundsException) {
                    Log.e(TAG, "Get call note id fails " + e.toString())
                }
            }
            cursor.close()
        }
        return 0
    }

    fun getSnippetById(resolver: ContentResolver, noteId: Long): String? {
        val cursor = resolver.query(
            Notes.CONTENT_NOTE_URI,
            arrayOf<String>(NoteColumns.Companion.SNIPPET),
            NoteColumns.Companion.ID + "=?",
            arrayOf<String>(noteId.toString()),
            null
        )

        if (cursor != null) {
            var snippet: String? = ""
            if (cursor.moveToFirst()) {
                snippet = cursor.getString(0)
            }
            cursor.close()
            return snippet
        }
        throw IllegalArgumentException("Note is not found with id: " + noteId)
    }

    fun getFormattedSnippet(snippet: String?): String? {
        var snippet = snippet
        if (snippet != null) {
            snippet = snippet.trim { it <= ' ' }
            val index = snippet.indexOf('\n')
            if (index != -1) {
                snippet = snippet.substring(0, index)
            }
        }
        return snippet
    }
}
