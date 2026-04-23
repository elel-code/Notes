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
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Window
import android.view.WindowManager
import net.micode.notes.R
import net.micode.notes.data.Notes
import net.micode.notes.tool.DataUtils
import java.io.IOException

class AlarmAlertActivity : Activity(), DialogInterface.OnClickListener,
    DialogInterface.OnDismissListener {
    private var mNoteId: Long = 0
    private var mSnippet: String? = null
    var mPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        val win = getWindow()
        win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)

        if (!this.isScreenOn) {
            win.addFlags(
                (WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        or WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                        or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR)
            )
        }

        val intent = getIntent()

        try {
            mNoteId = intent.getData()!!.getPathSegments().get(1).toLong()
            mSnippet = DataUtils.getSnippetById(this.getContentResolver(), mNoteId)
            mSnippet = if (mSnippet!!.length > SNIPPET_PREW_MAX_LEN)
                mSnippet!!.substring(
                    0,
                    SNIPPET_PREW_MAX_LEN
                ) + getResources().getString(R.string.notelist_string_info)
            else
                mSnippet
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            return
        }

        mPlayer = MediaPlayer()
        if (DataUtils.visibleInNoteDatabase(getContentResolver(), mNoteId, Notes.TYPE_NOTE)) {
            showActionDialog()
            playAlarmSound()
        } else {
            finish()
        }
    }

    private val isScreenOn: Boolean
        get() {
            val pm =
                getSystemService(POWER_SERVICE) as PowerManager
            return pm.isScreenOn()
        }

    private fun playAlarmSound() {
        val url = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)

        val silentModeStreams = Settings.System.getInt(
            getContentResolver(),
            Settings.System.MODE_RINGER_STREAMS_AFFECTED, 0
        )

        if ((silentModeStreams and (1 shl AudioManager.STREAM_ALARM)) != 0) {
            mPlayer!!.setAudioStreamType(silentModeStreams)
        } else {
            mPlayer!!.setAudioStreamType(AudioManager.STREAM_ALARM)
        }
        try {
            mPlayer!!.setDataSource(this, url)
            mPlayer!!.prepare()
            mPlayer!!.setLooping(true)
            mPlayer!!.start()
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
            // TODO Auto-generated catch block
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
        if (mPlayer != null) {
            mPlayer!!.stop()
            mPlayer!!.release()
            mPlayer = null
        }
    }

    companion object {
        private const val SNIPPET_PREW_MAX_LEN = 60
    }
}
