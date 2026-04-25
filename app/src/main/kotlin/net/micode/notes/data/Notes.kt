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
package net.micode.notes.data

/**
 * 小米便签核心常量定义类。
 * 
 * 集中管理所有与数据存储、类型识别、跨组件通信相关的常量，
 * 包括数据库列名、便签/文件夹类型、系统文件夹 ID、Intent Extra 键等。
 * 设计为单例 object，外部可直接通过 `Notes.XXX` 访问。
 */
object Notes {
    // ==================== 便签与文件夹类型 ====================

    /** 普通便签类型 */
    const val TYPE_NOTE: Int = 0

    /** 文件夹类型 */
    const val TYPE_FOLDER: Int = 1

    /** 系统类型（通话记录文件夹、根文件夹、回收站等） */
    const val TYPE_SYSTEM: Int = 2

    // ==================== 固定系统文件夹 ID ====================
    /**
     * 系统文件夹标识。
     * [ID_ROOT_FOLDER] 根文件夹（默认顶层文件夹）
     * [ID_CALL_RECORD_FOLDER] 通话记录文件夹
     * [ID_TRASH_FOLER] 回收站文件夹
     */
    const val ID_ROOT_FOLDER: Int = 0        // 根文件夹，parent_id = 0 即表示在此文件夹下
    val ID_CALL_RECORD_FOLDER: Int = -2      // 通话记录专用文件夹，ID 为 -2，避免与正常 ID 冲突
    val ID_TRASH_FOLER: Int = -3             // 回收站文件夹，ID 为 -3

    // ==================== Intent 额外数据键名 ====================
    /** Intent 中携带提醒日期的键 */
    const val INTENT_EXTRA_ALERT_DATE: String = "net.micode.notes.alert_date"

    /** Intent 中携带背景颜色 ID 的键 */
    const val INTENT_EXTRA_BACKGROUND_ID: String = "net.micode.notes.background_color_id"

    /** Intent 中携带小部件 ID 的键 */
    const val INTENT_EXTRA_WIDGET_ID: String = "net.micode.notes.widget_id"

    /** Intent 中携带小部件类型的键 */
    const val INTENT_EXTRA_WIDGET_TYPE: String = "net.micode.notes.widget_type"

    /** Intent 中携带目标文件夹 ID 的键 */
    const val INTENT_EXTRA_FOLDER_ID: String = "net.micode.notes.folder_id"

    /** Intent 中携带通话日期的键 */
    const val INTENT_EXTRA_CALL_DATE: String = "net.micode.notes.call_date"

    // ==================== 桌面小部件类型 ====================
    /** 无效小部件类型，表示未绑定或默认值 */
    val TYPE_WIDGET_INVALIDE: Int = -1

    /** 2x 规格小部件 */
    const val TYPE_WIDGET_2X: Int = 0

    /** 4x 规格小部件 */
    const val TYPE_WIDGET_4X: Int = 1

    // ==================== 数据库 note 表列名定义 ====================
    /**
     * 定义 note 表的所有列名。
     * 通过接口 + 伴生对象组织，方便被 TextNote、CallNote 等子对象复用，
     * 同时供 ContentProvider 和 Room Entity 统一引用。
     */
    interface NoteColumns {
        companion object {
            /** 主键 ID，类型 INTEGER (long) */
            const val ID: String = "_id"

            /** 父文件夹 ID，类型 INTEGER (long) */
            const val PARENT_ID: String = "parent_id"

            /** 创建日期（毫秒时间戳），类型 INTEGER (long) */
            const val CREATED_DATE: String = "created_date"

            /** 最后修改日期（毫秒时间戳），类型 INTEGER (long) */
            const val MODIFIED_DATE: String = "modified_date"

            /** 提醒日期（毫秒时间戳），类型 INTEGER (long) */
            const val ALERTED_DATE: String = "alert_date"

            /** 文本摘要（文件夹名或便签内容开头），类型 TEXT */
            const val SNIPPET: String = "snippet"

            /** 桌面小部件 ID，类型 INTEGER (long) */
            const val WIDGET_ID: String = "widget_id"

            /** 桌面小部件类型，类型 INTEGER (long) */
            const val WIDGET_TYPE: String = "widget_type"

            /** 背景颜色 ID，类型 INTEGER (long) */
            const val BG_COLOR_ID: String = "bg_color_id"

            /** 是否有附件 (0/1)，类型 INTEGER */
            const val HAS_ATTACHMENT: String = "has_attachment"

            /** 文件夹内子笔记数量，类型 INTEGER (long) */
            const val NOTES_COUNT: String = "notes_count"

            /** 类型（便签/文件夹/系统），类型 INTEGER */
            const val TYPE: String = "type"

            /** 最近一次同步 ID，类型 INTEGER (long) */
            const val SYNC_ID: String = "sync_id"

            /** 本地修改标记 (0:未修改, 1:已修改)，类型 INTEGER */
            const val LOCAL_MODIFIED: String = "local_modified"

            /** 移动到回收站前的原始父文件夹 ID，用于还原，类型 INTEGER */
            const val ORIGIN_PARENT_ID: String = "origin_parent_id"

            /** Google Tasks 任务 ID，类型 TEXT */
            const val GTASK_ID: String = "gtask_id"

            /** 数据版本号，用于同步冲突处理，类型 INTEGER (long) */
            const val VERSION: String = "version"
        }
    }

