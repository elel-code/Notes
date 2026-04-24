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
package net.micode.notes.gtask.remote

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.micode.notes.R
import net.micode.notes.tool.PendingIntentCompat
import net.micode.notes.ui.NotesListActivity
import net.micode.notes.ui.NotesPreferenceActivity

class GTaskSyncTask(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onProgress: (String?) -> Unit = {},
    private val onComplete: () -> Unit = {}
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val taskManager = GTaskManager.instance
    private var syncJob: Job? = null

    fun start() {
        if (syncJob != null) {
            return
        }

        syncJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                publishProgress(
                    context.getString(
                        R.string.sync_progress_login,
                        NotesPreferenceActivity.getSyncAccountName(context)
                    )
                )
                taskManager.sync(context, ::publishProgress)
            }

            handleSyncResult(result)
            syncJob = null
            onComplete()
        }
    }

    fun cancelSync() {
        taskManager.cancelSync()
    }

    private fun publishProgress(message: String?) {
        showNotification(R.string.ticker_syncing, message)
        onProgress(message)
    }

    private fun ensureNotificationChannel() {
        if (notificationManager.getNotificationChannel(GTASK_SYNC_CHANNEL_ID) != null) {
            return
        }
        val channel = NotificationChannel(
            GTASK_SYNC_CHANNEL_ID,
            context.getString(R.string.app_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotificationPendingIntent(tickerId: Int): PendingIntent? {
        val intent = if (tickerId != R.string.ticker_success) {
            Intent(context, NotesPreferenceActivity::class.java)
        } else {
            Intent(context, NotesListActivity::class.java)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntentCompat.updateCurrentImmutableFlag()
        )
    }

    private fun showNotification(tickerId: Int, content: String?) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureNotificationChannel()
        val notification = NotificationCompat.Builder(context, GTASK_SYNC_CHANNEL_ID)
            .setSmallIcon(R.drawable.notification)
            .setTicker(context.getString(tickerId))
            .setWhen(System.currentTimeMillis())
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            .setAutoCancel(true)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(content)
            .setContentIntent(createNotificationPendingIntent(tickerId))
            .build()
        notificationManager.notify(GTASK_SYNC_NOTIFICATION_ID, notification)
    }

    private fun handleSyncResult(result: Int) {
        when (result) {
            GTaskManager.STATE_SUCCESS -> {
                showNotification(
                    R.string.ticker_success,
                    context.getString(
                        R.string.success_sync_account,
                        taskManager.syncAccount
                    )
                )
                NotesPreferenceActivity.setLastSyncTime(context, System.currentTimeMillis())
            }

            GTaskManager.STATE_NETWORK_ERROR -> {
                showNotification(R.string.ticker_fail, context.getString(R.string.error_sync_network))
            }

            GTaskManager.STATE_INTERNAL_ERROR -> {
                showNotification(R.string.ticker_fail, context.getString(R.string.error_sync_internal))
            }

            GTaskManager.STATE_SYNC_CANCELLED -> {
                showNotification(R.string.ticker_cancel, context.getString(R.string.error_sync_cancelled))
            }
        }
    }

    companion object {
        private const val GTASK_SYNC_NOTIFICATION_ID = 5234235
        private const val GTASK_SYNC_CHANNEL_ID = "gtask_sync"
    }
}
