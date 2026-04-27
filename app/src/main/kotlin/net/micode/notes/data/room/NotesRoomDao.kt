// 声明包路径，属于 data 模块下的 Room 数据库相关类
package net.micode.notes.data.room

// 导入 Room 注解和基础类
import androidx.room.Dao                   // 标识该接口为数据访问对象
import androidx.room.Insert               // 标识插入操作
import androidx.room.Query                // 自定义 SQL 查询
import androidx.room.Update               // 标识更新操作

/**
 * 小米便签 Room 数据库的 DAO 接口。
 * 封装了对 note（便签/文件夹）和 data（便签内容）表的所有原子操作。
 * 所有公开方法均为 suspend 函数，适合在协程中调用，避免阻塞主线程。
 */
@Dao
interface NotesRoomDao {

    /**
     * 获取主列表（根目录）显示的便签/文件夹列表。
     * 查询逻辑：
     * - 属于当前文件夹（folderId）且不是系统类型的笔记；
     * - 或者当前文件夹本身是通话记录文件夹时，也显示其包含子笔记的通话记录条目；
     * - 计算每个笔记的子笔记数量（非文件夹类型的 notes_count 置为 0）；
     * - 排序列：类型降序（文件夹在前），再按修改时间降序。
     *
     * @param folderId  当前文件夹（父级）ID
     * @param systemType 系统笔记类型（如通话记录、系统文件夹），在 WHERE 中会排除
     * @param noteType   普通笔记的类型值，用于计算子笔记数时判断
     * @param callRecordFolderId 通话记录文件夹的特殊 ID（允许展示有子笔记的通话记录）
     * @return 列表行数据的集合，字段映射到 NoteListRow
     */
    @Query(
        """
        SELECT noteItem._id, noteItem.alert_date, noteItem.bg_color_id, noteItem.has_attachment,
               noteItem.modified_date,
               CASE
                   WHEN noteItem.type = :noteType THEN 0
                   ELSE (SELECT COUNT(*) FROM note AS child WHERE child.parent_id = noteItem._id)
               END AS notes_count,
               noteItem.parent_id, noteItem.snippet, noteItem.type, noteItem.widget_id,
               noteItem.widget_type
        FROM note AS noteItem
        WHERE ((noteItem.type <> :systemType AND noteItem.parent_id = :folderId)
            OR (noteItem._id = :callRecordFolderId
                AND EXISTS(SELECT 1 FROM note AS child WHERE child.parent_id = noteItem._id)))
        ORDER BY noteItem.type DESC, noteItem.modified_date DESC
        """
    )
    suspend fun getRootListItems(
        folderId: Long,
        systemType: Int,
        noteType: Int,
        callRecordFolderId: Long
    ): List<NoteListRow>

    /**
     * 在主列表中搜索（根据摘要文本过滤）。
     * SQL 与 getRootListItems 基本相同，仅增加了 snippet LIKE 条件。
     * 参数多了一个 searchText，使用 % 通配符需调用方构造（如 "%keyword%"）。
     */
    @Query(
        """
        SELECT noteItem._id, noteItem.alert_date, noteItem.bg_color_id, noteItem.has_attachment,
               noteItem.modified_date,
               CASE
                   WHEN noteItem.type = :noteType THEN 0
                   ELSE (SELECT COUNT(*) FROM note AS child WHERE child.parent_id = noteItem._id)
               END AS notes_count,
               noteItem.parent_id, noteItem.snippet, noteItem.type, noteItem.widget_id,
               noteItem.widget_type
        FROM note AS noteItem
        WHERE (((noteItem.type <> :systemType AND noteItem.parent_id = :folderId)
            OR (noteItem._id = :callRecordFolderId
                AND EXISTS(SELECT 1 FROM note AS child WHERE child.parent_id = noteItem._id)))
            AND noteItem.snippet LIKE :searchText)
        ORDER BY noteItem.type DESC, noteItem.modified_date DESC
        """
    )
    suspend fun searchRootListItems(
        folderId: Long,
        systemType: Int,
        noteType: Int,
        callRecordFolderId: Long,
        searchText: String
    ): List<NoteListRow>

