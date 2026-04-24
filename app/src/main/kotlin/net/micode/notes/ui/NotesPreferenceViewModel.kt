package net.micode.notes.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.micode.notes.data.room.NotesRoomDatabase

class NotesPreferenceViewModel : ViewModel() {
    fun clearSyncMetadata(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            NotesRoomDatabase(appContext).notesDao().clearSyncMetadata()
        }
    }
}
