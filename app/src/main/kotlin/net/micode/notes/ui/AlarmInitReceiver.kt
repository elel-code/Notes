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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.micode.notes.data.NotesRepository
import net.micode.notes.tool.PendingIntentCompat

/**
 * 闹钟初始化广播接收器
 * 
 * 该 BroadcastReceiver 专门用于监听系统启动完成的广播，并在设备重启后
 * 重新注册所有有效的闹钟提醒。这是必要的，因为系统的闹钟设置不会在设备重启后自动保留。
 * 
 * 主要功能：
 * 1. 监听系统启动完成事件（ACTION_BOOT_COMPLETED）
 * 2. 查询数据库中的所有未过期闹钟提醒
 * 3. 为每个有效的闹钟重新创建 PendingIntent 并注册到 AlarmManager
 * 
 * 工作流程：
 * 设备重启 → 系统发送 BOOT_COMPLETED 广播 → 本接收器被触发 → 
 * 查询未来所有未触发的闹钟 → 逐个重新设置 AlarmManager 提醒
 * 
 * 为什么需要这个组件：
 * - AlarmManager 中设置的闹钟是易失性的，设备重启后会全部丢失
 * - 用户设置的所有提醒（例如明天上午9点提醒写便签）需要在重启后仍然生效
 * - 通过监听开机广播，我们可以恢复所有的闹钟设置，提供持久化的提醒体验
 * 
 * @author MiCode Open Source Community
 */
class AlarmInitReceiver : BroadcastReceiver() {
    
    /**
     * 广播接收器的回调方法
     * 
     * 当设备发送匹配的广播时，系统会调用此方法。本接收器只关心系统启动完成的广播。
     * 
     * 执行逻辑：
     * 1. 验证广播的 Action 是否为 ACTION_BOOT_COMPLETED（系统启动完成）
     * 2. 从数据库查询所有未来需要触发且尚未触发的闹钟提醒
     * 3. 遍历每个闹钟，创建对应的 PendingIntent 并重新注册到 AlarmManager
     * 
     * 注意：
     * - 查询数据库使用 runBlocking + Dispatchers.IO 是合理的，因为这个接收器
     *   需要尽快完成工作，且查询操作相对快速（索引查询）
     * - 使用 RTC_WAKEUP 标志，确保在设备休眠时也能唤醒并触发提醒
     * 
     * @param context 接收器运行时的上下文环境
     * @param intent 包含广播信息的 Intent 对象，用于判断广播类型
     */
    override fun onReceive(context: Context, intent: Intent?) {
        // 第一步：验证广播类型
        // 只有系统启动完成的广播才需要处理
        // SYSTEM_USER_ADDED、MY_PACKAGE_REPLACED 等广播会被忽略
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) {
            return  // 不是开机广播，直接返回
        }

        // 第二步：获取当前时间戳
        // 用于过滤出未来需要触发的闹钟（alertedDate > currentDate）
        val currentDate = System.currentTimeMillis()
        
        // 第三步：从数据库查询所有未来的闹钟提醒
        // 使用 runBlocking 同步执行协程，因为 BroadcastReceiver 的 onReceive 方法
        // 必须在有限时间内返回（Android 框架限制为约10秒），同步执行更可控
        val alarms = runBlocking(Dispatchers.IO) {
            // 创建 NotesRepository 实例，通过 applicationContext 避免内存泄漏
            NotesRepository(context.applicationContext).getFutureAlertNotes(currentDate)
        }
        
        // 第四步：获取 AlarmManager 系统服务
        // AlarmManager 负责管理设备上的闹钟和定时任务
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // 第五步：遍历所有未触发的闹钟并重新设置
        alarms.forEach { alarm ->
            // 5.1 创建 Intent，用于在闹钟触发时启动 AlarmReceiver
            // Intent 携带便签 ID（EXTRA_UID），以便 AlarmReceiver 知道哪条便签触发了提醒
            val sender = Intent(context, AlarmReceiver::class.java).apply {
                putExtra(Intent.EXTRA_UID, alarm.noteId)  // 存储便签ID
            }
            
            // 5.2 创建 PendingIntent
            // PendingIntent 允许 AlarmManager 在未来某个时间点以当前应用的身份启动 Intent
            // 参数说明：
            //   - context: 上下文
            //   - requestCode: 0（所有闹钟使用相同的 requestCode，因为 noteId 存储在 Intent 中用于区分）
            //   - intent: 要执行的 Intent
            //   - flags: 使用 PendingIntentCompat.immutableFlag() 确保不可变性（Android 12+ 要求）
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,                                    // requestCode，设为0即可
                sender,                               // 要包装的 Intent
                PendingIntentCompat.immutableFlag()   // flag：保证 PendingIntent 不可变
            )
            
            // 5.3 将闹钟设置到 AlarmManager
            // 参数说明：
            //   - type: AlarmManager.RTC_WAKEUP - 使用真实时间（RTC），并在设备休眠时唤醒 CPU
            //   - triggerAtMillis: alarm.alertedDate - 闹钟触发的时间点（毫秒时间戳）
            //   - operation: pendingIntent - 触发时要执行的 PendingIntent
            alarmManager.set(AlarmManager.RTC_WAKEUP, alarm.alertedDate, pendingIntent)
        }
        
        // 注：本接收器不需要调用 abortBroadcast()，也不需要在 onReceive 中启动长时间运行的操作
        // 所有操作均为同步且轻量级，符合 BroadcastReceiver 的最佳实践
    }
}