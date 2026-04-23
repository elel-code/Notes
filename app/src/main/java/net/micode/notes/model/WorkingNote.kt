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

import android.appwidget.AppWidgetManager
import android.content.ContentUris
import android.content.Context
import android.text.TextUtils
import android.util.Log
import net.micode.notes.data.Notes
import net.micode.notes.data.Notes.CallNote
import net.micode.notes.data.Notes.DataConstants
import net.micode.notes.data.Notes.NoteColumns
import net.micode.notes.data.Notes.TextNote
import net.micode.notes.tool.ResourceParser.NoteBgResources

class WorkingNote {
    // Note for the working note
    private val mNote: Note

    // Note Id
    var noteId: Long
        private set

    // Note content
    var content: String? = null
        private set

    // Note mode
    private var mMode = 0

    var alertDate: Long = 0
        private set

    var modifiedDate: Long = 0
        private set

    private var mBgColorId = 0

    private var mWidgetId = 0

    private var mWidgetType = 0

    var folderId: Long
        private set

    private val mContext: Context

    private var mIsDeleted: Boolean

    private var mNoteSettingStatusListener: NoteSettingChangedListener? = null

    // New note construct
    private constructor(context: Context, folderId: Long) {
        mContext = context
        this.alertDate = 0
        this.modifiedDate = System.currentTimeMillis()
        this.folderId = folderId
        mNote = Note()
        this.noteId = 0
        mIsDeleted = false
        mMode = 0
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE
    }

    // Existing note construct
    private constructor(context: Context, noteId: Long, folderId: Long) {
        mContext = context
        this.noteId = noteId
        this.folderId = folderId
        mIsDeleted = false
        mNote = Note()
        loadNote()
    }

