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
package net.micode.notes.gtask.data

import android.database.Cursor
import android.util.Log
import net.micode.notes.tool.GTaskStringUtils
import org.json.JSONException
import org.json.JSONObject

class MetaData : Task() {
    var relatedGid: String? = null
        private set

    fun setMeta(gid: String?, metaInfo: JSONObject) {
        try {
            metaInfo.put(GTaskStringUtils.META_HEAD_GTASK_ID, gid)
        } catch (e: JSONException) {
            Log.e(TAG, "failed to put related gid")
        }
        setNotes(metaInfo.toString())
        setName(GTaskStringUtils.META_NOTE_NAME)
    }

    override fun isWorthSaving(): Boolean {
        return getNotes() != null
    }

    override fun setContentByRemoteJSON(js: JSONObject?) {
        super.setContentByRemoteJSON(js)
        if (getNotes() != null) {
            try {
                val metaInfo = JSONObject(getNotes().trim { it <= ' ' })
                this.relatedGid = metaInfo.getString(GTaskStringUtils.META_HEAD_GTASK_ID)
            } catch (e: JSONException) {
                Log.w(TAG, "failed to get related gid")
                this.relatedGid = null
            }
        }
    }

    override fun setContentByLocalJSON(js: JSONObject?) {
        // this function should not be called
        throw IllegalAccessError("MetaData:setContentByLocalJSON should not be called")
    }

    override fun getLocalJSONFromContent(): JSONObject? {
        throw IllegalAccessError("MetaData:getLocalJSONFromContent should not be called")
    }

    override fun getSyncAction(c: Cursor?): Int {
        throw IllegalAccessError("MetaData:getSyncAction should not be called")
    }

    companion object {
        private val TAG: String = MetaData::class.java.getSimpleName()
    }
}
