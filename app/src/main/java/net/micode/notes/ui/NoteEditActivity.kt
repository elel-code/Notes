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
package net.micode.notes.ui

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.app.SearchManager
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.text.style.BackgroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import net.micode.notes.R
import net.micode.notes.data.Notes
import net.micode.notes.data.Notes.TextNote
import net.micode.notes.model.WorkingNote
import net.micode.notes.model.WorkingNote.NoteSettingChangedListener
import net.micode.notes.tool.DataUtils
import net.micode.notes.tool.PendingIntentCompat
import net.micode.notes.tool.ResourceParser
import net.micode.notes.tool.ResourceParser.TextAppearanceResources
import net.micode.notes.ui.DateTimePickerDialog.OnDateTimeSetListener
import net.micode.notes.ui.NoteEditText.OnTextViewChangeListener
import net.micode.notes.widget.NoteWidgetProvider_2x
import net.micode.notes.widget.NoteWidgetProvider_4x
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.regex.Pattern
import kotlin.math.max

class NoteEditActivity : Activity(), View.OnClickListener, NoteSettingChangedListener,
    OnTextViewChangeListener {
    private inner class HeadViewHolder {
        var tvModified: TextView? = null

        var ivAlertIcon: ImageView? = null

        var tvAlertDate: TextView? = null

        var ibSetBgColor: ImageView? = null
    }

    private var mNoteHeaderHolder: HeadViewHolder? = null

    private var mHeadViewPanel: View? = null

    private var mNoteBgColorSelector: View? = null

    private var mFontSizeSelector: View? = null

    private var mNoteEditor: EditText? = null

    private var mNoteEditorPanel: View? = null

    private var mWorkingNote: WorkingNote? = null

    private var mSharedPrefs: SharedPreferences? = null
    private var mFontSizeId = 0

    private var mEditTextList: LinearLayout? = null

    private var mUserQuery: String? = null
    private var mPattern: Pattern? = null
    private var mPendingStorageAction: Int = STORAGE_ACTION_NONE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.setContentView(R.layout.note_edit)

        if (savedInstanceState == null && !initActivityState(getIntent())) {
            finish()
            return
        }
        initResources()
    }

    /**
     * Current activity may be killed when the memory is low. Once it is killed, for another time
     * user load this activity, we should restore the former state
     */
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        if (savedInstanceState != null && savedInstanceState.containsKey(Intent.EXTRA_UID)) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.putExtra(Intent.EXTRA_UID, savedInstanceState.getLong(Intent.EXTRA_UID))
            if (!initActivityState(intent)) {
                finish()
                return
            }
            Log.d(TAG, "Restoring from killed activity")
        }
    }

    private fun initActivityState(intent: Intent): Boolean {
        /**
         * If the user specified the [Intent.ACTION_VIEW] but not provided with id,
         * then jump to the NotesListActivity
         */
        mWorkingNote = null
        if (TextUtils.equals(Intent.ACTION_VIEW, intent.getAction())) {
            var noteId = intent.getLongExtra(Intent.EXTRA_UID, 0)
            mUserQuery = ""

            /**
             * Starting from the searched result
             */
            if (intent.hasExtra(SearchManager.EXTRA_DATA_KEY)) {
                noteId = intent.getStringExtra(SearchManager.EXTRA_DATA_KEY)!!.toLong()
                mUserQuery = intent.getStringExtra(SearchManager.USER_QUERY)
            }

            if (!DataUtils.visibleInNoteDatabase(getContentResolver(), noteId, Notes.TYPE_NOTE)) {
                val jump = Intent(this, NotesListActivity::class.java)
                startActivity(jump)
                showToast(R.string.error_note_not_exist)
                finish()
                return false
            } else {
                mWorkingNote = WorkingNote.Companion.load(this, noteId)
                if (mWorkingNote == null) {
                    Log.e(TAG, "load note failed with note id" + noteId)
                    finish()
                    return false
                }
            }
            getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                        or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
        } else if (TextUtils.equals(Intent.ACTION_INSERT_OR_EDIT, intent.getAction())) {
            // New note
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

            // Parse call-record note
            val phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
            val callDate = intent.getLongExtra(Notes.INTENT_EXTRA_CALL_DATE, 0)
            if (callDate != 0L && phoneNumber != null) {
                if (TextUtils.isEmpty(phoneNumber)) {
                    Log.w(TAG, "The call record number is null")
                }
                var noteId: Long = 0
                if ((DataUtils.getNoteIdByPhoneNumberAndCallDate(
                        getContentResolver(),
                        phoneNumber, callDate
                    ).also { noteId = it }) > 0
                ) {
                    mWorkingNote = WorkingNote.Companion.load(this, noteId)
                    if (mWorkingNote == null) {
                        Log.e(TAG, "load call note failed with note id" + noteId)
                        finish()
                        return false
                    }
                } else {
                    mWorkingNote = WorkingNote.Companion.createEmptyNote(
                        this, folderId, widgetId,
                        widgetType, bgResId
                    )
                    mWorkingNote!!.convertToCallNote(phoneNumber, callDate)
                }
            } else {
                mWorkingNote = WorkingNote.Companion.createEmptyNote(
                    this, folderId, widgetId, widgetType,
                    bgResId
                )
            }

            getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        or WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            )
        } else {
            Log.e(TAG, "Intent not specified action, should not support")
            finish()
            return false
        }
        mWorkingNote!!.setOnSettingStatusChangedListener(this)
        return true
    }

    override fun onResume() {
        super.onResume()
        initNoteScreen()
    }

    private fun initNoteScreen() {
        mNoteEditor!!.setTextAppearance(
            this,
            TextAppearanceResources.getTexAppearanceResource(mFontSizeId)
        )
        if (mWorkingNote!!.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            switchToListMode(mWorkingNote!!.getContent())
        } else {
            mNoteEditor!!.setText(getHighlightQueryResult(mWorkingNote!!.getContent(), mUserQuery))
            mNoteEditor!!.setSelection(mNoteEditor!!.getText().length)
        }
        for (id in sBgSelectorSelectionMap.keys) {
            findViewById<View?>(sBgSelectorSelectionMap.get(id)!!).setVisibility(View.GONE)
        }
        mHeadViewPanel!!.setBackgroundResource(mWorkingNote!!.getTitleBgResId())
        mNoteEditorPanel!!.setBackgroundResource(mWorkingNote!!.getBgColorResId())

        mNoteHeaderHolder!!.tvModified!!.setText(
            DateUtils.formatDateTime(
                this,
                mWorkingNote!!.getModifiedDate(), (DateUtils.FORMAT_SHOW_DATE
                        or DateUtils.FORMAT_NUMERIC_DATE or DateUtils.FORMAT_SHOW_TIME
                        or DateUtils.FORMAT_SHOW_YEAR)
            )
        )

        /**
         * TODO: Add the menu for setting alert. Currently disable it because the DateTimePicker
         * is not ready
         */
        showAlertHeader()
    }

    private fun showAlertHeader() {
        if (mWorkingNote!!.hasClockAlert()) {
            val time = System.currentTimeMillis()
            if (time > mWorkingNote!!.getAlertDate()) {
                mNoteHeaderHolder!!.tvAlertDate!!.setText(R.string.note_alert_expired)
            } else {
                mNoteHeaderHolder!!.tvAlertDate!!.setText(
                    DateUtils.getRelativeTimeSpanString(
                        mWorkingNote!!.getAlertDate(), time, DateUtils.MINUTE_IN_MILLIS
                    )
                )
            }
            mNoteHeaderHolder!!.tvAlertDate!!.setVisibility(View.VISIBLE)
            mNoteHeaderHolder!!.ivAlertIcon!!.setVisibility(View.VISIBLE)
        } else {
            mNoteHeaderHolder!!.tvAlertDate!!.setVisibility(View.GONE)
            mNoteHeaderHolder!!.ivAlertIcon!!.setVisibility(View.GONE)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        initActivityState(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        /**
         * For new note without note id, we should firstly save it to
         * generate a id. If the editing note is not worth saving, there
         * is no id which is equivalent to create new note
         */
        if (!mWorkingNote!!.existInDatabase()) {
            saveNote()
        }
        outState.putLong(Intent.EXTRA_UID, mWorkingNote!!.getNoteId())
        Log.d(TAG, "Save working note id: " + mWorkingNote!!.getNoteId() + " onSaveInstanceState")
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (mNoteBgColorSelector!!.getVisibility() == View.VISIBLE
            && !inRangeOfView(mNoteBgColorSelector!!, ev)
        ) {
            mNoteBgColorSelector!!.setVisibility(View.GONE)
            return true
        }

        if (mFontSizeSelector!!.getVisibility() == View.VISIBLE
            && !inRangeOfView(mFontSizeSelector!!, ev)
        ) {
            mFontSizeSelector!!.setVisibility(View.GONE)
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun inRangeOfView(view: View, ev: MotionEvent): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val x = location[0]
        val y = location[1]
        if (ev.getX() < x || ev.getX() > (x + view.getWidth()) || ev.getY() < y || ev.getY() > (y + view.getHeight())) {
            return false
        }
        return true
    }

    private fun initResources() {
        mHeadViewPanel = findViewById<View>(R.id.note_title)
        mNoteHeaderHolder = HeadViewHolder()
        mNoteHeaderHolder!!.tvModified = findViewById<View?>(R.id.tv_modified_date) as TextView
        mNoteHeaderHolder!!.ivAlertIcon = findViewById<View?>(R.id.iv_alert_icon) as ImageView
        mNoteHeaderHolder!!.tvAlertDate = findViewById<View?>(R.id.tv_alert_date) as TextView
        mNoteHeaderHolder!!.ibSetBgColor = findViewById<View?>(R.id.btn_set_bg_color) as ImageView
        mNoteHeaderHolder!!.ibSetBgColor!!.setOnClickListener(this)
        mNoteEditor = findViewById<View?>(R.id.note_edit_view) as EditText
        mNoteEditorPanel = findViewById<View>(R.id.sv_note_edit)
        mNoteBgColorSelector = findViewById<View>(R.id.note_bg_color_selector)
        for (id in sBgSelectorBtnsMap.keys) {
            val iv = findViewById<View?>(id!!) as ImageView
            iv.setOnClickListener(this)
        }

        mFontSizeSelector = findViewById<View>(R.id.font_size_selector)
        for (id in sFontSizeBtnsMap.keys) {
            val view = findViewById<View>(id!!)
            view.setOnClickListener(this)
        }

        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        mFontSizeId =
            mSharedPrefs!!.getInt(PREFERENCE_FONT_SIZE, ResourceParser.BG_DEFAULT_FONT_SIZE)
        /**
         * HACKME: Fix bug of store the resource id in shared preference.
         * The id may larger than the length of resources, in this case,
         * return the [ResourceParser.BG_DEFAULT_FONT_SIZE]
         */
        if (mFontSizeId >= TextAppearanceResources.getResourcesSize()) {
            mFontSizeId = ResourceParser.BG_DEFAULT_FONT_SIZE
        }
        mEditTextList = findViewById<View?>(R.id.note_edit_list) as LinearLayout
    }

    override fun onPause() {
        super.onPause()
        if (saveNote()) {
            Log.d(TAG, "Note data was saved with length:" + mWorkingNote!!.getContent().length)
        }
        clearSettingState()
    }

    private fun updateWidget() {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
        if (mWorkingNote!!.getWidgetType() == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x::class.java)
        } else if (mWorkingNote!!.getWidgetType() == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x::class.java)
        } else {
            Log.e(TAG, "Unspported widget type")
            return
        }

        intent.putExtra(
            AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(
                mWorkingNote!!.getWidgetId()
            )
        )

        sendBroadcast(intent)
        setResult(RESULT_OK, intent)
    }

    override fun onClick(v: View) {
        val id = v.getId()
        if (id == R.id.btn_set_bg_color) {
            mNoteBgColorSelector!!.setVisibility(View.VISIBLE)
            findViewById<View?>(sBgSelectorSelectionMap.get(mWorkingNote!!.getBgColorId())!!).setVisibility(
                -View.VISIBLE
            )
        } else if (sBgSelectorBtnsMap.containsKey(id)) {
            findViewById<View?>(sBgSelectorSelectionMap.get(mWorkingNote!!.getBgColorId())!!).setVisibility(
                View.GONE
            )
            mWorkingNote!!.setBgColorId(sBgSelectorBtnsMap.get(id)!!)
            mNoteBgColorSelector!!.setVisibility(View.GONE)
        } else if (sFontSizeBtnsMap.containsKey(id)) {
            findViewById<View?>(sFontSelectorSelectionMap.get(mFontSizeId)!!).setVisibility(View.GONE)
            mFontSizeId = sFontSizeBtnsMap.get(id)!!
            mSharedPrefs!!.edit().putInt(PREFERENCE_FONT_SIZE, mFontSizeId).commit()
            findViewById<View?>(sFontSelectorSelectionMap.get(mFontSizeId)!!).setVisibility(View.VISIBLE)
            if (mWorkingNote!!.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
                this.workingText
                switchToListMode(mWorkingNote!!.getContent())
            } else {
                mNoteEditor!!.setTextAppearance(
                    this,
                    TextAppearanceResources.getTexAppearanceResource(mFontSizeId)
                )
            }
            mFontSizeSelector!!.setVisibility(View.GONE)
        }
    }

    override fun onBackPressed() {
        if (clearSettingState()) {
            return
        }

        saveNote()
        super.onBackPressed()
    }

    private fun clearSettingState(): Boolean {
        if (mNoteBgColorSelector!!.getVisibility() == View.VISIBLE) {
            mNoteBgColorSelector!!.setVisibility(View.GONE)
            return true
        } else if (mFontSizeSelector!!.getVisibility() == View.VISIBLE) {
            mFontSizeSelector!!.setVisibility(View.GONE)
            return true
        }
        return false
    }

    override fun onBackgroundColorChanged() {
        findViewById<View?>(sBgSelectorSelectionMap.get(mWorkingNote!!.getBgColorId())!!).setVisibility(
            View.VISIBLE
        )
        mNoteEditorPanel!!.setBackgroundResource(mWorkingNote!!.getBgColorResId())
        mHeadViewPanel!!.setBackgroundResource(mWorkingNote!!.getTitleBgResId())
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return buildOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        return buildOptionsMenu(menu)
    }

    private fun buildOptionsMenu(menu: Menu): Boolean {
        if (isFinishing()) {
            return true
        }
        clearSettingState()
        menu.clear()
        if (mWorkingNote!!.getFolderId() == Notes.ID_CALL_RECORD_FOLDER.toLong()) {
            getMenuInflater().inflate(R.menu.call_note_edit, menu)
        } else {
            getMenuInflater().inflate(R.menu.note_edit, menu)
        }
        if (mWorkingNote!!.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_normal_mode)
        } else {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_list_mode)
        }
        if (mWorkingNote!!.hasClockAlert()) {
            menu.findItem(R.id.menu_alert).setVisible(false)
        } else {
            menu.findItem(R.id.menu_delete_remind).setVisible(false)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.getItemId()
        if (itemId == R.id.menu_new_note) {
            createNewNote()
        } else if (itemId == R.id.menu_delete) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle(getString(R.string.alert_title_delete))
            builder.setIcon(android.R.drawable.ic_dialog_alert)
            builder.setMessage(getString(R.string.alert_message_delete_note))
            builder.setPositiveButton(
                android.R.string.ok,
                object : DialogInterface.OnClickListener {
                    override fun onClick(dialog: DialogInterface?, which: Int) {
                        setResult(RESULT_OK)
                        deleteCurrentNote()
                        finish()
                    }
                })
            builder.setNegativeButton(android.R.string.cancel, null)
            builder.show()
        } else if (itemId == R.id.menu_font_size) {
            mFontSizeSelector!!.setVisibility(View.VISIBLE)
            findViewById<View?>(sFontSelectorSelectionMap.get(mFontSizeId)!!).setVisibility(View.VISIBLE)
        } else if (itemId == R.id.menu_list_mode) {
            mWorkingNote!!.setCheckListMode(if (mWorkingNote!!.getCheckListMode() == 0) TextNote.MODE_CHECK_LIST else 0)
        } else if (itemId == R.id.menu_share) {
            shareCurrentNote()
        } else if (itemId == R.id.menu_export_as_txt) {
            exportCurrentNoteAsTxt()
        } else if (itemId == R.id.menu_generate_long_image) {
            saveCurrentNoteAsLongImage()
        } else if (itemId == R.id.menu_send_to_desktop) {
            sendToDesktop()
        } else if (itemId == R.id.menu_alert) {
            setReminder()
        } else if (itemId == R.id.menu_delete_remind) {
            mWorkingNote!!.setAlertDate(0, false)
        }
        return true
    }

    private fun setReminder() {
        val d = DateTimePickerDialog(this, System.currentTimeMillis())
        d.setOnDateTimeSetListener(object : OnDateTimeSetListener {
            override fun OnDateTimeSet(dialog: AlertDialog?, date: Long) {
                mWorkingNote!!.setAlertDate(date, true)
            }
        })
        d.show()
    }

    /**
     * Share note to apps that support [Intent.ACTION_SEND] action
     * and {@text/plain} type
     */
    private fun shareCurrentNote() {
        sendTo(this, this.currentNoteTextForShare)
    }

    private fun sendTo(context: Context, info: String?) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.putExtra(Intent.EXTRA_TEXT, info)
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
        intent.setType("text/plain")
        try {
            context.startActivity(
                Intent.createChooser(
                    intent,
                    getString(R.string.share_note_chooser_title)
                )
            )
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Share note failed", e)
            showToast(R.string.error_no_share_app)
        }
    }

    private fun exportCurrentNoteAsTxt() {
        ensureStoragePermissionAndRun(STORAGE_ACTION_EXPORT_AS_TXT)
    }

    private fun saveCurrentNoteAsLongImage() {
        ensureStoragePermissionAndRun(STORAGE_ACTION_SAVE_LONG_IMAGE)
    }

    private fun ensureStoragePermissionAndRun(storageAction: Int) {
        if (TextUtils.isEmpty(this.currentNoteTextForExport.trim { it <= ' ' })) {
            showToast(R.string.error_note_empty_for_export)
            return
        }

        if (Environment.MEDIA_MOUNTED != Environment.getExternalStorageState()) {
            showToast(R.string.error_sdcard_unmounted)
            return
        }

        mPendingStorageAction = storageAction
        if (needsWriteStoragePermission()) {
            requestWriteStoragePermission()
            return
        }

        handlePendingStorageAction()
    }

    private fun handlePendingStorageAction() {
        val storageAction = mPendingStorageAction
        mPendingStorageAction = STORAGE_ACTION_NONE
        when (storageAction) {
            STORAGE_ACTION_EXPORT_AS_TXT -> doExportCurrentNoteAsTxt()
            STORAGE_ACTION_SAVE_LONG_IMAGE -> doSaveCurrentNoteAsLongImage()
            else -> {}
        }
    }

    private fun doExportCurrentNoteAsTxt() {
        val noteText = this.currentNoteTextForExport
        object : AsyncTask<Void?, Void?, Boolean?>() {
            override fun doInBackground(vararg params: Void?): Boolean {
                return writeNoteTextToDownload(noteText)
            }

            override fun onPostExecute(exported: Boolean) {
                showToast(if (exported) R.string.success_sdcard_export else R.string.error_sdcard_export)
            }
        }.execute()
    }

    private fun needsWriteStoragePermission(): Boolean {
        return Build.VERSION.SDK_INT >= RUNTIME_PERMISSION_API_LEVEL
                && (checkCallingOrSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
    }

    private fun requestWriteStoragePermission() {
        try {
            val requestPermissions = Activity::class.java.getMethod(
                "requestPermissions",
                Array<String>::class.java, Integer.TYPE
            )
            requestPermissions.invoke(
                this, *arrayOf<Any>(
                    arrayOf<String>(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    REQUEST_CODE_WRITE_EXTERNAL_STORAGE
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Request storage permission failed", e)
            mPendingStorageAction = STORAGE_ACTION_NONE
            showToast(R.string.error_storage_permission_denied)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>?,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_WRITE_EXTERNAL_STORAGE) {
            if (grantResults.size > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                handlePendingStorageAction()
            } else {
                mPendingStorageAction = STORAGE_ACTION_NONE
                showToast(R.string.error_storage_permission_denied)
            }
        }
    }

    private fun createNewNote() {
        // Firstly, save current editing notes
        saveNote()

        // For safety, start a new NoteEditActivity
        finish()
        val intent = Intent(this, NoteEditActivity::class.java)
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT)
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mWorkingNote!!.getFolderId())
        startActivity(intent)
    }

    private fun deleteCurrentNote() {
        if (mWorkingNote!!.existInDatabase()) {
            val ids = HashSet<Long?>()
            val id = mWorkingNote!!.getNoteId()
            if (id != Notes.ID_ROOT_FOLDER.toLong()) {
                ids.add(id)
            } else {
                Log.d(TAG, "Wrong note id, should not happen")
            }
            if (!this.isSyncMode) {
                if (!DataUtils.batchDeleteNotes(getContentResolver(), ids)) {
                    Log.e(TAG, "Delete Note error")
                }
            } else {
                if (!DataUtils.batchMoveToFolder(
                        getContentResolver(),
                        ids,
                        Notes.ID_TRASH_FOLER.toLong()
                    )
                ) {
                    Log.e(TAG, "Move notes to trash folder error, should not happens")
                }
            }
        }
        mWorkingNote!!.markDeleted(true)
    }

    private val isSyncMode: Boolean
        get() = NotesPreferenceActivity.Companion.getSyncAccountName(this)
            .trim { it <= ' ' }.length > 0

    override fun onClockAlertChanged(date: Long, set: Boolean) {
        /**
         * User could set clock to an unsaved note, so before setting the
         * alert clock, we should save the note first
         */
        if (!mWorkingNote!!.existInDatabase()) {
            saveNote()
        }
        if (mWorkingNote!!.getNoteId() > 0) {
            val intent = Intent(this, AlarmReceiver::class.java)
            intent.setData(
                ContentUris.withAppendedId(
                    Notes.CONTENT_NOTE_URI,
                    mWorkingNote!!.getNoteId()
                )
            )
            val pendingIntent = PendingIntent.getBroadcast(
                this, 0, intent,
                PendingIntentCompat.immutableFlag()
            )
            val alarmManager = (getSystemService(ALARM_SERVICE) as AlarmManager)
            showAlertHeader()
            if (!set) {
                alarmManager.cancel(pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, date, pendingIntent)
            }
        } else {
            /**
             * There is the condition that user has input nothing (the note is
             * not worthy saving), we have no note id, remind the user that he
             * should input something
             */
            Log.e(TAG, "Clock alert setting error")
            showToast(R.string.error_note_empty_for_clock)
        }
    }

    override fun onWidgetChanged() {
        updateWidget()
    }

    override fun onEditTextDelete(index: Int, text: String?) {
        val childCount = mEditTextList!!.getChildCount()
        if (childCount == 1) {
            return
        }

        for (i in index + 1..<childCount) {
            (mEditTextList!!.getChildAt(i).findViewById<View?>(R.id.et_edit_text) as NoteEditText)
                .setIndex(i - 1)
        }

        mEditTextList!!.removeViewAt(index)
        var edit: NoteEditText? = null
        if (index == 0) {
            edit = mEditTextList!!.getChildAt(0).findViewById<View?>(
                R.id.et_edit_text
            ) as NoteEditText?
        } else {
            edit = mEditTextList!!.getChildAt(index - 1).findViewById<View?>(
                R.id.et_edit_text
            ) as NoteEditText?
        }
        val length = edit!!.length()
        edit.append(text)
        edit.requestFocus()
        edit.setSelection(length)
    }

    override fun onEditTextEnter(index: Int, text: String) {
        /**
         * Should not happen, check for debug
         */
        if (index > mEditTextList!!.getChildCount()) {
            Log.e(TAG, "Index out of mEditTextList boundrary, should not happen")
        }

        val view = getListItem(text, index)
        mEditTextList!!.addView(view, index)
        val edit = view.findViewById<View?>(R.id.et_edit_text) as NoteEditText
        edit.requestFocus()
        edit.setSelection(0)
        for (i in index + 1..<mEditTextList!!.getChildCount()) {
            (mEditTextList!!.getChildAt(i).findViewById<View?>(R.id.et_edit_text) as NoteEditText)
                .setIndex(i)
        }
    }

    private fun switchToListMode(text: String) {
        mEditTextList!!.removeAllViews()
        val items: Array<String?> =
            text.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var index = 0
        for (item in items) {
            if (!TextUtils.isEmpty(item)) {
                mEditTextList!!.addView(getListItem(item!!, index))
                index++
            }
        }
        mEditTextList!!.addView(getListItem("", index))
        mEditTextList!!.getChildAt(index).findViewById<View?>(R.id.et_edit_text).requestFocus()

        mNoteEditor!!.setVisibility(View.GONE)
        mEditTextList!!.setVisibility(View.VISIBLE)
    }

    private fun getHighlightQueryResult(fullText: String?, userQuery: String?): Spannable {
        val spannable = SpannableString(if (fullText == null) "" else fullText)
        if (!TextUtils.isEmpty(userQuery)) {
            mPattern = Pattern.compile(userQuery)
            val m = mPattern!!.matcher(fullText)
            var start = 0
            while (m.find(start)) {
                spannable.setSpan(
                    BackgroundColorSpan(
                        this.getResources().getColor(
                            R.color.user_query_highlight
                        )
                    ), m.start(), m.end(),
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE
                )
                start = m.end()
            }
        }
        return spannable
    }

    private fun getListItem(item: String, index: Int): View {
        var item = item
        val view = LayoutInflater.from(this).inflate(R.layout.note_edit_list_item, null)
        val edit = view.findViewById<View?>(R.id.et_edit_text) as NoteEditText
        edit.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId))
        val cb = (view.findViewById<View?>(R.id.cb_edit_item) as CheckBox)
        cb.setOnCheckedChangeListener(object : CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
                if (isChecked) {
                    edit.setPaintFlags(edit.getPaintFlags() or Paint.STRIKE_THRU_TEXT_FLAG)
                } else {
                    edit.setPaintFlags(Paint.ANTI_ALIAS_FLAG or Paint.DEV_KERN_TEXT_FLAG)
                }
            }
        })

        if (item.startsWith(TAG_CHECKED)) {
            cb.setChecked(true)
            edit.setPaintFlags(edit.getPaintFlags() or Paint.STRIKE_THRU_TEXT_FLAG)
            item = item.substring(TAG_CHECKED.length, item.length).trim { it <= ' ' }
        } else if (item.startsWith(TAG_UNCHECKED)) {
            cb.setChecked(false)
            edit.setPaintFlags(Paint.ANTI_ALIAS_FLAG or Paint.DEV_KERN_TEXT_FLAG)
            item = item.substring(TAG_UNCHECKED.length, item.length).trim { it <= ' ' }
        }

        edit.setOnTextViewChangeListener(this)
        edit.setIndex(index)
        edit.setText(getHighlightQueryResult(item, mUserQuery))
        return view
    }

    override fun onTextChange(index: Int, hasText: Boolean) {
        if (index >= mEditTextList!!.getChildCount()) {
            Log.e(TAG, "Wrong index, should not happen")
            return
        }
        if (hasText) {
            mEditTextList!!.getChildAt(index).findViewById<View?>(R.id.cb_edit_item).setVisibility(
                View.VISIBLE
            )
        } else {
            mEditTextList!!.getChildAt(index).findViewById<View?>(R.id.cb_edit_item).setVisibility(
                View.GONE
            )
        }
    }

    override fun onCheckListModeChanged(oldMode: Int, newMode: Int) {
        if (newMode == TextNote.MODE_CHECK_LIST) {
            switchToListMode(mNoteEditor!!.getText().toString())
        } else {
            if (!this.workingText) {
                mWorkingNote!!.setWorkingText(
                    mWorkingNote!!.getContent().replace(
                        TAG_UNCHECKED + " ",
                        ""
                    )
                )
            }
            mNoteEditor!!.setText(getHighlightQueryResult(mWorkingNote!!.getContent(), mUserQuery))
            mEditTextList!!.setVisibility(View.GONE)
            mNoteEditor!!.setVisibility(View.VISIBLE)
        }
    }

    private val workingText: Boolean
        get() {
            var hasChecked = false
            if (mWorkingNote!!.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
                val sb = StringBuilder()
                for (i in 0..<mEditTextList!!.getChildCount()) {
                    val view = mEditTextList!!.getChildAt(i)
                    val edit =
                        view.findViewById<View?>(R.id.et_edit_text) as NoteEditText
                    if (!TextUtils.isEmpty(edit.getText())) {
                        if ((view.findViewById<View?>(R.id.cb_edit_item) as CheckBox).isChecked()) {
                            sb.append(TAG_CHECKED).append(" ")
                                .append(edit.getText()).append("\n")
                            hasChecked = true
                        } else {
                            sb.append(TAG_UNCHECKED).append(" ")
                                .append(edit.getText()).append("\n")
                        }
                    }
                }
                mWorkingNote!!.setWorkingText(sb.toString())
            } else {
                mWorkingNote!!.setWorkingText(mNoteEditor!!.getText().toString())
            }
            return hasChecked
        }

    private val currentNoteTextForExport: String
        get() {
            if (mWorkingNote!!.getCheckListMode() != TextNote.MODE_CHECK_LIST) {
                return mNoteEditor!!.getText().toString()
            }

            val sb = StringBuilder()
            for (i in 0..<mEditTextList!!.getChildCount()) {
                val view = mEditTextList!!.getChildAt(i)
                val edit =
                    view.findViewById<View?>(R.id.et_edit_text) as NoteEditText
                val itemText: CharSequence? = edit.getText()
                if (!TextUtils.isEmpty(itemText)) {
                    if (sb.length > 0) {
                        sb.append('\n')
                    }
                    sb.append(itemText)
                }
            }
            return sb.toString()
        }

    private val currentNoteTextForShare: String
        get() {
            if (mWorkingNote!!.getCheckListMode() != TextNote.MODE_CHECK_LIST) {
                return mNoteEditor!!.getText().toString()
            }
            return this.currentNoteTextForLongImage
        }

    private val currentNoteTextForLongImage: String
        get() {
            if (mWorkingNote!!.getCheckListMode() != TextNote.MODE_CHECK_LIST) {
                return mNoteEditor!!.getText().toString()
            }

            val sb = StringBuilder()
            for (i in 0..<mEditTextList!!.getChildCount()) {
                val view = mEditTextList!!.getChildAt(i)
                val edit =
                    view.findViewById<View?>(R.id.et_edit_text) as NoteEditText
                val itemText: CharSequence? = edit.getText()
                if (!TextUtils.isEmpty(itemText)) {
                    if (sb.length > 0) {
                        sb.append('\n')
                    }
                    sb.append(
                        if ((view.findViewById<View?>(R.id.cb_edit_item) as CheckBox).isChecked())
                            TAG_CHECKED
                        else
                            TAG_UNCHECKED
                    )
                        .append(" ")
                        .append(itemText)
                }
            }
            return sb.toString()
        }

    private fun doSaveCurrentNoteAsLongImage() {
        val noteText = this.currentNoteTextForLongImage
        val textSize = this.currentEditorTextSize
        val backgroundResId = mWorkingNote!!.getBgColorResId()
        val imageWidth = this.longImageWidth

        object : AsyncTask<Void?, Void?, File?>() {
            override fun doInBackground(vararg params: Void?): File? {
                val bitmap = createLongImageBitmap(
                    noteText, textSize, backgroundResId,
                    imageWidth
                )
                return saveLongImageBitmap(bitmap)
            }

            override fun onPostExecute(imageFile: File?) {
                if (imageFile != null) {
                    notifyGallery(imageFile)
                    showToast(R.string.success_long_image_saved)
                } else {
                    showToast(R.string.error_long_image_save_failed)
                }
            }
        }.execute()
    }

    private val currentEditorTextSize: Float
        get() {
            if (mWorkingNote!!.getCheckListMode() == TextNote.MODE_CHECK_LIST
                && mEditTextList!!.getChildCount() > 0
            ) {
                val edit =
                    mEditTextList!!.getChildAt(0).findViewById<View?>(
                        R.id.et_edit_text
                    ) as NoteEditText?
                if (edit != null && edit.getTextSize() > 0) {
                    return edit.getTextSize()
                }
            }
            return if (mNoteEditor!!.getTextSize() > 0) mNoteEditor!!.getTextSize() else spToPx(18)
        }

    private val longImageWidth: Int
        get() {
            val width = getResources().getDisplayMetrics().widthPixels
            return if (width > 0) width else dpToPx(360)
        }

    private fun createLongImageBitmap(
        noteText: String?, textSize: Float, backgroundResId: Int,
        imageWidth: Int
    ): Bitmap? {
        try {
            val content: String = (if (TextUtils.isEmpty(noteText)) " " else noteText)!!
            val horizontalPadding = dpToPx(LONG_IMAGE_HORIZONTAL_PADDING_DP)
            val verticalPadding = dpToPx(LONG_IMAGE_VERTICAL_PADDING_DP)
            val contentWidth = max(imageWidth - horizontalPadding * 2, dpToPx(160))

            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
            textPaint.setColor(Color.rgb(51, 51, 51))
            textPaint.setTextSize(if (textSize > 0) textSize else spToPx(18))

            val layout = StaticLayout(
                content, textPaint, contentWidth,
                Layout.Alignment.ALIGN_NORMAL, 1.4f, dpToPx(4).toFloat(), false
            )
            val imageHeight = max(
                layout.getHeight() + verticalPadding * 2,
                dpToPx(LONG_IMAGE_MIN_HEIGHT_DP)
            )

            val bitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawLongImageBackground(canvas, backgroundResId, imageWidth, imageHeight)
            canvas.save()
            canvas.translate(horizontalPadding.toFloat(), verticalPadding.toFloat())
            layout.draw(canvas)
            canvas.restore()
            return bitmap
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Create long image bitmap failed", e)
            return null
        }
    }

    private fun drawLongImageBackground(
        canvas: Canvas, backgroundResId: Int, width: Int,
        height: Int
    ) {
        try {
            val background = getResources().getDrawable(backgroundResId)
            if (background != null) {
                background.setBounds(0, 0, width, height)
                background.draw(canvas)
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Draw long image background failed", e)
        }
        canvas.drawColor(Color.WHITE)
    }

    private fun saveLongImageBitmap(bitmap: Bitmap?): File? {
        if (bitmap == null) {
            return null
        }

        val imageFile = createLongImageFile()
        if (imageFile == null) {
            bitmap.recycle()
            return null
        }

        var fos: FileOutputStream? = null
        try {
            fos = FileOutputStream(imageFile)
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)) {
                imageFile.delete()
                return null
            }
            fos.flush()
            return imageFile
        } catch (e: IOException) {
            Log.e(TAG, "Save long image bitmap failed", e)
            imageFile.delete()
            return null
        } finally {
            bitmap.recycle()
            if (fos != null) {
                try {
                    fos.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Close image output stream failed", e)
                }
            }
        }
    }

    private fun createLongImageFile(): File? {
        val picturesDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES
        )
        if (picturesDir == null) {
            Log.e(TAG, "Pictures directory is null")
            return null
        }

        val notesDir = File(picturesDir, "Notes")
        if (!notesDir.exists() && !notesDir.mkdirs()) {
            Log.e(TAG, "Create image export directory failed: " + notesDir.getAbsolutePath())
            return null
        }

        val imageFile = File(
            notesDir, ("note_long_image_"
                    + DateFormat.format("yyyyMMdd_HHmmss", System.currentTimeMillis()) + ".png")
        )
        try {
            if (!imageFile.exists() && !imageFile.createNewFile()) {
                Log.e(TAG, "Create image file failed: " + imageFile.getAbsolutePath())
                return null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Create image file failed", e)
            return null
        }
        return imageFile
    }

    private fun notifyGallery(imageFile: File?) {
        val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        intent.setData(Uri.fromFile(imageFile))
        sendBroadcast(intent)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * getResources().getDisplayMetrics().density + 0.5f).toInt()
    }

    private fun spToPx(sp: Int): Float {
        return sp * getResources().getDisplayMetrics().scaledDensity
    }

    private fun writeNoteTextToDownload(noteText: String?): Boolean {
        val exportFile = createTxtExportFile()
        if (exportFile == null) {
            return false
        }

        var writer: BufferedWriter? = null
        try {
            writer = BufferedWriter(
                OutputStreamWriter(
                    FileOutputStream(exportFile), "UTF-8"
                )
            )
            writer.write(if (noteText == null) "" else noteText)
            writer.flush()
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Export note as txt failed", e)
            return false
        } finally {
            if (writer != null) {
                try {
                    writer.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Close txt writer failed", e)
                }
            }
        }
    }

    private fun createTxtExportFile(): File? {
        val downloadDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        if (downloadDir == null) {
            Log.e(TAG, "Download directory is null")
            return null
        }

        if (!downloadDir.exists() && !downloadDir.mkdirs()) {
            Log.e(TAG, "Create download directory failed: " + downloadDir.getAbsolutePath())
            return null
        }

        val exportFile = File(
            downloadDir, ("note_"
                    + DateFormat.format("yyyyMMdd_HHmmss", System.currentTimeMillis()) + ".txt")
        )
        try {
            if (!exportFile.exists() && !exportFile.createNewFile()) {
                Log.e(TAG, "Create txt export file failed: " + exportFile.getAbsolutePath())
                return null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Create txt export file failed", e)
            return null
        }
        return exportFile
    }

    private fun saveNote(): Boolean {
        this.workingText
        val saved = mWorkingNote!!.saveNote()
        if (saved) {
            /**
             * There are two modes from List view to edit view, open one note,
             * create/edit a node. Opening node requires to the original
             * position in the list when back from edit view, while creating a
             * new node requires to the top of the list. This code
             * [.RESULT_OK] is used to identify the create/edit state
             */
            setResult(RESULT_OK)
        }
        return saved
    }

    private fun sendToDesktop() {
        /**
         * Before send message to home, we should make sure that current
         * editing note is exists in databases. So, for new note, firstly
         * save it
         */
        if (!mWorkingNote!!.existInDatabase()) {
            saveNote()
        }

        if (mWorkingNote!!.getNoteId() > 0) {
            val sender = Intent()
            val shortcutIntent = Intent(this, NoteEditActivity::class.java)
            shortcutIntent.setAction(Intent.ACTION_VIEW)
            shortcutIntent.putExtra(Intent.EXTRA_UID, mWorkingNote!!.getNoteId())
            sender.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
            sender.putExtra(
                Intent.EXTRA_SHORTCUT_NAME,
                makeShortcutIconTitle(mWorkingNote!!.getContent())
            )
            sender.putExtra(
                Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(this, R.drawable.icon_app)
            )
            sender.putExtra("duplicate", true)
            sender.setAction("com.android.launcher.action.INSTALL_SHORTCUT")
            showToast(R.string.info_note_enter_desktop)
            sendBroadcast(sender)
        } else {
            /**
             * There is the condition that user has input nothing (the note is
             * not worthy saving), we have no note id, remind the user that he
             * should input something
             */
            Log.e(TAG, "Send to desktop error")
            showToast(R.string.error_note_empty_for_send_to_desktop)
        }
    }

    private fun makeShortcutIconTitle(content: String): String {
        var content = content
        content = content.replace(TAG_CHECKED, "")
        content = content.replace(TAG_UNCHECKED, "")
        return if (content.length > SHORTCUT_ICON_TITLE_MAX_LEN) content.substring(
            0,
            SHORTCUT_ICON_TITLE_MAX_LEN
        ) else content
    }

    private fun showToast(resId: Int, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, resId, duration).show()
    }

    companion object {
        private val sBgSelectorBtnsMap: MutableMap<Int?, Int?> = HashMap<Int?, Int?>()

        init {
            sBgSelectorBtnsMap.put(R.id.iv_bg_yellow, ResourceParser.YELLOW)
            sBgSelectorBtnsMap.put(R.id.iv_bg_red, ResourceParser.RED)
            sBgSelectorBtnsMap.put(R.id.iv_bg_blue, ResourceParser.BLUE)
            sBgSelectorBtnsMap.put(R.id.iv_bg_green, ResourceParser.GREEN)
            sBgSelectorBtnsMap.put(R.id.iv_bg_white, ResourceParser.WHITE)
        }

        private val sBgSelectorSelectionMap: MutableMap<Int?, Int?> = HashMap<Int?, Int?>()

        init {
            sBgSelectorSelectionMap.put(ResourceParser.YELLOW, R.id.iv_bg_yellow_select)
            sBgSelectorSelectionMap.put(ResourceParser.RED, R.id.iv_bg_red_select)
            sBgSelectorSelectionMap.put(ResourceParser.BLUE, R.id.iv_bg_blue_select)
            sBgSelectorSelectionMap.put(ResourceParser.GREEN, R.id.iv_bg_green_select)
            sBgSelectorSelectionMap.put(ResourceParser.WHITE, R.id.iv_bg_white_select)
        }

        private val sFontSizeBtnsMap: MutableMap<Int?, Int?> = HashMap<Int?, Int?>()

        init {
            sFontSizeBtnsMap.put(R.id.ll_font_large, ResourceParser.TEXT_LARGE)
            sFontSizeBtnsMap.put(R.id.ll_font_small, ResourceParser.TEXT_SMALL)
            sFontSizeBtnsMap.put(R.id.ll_font_normal, ResourceParser.TEXT_MEDIUM)
            sFontSizeBtnsMap.put(R.id.ll_font_super, ResourceParser.TEXT_SUPER)
        }

        private val sFontSelectorSelectionMap: MutableMap<Int?, Int?> = HashMap<Int?, Int?>()

        init {
            sFontSelectorSelectionMap.put(ResourceParser.TEXT_LARGE, R.id.iv_large_select)
            sFontSelectorSelectionMap.put(ResourceParser.TEXT_SMALL, R.id.iv_small_select)
            sFontSelectorSelectionMap.put(ResourceParser.TEXT_MEDIUM, R.id.iv_medium_select)
            sFontSelectorSelectionMap.put(ResourceParser.TEXT_SUPER, R.id.iv_super_select)
        }

        private const val TAG = "NoteEditActivity"

        private const val PREFERENCE_FONT_SIZE = "pref_font_size"

        private const val SHORTCUT_ICON_TITLE_MAX_LEN = 10
        private const val REQUEST_CODE_WRITE_EXTERNAL_STORAGE = 1001
        private const val RUNTIME_PERMISSION_API_LEVEL = 23
        private const val STORAGE_ACTION_NONE = 0
        private const val STORAGE_ACTION_EXPORT_AS_TXT = 1
        private const val STORAGE_ACTION_SAVE_LONG_IMAGE = 2
        private const val LONG_IMAGE_HORIZONTAL_PADDING_DP = 24
        private const val LONG_IMAGE_VERTICAL_PADDING_DP = 32
        private const val LONG_IMAGE_MIN_HEIGHT_DP = 240

        val TAG_CHECKED: String = '\u221A'.toString()
        val TAG_UNCHECKED: String = '\u25A1'.toString()
    }
}
