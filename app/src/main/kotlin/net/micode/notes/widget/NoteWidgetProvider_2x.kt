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

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 2x2 尺寸笔记小部件的广播接收器
 *
 * 该类作为系统应用小部件框架与 [NoteWidget2x] 之间的桥梁。
 * 继承自 [GlanceAppWidgetReceiver]，负责处理小部件的生命周期事件，
 * 包括小部件的创建、更新、删除等系统回调。
 *
 * 使用 Glance 框架的优势：
 * - 基于 Compose UI 声明式编程
 * - 自动处理小部件的状态更新
 * - 简化异步数据加载逻辑
 *
 * @see NoteWidget2x 实际提供 2x2 尺寸小部件的 UI 和逻辑实现
 * @see GlanceAppWidgetReceiver Glance 框架的基础接收器类
 */
class NoteWidgetProvider_2x : () {  // TODO: 应继承 GlanceAppWidgetReceiver()
    /**
     * 绑定到当前接收器的 GlanceAppWidget 实例
     *
     * 该属性决定了小部件的具体外观和行为。[NoteWidget2x] 作为单例对象，
     * 定义了 2x2 尺寸小部件的：
     * - 布局规格（最大行数：6 行，顶部内边距：20dp）
     * - 背景资源映射规则
     * - 数据加载和绑定逻辑
     *
     * 重写此属性是 [GlanceAppWidgetReceiver] 的核心要求，
     * Glance 框架会根据此实例自动完成小部件的渲染和更新。
     */
    override val glanceAppWidget: GlanceAppWidget = NoteWidget2x
}