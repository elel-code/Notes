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

// 2x尺寸的笔记小部件提供者（接收器）
// 继承自 GlanceAppWidgetReceiver，用于接收系统小部件相关事件并绑定对应的 GlanceAppWidget
class NoteWidgetProvider_2x : () {
    // 重写 glanceAppWidget 属性，指定该小部件实际使用的 GlanceAppWidget 实现为 NoteWidget2x
    override val glanceAppWidget: GlanceAppWidget = NoteWidget2x
}