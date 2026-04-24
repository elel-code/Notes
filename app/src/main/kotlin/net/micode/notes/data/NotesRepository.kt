package net.micode.notes.data

import android.content.Context
import android.telephony.PhoneNumberUtils
import androidx.room.withTransaction
import net.micode.notes.data.room.AlarmRow
import net.micode.notes.data.room.CallNoteLookupRow
import net.micode.notes.data.room.NoteListRow
import net.micode.notes.data.room.NotesRoomDatabase
import net.micode.notes.data.room.RoomDataEntity
import net.micode.notes.data.room.RoomNoteEntity
import net.micode.notes.data.room.StoredNotePayload
import net.micode.notes.data.room.WidgetNoteRow
import net.micode.notes.tool.NotesSortMode
import net.micode.notes.ui.AppWidgetAttribute

data class NoteSaveRequest(
    val noteId: Long,
    val folderId: Long,
    val content: String,
    val checkListMode: Int,
    val alertDate: Long,
    val bgColorId: Int,
    val widgetId: Int,
    val widgetType: Int,
    val modifiedDate: Long,
    val callDate: Long?,
    val phoneNumber: String?
)

data class NoteSaveResult(
    val noteId: Long,
    val modifiedDate: Long
)

class NotesRepository private constructor(context: Context) {
    private val database = NotesRoomDatabase(context.applicationContext)
    private val notesDao = database.notesDao()

    suspend fun loadListItems(
        folderId: Long,
        searchText: String,
        sortMode: NotesSortMode
    ): List<NoteListRow> {
        val trimmedSearchText = searchText.trim()
        val rows = if (folderId == Notes.ID_ROOT_FOLDER.toLong()) {
            if (trimmedSearchText.isEmpty()) {
                notesDao.getRootListItems(
                    folderId = folderId,
                    systemType = Notes.TYPE_SYSTEM,
                    noteType = Notes.TYPE_NOTE,
                    callRecordFolderId = Notes.ID_CALL_RECORD_FOLDER.toLong()
                )
            } else {
                notesDao.searchRootListItems(
                    folderId = folderId,
                    systemType = Notes.TYPE_SYSTEM,
                    noteType = Notes.TYPE_NOTE,
                    callRecordFolderId = Notes.ID_CALL_RECORD_FOLDER.toLong(),
                    searchText = "%$trimmedSearchText%"
                )
            }
        } else {
            if (trimmedSearchText.isEmpty()) {
                notesDao.getFolderListItems(folderId, Notes.TYPE_NOTE)
            } else {
                notesDao.searchFolderListItems(
                    folderId = folderId,
                    noteType = Notes.TYPE_NOTE,
                    searchText = "%$trimmedSearchText%"
                )
            }
        }
        return rows.sortedWith(noteListComparator(sortMode))
    }

    suspend fun hasVisibleFolderNamed(name: String): Boolean {
        return notesDao.hasVisibleFolderNamed(
            name = name,
            folderType = Notes.TYPE_FOLDER,
            trashFolderId = Notes.ID_TRASH_FOLER.toLong()
        )
    }

    suspend fun createFolder(name: String): Long {
        val now = System.currentTimeMillis()
        return notesDao.insertNote(
            RoomNoteEntity(
                parentId = Notes.ID_ROOT_FOLDER.toLong(),
                createdDate = now,
                modifiedDate = now,
                snippet = name,
                type = Notes.TYPE_FOLDER
            )
        )
    }

    suspend fun renameFolder(folderId: Long, name: String): Boolean {
        return notesDao.renameFolder(
            folderId = folderId,
            name = name,
            folderType = Notes.TYPE_FOLDER
        ) > 0
    }

    suspend fun collectFolderWidgets(folderId: Long): Set<AppWidgetAttribute> {
        return notesDao.getFolderWidgets(folderId)
            .mapTo(linkedSetOf()) { widget ->
                AppWidgetAttribute(
                    widgetId = widget.widgetId,
                    widgetType = widget.widgetType
                )
            }
    }

