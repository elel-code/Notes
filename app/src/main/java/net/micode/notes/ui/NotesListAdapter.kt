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

import android.content.Context
import android.database.Cursor
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.CursorAdapter
import net.micode.notes.data.Notes

class NotesListAdapter(private val mContext: Context?) : CursorAdapter(mContext, null) {
    private val mSelectedIndex: HashMap<Int, Boolean?>
    private var mNotesCount = 0
    var isInChoiceMode: Boolean = false
        private set
    private var mSearchText: String? = ""

    class AppWidgetAttribute {
        var widgetId: Int = 0
        var widgetType: Int = 0
    }

    init {
        mSelectedIndex = HashMap<Int, Boolean?>()
    }

    override fun newView(context: Context?, cursor: Cursor?, parent: ViewGroup?): View {
        return NotesListItem(context)
    }

    override fun bindView(view: View?, context: Context?, cursor: Cursor) {
        if (view is NotesListItem) {
            val itemData = NoteItemData(context, cursor)
            view.bind(
                context, itemData, this.isInChoiceMode,
                isSelectedItem(cursor.getPosition()), mSearchText
            )
        }
    }

    fun setSearchText(searchText: String?) {
        mSearchText = searchText
    }

    fun setCheckedItem(position: Int, checked: Boolean) {
        mSelectedIndex.put(position, checked)
        notifyDataSetChanged()
    }

    fun setChoiceMode(mode: Boolean) {
        mSelectedIndex.clear()
        this.isInChoiceMode = mode
    }

    fun selectAll(checked: Boolean) {
        val cursor = getCursor()
        for (i in 0..<getCount()) {
            if (cursor.moveToPosition(i)) {
                if (NoteItemData.Companion.getNoteType(cursor) == Notes.TYPE_NOTE) {
                    setCheckedItem(i, checked)
                }
            }
        }
    }

    val selectedItemIds: HashSet<Long?>
        get() {
            val itemSet = java.util.HashSet<Long?>()
            for (position in mSelectedIndex.keys) {
                if (mSelectedIndex.get(position) == true) {
                    val id = getItemId(position)
                    if (id == Notes.ID_ROOT_FOLDER.toLong()) {
                        Log.d(
                            TAG,
                            "Wrong item id, should not happen"
                        )
                    } else {
                        itemSet.add(id)
                    }
                }
            }

            return itemSet
        }

    val selectedWidget: HashSet<AppWidgetAttribute?>?
        get() {
            val itemSet =
                java.util.HashSet<AppWidgetAttribute?>()
            for (position in mSelectedIndex.keys) {
                if (mSelectedIndex.get(position) == true) {
                    val c = getItem(position) as Cursor?
                    if (c != null) {
                        val widget = AppWidgetAttribute()
                        val item = NoteItemData(mContext, c)
                        widget.widgetId = item.getWidgetId()
                        widget.widgetType = item.getWidgetType()
                        itemSet.add(widget)
                        /**
                         * Don't close cursor here, only the adapter could close it
                         */
                    } else {
                        Log.e(TAG, "Invalid cursor")
                        return null
                    }
                }
            }
            return itemSet
        }

    val selectedCount: Int
        get() {
            val values =
                mSelectedIndex.values
            if (null == values) {
                return 0
            }
            val iter = values.iterator()
            var count = 0
            while (iter.hasNext()) {
                if (true == iter.next()) {
                    count++
                }
            }
            return count
        }

    val isAllSelected: Boolean
        get() {
            val checkedCount = this.selectedCount
            return (checkedCount != 0 && checkedCount == mNotesCount)
        }

    fun isSelectedItem(position: Int): Boolean {
        if (null == mSelectedIndex.get(position)) {
            return false
        }
        return mSelectedIndex.get(position)!!
    }

    override fun onContentChanged() {
        super.onContentChanged()
        calcNotesCount()
    }

    override fun changeCursor(cursor: Cursor?) {
        super.changeCursor(cursor)
        calcNotesCount()
    }

    private fun calcNotesCount() {
        mNotesCount = 0
        for (i in 0..<getCount()) {
            val c = getItem(i) as Cursor?
            if (c != null) {
                if (NoteItemData.Companion.getNoteType(c) == Notes.TYPE_NOTE) {
                    mNotesCount++
                }
            } else {
                Log.e(TAG, "Invalid cursor")
                return
            }
        }
    }

    companion object {
        private const val TAG = "NotesListAdapter"
    }
}
