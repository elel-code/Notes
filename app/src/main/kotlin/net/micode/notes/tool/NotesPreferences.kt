package net.micode.notes.tool

import android.content.Context
import androidx.core.content.edit
import net.micode.notes.data.Notes

enum class NotesSortMode(val value: Int) {
    MODIFIED_DESC(0),
    MODIFIED_ASC(1),
    TITLE_ASC(2);

    companion object {
        fun fromValue(value: Int): NotesSortMode {
            return values().firstOrNull { it.value == value } ?: MODIFIED_DESC
        }
    }
}

object NotesPreferences {
    private const val KEY_RANDOM_BACKGROUND = "pref_key_bg_random_appear"
    private const val KEY_DEFAULT_BACKGROUND = "pref_key_default_bg_color"
    private const val KEY_LIST_SORT_MODE = "pref_key_list_sort_mode"
    private const val KEY_DELETE_CONFIRMATION = "pref_key_delete_confirmation"
    private const val KEY_REMEMBER_LAST_FOLDER = "pref_key_remember_last_folder"
    private const val KEY_LAST_FOLDER_ID = "pref_key_last_folder_id"
    private const val KEY_LAST_FOLDER_TITLE = "pref_key_last_folder_title"

    fun isRandomBackgroundEnabled(context: Context): Boolean {
        return context.defaultPreferences().getBoolean(KEY_RANDOM_BACKGROUND, false)
    }

    fun setRandomBackgroundEnabled(context: Context, enabled: Boolean) {
        context.defaultPreferences().edit { putBoolean(KEY_RANDOM_BACKGROUND, enabled) }
    }

    fun getDefaultBackgroundColor(context: Context): Int {
        val colorId = context.defaultPreferences()
            .getInt(KEY_DEFAULT_BACKGROUND, ResourceParser.BG_DEFAULT_COLOR)
        return if (colorId in 0 until ResourceParser.NoteBgResources.resourcesSize) {
            colorId
        } else {
            ResourceParser.BG_DEFAULT_COLOR
        }
    }

    fun setDefaultBackgroundColor(context: Context, colorId: Int) {
        context.defaultPreferences().edit {
            putInt(KEY_DEFAULT_BACKGROUND, getDefaultBackgroundColorId(colorId))
        }
    }

    fun getListSortMode(context: Context): NotesSortMode {
        val value = context.defaultPreferences().getInt(
            KEY_LIST_SORT_MODE,
            NotesSortMode.MODIFIED_DESC.value
        )
        return NotesSortMode.fromValue(value)
    }

    fun setListSortMode(context: Context, sortMode: NotesSortMode) {
        context.defaultPreferences().edit { putInt(KEY_LIST_SORT_MODE, sortMode.value) }
    }

    fun isDeleteConfirmationEnabled(context: Context): Boolean {
        return context.defaultPreferences().getBoolean(KEY_DELETE_CONFIRMATION, true)
    }

    fun setDeleteConfirmationEnabled(context: Context, enabled: Boolean) {
        context.defaultPreferences().edit { putBoolean(KEY_DELETE_CONFIRMATION, enabled) }
    }

    fun isRememberLastFolderEnabled(context: Context): Boolean {
        return context.defaultPreferences().getBoolean(KEY_REMEMBER_LAST_FOLDER, true)
    }

    fun setRememberLastFolderEnabled(context: Context, enabled: Boolean) {
        context.defaultPreferences().edit { putBoolean(KEY_REMEMBER_LAST_FOLDER, enabled) }
        if (!enabled) {
            clearRememberedFolder(context)
        }
    }

    fun getRememberedFolderId(context: Context): Long {
        if (!isRememberLastFolderEnabled(context)) {
            return Notes.ID_ROOT_FOLDER.toLong()
        }
        return context.defaultPreferences()
            .getLong(KEY_LAST_FOLDER_ID, Notes.ID_ROOT_FOLDER.toLong())
    }

    fun getRememberedFolderTitle(context: Context): String {
        if (!isRememberLastFolderEnabled(context)) {
            return ""
        }
        return context.defaultPreferences().getString(KEY_LAST_FOLDER_TITLE, "").orEmpty()
    }

    fun rememberLastFolder(context: Context, folderId: Long, folderTitle: String) {
        if (!isRememberLastFolderEnabled(context)) {
            clearRememberedFolder(context)
            return
        }

        context.defaultPreferences().edit {
            putLong(KEY_LAST_FOLDER_ID, folderId)
            putString(KEY_LAST_FOLDER_TITLE, folderTitle)
        }
    }

    fun clearRememberedFolder(context: Context) {
        context.defaultPreferences().edit {
            putLong(KEY_LAST_FOLDER_ID, Notes.ID_ROOT_FOLDER.toLong())
            putString(KEY_LAST_FOLDER_TITLE, "")
        }
    }

    private fun getDefaultBackgroundColorId(colorId: Int): Int {
        return if (colorId in 0 until ResourceParser.NoteBgResources.resourcesSize) {
            colorId
        } else {
            ResourceParser.BG_DEFAULT_COLOR
        }
    }
}
