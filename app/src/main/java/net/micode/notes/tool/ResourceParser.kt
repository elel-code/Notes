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
package net.micode.notes.tool

import android.content.Context
import android.preference.PreferenceManager
import net.micode.notes.R
import net.micode.notes.ui.NotesPreferenceActivity

object ResourceParser {
    const val YELLOW: Int = 0
    const val BLUE: Int = 1
    const val WHITE: Int = 2
    const val GREEN: Int = 3
    const val RED: Int = 4

    val BG_DEFAULT_COLOR: Int = YELLOW

    const val TEXT_SMALL: Int = 0
    const val TEXT_MEDIUM: Int = 1
    const val TEXT_LARGE: Int = 2
    const val TEXT_SUPER: Int = 3

    val BG_DEFAULT_FONT_SIZE: Int = TEXT_MEDIUM

    fun getDefaultBgId(context: Context?): Int {
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                NotesPreferenceActivity.Companion.PREFERENCE_SET_BG_COLOR_KEY, false
            )
        ) {
            return (Math.random() * NoteBgResources.BG_EDIT_RESOURCES.size).toInt()
        } else {
            return BG_DEFAULT_COLOR
        }
    }

    object NoteBgResources {
        private val BG_EDIT_RESOURCES = intArrayOf(
            R.drawable.edit_yellow,
            R.drawable.edit_blue,
            R.drawable.edit_white,
            R.drawable.edit_green,
            R.drawable.edit_red
        )

        private val BG_EDIT_TITLE_RESOURCES = intArrayOf(
            R.drawable.edit_title_yellow,
            R.drawable.edit_title_blue,
            R.drawable.edit_title_white,
            R.drawable.edit_title_green,
            R.drawable.edit_title_red
        )

        fun getNoteBgResource(id: Int): Int {
            return BG_EDIT_RESOURCES[id]
        }

        fun getNoteTitleBgResource(id: Int): Int {
            return BG_EDIT_TITLE_RESOURCES[id]
        }
    }

    object NoteItemBgResources {
        private val BG_FIRST_RESOURCES = intArrayOf(
            R.drawable.list_yellow_up,
            R.drawable.list_blue_up,
            R.drawable.list_white_up,
            R.drawable.list_green_up,
            R.drawable.list_red_up
        )

        private val BG_NORMAL_RESOURCES = intArrayOf(
            R.drawable.list_yellow_middle,
            R.drawable.list_blue_middle,
            R.drawable.list_white_middle,
            R.drawable.list_green_middle,
            R.drawable.list_red_middle
        )

        private val BG_LAST_RESOURCES = intArrayOf(
            R.drawable.list_yellow_down,
            R.drawable.list_blue_down,
            R.drawable.list_white_down,
            R.drawable.list_green_down,
            R.drawable.list_red_down,
        )

        private val BG_SINGLE_RESOURCES = intArrayOf(
            R.drawable.list_yellow_single,
            R.drawable.list_blue_single,
            R.drawable.list_white_single,
            R.drawable.list_green_single,
            R.drawable.list_red_single
        )

        fun getNoteBgFirstRes(id: Int): Int {
            return BG_FIRST_RESOURCES[id]
        }

        fun getNoteBgLastRes(id: Int): Int {
            return BG_LAST_RESOURCES[id]
        }

        fun getNoteBgSingleRes(id: Int): Int {
            return BG_SINGLE_RESOURCES[id]
        }

        fun getNoteBgNormalRes(id: Int): Int {
            return BG_NORMAL_RESOURCES[id]
        }

        val folderBgRes: Int
            get() = R.drawable.list_folder
    }

    object WidgetBgResources {
        private val BG_2X_RESOURCES = intArrayOf(
            R.drawable.widget_2x_yellow,
            R.drawable.widget_2x_blue,
            R.drawable.widget_2x_white,
            R.drawable.widget_2x_green,
            R.drawable.widget_2x_red,
        )

        fun getWidget2xBgResource(id: Int): Int {
            return BG_2X_RESOURCES[id]
        }

        private val BG_4X_RESOURCES = intArrayOf(
            R.drawable.widget_4x_yellow,
            R.drawable.widget_4x_blue,
            R.drawable.widget_4x_white,
            R.drawable.widget_4x_green,
            R.drawable.widget_4x_red
        )

        fun getWidget4xBgResource(id: Int): Int {
            return BG_4X_RESOURCES[id]
        }
    }

    object TextAppearanceResources {
        private val TEXTAPPEARANCE_RESOURCES = intArrayOf(
            R.style.TextAppearanceNormal,
            R.style.TextAppearanceMedium,
            R.style.TextAppearanceLarge,
            R.style.TextAppearanceSuper
        )

        fun getTexAppearanceResource(id: Int): Int {
            /**
             * HACKME: Fix bug of store the resource id in shared preference.
             * The id may larger than the length of resources, in this case,
             * return the [ResourceParser.BG_DEFAULT_FONT_SIZE]
             */
            if (id >= TEXTAPPEARANCE_RESOURCES.size) {
                return BG_DEFAULT_FONT_SIZE
            }
            return TEXTAPPEARANCE_RESOURCES[id]
        }

        val resourcesSize: Int
            get() = TEXTAPPEARANCE_RESOURCES.size
    }
}
