package net.micode.notes.data

import android.content.Context
import android.telephony.PhoneNumberUtils
import androidx.room.withTransaction
import net.micode.notes.data.room.AlarmRow
import net.micode.notes.data.room.CallNoteLookupRow
import net.micode.notes.data.room.NoteListRow
import net.micode.notes.data.room.NotesRoomDatabase
import net.micode.notes.data.room.RoomDataEntity
import net.micode.notes.data.room.RoomNoteEntity
import net.micode.notes.data.room.StoredNotePayload
import net.micode.notes.data.room.WidgetNoteRow
import net.micode.notes.tool.NotesSortMode
import net.micode.notes.ui.AppWidgetAttribute

/**
 * 前端调用保存便签时的请求体。
 * 封装所有需要传递给仓库层的字段，避免参数过多。
 */
data class NoteSaveRequest(
    val noteId: Long,           // 现有便签 ID，新建时为 0 或负数
    val folderId: Long,         // 目标文件夹 ID
    val content: String,        // 便签文本内容（片段，用于 snippet）
    val checkListMode: Int,     // 清单模式：0 正常，1 清单
    val alertDate: Long,        // 提醒时间（毫秒）
    val bgColorId: Int,         // 背景颜色 ID
    val widgetId: Int,          // 绑定小部件 ID（0 为无）
    val widgetType: Int,        // 小部件类型
    val modifiedDate: Long,     // 用户指定的修改时间，0 则使用当前时间
    val callDate: Long?,        // 通话日期（仅通话记录便签）
    val phoneNumber: String?    // 电话号码（仅通话记录便签）
)

/**
 * 保存便签后返回的结果。
 */
data class NoteSaveResult(
    val noteId: Long,           // 最终保存后的便签 ID
    val modifiedDate: Long      // 实际写入的修改时间戳
)

/**
 * 便签数据仓库。
 * 
 * 负责协调 Room 数据库的所有操作，向上层提供业务级 API。
 * 单例模式，保证全局仅有一个数据库实例和仓库实例。
 */
class NotesRepository private constructor(context: Context) {
    // 获取 Room 数据库实例
    private val database = NotesRoomDatabase(context.applicationContext)
    // 获取 DAO 接口实例
    private val notesDao = database.notesDao()

    /**
     * 加载指定文件夹下的便签/文件夹列表。
     * 支持搜索过滤和多种排序方式。
     * 
     * @param folderId    文件夹 ID，根文件夹使用 ID_ROOT_FOLDER(0)
     * @param searchText  搜索关键词，空字符串表示不过滤
     * @param sortMode    排序模式（按修改时间降序/升序、按标题升序）
     * @return 列表行数据
     */
    suspend fun loadListItems(
        folderId: Long,
        searchText: String,
        sortMode: NotesSortMode
    ): List<NoteListRow> {
        // 去除搜索文本前后空格
        val trimmedSearchText = searchText.trim()
        
        // 根据是否根文件夹以及是否搜索选择不同的 DAO 方法
        val rows = if (folderId == Notes.ID_ROOT_FOLDER.toLong()) {
            // 根文件夹需要特殊处理：排除系统文件夹，但显示通话记录文件夹（若有子记录）
            if (trimmedSearchText.isEmpty()) {
                notesDao.getRootListItems(
                    folderId = folderId,
                    systemType = Notes.TYPE_SYSTEM,
                    noteType = Notes.TYPE_NOTE,
                    callRecordFolderId = Notes.ID_CALL_RECORD_FOLDER.toLong()
                )
            } else {
                // 带搜索的根列表，搜索关键字两边加 % 实现模糊匹配
                notesDao.searchRootListItems(
                    folderId = folderId,
                    systemType = Notes.TYPE_SYSTEM,
                    noteType = Notes.TYPE_NOTE,
                    callRecordFolderId = Notes.ID_CALL_RECORD_FOLDER.toLong(),
                    searchText = "%$trimmedSearchText%"
                )
            }
        } else {
            // 普通文件夹直接查询其子项
            if (trimmedSearchText.isEmpty()) {
                notesDao.getFolderListItems(folderId, Notes.TYPE_NOTE)
            } else {
                notesDao.searchFolderListItems(
                    folderId = folderId,
                    noteType = Notes.TYPE_NOTE,
                    searchText = "%$trimmedSearchText%"
                )
            }
        }
        
        // 返回排序后的结果（客户端排序，不依赖 SQL 动态排序）
        return rows.sortedWith(noteListComparator(sortMode))
    }

