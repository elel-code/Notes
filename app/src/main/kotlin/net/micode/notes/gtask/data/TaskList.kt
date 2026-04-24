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
import net.micode.notes.data.Notes
import net.micode.notes.data.Notes.NoteColumns
import net.micode.notes.gtask.exception.ActionFailureException
import net.micode.notes.tool.GTaskStringUtils
import org.json.JSONException
import org.json.JSONObject

class TaskList : Node() {
    var index: Int = 1

    val childTaskList: ArrayList<Task>

    init {
        this.childTaskList = ArrayList<Task>()
    }

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
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, this.index)

            // entity_delta
            val entity = JSONObject()
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, name)
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null")
            entity.put(
                GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                GTaskStringUtils.GTASK_JSON_TYPE_GROUP
            )
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity)
        } catch (e: JSONException) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
            throw ActionFailureException("fail to generate tasklist-create jsonobject")
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
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, deleted)
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity)
        } catch (e: JSONException) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
            throw ActionFailureException("fail to generate tasklist-update jsonobject")
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
            } catch (e: JSONException) {
                Log.e(TAG, e.toString())
                e.printStackTrace()
                throw ActionFailureException("fail to get tasklist content from jsonobject")
            }
        }
    }

    override fun setContentByLocalJSON(js: JSONObject?) {
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable")
            return
        }

        try {
            val folder = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE)

            if (folder.getInt(NoteColumns.Companion.TYPE) == Notes.TYPE_FOLDER) {
                val name = folder.getString(NoteColumns.Companion.SNIPPET)
                this.name = GTaskStringUtils.MIUI_FOLDER_PREFFIX + name
            } else if (folder.getInt(NoteColumns.Companion.TYPE) == Notes.TYPE_SYSTEM) {
                when (folder.getLong(NoteColumns.Companion.ID)) {
                    Notes.ID_ROOT_FOLDER.toLong() -> {
                        name = GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT
                    }

                    Notes.ID_CALL_RECORD_FOLDER.toLong() -> {
                        name = GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_CALL_NOTE
                    }

                    else -> Log.e(TAG, "invalid system folder")
                }
            } else {
                Log.e(TAG, "error type")
            }
        } catch (e: JSONException) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
        }
    }

    override fun getLocalJSONFromContent(): JSONObject? {
        try {
            val js = JSONObject()
            val folder = JSONObject()

            var folderName = name.orEmpty()
            if (folderName.startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX)) {
                folderName = folderName.removePrefix(GTaskStringUtils.MIUI_FOLDER_PREFFIX)
            }
            folder.put(NoteColumns.Companion.SNIPPET, folderName)
            if (folderName == GTaskStringUtils.FOLDER_DEFAULT
                || folderName == GTaskStringUtils.FOLDER_CALL_NOTE
            ) folder.put(NoteColumns.Companion.TYPE, Notes.TYPE_SYSTEM)
            else folder.put(NoteColumns.Companion.TYPE, Notes.TYPE_FOLDER)

            js.put(GTaskStringUtils.META_HEAD_NOTE, folder)

            return js
        } catch (e: JSONException) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
            return null
        }
    }

    override fun getSyncAction(c: Cursor): Int {
        try {
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
                    // for folder conflicts, just apply local modification
                    return Node.Companion.SYNC_ACTION_UPDATE_REMOTE
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
        }

        return Node.Companion.SYNC_ACTION_ERROR
    }

    val childTaskCount: Int
        get() = childTaskList.size

    fun addChildTask(task: Task?): Boolean {
        var ret = false
        if (task != null && !childTaskList.contains(task)) {
            val previousTask = childTaskList.lastOrNull()
            ret = childTaskList.add(task)
            if (ret) {
                // need to set prior sibling and parent
                task.priorSibling = previousTask
                task.parent = this
            }
        }
        return ret
    }

    fun addChildTask(task: Task?, index: Int): Boolean {
        if (index < 0 || index > childTaskList.size) {
            Log.e(TAG, "add child task: invalid index")
            return false
        }

        val pos = childTaskList.indexOf(task)
        if (task != null && pos == -1) {
            childTaskList.add(index, task)

            // update the task list
            var preTask: Task? = null
            var afterTask: Task? = null
            if (index != 0) preTask = childTaskList.get(index - 1)
            if (index != childTaskList.size - 1) afterTask = childTaskList.get(index + 1)

            task.priorSibling = preTask
            if (afterTask != null) afterTask.priorSibling = task
            task.parent = this
        }

        return true
    }

    fun removeChildTask(task: Task): Boolean {
        var ret = false
        val index = childTaskList.indexOf(task)
        if (index != -1) {
            ret = childTaskList.remove(task)

            if (ret) {
                // reset prior sibling and parent
                task.priorSibling = null
                task.parent = null

                // update the task list
                if (index != childTaskList.size) {
                    childTaskList.get(index).priorSibling =
                        if (index == 0) null else childTaskList.get(index - 1)
                }
            }
        }
        return ret
    }

    fun moveChildTask(task: Task, index: Int): Boolean {
        if (index < 0 || index >= childTaskList.size) {
            Log.e(TAG, "move child task: invalid index")
            return false
        }

        val pos = childTaskList.indexOf(task)
        if (pos == -1) {
            Log.e(TAG, "move child task: the task should in the list")
            return false
        }

        if (pos == index) return true
        return (removeChildTask(task) && addChildTask(task, index))
    }

    fun findChildTaskByGid(gid: String?): Task? {
        return childTaskList.firstOrNull { it.gid == gid }
    }

    fun getChildTaskIndex(task: Task?): Int {
        return childTaskList.indexOf(task)
    }

    fun getChildTaskByIndex(index: Int): Task? {
        if (index < 0 || index >= childTaskList.size) {
            Log.e(TAG, "getTaskByIndex: invalid index")
            return null
        }
        return childTaskList.getOrNull(index)
    }

    fun getChilTaskByGid(gid: String?): Task? {
        for (task in this.childTaskList) {
            if (task.gid == gid) return task
        }
        return null
    }

    companion object {
        private val TAG: String = TaskList::class.java.getSimpleName()
    }
}
