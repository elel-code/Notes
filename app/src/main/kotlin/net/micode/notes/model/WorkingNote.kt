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
package net.micode.notes.model

import android.content.Context
import android.text.TextUtils
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.micode.notes.data.NoteSaveRequest
import net.micode.notes.data.Notes
import net.micode.notes.data.NotesRepository
import net.micode.notes.tool.ResourceParser.NoteBgResources

/**
 * 内存中的便签工作模型。
 * 用于 UI 层编辑、创建、查看便签时的状态管理。
 * 一个 [WorkingNote] 实例代表一个正在被操作的便签，它持有该便签的当前数据，
 * 并在调用 [saveNote] 时将所有更改一次性推送到数据库。
 *
 * 设计原则：
 * - 属性修改自动标记脏标志（[hasLocalChanges]），只有确实改动时才保存。
 * - 通过 [runBlocking] 调用 Repository 的挂起函数，避免调用方处理协程。
 * - 构造器私有化，通过 [createEmptyNote] 和 [load] 工厂方法获取实例。
 */
class WorkingNote {
    // ==================== 核心标识 ====================
    /**
     * 便签在数据库中的 ID。
     * - 新建便签时 ID 为 0，保存后由数据库生成并被赋值。
     * - 已有便签在加载时从数据库读取。
     */
    var noteId: Long
        private set

    /**
     * 便签的文本内容（普通模式）或清单文本（清单模式）。
     * 可为 null 表示未设置内容。
     */
    var content: String? = null
        private set

    // ==================== 模式与提醒 ====================
    /**
     * 清单模式标志。
     * 0：普通文本；1：清单模式
     */
    private var mode = 0

    /**
     * 提醒时间（毫秒时间戳），0 表示不提醒。
     */
    var alertDate: Long = 0
        private set

    // ==================== 时间戳 ====================
    /**
     * 最后修改时间（毫秒时间戳），在修改或保存时更新。
     */
    var modifiedDate: Long = 0
        private set

    // ==================== 外观与小部件绑定 ====================
    /**
     * 背景颜色 ID（内部存储值）。
     * 通过 [bgColorId] 自定义 getter/setter 修改，访问资源时使用 [bgColorResId]。
     */
    private var bgColorIdInternal = 0

    /**
     * 桌面小部件 ID，0 表示未绑定。
     */
    private var widgetIdInternal = 0

    /**
     * 桌面小部件类型，默认 [Notes.TYPE_WIDGET_INVALIDE] 表示无效。
     */
    private var widgetTypeInternal = Notes.TYPE_WIDGET_INVALIDE

    // ==================== 通话记录相关（可选） ====================
    /**
     * 通话日期（毫秒），仅通话记录便签有效。
     */
    private var callDate: Long? = null

    /**
     * 电话号码，仅通话记录便签有效。
     */
    private var phoneNumber: String? = null

    // ==================== 文件夹 ====================
    /**
     * 当前便签所在的文件夹 ID。
     */
    var folderId: Long
        private set

    // ==================== 依赖 ====================
    /**
     * 数据仓库引用，用于加载和保存。
     */
    private val repository: NotesRepository

    /**
     * 标记是否已被标记为“已删除”（逻辑删除），之后不再保存。
     */
    private var isDeleted: Boolean

    /**
     * 标记自加载或上次保存后是否有本地修改。
     * 仅在有必要写入数据库时 [saveNote] 才真正执行。
     */
    private var hasLocalChanges = false

    // ==================== 构造函数 ====================
    /**
     * 用于创建全新便签的私有构造器。
     * @param context  应用上下文
     * @param folderId 目标文件夹 ID
     */
    private constructor(context: Context, folderId: Long) {
        repository = NotesRepository(context.applicationContext)
        alertDate = 0
        modifiedDate = System.currentTimeMillis()
        this.folderId = folderId
        noteId = 0                 // 0 表示尚未入库
        isDeleted = false
    }