    /**
     * 获取某个文件夹内的直接子项目列表（不区分系统类型）。
     * 简单条件：parent_id = folderId。
     * 同样计算 notes_count。
     */
    @Query(
        """
        SELECT noteItem._id, noteItem.alert_date, noteItem.bg_color_id, noteItem.has_attachment,
               noteItem.modified_date,
               CASE
                   WHEN noteItem.type = :noteType THEN 0
                   ELSE (SELECT COUNT(*) FROM note AS child WHERE child.parent_id = noteItem._id)
               END AS notes_count,
               noteItem.parent_id, noteItem.snippet, noteItem.type, noteItem.widget_id,
               noteItem.widget_type
        FROM note AS noteItem
        WHERE noteItem.parent_id = :folderId
        ORDER BY noteItem.type DESC, noteItem.modified_date DESC
        """
    )
    suspend fun getFolderListItems(folderId: Long, noteType: Int): List<NoteListRow>

    /**
     * 在特定文件夹内按摘要文本搜索。
     * 条件：parent_id = folderId 且 snippet 包含关键字。
     */
    @Query(
        """
        SELECT noteItem._id, noteItem.alert_date, noteItem.bg_color_id, noteItem.has_attachment,
               noteItem.modified_date,
               CASE
                   WHEN noteItem.type = :noteType THEN 0
                   ELSE (SELECT COUNT(*) FROM note AS child WHERE child.parent_id = noteItem._id)
               END AS notes_count,
               noteItem.parent_id, noteItem.snippet, noteItem.type, noteItem.widget_id,
               noteItem.widget_type
        FROM note AS noteItem
        WHERE noteItem.parent_id = :folderId AND noteItem.snippet LIKE :searchText
        ORDER BY noteItem.type DESC, noteItem.modified_date DESC
        """
    )
    suspend fun searchFolderListItems(folderId: Long, noteType: Int, searchText: String): List<NoteListRow>