    /**
     * 检查是否存在可见的同名文件夹（回收站中的同名文件夹不冲突）。
     * 用于新建文件夹时的重名校验。
     */
    suspend fun hasVisibleFolderNamed(name: String): Boolean {
        return notesDao.hasVisibleFolderNamed(
            name = name,
            folderType = Notes.TYPE_FOLDER,
            trashFolderId = Notes.ID_TRASH_FOLER.toLong()
        )
    }

    /**
     * 在根目录下创建一个新文件夹。
     * @param name 文件夹名称
     * @return 新文件夹的 ID，插入失败返回 -1
     */
    suspend fun createFolder(name: String): Long {
        val now = System.currentTimeMillis()
        return notesDao.insertNote(
            RoomNoteEntity(
                parentId = Notes.ID_ROOT_FOLDER.toLong(),
                createdDate = now,
                modifiedDate = now,
                snippet = name,               // 文件夹名存储在 snippet 中
                type = Notes.TYPE_FOLDER
            )
        )
    }

    /**
     * 重命名文件夹。
     * @return 是否成功更新（影响行数 > 0）
     */
    suspend fun renameFolder(folderId: Long, name: String): Boolean {
        return notesDao.renameFolder(
            folderId = folderId,
            name = name,
            folderType = Notes.TYPE_FOLDER
        ) > 0
    }

    /**
     * 收集指定文件夹下所有笔记绑定的桌面小部件信息。
     * 返回去重的小部件属性集合（LinkedSet 保持插入顺序）。
     */
    suspend fun collectFolderWidgets(folderId: Long): Set<AppWidgetAttribute> {
        return notesDao.getFolderWidgets(folderId)
            .mapTo(linkedSetOf()) { widget ->
                AppWidgetAttribute(
                    widgetId = widget.widgetId,
                    widgetType = widget.widgetType
                )
            }
    }

    /**
     * 批量删除笔记（包括文件夹及其所有子孙）。
     * 采用递归收集所有后代 ID，然后在一个事务中先删 data 再删 note。
     * 不允许删除根文件夹（ID_ROOT_FOLDER）。
     * 
     * @param noteIds 待删除的笔记 ID 集合
     * @return 是否删除成功
     */
    suspend fun deleteNotes(noteIds: Set<Long>): Boolean {
        // 过滤掉根文件夹的 ID，防止误删
        val validNoteIds = noteIds
            .filter { it != Notes.ID_ROOT_FOLDER.toLong() }
            .distinct()
        if (validNoteIds.isEmpty()) {
            return true  // 没有要删的内容，视为成功
        }

        // 开启事务，确保 data 和 note 同步删除
        return database.withTransaction {
            // 递归查询所有后代 ID
            val allNoteIds = notesDao.getDescendantNoteIds(validNoteIds)
            if (allNoteIds.isEmpty()) {
                return@withTransaction false
            }

            // 先删除所有关联的 data 行
            notesDao.deleteDataByNoteIds(allNoteIds)
            // 再删除所有 note 行，返回影响行数 > 0 表示成功
            notesDao.deleteNotesByIds(allNoteIds) > 0
        }
    }

    /**
     * 删除单个文件夹（本质就是删除它及其子孙）。
     * 特殊处理：不允许删除根文件夹，直接返回 false。
     */
    suspend fun deleteFolder(folderId: Long): Boolean {
        if (folderId == Notes.ID_ROOT_FOLDER.toLong()) {
            return false
        }
        return deleteNotes(setOf(folderId))
    }

    /**
     * 判断笔记是否可见（即存在且不在回收站中）。
     */
    suspend fun isVisibleNote(noteId: Long, type: Int): Boolean {
        return notesDao.isVisibleNote(noteId, type, Notes.ID_TRASH_FOLER.toLong())
    }

    /**
     * 加载指定便签的完整存储数据。
     * 返回一个 StoredNotePayload，包含 note 实体和对应的文本/call data。
     * 如果便签不存在，返回 null。
     */
    suspend fun loadStoredNote(noteId: Long): StoredNotePayload? {
        val note = notesDao.getNoteById(noteId) ?: return null
        val dataRows = notesDao.getDataByNoteId(noteId)
        return StoredNotePayload(
            note = note,
            textData = dataRows.firstOrNull { it.mimeType == Notes.TextNote.CONTENT_ITEM_TYPE },
            callData = dataRows.firstOrNull { it.mimeType == Notes.CallNote.CONTENT_ITEM_TYPE }
        )
    }

