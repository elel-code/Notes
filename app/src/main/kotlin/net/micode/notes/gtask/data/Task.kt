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
        val parentTaskList = parent ?: throw ActionFailureException("task parent is not set")
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
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, parentTaskList.getChildTaskIndex(this))

            // entity_delta
            val entity = JSONObject()
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, name)
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
            js.put(GTaskStringUtils.GTASK_JSON_PARENT_ID, parentTaskList.gid)

            // dest_parent_type
            js.put(
                GTaskStringUtils.GTASK_JSON_DEST_PARENT_TYPE,
                GTaskStringUtils.GTASK_JSON_TYPE_GROUP
            )

            // list_id
            js.put(GTaskStringUtils.GTASK_JSON_LIST_ID, parentTaskList.gid)

            // prior_sibling_id
            priorSibling?.gid?.let { js.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID, it) }
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
            js.put(GTaskStringUtils.GTASK_JSON_ID, gid)

            // entity_delta
            val entity = JSONObject()
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, name)
            if (this.notes != null) {
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, this.notes)
            }
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, deleted)
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
                    gid = js.getString(GTaskStringUtils.GTASK_JSON_ID)
                }

                // last_modified
                if (js.has(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED)) {
                    lastModified = js.getLong(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED)
                }

                // name
                if (js.has(GTaskStringUtils.GTASK_JSON_NAME)) {
                    name = js.getString(GTaskStringUtils.GTASK_JSON_NAME)
                }

                // notes
                if (js.has(GTaskStringUtils.GTASK_JSON_NOTES)) {
                    this.notes = js.getString(GTaskStringUtils.GTASK_JSON_NOTES)
                }

                // deleted
                if (js.has(GTaskStringUtils.GTASK_JSON_DELETED)) {
                    deleted = js.getBoolean(GTaskStringUtils.GTASK_JSON_DELETED)
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
            return
        }

        try {
            val note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE)
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
                    name = data.getString(Notes.DataColumns.Companion.CONTENT)
                    break
                }
            }
        } catch (e: JSONException) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
        }
    }

    override fun getLocalJSONFromContent(): JSONObject? {
        val taskName = this.name
        try {
            val metaInfo = mMetaInfo
            if (metaInfo == null) {
                // new task created from web
                if (taskName == null) {
                    Log.w(TAG, "the note seems to be an empty one")
                    return null
                }

                val js = JSONObject()
                val note = JSONObject()
                val dataArray = JSONArray()
                val data = JSONObject()
                data.put(Notes.DataColumns.Companion.CONTENT, taskName)
                dataArray.put(data)
                js.put(GTaskStringUtils.META_HEAD_DATA, dataArray)
                note.put(NoteColumns.Companion.TYPE, Notes.TYPE_NOTE)
                js.put(GTaskStringUtils.META_HEAD_NOTE, note)
                return js
            } else {
                // synced task
                val note = metaInfo.getJSONObject(GTaskStringUtils.META_HEAD_NOTE)
                val dataArray = metaInfo.getJSONArray(GTaskStringUtils.META_HEAD_DATA)

                for (i in 0..<dataArray.length()) {
                    val data = dataArray.getJSONObject(i)
                    if (TextUtils.equals(
                            data.getString(Notes.DataColumns.Companion.MIME_TYPE),
                            DataConstants.NOTE
                        )
                    ) {
                            data.put(Notes.DataColumns.Companion.CONTENT, taskName)
                        break
                    }
                }

                note.put(NoteColumns.Companion.TYPE, Notes.TYPE_NOTE)
                return metaInfo
            }
        } catch (e: JSONException) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
            return null
        }
    }

    fun setMetaInfo(metaData: MetaData?) {
        metaData?.notes?.let { notes ->
            try {
                mMetaInfo = JSONObject(notes)
            } catch (e: JSONException) {
                Log.w(TAG, e.toString())
                mMetaInfo = null
            }
        } ?: run {
            mMetaInfo = null
        }
    }

    override fun getSyncAction(c: Cursor): Int {
        try {
            val noteInfo = mMetaInfo
                ?.takeIf { it.has(GTaskStringUtils.META_HEAD_NOTE) }
                ?.getJSONObject(GTaskStringUtils.META_HEAD_NOTE)

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
                if (c.getLong(SqlNote.Companion.SYNC_ID_COLUMN) == lastModified) {
                    // no update both side
                    return Node.Companion.SYNC_ACTION_NONE
                } else {
                    // apply remote to local
                    return Node.Companion.SYNC_ACTION_UPDATE_LOCAL
                }
            } else {
                // validate gtask id
                if (c.getString(SqlNote.Companion.GTASK_ID_COLUMN) != gid) {
                    Log.e(TAG, "gtask id doesn't match")
                    return Node.Companion.SYNC_ACTION_ERROR
                }
                if (c.getLong(SqlNote.Companion.SYNC_ID_COLUMN) == lastModified) {
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

    open fun isWorthSaving(): Boolean {
        return mMetaInfo != null
                || (!name.isNullOrBlank())
                || (!notes.isNullOrBlank())
    }

    companion object {
        private val TAG: String = Task::class.java.getSimpleName()
    }
}
