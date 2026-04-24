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
import net.micode.notes.R
import kotlin.random.Random

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

    fun getDefaultBgId(context: Context): Int {
        return if (NotesPreferences.isRandomBackgroundEnabled(context)) {
            Random.nextInt(NoteBgResources.resourcesSize)
        } else {
            NotesPreferences.getDefaultBackgroundColor(context)
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
            return BG_EDIT_RESOURCES[safeIndex(id, BG_EDIT_RESOURCES.size)]
        }

        fun getNoteTitleBgResource(id: Int): Int {
            return BG_EDIT_TITLE_RESOURCES[safeIndex(id, BG_EDIT_TITLE_RESOURCES.size)]
        }

        val resourcesSize: Int
            get() = BG_EDIT_RESOURCES.size
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
            return BG_2X_RESOURCES[safeIndex(id, BG_2X_RESOURCES.size)]
        }

        private val BG_4X_RESOURCES = intArrayOf(
            R.drawable.widget_4x_yellow,
            R.drawable.widget_4x_blue,
            R.drawable.widget_4x_white,
            R.drawable.widget_4x_green,
            R.drawable.widget_4x_red
        )

        fun getWidget4xBgResource(id: Int): Int {
            return BG_4X_RESOURCES[safeIndex(id, BG_4X_RESOURCES.size)]
        }
    }

    private fun safeIndex(id: Int, size: Int): Int {
        return if (id in 0 until size) id else BG_DEFAULT_COLOR
    }
}