    /**
     * 用于加载已有便签的私有构造器。
     * @param context 应用上下文
     * @param noteId  已存在的 note ID
     * @param folderId (暂时未实际使用，内部会通过 loadNote 从数据库读取真实值)
     */
    private constructor(context: Context, noteId: Long, folderId: Long) {
        repository = NotesRepository(context.applicationContext)
        this.noteId = noteId
        this.folderId = folderId  // 临时赋值，稍后 loadNote() 会用数据库值覆盖
        isDeleted = false
        loadNote()                // 加载时同步数据到各字段
    }

    /**
     * 从数据库加载 [noteId] 对应的完整数据，填充到当前实例的各个属性。
     * 使用 [runBlocking] 阻塞当前线程直到数据库查询完成，因此不应在主线程调用。
     * 若便签不存在，抛出 [IllegalArgumentException]。
     */
    private fun loadNote() {
        // 同步调用挂起函数，阻塞直到拿到结果
        val storedNote = runBlocking(Dispatchers.IO) {
            repository.loadStoredNote(noteId)
        } ?: run {
            Log.e(TAG, "No note with id: $noteId")
            throw IllegalArgumentException("Unable to find note with id $noteId")
        }

        // 用数据库中的值填充属性
        folderId = storedNote.note.parentId
        bgColorIdInternal = storedNote.note.bgColorId
        widgetIdInternal = storedNote.note.widgetId
        widgetTypeInternal = storedNote.note.widgetType
        alertDate = storedNote.note.alertedDate
        modifiedDate = storedNote.note.modifiedDate

        // 文本内容和模式来自 textData
        content = storedNote.textData?.content
        mode = storedNote.textData?.data1?.toInt() ?: 0

        // 通话记录数据（可能为 null）
        callDate = storedNote.callData?.data1
        phoneNumber = storedNote.callData?.data3
        
        // 重置脏标记，因为刚刚从数据库加载，与持久化状态一致
        hasLocalChanges = false
    }

    // ==================== 保存逻辑 ====================
    /**
     * 将当前便签保存到数据库。
     * 若 [isWorthSaving] 为 `false`（例如没有修改或已被删除），则直接返回 false。
     * 使用 [runBlocking] 进行阻塞调用。
     * @return `true` 表示保存成功，`false` 表示未执行保存或保存失败。
     */
    @Synchronized
    fun saveNote(): Boolean {
        // 不值得保存的几种情况：未修改、已删除、空内容新建且无通话记录等
        if (!isWorthSaving) {
            return false
        }

        // 调用仓库层的 saveNote，同步等待结果
        val result = runBlocking(Dispatchers.IO) {
            repository.saveNote(
                NoteSaveRequest(
                    noteId = noteId,
                    folderId = folderId,
                    content = content.orEmpty(),
                    checkListMode = mode,
                    alertDate = alertDate,
                    bgColorId = bgColorIdInternal,
                    widgetId = widgetIdInternal,
                    widgetType = widgetTypeInternal,
                    modifiedDate = modifiedDate,
                    callDate = callDate,
                    phoneNumber = phoneNumber
                )
            )
        } ?: return false  // 仓库返回 null 表示错误

        // 保存成功后更新当前实例的 ID 和修改时间，清除脏标记
        noteId = result.noteId
        modifiedDate = result.modifiedDate
        hasLocalChanges = false
        return true
    }

    /**
     * 检查便签是否已有数据库记录（ID > 0）。
     */
    fun existInDatabase(): Boolean = noteId > 0

    /**
     * 判断当前便签是否值得保存。
     * 满足以下任一条件则不保存：
     * - 已被标记删除。
     * - 是新建便签但没有内容且无通话记录（空便签无保存必要）。
     * - 已存在数据库但没有本地修改。
     * 注意：新建便签即使内容为空但若有通话记录，也值得保存。
     */
    private val isWorthSaving: Boolean
        get() {
            return !(isDeleted || (!existInDatabase() && TextUtils.isEmpty(content) &&
                callDate == null && phoneNumber.isNullOrBlank()) ||
                (existInDatabase() && !hasLocalChanges))
        }

