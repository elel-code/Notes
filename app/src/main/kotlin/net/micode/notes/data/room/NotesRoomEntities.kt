/**
 * 便签内容数据的 Room 实体，映射 data 表。
 * 
 * 一条便签可以对应多条 data 记录（例如一条便签同时包含文本和一张图片的引用），
 * 通过 mime_type 区分数据类型，data1~data5 为通用扩展字段。
 * 
 * @Entity 注解：
 *   tableName = "data"  表名
 *   indices   为 note_id 列创建索引，优化按便签查询内容的性能
 */
@Entity(
    tableName = "data",
    indices = [Index(value = [Notes.DataColumns.NOTE_ID], name = "note_id_index")]
)
data class RoomDataEntity(
    // ==================== 主键 ====================
    /**
     * 主键 ID，自动生成。
     */
    @PrimaryKey
    @ColumnInfo(name = Notes.DataColumns.ID)
    val id: Long? = null,

    // ==================== 关联信息 ====================
    /**
     * 内容类型（MIME 类型）。
     * 例如：
     *   "text/plain"             纯文本
     *   "vnd.android.cursor.item/phone"  电话号码（通话记录）
     * 该字段告知系统如何解析 data1~data5 和 content 字段。
     */
    @ColumnInfo(name = Notes.DataColumns.MIME_TYPE)
    val mimeType: String,

    /**
     * 关联的便签 ID（note 表的主键）。
     * 默认值 0。
     */
    @ColumnInfo(name = Notes.DataColumns.NOTE_ID, defaultValue = "0")
    val noteId: Long = 0,

    // ==================== 时间信息 ====================
    /**
     * 创建日期（毫秒时间戳），默认当前时间。
     */
    @ColumnInfo(name = Notes.DataColumns.CREATED_DATE, defaultValue = "(strftime('%s','now') * 1000)")
    val createdDate: Long = 0,

    /**
     * 最后修改日期（毫秒时间戳），默认当前时间。
     */
    @ColumnInfo(name = Notes.DataColumns.MODIFIED_DATE, defaultValue = "(strftime('%s','now') * 1000)")
    val modifiedDate: Long = 0,

    // ==================== 内容数据 ====================
    /**
     * 文本内容。
     * 对于纯文本便签，这里存储完整文本；对于其他类型，可能存储辅助说明文本。
     * 默认空字符串。
     */
    @ColumnInfo(name = Notes.DataColumns.CONTENT, defaultValue = "''")
    val content: String = "",

    /**
     * 通用数据字段 1。
     * 用途取决于 mime_type，例如：
     *   - 通话记录：存储通话日期
     *   - 日历事件：存储事件开始时间
     * 可为 null。
     */
    @ColumnInfo(name = Notes.DataColumns.DATA1)
    val data1: Long? = null,

    /**
     * 通用数据字段 2。
     * 用途同上，根据 mime_type 灵活使用，可为 null。
     */
    @ColumnInfo(name = Notes.DataColumns.DATA2)
    val data2: Long? = null,

    /**
     * 通用数据字段 3（文本）。
     * 例如通话记录中存储电话号码。
     */
    @ColumnInfo(name = Notes.DataColumns.DATA3, defaultValue = "''")
    val data3: String = "",

    /**
     * 通用数据字段 4（文本）。
     * 备用扩展字段。
     */
    @ColumnInfo(name = Notes.DataColumns.DATA4, defaultValue = "''")
    val data4: String = "",

    /**
     * 通用数据字段 5（文本）。
     * 备用扩展字段。
     */
    @ColumnInfo(name = Notes.DataColumns.DATA5, defaultValue = "''")
    val data5: String = ""
)