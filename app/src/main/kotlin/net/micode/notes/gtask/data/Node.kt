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
import org.json.JSONObject

abstract class Node {
    var gid: String? = null

    var name: String? = ""

    var lastModified: Long = 0

    var deleted: Boolean = false

    abstract fun getCreateAction(actionId: Int): JSONObject?

    abstract fun getUpdateAction(actionId: Int): JSONObject?

    abstract fun setContentByRemoteJSON(js: JSONObject?)

    abstract fun setContentByLocalJSON(js: JSONObject?)

    abstract fun getLocalJSONFromContent(): JSONObject?

    val localJsonFromContent: JSONObject?
        get() = getLocalJSONFromContent()

    abstract fun getSyncAction(c: Cursor): Int

    companion object {
        const val SYNC_ACTION_NONE: Int = 0

        const val SYNC_ACTION_ADD_REMOTE: Int = 1

        const val SYNC_ACTION_ADD_LOCAL: Int = 2

        const val SYNC_ACTION_DEL_REMOTE: Int = 3

        const val SYNC_ACTION_DEL_LOCAL: Int = 4

        const val SYNC_ACTION_UPDATE_REMOTE: Int = 5

        const val SYNC_ACTION_UPDATE_LOCAL: Int = 6

        const val SYNC_ACTION_UPDATE_CONFLICT: Int = 7

        const val SYNC_ACTION_ERROR: Int = 8
    }
}
