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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 闹钟提醒广播接收器
 * 
 * 该 BroadcastReceiver 作为闹钟触发时的入口点，负责将系统闹钟事件转化为
 * 可见的 Activity 界面。它是 AlarmManager 和实际 UI 显示之间的桥梁。
 * 
 * 主要功能：
 * 1. 接收 AlarmManager 在指定时间触发的系统广播
 * 2. 将广播 Intent 转换为启动 AlarmAlertActivity 的 Intent
 * 3. 添加 FLAG_ACTIVITY_NEW_TASK 标志启动 Activity
 * 
 * 工作流程：
 * AlarmManager 触发 → 系统发送广播 → AlarmReceiver.onReceive() 被调用 → 
 * 重新设置 Intent 的目标组件 → 启动 AlarmAlertActivity 显示提醒界面
 * 
 * 为什么需要这个组件：
 * - AlarmManager 只能发送广播或启动 Service，不能直接启动 Activity
 * - 从广播接收器启动 Activity 需要 FLAG_ACTIVITY_NEW_TASK 标志
 * - 分离 AlarmReceiver 和 AlarmAlertActivity 可以实现职责单一：
 *   * AlarmReceiver：处理系统级闹钟事件，轻量级转发
 *   * AlarmAlertActivity：显示 UI 界面，处理用户交互
 * 
 * @author MiCode Open Source Community
 */
class AlarmReceiver : BroadcastReceiver() {
    
    /**
     * 广播接收器的回调方法
     * 
     * 当 AlarmManager 设定的时间到达时，系统会调用此方法。
     * 
     * 执行逻辑：
     * 1. 将传入的 Intent 重新设置目标组件为 AlarmAlertActivity
     * 2. 添加 FLAG_ACTIVITY_NEW_TASK 标志（从非 Activity 上下文启动 Activity 的必要标志）
     * 3. 启动 AlarmAlertActivity，显示提醒界面
     * 
     * 注意：
     * - 本方法执行时间应当极短，仅做必要的 Intent 转换和 Activity 启动
     * - 不要在此方法中执行耗时操作（如数据库查询、网络请求等）
     * - Intent 中已经包含了便签 ID（通过 EXTRA_UID 在设置闹钟时存入），
     *   这些数据会随着 Intent 自动传递给 AlarmAlertActivity
     * 
     * @param context 接收器运行时的上下文环境，用于启动 Activity
     * @param intent 系统广播传递的 Intent 对象，包含闹钟触发信息
     *               （其中含有之前通过 PendingIntent 设置的所有 extra 数据，
     *                如 EXTRA_UID 保存的便签 ID）
     */
    override fun onReceive(context: Context, intent: Intent) {
        // 第一步：重新设置 Intent 的目标组件
        // 原始 Intent 来自 PendingIntent，其目标组件可能是隐式的或未设置
        // 显式设置为 AlarmAlertActivity，确保系统知道要启动哪个 Activity
        intent.setClass(context, AlarmAlertActivity::class.java)
        
        // 第二步：添加 NEW_TASK 标志
        // 因为 BroadcastReceiver 不属于 Activity 上下文，启动 Activity 时
        // 必须使用 FLAG_ACTIVITY_NEW_TASK 标志，否则系统会抛出异常
        // 该标志会创建一个新的任务栈来承载这个 Activity
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        
        // 第三步：启动闹钟提醒 Activity
        // AlarmAlertActivity 将接管后续的 UI 显示和用户交互逻辑
        context.startActivity(intent)
    }
}