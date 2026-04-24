package net.micode.notes.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.micode.notes.data.Notes
import net.micode.notes.data.NotesRepository
import net.micode.notes.data.room.NoteListRow
import net.micode.notes.tool.NotesPreferences

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
            val items = NotesRepository(context).loadListItems(
                folderId = currentState.currentFolderId,
                searchText = currentState.searchText,
                sortMode = NotesPreferences.getListSortMode(context)
            ).map { item ->
                item.toUi()
            }

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

    fun restoreFolder(folderId: Long, folderTitle: String) {
        _uiState.update {
            it.copy(
                currentFolderId = folderId,
                currentFolderTitle = folderTitle,
                selectedIds = emptySet()
            )
        }
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
        context: Context,
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
            val success = NotesRepository(context).deleteNotes(selectedIds)

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
        context: Context,
        folderId: Long,
        onComplete: (Set<AppWidgetAttribute>) -> Unit
    ) {
        if (folderId == Notes.ID_ROOT_FOLDER.toLong()) {
            Log.e(TAG, "Wrong folder id, should not happen: $folderId")
            onComplete(emptySet())
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val repository = NotesRepository(context)
            val widgets = repository.collectFolderWidgets(folderId)
            val success = repository.deleteFolder(folderId)

            if (!success) {
                Log.e(TAG, "Failed to delete folder $folderId")
            }

            withContext(Dispatchers.Main) {
                onComplete(widgets)
            }
        }
    }

    fun saveFolder(
        context: Context,
        dialogState: FolderDialogUiState,
        onDuplicateName: () -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        val name = dialogState.name.trim()
        if (name.isEmpty()) {
            onComplete(false)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val repository = NotesRepository(context)
            val duplicateName = (dialogState.create || !name.contentEquals(dialogState.folder?.title)) &&
                repository.hasVisibleFolderNamed(name)
            if (duplicateName) {
                withContext(Dispatchers.Main) {
                    onDuplicateName()
                }
                return@launch
            }

            val success = if (dialogState.create) {
                repository.createFolder(name) > 0
            } else {
                val folderId = dialogState.folder?.id
                if (folderId == null) {
                    Log.e(TAG, "Missing folder while renaming")
                    false
                } else {
                    repository.renameFolder(folderId, name)
                }
            }

            if (!success) {
                Log.e(TAG, "Failed to save folder")
            }

            withContext(Dispatchers.Main) {
                if (success && !dialogState.create && _uiState.value.currentFolderId == dialogState.folder?.id) {
                    _uiState.update { state ->
                        state.copy(currentFolderTitle = name)
                    }
                }
                onComplete(success)
            }
        }
    }

    companion object {
        private const val TAG = "NotesListViewModel"
    }
}

private fun NoteListRow.toUi(): NotesListItemUi {
    val title = snippet
        .replace(NoteEditActivity.TAG_CHECKED, "")
        .replace(NoteEditActivity.TAG_UNCHECKED, "")
        .trim()

    return NotesListItemUi(
        id = id,
        title = title,
        type = type,
        folderId = parentId,
        modifiedDate = modifiedDate,
        notesCount = notesCount,
        bgColorId = bgColorId,
        hasAttachment = hasAttachment > 0,
        hasAlert = alertedDate > 0L,
        widget = AppWidgetAttribute(
            widgetId = widgetId,
            widgetType = widgetType
        )
    )
}
