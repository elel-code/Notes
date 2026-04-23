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
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import net.micode.notes.data.Notes
import net.micode.notes.data.Notes.NoteColumns
import net.micode.notes.tool.PendingIntentCompat

class AlarmInitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val currentDate = System.currentTimeMillis()
        val c = context.getContentResolver().query(
            Notes.CONTENT_NOTE_URI,
            PROJECTION,
            NoteColumns.Companion.ALERTED_DATE + ">? AND " + NoteColumns.Companion.TYPE + "=" + Notes.TYPE_NOTE,
            arrayOf<String>(currentDate.toString()),
            null
        )

        if (c != null) {
            if (c.moveToFirst()) {
                do {
                    val alertDate = c.getLong(COLUMN_ALERTED_DATE)
                    val sender = Intent(context, AlarmReceiver::class.java)
                    sender.setData(
                        ContentUris.withAppendedId(
                            Notes.CONTENT_NOTE_URI, c.getLong(
                                COLUMN_ID
                            )
                        )
                    )
                    val pendingIntent = PendingIntent.getBroadcast(
                        context, 0, sender,
                        PendingIntentCompat.immutableFlag()
                    )
                    val alermManager = context
                        .getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    alermManager.set(AlarmManager.RTC_WAKEUP, alertDate, pendingIntent)
                } while (c.moveToNext())
            }
            c.close()
        }
    }

    companion object {
        private val PROJECTION: Array<String?> = arrayOf<String>(
            NoteColumns.Companion.ID,
            NoteColumns.Companion.ALERTED_DATE
        )

        private const val COLUMN_ID = 0
        private const val COLUMN_ALERTED_DATE = 1
    }
}
