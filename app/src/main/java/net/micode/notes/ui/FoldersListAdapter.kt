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
import android.view.View
import android.view.ViewGroup
import android.widget.CursorAdapter
import android.widget.LinearLayout
import android.widget.TextView
import net.micode.notes.R
import net.micode.notes.data.Notes
import net.micode.notes.data.Notes.NoteColumns

class FoldersListAdapter(context: Context?, c: Cursor?) : CursorAdapter(context, c) {
    override fun newView(context: Context?, cursor: Cursor?, parent: ViewGroup?): View {
        return FolderListItem(context)
    }

    override fun bindView(view: View?, context: Context, cursor: Cursor) {
        if (view is FolderListItem) {
            val folderName = if (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER.toLong()) context
                .getString(R.string.menu_move_parent_folder) else cursor.getString(NAME_COLUMN)
            view.bind(folderName)
        }
    }

    fun getFolderName(context: Context, position: Int): String? {
        val cursor = getItem(position) as Cursor
        return if (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER.toLong()) context
            .getString(R.string.menu_move_parent_folder) else cursor.getString(NAME_COLUMN)
    }

    private inner class FolderListItem(context: Context?) : LinearLayout(context) {
        private val mName: TextView

        init {
            inflate(context, R.layout.folder_list_item, this)
            mName = findViewById<View?>(R.id.tv_folder_name) as TextView
        }

        fun bind(name: String?) {
            mName.setText(name)
        }
    }

    companion object {
        val PROJECTION: Array<String?> = arrayOf<String?>(
            NoteColumns.Companion.ID,
            NoteColumns.Companion.SNIPPET
        )

        const val ID_COLUMN: Int = 0
        const val NAME_COLUMN: Int = 1
    }
}
