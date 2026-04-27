package net.micode.notes.data.room

import androidx.room.ColumnInfo
import net.micode.notes.data.Notes

/**
 * 主列表/文件夹列表的单条数据行。
 * 对应 DAO 中 getRootListItems、searchRootListItems、getFolderListItems、searchFolderListItems 的返回结果。
 * 包含了前端列表展示所需的核心字段。
 */
data class NoteListRow(
    // 便签/文件夹的 _id
    @ColumnInfo(name = Notes.NoteColumns.ID)
    val id: Long,

    // 提醒时间（毫秒时间戳），0 表示无提醒
    @ColumnInfo(name = Notes.NoteColumns.ALERTED_DATE)
    val alertedDate: Long,

    // 背景颜色 ID，用于显示不同颜色区分
    @ColumnInfo(name = Notes.NoteColumns.BG_COLOR_ID)
    val bgColorId: Int,

    // 是否有附件 (0/1)，用于显示附件图标
    @ColumnInfo(name = Notes.NoteColumns.HAS_ATTACHMENT)
    val hasAttachment: Int,

    // 最后修改时间（毫秒时间戳），用于排序和显示
    @ColumnInfo(name = Notes.NoteColumns.MODIFIED_DATE)
    val modifiedDate: Long,

    // 子笔记数量（对于文件夹），对于便签固定为 0
    @ColumnInfo(name = Notes.NoteColumns.NOTES_COUNT)
    val notesCount: Int,

    // 父文件夹 ID，0 表示根目录
    @ColumnInfo(name = Notes.NoteColumns.PARENT_ID)
    val parentId: Long,

    // 文本摘要（便签内容前部或文件夹名）
    @ColumnInfo(name = Notes.NoteColumns.SNIPPET)
    val snippet: String,

    // 类型：0 普通便签，1 文件夹，系统类型等
    @ColumnInfo(name = Notes.NoteColumns.TYPE)
    val type: Int,

    // 绑定的桌面小部件 ID，0 表示无
    @ColumnInfo(name = Notes.NoteColumns.WIDGET_ID)
    val widgetId: Int,

    // 桌面小部件类型，-1 表示无
    @ColumnInfo(name = Notes.NoteColumns.WIDGET_TYPE)
    val widgetType: Int
)

/**
 * 桌面小部件绑定信息行。
 * 用于 getFolderWidgets 查询，返回某个文件夹下所有便签的小部件关联信息。
 */
data class WidgetRow(
    // 小部件 ID
    @ColumnInfo(name = Notes.NoteColumns.WIDGET_ID)
    val widgetId: Int,

    // 小部件类型
    @ColumnInfo(name = Notes.NoteColumns.WIDGET_TYPE)
    val widgetType: Int
)

/**
 * 未来的提醒行数据。
 * 用于 getFutureAlertNotes 查询，返回需要设置下一次闹钟的便签。
 */
data class AlarmRow(
    // 便签 ID（查询中使用 AS noteId 别名）
    @ColumnInfo(name = "noteId")
    val noteId: Long,

    // 提醒日期（毫秒时间戳）
    @ColumnInfo(name = Notes.NoteColumns.ALERTED_DATE)
    val alertedDate: Long
)

/**
 * 桌面小部件上需要显示的便签简要信息。
 * 用于 getNotesByWidgetId 查询，根据小部件 ID 获取其绑定的便签摘要。
 */
data class WidgetNoteRow(
    // 便签 ID
    @ColumnInfo(name = Notes.NoteColumns.ID)
    val id: Long,

    // 背景颜色 ID，用于小部件背景色
    @ColumnInfo(name = Notes.NoteColumns.BG_COLOR_ID)
    val bgColorId: Int,

    // 文本摘要，显示在小部件上
    @ColumnInfo(name = Notes.NoteColumns.SNIPPET)
    val snippet: String
)

/**
 * 通话记录查找结果行。
 * 通过 findCallNotesByDate 查询，用于根据通话日期找到对应的便签及号码。
 */
data class CallNoteLookupRow(
    // 关联的便签 ID (查询中 note_id AS noteId)
    val noteId: Long,

    // 电话号码 (查询中 data3 AS phoneNumber)
    val phoneNumber: String
)

/**
 * 封装保存便签时所需的完整数据负载。
 * 通常在编辑界面保存时，由业务逻辑组装：
 * - note：便签元数据（标题、类型、提醒等）
 * - textData：文本内容数据，可能为 null（例如纯附件便签或没有文字）
 * - callData：通话记录附加数据，可能为 null（非通话记录便签）
 *
 * 这样设计可以将多个实体的插入/更新操作在一次事务中完成，保证数据一致性。
 */
data class StoredNotePayload(
    // 便签主实体，必填
    val note: RoomNoteEntity,
    // 文本类型的数据实体（mime_type = "text/plain"），可选
    val textData: RoomDataEntity?,
    // 通话记录类型的数据实体，可选
    val callData: RoomDataEntity?
)