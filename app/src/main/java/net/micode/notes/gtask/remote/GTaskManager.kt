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
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.util.Log
import net.micode.notes.R
import net.micode.notes.data.Notes
import net.micode.notes.data.Notes.NoteColumns
import net.micode.notes.gtask.data.MetaData
import net.micode.notes.gtask.data.Node
import net.micode.notes.gtask.data.SqlNote
import net.micode.notes.gtask.data.Task
import net.micode.notes.gtask.data.TaskList
import net.micode.notes.gtask.exception.ActionFailureException
import net.micode.notes.gtask.exception.NetworkFailureException
import net.micode.notes.tool.DataUtils
import net.micode.notes.tool.GTaskStringUtils
import org.json.JSONException
import org.json.JSONObject

class GTaskManager private constructor() {
    private var mActivity: Activity? = null

    private var mContext: Context? = null

    private var mContentResolver: ContentResolver? = null

    private var mSyncing = false

    private var mCancelled = false

    private val mGTaskListHashMap: HashMap<String?, TaskList?>

    private val mGTaskHashMap: HashMap<String?, Node?>

    private val mMetaHashMap: HashMap<String?, MetaData?>

    private var mMetaList: TaskList? = null

    private val mLocalDeleteIdMap: HashSet<Long?>

    private val mGidToNid: HashMap<String?, Long?>

    private val mNidToGid: HashMap<Long?, String?>

    init {
        mGTaskListHashMap = HashMap<String?, TaskList?>()
        mGTaskHashMap = HashMap<String?, Node?>()
        mMetaHashMap = HashMap<String?, MetaData?>()
        mLocalDeleteIdMap = HashSet<Long?>()
        mGidToNid = HashMap<String?, Long?>()
        mNidToGid = HashMap<Long?, String?>()
    }

    @Synchronized
    fun setActivityContext(activity: Activity?) {
        // used for getting authtoken
        mActivity = activity
    }

    fun sync(context: Context, asyncTask: GTaskASyncTask): Int {
        if (mSyncing) {
            Log.d(TAG, "Sync is in progress")
            return STATE_SYNC_IN_PROGRESS
        }
        mContext = context
        mContentResolver = mContext!!.getContentResolver()
        mSyncing = true
        mCancelled = false
        mGTaskListHashMap.clear()
        mGTaskHashMap.clear()
        mMetaHashMap.clear()
        mLocalDeleteIdMap.clear()
        mGidToNid.clear()
        mNidToGid.clear()

        try {
            val client: GTaskClient = GTaskClient.Companion.getInstance()
            client.resetUpdateArray()

            // login google task
            if (!mCancelled) {
                if (!client.login(mActivity)) {
                    throw NetworkFailureException("login google task failed")
                }
            }

            // get the task list from google
            asyncTask.publishProgess(mContext!!.getString(R.string.sync_progress_init_list))
            initGTaskList()

            // do content sync work
            asyncTask.publishProgess(mContext!!.getString(R.string.sync_progress_syncing))
            syncContent()
        } catch (e: NetworkFailureException) {
            Log.e(TAG, e.toString())
            return STATE_NETWORK_ERROR
        } catch (e: ActionFailureException) {
            Log.e(TAG, e.toString())
            return STATE_INTERNAL_ERROR
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
            return STATE_INTERNAL_ERROR
        } finally {
            mGTaskListHashMap.clear()
            mGTaskHashMap.clear()
            mMetaHashMap.clear()
            mLocalDeleteIdMap.clear()
            mGidToNid.clear()
            mNidToGid.clear()
            mSyncing = false
        }

        return if (mCancelled) STATE_SYNC_CANCELLED else STATE_SUCCESS
    }

