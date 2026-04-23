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
package net.micode.notes.ui

import android.content.Context
import android.database.Cursor
import android.text.TextUtils
import net.micode.notes.data.Contact
import net.micode.notes.data.Notes
import net.micode.notes.data.Notes.NoteColumns
import net.micode.notes.tool.DataUtils

class NoteItemData(context: Context, cursor: Cursor) {
    var id: Long
        private set
    val alertDate: Long
    val bgColorId: Int
    val createdDate: Long
    private val mHasAttachment: Boolean
    val modifiedDate: Long
    val notesCount: Int
    var folderId: Long
        private set
    set
    var snippet: String
        private set
    val type: Int
    val widgetId: Int
    val widgetType: Int
    var callName: String? = null
        private set
    private var mPhoneNumber: String?

    var isLast: Boolean = false
        private set
    var isFirst: Boolean = false
        private set
    var isSingle: Boolean = false
        private set
    var isOneFollowingFolder: Boolean = false
        private set
    var isMultiFollowingFolder: Boolean = false
        private set

    init {
        this.id = cursor.getLong(ID_COLUMN)
        this.alertDate = cursor.getLong(ALERTED_DATE_COLUMN)
        this.bgColorId = cursor.getInt(BG_COLOR_ID_COLUMN)
        this.createdDate = cursor.getLong(CREATED_DATE_COLUMN)
        mHasAttachment = if (cursor.getInt(HAS_ATTACHMENT_COLUMN) > 0) true else false
        this.modifiedDate = cursor.getLong(MODIFIED_DATE_COLUMN)
        this.notesCount = cursor.getInt(NOTES_COUNT_COLUMN)
        this.folderId = cursor.getLong(PARENT_ID_COLUMN)
        this.snippet = cursor.getString(SNIPPET_COLUMN)
        this.snippet = snippet.replace(NoteEditActivity.Companion.TAG_CHECKED, "").replace(
            NoteEditActivity.Companion.TAG_UNCHECKED, ""
        )
        this.type = cursor.getInt(TYPE_COLUMN)
        this.widgetId = cursor.getInt(WIDGET_ID_COLUMN)
        this.widgetType = cursor.getInt(WIDGET_TYPE_COLUMN)

        mPhoneNumber = ""
        if (this.folderId == Notes.ID_CALL_RECORD_FOLDER.toLong()) {
            mPhoneNumber = DataUtils.getCallNumberByNoteId(context.getContentResolver(), this.id)
            if (!TextUtils.isEmpty(mPhoneNumber)) {
                this.callName = Contact.getContact(context, mPhoneNumber)
                if (this.callName == null) {
                    this.callName = mPhoneNumber
                }
            }
        }

        if (this.callName == null) {
            this.callName = ""
        }
        checkPostion(cursor)
    }

    private fun checkPostion(cursor: Cursor) {
        this.isLast = if (cursor.isLast()) true else false
        this.isFirst = if (cursor.isFirst()) true else false
        this.isSingle = (cursor.getCount() == 1)
        this.isMultiFollowingFolder = false
        this.isOneFollowingFolder = false

        if (this.type == Notes.TYPE_NOTE && !this.isFirst) {
            val position = cursor.getPosition()
            if (cursor.moveToPrevious()) {
                if (cursor.getInt(TYPE_COLUMN) == Notes.TYPE_FOLDER
                    || cursor.getInt(TYPE_COLUMN) == Notes.TYPE_SYSTEM
                ) {
                    if (cursor.getCount() > (position + 1)) {
                        this.isMultiFollowingFolder = true
                    } else {
                        this.isOneFollowingFolder = true
                    }
                }
                check(cursor.moveToNext()) { "cursor move to previous but can't move back" }
            }
        }
    }

    fun hasAttachment(): Boolean {
        return mHasAttachment
    }

    fun hasAlert(): Boolean {
        return (this.alertDate > 0)
    }

    val isCallRecord: Boolean
        get() = (this.folderId == Notes.ID_CALL_RECORD_FOLDER.toLong() && !TextUtils.isEmpty(
            mPhoneNumber
        ))

    companion object {
        val PROJECTION: Array<String?> = arrayOf<String>(
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
        )

        private const val ID_COLUMN = 0
        private const val ALERTED_DATE_COLUMN = 1
        private const val BG_COLOR_ID_COLUMN = 2
        private const val CREATED_DATE_COLUMN = 3
        private const val HAS_ATTACHMENT_COLUMN = 4
        private const val MODIFIED_DATE_COLUMN = 5
        private const val NOTES_COUNT_COLUMN = 6
        private const val PARENT_ID_COLUMN = 7
        private const val SNIPPET_COLUMN = 8
        private const val TYPE_COLUMN = 9
        private const val WIDGET_ID_COLUMN = 10
        private const val WIDGET_TYPE_COLUMN = 11

        fun getNoteType(cursor: Cursor): Int {
            return cursor.getInt(TYPE_COLUMN)
        }
    }
}