    // ==================== 数据库 data 表列名定义 ====================
    /**
     * 定义 data 表的所有列名。
     * 结构同 NoteColumns，提供统一引用。
     */
    interface DataColumns {
        companion object {
            /** 主键 ID */
            const val ID: String = "_id"

            /** MIME 类型，标识该行数据的格式（如 text/plain, vnd.android.cursor.item/phone） */
            const val MIME_TYPE: String = "mime_type"

            /** 所属便签的 ID，类型 INTEGER (long) */
            const val NOTE_ID: String = "note_id"

            /** 创建日期（毫秒时间戳） */
            const val CREATED_DATE: String = "created_date"

            /** 最后修改日期（毫秒时间戳） */
            const val MODIFIED_DATE: String = "modified_date"

            /** 文本内容（主要用于纯文本便签） */
            const val CONTENT: String = "content"

            /**
             * 通用整型数据列 1，用途由 MIME_TYPE 决定
             * （例如通话记录中为通话日期）
             */
            const val DATA1: String = "data1"

            /**
             * 通用整型数据列 2，用途由 MIME_TYPE 决定
             */
            const val DATA2: String = "data2"

            /**
             * 通用文本数据列 3，用途由 MIME_TYPE 决定
             * （例如通话记录中为电话号码）
             */
            const val DATA3: String = "data3"

            /** 通用文本数据列 4 */
            const val DATA4: String = "data4"

            /** 通用文本数据列 5 */
            const val DATA5: String = "data5"
        }
    }

    // ==================== 特定内容类型映射 ====================
    /**
     * 纯文本便签的数据定义。
     * 继承 DataColumns，复用列名常量，并声明文本模式。
     */
    object TextNote : DataColumns {
        /**
         * 清单模式标识。
         * 数据位置：对应 data 表的 DATA1 列。
         * 值为 MODE_CHECK_LIST (1) 时表示清单模式，否则普通文本模式。
         */
        val MODE: String = DataColumns.Companion.DATA1

        /** 清单模式标识值 */
        const val MODE_CHECK_LIST: Int = 1

        /** 文本便签对应的 MIME 类型 */
        const val CONTENT_ITEM_TYPE: String = "vnd.android.cursor.item/text_note"
    }

    /**
     * 通话记录便签的数据定义。
     * 通过 DataColumns 的通用列存储具体业务数据。
     */
    object CallNote : DataColumns {
        /**
         * 通话日期。
         * 映射到 data 表的 DATA1 列，类型 INTEGER (long)。
         */
        val CALL_DATE: String = DataColumns.Companion.DATA1

        /**
         * 电话号码。
         * 映射到 data 表的 DATA3 列，类型 TEXT。
         */
        val PHONE_NUMBER: String = DataColumns.Companion.DATA3

        /** 通话记录便签对应的 MIME 类型 */
        const val CONTENT_ITEM_TYPE: String = "vnd.android.cursor.item/call_note"
    }
}