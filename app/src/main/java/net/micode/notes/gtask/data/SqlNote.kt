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

import android.appwidget.AppWidgetManager
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.util.Log
import net.micode.notes.data.Notes
import net.micode.notes.data.Notes.NoteColumns
import net.micode.notes.gtask.exception.ActionFailureException
import net.micode.notes.tool.GTaskStringUtils
import net.micode.notes.tool.ResourceParser
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class SqlNote {
    private val mContext: Context

    private val mContentResolver: ContentResolver

    private var mIsCreate: Boolean

    var id: Long = 0
        private set

    private var mAlertDate: Long = 0

    private var mBgColorId = 0

    private var mCreatedDate: Long = 0

    private var mHasAttachment = 0

    private var mModifiedDate: Long = 0

    private var mParentId: Long = 0

    private var mSnippet: String? = null

    private var mType = 0

    private var mWidgetId = 0

    private var mWidgetType = 0

    private var mOriginParent: Long = 0

    private var mVersion: Long = 0

    private val mDiffNoteValues: ContentValues

    private val mDataList: ArrayList<SqlData>

    constructor(context: Context) {
        mContext = context
        mContentResolver = context.getContentResolver()
        mIsCreate = true
        this.id = INVALID_ID.toLong()
        mAlertDate = 0
        mBgColorId = ResourceParser.getDefaultBgId(context)
        mCreatedDate = System.currentTimeMillis()
        mHasAttachment = 0
        mModifiedDate = System.currentTimeMillis()
        mParentId = 0
        mSnippet = ""
        mType = Notes.TYPE_NOTE
        mWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE
        mOriginParent = 0
        mVersion = 0
        mDiffNoteValues = ContentValues()
        mDataList = ArrayList<SqlData>()
    }

    constructor(context: Context, c: Cursor) {
        mContext = context
        mContentResolver = context.getContentResolver()
        mIsCreate = false
        loadFromCursor(c)
        mDataList = ArrayList<SqlData>()
        if (mType == Notes.TYPE_NOTE) loadDataContent()
        mDiffNoteValues = ContentValues()
    }

    constructor(context: Context, id: Long) {
        mContext = context
        mContentResolver = context.getContentResolver()
        mIsCreate = false
        loadFromCursor(id)
        mDataList = ArrayList<SqlData>()
        if (mType == Notes.TYPE_NOTE) loadDataContent()
        mDiffNoteValues = ContentValues()
    }

    private fun loadFromCursor(id: Long) {
        var c: Cursor? = null
        try {
            c = mContentResolver.query(
                Notes.CONTENT_NOTE_URI, PROJECTION_NOTE, "(_id=?)",
                arrayOf<String>(
                    id.toString()
                ), null
            )
            if (c != null) {
                c.moveToNext()
                loadFromCursor(c)
            } else {
                Log.w(TAG, "loadFromCursor: cursor = null")
            }
        } finally {
            if (c != null) c.close()
        }
    }

    private fun loadFromCursor(c: Cursor) {
        this.id = c.getLong(ID_COLUMN)
        mAlertDate = c.getLong(ALERTED_DATE_COLUMN)
        mBgColorId = c.getInt(BG_COLOR_ID_COLUMN)
        mCreatedDate = c.getLong(CREATED_DATE_COLUMN)
        mHasAttachment = c.getInt(HAS_ATTACHMENT_COLUMN)
        mModifiedDate = c.getLong(MODIFIED_DATE_COLUMN)
        mParentId = c.getLong(PARENT_ID_COLUMN)
        mSnippet = c.getString(SNIPPET_COLUMN)
        mType = c.getInt(TYPE_COLUMN)
        mWidgetId = c.getInt(WIDGET_ID_COLUMN)
        mWidgetType = c.getInt(WIDGET_TYPE_COLUMN)
        mVersion = c.getLong(VERSION_COLUMN)
    }

    private fun loadDataContent() {
        var c: Cursor? = null
        mDataList.clear()
        try {
            c = mContentResolver.query(
                Notes.CONTENT_DATA_URI, SqlData.Companion.PROJECTION_DATA,
                "(note_id=?)", arrayOf<String>(
                    id.toString()
                ), null
            )
            if (c != null) {
                if (c.getCount() == 0) {
                    Log.w(TAG, "it seems that the note has not data")
                    return
                }
                while (c.moveToNext()) {
                    val data = SqlData(mContext, c)
                    mDataList.add(data)
                }
            } else {
                Log.w(TAG, "loadDataContent: cursor = null")
            }
        } finally {
            if (c != null) c.close()
        }
    }

    fun setContent(js: JSONObject): Boolean {
        try {
            val note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE)
            if (note.getInt(NoteColumns.Companion.TYPE) == Notes.TYPE_SYSTEM) {
                Log.w(TAG, "cannot set system folder")
            } else if (note.getInt(NoteColumns.Companion.TYPE) == Notes.TYPE_FOLDER) {
                // for folder we can only update the snnipet and type
                val snippet = if (note.has(NoteColumns.Companion.SNIPPET)) note
                    .getString(NoteColumns.Companion.SNIPPET) else ""
                if (mIsCreate || mSnippet != snippet) {
                    mDiffNoteValues.put(NoteColumns.Companion.SNIPPET, snippet)
                }
                mSnippet = snippet

                val type = if (note.has(NoteColumns.Companion.TYPE))
                    note.getInt(NoteColumns.Companion.TYPE)
                else
                    Notes.TYPE_NOTE
                if (mIsCreate || mType != type) {
                    mDiffNoteValues.put(NoteColumns.Companion.TYPE, type)
                }
                mType = type
            } else if (note.getInt(NoteColumns.Companion.TYPE) == Notes.TYPE_NOTE) {
                val dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA)
                val id =
                    if (note.has(NoteColumns.Companion.ID)) note.getLong(NoteColumns.Companion.ID) else INVALID_ID.toLong()
                if (mIsCreate || this.id != id) {
                    mDiffNoteValues.put(NoteColumns.Companion.ID, id)
                }
                this.id = id

                val alertDate = if (note.has(NoteColumns.Companion.ALERTED_DATE)) note
                    .getLong(NoteColumns.Companion.ALERTED_DATE) else 0
                if (mIsCreate || mAlertDate != alertDate) {
                    mDiffNoteValues.put(NoteColumns.Companion.ALERTED_DATE, alertDate)
                }
                mAlertDate = alertDate

                val bgColorId = if (note.has(NoteColumns.Companion.BG_COLOR_ID)) note
                    .getInt(NoteColumns.Companion.BG_COLOR_ID) else ResourceParser.getDefaultBgId(
                    mContext
                )
                if (mIsCreate || mBgColorId != bgColorId) {
                    mDiffNoteValues.put(NoteColumns.Companion.BG_COLOR_ID, bgColorId)
                }
                mBgColorId = bgColorId

                val createDate = if (note.has(NoteColumns.Companion.CREATED_DATE)) note
                    .getLong(NoteColumns.Companion.CREATED_DATE) else System.currentTimeMillis()
                if (mIsCreate || mCreatedDate != createDate) {
                    mDiffNoteValues.put(NoteColumns.Companion.CREATED_DATE, createDate)
                }
                mCreatedDate = createDate

                val hasAttachment = if (note.has(NoteColumns.Companion.HAS_ATTACHMENT)) note
                    .getInt(NoteColumns.Companion.HAS_ATTACHMENT) else 0
                if (mIsCreate || mHasAttachment != hasAttachment) {
                    mDiffNoteValues.put(NoteColumns.Companion.HAS_ATTACHMENT, hasAttachment)
                }
                mHasAttachment = hasAttachment

                val modifiedDate = if (note.has(NoteColumns.Companion.MODIFIED_DATE)) note
                    .getLong(NoteColumns.Companion.MODIFIED_DATE) else System.currentTimeMillis()
                if (mIsCreate || mModifiedDate != modifiedDate) {
                    mDiffNoteValues.put(NoteColumns.Companion.MODIFIED_DATE, modifiedDate)
                }
                mModifiedDate = modifiedDate

                val parentId = if (note.has(NoteColumns.Companion.PARENT_ID)) note
                    .getLong(NoteColumns.Companion.PARENT_ID) else 0
                if (mIsCreate || mParentId != parentId) {
                    mDiffNoteValues.put(NoteColumns.Companion.PARENT_ID, parentId)
                }
                mParentId = parentId

                val snippet = if (note.has(NoteColumns.Companion.SNIPPET)) note
                    .getString(NoteColumns.Companion.SNIPPET) else ""
                if (mIsCreate || mSnippet != snippet) {
                    mDiffNoteValues.put(NoteColumns.Companion.SNIPPET, snippet)
                }
                mSnippet = snippet

                val type = if (note.has(NoteColumns.Companion.TYPE))
                    note.getInt(NoteColumns.Companion.TYPE)
                else
                    Notes.TYPE_NOTE
                if (mIsCreate || mType != type) {
                    mDiffNoteValues.put(NoteColumns.Companion.TYPE, type)
                }
                mType = type

                val widgetId = if (note.has(NoteColumns.Companion.WIDGET_ID))
                    note.getInt(NoteColumns.Companion.WIDGET_ID)
                else
                    AppWidgetManager.INVALID_APPWIDGET_ID
                if (mIsCreate || mWidgetId != widgetId) {
                    mDiffNoteValues.put(NoteColumns.Companion.WIDGET_ID, widgetId)
                }
                mWidgetId = widgetId

                val widgetType = if (note.has(NoteColumns.Companion.WIDGET_TYPE)) note
                    .getInt(NoteColumns.Companion.WIDGET_TYPE) else Notes.TYPE_WIDGET_INVALIDE
                if (mIsCreate || mWidgetType != widgetType) {
                    mDiffNoteValues.put(NoteColumns.Companion.WIDGET_TYPE, widgetType)
                }
                mWidgetType = widgetType

                val originParent = if (note.has(NoteColumns.Companion.ORIGIN_PARENT_ID)) note
                    .getLong(NoteColumns.Companion.ORIGIN_PARENT_ID) else 0
                if (mIsCreate || mOriginParent != originParent) {
                    mDiffNoteValues.put(NoteColumns.Companion.ORIGIN_PARENT_ID, originParent)
                }
                mOriginParent = originParent

                for (i in 0..<dataArray.length()) {
                    val data = dataArray.getJSONObject(i)
                    var sqlData: SqlData? = null
                    if (data.has(Notes.DataColumns.Companion.ID)) {
                        val dataId = data.getLong(Notes.DataColumns.Companion.ID)
                        for (temp in mDataList) {
                            if (dataId == temp.getId()) {
                                sqlData = temp
                            }
                        }
                    }

                    if (sqlData == null) {
                        sqlData = SqlData(mContext)
                        mDataList.add(sqlData)
                    }

                    sqlData.setContent(data)
                }
            }
        } catch (e: JSONException) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
            return false
        }
        return true
    }

    val content: JSONObject?
        get() {
            try {
                val js = JSONObject()

                if (mIsCreate) {
                    Log.e(
                        TAG,
                        "it seems that we haven't created this in database yet"
                    )
                    return null
                }

                val note = JSONObject()
                if (mType == Notes.TYPE_NOTE) {
                    note.put(NoteColumns.Companion.ID, this.id)
                    note.put(NoteColumns.Companion.ALERTED_DATE, mAlertDate)
                    note.put(NoteColumns.Companion.BG_COLOR_ID, mBgColorId)
                    note.put(NoteColumns.Companion.CREATED_DATE, mCreatedDate)
                    note.put(NoteColumns.Companion.HAS_ATTACHMENT, mHasAttachment)
                    note.put(NoteColumns.Companion.MODIFIED_DATE, mModifiedDate)
                    note.put(NoteColumns.Companion.PARENT_ID, mParentId)
                    note.put(NoteColumns.Companion.SNIPPET, mSnippet)
                    note.put(NoteColumns.Companion.TYPE, mType)
                    note.put(NoteColumns.Companion.WIDGET_ID, mWidgetId)
                    note.put(NoteColumns.Companion.WIDGET_TYPE, mWidgetType)
                    note.put(NoteColumns.Companion.ORIGIN_PARENT_ID, mOriginParent)
                    js.put(GTaskStringUtils.META_HEAD_NOTE, note)

                    val dataArray = JSONArray()
                    for (sqlData in mDataList) {
                        val data = sqlData.getContent()
                        if (data != null) {
                            dataArray.put(data)
                        }
                    }
                    js.put(GTaskStringUtils.META_HEAD_DATA, dataArray)
                } else if (mType == Notes.TYPE_FOLDER || mType == Notes.TYPE_SYSTEM) {
                    note.put(NoteColumns.Companion.ID, this.id)
                    note.put(NoteColumns.Companion.TYPE, mType)
                    note.put(NoteColumns.Companion.SNIPPET, mSnippet)
                    js.put(GTaskStringUtils.META_HEAD_NOTE, note)
                }

                return js
            } catch (e: JSONException) {
                Log.e(TAG, e.toString())
                e.printStackTrace()
            }
            return null
        }

    fun setGtaskId(gid: String?) {
        mDiffNoteValues.put(NoteColumns.Companion.GTASK_ID, gid)
    }

    fun setSyncId(syncId: Long) {
        mDiffNoteValues.put(NoteColumns.Companion.SYNC_ID, syncId)
    }

    fun resetLocalModified() {
        mDiffNoteValues.put(NoteColumns.Companion.LOCAL_MODIFIED, 0)
    }

    var parentId: Long
        get() = mParentId
        set(id) {
            mParentId = id
            mDiffNoteValues.put(NoteColumns.Companion.PARENT_ID, id)
        }

    val snippet: String
        get() = mSnippet!!

    val isNoteType: Boolean
        get() = mType == Notes.TYPE_NOTE

    fun commit(validateVersion: Boolean) {
        if (mIsCreate) {
            if (this.id == INVALID_ID.toLong() && mDiffNoteValues.containsKey(NoteColumns.Companion.ID)) {
                mDiffNoteValues.remove(NoteColumns.Companion.ID)
            }

            val uri = mContentResolver.insert(Notes.CONTENT_NOTE_URI, mDiffNoteValues)
            try {
                this.id = uri!!.getPathSegments().get(1).toLong()
            } catch (e: NumberFormatException) {
                Log.e(TAG, "Get note id error :" + e.toString())
                throw ActionFailureException("create note failed")
            }
            check(this.id != 0L) { "Create thread id failed" }

            if (mType == Notes.TYPE_NOTE) {
                for (sqlData in mDataList) {
                    sqlData.commit(this.id, false, -1)
                }
            }
        } else {
            if (this.id <= 0 && this.id != Notes.ID_ROOT_FOLDER.toLong() && this.id != Notes.ID_CALL_RECORD_FOLDER.toLong()) {
                Log.e(TAG, "No such note")
                throw IllegalStateException("Try to update note with invalid id")
            }
            if (mDiffNoteValues.size() > 0) {
                mVersion++
                var result = 0
                if (!validateVersion) {
                    result = mContentResolver.update(
                        Notes.CONTENT_NOTE_URI, mDiffNoteValues, ("("
                                + NoteColumns.Companion.ID + "=?)"), arrayOf<String>(
                            id.toString()
                        )
                    )
                } else {
                    result = mContentResolver.update(
                        Notes.CONTENT_NOTE_URI, mDiffNoteValues, ("("
                                + NoteColumns.Companion.ID + "=?) AND (" + NoteColumns.Companion.VERSION + "<=?)"),
                        arrayOf<String>(
                            id.toString(), mVersion.toString()
                        )
                    )
                }
                if (result == 0) {
                    Log.w(TAG, "there is no update. maybe user updates note when syncing")
                }
            }

            if (mType == Notes.TYPE_NOTE) {
                for (sqlData in mDataList) {
                    sqlData.commit(this.id, validateVersion, mVersion)
                }
            }
        }

        // refresh local info
        loadFromCursor(this.id)
        if (mType == Notes.TYPE_NOTE) loadDataContent()

        mDiffNoteValues.clear()
        mIsCreate = false
    }

    companion object {
        private val TAG: String = SqlNote::class.java.getSimpleName()

        private val INVALID_ID = -99999

        val PROJECTION_NOTE: Array<String?> = arrayOf<String>(
            NoteColumns.Companion.ID,
            NoteColumns.Companion.ALERTED_DATE,
            NoteColumns.Companion.BG_COLOR_ID,
            NoteColumns.Companion.CREATED_DATE,
            NoteColumns.Companion.HAS_ATTACHMENT,
            NoteColumns.Companion.MODIFIED_DATE,
            NoteColumns.Companion.NOTES_COUNT,
            NoteColumns.Companion.PARENT_ID,
            NoteColumns.Companion.SNIPPET,
            NoteColumns.Companion.TYPE,
            NoteColumns.Companion.WIDGET_ID,
            NoteColumns.Companion.WIDGET_TYPE,
            NoteColumns.Companion.SYNC_ID,
            NoteColumns.Companion.LOCAL_MODIFIED,
            NoteColumns.Companion.ORIGIN_PARENT_ID,
            NoteColumns.Companion.GTASK_ID,
            NoteColumns.Companion.VERSION
        )

        const val ID_COLUMN: Int = 0

        const val ALERTED_DATE_COLUMN: Int = 1

        const val BG_COLOR_ID_COLUMN: Int = 2

        const val CREATED_DATE_COLUMN: Int = 3

        const val HAS_ATTACHMENT_COLUMN: Int = 4

        const val MODIFIED_DATE_COLUMN: Int = 5

        const val NOTES_COUNT_COLUMN: Int = 6

        const val PARENT_ID_COLUMN: Int = 7

        const val SNIPPET_COLUMN: Int = 8

        const val TYPE_COLUMN: Int = 9

        const val WIDGET_ID_COLUMN: Int = 10

        const val WIDGET_TYPE_COLUMN: Int = 11

        const val SYNC_ID_COLUMN: Int = 12

        const val LOCAL_MODIFIED_COLUMN: Int = 13

        const val ORIGIN_PARENT_ID_COLUMN: Int = 14

        const val GTASK_ID_COLUMN: Int = 15

        const val VERSION_COLUMN: Int = 16
    }
}
