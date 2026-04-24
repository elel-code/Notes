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

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.Window
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.micode.notes.R
import net.micode.notes.data.Notes
import net.micode.notes.data.NotesRepository
import java.io.IOException

class AlarmAlertActivity : Activity(), DialogInterface.OnClickListener,
    DialogInterface.OnDismissListener {
    private var mNoteId: Long = 0
    private var mSnippet: String? = null
    var mPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        configureWindowForAlarm()

        val intent = intent

        try {
            mNoteId = intent.getLongExtra(Intent.EXTRA_UID, 0L).takeIf { it > 0L }
                ?: intent.data?.pathSegments?.getOrNull(1)?.toLongOrNull()
                ?: throw IllegalArgumentException("Missing note id in alarm intent")
            val repository = NotesRepository(applicationContext)
            val snippet = runBlocking(Dispatchers.IO) {
                repository.getSnippetById(mNoteId).orEmpty()
            }
            mSnippet = if (snippet.length > SNIPPET_PREW_MAX_LEN)
                snippet.substring(
                    0,
                    SNIPPET_PREW_MAX_LEN
                ) + getResources().getString(R.string.notelist_string_info)
            else
                snippet
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            return
        }

        mPlayer = MediaPlayer()
        val visible = runBlocking(Dispatchers.IO) {
            NotesRepository(applicationContext).isVisibleNote(mNoteId, Notes.TYPE_NOTE)
        }
        if (visible) {
            showActionDialog()
            playAlarmSound()
        } else {
            finish()
        }
    }

    private val isScreenOn: Boolean
        get() = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive

    @Suppress("DEPRECATION")
    private fun configureWindowForAlarm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            return
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
    }

    private fun playAlarmSound() {
        val url = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
        val player = mPlayer ?: return
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        try {
            player.setDataSource(this, url)
            player.prepare()
            player.isLooping = true
            player.start()
        } catch (e: IllegalArgumentException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        } catch (e: SecurityException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        } catch (e: IllegalStateException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun showActionDialog() {
        val dialog = AlertDialog.Builder(this)
        dialog.setTitle(R.string.app_name)
        dialog.setMessage(mSnippet)
        dialog.setPositiveButton(R.string.notealert_ok, this)
        if (this.isScreenOn) {
            dialog.setNegativeButton(R.string.notealert_enter, this)
        }
        dialog.show().setOnDismissListener(this)
    }

    override fun onClick(dialog: DialogInterface?, which: Int) {
        when (which) {
            DialogInterface.BUTTON_NEGATIVE -> {
                val intent = Intent(this, NoteEditActivity::class.java)
                intent.setAction(Intent.ACTION_VIEW)
                intent.putExtra(Intent.EXTRA_UID, mNoteId)
                startActivity(intent)
            }

            else -> {}
        }
    }

    override fun onDismiss(dialog: DialogInterface?) {
        stopAlarmSound()
        finish()
    }

    private fun stopAlarmSound() {
        mPlayer?.let { player ->
            player.stop()
            player.release()
            mPlayer = null
        }
    }

    companion object {
        private const val SNIPPET_PREW_MAX_LEN = 60
    }
}
