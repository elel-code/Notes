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

import android.content.Context
import android.text.TextUtils
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.micode.notes.data.NoteSaveRequest
import net.micode.notes.data.Notes
import net.micode.notes.data.NotesRepository
import net.micode.notes.tool.ResourceParser.NoteBgResources

class WorkingNote {
    var noteId: Long
        private set

    var content: String? = null
        private set

    private var mode = 0

    var alertDate: Long = 0
        private set

    var modifiedDate: Long = 0
        private set

    private var bgColorIdInternal = 0
    private var widgetIdInternal = 0
    private var widgetTypeInternal = Notes.TYPE_WIDGET_INVALIDE
    private var callDate: Long? = null
    private var phoneNumber: String? = null

    var folderId: Long
        private set

    private val repository: NotesRepository
    private var isDeleted: Boolean
    private var hasLocalChanges = false

    private constructor(context: Context, folderId: Long) {
        repository = NotesRepository(context.applicationContext)
        alertDate = 0
        modifiedDate = System.currentTimeMillis()
        this.folderId = folderId
        noteId = 0
        isDeleted = false
    }

    private constructor(context: Context, noteId: Long, folderId: Long) {
        repository = NotesRepository(context.applicationContext)
        this.noteId = noteId
        this.folderId = folderId
        isDeleted = false
        loadNote()
    }

    private fun loadNote() {
        val storedNote = runBlocking(Dispatchers.IO) {
            repository.loadStoredNote(noteId)
        } ?: run {
            Log.e(TAG, "No note with id: $noteId")
            throw IllegalArgumentException("Unable to find note with id $noteId")
        }

        folderId = storedNote.note.parentId
        bgColorIdInternal = storedNote.note.bgColorId
        widgetIdInternal = storedNote.note.widgetId
        widgetTypeInternal = storedNote.note.widgetType
        alertDate = storedNote.note.alertedDate
        modifiedDate = storedNote.note.modifiedDate

        content = storedNote.textData?.content
        mode = storedNote.textData?.data1?.toInt() ?: 0

        callDate = storedNote.callData?.data1
        phoneNumber = storedNote.callData?.data3
        hasLocalChanges = false
    }

    @Synchronized
    fun saveNote(): Boolean {
        if (!isWorthSaving) {
            return false
        }

        val result = runBlocking(Dispatchers.IO) {
            repository.saveNote(
                NoteSaveRequest(
                    noteId = noteId,
                    folderId = folderId,
                    content = content.orEmpty(),
                    checkListMode = mode,
                    alertDate = alertDate,
                    bgColorId = bgColorIdInternal,
                    widgetId = widgetIdInternal,
                    widgetType = widgetTypeInternal,
                    modifiedDate = modifiedDate,
                    callDate = callDate,
                    phoneNumber = phoneNumber
                )
            )
        } ?: return false

        noteId = result.noteId
        modifiedDate = result.modifiedDate
        hasLocalChanges = false
        return true
    }

    fun existInDatabase(): Boolean = noteId > 0

    private val isWorthSaving: Boolean
        get() {
            return !(isDeleted || (!existInDatabase() && TextUtils.isEmpty(content) &&
                callDate == null && phoneNumber.isNullOrBlank()) ||
                (existInDatabase() && !hasLocalChanges))
        }

    fun setAlertDate(date: Long) {
        if (date != alertDate) {
            alertDate = date
            markDirty()
        }
    }

    fun markDeleted() {
        isDeleted = true
    }

    fun setWorkingText(text: String?) {
        if (!TextUtils.equals(content, text)) {
            content = text
            markDirty()
        }
    }

    fun convertToCallNote(phoneNumber: String?, callDate: Long) {
        this.phoneNumber = phoneNumber
        this.callDate = callDate
        folderId = Notes.ID_CALL_RECORD_FOLDER.toLong()
        markDirty()
    }

    val bgColorResId: Int
        get() = NoteBgResources.getNoteBgResource(bgColorIdInternal)

    var bgColorId: Int
        get() = bgColorIdInternal
        set(id) {
            if (id != bgColorIdInternal) {
                bgColorIdInternal = id
                markDirty()
            }
        }

    val titleBgResId: Int
        get() = NoteBgResources.getNoteTitleBgResource(bgColorIdInternal)

    var checkListMode: Int
        get() = mode
        set(value) {
            if (mode != value) {
                mode = value
                markDirty()
            }
        }

    var widgetId: Int
        get() = widgetIdInternal
        set(id) {
            if (id != widgetIdInternal) {
                widgetIdInternal = id
                markDirty()
            }
        }

    var widgetType: Int
        get() = widgetTypeInternal
        set(type) {
            if (type != widgetTypeInternal) {
                widgetTypeInternal = type
                markDirty()
            }
        }

    private fun markDirty() {
        hasLocalChanges = true
        modifiedDate = System.currentTimeMillis()
    }

    companion object {
        private const val TAG = "WorkingNote"

        fun createEmptyNote(
            context: Context,
            folderId: Long,
            widgetId: Int,
            widgetType: Int,
            defaultBgColorId: Int
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
