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
package net.micode.notes.model

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.OperationApplicationException
import android.net.Uri
import android.os.RemoteException
import android.util.Log
import net.micode.notes.data.Notes
import net.micode.notes.data.Notes.CallNote
import net.micode.notes.data.Notes.NoteColumns
import net.micode.notes.data.Notes.TextNote

class Note {
    private val mNoteDiffValues: ContentValues
    private val mNoteData: NoteData

    init {
        mNoteDiffValues = ContentValues()
        mNoteData = NoteData()
    }

    fun setNoteValue(key: String?, value: String?) {
        mNoteDiffValues.put(key, value)
        mNoteDiffValues.put(NoteColumns.Companion.LOCAL_MODIFIED, 1)
        mNoteDiffValues.put(NoteColumns.Companion.MODIFIED_DATE, System.currentTimeMillis())
    }

    fun setTextData(key: String?, value: String?) {
        mNoteData.setTextData(key, value)
    }

    var textDataId: Long
        get() = mNoteData.mTextDataId
        set(id) {
            mNoteData.setTextDataId(id)
        }

    fun setCallDataId(id: Long) {
        mNoteData.setCallDataId(id)
    }

    fun setCallData(key: String?, value: String?) {
        mNoteData.setCallData(key, value)
    }

    val isLocalModified: Boolean
        get() = mNoteDiffValues.size() > 0 || mNoteData.isLocalModified

    fun syncNote(context: Context, noteId: Long): Boolean {
        require(noteId > 0) { "Wrong note id:" + noteId }

        if (!this.isLocalModified) {
            return true
        }

        /**
         * In theory, once data changed, the note should be updated on [NoteColumns.LOCAL_MODIFIED] and
         * [NoteColumns.MODIFIED_DATE]. For data safety, though update note fails, we also update the
         * note data info
         */
        if (context.getContentResolver().update(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), mNoteDiffValues, null,
                null
            ) == 0
        ) {
            Log.e(TAG, "Update note error, should not happen")
            // Do not return, fall through
        }
        mNoteDiffValues.clear()

        if (mNoteData.isLocalModified
            && (mNoteData.pushIntoContentResolver(context, noteId) == null)
        ) {
            return false
        }

        return true
    }

    private inner class NoteData {
        private var mTextDataId: Long

        private val mTextDataValues: ContentValues

        private var mCallDataId: Long

        private val mCallDataValues: ContentValues

        init {
            mTextDataValues = ContentValues()
            mCallDataValues = ContentValues()
            mTextDataId = 0
            mCallDataId = 0
        }

        val isLocalModified: Boolean
            get() = mTextDataValues.size() > 0 || mCallDataValues.size() > 0

        fun setTextDataId(id: Long) {
            require(id > 0) { "Text data id should larger than 0" }
            mTextDataId = id
        }

        fun setCallDataId(id: Long) {
            require(id > 0) { "Call data id should larger than 0" }
            mCallDataId = id
        }

        fun setCallData(key: String?, value: String?) {
            mCallDataValues.put(key, value)
            mNoteDiffValues.put(NoteColumns.Companion.LOCAL_MODIFIED, 1)
            mNoteDiffValues.put(NoteColumns.Companion.MODIFIED_DATE, System.currentTimeMillis())
        }

        fun setTextData(key: String?, value: String?) {
            mTextDataValues.put(key, value)
            mNoteDiffValues.put(NoteColumns.Companion.LOCAL_MODIFIED, 1)
            mNoteDiffValues.put(NoteColumns.Companion.MODIFIED_DATE, System.currentTimeMillis())
        }

        fun pushIntoContentResolver(context: Context, noteId: Long): Uri? {
            /**
             * Check for safety
             */
            require(noteId > 0) { "Wrong note id:" + noteId }

            val operationList = ArrayList<ContentProviderOperation?>()
            var builder: ContentProviderOperation.Builder? = null

            if (mTextDataValues.size() > 0) {
                mTextDataValues.put(Notes.DataColumns.Companion.NOTE_ID, noteId)
                if (mTextDataId == 0L) {
                    mTextDataValues.put(
                        Notes.DataColumns.Companion.MIME_TYPE,
                        TextNote.CONTENT_ITEM_TYPE
                    )
                    val uri = context.getContentResolver().insert(
                        Notes.CONTENT_DATA_URI,
                        mTextDataValues
                    )
                    try {
                        setTextDataId(uri!!.getPathSegments().get(1).toLong())
                    } catch (e: NumberFormatException) {
                        Log.e(TAG, "Insert new text data fail with noteId" + noteId)
                        mTextDataValues.clear()
                        return null
                    }
                } else {
                    builder = ContentProviderOperation.newUpdate(
                        ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mTextDataId
                        )
                    )
                    builder.withValues(mTextDataValues)
                    operationList.add(builder.build())
                }
                mTextDataValues.clear()
            }

            if (mCallDataValues.size() > 0) {
                mCallDataValues.put(Notes.DataColumns.Companion.NOTE_ID, noteId)
                if (mCallDataId == 0L) {
                    mCallDataValues.put(
                        Notes.DataColumns.Companion.MIME_TYPE,
                        CallNote.CONTENT_ITEM_TYPE
                    )
                    val uri = context.getContentResolver().insert(
                        Notes.CONTENT_DATA_URI,
                        mCallDataValues
                    )
                    try {
                        setCallDataId(uri!!.getPathSegments().get(1).toLong())
                    } catch (e: NumberFormatException) {
                        Log.e(TAG, "Insert new call data fail with noteId" + noteId)
                        mCallDataValues.clear()
                        return null
                    }
                } else {
                    builder = ContentProviderOperation.newUpdate(
                        ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mCallDataId
                        )
                    )
                    builder.withValues(mCallDataValues)
                    operationList.add(builder.build())
                }
                mCallDataValues.clear()
            }

            if (operationList.size > 0) {
                try {
                    val results = context.getContentResolver().applyBatch(
                        Notes.AUTHORITY, operationList
                    )
                    return if (results == null || results.size == 0 || results[0] == null)
                        null
                    else
                        ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId)
                } catch (e: RemoteException) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.message))
                    return null
                } catch (e: OperationApplicationException) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.message))
                    return null
                }
            }
            return null
        }

        companion object {
            private const val TAG = "NoteData"
        }
    }

    companion object {
        private const val TAG = "Note"

        /**
         * Create a new note id for adding a new note to databases
         */
        @Synchronized
        fun getNewNoteId(context: Context, folderId: Long): Long {
            // Create a new note in the database
            val values = ContentValues()
            val createdTime = System.currentTimeMillis()
            values.put(NoteColumns.Companion.CREATED_DATE, createdTime)
            values.put(NoteColumns.Companion.MODIFIED_DATE, createdTime)
            values.put(NoteColumns.Companion.TYPE, Notes.TYPE_NOTE)
            values.put(NoteColumns.Companion.LOCAL_MODIFIED, 1)
            values.put(NoteColumns.Companion.PARENT_ID, folderId)
            val uri = context.getContentResolver().insert(Notes.CONTENT_NOTE_URI, values)

            var noteId: Long = 0
            try {
                noteId = uri!!.getPathSegments().get(1).toLong()
            } catch (e: NumberFormatException) {
                Log.e(TAG, "Get note id error :" + e.toString())
                noteId = 0
            }
            check(noteId != -1L) { "Wrong note id:" + noteId }
            return noteId
        }
    }
}
