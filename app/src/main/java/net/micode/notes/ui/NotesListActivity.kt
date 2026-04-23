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

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.appwidget.AppWidgetManager
import android.content.AsyncQueryHandler
import android.content.ContentResolver
import android.content.ContentValues
import android.content.DialogInterface
import android.content.Intent
import android.database.Cursor
import android.os.AsyncTask
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.ActionMode
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.OnCreateContextMenuListener
import android.view.View.OnTouchListener
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView.MultiChoiceModeListener
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import net.micode.notes.R
import net.micode.notes.data.Notes
import net.micode.notes.data.Notes.NoteColumns
import net.micode.notes.gtask.remote.GTaskSyncService
import net.micode.notes.model.WorkingNote
import net.micode.notes.tool.DataUtils
import net.micode.notes.tool.ResourceParser
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute
import net.micode.notes.widget.NoteWidgetProvider_2x
import net.micode.notes.widget.NoteWidgetProvider_4x
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

class NotesListActivity : Activity(), View.OnClickListener, OnItemLongClickListener {
    private enum class ListEditState {
        NOTE_LIST, SUB_FOLDER, CALL_RECORD_FOLDER
    }

    private class NoteListQueryCookie(
        var folderId: Long, var searchText: String?, var selection: String?,
        var selectionArgs: Array<String?>?
    )

    private var mState: ListEditState? = null

    private var mBackgroundQueryHandler: BackgroundQueryHandler? = null

    private var mNotesListAdapter: NotesListAdapter? = null

    private var mNotesListView: ListView? = null

    private var mAddNewNote: Button? = null

    private var mDispatch = false

    private var mOriginY = 0

    private var mDispatchY = 0

    private var mTitleBar: TextView? = null

    private var mFooterTitle: TextView? = null

    private var mFooterSummary: TextView? = null

    private var mSearchEditText: EditText? = null

    private var mCurrentFolderId: Long = 0

    private var mContentResolver: ContentResolver? = null

    private var mModeCallBack: ModeCallback? = null

    private var mLastNoteListQueryCookie: NoteListQueryCookie? = null

    private var mFooterItemCount = 0

