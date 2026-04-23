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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Build
import net.micode.notes.R
import net.micode.notes.tool.PendingIntentCompat
import net.micode.notes.ui.NotesListActivity
import net.micode.notes.ui.NotesPreferenceActivity

class GTaskASyncTask(
    private val mContext: Context,
    private val mOnCompleteListener: OnCompleteListener?
) : AsyncTask<Void?, String?, Int?>() {
    interface OnCompleteListener {
        fun onComplete()
    }

    private val mNotifiManager: NotificationManager

    private val mTaskManager: GTaskManager

    init {
        mNotifiManager = mContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mTaskManager = GTaskManager.Companion.getInstance()
    }

    fun cancelSync() {
        mTaskManager.cancelSync()
    }

    fun publishProgess(message: String?) {
        publishProgress(
            *arrayOf<String?>(
                message
            )
        )
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        if (mNotifiManager.getNotificationChannel(GTASK_SYNC_CHANNEL_ID) != null) {
            return
        }
        val channel = NotificationChannel(
            GTASK_SYNC_CHANNEL_ID,
            mContext.getString(R.string.app_name), NotificationManager.IMPORTANCE_DEFAULT
        )
        mNotifiManager.createNotificationChannel(channel)
    }

    private fun createNotificationPendingIntent(tickerId: Int): PendingIntent? {
        val intent: Intent?
        if (tickerId != R.string.ticker_success) {
            intent = Intent(mContext, NotesPreferenceActivity::class.java)
        } else {
            intent = Intent(mContext, NotesListActivity::class.java)
        }
        return PendingIntent.getActivity(
            mContext, 0, intent,
            PendingIntentCompat.updateCurrentImmutableFlag()
        )
    }

    private fun showNotification(tickerId: Int, content: String?) {
        ensureNotificationChannel()
        val builder: Notification.Builder?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = Notification.Builder(mContext, GTASK_SYNC_CHANNEL_ID)
        } else {
            builder = Notification.Builder(mContext)
        }

        builder.setSmallIcon(R.drawable.notification)
            .setTicker(mContext.getString(tickerId))
            .setWhen(System.currentTimeMillis())
            .setDefaults(Notification.DEFAULT_LIGHTS)
            .setAutoCancel(true)
            .setContentTitle(mContext.getString(R.string.app_name))
            .setContentText(content)
            .setContentIntent(createNotificationPendingIntent(tickerId))

        val notification: Notification?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            notification = builder.build()
        } else {
            notification = builder.getNotification()
        }
        mNotifiManager.notify(GTASK_SYNC_NOTIFICATION_ID, notification)
    }

    override fun doInBackground(vararg unused: Void?): Int {
        publishProgess(
            mContext.getString(
                R.string.sync_progress_login,
                NotesPreferenceActivity.Companion.getSyncAccountName(mContext)
            )
        )
        return mTaskManager.sync(mContext, this)
    }

    override fun onProgressUpdate(vararg progress: String?) {
        showNotification(R.string.ticker_syncing, progress[0])
        if (mContext is GTaskSyncService) {
            mContext.sendBroadcast(progress[0])
        }
    }

    override fun onPostExecute(result: Int) {
        if (result == GTaskManager.Companion.STATE_SUCCESS) {
            showNotification(
                R.string.ticker_success, mContext.getString(
                    R.string.success_sync_account, mTaskManager.getSyncAccount()
                )
            )
            NotesPreferenceActivity.Companion.setLastSyncTime(mContext, System.currentTimeMillis())
        } else if (result == GTaskManager.Companion.STATE_NETWORK_ERROR) {
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_network))
        } else if (result == GTaskManager.Companion.STATE_INTERNAL_ERROR) {
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_internal))
        } else if (result == GTaskManager.Companion.STATE_SYNC_CANCELLED) {
            showNotification(
                R.string.ticker_cancel, mContext
                    .getString(R.string.error_sync_cancelled)
            )
        }
        if (mOnCompleteListener != null) {
            Thread(object : Runnable {
                override fun run() {
                    mOnCompleteListener.onComplete()
                }
            }).start()
        }
    }

    companion object {
        private const val GTASK_SYNC_NOTIFICATION_ID = 5234235

        private const val GTASK_SYNC_CHANNEL_ID = "gtask_sync"
    }
}