    private fun loadNote() {
        val cursor = mContext.getContentResolver().query(
            ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, this.noteId), NOTE_PROJECTION, null,
            null, null
        )

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                this.folderId = cursor.getLong(NOTE_PARENT_ID_COLUMN)
                mBgColorId = cursor.getInt(NOTE_BG_COLOR_ID_COLUMN)
                mWidgetId = cursor.getInt(NOTE_WIDGET_ID_COLUMN)
                mWidgetType = cursor.getInt(NOTE_WIDGET_TYPE_COLUMN)
                this.alertDate = cursor.getLong(NOTE_ALERTED_DATE_COLUMN)
                this.modifiedDate = cursor.getLong(NOTE_MODIFIED_DATE_COLUMN)
            }
            cursor.close()
        } else {
            Log.e(TAG, "No note with id:" + this.noteId)
            throw IllegalArgumentException("Unable to find note with id " + this.noteId)
        }
        loadNoteData()
    }

    private fun loadNoteData() {
        val cursor = mContext.getContentResolver().query(
            Notes.CONTENT_DATA_URI, DATA_PROJECTION,
            Notes.DataColumns.Companion.NOTE_ID + "=?", arrayOf<String>(
                noteId.toString()
            ), null
        )

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    val type = cursor.getString(DATA_MIME_TYPE_COLUMN)
                    if (DataConstants.NOTE == type) {
                        this.content = cursor.getString(DATA_CONTENT_COLUMN)
                        mMode = cursor.getInt(DATA_MODE_COLUMN)
                        mNote.setTextDataId(cursor.getLong(DATA_ID_COLUMN))
                    } else if (DataConstants.CALL_NOTE == type) {
                        mNote.setCallDataId(cursor.getLong(DATA_ID_COLUMN))
                    } else {
                        Log.d(TAG, "Wrong note type with type:" + type)
                    }
                } while (cursor.moveToNext())
            }
            cursor.close()
        } else {
            Log.e(TAG, "No data with id:" + this.noteId)
            throw IllegalArgumentException("Unable to find note's data with id " + this.noteId)
        }
    }

    @Synchronized
    fun saveNote(): Boolean {
        if (this.isWorthSaving) {
            if (!existInDatabase()) {
                if ((Note.Companion.getNewNoteId(
                        mContext,
                        this.folderId
                    ).also { this.noteId = it }) == 0L
                ) {
                    Log.e(TAG, "Create new note fail with id:" + this.noteId)
                    return false
                }
            }

            mNote.syncNote(mContext, this.noteId)

            /**
             * Update widget content if there exist any widget of this note
             */
            if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && mWidgetType != Notes.TYPE_WIDGET_INVALIDE && mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener!!.onWidgetChanged()
            }
            return true
        } else {
            return false
        }
    }

    fun existInDatabase(): Boolean {
        return this.noteId > 0
    }

    private val isWorthSaving: Boolean
        get() {
            if (mIsDeleted || (!existInDatabase() && TextUtils.isEmpty(this.content))
                || (existInDatabase() && !mNote.isLocalModified())
            ) {
                return false
            } else {
                return true
            }
        }

    fun setOnSettingStatusChangedListener(l: NoteSettingChangedListener?) {
        mNoteSettingStatusListener = l
    }

    fun setAlertDate(date: Long, set: Boolean) {
        if (date != this.alertDate) {
            this.alertDate = date
            mNote.setNoteValue(NoteColumns.Companion.ALERTED_DATE, alertDate.toString())
        }
        if (mNoteSettingStatusListener != null) {
            mNoteSettingStatusListener!!.onClockAlertChanged(date, set)
        }
    }

    fun markDeleted(mark: Boolean) {
        mIsDeleted = mark
        if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && mWidgetType != Notes.TYPE_WIDGET_INVALIDE && mNoteSettingStatusListener != null) {
            mNoteSettingStatusListener!!.onWidgetChanged()
        }
    }

    fun setWorkingText(text: String?) {
        if (!TextUtils.equals(this.content, text)) {
            this.content = text
            mNote.setTextData(
                Notes.DataColumns.Companion.CONTENT,
                this.content
            )
        }
    }

    fun convertToCallNote(phoneNumber: String?, callDate: Long) {
        mNote.setCallData(CallNote.CALL_DATE, callDate.toString())
        mNote.setCallData(CallNote.PHONE_NUMBER, phoneNumber)
        mNote.setNoteValue(NoteColumns.Companion.PARENT_ID, Notes.ID_CALL_RECORD_FOLDER.toString())
    }

    fun hasClockAlert(): Boolean {
        return (if (this.alertDate > 0) true else false)
    }

    val bgColorResId: Int
        get() = NoteBgResources.getNoteBgResource(mBgColorId)

    var bgColorId: Int
        get() = mBgColorId
        set(id) {
            if (id != mBgColorId) {
                mBgColorId = id
                if (mNoteSettingStatusListener != null) {
                    mNoteSettingStatusListener!!.onBackgroundColorChanged()
                }
                mNote.setNoteValue(NoteColumns.Companion.BG_COLOR_ID, id.toString())
            }
        }

    val titleBgResId: Int
        get() = NoteBgResources.getNoteTitleBgResource(mBgColorId)

    var checkListMode: Int
        get() = mMode
        set(mode) {
            if (mMode != mode) {
                if (mNoteSettingStatusListener != null) {
                    mNoteSettingStatusListener!!.onCheckListModeChanged(mMode, mode)
                }
                mMode = mode
                mNote.setTextData(TextNote.MODE, mMode.toString())
            }
        }

    var widgetId: Int
        get() = mWidgetId
        set(id) {
            if (id != mWidgetId) {
                mWidgetId = id
                mNote.setNoteValue(NoteColumns.Companion.WIDGET_ID, mWidgetId.toString())
            }
        }

    var widgetType: Int
        get() = mWidgetType
        set(type) {
            if (type != mWidgetType) {
                mWidgetType = type
                mNote.setNoteValue(NoteColumns.Companion.WIDGET_TYPE, mWidgetType.toString())
            }
        }

    interface NoteSettingChangedListener {
        /**
         * Called when the background color of current note has just changed
         */
        fun onBackgroundColorChanged()

        /**
         * Called when user set clock
         */
        fun onClockAlertChanged(date: Long, set: Boolean)

        /**
         * Call when user create note from widget
         */
        fun onWidgetChanged()

        /**
         * Call when switch between check list mode and normal mode
         * @param oldMode is previous mode before change
         * @param newMode is new mode
         */
        fun onCheckListModeChanged(oldMode: Int, newMode: Int)
    }

    companion object {
        private const val TAG = "WorkingNote"

        val DATA_PROJECTION: Array<String?> = arrayOf<String>(
            Notes.DataColumns.Companion.ID,
            Notes.DataColumns.Companion.CONTENT,
            Notes.DataColumns.Companion.MIME_TYPE,
            Notes.DataColumns.Companion.DATA1,
            Notes.DataColumns.Companion.DATA2,
            Notes.DataColumns.Companion.DATA3,
            Notes.DataColumns.Companion.DATA4,
        )

        val NOTE_PROJECTION: Array<String?> = arrayOf<String>(
            NoteColumns.Companion.PARENT_ID,
            NoteColumns.Companion.ALERTED_DATE,
            NoteColumns.Companion.BG_COLOR_ID,
            NoteColumns.Companion.WIDGET_ID,
            NoteColumns.Companion.WIDGET_TYPE,
            NoteColumns.Companion.MODIFIED_DATE
        )

        private const val DATA_ID_COLUMN = 0

        private const val DATA_CONTENT_COLUMN = 1

        private const val DATA_MIME_TYPE_COLUMN = 2

        private const val DATA_MODE_COLUMN = 3

        private const val NOTE_PARENT_ID_COLUMN = 0

        private const val NOTE_ALERTED_DATE_COLUMN = 1

        private const val NOTE_BG_COLOR_ID_COLUMN = 2

        private const val NOTE_WIDGET_ID_COLUMN = 3

        private const val NOTE_WIDGET_TYPE_COLUMN = 4

        private const val NOTE_MODIFIED_DATE_COLUMN = 5

        fun createEmptyNote(
            context: Context, folderId: Long, widgetId: Int,
            widgetType: Int, defaultBgColorId: Int
        ): WorkingNote {
            val note = WorkingNote(context, folderId)
            note.bgColorId = defaultBgColorId
            note.widgetId = widgetId
            note.widgetType = widgetType
            return note
        }

        fun load(context: Context, id: Long): WorkingNote {
            return WorkingNote(context, id, 0)
        }
    }
}