    /**
     * 保存便签（新建或更新）。
     * 这是一次事务操作，内部会：
     * 1. 判断是新建还是更新
     * 2. 插入/更新 note 表
     * 3. upsert 文本类型 data（TextNote）
     * 4. upsert 通话记录类型 data（CallNote）
     * 
     * @return NoteSaveResult 包含最终 ID 和修改时间，若失败返回 null
     */
    suspend fun saveNote(request: NoteSaveRequest): NoteSaveResult? {
        return database.withTransaction {
            // 确定修改时间：优先使用传入值，否则用当前时间
            val now = request.modifiedDate.takeIf { it > 0L } ?: System.currentTimeMillis()
            
            // 如果提供了 noteId > 0，则查询现有实体
            val existingNote = if (request.noteId > 0L) {
                notesDao.getNoteById(request.noteId)
            } else {
                null
            }
            // 要求更新的便签必须存在，否则失败
            if (request.noteId > 0L && existingNote == null) {
                return@withTransaction null
            }

            // 如果是新建，先插入得到 noteId；否则使用现有 noteId
            val noteId = existingNote?.id ?: notesDao.insertNote(
                RoomNoteEntity(
                    parentId = request.folderId,
                    alertedDate = request.alertDate,
                    bgColorId = request.bgColorId,
                    createdDate = now,
                    modifiedDate = now,
                    snippet = request.content,
                    type = Notes.TYPE_NOTE,
                    widgetId = request.widgetId,
                    widgetType = request.widgetType,
                    localModified = 1  // 标记为本地已修改，用于同步
                )
            )

            // 插入失败则返回 null
            if (noteId <= 0L) {
                return@withTransaction null
            }

            // 如果是更新现有笔记，执行 update
            if (existingNote != null) {
                notesDao.updateNote(
                    existingNote.copy(
                        parentId = request.folderId,
                        alertedDate = request.alertDate,
                        bgColorId = request.bgColorId,
                        modifiedDate = now,
                        snippet = request.content,
                        type = Notes.TYPE_NOTE,
                        widgetId = request.widgetId,
                        widgetType = request.widgetType,
                        localModified = 1
                    )
                )
            }

            // 处理文本内容 upsert
            upsertTextData(noteId, request, now)
            // 处理通话记录 upsert（如果有的话）
            upsertCallData(noteId, request, now)

            NoteSaveResult(
                noteId = noteId,
                modifiedDate = now
            )
        }
    }

    /**
     * 通过电话号码和通话日期查找对应的通话记录便签 ID。
     * 首先通过日期找到所有候选，再用 PhoneNumberUtils 进行号码精确匹配。
     * @return 匹配的 noteId，未找到返回 0
     */
    suspend fun getNoteIdByPhoneNumberAndCallDate(phoneNumber: String, callDate: Long): Long {
        val candidates = notesDao.findCallNotesByDate(
            callDate = callDate,
            mimeType = Notes.CallNote.CONTENT_ITEM_TYPE
        )
        return candidates.firstOrNull { candidate ->
            areSamePhoneNumber(phoneNumber, candidate)
        }?.noteId ?: 0L
    }

    /**
     * 获取便签的文本摘要（用于快速预览）。
     */
    suspend fun getSnippetById(noteId: Long): String? {
        return notesDao.getSnippetById(noteId)
    }

    /**
     * 获取所有未来需要提醒的普通便签（提醒时间 > currentDate）。
     */
    suspend fun getFutureAlertNotes(currentDate: Long): List<AlarmRow> {
        return notesDao.getFutureAlertNotes(currentDate, Notes.TYPE_NOTE)
    }

    /**
     * 根据桌面小部件 ID 获取其绑定的便签列表（排除回收站中的）。
     */
    suspend fun getNotesByWidgetId(appWidgetId: Int): List<WidgetNoteRow> {
        return notesDao.getNotesByWidgetId(
            appWidgetId = appWidgetId,
            trashFolderId = Notes.ID_TRASH_FOLER.toLong()
        )
    }

    /**
     * 清除指定小部件的绑定关系（将 widget_id 设置为无效值）。
     * @return 操作是否成功
     */
    suspend fun clearWidgetBinding(appWidgetId: Int, invalidWidgetId: Int): Boolean {
        return notesDao.clearWidgetBinding(appWidgetId, invalidWidgetId) > 0
    }

