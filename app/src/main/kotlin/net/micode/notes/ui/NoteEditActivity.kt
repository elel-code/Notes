package net.micode.notes.ui

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.SearchManager
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import net.micode.notes.R
import net.micode.notes.data.Notes
import net.micode.notes.data.Notes.TextNote
import net.micode.notes.model.WorkingNote
import net.micode.notes.tool.DataUtils
import net.micode.notes.tool.PendingIntentCompat
import net.micode.notes.tool.ResourceParser
import net.micode.notes.tool.defaultPreferences
import net.micode.notes.widget.NoteWidgetUpdater

class NoteEditActivity : ComponentActivity() {
    private lateinit var workingNote: WorkingNote
    private lateinit var sharedPrefs: SharedPreferences
    private var userQuery = ""
    private var uiState by mutableStateOf(NoteEditUiState())
    private var deleteDialogVisible by mutableStateOf(false)
    private var reminderDialogState by mutableStateOf<ReminderDialogUiState?>(null)

    private val noteEditViewModel: NoteEditViewModel by lazy {
        ViewModelProvider(this)[NoteEditViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        sharedPrefs = defaultPreferences()
        val initialIntent = if (savedInstanceState?.containsKey(Intent.EXTRA_UID) == true) {
            Intent(Intent.ACTION_VIEW).apply {
                putExtra(Intent.EXTRA_UID, savedInstanceState.getLong(Intent.EXTRA_UID))
            }
        } else {
            intent
        }

        if (!initActivityState(initialIntent)) {
            finish()
            return
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveNote()
                finish()
            }
        })

