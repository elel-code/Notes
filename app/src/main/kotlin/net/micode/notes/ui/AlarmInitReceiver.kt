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

class AlarmInitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        val currentDate = System.currentTimeMillis()
        val alarms = runBlocking(Dispatchers.IO) {
            NotesRepository(context.applicationContext).getFutureAlertNotes(currentDate)
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.forEach { alarm ->
            val sender = Intent(context, AlarmReceiver::class.java).apply {
                putExtra(Intent.EXTRA_UID, alarm.noteId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                sender,
                PendingIntentCompat.immutableFlag()
            )
            alarmManager.set(AlarmManager.RTC_WAKEUP, alarm.alertedDate, pendingIntent)
        }
    }
}