    private var mFocusNoteDataItem: NoteItemData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.note_list)
        initResources()

        /**
         * Insert an introduction when user firstly use this application
         */
        setAppInfoFromRawRes()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK
            && (requestCode == REQUEST_CODE_OPEN_NODE || requestCode == REQUEST_CODE_NEW_NODE)
        ) {
            mNotesListAdapter!!.changeCursor(null)
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun setAppInfoFromRawRes() {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        if (!sp.getBoolean(PREFERENCE_ADD_INTRODUCTION, false)) {
            val sb = StringBuilder()
            var `in`: InputStream? = null
            try {
                `in` = getResources().openRawResource(R.raw.introduction)
                if (`in` != null) {
                    val isr = InputStreamReader(`in`)
                    val br = BufferedReader(isr)
                    val buf = CharArray(1024)
                    var len = 0
                    while ((br.read(buf).also { len = it }) > 0) {
                        sb.append(buf, 0, len)
                    }
                } else {
                    Log.e(TAG, "Read introduction file error")
                    return
                }
            } catch (e: IOException) {
                e.printStackTrace()
                return
            } finally {
                if (`in` != null) {
                    try {
                        `in`.close()
                    } catch (e: IOException) {
                        // TODO Auto-generated catch block
                        e.printStackTrace()
                    }
                }
            }

            val note: WorkingNote = WorkingNote.Companion.createEmptyNote(
                this, Notes.ID_ROOT_FOLDER.toLong(),
                AppWidgetManager.INVALID_APPWIDGET_ID, Notes.TYPE_WIDGET_INVALIDE,
                ResourceParser.RED
            )
            note.setWorkingText(sb.toString())
            if (note.saveNote()) {
                sp.edit().putBoolean(PREFERENCE_ADD_INTRODUCTION, true).commit()
            } else {
                Log.e(TAG, "Save introduction note error")
                return
            }
        }
    }

    override fun onStart() {
        super.onStart()
        startAsyncNotesListQuery()
    }

    private fun initResources() {
        mContentResolver = this.getContentResolver()
        mBackgroundQueryHandler = BackgroundQueryHandler(this.getContentResolver())
        mCurrentFolderId = Notes.ID_ROOT_FOLDER.toLong()
        mNotesListView = findViewById<View?>(R.id.notes_list) as ListView?
        val footerView = LayoutInflater.from(this).inflate(R.layout.note_list_footer, null)
        mFooterTitle = footerView.findViewById<View?>(R.id.tv_footer_title) as TextView?
        mFooterSummary = footerView.findViewById<View?>(R.id.tv_footer_summary) as TextView?
        mNotesListView!!.addFooterView(footerView, null, false)
        mNotesListView!!.setOnItemClickListener(OnListItemClickListener())
        mNotesListView!!.setOnItemLongClickListener(this)
        mNotesListAdapter = NotesListAdapter(this)
        mNotesListView!!.setAdapter(mNotesListAdapter)
        mAddNewNote = findViewById<View?>(R.id.btn_new_note) as Button?
        mAddNewNote!!.setOnClickListener(this)
        mAddNewNote!!.setOnTouchListener(NewNoteOnTouchListener())
        mDispatch = false
        mDispatchY = 0
        mOriginY = 0
        mModeCallBack = ModeCallback()
        mTitleBar = findViewById<View?>(R.id.tv_title_bar) as TextView
        mSearchEditText = findViewById<View?>(R.id.et_search) as EditText?
        mSearchEditText!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Do nothing
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (mNotesListAdapter!!.isInChoiceMode()) {
                    mModeCallBack!!.finishActionMode()
                }
                startAsyncNotesListQuery()
            }

            override fun afterTextChanged(s: Editable?) {
                // Do nothing
            }
        })
        mSearchEditText!!.clearFocus()
        mNotesListView!!.requestFocus()
        mState = ListEditState.NOTE_LIST
        updateFooterInfo(0)
    }

    private inner class ModeCallback : MultiChoiceModeListener, MenuItem.OnMenuItemClickListener {
        private var mDropDownMenu: DropdownMenu? = null
        private var mActionMode: ActionMode? = null
        private var mMoveMenu: MenuItem? = null

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            getMenuInflater().inflate(R.menu.note_list_options, menu)
            menu.findItem(R.id.delete).setOnMenuItemClickListener(this)
            mMoveMenu = menu.findItem(R.id.move)
            if (mFocusNoteDataItem!!.getParentId() == Notes.ID_CALL_RECORD_FOLDER.toLong()
                || DataUtils.getUserFolderCount(mContentResolver) == 0
            ) {
                mMoveMenu!!.setVisible(false)
            } else {
                mMoveMenu!!.setVisible(true)
                mMoveMenu!!.setOnMenuItemClickListener(this)
            }
            mActionMode = mode
            mNotesListAdapter!!.setChoiceMode(true)
            mNotesListView!!.setLongClickable(false)
            mAddNewNote!!.setVisibility(View.GONE)
            updateFooterInfo(mFooterItemCount)

            val customView = LayoutInflater.from(this@NotesListActivity).inflate(
                R.layout.note_list_dropdown_menu, null
            )
            mode.setCustomView(customView)
            mDropDownMenu = DropdownMenu(
                this@NotesListActivity,
                customView.findViewById<View?>(R.id.selection_menu) as Button?,
                R.menu.note_list_dropdown
            )
            mDropDownMenu!!.setOnDropdownMenuItemClickListener(object :
                PopupMenu.OnMenuItemClickListener {
                override fun onMenuItemClick(item: MenuItem?): Boolean {
                    mNotesListAdapter!!.selectAll(!mNotesListAdapter!!.isAllSelected())
                    updateMenu()
                    return true
                }
            })
            return true
        }

        fun updateMenu() {
            val selectedCount = mNotesListAdapter!!.getSelectedCount()
            // Update dropdown menu
            val format = getResources().getString(R.string.menu_select_title, selectedCount)
            mDropDownMenu!!.setTitle(format)
            val item = mDropDownMenu!!.findItem(R.id.action_select_all)
            if (item != null) {
                if (mNotesListAdapter!!.isAllSelected()) {
                    item.setChecked(true)
                    item.setTitle(R.string.menu_deselect_all)
                } else {
                    item.setChecked(false)
                    item.setTitle(R.string.menu_select_all)
                }
            }
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            // TODO Auto-generated method stub
            return false
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            // TODO Auto-generated method stub
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            mNotesListAdapter!!.setChoiceMode(false)
            mNotesListView!!.setLongClickable(true)
            mAddNewNote!!.setVisibility(View.VISIBLE)
            mActionMode = null
            updateFooterInfo(mFooterItemCount)
        }

        fun finishActionMode() {
            if (mActionMode != null) {
                mActionMode!!.finish()
            }
        }

        override fun onItemCheckedStateChanged(
            mode: ActionMode?, position: Int, id: Long,
            checked: Boolean
        ) {
            mNotesListAdapter!!.setCheckedItem(position, checked)
            updateMenu()
        }

        override fun onMenuItemClick(item: MenuItem): Boolean {
            if (mNotesListAdapter!!.getSelectedCount() == 0) {
                Toast.makeText(
                    this@NotesListActivity, getString(R.string.menu_select_none),
                    Toast.LENGTH_SHORT
                ).show()
                return true
            }

            val itemId = item.getItemId()
            if (itemId == R.id.delete) {
                val builder = AlertDialog.Builder(this@NotesListActivity)
                builder.setTitle(getString(R.string.alert_title_delete))
                builder.setIcon(android.R.drawable.ic_dialog_alert)
                builder.setMessage(
                    getString(
                        R.string.alert_message_delete_notes,
                        mNotesListAdapter!!.getSelectedCount()
                    )
                )
                builder.setPositiveButton(
                    android.R.string.ok,
                    object : DialogInterface.OnClickListener {
                        override fun onClick(
                            dialog: DialogInterface?,
                            which: Int
                        ) {
                            batchDelete()
                        }
                    })
                builder.setNegativeButton(android.R.string.cancel, null)
                builder.show()
            } else if (itemId == R.id.move) {
                startQueryDestinationFolders()
            } else {
                return false
            }
            return true
        }
    }

    private inner class NewNoteOnTouchListener : OnTouchListener {
        override fun onTouch(v: View?, event: MotionEvent): Boolean {
            when (event.getAction()) {
                MotionEvent.ACTION_DOWN -> {
                    val display = getWindowManager().getDefaultDisplay()
                    val screenHeight = display.getHeight()
                    val newNoteViewHeight = mAddNewNote!!.getHeight()
                    var start = screenHeight - newNoteViewHeight
                    var eventY = start + event.getY().toInt()
                    /**
                     * Minus TitleBar's height
                     */
                    if (mState == ListEditState.SUB_FOLDER) {
                        eventY -= mTitleBar!!.getHeight()
                        start -= mTitleBar!!.getHeight()
                    }
                    /**
                     * HACKME:When click the transparent part of "New Note" button, dispatch
                     * the event to the list view behind this button. The transparent part of
                     * "New Note" button could be expressed by formula y=-0.12x+94（Unit:pixel）
                     * and the line top of the button. The coordinate based on left of the "New
                     * Note" button. The 94 represents maximum height of the transparent part.
                     * Notice that, if the background of the button changes, the formula should
                     * also change. This is very bad, just for the UI designer's strong requirement.
                     */
                    if (event.getY() < (event.getX() * (-0.12) + 94)) {
                        val view = mNotesListView!!.getChildAt(
                            (mNotesListView!!.getChildCount() - 1
                                    - mNotesListView!!.getFooterViewsCount())
                        )
                        if (view != null && view.getBottom() > start && (view.getTop() < (start + 94))) {
                            mOriginY = event.getY().toInt()
                            mDispatchY = eventY
                            event.setLocation(event.getX(), mDispatchY.toFloat())
                            mDispatch = true
                            return mNotesListView!!.dispatchTouchEvent(event)
                        }
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (mDispatch) {
                        mDispatchY += event.getY().toInt() - mOriginY
                        event.setLocation(event.getX(), mDispatchY.toFloat())
                        return mNotesListView!!.dispatchTouchEvent(event)
                    }
                }

                else -> {
                    if (mDispatch) {
                        event.setLocation(event.getX(), mDispatchY.toFloat())
                        mDispatch = false
                        return mNotesListView!!.dispatchTouchEvent(event)
                    }
                }
            }
            return false
        }
    }

    private fun startAsyncNotesListQuery() {
        val searchText = this.searchText
        if (!TextUtils.isEmpty(searchText)) {
            startAsyncNotesListQuery(
                NoteColumns.Companion.SNIPPET + " LIKE ?",
                arrayOf<String>(
                    "%" + searchText + "%"
                )
            )
        } else {
            startAsyncNotesListQuery(null, null)
        }
    }

    private fun startAsyncNotesListQuery(
        extraSelection: String?,
        extraSelectionArgs: Array<String?>?
    ) {
        val searchText = this.searchText
        var selection: String = if (mCurrentFolderId == Notes.ID_ROOT_FOLDER.toLong())
            ROOT_FOLDER_SELECTION
        else
            NORMAL_SELECTION
        var selectionArgs: Array<String?>? = arrayOf<String>(
            mCurrentFolderId.toString()
        )

        if (!TextUtils.isEmpty(extraSelection)) {
            selection = "(" + selection + ") AND (" + extraSelection + ")"
            selectionArgs = appendSelectionArgs(selectionArgs, extraSelectionArgs)
        }

        val queryCookie = NoteListQueryCookie(
            mCurrentFolderId, searchText,
            selection, selectionArgs
        )
        mLastNoteListQueryCookie = queryCookie
        mBackgroundQueryHandler!!.cancelOperation(FOLDER_NOTE_LIST_QUERY_TOKEN)
        mBackgroundQueryHandler!!.startQuery(
            FOLDER_NOTE_LIST_QUERY_TOKEN, queryCookie,
            Notes.CONTENT_NOTE_URI, NoteItemData.Companion.PROJECTION, selection, selectionArgs,
            NoteColumns.Companion.TYPE + " DESC," + NoteColumns.Companion.MODIFIED_DATE + " DESC"
        )
    }

    private inner class BackgroundQueryHandler(contentResolver: ContentResolver?) :
        AsyncQueryHandler(contentResolver) {
        override fun onQueryComplete(token: Int, cookie: Any?, cursor: Cursor?) {
            when (token) {
                FOLDER_NOTE_LIST_QUERY_TOKEN -> {
                    if (isStaleQuery(cookie as NoteListQueryCookie?)) {
                        if (cursor != null) {
                            cursor.close()
                        }
                        return
                    }
                    mNotesListAdapter!!.setSearchText((cookie as NoteListQueryCookie).searchText)
                    mNotesListAdapter!!.changeCursor(cursor)
                    updateFooterInfo(if (cursor != null) cursor.getCount() else 0)
                }

                FOLDER_LIST_QUERY_TOKEN -> if (cursor != null && cursor.getCount() > 0) {
                    showFolderListMenu(cursor)
                } else {
                    Log.e(TAG, "Query folder failed")
                }

                else -> return
            }
        }
    }

    private val searchText: String
        get() {
            if (mSearchEditText == null || mSearchEditText!!.getText() == null) {
                return ""
            }
            return mSearchEditText!!.getText().toString().trim { it <= ' ' }
        }

    private fun updateFooterInfo(itemCount: Int) {
        mFooterItemCount = itemCount
        if (mFooterTitle == null || mFooterSummary == null) {
            return
        }

        if (mState == ListEditState.CALL_RECORD_FOLDER) {
            if (itemCount > 0) {
                mFooterTitle!!.setText(getString(R.string.footer_call_notes_count, itemCount))
            } else {
                mFooterTitle!!.setText(R.string.footer_call_notes_empty)
            }
        } else {
            if (itemCount > 0) {
                mFooterTitle!!.setText(getString(R.string.footer_items_count, itemCount))
            } else {
                mFooterTitle!!.setText(R.string.footer_items_empty)
            }
        }

        mFooterSummary!!.setText(
            if (mAddNewNote != null && mAddNewNote!!.getVisibility() == View.VISIBLE)
                R.string.footer_hint_add_note
            else
                R.string.footer_hint_search
        )
    }

    private fun appendSelectionArgs(
        baseArgs: Array<String?>?,
        extraArgs: Array<String?>?
    ): Array<String?>? {
        if (extraArgs == null || extraArgs.size == 0) {
            return baseArgs
        }

        val baseLength = if (baseArgs != null) baseArgs.size else 0
        val result = arrayOfNulls<String>(baseLength + extraArgs.size)
        for (i in 0..<baseLength) {
            result[i] = baseArgs!![i]
        }
        for (i in extraArgs.indices) {
            result[baseLength + i] = extraArgs[i]
        }
        return result
    }

    private fun isStaleQuery(cookie: NoteListQueryCookie?): Boolean {
        if (cookie == null) {
            return true
        }
        if (mLastNoteListQueryCookie == null) {
            return true
        }
        return !TextUtils.equals(
            cookie.searchText,
            mLastNoteListQueryCookie!!.searchText
        ) || cookie.folderId != mLastNoteListQueryCookie!!.folderId || !TextUtils.equals(
            cookie.selection,
            mLastNoteListQueryCookie!!.selection
        ) || !isSameSelectionArgs(
            cookie.selectionArgs,
            mLastNoteListQueryCookie!!.selectionArgs
        )
    }

    private fun isSameSelectionArgs(first: Array<String?>?, second: Array<String?>?): Boolean {
        if (first == second) {
            return true
        }
        if (first == null || second == null) {
            return false
        }
        if (first.size != second.size) {
            return false
        }
        for (i in first.indices) {
            if (!TextUtils.equals(first[i], second[i])) {
                return false
            }
        }
        return true
    }

    private fun focusSearchInput() {
        if (mSearchEditText == null) {
            return
        }
        mSearchEditText!!.requestFocus()
        mSearchEditText!!.setSelection(mSearchEditText!!.length())
        val inputMethodManager = getSystemService(
            INPUT_METHOD_SERVICE
        ) as InputMethodManager?
        if (inputMethodManager != null) {
            inputMethodManager.showSoftInput(mSearchEditText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun showFolderListMenu(cursor: Cursor?) {
        val builder = AlertDialog.Builder(this@NotesListActivity)
        builder.setTitle(R.string.menu_title_select_folder)
        val adapter = FoldersListAdapter(this, cursor)
        builder.setAdapter(adapter, object : DialogInterface.OnClickListener {
            override fun onClick(dialog: DialogInterface?, which: Int) {
                DataUtils.batchMoveToFolder(
                    mContentResolver,
                    mNotesListAdapter!!.getSelectedItemIds(), adapter.getItemId(which)
                )
                Toast.makeText(
                    this@NotesListActivity,
                    getString(
                        R.string.format_move_notes_to_folder,
                        mNotesListAdapter!!.getSelectedCount(),
                        adapter.getFolderName(this@NotesListActivity, which)
                    ),
                    Toast.LENGTH_SHORT
                ).show()
                mModeCallBack!!.finishActionMode()
            }
        })
        builder.show()
    }

    private fun createNewNote() {
        val intent = Intent(this, NoteEditActivity::class.java)
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT)
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mCurrentFolderId)
        this.startActivityForResult(intent, REQUEST_CODE_NEW_NODE)
    }

    private fun batchDelete() {
        object : AsyncTask<Void?, Void?, HashSet<AppWidgetAttribute?>?>() {
            override fun doInBackground(vararg unused: Void?): HashSet<AppWidgetAttribute?>? {
                val widgets = mNotesListAdapter!!.getSelectedWidget()
                if (!this.isSyncMode) {
                    // if not synced, delete notes directly
                    if (DataUtils.batchDeleteNotes(
                            mContentResolver, mNotesListAdapter!!
                                .getSelectedItemIds()
                        )
                    ) {
                    } else {
                        Log.e(TAG, "Delete notes error, should not happens")
                    }
                } else {
                    // in sync mode, we'll move the deleted note into the trash
                    // folder
                    if (!DataUtils.batchMoveToFolder(
                            mContentResolver, mNotesListAdapter!!
                                .getSelectedItemIds(), Notes.ID_TRASH_FOLER.toLong()
                        )
                    ) {
                        Log.e(TAG, "Move notes to trash folder error, should not happens")
                    }
                }
                return widgets
            }

            override fun onPostExecute(widgets: HashSet<AppWidgetAttribute>?) {
                if (widgets != null) {
                    for (widget in widgets) {
                        if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                            && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE
                        ) {
                            updateWidget(widget.widgetId, widget.widgetType)
                        }
                    }
                }
                mModeCallBack!!.finishActionMode()
            }
        }.execute()
    }

    private fun deleteFolder(folderId: Long) {
        if (folderId == Notes.ID_ROOT_FOLDER.toLong()) {
            Log.e(TAG, "Wrong folder id, should not happen " + folderId)
            return
        }

        val ids = HashSet<Long?>()
        ids.add(folderId)
        val widgets = DataUtils.getFolderNoteWidget(
            mContentResolver,
            folderId
        )
        if (!this.isSyncMode) {
            // if not synced, delete folder directly
            DataUtils.batchDeleteNotes(mContentResolver, ids)
        } else {
            // in sync mode, we'll move the deleted folder into the trash folder
            DataUtils.batchMoveToFolder(mContentResolver, ids, Notes.ID_TRASH_FOLER.toLong())
        }
        if (widgets != null) {
            for (widget in widgets) {
                if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                    && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE
                ) {
                    updateWidget(widget.widgetId, widget.widgetType)
                }
            }
        }
    }

    private fun openNode(data: NoteItemData) {
        val intent = Intent(this, NoteEditActivity::class.java)
        intent.setAction(Intent.ACTION_VIEW)
        intent.putExtra(Intent.EXTRA_UID, data.getId())
        this.startActivityForResult(intent, REQUEST_CODE_OPEN_NODE)
    }

    private fun openFolder(data: NoteItemData) {
        mCurrentFolderId = data.getId()
        startAsyncNotesListQuery()
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER.toLong()) {
            mState = ListEditState.CALL_RECORD_FOLDER
            mAddNewNote!!.setVisibility(View.GONE)
        } else {
            mState = ListEditState.SUB_FOLDER
        }
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER.toLong()) {
            mTitleBar!!.setText(R.string.call_record_folder_name)
        } else {
            mTitleBar!!.setText(data.getSnippet())
        }
        mTitleBar!!.setVisibility(View.VISIBLE)
    }

    override fun onClick(v: View) {
        if (v.getId() == R.id.btn_new_note) {
            createNewNote()
        }
    }

    private fun showSoftInput() {
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager?
        if (inputMethodManager != null) {
            inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
        }
    }

    private fun hideSoftInput(view: View) {
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0)
    }

    private fun showCreateOrModifyFolderDialog(create: Boolean) {
        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_text, null)
        val etName = view.findViewById<View?>(R.id.et_foler_name) as EditText
        showSoftInput()
        if (!create) {
            if (mFocusNoteDataItem != null) {
                etName.setText(mFocusNoteDataItem!!.getSnippet())
                builder.setTitle(getString(R.string.menu_folder_change_name))
            } else {
                Log.e(TAG, "The long click data item is null")
                return
            }
        } else {
            etName.setText("")
            builder.setTitle(this.getString(R.string.menu_create_folder))
        }

        builder.setPositiveButton(android.R.string.ok, null)
        builder.setNegativeButton(
            android.R.string.cancel,
            object : DialogInterface.OnClickListener {
                override fun onClick(dialog: DialogInterface?, which: Int) {
                    hideSoftInput(etName)
                }
            })

        val dialog: Dialog = builder.setView(view).show()
        val positive = dialog.findViewById<View?>(android.R.id.button1) as Button
        positive.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                hideSoftInput(etName)
                val name = etName.getText().toString()
                if (DataUtils.checkVisibleFolderName(mContentResolver, name)) {
                    Toast.makeText(
                        this@NotesListActivity, getString(R.string.folder_exist, name),
                        Toast.LENGTH_LONG
                    ).show()
                    etName.setSelection(0, etName.length())
                    return
                }
                if (!create) {
                    if (!TextUtils.isEmpty(name)) {
                        val values = ContentValues()
                        values.put(NoteColumns.Companion.SNIPPET, name)
                        values.put(NoteColumns.Companion.TYPE, Notes.TYPE_FOLDER)
                        values.put(NoteColumns.Companion.LOCAL_MODIFIED, 1)
                        mContentResolver!!.update(
                            Notes.CONTENT_NOTE_URI, values, NoteColumns.Companion.ID
                                    + "=?", arrayOf<String>(
                                mFocusNoteDataItem!!.getId().toString()
                            )
                        )
                    }
                } else if (!TextUtils.isEmpty(name)) {
                    val values = ContentValues()
                    values.put(NoteColumns.Companion.SNIPPET, name)
                    values.put(NoteColumns.Companion.TYPE, Notes.TYPE_FOLDER)
                    mContentResolver!!.insert(Notes.CONTENT_NOTE_URI, values)
                }
                dialog.dismiss()
            }
        })

        if (TextUtils.isEmpty(etName.getText())) {
            positive.setEnabled(false)
        }
        /**
         * When the name edit text is null, disable the positive button
         */
        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // TODO Auto-generated method stub
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (TextUtils.isEmpty(etName.getText())) {
                    positive.setEnabled(false)
                } else {
                    positive.setEnabled(true)
                }
            }

            override fun afterTextChanged(s: Editable?) {
                // TODO Auto-generated method stub
            }
        })
    }

    override fun onBackPressed() {
        when (mState) {
            ListEditState.SUB_FOLDER -> {
                mCurrentFolderId = Notes.ID_ROOT_FOLDER.toLong()
                mState = ListEditState.NOTE_LIST
                startAsyncNotesListQuery()
                mTitleBar!!.setVisibility(View.GONE)
            }

            ListEditState.CALL_RECORD_FOLDER -> {
                mCurrentFolderId = Notes.ID_ROOT_FOLDER.toLong()
                mState = ListEditState.NOTE_LIST
                mAddNewNote!!.setVisibility(View.VISIBLE)
                mTitleBar!!.setVisibility(View.GONE)
                startAsyncNotesListQuery()
            }

            ListEditState.NOTE_LIST -> super.onBackPressed()
            else -> {}
        }
    }

    private fun updateWidget(appWidgetId: Int, appWidgetType: Int) {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
        if (appWidgetType == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x::class.java)
        } else if (appWidgetType == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x::class.java)
        } else {
            Log.e(TAG, "Unspported widget type")
            return
        }

        intent.putExtra(
            AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(
                appWidgetId
            )
        )

        sendBroadcast(intent)
        setResult(RESULT_OK, intent)
    }

    private val mFolderOnCreateContextMenuListener: OnCreateContextMenuListener =
        object : OnCreateContextMenuListener {
            override fun onCreateContextMenu(
                menu: ContextMenu,
                v: View?,
                menuInfo: ContextMenuInfo?
            ) {
                if (mFocusNoteDataItem != null) {
                    menu.setHeaderTitle(mFocusNoteDataItem!!.getSnippet())
                    menu.add(0, MENU_FOLDER_VIEW, 0, R.string.menu_folder_view)
                    menu.add(0, MENU_FOLDER_DELETE, 0, R.string.menu_folder_delete)
                    menu.add(0, MENU_FOLDER_CHANGE_NAME, 0, R.string.menu_folder_change_name)
                }
            }
        }

    override fun onContextMenuClosed(menu: Menu) {
        if (mNotesListView != null) {
            mNotesListView!!.setOnCreateContextMenuListener(null)
        }
        super.onContextMenuClosed(menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (mFocusNoteDataItem == null) {
            Log.e(TAG, "The long click data item is null")
            return false
        }
        when (item.getItemId()) {
            MENU_FOLDER_VIEW -> openFolder(mFocusNoteDataItem!!)
            MENU_FOLDER_DELETE -> {
                val builder = AlertDialog.Builder(this)
                builder.setTitle(getString(R.string.alert_title_delete))
                builder.setIcon(android.R.drawable.ic_dialog_alert)
                builder.setMessage(getString(R.string.alert_message_delete_folder))
                builder.setPositiveButton(
                    android.R.string.ok,
                    object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                            deleteFolder(mFocusNoteDataItem!!.getId())
                        }
                    })
                builder.setNegativeButton(android.R.string.cancel, null)
                builder.show()
            }

            MENU_FOLDER_CHANGE_NAME -> showCreateOrModifyFolderDialog(false)
            else -> {}
        }

        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return buildOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        return buildOptionsMenu(menu)
    }

    private fun buildOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        if (mState == ListEditState.NOTE_LIST) {
            getMenuInflater().inflate(R.menu.note_list, menu)
            // set sync or sync_cancel
            menu.findItem(R.id.menu_sync).setTitle(
                if (GTaskSyncService.Companion.isSyncing()) R.string.menu_sync_cancel else R.string.menu_sync
            )
        } else if (mState == ListEditState.SUB_FOLDER) {
            getMenuInflater().inflate(R.menu.sub_folder, menu)
        } else if (mState == ListEditState.CALL_RECORD_FOLDER) {
            getMenuInflater().inflate(R.menu.call_record_folder, menu)
        } else {
            Log.e(TAG, "Wrong state:" + mState)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.getItemId()
        if (itemId == R.id.menu_new_folder) {
            showCreateOrModifyFolderDialog(true)
        } else if (itemId == R.id.menu_sync) {
            if (this.isSyncMode) {
                if (TextUtils.equals(item.getTitle(), getString(R.string.menu_sync))) {
                    GTaskSyncService.Companion.startSync(this)
                } else {
                    GTaskSyncService.Companion.cancelSync(this)
                }
            } else {
                startPreferenceActivity()
            }
        } else if (itemId == R.id.menu_setting) {
            startPreferenceActivity()
        } else if (itemId == R.id.menu_new_note) {
            createNewNote()
        } else if (itemId == R.id.menu_search) {
            focusSearchInput()
        }
        return true
    }

    override fun onSearchRequested(): Boolean {
        focusSearchInput()
        return true
    }

    private val isSyncMode: Boolean
        get() = NotesPreferenceActivity.Companion.getSyncAccountName(this)
            .trim { it <= ' ' }.length > 0

    private fun startPreferenceActivity() {
        val from = if (getParent() != null) getParent() else this
        val intent = Intent(from, NotesPreferenceActivity::class.java)
        from.startActivityIfNeeded(intent, -1)
    }

    private inner class OnListItemClickListener : OnItemClickListener {
        override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            var position = position
            if (view is NotesListItem) {
                val item = view.getItemData()
                if (mNotesListAdapter!!.isInChoiceMode()) {
                    if (item.getType() == Notes.TYPE_NOTE) {
                        position = position - mNotesListView!!.getHeaderViewsCount()
                        mModeCallBack!!.onItemCheckedStateChanged(
                            null, position, id,
                            !mNotesListAdapter!!.isSelectedItem(position)
                        )
                    }
                    return
                }

                when (mState) {
                    ListEditState.NOTE_LIST -> if (item.getType() == Notes.TYPE_FOLDER
                        || item.getType() == Notes.TYPE_SYSTEM
                    ) {
                        openFolder(item)
                    } else if (item.getType() == Notes.TYPE_NOTE) {
                        openNode(item)
                    } else {
                        Log.e(TAG, "Wrong note type in NOTE_LIST")
                    }

                    ListEditState.SUB_FOLDER, ListEditState.CALL_RECORD_FOLDER -> if (item.getType() == Notes.TYPE_NOTE) {
                        openNode(item)
                    } else {
                        Log.e(TAG, "Wrong note type in SUB_FOLDER")
                    }

                    else -> {}
                }
            }
        }
    }

    private fun startQueryDestinationFolders() {
        var selection: String =
            NoteColumns.Companion.TYPE + "=? AND " + NoteColumns.Companion.PARENT_ID + "<>? AND " + NoteColumns.Companion.ID + "<>?"
        selection =
            if (mState == ListEditState.NOTE_LIST) selection else "(" + selection + ") OR (" + NoteColumns.Companion.ID + "=" + Notes.ID_ROOT_FOLDER + ")"

        mBackgroundQueryHandler!!.startQuery(
            FOLDER_LIST_QUERY_TOKEN,
            null,
            Notes.CONTENT_NOTE_URI,
            FoldersListAdapter.Companion.PROJECTION,
            selection,
            arrayOf<String>(
                Notes.TYPE_FOLDER.toString(),
                Notes.ID_TRASH_FOLER.toString(),
                mCurrentFolderId.toString()
            ),
            NoteColumns.Companion.MODIFIED_DATE + " DESC"
        )
    }

    override fun onItemLongClick(
        parent: AdapterView<*>?,
        view: View?,
        position: Int,
        id: Long
    ): Boolean {
        if (view is NotesListItem) {
            mFocusNoteDataItem = view.getItemData()
            if (mFocusNoteDataItem!!.getType() == Notes.TYPE_NOTE && !mNotesListAdapter!!.isInChoiceMode()) {
                if (mNotesListView!!.startActionMode(mModeCallBack) != null) {
                    mModeCallBack!!.onItemCheckedStateChanged(null, position, id, true)
                    mNotesListView!!.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                } else {
                    Log.e(TAG, "startActionMode fails")
                }
            } else if (mFocusNoteDataItem!!.getType() == Notes.TYPE_FOLDER) {
                mNotesListView!!.setOnCreateContextMenuListener(mFolderOnCreateContextMenuListener)
            }
        }
        return false
    }

    companion object {
        private const val FOLDER_NOTE_LIST_QUERY_TOKEN = 0

        private const val FOLDER_LIST_QUERY_TOKEN = 1

        private const val MENU_FOLDER_DELETE = 0

        private const val MENU_FOLDER_VIEW = 1

        private const val MENU_FOLDER_CHANGE_NAME = 2

        private const val PREFERENCE_ADD_INTRODUCTION = "net.micode.notes.introduction"

        private const val TAG = "NotesListActivity"

        const val NOTES_LISTVIEW_SCROLL_RATE: Int = 30

        private val NORMAL_SELECTION: String = NoteColumns.Companion.PARENT_ID + "=?"

        private val ROOT_FOLDER_SELECTION = ("(" + NoteColumns.Companion.TYPE + "<>"
                + Notes.TYPE_SYSTEM + " AND " + NoteColumns.Companion.PARENT_ID + "=?)" + " OR ("
                + NoteColumns.Companion.ID + "=" + Notes.ID_CALL_RECORD_FOLDER + " AND "
                + NoteColumns.Companion.NOTES_COUNT + ">0)")

        private const val REQUEST_CODE_OPEN_NODE = 102
        private const val REQUEST_CODE_NEW_NODE = 103
    }
}
