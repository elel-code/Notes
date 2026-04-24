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

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class GTaskSyncService : Service() {
    private val mServiceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mSyncTask: GTaskSyncTask? = null

    private fun startSync() {
        if (mSyncTask == null) {
            mSyncTask = GTaskSyncTask(
                context = this,
                scope = mServiceScope,
                onProgress = ::broadcastSyncState,
                onComplete = {
                    mSyncTask = null
                    broadcastSyncState("")
                    stopSelf()
                }
            )
            broadcastSyncState("")
            mSyncTask?.start()
        }
    }

    private fun cancelSync() {
        mSyncTask?.cancelSync()
    }

    override fun onCreate() {
        super.onCreate()
        mSyncTask = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.extras?.getInt(ACTION_STRING_NAME, ACTION_INVALID) ?: ACTION_INVALID) {
            ACTION_START_SYNC -> startSync()
            ACTION_CANCEL_SYNC -> cancelSync()
            else -> return super.onStartCommand(intent, flags, startId)
        }
        return START_STICKY
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mSyncTask?.cancelSync()
    }

    override fun onDestroy() {
        mSyncTask?.cancelSync()
        mSyncTask = null
        broadcastSyncState("")
        mServiceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun broadcastSyncState(msg: String?) {
        progressString = msg
        mIsSyncing = mSyncTask != null
        val intent = Intent(GTASK_SERVICE_BROADCAST_NAME).setPackage(packageName)
        intent.putExtra(GTASK_SERVICE_BROADCAST_IS_SYNCING, mIsSyncing)
        intent.putExtra(GTASK_SERVICE_BROADCAST_PROGRESS_MSG, msg)
        sendBroadcast(intent)
    }

    companion object {
        const val ACTION_STRING_NAME: String = "sync_action_type"

        const val ACTION_START_SYNC: Int = 0

        const val ACTION_CANCEL_SYNC: Int = 1

        const val ACTION_INVALID: Int = 2

        const val GTASK_SERVICE_BROADCAST_NAME: String =
            "net.micode.notes.gtask.remote.gtask_sync_service"

        const val GTASK_SERVICE_BROADCAST_IS_SYNCING: String = "isSyncing"

        const val GTASK_SERVICE_BROADCAST_PROGRESS_MSG: String = "progressMsg"

        private var mIsSyncing = false

        var progressString: String? = ""
            private set

        fun startSync(activity: Activity) {
            GTaskManager.instance.setActivityContext(activity)
            val intent = Intent(activity, GTaskSyncService::class.java)
            intent.putExtra(ACTION_STRING_NAME, ACTION_START_SYNC)
            activity.startService(intent)
        }

        fun cancelSync(context: Context) {
            val intent = Intent(context, GTaskSyncService::class.java)
            intent.putExtra(ACTION_STRING_NAME, ACTION_CANCEL_SYNC)
            context.startService(intent)
        }

        val isSyncing: Boolean
            get() = mIsSyncing
    }
}