    suspend fun deleteNotes(noteIds: Set<Long>): Boolean {
        val validNoteIds = noteIds
            .filter { it != Notes.ID_ROOT_FOLDER.toLong() }
            .distinct()
        if (validNoteIds.isEmpty()) {
            return true
        }

        return database.withTransaction {
            val allNoteIds = notesDao.getDescendantNoteIds(validNoteIds)
            if (allNoteIds.isEmpty()) {
                return@withTransaction false
            }

            notesDao.deleteDataByNoteIds(allNoteIds)
            notesDao.deleteNotesByIds(allNoteIds) > 0
        }
    }

    suspend fun deleteFolder(folderId: Long): Boolean {
        if (folderId == Notes.ID_ROOT_FOLDER.toLong()) {
            return false
        }

        return deleteNotes(setOf(folderId))
    }

    suspend fun isVisibleNote(noteId: Long, type: Int): Boolean {
        return notesDao.isVisibleNote(noteId, type, Notes.ID_TRASH_FOLER.toLong())
    }

    suspend fun loadStoredNote(noteId: Long): StoredNotePayload? {
        val note = notesDao.getNoteById(noteId) ?: return null
        val dataRows = notesDao.getDataByNoteId(noteId)
        return StoredNotePayload(
            note = note,
            textData = dataRows.firstOrNull { it.mimeType == Notes.TextNote.CONTENT_ITEM_TYPE },
            callData = dataRows.firstOrNull { it.mimeType == Notes.CallNote.CONTENT_ITEM_TYPE }
        )
    }

    suspend fun saveNote(request: NoteSaveRequest): NoteSaveResult? {
        return database.withTransaction {
            val now = request.modifiedDate.takeIf { it > 0L } ?: System.currentTimeMillis()
            val existingNote = if (request.noteId > 0L) {
                notesDao.getNoteById(request.noteId)
            } else {
                null
            }
            if (request.noteId > 0L && existingNote == null) {
                return@withTransaction null
            }

            val noteId = existingNote?.id ?: notesDao.insertNote(
                RoomNoteEntity(
                    parentId = request.folderId,
                    alertedDate = request.alertDate,
                    bgColorId = request.bgColorId,
                    createdDate = now,
                    modifiedDate = now,
                    snippet = request.content,
                    type = Notes.TYPE_NOTE,
                    widgetId = request.widgetId,
                    widgetType = request.widgetType,
                    localModified = 1
                )
            )

            if (noteId <= 0L) {
                return@withTransaction null
            }

            if (existingNote != null) {
                notesDao.updateNote(
                    existingNote.copy(
                        parentId = request.folderId,
                        alertedDate = request.alertDate,
                        bgColorId = request.bgColorId,
                        modifiedDate = now,
                        snippet = request.content,
                        type = Notes.TYPE_NOTE,
                        widgetId = request.widgetId,
                        widgetType = request.widgetType,
                        localModified = 1
                    )
                )
            }

            upsertTextData(noteId, request, now)
            upsertCallData(noteId, request, now)

            NoteSaveResult(
                noteId = noteId,
                modifiedDate = now
            )
        }
    }

    suspend fun getNoteIdByPhoneNumberAndCallDate(phoneNumber: String, callDate: Long): Long {
        val candidates = notesDao.findCallNotesByDate(
            callDate = callDate,
            mimeType = Notes.CallNote.CONTENT_ITEM_TYPE
        )
        return candidates.firstOrNull { candidate ->
            areSamePhoneNumber(phoneNumber, candidate)
        }?.noteId ?: 0L
    }

    suspend fun getSnippetById(noteId: Long): String? {
        return notesDao.getSnippetById(noteId)
    }

