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
package net.micode.notes.gtask.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.util.Log
import net.micode.notes.data.Notes
import net.micode.notes.data.Notes.DataConstants
import net.micode.notes.data.Notes.NoteColumns
import net.micode.notes.data.NotesDatabaseHelper.TABLE
import net.micode.notes.gtask.exception.ActionFailureException
import org.json.JSONException
import org.json.JSONObject

class SqlData {
    private val mContentResolver: ContentResolver

    private var mIsCreate: Boolean

    var id: Long = 0
        private set

    private var mDataMimeType: String? = null

    private var mDataContent: String? = null

    private var mDataContentData1: Long = 0

    private var mDataContentData3: String? = null

    private val mDiffDataValues: ContentValues

    constructor(context: Context) {
        mContentResolver = context.getContentResolver()
        mIsCreate = true
        this.id = INVALID_ID.toLong()
        mDataMimeType = DataConstants.NOTE
        mDataContent = ""
        mDataContentData1 = 0
        mDataContentData3 = ""
        mDiffDataValues = ContentValues()
    }

    constructor(context: Context, c: Cursor) {
        mContentResolver = context.getContentResolver()
        mIsCreate = false
        loadFromCursor(c)
        mDiffDataValues = ContentValues()
    }

    private fun loadFromCursor(c: Cursor) {
        this.id = c.getLong(DATA_ID_COLUMN)
        mDataMimeType = c.getString(DATA_MIME_TYPE_COLUMN)
        mDataContent = c.getString(DATA_CONTENT_COLUMN)
        mDataContentData1 = c.getLong(DATA_CONTENT_DATA_1_COLUMN)
        mDataContentData3 = c.getString(DATA_CONTENT_DATA_3_COLUMN)
    }

    @get:Throws(JSONException::class)
    @set:Throws(JSONException::class)
    var content: JSONObject?
        get() {
            if (mIsCreate) {
                Log.e(
                    TAG,
                    "it seems that we haven't created this in database yet"
                )
                return null
            }
            val js = JSONObject()
            js.put(Notes.DataColumns.Companion.ID, this.id)
            js.put(Notes.DataColumns.Companion.MIME_TYPE, mDataMimeType)
            js.put(Notes.DataColumns.Companion.CONTENT, mDataContent)
            js.put(Notes.DataColumns.Companion.DATA1, mDataContentData1)
            js.put(Notes.DataColumns.Companion.DATA3, mDataContentData3)
            return js
        }
        set(js) {
            val dataId =
                if (js!!.has(Notes.DataColumns.Companion.ID)) js.getLong(Notes.DataColumns.Companion.ID) else INVALID_ID.toLong()
            if (mIsCreate || this.id != dataId) {
                mDiffDataValues.put(Notes.DataColumns.Companion.ID, dataId)
            }
            this.id = dataId

            val dataMimeType =
                if (js.has(Notes.DataColumns.Companion.MIME_TYPE))
                    js.getString(Notes.DataColumns.Companion.MIME_TYPE)
                else
                    DataConstants.NOTE
            if (mIsCreate || mDataMimeType != dataMimeType) {
                mDiffDataValues.put(
                    Notes.DataColumns.Companion.MIME_TYPE,
                    dataMimeType
                )
            }
            mDataMimeType = dataMimeType

            val dataContent =
                if (js.has(Notes.DataColumns.Companion.CONTENT)) js.getString(
                    Notes.DataColumns.Companion.CONTENT
                ) else ""
            if (mIsCreate || mDataContent != dataContent) {
                mDiffDataValues.put(
                    Notes.DataColumns.Companion.CONTENT,
                    dataContent
                )
            }
            mDataContent = dataContent

            val dataContentData1 =
                if (js.has(Notes.DataColumns.Companion.DATA1)) js.getLong(Notes.DataColumns.Companion.DATA1) else 0
            if (mIsCreate || mDataContentData1 != dataContentData1) {
                mDiffDataValues.put(
                    Notes.DataColumns.Companion.DATA1,
                    dataContentData1
                )
            }
            mDataContentData1 = dataContentData1

            val dataContentData3 =
                if (js.has(Notes.DataColumns.Companion.DATA3)) js.getString(
                    Notes.DataColumns.Companion.DATA3
                ) else ""
            if (mIsCreate || mDataContentData3 != dataContentData3) {
                mDiffDataValues.put(
                    Notes.DataColumns.Companion.DATA3,
                    dataContentData3
                )
            }
            mDataContentData3 = dataContentData3
        }

    fun commit(noteId: Long, validateVersion: Boolean, version: Long) {
        if (mIsCreate) {
            if (this.id == INVALID_ID.toLong() && mDiffDataValues.containsKey(Notes.DataColumns.Companion.ID)) {
                mDiffDataValues.remove(Notes.DataColumns.Companion.ID)
            }

            mDiffDataValues.put(Notes.DataColumns.Companion.NOTE_ID, noteId)
            val uri = mContentResolver.insert(Notes.CONTENT_DATA_URI, mDiffDataValues)
            try {
                this.id = uri!!.getPathSegments().get(1).toLong()
            } catch (e: NumberFormatException) {
                Log.e(TAG, "Get note id error :" + e.toString())
                throw ActionFailureException("create note failed")
            }
        } else {
            if (mDiffDataValues.size() > 0) {
                var result = 0
                if (!validateVersion) {
                    result = mContentResolver.update(
                        ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, this.id
                        ), mDiffDataValues, null, null
                    )
                } else {
                    result = mContentResolver.update(
                        ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, this.id
                        ),
                        mDiffDataValues,
                        (" ? in (SELECT " + NoteColumns.Companion.ID + " FROM " + TABLE.Companion.NOTE
                                + " WHERE " + NoteColumns.Companion.VERSION + "=?)"),
                        arrayOf<String>(
                            noteId.toString(), version.toString()
                        )
                    )
                }
                if (result == 0) {
                    Log.w(TAG, "there is no update. maybe user updates note when syncing")
                }
            }
        }

        mDiffDataValues.clear()
        mIsCreate = false
    }

    companion object {
        private val TAG: String = SqlData::class.java.getSimpleName()

        private val INVALID_ID = -99999

        val PROJECTION_DATA: Array<String?> = arrayOf<String>(
            Notes.DataColumns.Companion.ID,
            Notes.DataColumns.Companion.MIME_TYPE,
            Notes.DataColumns.Companion.CONTENT,
            Notes.DataColumns.Companion.DATA1,
            Notes.DataColumns.Companion.DATA3
        )

        const val DATA_ID_COLUMN: Int = 0

        const val DATA_MIME_TYPE_COLUMN: Int = 1

        const val DATA_CONTENT_COLUMN: Int = 2

        const val DATA_CONTENT_DATA_1_COLUMN: Int = 3

        const val DATA_CONTENT_DATA_3_COLUMN: Int = 4
    }
}