    /**
     * 对文本类型 data 行执行 upsert（存在则更新，不存在则插入）。
     */
    private suspend fun upsertTextData(
        noteId: Long,
        request: NoteSaveRequest,
        now: Long
    ) {
        // 查找当前便签已有的文本数据
        val existingTextData = notesDao.getDataByNoteIdAndMimeType(noteId, Notes.TextNote.CONTENT_ITEM_TYPE)
        val content = request.content
        val modeValue = request.checkListMode.toLong()

        if (existingTextData == null) {
            // 插入新行
            notesDao.insertData(
                RoomDataEntity(
                    mimeType = Notes.TextNote.CONTENT_ITEM_TYPE,
                    noteId = noteId,
                    createdDate = now,
                    modifiedDate = now,
                    content = content,
                    data1 = modeValue   // 清单模式标志存入 data1
                )
            )
        } else {
            // 更新现存行
            notesDao.updateData(
                existingTextData.copy(
                    noteId = noteId,
                    modifiedDate = now,
                    content = content,
                    data1 = modeValue
                )
            )
        }
    }

    /**
     * 对通话记录类型 data 行执行 upsert。
     * 如果请求中没有提供电话号码且 callDate 为 null，且之前也没有老数据，则什么都不做。
     */
    private suspend fun upsertCallData(
        noteId: Long,
        request: NoteSaveRequest,
        now: Long
    ) {
        val existingCallData = notesDao.getDataByNoteIdAndMimeType(noteId, Notes.CallNote.CONTENT_ITEM_TYPE)
        
        // 既没有已有数据，也没有新数据提供，直接返回
        if (existingCallData == null && request.phoneNumber.isNullOrBlank() && request.callDate == null) {
            return
        }

        if (existingCallData == null) {
            notesDao.insertData(
                RoomDataEntity(
                    mimeType = Notes.CallNote.CONTENT_ITEM_TYPE,
                    noteId = noteId,
                    createdDate = now,
                    modifiedDate = now,
                    data1 = request.callDate,          // 通话日期存入 data1
                    data3 = request.phoneNumber.orEmpty()  // 电话号码存入 data3
                )
            )
        } else {
            notesDao.updateData(
                existingCallData.copy(
                    noteId = noteId,
                    modifiedDate = now,
                    data1 = request.callDate,
                    data3 = request.phoneNumber.orEmpty()
                )
            )
        }
    }

    /**
     * 利用系统 PhoneNumberUtils 判断两个号码是否相同（忽略格式差异）。
     * 参数中的 candidate 来自数据库查询结果。
     */
    @Suppress("DEPRECATION")
    private fun areSamePhoneNumber(phoneNumber: String, candidate: CallNoteLookupRow): Boolean {
        return PhoneNumberUtils.compare(phoneNumber, candidate.phoneNumber)
    }

    // ==================== 伴生对象：单例与排序工具 ====================
    companion object {
        /**
         * 根据排序模式返回对应的比较器。
         * 
         * 排序规则：
         * 1. 首先按类型分组：系统文件夹(0) → 文件夹(1) → 便签(2)
         * 2. 然后在组内：
         *    - MODIFIED_DESC：修改时间降序，相同则按标题字母升序
         *    - MODIFIED_ASC：修改时间升序，相同则按标题字母升序
         *    - TITLE_ASC：标题字母升序，相同则按修改时间降序
         */
        private fun noteListComparator(sortMode: NotesSortMode): Comparator<NoteListRow> {
            // 分组比较器：类型分组保证系统文件夹总是在最前面
            val groupComparator = compareBy<NoteListRow> {
                when (it.type) {
                    Notes.TYPE_SYSTEM -> 0
                    Notes.TYPE_FOLDER -> 1
                    else -> 2
                }
            }
            // 标题提取器：去除首尾空格并小写化，实现不区分大小写的排序
            val titleSelector: (NoteListRow) -> String = { row ->
                row.snippet.trim().lowercase()
            }

            return when (sortMode) {
                NotesSortMode.MODIFIED_DESC -> groupComparator
                    .thenByDescending { it.modifiedDate }
                    .thenBy { titleSelector(it) }

                NotesSortMode.MODIFIED_ASC -> groupComparator
                    .thenBy { it.modifiedDate }
                    .thenBy { titleSelector(it) }

                NotesSortMode.TITLE_ASC -> groupComparator
                    .thenBy { titleSelector(it) }
                    .thenByDescending { it.modifiedDate }
            }
        }

        /** 单例实例，@Volatile 保证可见性 */
        @Volatile
        private var instance: NotesRepository? = null

        /**
         * 获取仓库单例的入口。
         * 双重检查锁定线程安全，传入 Context 采用 applicationContext 防止内存泄漏。
         */
        operator fun invoke(context: Context): NotesRepository {
            return instance ?: synchronized(this) {
                instance ?: NotesRepository(context.applicationContext).also { repository ->
                    instance = repository
                }
            }
        }
    }
}