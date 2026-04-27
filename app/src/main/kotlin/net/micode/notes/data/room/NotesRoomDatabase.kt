package net.micode.notes.data.room

import android.content.Context
import androidx.room.Database                        // 声明 Room 数据库
import androidx.room.Room                            // Room 数据库构建器
import androidx.room.RoomDatabase                    // Room 数据库抽象基类
import androidx.room.migration.Migration             // 数据库迁移基类
import androidx.sqlite.db.SupportSQLiteDatabase     // 底层 SQLite 数据库对象，用于迁移和回调中执行 SQL
import net.micode.notes.data.Notes                  // 导入常量定义（如系统文件夹 ID、类型等）

/**
 * 小米便签 Room 数据库定义。
 * 包含两张核心表：note（便签/文件夹）和 data（便签内容）。
 * 数据库版本当前为 4，支持从 1、2、3 到 4 的迁移。
 * 采用单例模式，全局共享一个数据库实例。
 *
 * @Database 注解：
 *   entities: 数据库包含的实体类，Room 会据此创建表
 *   version: 数据库版本号，用于迁移判断
 *   exportSchema: 是否导出 schema 文件到项目（此处关闭）
 */
@Database(
    entities = [RoomNoteEntity::class, RoomDataEntity::class],
    version = 4,
    exportSchema = false
)
abstract class NotesRoomDatabase : RoomDatabase() {

    /**
     * 抽象方法，由 Room 在编译时生成实现，返回 DAO 接口。
     * 调用方通过此方法获取数据库操作对象。
     */
    abstract fun notesDao(): NotesRoomDao

