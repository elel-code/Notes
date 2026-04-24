package net.micode.notes.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import net.micode.notes.tool.NotesPreferences

class NotesPreferenceActivity : ComponentActivity() {
    private var uiState by mutableStateOf(NotesSettingsUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        uiState = loadUiState()

        setContent {
            MaterialTheme {
                NotesPreferenceScreen(
                    state = uiState,
                    onBack = { finish() },
                    onToggleRandomBackground = { enabled ->
                        NotesPreferences.setRandomBackgroundEnabled(this, enabled)
                        uiState = uiState.copy(randomBackgroundEnabled = enabled)
                    },
                    onSelectDefaultBackgroundColor = { colorId ->
                        NotesPreferences.setDefaultBackgroundColor(this, colorId)
                        uiState = uiState.copy(defaultBackgroundColor = colorId)
                    },
                    onSelectListSortMode = { sortMode ->
                        NotesPreferences.setListSortMode(this, sortMode)
                        uiState = uiState.copy(listSortMode = sortMode)
                    },
                    onToggleDeleteConfirmation = { enabled ->
                        NotesPreferences.setDeleteConfirmationEnabled(this, enabled)
                        uiState = uiState.copy(deleteConfirmationEnabled = enabled)
                    },
                    onToggleRememberLastFolder = { enabled ->
                        NotesPreferences.setRememberLastFolderEnabled(this, enabled)
                        uiState = uiState.copy(rememberLastFolderEnabled = enabled)
                    }
                )
            }
        }
    }

    private fun loadUiState(): NotesSettingsUiState {
        return NotesSettingsUiState(
            randomBackgroundEnabled = NotesPreferences.isRandomBackgroundEnabled(this),
            defaultBackgroundColor = NotesPreferences.getDefaultBackgroundColor(this),
            listSortMode = NotesPreferences.getListSortMode(this),
            deleteConfirmationEnabled = NotesPreferences.isDeleteConfirmationEnabled(this),
            rememberLastFolderEnabled = NotesPreferences.isRememberLastFolderEnabled(this)
        )
    }
}
