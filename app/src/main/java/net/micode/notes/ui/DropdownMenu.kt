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
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.PopupMenu
import net.micode.notes.R

class DropdownMenu(context: Context?, private val mButton: Button, menuId: Int) {
    private val mPopupMenu: PopupMenu?
    private val mMenu: Menu

    init {
        mButton.setBackgroundResource(R.drawable.dropdown_icon)
        mPopupMenu = PopupMenu(context, mButton)
        mMenu = mPopupMenu.getMenu()
        mPopupMenu.getMenuInflater().inflate(menuId, mMenu)
        mButton.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                mPopupMenu.show()
            }
        })
    }

    fun setOnDropdownMenuItemClickListener(listener: PopupMenu.OnMenuItemClickListener?) {
        if (mPopupMenu != null) {
            mPopupMenu.setOnMenuItemClickListener(listener)
        }
    }

    fun findItem(id: Int): MenuItem? {
        return mMenu.findItem(id)
    }

    fun setTitle(title: CharSequence?) {
        mButton.setText(title)
    }
}