    companion object {
        // 数据库文件名
        private const val DB_NAME = "note.db"

        /**
         * 创建 note 表的 SQL 语句。
         * 如果表不存在则创建。
         * 各字段含义（与旧版本 ContentProvider 中对应）：
         *   _id              主键
         *   parent_id        父文件夹 ID，0 表示根目录
         *   alert_date       提醒时间（毫秒时间戳）
         *   bg_color_id      背景颜色 ID
         *   created_date     创建时间（默认当前时间）
         *   has_attachment   是否有附件
         *   modified_date    最后修改时间
         *   notes_count      子笔记数量（冗余字段，加速显示）
         *   snippet          文本摘要（文件夹名或便签内容摘要）
         *   type             类型（便签/文件夹/系统文件夹等）
         *   widget_id        绑定的桌面小部件 ID
         *   widget_type      小部件类型
         *   sync_id          同步 ID（用于云端同步）
         *   local_modified   本地是否有未同步的修改（0/1）
         *   origin_parent_id 原始父文件夹 ID（用于还原）
         *   gtask_id         Google Task 任务 ID（旧版同步相关）
         *   version          数据版本号（用于同步冲突处理）
         */
        private const val CREATE_NOTE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS note (
                _id INTEGER PRIMARY KEY,
                parent_id INTEGER NOT NULL DEFAULT 0,
                alert_date INTEGER NOT NULL DEFAULT 0,
                bg_color_id INTEGER NOT NULL DEFAULT 0,
                created_date INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
                has_attachment INTEGER NOT NULL DEFAULT 0,
                modified_date INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
                notes_count INTEGER NOT NULL DEFAULT 0,
                snippet TEXT NOT NULL DEFAULT '',
                type INTEGER NOT NULL DEFAULT 0,
                widget_id INTEGER NOT NULL DEFAULT 0,
                widget_type INTEGER NOT NULL DEFAULT -1,
                sync_id INTEGER NOT NULL DEFAULT 0,
                local_modified INTEGER NOT NULL DEFAULT 0,
                origin_parent_id INTEGER NOT NULL DEFAULT 0,
                gtask_id TEXT NOT NULL DEFAULT '',
                version INTEGER NOT NULL DEFAULT 0
            )
        """

        /**
         * 创建 data 表的 SQL 语句。
         * 该表存储便签的实际内容，如文本、联系人信息等。
         * 字段说明：
         *   _id           主键
         *   mime_type     数据类型（如 text/plain, vnd.android.cursor.item/phone）
         *   note_id       关联的便签 ID
         *   created_date  创建时间
         *   modified_date 修改时间
         *   content       文本内容（主要用于拼接显示）
         *   data1~data5   通用扩展字段，根据 mime_type 存储不同含义的值
         *                  （如 data3 常存储电话号码）
         */
        private const val CREATE_DATA_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS data (
                _id INTEGER PRIMARY KEY,
                mime_type TEXT NOT NULL,
                note_id INTEGER NOT NULL DEFAULT 0,
                created_date INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
                modified_date INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
                content TEXT NOT NULL DEFAULT '',
                data1 INTEGER,
                data2 INTEGER,
                data3 TEXT NOT NULL DEFAULT '',
                data4 TEXT NOT NULL DEFAULT '',
                data5 TEXT NOT NULL DEFAULT ''
            )
        """

        /**
         * 在 data 表的 note_id 列上创建索引。
         * 加速通过 note_id 查询内容数据的操作（非常高频）。
         */
        private const val CREATE_DATA_NOTE_ID_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS note_id_index ON data(note_id)"

        /**
         * 单例实例，使用 @Volatile 保证多线程可见性。
         */
        @Volatile
        private var instance: NotesRoomDatabase? = null

        /**
         * 获取数据库单例的入口。
         * 使用双重检查锁定（DCL）确保线程安全且仅在第一次调用时创建。
         *
         * @param context 应用上下文（内部会转为 applicationContext 防止泄漏）
         * @return NotesRoomDatabase 实例
         */
        operator fun invoke(context: Context): NotesRoomDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context.applicationContext).also { db ->
                    instance = db
                }
            }
        }

        /**
         * 构建 Room 数据库实例。
         * 添加了从版本 1、2、3 到 4 的迁移策略，以及打开数据库时的回调（用于插入系统文件夹）。
         */
        private fun buildDatabase(context: Context): NotesRoomDatabase {
            return Room.databaseBuilder(context, NotesRoomDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_1_4, MIGRATION_2_4, MIGRATION_3_4)
                .addCallback(SystemFoldersCallback)
                .build()
        }

        /**
         * 数据库打开回调。
         * 每次打开数据库时都会执行，用于确保系统文件夹必须存在。
         */
        private val SystemFoldersCallback = object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                insertSystemFolders(db)
            }
        }

        /**
         * 版本 1 → 4 的迁移策略。
         * 由于旧版本结构差异较大（可能没有部分字段），直接删除旧表并重新创建新表。
         * 数据会丢失，适合早期测试或数据结构大改的情况。
         */
        private val MIGRATION_1_4 = object : Migration(1, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS note")
                db.execSQL("DROP TABLE IF EXISTS data")
                createSchema(db)
                insertSystemFolders(db)
            }
        }

        /**
         * 版本 2 → 4 的迁移策略。
         * 版本 2 缺少 gtask_id 和 version 列，以及 note_id 索引。
         * 使用 ALTER TABLE 增加列，保留原有数据。
         */
        private val MIGRATION_2_4 = object : Migration(2, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 增加 Google 任务同步 ID 列
                db.execSQL("ALTER TABLE note ADD COLUMN gtask_id TEXT NOT NULL DEFAULT ''")
                // 增加数据版本列
                db.execSQL("ALTER TABLE note ADD COLUMN version INTEGER NOT NULL DEFAULT 0")
                // 创建数据表索引
                db.execSQL(CREATE_DATA_NOTE_ID_INDEX_SQL)
                // 插入系统文件夹（如果尚未存在）
                insertSystemFolders(db)
            }
        }

        /**
         * 版本 3 → 4 的迁移策略。
         * 版本 3 仅缺少 version 列和索引，结构接近最新。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE note ADD COLUMN version INTEGER NOT NULL DEFAULT 0")
                db.execSQL(CREATE_DATA_NOTE_ID_INDEX_SQL)
                insertSystemFolders(db)
            }
        }

        /**
         * 创建数据库表结构。
         */
        private fun createSchema(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_NOTE_TABLE_SQL)
            db.execSQL(CREATE_DATA_TABLE_SQL)
            db.execSQL(CREATE_DATA_NOTE_ID_INDEX_SQL)
        }

        /**
         * 插入系统必需的三个文件夹（如果它们尚不存在）。
         * 使用 INSERT OR IGNORE 避免重复插入。
         * 文件夹 ID 定义在 Notes 工具类中：
         *   ID_CALL_RECORD_FOLDER  通话记录文件夹
         *   ID_ROOT_FOLDER        根目录（便签列表中“便签”项）
         *   ID_TRASH_FOLER        回收站
         * 三个文件夹的类型均为 TYPE_SYSTEM，即系统文件夹。
         */
        private fun insertSystemFolders(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT OR IGNORE INTO note (_id, type) VALUES " +
                    "(${Notes.ID_CALL_RECORD_FOLDER}, ${Notes.TYPE_SYSTEM}), " +
                    "(${Notes.ID_ROOT_FOLDER}, ${Notes.TYPE_SYSTEM}), " +
                    "(${Notes.ID_TRASH_FOLER}, ${Notes.TYPE_SYSTEM})"
            )
        }
    }
}