    /**
     * 检查是否有可见同名文件夹。
     * 用于新建文件夹时避免重名（回收站里的同名文件夹不冲突）。
     * EXISTS 子查询，返回布尔值。
     */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM note
            WHERE type = :folderType
              AND parent_id <> :trashFolderId
              AND snippet = :name
        )
        """
    )
    suspend fun hasVisibleFolderNamed(
        name: String,
        folderType: Int,
        trashFolderId: Long
    ): Boolean

    /**
     * 插入一条便签/文件夹记录。
     * 返回新插入行的 row ID。
     */
    @Insert
    suspend fun insertNote(note: RoomNoteEntity): Long

    /**
     * 更新一条便签/文件夹记录（通过主键 _id 匹配）。
     * 返回被更新的行数。
     */
    @Update
    suspend fun updateNote(note: RoomNoteEntity): Int

    /**
     * 根据 ID 获取单条便签/文件夹记录。
     * 如果不存在则返回 null。
     */
    @Query("SELECT * FROM note WHERE _id = :noteId LIMIT 1")
    suspend fun getNoteById(noteId: Long): RoomNoteEntity?

    /**
     * 重命名文件夹。
     * 同时修改 snippet（名称）和 type（保持为文件夹类型），并标记本地已修改，便于同步。
     * 返回更新行数。
     */
    @Query(
        """
        UPDATE note
        SET snippet = :name,
            type = :folderType,
            local_modified = 1
        WHERE _id = :folderId
        """
    )
    suspend fun renameFolder(folderId: Long, name: String, folderType: Int): Int

    /**
     * 递归查询所有子孙笔记/文件夹的 ID。
     * 使用 WITH RECURSIVE 公用表表达式，从给定的根节点 ID 列表开始，
     * 逐层查找 child.parent_id 等于上一级 _id 的子节点，直到没有更多后代。
     * 通常用于删除文件夹时批量级联删除其下所有子内容。
     */
    @Query(
        """
        WITH RECURSIVE descendants(_id) AS (
            SELECT _id
            FROM note
            WHERE _id IN (:rootIds)
            UNION
            SELECT child._id
            FROM note AS child
            INNER JOIN descendants ON child.parent_id = descendants._id
        )
        SELECT _id FROM descendants
        """
    )
    suspend fun getDescendantNoteIds(rootIds: List<Long>): List<Long>

    /**
     * 根据 note ID 列表批量删除 data 表中的相关数据（如文本内容、附件引用等）。
     * 返回删除行数。
     */
    @Query("DELETE FROM data WHERE note_id IN (:noteIds)")
    suspend fun deleteDataByNoteIds(noteIds: List<Long>): Int

    /**
     * 根据 ID 列表批量删除 note 表中的行。
     * 返回删除行数。
     */
    @Query("DELETE FROM note WHERE _id IN (:noteIds)")
    suspend fun deleteNotesByIds(noteIds: List<Long>): Int

    /**
     * 获取指定文件夹下的所有小部件引用信息。
     * 返回 widget_id 和 widget_type，供桌面小部件更新使用。
     */
    @Query(
        """
        SELECT widget_id, widget_type
        FROM note
        WHERE parent_id = :folderId
        """
    )
    suspend fun getFolderWidgets(folderId: Long): List<WidgetRow>

    /**
     * 判断某笔记是否可见（即存在且不在回收站中）。
     * 用于操作前验证笔记状态。
     */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM note
            WHERE _id = :noteId
              AND type = :type
              AND parent_id <> :trashFolderId
        )
        """
    )
    suspend fun isVisibleNote(noteId: Long, type: Int, trashFolderId: Long): Boolean

    /**
     * 获取某笔记的文本摘要（snippet），通常用于快速显示。
     */
    @Query("SELECT snippet FROM note WHERE _id = :noteId LIMIT 1")
    suspend fun getSnippetById(noteId: Long): String?

    /**
     * 获取未来将要提醒的笔记信息（提醒时间大于给定当前时间的）。
     * 用于设置或更新系统闹钟提醒。
     */
    @Query(
        """
        SELECT _id AS noteId, alert_date
        FROM note
        WHERE alert_date > :currentDate
          AND type = :noteType
        """
    )
    suspend fun getFutureAlertNotes(currentDate: Long, noteType: Int): List<AlarmRow>

    /**
     * 根据桌面小部件 ID 获取其绑定的便签摘要信息（排除回收站内的）。
     * 用于小部件上展示对应便签内容。
     */
    @Query(
        """
        SELECT _id, bg_color_id, snippet
        FROM note
        WHERE widget_id = :appWidgetId
          AND parent_id <> :trashFolderId
        """
    )
    suspend fun getNotesByWidgetId(appWidgetId: Int, trashFolderId: Long): List<WidgetNoteRow>

    /**
     * 将指定小部件 ID 的绑定清除（重置为无效 ID）。
     * 用于用户移除桌面小部件后清除数据库关联。
     * 返回更新行数。
     */
    @Query(
        """
        UPDATE note
        SET widget_id = :invalidWidgetId
        WHERE widget_id = :appWidgetId
        """
    )
    suspend fun clearWidgetBinding(appWidgetId: Int, invalidWidgetId: Int): Int

    /**
     * 获取某个便签的所有内容数据（data 表）。
     * 一条便签可能有多条 data，例如文本、附件等，通过 mime_type 区分。
     */
    @Query("SELECT * FROM data WHERE note_id = :noteId")
    suspend fun getDataByNoteId(noteId: Long): List<RoomDataEntity>

    /**
     * 根据便签 ID 和 MIME 类型获取单条内容数据。
     * 例如，获取纯文本内容时可指定 mimeType = "text/plain"。
     * 如果不存在则返回 null。
     */
    @Query("SELECT * FROM data WHERE note_id = :noteId AND mime_type = :mimeType LIMIT 1")
    suspend fun getDataByNoteIdAndMimeType(noteId: Long, mimeType: String): RoomDataEntity?

    /**
     * 插入一条内容数据。
     * 返回新行的 row ID。
     */
    @Insert
    suspend fun insertData(data: RoomDataEntity): Long

    /**
     * 更新一条内容数据（基于主键匹配）。
     * 返回更新行数。
     */
    @Update
    suspend fun updateData(data: RoomDataEntity): Int

    /**
     * 通过通话日期查找对应的通话记录便签。
     * data3 字段存储电话号码，data1 存储呼叫日期。
     * 返回一份简化的查询结果，包含 noteId 和 phoneNumber。
     */
    @Query(
        """
        SELECT note_id AS noteId, data3 AS phoneNumber
        FROM data
        WHERE data1 = :callDate
          AND mime_type = :mimeType
        """
    )
    suspend fun findCallNotesByDate(callDate: Long, mimeType: String): List<CallNoteLookupRow>

}