    @Throws(NetworkFailureException::class)
    private fun initGTaskList() {
        if (mCancelled) return
        val client: GTaskClient = GTaskClient.Companion.getInstance()
        try {
            val jsTaskLists = client.getTaskLists()

            // init meta list first
            mMetaList = null
            for (i in 0..<jsTaskLists.length()) {
                var `object` = jsTaskLists.getJSONObject(i)
                val gid = `object`.getString(GTaskStringUtils.GTASK_JSON_ID)
                val name = `object`.getString(GTaskStringUtils.GTASK_JSON_NAME)

                if (name
                    == GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_META
                ) {
                    mMetaList = TaskList()
                    mMetaList!!.setContentByRemoteJSON(`object`)

                    // load meta data
                    val jsMetas = client.getTaskList(gid)
                    for (j in 0..<jsMetas.length()) {
                        `object` = jsMetas.getJSONObject(j) as JSONObject
                        val metaData = MetaData()
                        metaData.setContentByRemoteJSON(`object`)
                        if (metaData.isWorthSaving()) {
                            mMetaList!!.addChildTask(metaData)
                            if (metaData.getGid() != null) {
                                mMetaHashMap.put(metaData.getRelatedGid(), metaData)
                            }
                        }
                    }
                }
            }

            // create meta list if not existed
            if (mMetaList == null) {
                mMetaList = TaskList()
                mMetaList!!.setName(
                    GTaskStringUtils.MIUI_FOLDER_PREFFIX
                            + GTaskStringUtils.FOLDER_META
                )
                GTaskClient.Companion.getInstance().createTaskList(mMetaList)
            }

            // init task list
            for (i in 0..<jsTaskLists.length()) {
                var `object` = jsTaskLists.getJSONObject(i)
                var gid = `object`.getString(GTaskStringUtils.GTASK_JSON_ID)
                val name = `object`.getString(GTaskStringUtils.GTASK_JSON_NAME)

                if (name.startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX)
                    && name != (GTaskStringUtils.MIUI_FOLDER_PREFFIX
                            + GTaskStringUtils.FOLDER_META)
                ) {
                    val tasklist = TaskList()
                    tasklist.setContentByRemoteJSON(`object`)
                    mGTaskListHashMap.put(gid, tasklist)
                    mGTaskHashMap.put(gid, tasklist)

                    // load tasks
                    val jsTasks = client.getTaskList(gid)
                    for (j in 0..<jsTasks.length()) {
                        `object` = jsTasks.getJSONObject(j) as JSONObject
                        gid = `object`.getString(GTaskStringUtils.GTASK_JSON_ID)
                        val task = Task()
                        task.setContentByRemoteJSON(`object`)
                        if (task.isWorthSaving()) {
                            task.setMetaInfo(mMetaHashMap.get(gid))
                            tasklist.addChildTask(task)
                            mGTaskHashMap.put(gid, task)
                        }
                    }
                }
            }
        } catch (e: JSONException) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
            throw ActionFailureException("initGTaskList: handing JSONObject failed")
        }
    }

    @Throws(NetworkFailureException::class)
    private fun syncContent() {
        var syncType: Int
        var c: Cursor? = null
        var gid: String?
        var node: Node?

        mLocalDeleteIdMap.clear()

        if (mCancelled) {
            return
        }

        // for local deleted note
        try {
            c = mContentResolver!!.query(
                Notes.CONTENT_NOTE_URI, SqlNote.Companion.PROJECTION_NOTE,
                "(type<>? AND parent_id=?)", arrayOf<String>(
                    Notes.TYPE_SYSTEM.toString(), Notes.ID_TRASH_FOLER.toString()
                ), null
            )
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.Companion.GTASK_ID_COLUMN)
                    node = mGTaskHashMap.get(gid)
                    if (node != null) {
                        mGTaskHashMap.remove(gid)
                        doContentSync(Node.Companion.SYNC_ACTION_DEL_REMOTE, node, c)
                    }

                    mLocalDeleteIdMap.add(c.getLong(SqlNote.Companion.ID_COLUMN))
                }
            } else {
                Log.w(TAG, "failed to query trash folder")
            }
        } finally {
            if (c != null) {
                c.close()
                c = null
            }
        }

        // sync folder first
        syncFolder()

        // for note existing in database
        try {
            c = mContentResolver!!.query(
                Notes.CONTENT_NOTE_URI, SqlNote.Companion.PROJECTION_NOTE,
                "(type=? AND parent_id<>?)", arrayOf<String>(
                    Notes.TYPE_NOTE.toString(), Notes.ID_TRASH_FOLER.toString()
                ), NoteColumns.Companion.TYPE + " DESC"
            )
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.Companion.GTASK_ID_COLUMN)
                    node = mGTaskHashMap.get(gid)
                    if (node != null) {
                        mGTaskHashMap.remove(gid)
                        mGidToNid.put(gid, c.getLong(SqlNote.Companion.ID_COLUMN))
                        mNidToGid.put(c.getLong(SqlNote.Companion.ID_COLUMN), gid)
                        syncType = node.getSyncAction(c)
                    } else {
                        if (c.getString(SqlNote.Companion.GTASK_ID_COLUMN)
                                .trim { it <= ' ' }.length == 0
                        ) {
                            // local add
                            syncType = Node.Companion.SYNC_ACTION_ADD_REMOTE
                        } else {
                            // remote delete
                            syncType = Node.Companion.SYNC_ACTION_DEL_LOCAL
                        }
                    }
                    doContentSync(syncType, node!!, c)
                }
            } else {
                Log.w(TAG, "failed to query existing note in database")
            }
        } finally {
            if (c != null) {
                c.close()
                c = null
            }
        }

        // go through remaining items
        val iter = mGTaskHashMap.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            node = entry.value
            doContentSync(Node.Companion.SYNC_ACTION_ADD_LOCAL, node!!, null)
        }

        // mCancelled can be set by another thread, so we neet to check one by
        // one
        // clear local delete table
        if (!mCancelled) {
            if (!DataUtils.batchDeleteNotes(mContentResolver, mLocalDeleteIdMap)) {
                throw ActionFailureException("failed to batch-delete local deleted notes")
            }
        }

        // refresh local sync id
        if (!mCancelled) {
            GTaskClient.Companion.getInstance().commitUpdate()
            refreshLocalSyncId()
        }
    }

    @Throws(NetworkFailureException::class)
    private fun syncFolder() {
        var c: Cursor? = null
        var gid: String?
        var node: Node?
        var syncType: Int

        if (mCancelled) {
            return
        }

        // for root folder
        try {
            c = mContentResolver!!.query(
                ContentUris.withAppendedId(
                    Notes.CONTENT_NOTE_URI,
                    Notes.ID_ROOT_FOLDER.toLong()
                ), SqlNote.Companion.PROJECTION_NOTE, null, null, null
            )
            if (c != null) {
                c.moveToNext()
                gid = c.getString(SqlNote.Companion.GTASK_ID_COLUMN)
                node = mGTaskHashMap.get(gid)
                if (node != null) {
                    mGTaskHashMap.remove(gid)
                    mGidToNid.put(gid, Notes.ID_ROOT_FOLDER.toLong())
                    mNidToGid.put(Notes.ID_ROOT_FOLDER.toLong(), gid)
                    // for system folder, only update remote name if necessary
                    if (node.getName() != GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT) doContentSync(
                        Node.Companion.SYNC_ACTION_UPDATE_REMOTE, node, c
                    )
                } else {
                    doContentSync(Node.Companion.SYNC_ACTION_ADD_REMOTE, node, c)
                }
            } else {
                Log.w(TAG, "failed to query root folder")
            }
        } finally {
            if (c != null) {
                c.close()
                c = null
            }
        }

        // for call-note folder
        try {
            c = mContentResolver!!.query(
                Notes.CONTENT_NOTE_URI, SqlNote.Companion.PROJECTION_NOTE, "(_id=?)",
                arrayOf<String>(
                    Notes.ID_CALL_RECORD_FOLDER.toString()
                ), null
            )
            if (c != null) {
                if (c.moveToNext()) {
                    gid = c.getString(SqlNote.Companion.GTASK_ID_COLUMN)
                    node = mGTaskHashMap.get(gid)
                    if (node != null) {
                        mGTaskHashMap.remove(gid)
                        mGidToNid.put(gid, Notes.ID_CALL_RECORD_FOLDER.toLong())
                        mNidToGid.put(Notes.ID_CALL_RECORD_FOLDER.toLong(), gid)
                        // for system folder, only update remote name if
                        // necessary
                        if (node.getName() != (GTaskStringUtils.MIUI_FOLDER_PREFFIX
                                    + GTaskStringUtils.FOLDER_CALL_NOTE)
                        ) doContentSync(Node.Companion.SYNC_ACTION_UPDATE_REMOTE, node, c)
                    } else {
                        doContentSync(Node.Companion.SYNC_ACTION_ADD_REMOTE, node, c)
                    }
                }
            } else {
                Log.w(TAG, "failed to query call note folder")
            }
        } finally {
            if (c != null) {
                c.close()
                c = null
            }
        }

        // for local existing folders
        try {
            c = mContentResolver!!.query(
                Notes.CONTENT_NOTE_URI, SqlNote.Companion.PROJECTION_NOTE,
                "(type=? AND parent_id<>?)", arrayOf<String>(
                    Notes.TYPE_FOLDER.toString(), Notes.ID_TRASH_FOLER.toString()
                ), NoteColumns.Companion.TYPE + " DESC"
            )
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.Companion.GTASK_ID_COLUMN)
                    node = mGTaskHashMap.get(gid)
                    if (node != null) {
                        mGTaskHashMap.remove(gid)
                        mGidToNid.put(gid, c.getLong(SqlNote.Companion.ID_COLUMN))
                        mNidToGid.put(c.getLong(SqlNote.Companion.ID_COLUMN), gid)
                        syncType = node.getSyncAction(c)
                    } else {
                        if (c.getString(SqlNote.Companion.GTASK_ID_COLUMN)
                                .trim { it <= ' ' }.length == 0
                        ) {
                            // local add
                            syncType = Node.Companion.SYNC_ACTION_ADD_REMOTE
                        } else {
                            // remote delete
                            syncType = Node.Companion.SYNC_ACTION_DEL_LOCAL
                        }
                    }
                    doContentSync(syncType, node!!, c)
                }
            } else {
                Log.w(TAG, "failed to query existing folder")
            }
        } finally {
            if (c != null) {
                c.close()
                c = null
            }
        }

        // for remote add folders
        val iter = mGTaskListHashMap.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            gid = entry.key
            node = entry.value
            if (mGTaskHashMap.containsKey(gid)) {
                mGTaskHashMap.remove(gid)
                doContentSync(Node.Companion.SYNC_ACTION_ADD_LOCAL, node!!, null)
            }
        }

        if (!mCancelled) GTaskClient.Companion.getInstance().commitUpdate()
    }

    @Throws(NetworkFailureException::class)
    private fun doContentSync(syncType: Int, node: Node, c: Cursor) {
        if (mCancelled) {
            return
        }

        val meta: MetaData?
        when (syncType) {
            Node.Companion.SYNC_ACTION_ADD_LOCAL -> addLocalNode(node)
            Node.Companion.SYNC_ACTION_ADD_REMOTE -> addRemoteNode(node, c)
            Node.Companion.SYNC_ACTION_DEL_LOCAL -> {
                meta = mMetaHashMap.get(c.getString(SqlNote.Companion.GTASK_ID_COLUMN))
                if (meta != null) {
                    GTaskClient.Companion.getInstance().deleteNode(meta)
                }
                mLocalDeleteIdMap.add(c.getLong(SqlNote.Companion.ID_COLUMN))
            }

            Node.Companion.SYNC_ACTION_DEL_REMOTE -> {
                meta = mMetaHashMap.get(node.getGid())
                if (meta != null) {
                    GTaskClient.Companion.getInstance().deleteNode(meta)
                }
                GTaskClient.Companion.getInstance().deleteNode(node)
            }

            Node.Companion.SYNC_ACTION_UPDATE_LOCAL -> updateLocalNode(node, c)
            Node.Companion.SYNC_ACTION_UPDATE_REMOTE -> updateRemoteNode(node, c)
            Node.Companion.SYNC_ACTION_UPDATE_CONFLICT ->                 // merging both modifications maybe a good idea
                // right now just use local update simply
                updateRemoteNode(node, c)

            Node.Companion.SYNC_ACTION_NONE -> {}
            Node.Companion.SYNC_ACTION_ERROR -> throw ActionFailureException("unkown sync action type")
            else -> throw ActionFailureException("unkown sync action type")
        }
    }

    @Throws(NetworkFailureException::class)
    private fun addLocalNode(node: Node) {
        if (mCancelled) {
            return
        }

        val sqlNote: SqlNote?
        if (node is TaskList) {
            if (node.getName() == GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT) {
                sqlNote = SqlNote(mContext, Notes.ID_ROOT_FOLDER.toLong())
            } else if (node.getName() == GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_CALL_NOTE) {
                sqlNote = SqlNote(mContext, Notes.ID_CALL_RECORD_FOLDER.toLong())
            } else {
                sqlNote = SqlNote(mContext)
                sqlNote.setContent(node.getLocalJSONFromContent())
                sqlNote.setParentId(Notes.ID_ROOT_FOLDER.toLong())
            }
        } else {
            sqlNote = SqlNote(mContext)
            val js = node.getLocalJSONFromContent()
            try {
                if (js.has(GTaskStringUtils.META_HEAD_NOTE)) {
                    val note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE)
                    if (note.has(NoteColumns.Companion.ID)) {
                        val id = note.getLong(NoteColumns.Companion.ID)
                        if (DataUtils.existInNoteDatabase(mContentResolver, id)) {
                            // the id is not available, have to create a new one
                            note.remove(NoteColumns.Companion.ID)
                        }
                    }
                }

                if (js.has(GTaskStringUtils.META_HEAD_DATA)) {
                    val dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA)
                    for (i in 0..<dataArray.length()) {
                        val data = dataArray.getJSONObject(i)
                        if (data.has(Notes.DataColumns.Companion.ID)) {
                            val dataId = data.getLong(Notes.DataColumns.Companion.ID)
                            if (DataUtils.existInDataDatabase(mContentResolver, dataId)) {
                                // the data id is not available, have to create
                                // a new one
                                data.remove(Notes.DataColumns.Companion.ID)
                            }
                        }
                    }
                }
            } catch (e: JSONException) {
                Log.w(TAG, e.toString())
                e.printStackTrace()
            }
            sqlNote.setContent(js)

            val parentId = mGidToNid.get((node as Task).getParent().getGid())
            if (parentId == null) {
                Log.e(TAG, "cannot find task's parent id locally")
                throw ActionFailureException("cannot add local node")
            }
            sqlNote.setParentId(parentId)
        }

        // create the local node
        sqlNote.setGtaskId(node.getGid())
        sqlNote.commit(false)

        // update gid-nid mapping
        mGidToNid.put(node.getGid(), sqlNote.getId())
        mNidToGid.put(sqlNote.getId(), node.getGid())

        // update meta
        updateRemoteMeta(node.getGid(), sqlNote)
    }

    @Throws(NetworkFailureException::class)
    private fun updateLocalNode(node: Node, c: Cursor?) {
        if (mCancelled) {
            return
        }

        val sqlNote: SqlNote?
        // update the note locally
        sqlNote = SqlNote(mContext, c)
        sqlNote.setContent(node.getLocalJSONFromContent())

        val parentId = if (node is Task)
            mGidToNid.get(node.getParent().getGid())
        else Notes.ID_ROOT_FOLDER.toLong()
        if (parentId == null) {
            Log.e(TAG, "cannot find task's parent id locally")
            throw ActionFailureException("cannot update local node")
        }
        sqlNote.setParentId(parentId)
        sqlNote.commit(true)

        // update meta info
        updateRemoteMeta(node.getGid(), sqlNote)
    }

    @Throws(NetworkFailureException::class)
    private fun addRemoteNode(node: Node?, c: Cursor?) {
        if (mCancelled) {
            return
        }

        val sqlNote = SqlNote(mContext, c)
        val n: Node?

        // update remotely
        if (sqlNote.isNoteType()) {
            val task = Task()
            task.setContentByLocalJSON(sqlNote.getContent())

            val parentGid = mNidToGid.get(sqlNote.getParentId())
            if (parentGid == null) {
                Log.e(TAG, "cannot find task's parent tasklist")
                throw ActionFailureException("cannot add remote task")
            }
            mGTaskListHashMap.get(parentGid)!!.addChildTask(task)

            GTaskClient.Companion.getInstance().createTask(task)
            n = task as Node

            // add meta
            updateRemoteMeta(task.getGid(), sqlNote)
        } else {
            var tasklist: TaskList? = null

            // we need to skip folder if it has already existed
            var folderName: String? = GTaskStringUtils.MIUI_FOLDER_PREFFIX
            if (sqlNote.getId() == Notes.ID_ROOT_FOLDER.toLong()) folderName += GTaskStringUtils.FOLDER_DEFAULT
            else if (sqlNote.getId() == Notes.ID_CALL_RECORD_FOLDER.toLong()) folderName += GTaskStringUtils.FOLDER_CALL_NOTE
            else folderName += sqlNote.getSnippet()

            val iter = mGTaskListHashMap.entries.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                val gid = entry.key
                val list = entry.value

                if (list.getName() == folderName) {
                    tasklist = list
                    if (mGTaskHashMap.containsKey(gid)) {
                        mGTaskHashMap.remove(gid)
                    }
                    break
                }
            }

            // no match we can add now
            if (tasklist == null) {
                tasklist = TaskList()
                tasklist.setContentByLocalJSON(sqlNote.getContent())
                GTaskClient.Companion.getInstance().createTaskList(tasklist)
                mGTaskListHashMap.put(tasklist.getGid(), tasklist)
            }
            n = tasklist as Node
        }

        // update local note
        sqlNote.setGtaskId(n.getGid())
        sqlNote.commit(false)
        sqlNote.resetLocalModified()
        sqlNote.commit(true)

        // gid-id mapping
        mGidToNid.put(n.getGid(), sqlNote.getId())
        mNidToGid.put(sqlNote.getId(), n.getGid())
    }

    @Throws(NetworkFailureException::class)
    private fun updateRemoteNode(node: Node, c: Cursor?) {
        if (mCancelled) {
            return
        }

        val sqlNote = SqlNote(mContext, c)

        // update remotely
        node.setContentByLocalJSON(sqlNote.getContent())
        GTaskClient.Companion.getInstance().addUpdateNode(node)

        // update meta
        updateRemoteMeta(node.getGid(), sqlNote)

        // move task if necessary
        if (sqlNote.isNoteType()) {
            val task = node as Task
            val preParentList = task.getParent()

            val curParentGid = mNidToGid.get(sqlNote.getParentId())
            if (curParentGid == null) {
                Log.e(TAG, "cannot find task's parent tasklist")
                throw ActionFailureException("cannot update remote task")
            }
            val curParentList = mGTaskListHashMap.get(curParentGid)

            if (preParentList !== curParentList) {
                preParentList.removeChildTask(task)
                curParentList!!.addChildTask(task)
                GTaskClient.Companion.getInstance().moveTask(task, preParentList, curParentList)
            }
        }

        // clear local modified flag
        sqlNote.resetLocalModified()
        sqlNote.commit(true)
    }

    @Throws(NetworkFailureException::class)
    private fun updateRemoteMeta(gid: String?, sqlNote: SqlNote?) {
        if (sqlNote != null && sqlNote.isNoteType()) {
            var metaData = mMetaHashMap.get(gid)
            if (metaData != null) {
                metaData.setMeta(gid, sqlNote.getContent())
                GTaskClient.Companion.getInstance().addUpdateNode(metaData)
            } else {
                metaData = MetaData()
                metaData.setMeta(gid, sqlNote.getContent())
                mMetaList!!.addChildTask(metaData)
                mMetaHashMap.put(gid, metaData)
                GTaskClient.Companion.getInstance().createTask(metaData)
            }
        }
    }

    @Throws(NetworkFailureException::class)
    private fun refreshLocalSyncId() {
        if (mCancelled) {
            return
        }

        // get the latest gtask list
        mGTaskHashMap.clear()
        mGTaskListHashMap.clear()
        mMetaHashMap.clear()
        initGTaskList()

        var c: Cursor? = null
        try {
            c = mContentResolver!!.query(
                Notes.CONTENT_NOTE_URI, SqlNote.Companion.PROJECTION_NOTE,
                "(type<>? AND parent_id<>?)", arrayOf<String>(
                    Notes.TYPE_SYSTEM.toString(), Notes.ID_TRASH_FOLER.toString()
                ), NoteColumns.Companion.TYPE + " DESC"
            )
            if (c != null) {
                while (c.moveToNext()) {
                    val gid = c.getString(SqlNote.Companion.GTASK_ID_COLUMN)
                    val node = mGTaskHashMap.get(gid)
                    if (node != null) {
                        mGTaskHashMap.remove(gid)
                        val values = ContentValues()
                        values.put(NoteColumns.Companion.SYNC_ID, node.getLastModified())
                        mContentResolver!!.update(
                            ContentUris.withAppendedId(
                                Notes.CONTENT_NOTE_URI,
                                c.getLong(SqlNote.Companion.ID_COLUMN)
                            ), values, null, null
                        )
                    } else {
                        Log.e(TAG, "something is missed")
                        throw ActionFailureException(
                            "some local items don't have gid after sync"
                        )
                    }
                }
            } else {
                Log.w(TAG, "failed to query local note to refresh sync id")
            }
        } finally {
            if (c != null) {
                c.close()
                c = null
            }
        }
    }

    val syncAccount: String
        get() = GTaskClient.Companion.getInstance().getSyncAccount().name

    fun cancelSync() {
        mCancelled = true
    }

    companion object {
        private val TAG: String = GTaskManager::class.java.getSimpleName()

        const val STATE_SUCCESS: Int = 0

        const val STATE_NETWORK_ERROR: Int = 1

        const val STATE_INTERNAL_ERROR: Int = 2

        const val STATE_SYNC_IN_PROGRESS: Int = 3

        const val STATE_SYNC_CANCELLED: Int = 4

        private var mInstance: GTaskManager? = null

        @get:Synchronized
        val instance: GTaskManager
            get() {
                if (mInstance == null) {
                    mInstance = GTaskManager()
                }
                return mInstance!!
            }
    }
}