    suspend fun getFutureAlertNotes(currentDate: Long): List<AlarmRow> {
        return notesDao.getFutureAlertNotes(currentDate, Notes.TYPE_NOTE)
    }

    suspend fun getNotesByWidgetId(appWidgetId: Int): List<WidgetNoteRow> {
        return notesDao.getNotesByWidgetId(
            appWidgetId = appWidgetId,
            trashFolderId = Notes.ID_TRASH_FOLER.toLong()
        )
    }

    suspend fun clearWidgetBinding(appWidgetId: Int, invalidWidgetId: Int): Boolean {
        return notesDao.clearWidgetBinding(appWidgetId, invalidWidgetId) > 0
    }

    private suspend fun upsertTextData(
        noteId: Long,
        request: NoteSaveRequest,
        now: Long
    ) {
        val existingTextData = notesDao.getDataByNoteIdAndMimeType(noteId, Notes.TextNote.CONTENT_ITEM_TYPE)

        val content = request.content
        val modeValue = request.checkListMode.toLong()
        if (existingTextData == null) {
            notesDao.insertData(
                RoomDataEntity(
                    mimeType = Notes.TextNote.CONTENT_ITEM_TYPE,
                    noteId = noteId,
                    createdDate = now,
                    modifiedDate = now,
                    content = content,
                    data1 = modeValue
                )
            )
        } else {
            notesDao.updateData(
                existingTextData.copy(
                    noteId = noteId,
                    modifiedDate = now,
                    content = content,
                    data1 = modeValue
                )
            )
        }
    }

    private suspend fun upsertCallData(
        noteId: Long,
        request: NoteSaveRequest,
        now: Long
    ) {
        val existingCallData = notesDao.getDataByNoteIdAndMimeType(noteId, Notes.CallNote.CONTENT_ITEM_TYPE)
        if (existingCallData == null && request.phoneNumber.isNullOrBlank() && request.callDate == null) {
            return
        }

        if (existingCallData == null) {
            notesDao.insertData(
                RoomDataEntity(
                    mimeType = Notes.CallNote.CONTENT_ITEM_TYPE,
                    noteId = noteId,
                    createdDate = now,
                    modifiedDate = now,
                    data1 = request.callDate,
                    data3 = request.phoneNumber.orEmpty()
                )
            )
        } else {
            notesDao.updateData(
                existingCallData.copy(
                    noteId = noteId,
                    modifiedDate = now,
                    data1 = request.callDate,
                    data3 = request.phoneNumber.orEmpty()
                )
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun areSamePhoneNumber(phoneNumber: String, candidate: CallNoteLookupRow): Boolean {
        return PhoneNumberUtils.compare(phoneNumber, candidate.phoneNumber)
    }

    companion object {
        private fun noteListComparator(sortMode: NotesSortMode): Comparator<NoteListRow> {
            val groupComparator = compareBy<NoteListRow> {
                when (it.type) {
                    Notes.TYPE_SYSTEM -> 0
                    Notes.TYPE_FOLDER -> 1
                    else -> 2
                }
            }
            val titleSelector: (NoteListRow) -> String = { row ->
                row.snippet.trim().lowercase()
            }

            return when (sortMode) {
                NotesSortMode.MODIFIED_DESC -> groupComparator
                    .thenByDescending { it.modifiedDate }
                    .thenBy { titleSelector(it) }

                NotesSortMode.MODIFIED_ASC -> groupComparator
                    .thenBy { it.modifiedDate }
                    .thenBy { titleSelector(it) }

                NotesSortMode.TITLE_ASC -> groupComparator
                    .thenBy { titleSelector(it) }
                    .thenByDescending { it.modifiedDate }
            }
        }

        @Volatile
        private var instance: NotesRepository? = null

        operator fun invoke(context: Context): NotesRepository {
            return instance ?: synchronized(this) {
                instance ?: NotesRepository(context.applicationContext).also { repository ->
                    instance = repository
                }
            }
        }
    }
}