    // ==================== 属性修改方法（均会标记脏） ====================
    /**
     * 设置提醒时间。若新值与当前不同则标记脏并更新。
     */
    fun setAlertDate(date: Long) {
        if (date != alertDate) {
            alertDate = date
            markDirty()
        }
    }

    /**
     * 标记便签已被逻辑删除，后续不再保存。
     */
    fun markDeleted() {
        isDeleted = true
    }

    /**
     * 设置/修改文本内容。若内容确实变化则标记脏。
     */
    fun setWorkingText(text: String?) {
        if (!TextUtils.equals(content, text)) {
            content = text
            markDirty()
        }
    }

    /**
     * 将当前便签转换为通话记录便签。
     * 设置电话号码和通话日期，并将文件夹移动到通话记录专用文件夹。
     */
    fun convertToCallNote(phoneNumber: String?, callDate: Long) {
        this.phoneNumber = phoneNumber
        this.callDate = callDate
        folderId = Notes.ID_CALL_RECORD_FOLDER.toLong()
        markDirty()
    }

    // ==================== 资源访问属性 ====================
    /**
     * 根据当前背景颜色 ID 获取对应的背景资源 ID（用于 View 设置背景）。
     */
    val bgColorResId: Int
        get() = NoteBgResources.getNoteBgResource(bgColorIdInternal)

    /**
     * 背景颜色 ID 的 getter/setter。
     * 修改时若值变化则标记脏。
     */
    var bgColorId: Int
        get() = bgColorIdInternal
        set(id) {
            if (id != bgColorIdInternal) {
                bgColorIdInternal = id
                markDirty()
            }
        }

    /**
     * 根据当前背景颜色 ID 获取对应的标题栏背景资源 ID。
     */
    val titleBgResId: Int
        get() = NoteBgResources.getNoteTitleBgResource(bgColorIdInternal)

    /**
     * 清单模式（0/1）的 getter/setter，修改时标记脏。
     */
    var checkListMode: Int
        get() = mode
        set(value) {
            if (mode != value) {
                mode = value
                markDirty()
            }
        }

    /**
     * 桌面小部件 ID 的 getter/setter。
     */
    var widgetId: Int
        get() = widgetIdInternal
        set(id) {
            if (id != widgetIdInternal) {
                widgetIdInternal = id
                markDirty()
            }
        }

    /**
     * 桌面小部件类型的 getter/setter。
     */
    var widgetType: Int
        get() = widgetTypeInternal
        set(type) {
            if (type != widgetTypeInternal) {
                widgetTypeInternal = type
                markDirty()
            }
        }

    // ==================== 内部工具 ====================
    /**
     * 标记数据已变脏，并更新 [modifiedDate] 为当前时间。
     */
    private fun markDirty() {
        hasLocalChanges = true
        modifiedDate = System.currentTimeMillis()
    }

    // ==================== 伴生对象（静态方法） ====================
    companion object {
        private const val TAG = "WorkingNote"

        /**
         * 创建一个全新的空白便签的工作模型。
         * @param context          上下文
         * @param folderId         目标文件夹 ID
         * @param widgetId         绑定的小部件 ID（0 表示无）
         * @param widgetType       绑定的小部件类型
         * @param defaultBgColorId 默认背景颜色 ID
         * @return 全新的 [WorkingNote] 实例，尚未保存到数据库。
         */
        fun createEmptyNote(
            context: Context,
            folderId: Long,
            widgetId: Int,
            widgetType: Int,
            defaultBgColorId: Int
        ): WorkingNote {
            val note = WorkingNote(context, folderId)
            note.bgColorId = defaultBgColorId
            note.widgetId = widgetId
            note.widgetType = widgetType
            return note
        }

        /**
         * 从数据库加载一个已有便签为工作模型。
         * @param context 上下文
         * @param id      要加载的便签 ID
         * @return 填充好数据的 [WorkingNote] 实例，若便签不存在则抛异常。
         */
        fun load(context: Context, id: Long): WorkingNote {
            return WorkingNote(context, id, 0)
        }
    }
}