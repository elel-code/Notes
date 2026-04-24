package net.micode.notes.ui

import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.micode.notes.R
import net.micode.notes.data.Notes
import net.micode.notes.data.Notes.NoteColumns
import net.micode.notes.gtask.remote.GTaskSyncService
import net.micode.notes.model.WorkingNote
import net.micode.notes.tool.DataUtils
import net.micode.notes.tool.ResourceParser
import net.micode.notes.tool.defaultPreferences
import net.micode.notes.widget.NoteWidgetUpdater

class NotesListActivity : ComponentActivity() {
    private var folderDialogState by mutableStateOf<FolderDialogUiState?>(null)

    private val noteEditorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        notesListViewModel.refresh(this)
    }

    private val notesListViewModel: NotesListViewModel by lazy {
        ViewModelProvider(this)[NotesListViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppInfoFromRawRes()

        setContent {
            MaterialTheme {
                val uiState by notesListViewModel.uiState.collectAsStateWithLifecycle()
                NotesListScreen(
                    state = uiState,
                    isSyncMode = isSyncMode,
                    folderDialogState = folderDialogState,
                    onBack = { handleBack() },
                    onSearchTextChange = { notesListViewModel.setSearchText(this, it) },
                    onItemClick = { item -> handleItemClick(item) },
                    onItemLongClick = { item -> notesListViewModel.toggleSelection(item.id) },
                    onCreateNote = { createNewNote(uiState.currentFolderId) },
                    onCreateFolder = {
                        folderDialogState = FolderDialogUiState(create = true, name = "")
                    },
                    onDeleteSelection = { confirmDeleteSelected(uiState.selectedIds.size) },
                    onClearSelection = { notesListViewModel.clearSelection() },
                    onRenameFolder = {
                        folderDialogState = FolderDialogUiState(
                            create = false,
                            name = it.title,
                            folder = it
                        )
                    },
                    onDeleteFolder = { confirmDeleteFolder(it) },
                    onOpenSettings = { startPreferenceActivity() },
                    onSyncClick = { handleSyncAction() },
                    onFolderDialogNameChange = { name ->
                        folderDialogState = folderDialogState?.copy(name = name)
                    },
                    onDismissFolderDialog = { folderDialogState = null },
                    onConfirmFolderDialog = { confirmFolderDialog() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        notesListViewModel.refresh(this)
    }

    private fun handleBack() {
        val state = notesListViewModel.uiState.value
        when {
            state.selectedIds.isNotEmpty() -> notesListViewModel.clearSelection()
            !state.isRoot -> notesListViewModel.goToRoot(this)
            else -> finish()
        }
    }

    private fun handleItemClick(item: NotesListItemUi) {
        val selectionActive = notesListViewModel.uiState.value.selectedIds.isNotEmpty()
        when {
            selectionActive && !item.isFolder -> notesListViewModel.toggleSelection(item.id)
            selectionActive -> Unit
            item.isFolder -> notesListViewModel.openFolder(this, item)
            else -> openNote(item.id)
        }
    }

    private fun createNewNote(folderId: Long) {
        val intent = Intent(this, NoteEditActivity::class.java).apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            putExtra(Notes.INTENT_EXTRA_FOLDER_ID, folderId)
        }
        noteEditorLauncher.launch(intent)
    }

    private fun openNote(noteId: Long) {
        val intent = Intent(this, NoteEditActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(Intent.EXTRA_UID, noteId)
        }
        noteEditorLauncher.launch(intent)
    }

    private fun confirmDeleteSelected(selectedCount: Int) {
        if (selectedCount == 0) {
            Toast.makeText(this, getString(R.string.menu_select_none), Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.alert_title_delete))
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setMessage(
                resources.getQuantityString(
                    R.plurals.alert_message_delete_notes,
                    selectedCount,
                    selectedCount
                )
            )
            .setPositiveButton(android.R.string.ok) { _, _ ->
                notesListViewModel.deleteSelectedNotes(
                    contentResolver = contentResolver,
                    isSyncMode = isSyncMode
                ) { widgets ->
                    updateWidgets(widgets)
                    notesListViewModel.refresh(this)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteFolder(folder: NotesListItemUi) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.alert_title_delete))
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setMessage(getString(R.string.alert_message_delete_folder))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                notesListViewModel.deleteFolder(
                    contentResolver = contentResolver,
                    folderId = folder.id,
                    isSyncMode = isSyncMode
                ) { widgets ->
                    updateWidgets(widgets)
                    notesListViewModel.refresh(this)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmFolderDialog() {
        val dialogState = folderDialogState ?: return
        val name = dialogState.name.trim()
        if (name.isEmpty()) {
            return
        }

        if (DataUtils.checkVisibleFolderName(contentResolver, name) &&
            (dialogState.create || !name.contentEquals(dialogState.folder?.title))
        ) {
            Toast.makeText(
                this,
                getString(R.string.folder_exist, name),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (dialogState.create) {
            val values = ContentValues().apply {
                put(NoteColumns.Companion.SNIPPET, name)
                put(NoteColumns.Companion.TYPE, Notes.TYPE_FOLDER)
            }
            contentResolver.insert(Notes.CONTENT_NOTE_URI, values)
        } else {
            val folderId = dialogState.folder?.id ?: run {
                Log.e(TAG, "Missing folder while renaming")
                return
            }
            val values = ContentValues().apply {
                put(NoteColumns.Companion.SNIPPET, name)
                put(NoteColumns.Companion.TYPE, Notes.TYPE_FOLDER)
                put(NoteColumns.Companion.LOCAL_MODIFIED, 1)
            }
            contentResolver.update(
                Notes.CONTENT_NOTE_URI,
                values,
                "${NoteColumns.Companion.ID}=?",
                arrayOf(folderId.toString())
            )
        }

        folderDialogState = null
        notesListViewModel.refresh(this)
    }

    private fun handleSyncAction() {
        if (!isSyncMode) {
            startPreferenceActivity()
            return
        }

        if (GTaskSyncService.isSyncing) {
            GTaskSyncService.cancelSync(this)
        } else {
            GTaskSyncService.startSync(this)
        }
    }

    private fun updateWidgets(widgets: Set<AppWidgetAttribute>) {
        NoteWidgetUpdater.updateWidgets(this, widgets)
    }

    private val isSyncMode: Boolean
        get() = NotesPreferenceActivity.getSyncAccountName(this).isNotBlank()

    private fun startPreferenceActivity() {
        startActivity(Intent(this, NotesPreferenceActivity::class.java))
    }

    private fun setAppInfoFromRawRes() {
        val preferences = defaultPreferences()
        if (preferences.getBoolean(PREFERENCE_ADD_INTRODUCTION, false)) {
            return
        }

        val introduction = runCatching {
            resources.openRawResource(R.raw.introduction).bufferedReader().use { it.readText() }
        }.getOrElse { error ->
            Log.e(TAG, "Read introduction file error", error)
            return
        }

        val note = WorkingNote.createEmptyNote(
            this,
            Notes.ID_ROOT_FOLDER.toLong(),
            AppWidgetManager.INVALID_APPWIDGET_ID,
            Notes.TYPE_WIDGET_INVALIDE,
            ResourceParser.RED
        )
        note.setWorkingText(introduction)
        if (note.saveNote()) {
            preferences.edit { putBoolean(PREFERENCE_ADD_INTRODUCTION, true) }
        } else {
            Log.e(TAG, "Save introduction note error")
        }
    }

    companion object {
        private const val TAG = "NotesListActivity"
        private const val PREFERENCE_ADD_INTRODUCTION = "net.micode.notes.introduction"
    }
}
