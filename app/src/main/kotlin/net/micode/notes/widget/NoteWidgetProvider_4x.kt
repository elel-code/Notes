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
 * 4x4 尺寸笔记小部件的提供者类
 *
 * 继承自 [GlanceAppWidgetReceiver]，用于接收系统小部件生命周期事件（如添加、更新、删除等）。
 * 通过重写 [glanceAppWidget] 属性，将小部件与具体的 [GlanceAppWidget] 实现 [NoteWidget4x] 绑定。
 *
 * 注：该类需要在 AndroidManifest.xml 中注册为 receiver，并配置对应的 meta-data 和 intent-filter，
 * 以便系统能够识别并管理该小部件。
 */
class NoteWidgetProvider_4x : GlanceAppWidgetReceiver() {
    /**
     * 绑定到该 Receiver 的 [GlanceAppWidget] 实例
     *
     * [NoteWidget4x] 负责定义 4x4 尺寸小部件的 UI 布局、状态管理和数据更新逻辑。
     */
    override val glanceAppWidget: GlanceAppWidget = NoteWidget4x
}