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
import android.text.TextUtils
import android.util.Log
import net.micode.notes.data.Notes
import net.micode.notes.data.Notes.DataConstants
import net.micode.notes.data.Notes.NoteColumns
import net.micode.notes.gtask.exception.ActionFailureException
import net.micode.notes.tool.GTaskStringUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

open class Task : Node() {
    var completed: Boolean = false

    var notes: String? = null

    private var mMetaInfo: JSONObject? = null

    var priorSibling: Task? = null

    var parent: TaskList? = null

    override fun getCreateAction(actionId: Int): JSONObject {
        val js = JSONObject()

        try {
            // action_type
            js.put(
                GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                GTaskStringUtils.GTASK_JSON_ACTION_TYPE_CREATE
            )

            // action_id
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId)

            // index
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, parent!!.getChildTaskIndex(this))

            // entity_delta
            val entity = JSONObject()
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName())
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null")
            entity.put(
                GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                GTaskStringUtils.GTASK_JSON_TYPE_TASK
            )
            if (this.notes != null) {
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, this.notes)
            }
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity)

            // parent_id
            js.put(GTaskStringUtils.GTASK_JSON_PARENT_ID, parent!!.getGid())

            // dest_parent_type
            js.put(
                GTaskStringUtils.GTASK_JSON_DEST_PARENT_TYPE,
                GTaskStringUtils.GTASK_JSON_TYPE_GROUP
            )

            // list_id
            js.put(GTaskStringUtils.GTASK_JSON_LIST_ID, parent!!.getGid())

            // prior_sibling_id
            if (this.priorSibling != null) {
                js.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID, priorSibling!!.getGid())
            }
        } catch (e: JSONException) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
            throw ActionFailureException("fail to generate task-create jsonobject")
        }

        return js
    }

    override fun getUpdateAction(actionId: Int): JSONObject {
        val js = JSONObject()

        try {
            // action_type
            js.put(
                GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                GTaskStringUtils.GTASK_JSON_ACTION_TYPE_UPDATE
            )

            // action_id
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId)

            // id
            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid())

            // entity_delta
            val entity = JSONObject()
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName())
            if (this.notes != null) {
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, this.notes)
            }
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted())
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity)
        } catch (e: JSONException) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
            throw ActionFailureException("fail to generate task-update jsonobject")
        }

        return js
    }

    override fun setContentByRemoteJSON(js: JSONObject?) {
        if (js != null) {
            try {
                // id
                if (js.has(GTaskStringUtils.GTASK_JSON_ID)) {
                    setGid(js.getString(GTaskStringUtils.GTASK_JSON_ID))
                }

                // last_modified
                if (js.has(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED)) {
                    setLastModified(js.getLong(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED))
                }

                // name
                if (js.has(GTaskStringUtils.GTASK_JSON_NAME)) {
                    setName(js.getString(GTaskStringUtils.GTASK_JSON_NAME))
                }

                // notes
                if (js.has(GTaskStringUtils.GTASK_JSON_NOTES)) {
                    this.notes = js.getString(GTaskStringUtils.GTASK_JSON_NOTES)
                }

                // deleted
                if (js.has(GTaskStringUtils.GTASK_JSON_DELETED)) {
                    setDeleted(js.getBoolean(GTaskStringUtils.GTASK_JSON_DELETED))
                }

                // completed
                if (js.has(GTaskStringUtils.GTASK_JSON_COMPLETED)) {
                    this.completed = js.getBoolean(GTaskStringUtils.GTASK_JSON_COMPLETED)
                }
            } catch (e: JSONException) {
                Log.e(TAG, e.toString())
                e.printStackTrace()
                throw ActionFailureException("fail to get task content from jsonobject")
            }
        }
    }

    override fun setContentByLocalJSON(js: JSONObject?) {
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE) || !js.has(GTaskStringUtils.META_HEAD_DATA)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable")
        }

        try {
            val note = js!!.getJSONObject(GTaskStringUtils.META_HEAD_NOTE)
            val dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA)

            if (note.getInt(NoteColumns.Companion.TYPE) != Notes.TYPE_NOTE) {
                Log.e(TAG, "invalid type")
                return
            }

            for (i in 0..<dataArray.length()) {
                val data = dataArray.getJSONObject(i)
                if (TextUtils.equals(
                        data.getString(Notes.DataColumns.Companion.MIME_TYPE),
                        DataConstants.NOTE
                    )
                ) {
                    setName(data.getString(Notes.DataColumns.Companion.CONTENT))
                    break
                }
            }
        } catch (e: JSONException) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
        }
    }

    override fun getLocalJSONFromContent(): JSONObject? {
        val name = getName()
        try {
            if (mMetaInfo == null) {
                // new task created from web
                if (name == null) {
                    Log.w(TAG, "the note seems to be an empty one")
                    return null
                }

                val js = JSONObject()
                val note = JSONObject()
                val dataArray = JSONArray()
                val data = JSONObject()
                data.put(Notes.DataColumns.Companion.CONTENT, name)
                dataArray.put(data)
                js.put(GTaskStringUtils.META_HEAD_DATA, dataArray)
                note.put(NoteColumns.Companion.TYPE, Notes.TYPE_NOTE)
                js.put(GTaskStringUtils.META_HEAD_NOTE, note)
                return js
            } else {
                // synced task
                val note = mMetaInfo!!.getJSONObject(GTaskStringUtils.META_HEAD_NOTE)
                val dataArray = mMetaInfo!!.getJSONArray(GTaskStringUtils.META_HEAD_DATA)

                for (i in 0..<dataArray.length()) {
                    val data = dataArray.getJSONObject(i)
                    if (TextUtils.equals(
                            data.getString(Notes.DataColumns.Companion.MIME_TYPE),
                            DataConstants.NOTE
                        )
                    ) {
                        data.put(Notes.DataColumns.Companion.CONTENT, getName())
                        break
                    }
                }

                note.put(NoteColumns.Companion.TYPE, Notes.TYPE_NOTE)
                return mMetaInfo
            }
        } catch (e: JSONException) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
            return null
        }
    }

    fun setMetaInfo(metaData: MetaData?) {
        if (metaData != null && metaData.notes != null) {
            try {
                mMetaInfo = JSONObject(metaData.notes)
            } catch (e: JSONException) {
                Log.w(TAG, e.toString())
                mMetaInfo = null
            }
        }
    }

    override fun getSyncAction(c: Cursor): Int {
        try {
            var noteInfo: JSONObject? = null
            if (mMetaInfo != null && mMetaInfo!!.has(GTaskStringUtils.META_HEAD_NOTE)) {
                noteInfo = mMetaInfo!!.getJSONObject(GTaskStringUtils.META_HEAD_NOTE)
            }

            if (noteInfo == null) {
                Log.w(TAG, "it seems that note meta has been deleted")
                return Node.Companion.SYNC_ACTION_UPDATE_REMOTE
            }

            if (!noteInfo.has(NoteColumns.Companion.ID)) {
                Log.w(TAG, "remote note id seems to be deleted")
                return Node.Companion.SYNC_ACTION_UPDATE_LOCAL
            }

            // validate the note id now
            if (c.getLong(SqlNote.Companion.ID_COLUMN) != noteInfo.getLong(NoteColumns.Companion.ID)) {
                Log.w(TAG, "note id doesn't match")
                return Node.Companion.SYNC_ACTION_UPDATE_LOCAL
            }

            if (c.getInt(SqlNote.Companion.LOCAL_MODIFIED_COLUMN) == 0) {
                // there is no local update
                if (c.getLong(SqlNote.Companion.SYNC_ID_COLUMN) == getLastModified()) {
                    // no update both side
                    return Node.Companion.SYNC_ACTION_NONE
                } else {
                    // apply remote to local
                    return Node.Companion.SYNC_ACTION_UPDATE_LOCAL
                }
            } else {
                // validate gtask id
                if (c.getString(SqlNote.Companion.GTASK_ID_COLUMN) != getGid()) {
                    Log.e(TAG, "gtask id doesn't match")
                    return Node.Companion.SYNC_ACTION_ERROR
                }
                if (c.getLong(SqlNote.Companion.SYNC_ID_COLUMN) == getLastModified()) {
                    // local modification only
                    return Node.Companion.SYNC_ACTION_UPDATE_REMOTE
                } else {
                    return Node.Companion.SYNC_ACTION_UPDATE_CONFLICT
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
        }

        return Node.Companion.SYNC_ACTION_ERROR
    }

    open val isWorthSaving: Boolean
        get() = mMetaInfo != null || (getName() != null && getName().trim { it <= ' ' }.length > 0)
                || (this.notes != null && this.notes!!.trim { it <= ' ' }.length > 0)

    companion object {
        private val TAG: String = Task::class.java.getSimpleName()
    }
}
