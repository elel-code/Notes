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
package net.micode.notes.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import net.micode.notes.R
import net.micode.notes.data.Notes
import net.micode.notes.tool.ResourceParser.WidgetBgResources

class NoteWidgetProvider_2x : NoteWidgetProvider() {
    override fun onUpdate(
        context: Context?,
        appWidgetManager: AppWidgetManager?,
        appWidgetIds: IntArray?
    ) {
        super.update(context, appWidgetManager, appWidgetIds)
    }

    override fun getLayoutId(): Int {
        return R.layout.widget_2x
    }

    override fun getBgResourceId(bgId: Int): Int {
        return WidgetBgResources.getWidget2xBgResource(bgId)
    }

    override fun getWidgetType(): Int {
        return Notes.TYPE_WIDGET_2X
    }
}
