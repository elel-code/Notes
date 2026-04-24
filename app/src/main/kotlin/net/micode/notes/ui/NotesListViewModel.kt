package net.micode.notes.ui

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.micode.notes.data.Notes
import net.micode.notes.data.Notes.NoteColumns
import net.micode.notes.tool.DataUtils

data class NotesListItemUi(
    val id: Long,
    val title: String,
    val type: Int,
    val folderId: Long,
    val modifiedDate: Long,
    val notesCount: Int,
    val bgColorId: Int,
    val hasAttachment: Boolean,
    val hasAlert: Boolean,
    val widget: AppWidgetAttribute
) {
    val isFolder: Boolean
        get() = type == Notes.TYPE_FOLDER || type == Notes.TYPE_SYSTEM
}

data class NotesListUiState(
    val currentFolderId: Long = Notes.ID_ROOT_FOLDER.toLong(),
    val currentFolderTitle: String = "",
    val searchText: String = "",
    val items: List<NotesListItemUi> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val isLoading: Boolean = false
) {
    val isRoot: Boolean
        get() = currentFolderId == Notes.ID_ROOT_FOLDER.toLong()

    val isCallRecordFolder: Boolean
        get() = currentFolderId == Notes.ID_CALL_RECORD_FOLDER.toLong()
}

class NotesListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(NotesListUiState())
    val uiState = _uiState.asStateFlow()

    private var queryGeneration = 0L

    fun refresh(context: Context) {
        val generation = ++queryGeneration
        val currentState = _uiState.value
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val items = loadItems(
                context = context.applicationContext,
                folderId = currentState.currentFolderId,
                searchText = currentState.searchText
            )

            withContext(Dispatchers.Main) {
                if (generation != queryGeneration) {
                    return@withContext
                }

                val validSelectedIds = currentState.selectedIds.intersect(items.mapTo(hashSetOf()) { it.id })
                _uiState.update {
                    it.copy(
                        items = items,
                        selectedIds = validSelectedIds,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun setSearchText(context: Context, searchText: String) {
        _uiState.update {
            it.copy(
                searchText = searchText,
                selectedIds = emptySet()
            )
        }
        refresh(context)
    }

    fun openFolder(context: Context, item: NotesListItemUi) {
        _uiState.update {
            it.copy(
                currentFolderId = item.id,
                currentFolderTitle = item.title,
                selectedIds = emptySet()
            )
        }
        refresh(context)
    }

    fun goToRoot(context: Context) {
        _uiState.update {
            it.copy(
                currentFolderId = Notes.ID_ROOT_FOLDER.toLong(),
                currentFolderTitle = "",
                selectedIds = emptySet()
            )
        }
        refresh(context)
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun toggleSelection(itemId: Long) {
        _uiState.update { state ->
            val selectedIds = state.selectedIds.toMutableSet()
            if (!selectedIds.add(itemId)) {
                selectedIds.remove(itemId)
            }
            state.copy(selectedIds = selectedIds)
        }
    }

    fun deleteSelectedNotes(
        contentResolver: ContentResolver,
        isSyncMode: Boolean,
        onComplete: (Set<AppWidgetAttribute>) -> Unit
    ) {
        val selectedIds = _uiState.value.selectedIds
        if (selectedIds.isEmpty()) {
            onComplete(emptySet())
            return
        }

        val selectedWidgets = _uiState.value.items
            .filter { it.id in selectedIds }
            .mapTo(linkedSetOf()) { it.widget }

        viewModelScope.launch(Dispatchers.IO) {
            val noteIds = selectedIds.mapTo(hashSetOf<Long?>()) { it }
            val success = if (isSyncMode) {
                DataUtils.batchMoveToFolder(
                    contentResolver,
                    noteIds,
                    Notes.ID_TRASH_FOLER.toLong()
                )
            } else {
                DataUtils.batchDeleteNotes(contentResolver, noteIds)
            }

            if (!success) {
                Log.e(TAG, "Failed to delete selected notes")
            }

            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(selectedIds = emptySet()) }
                onComplete(selectedWidgets)
            }
        }
    }

    fun deleteFolder(
        contentResolver: ContentResolver,
        folderId: Long,
        isSyncMode: Boolean,
        onComplete: (Set<AppWidgetAttribute>) -> Unit
    ) {
        if (folderId == Notes.ID_ROOT_FOLDER.toLong()) {
            Log.e(TAG, "Wrong folder id, should not happen: $folderId")
            onComplete(emptySet())
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val ids = hashSetOf<Long?>(folderId)
            val widgets = DataUtils.getFolderNoteWidget(contentResolver, folderId)
                .orEmpty()
                .filterNotNull()
                .toSet()

            val success = if (isSyncMode) {
                DataUtils.batchMoveToFolder(contentResolver, ids, Notes.ID_TRASH_FOLER.toLong())
            } else {
                DataUtils.batchDeleteNotes(contentResolver, ids)
            }

            if (!success) {
                Log.e(TAG, "Failed to delete folder $folderId")
            }

            withContext(Dispatchers.Main) {
                onComplete(widgets)
            }
        }
    }

    private fun loadItems(
        context: Context,
        folderId: Long,
        searchText: String
    ): List<NotesListItemUi> {
        val trimmedSearchText = searchText.trim()
        var selection = if (folderId == Notes.ID_ROOT_FOLDER.toLong()) {
            ROOT_FOLDER_SELECTION
        } else {
            NORMAL_SELECTION
        }
        val selectionArgs = mutableListOf(folderId.toString())
        if (trimmedSearchText.isNotEmpty()) {
            selection = "($selection) AND (${NoteColumns.Companion.SNIPPET} LIKE ?)"
            selectionArgs += "%$trimmedSearchText%"
        }

        val cursor = context.contentResolver.query(
            Notes.CONTENT_NOTE_URI,
            PROJECTION,
            selection,
            selectionArgs.toTypedArray(),
            "${NoteColumns.Companion.TYPE} DESC,${NoteColumns.Companion.MODIFIED_DATE} DESC"
        ) ?: return emptyList()

        cursor.use {
            return buildList {
                while (it.moveToNext()) {
                    add(it.toItem())
                }
            }
        }
    }

    private fun Cursor.toItem(): NotesListItemUi {
        val snippet = getString(SNIPPET_COLUMN)
            .replace(NoteEditActivity.TAG_CHECKED, "")
            .replace(NoteEditActivity.TAG_UNCHECKED, "")
            .trim()

        return NotesListItemUi(
            id = getLong(ID_COLUMN),
            title = snippet,
            type = getInt(TYPE_COLUMN),
            folderId = getLong(PARENT_ID_COLUMN),
            modifiedDate = getLong(MODIFIED_DATE_COLUMN),
            notesCount = getInt(NOTES_COUNT_COLUMN),
            bgColorId = getInt(BG_COLOR_ID_COLUMN),
            hasAttachment = getInt(HAS_ATTACHMENT_COLUMN) > 0,
            hasAlert = getLong(ALERT_DATE_COLUMN) > 0L,
            widget = AppWidgetAttribute(
                widgetId = getInt(WIDGET_ID_COLUMN),
                widgetType = getInt(WIDGET_TYPE_COLUMN)
            )
        )
    }

    companion object {
        private const val TAG = "NotesListViewModel"

        private val PROJECTION = arrayOf(
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
            NoteColumns.Companion.WIDGET_TYPE
        )

        private const val ID_COLUMN = 0
        private const val ALERT_DATE_COLUMN = 1
        private const val BG_COLOR_ID_COLUMN = 2
        private const val HAS_ATTACHMENT_COLUMN = 4
        private const val MODIFIED_DATE_COLUMN = 5
        private const val NOTES_COUNT_COLUMN = 6
        private const val PARENT_ID_COLUMN = 7
        private const val SNIPPET_COLUMN = 8
        private const val TYPE_COLUMN = 9
        private const val WIDGET_ID_COLUMN = 10
        private const val WIDGET_TYPE_COLUMN = 11

        private val NORMAL_SELECTION = "${NoteColumns.Companion.PARENT_ID}=?"

        private val ROOT_FOLDER_SELECTION = (
            "(${NoteColumns.Companion.TYPE}<>${Notes.TYPE_SYSTEM} AND ${NoteColumns.Companion.PARENT_ID}=?)" +
                " OR (${NoteColumns.Companion.ID}=${Notes.ID_CALL_RECORD_FOLDER} AND ${NoteColumns.Companion.NOTES_COUNT}>0)"
            )
    }
}
