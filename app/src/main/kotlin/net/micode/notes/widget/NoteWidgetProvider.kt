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
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.micode.notes.R
import net.micode.notes.data.Notes
import net.micode.notes.data.Notes.NoteColumns
import net.micode.notes.tool.ResourceParser
import net.micode.notes.ui.AppWidgetAttribute
import net.micode.notes.ui.NoteEditActivity

internal data class NoteWidgetLayoutSpec(
    val topPadding: androidx.compose.ui.unit.Dp,
    val maxLines: Int
)

private data class NoteWidgetModel(
    val backgroundResId: Int,
    val text: String,
    val intent: Intent
)

internal abstract class NoteGlanceWidget(
    private val widgetType: Int,
    private val layoutSpec: NoteWidgetLayoutSpec
) : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val model = withContext(Dispatchers.IO) {
            loadWidgetModel(context, appWidgetId, widgetType)
        }

        provideContent {
            NoteWidgetContent(
                model = model,
                layoutSpec = layoutSpec
            )
        }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        clearWidgetBinding(context, appWidgetId)
        super.onDelete(context, glanceId)
    }

    protected abstract fun getBackgroundResource(bgId: Int): Int

    private fun loadWidgetModel(
        context: Context,
        appWidgetId: Int,
        widgetType: Int
    ): NoteWidgetModel {
        var bgId = ResourceParser.getDefaultBgId(context)
        val intent = Intent(context, NoteEditActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(Notes.INTENT_EXTRA_WIDGET_ID, appWidgetId)
            putExtra(Notes.INTENT_EXTRA_WIDGET_TYPE, widgetType)
        }

        val snippet = context.contentResolver.query(
            Notes.CONTENT_NOTE_URI,
            PROJECTION,
            "${NoteColumns.Companion.WIDGET_ID}=? AND ${NoteColumns.Companion.PARENT_ID}<>?",
            arrayOf(appWidgetId.toString(), Notes.ID_TRASH_FOLER.toString()),
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return@use null
            }

            if (cursor.count > 1) {
                Log.e(TAG, "Multiple notes found with widget id: $appWidgetId")
            }

            bgId = cursor.getInt(COLUMN_BG_COLOR_ID)
            intent.putExtra(Intent.EXTRA_UID, cursor.getLong(COLUMN_ID))
            intent.action = Intent.ACTION_VIEW
            cursor.getString(COLUMN_SNIPPET).orEmpty()
        } ?: run {
            intent.action = Intent.ACTION_INSERT_OR_EDIT
            context.getString(R.string.widget_havenot_content)
        }

        intent.putExtra(Notes.INTENT_EXTRA_BACKGROUND_ID, bgId)

        return NoteWidgetModel(
            backgroundResId = getBackgroundResource(bgId),
            text = snippet,
            intent = intent
        )
    }

    companion object {
        private val PROJECTION: Array<String> = arrayOf(
            NoteColumns.Companion.ID,
            NoteColumns.Companion.BG_COLOR_ID,
            NoteColumns.Companion.SNIPPET
        )

        private const val COLUMN_ID = 0
        private const val COLUMN_BG_COLOR_ID = 1
        private const val COLUMN_SNIPPET = 2
        private const val TAG = "NoteWidgetProvider"

        private fun clearWidgetBinding(context: Context, appWidgetId: Int) {
            val values = ContentValues().apply {
                put(NoteColumns.Companion.WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            }
            context.contentResolver.update(
                Notes.CONTENT_NOTE_URI,
                values,
                "${NoteColumns.Companion.WIDGET_ID}=?",
                arrayOf(appWidgetId.toString())
            )
        }
    }
}

internal object NoteWidget2x : NoteGlanceWidget(
    widgetType = Notes.TYPE_WIDGET_2X,
    layoutSpec = NoteWidgetLayoutSpec(topPadding = 20.dp, maxLines = 6)
) {
    override fun getBackgroundResource(bgId: Int): Int {
        return ResourceParser.WidgetBgResources.getWidget2xBgResource(bgId)
    }
}

internal object NoteWidget4x : NoteGlanceWidget(
    widgetType = Notes.TYPE_WIDGET_4X,
    layoutSpec = NoteWidgetLayoutSpec(topPadding = 40.dp, maxLines = 12)
) {
    override fun getBackgroundResource(bgId: Int): Int {
        return ResourceParser.WidgetBgResources.getWidget4xBgResource(bgId)
    }
}

object NoteWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun updateWidget(context: Context, appWidgetId: Int, widgetType: Int) {
        scope.launch {
            updateWidgetInternal(context.applicationContext, appWidgetId, widgetType)
        }
    }

    fun updateWidgets(context: Context, widgets: Set<AppWidgetAttribute>) {
        scope.launch {
            widgets
                .filter {
                    it.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID &&
                        it.widgetType != Notes.TYPE_WIDGET_INVALIDE
                }
                .forEach { widget ->
                    updateWidgetInternal(
                        context = context.applicationContext,
                        appWidgetId = widget.widgetId,
                        widgetType = widget.widgetType
                    )
                }
        }
    }

    private suspend fun updateWidgetInternal(context: Context, appWidgetId: Int, widgetType: Int) {
        val widget = when (widgetType) {
            Notes.TYPE_WIDGET_2X -> NoteWidget2x
            Notes.TYPE_WIDGET_4X -> NoteWidget4x
            else -> {
                Log.e(TAG, "Unsupported widget type: $widgetType")
                return
            }
        }

        val glanceId = runCatching {
            GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        }.getOrElse { error ->
            Log.e(TAG, "Unable to find GlanceId for widget id $appWidgetId", error)
            return
        }

        widget.update(context, glanceId)
    }

    private const val TAG = "NoteWidgetUpdater"
}

@Composable
private fun NoteWidgetContent(
    model: NoteWidgetModel,
    layoutSpec: NoteWidgetLayoutSpec
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity(model.intent))
    ) {
        Image(
            provider = ImageProvider(model.backgroundResId),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize()
        )
        Text(
            text = model.text,
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(
                    top = layoutSpec.topPadding,
                    start = 15.dp,
                    end = 15.dp
                ),
            maxLines = layoutSpec.maxLines,
            style = TextStyle(
                color = ColorProvider(Color(0xFF663300)),
                fontSize = 14.sp
            )
        )
    }
}
