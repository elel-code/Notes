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
import net.micode.notes.data.NotesRepository
import net.micode.notes.tool.ResourceParser
import net.micode.notes.ui.AppWidgetAttribute
import net.micode.notes.ui.NoteEditActivity

/**
 * 笔记小部件的布局规格参数
 *
 * @property topPadding 文本内容的顶部内边距
 * @property maxLines 文本显示的最大行数
 */
internal data class NoteWidgetLayoutSpec(
    val topPadding: androidx.compose.ui.unit.Dp,
    val maxLines: Int
)

/**
 * 笔记小部件的数据模型
 *
 * @property backgroundResId 背景图片的资源ID
 * @property text 要显示的笔记内容文本
 * @property intent 点击小部件时启动的Intent
 */
private data class NoteWidgetModel(
    val backgroundResId: Int,
    val text: String,
    val intent: Intent
)

/**
 * 笔记小部件的抽象基类，使用Glance框架实现
 *
 * 负责加载笔记数据、绑定背景资源、处理小部件点击事件以及清理资源。
 * 子类需要实现不同尺寸小部件的具体背景资源获取逻辑。
 *
 * @param widgetType 小部件类型（2x或4x）
 * @param layoutSpec 布局规格参数（内边距和最大行数）
 */
internal abstract class NoteGlanceWidget(
    private val widgetType: Int,
    private val layoutSpec: NoteWidgetLayoutSpec
) : GlanceAppWidget() {
    /**
     * 提供Glance小部件的内容
     *
     * 在IO线程中加载小部件数据模型，然后在Compose上下文中渲染UI。
     *
     * @param context 上下文对象
     * @param id Glance唯一标识符
     */
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 根据GlanceId获取实际的AppWidgetId
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        // 在IO线程中加载小部件数据
        val model = withContext(Dispatchers.IO) {
            loadWidgetModel(context, appWidgetId, widgetType)
        }

        // 提供Compose UI内容
        provideContent {
            NoteWidgetContent(
                model = model,
                layoutSpec = layoutSpec
            )
        }
    }

    /**
     * 小部件被删除时的回调
     *
     * 清理小部件与笔记的绑定关系，释放相关资源。
     *
     * @param context 上下文对象
     * @param glanceId 被删除小部件的Glance唯一标识符
     */
    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        clearWidgetBinding(context, appWidgetId)
        super.onDelete(context, glanceId)
    }

    /**
     * 根据背景ID获取对应的背景资源
     *
     * 由子类实现，为不同尺寸的小部件提供特定的背景资源映射。
     *
     * @param bgId 背景颜色ID
     * @return 背景图片的资源ID
     */
    protected abstract fun getBackgroundResource(bgId: Int): Int

    /**
     * 加载小部件的数据模型
     *
     * 从数据库中查询绑定到该小部件的笔记，提取笔记片段内容，
     * 并配置点击小部件时启动的Intent。
     *
     * @param context 上下文对象
     * @param appWidgetId 应用小部件ID
     * @param widgetType 小部件类型
     * @return 包含背景资源、文本内容和Intent的NoteWidgetModel
     */
    private suspend fun loadWidgetModel(
        context: Context,
        appWidgetId: Int,
        widgetType: Int
    ): NoteWidgetModel {
        val repository = NotesRepository(context.applicationContext)
        var bgId = ResourceParser.getDefaultBgId(context)  // 默认背景ID
        // 创建点击小部件时启动的Intent
        val intent = Intent(context, NoteEditActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(Notes.INTENT_EXTRA_WIDGET_ID, appWidgetId)
            putExtra(Notes.INTENT_EXTRA_WIDGET_TYPE, widgetType)
        }

        // 查询绑定到该小部件的笔记
        val notes = repository.getNotesByWidgetId(appWidgetId)
        val snippet = notes.firstOrNull()?.let { note ->
            if (notes.size > 1) {
                Log.e(TAG, "Multiple notes found with widget id: $appWidgetId")
            }

            bgId = note.bgColorId  // 使用笔记的实际背景色
            intent.putExtra(Intent.EXTRA_UID, note.id)
            intent.action = Intent.ACTION_VIEW  // 查看已有笔记
            note.snippet  // 返回笔记片段内容
        } ?: run {
            // 没有找到关联笔记时，显示提示文本并进入新建模式
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
        private const val TAG = "NoteWidgetProvider"

        /**
         * 清除小部件与笔记的绑定关系
         *
         * @param context 上下文对象
         * @param appWidgetId 要清除绑定的应用小部件ID
         */
        private suspend fun clearWidgetBinding(context: Context, appWidgetId: Int) {
            NotesRepository(context.applicationContext).clearWidgetBinding(
                appWidgetId = appWidgetId,
                invalidWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }
    }
}

/**
 * 2x尺寸的笔记小部件实现
 *
 * 支持最多6行文本，顶部内边距20dp，使用2x小部件专用的背景资源。
 */
internal object NoteWidget2x : NoteGlanceWidget(
    widgetType = Notes.TYPE_WIDGET_2X,
    layoutSpec = NoteWidgetLayoutSpec(topPadding = 20.dp, maxLines = 6)
) {
    override fun getBackgroundResource(bgId: Int): Int {
        return ResourceParser.WidgetBgResources.getWidget2xBgResource(bgId)
    }
}

/**
 * 4x尺寸的笔记小部件实现
 *
 * 支持最多12行文本，顶部内边距40dp，使用4x小部件专用的背景资源。
 */
internal object NoteWidget4x : NoteGlanceWidget(
    widgetType = Notes.TYPE_WIDGET_4X,
    layoutSpec = NoteWidgetLayoutSpec(topPadding = 40.dp, maxLines = 12)
) {
    override fun getBackgroundResource(bgId: Int): Int {
        return ResourceParser.WidgetBgResources.getWidget4xBgResource(bgId)
    }
}

/**
 * 笔记小部件更新器（单例对象）
 *
 * 提供手动或批量更新小部件内容的功能，支持异步更新操作。
 */
object NoteWidgetUpdater {
    // 协程作用域，使用IO调度器和SupervisorJob确保单个更新失败不影响其他更新
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 更新指定的小部件
     *
     * @param context 上下文对象
     * @param appWidgetId 要更新的应用小部件ID
     * @param widgetType 小部件类型（2x或4x）
     */
    fun updateWidget(context: Context, appWidgetId: Int, widgetType: Int) {
        scope.launch {
            updateWidgetInternal(context.applicationContext, appWidgetId, widgetType)
        }
    }

    /**
     * 批量更新多个小部件
     *
     * @param context 上下文对象
     * @param widgets 需要更新的小部件属性集合
     */
    fun updateWidgets(context: Context, widgets: Set<AppWidgetAttribute>) {
        scope.launch {
            widgets
                .filter {
                    // 过滤掉无效的小部件ID和类型
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

    /**
     * 内部更新逻辑
     *
     * 根据小部件类型获取对应的GlanceWidget实例，并执行更新操作。
     *
     * @param context 上下文对象
     * @param appWidgetId 应用小部件ID
     * @param widgetType 小部件类型
     */
    private suspend fun updateWidgetInternal(context: Context, appWidgetId: Int, widgetType: Int) {
        // 根据类型选择对应的小部件实现
        val widget = when (widgetType) {
            Notes.TYPE_WIDGET_2X -> NoteWidget2x
            Notes.TYPE_WIDGET_4X -> NoteWidget4x
            else -> {
                Log.e(TAG, "Unsupported widget type: $widgetType")
                return
            }
        }

        // 通过AppWidgetId获取GlanceId
        val glanceId = runCatching {
            GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        }.getOrElse { error ->
            Log.e(TAG, "Unable to find GlanceId for widget id $appWidgetId", error)
            return
        }

        // 执行小部件更新
        widget.update(context, glanceId)
    }

    private const val TAG = "NoteWidgetUpdater"
}

/**
 * 笔记小部件的Compose UI内容
 *
 * 渲染小部件的界面，包含背景图片和笔记文本内容。
 *
 * @param model 小部件数据模型
 * @param layoutSpec 布局规格参数
 */
@Composable
private fun NoteWidgetContent(
    model: NoteWidgetModel,
    layoutSpec: NoteWidgetLayoutSpec
) {
    // 最外层Box作为可点击容器
    Box(
        modifier = GlanceModifier
            .fillMaxSize()  // 填充整个小部件区域
            .clickable(actionStartActivity(model.intent))  // 点击时启动指定的Activity
    ) {
        // 背景图片层
        Image(
            provider = ImageProvider(model.backgroundResId),
            contentDescription = null,  // 装饰性图片，不需要内容描述
            contentScale = ContentScale.FillBounds,  // 缩放图片以完全填充区域
            modifier = GlanceModifier.fillMaxSize()
        )
        // 文本内容层
        Text(
            text = model.text,
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(
                    top = layoutSpec.topPadding,
                    start = 15.dp,
                    end = 15.dp
                ),
            maxLines = layoutSpec.maxLines,  // 限制最大行数
            style = TextStyle(
                color = ColorProvider(Color(0xFF663300)),  // 棕色文字
                fontSize = 14.sp
            )
        )
    }
}