        setContent {
            MaterialTheme {
                NoteEditScreen(
                    state = uiState,
                    deleteDialogVisible = deleteDialogVisible,
                    reminderDialogState = reminderDialogState,
                    onBack = {
                        saveNote()
                        finish()
                    },
                    onContentChange = { uiState = uiState.copy(content = it) },
                    onNewNote = { createNewNote() },
                    onDelete = { deleteDialogVisible = true },
                    onDismissDeleteDialog = { deleteDialogVisible = false },
                    onConfirmDelete = {
                        deleteDialogVisible = false
                        setResult(RESULT_OK)
                        deleteCurrentNote()
                        finish()
                    },
                    onToggleChecklist = { toggleChecklistMode() },
                    onShare = { shareCurrentNote() },
                    onExportText = { exportCurrentNoteAsTxt() },
                    onSaveLongImage = { saveCurrentNoteAsLongImage() },
                    onSendToDesktop = { sendToDesktop() },
                    onSetReminder = {
                        reminderDialogState = ReminderDialogUiState(
                            initialDate = uiState.alertDate.takeIf { it > 0L }
                                ?: System.currentTimeMillis()
                        )
                    },
                    onDismissReminderDialog = { reminderDialogState = null },
                    onConfirmReminder = { date ->
                        reminderDialogState = null
                        workingNote.setAlertDate(date, true)
                        updateAlarm(date, true)
                        uiState = uiState.copy(alertDate = date)
                    },
                    onClearReminder = { clearReminder() },
                    onSelectBackground = { bgColorId ->
                        workingNote.bgColorId = bgColorId
                        syncUiStateFromWorkingNote(uiState.content)
                    },
                    onSelectFontSize = { fontSizeId ->
                        sharedPrefs.edit { putInt(PREFERENCE_FONT_SIZE, fontSizeId) }
                        uiState = uiState.copy(fontSizeId = fontSizeId)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (initActivityState(intent)) {
            syncUiStateFromWorkingNote()
        }
    }

    override fun onResume() {
        super.onResume()
        syncUiStateFromWorkingNote(uiState.content)
    }

    override fun onPause() {
        super.onPause()
        saveNote()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!workingNote.existInDatabase()) {
            saveNote()
        }
        outState.putLong(Intent.EXTRA_UID, workingNote.noteId)
    }

    private fun initActivityState(intent: Intent): Boolean {
        @Suppress("DEPRECATION")
        val note = if (intent.action == Intent.ACTION_VIEW) {
            var noteId = intent.getLongExtra(Intent.EXTRA_UID, 0)
            userQuery = ""

            if (intent.hasExtra(SearchManager.EXTRA_DATA_KEY)) {
                noteId = intent.getStringExtra(SearchManager.EXTRA_DATA_KEY)?.toLongOrNull() ?: 0L
                userQuery = intent.getStringExtra(SearchManager.USER_QUERY).orEmpty()
            }

            if (!DataUtils.visibleInNoteDatabase(contentResolver, noteId, Notes.TYPE_NOTE)) {
                startActivity(Intent(this, NotesListActivity::class.java))
                showToast(R.string.error_note_not_exist)
                return false
            }

            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
            loadWorkingNote(noteId) ?: return false
        } else if (intent.action == Intent.ACTION_INSERT_OR_EDIT) {
            val folderId = intent.getLongExtra(Notes.INTENT_EXTRA_FOLDER_ID, 0)
            val widgetId = intent.getIntExtra(
                Notes.INTENT_EXTRA_WIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            val widgetType = intent.getIntExtra(
                Notes.INTENT_EXTRA_WIDGET_TYPE,
                Notes.TYPE_WIDGET_INVALIDE
            )
            val bgResId = intent.getIntExtra(
                Notes.INTENT_EXTRA_BACKGROUND_ID,
                ResourceParser.getDefaultBgId(this)
            )

            val phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
            val callDate = intent.getLongExtra(Notes.INTENT_EXTRA_CALL_DATE, 0)
            val createdNote = if (callDate != 0L && phoneNumber != null) {
                val noteId = DataUtils.getNoteIdByPhoneNumberAndCallDate(
                    contentResolver,
                    phoneNumber,
                    callDate
                )
                if (noteId > 0) {
                    loadWorkingNote(noteId) ?: return false
                } else {
                    WorkingNote.createEmptyNote(this, folderId, widgetId, widgetType, bgResId).apply {
                        convertToCallNote(phoneNumber, callDate)
                    }
                }
            } else {
                WorkingNote.createEmptyNote(this, folderId, widgetId, widgetType, bgResId)
            }

            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            )
            createdNote
        } else {
            Log.e(TAG, "Intent not specified action, should not support")
            return false
        }

        workingNote = note
        deleteDialogVisible = false
        reminderDialogState = null
        syncUiStateFromWorkingNote()
        return true
    }

    private fun loadWorkingNote(noteId: Long): WorkingNote? {
        return runCatching { WorkingNote.load(this, noteId) }
            .onFailure { error ->
                Log.e(TAG, "load note failed with note id $noteId", error)
            }
            .getOrNull()
    }

    private fun syncUiStateFromWorkingNote(contentOverride: String? = null) {
        val fontSizeId = sharedPrefs.getInt(PREFERENCE_FONT_SIZE, ResourceParser.BG_DEFAULT_FONT_SIZE)
            .takeIf { it < 4 }
            ?: ResourceParser.BG_DEFAULT_FONT_SIZE

        uiState = NoteEditUiState(
            content = contentOverride ?: workingNote.content.orEmpty(),
            bgColorId = workingNote.bgColorId,
            bgResId = workingNote.bgColorResId,
            titleBgResId = workingNote.titleBgResId,
            fontSizeId = fontSizeId,
            modifiedDate = if (workingNote.modifiedDate > 0L) {
                workingNote.modifiedDate
            } else {
                System.currentTimeMillis()
            },
            alertDate = workingNote.alertDate,
            isChecklistMode = workingNote.checkListMode == TextNote.MODE_CHECK_LIST
        )
    }

    private fun toggleChecklistMode() {
        val enableChecklist = workingNote.checkListMode != TextNote.MODE_CHECK_LIST
        val updatedContent = if (enableChecklist) {
            toChecklistText(uiState.content)
        } else {
            fromChecklistText(uiState.content)
        }
        workingNote.checkListMode = if (enableChecklist) TextNote.MODE_CHECK_LIST else 0
        uiState = uiState.copy(
            content = updatedContent,
            isChecklistMode = enableChecklist
        )
    }

    private fun toChecklistText(text: String): String {
        val normalized = fromChecklistText(text)
        return normalized.lineSequence()
            .filter { it.isNotBlank() }
            .joinToString("\n") { "$TAG_UNCHECKED ${it.trim()}" }
    }

    private fun fromChecklistText(text: String): String {
        return text.lineSequence()
            .joinToString("\n") { line ->
                line.removePrefix("$TAG_CHECKED ")
                    .removePrefix("$TAG_UNCHECKED ")
            }
            .trimEnd()
    }

    private fun deleteCurrentNote() {
        if (workingNote.existInDatabase()) {
            val ids = hashSetOf<Long?>()
            val id = workingNote.noteId
            if (id != Notes.ID_ROOT_FOLDER.toLong()) {
                ids.add(id)
            }
            val deleted = if (!isSyncMode) {
                DataUtils.batchDeleteNotes(contentResolver, ids)
            } else {
                DataUtils.batchMoveToFolder(contentResolver, ids, Notes.ID_TRASH_FOLER.toLong())
            }
            if (!deleted) {
                Log.e(TAG, "Delete note failed")
            }
        }
        workingNote.markDeleted(true)
        updateWidgetIfNeeded()
    }

    private fun clearReminder() {
        reminderDialogState = null
        workingNote.setAlertDate(0, false)
        updateAlarm(0, false)
        uiState = uiState.copy(alertDate = 0L)
    }

    private fun updateAlarm(date: Long, set: Boolean) {
        if (!workingNote.existInDatabase()) {
            saveNote()
        }
        if (workingNote.noteId <= 0) {
            showToast(R.string.error_note_empty_for_clock)
            return
        }

        val intent = Intent(this, AlarmReceiver::class.java).apply {
            data = ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, workingNote.noteId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntentCompat.immutableFlag()
        )
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        if (!set) {
            alarmManager.cancel(pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, date, pendingIntent)
        }
    }

    private fun createNewNote() {
        saveNote()
        finish()
        startActivity(
            Intent(this, NoteEditActivity::class.java).apply {
                action = Intent.ACTION_INSERT_OR_EDIT
                putExtra(Notes.INTENT_EXTRA_FOLDER_ID, workingNote.folderId)
            }
        )
    }

    private fun shareCurrentNote() {
        val text = currentNoteTextForShare
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
            type = "text/plain"
        }
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.share_note_chooser_title)))
        } catch (error: ActivityNotFoundException) {
            Log.e(TAG, "Share note failed", error)
            showToast(R.string.error_no_share_app)
        }
    }

    private fun exportCurrentNoteAsTxt() {
        val noteText = currentNoteTextForExport
        if (noteText.isBlank()) {
            showToast(R.string.error_note_empty_for_export)
            return
        }

        noteEditViewModel.exportCurrentNoteAsTxt(applicationContext, noteText) { exported ->
            showToast(if (exported) R.string.success_sdcard_export else R.string.error_sdcard_export)
        }
    }

    private fun saveCurrentNoteAsLongImage() {
        val noteText = currentNoteTextForLongImage
        if (noteText.isBlank()) {
            showToast(R.string.error_note_empty_for_export)
            return
        }

        noteEditViewModel.saveCurrentNoteAsLongImage(
            applicationContext,
            noteText,
            currentEditorTextSize(),
            workingNote.bgColorResId,
            resources.displayMetrics.widthPixels
        ) { saved ->
            showToast(
                if (saved) R.string.success_long_image_saved else R.string.error_long_image_save_failed
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun sendToDesktop() {
        if (!workingNote.existInDatabase()) {
            saveNote()
        }

        if (workingNote.noteId <= 0) {
            showToast(R.string.error_note_empty_for_send_to_desktop)
            return
        }

        val sender = Intent()
        val shortcutIntent = Intent(this, NoteEditActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(Intent.EXTRA_UID, workingNote.noteId)
        }
        sender.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
        sender.putExtra(Intent.EXTRA_SHORTCUT_NAME, makeShortcutIconTitle(uiState.content))
        sender.putExtra(
            Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
            Intent.ShortcutIconResource.fromContext(this, R.drawable.icon_app)
        )
        sender.putExtra("duplicate", true)
        sender.action = "com.android.launcher.action.INSTALL_SHORTCUT"
        sendBroadcast(sender)
        showToast(R.string.info_note_enter_desktop)
    }

    private fun saveNote(): Boolean {
        workingNote.setWorkingText(uiState.content)
        val saved = workingNote.saveNote()
        if (saved) {
            setResult(RESULT_OK)
            uiState = uiState.copy(modifiedDate = System.currentTimeMillis())
            updateWidgetIfNeeded()
        }
        return saved
    }

    private fun updateWidgetIfNeeded() {
        if (workingNote.widgetId == AppWidgetManager.INVALID_APPWIDGET_ID ||
            workingNote.widgetType == Notes.TYPE_WIDGET_INVALIDE
        ) {
            return
        }
        NoteWidgetUpdater.updateWidget(this, workingNote.widgetId, workingNote.widgetType)
    }

    private fun currentEditorTextSize(): Float {
        val spValue = when (uiState.fontSizeId) {
            ResourceParser.TEXT_SMALL -> 15
            ResourceParser.TEXT_MEDIUM -> 18
            ResourceParser.TEXT_LARGE -> 22
            ResourceParser.TEXT_SUPER -> 26
            else -> 18
        }
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            spValue.toFloat(),
            resources.displayMetrics
        )
    }

    private val currentNoteTextForExport: String
        get() = fromChecklistText(uiState.content)

    private val currentNoteTextForShare: String
        get() = uiState.content

    private val currentNoteTextForLongImage: String
        get() = uiState.content

    private val isSyncMode: Boolean
        get() = NotesPreferenceActivity.getSyncAccountName(this).isNotBlank()

    private fun makeShortcutIconTitle(content: String): String {
        val title = content.replace(TAG_CHECKED, "").replace(TAG_UNCHECKED, "")
        return if (title.length > SHORTCUT_ICON_TITLE_MAX_LEN) {
            title.substring(0, SHORTCUT_ICON_TITLE_MAX_LEN)
        } else {
            title
        }
    }

    private fun showToast(resId: Int, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, resId, duration).show()
    }

    companion object {
        private const val TAG = "NoteEditActivity"
        private const val PREFERENCE_FONT_SIZE = "pref_font_size"
        private const val SHORTCUT_ICON_TITLE_MAX_LEN = 10
        val TAG_CHECKED: String = '\u221A'.toString()
        val TAG_UNCHECKED: String = '\u25A1'.toString()
    }
}
