package net.micode.notes.tool

import android.content.Context
import androidx.core.content.edit
import net.micode.notes.data.Notes

/**
 * 便签列表的排序模式枚举。
 * 值为对应的存储整数值，用于持久化到 SharedPreferences。
 */
enum class NotesSortMode(val value: Int) {
    /** 按修改时间降序（默认） */
    MODIFIED_DESC(0),
    /** 按修改时间升序 */
    MODIFIED_ASC(1),
    /** 按标题字母升序 */
    TITLE_ASC(2);

    companion object {
        /**
         * 根据存储数值还原排序模式。
         * 若值不合法，默认返回 [MODIFIED_DESC]。
         */
        fun fromValue(value: Int): NotesSortMode {
            return values().firstOrNull { it.value == value } ?: MODIFIED_DESC
        }
    }
}

/**
 * 便签应用偏好设置管理单例。
 * 所有设置项通过内部的固定键读写，提供类型安全的访问和修改方法。
 */
object NotesPreferences {
    // 随机背景开关键
    private const val KEY_RANDOM_BACKGROUND = "pref_key_bg_random_appear"
    // 默认背景颜色键
    private const val KEY_DEFAULT_BACKGROUND = "pref_key_default_bg_color"
    // 列表排序模式键
    private const val KEY_LIST_SORT_MODE = "pref_key_list_sort_mode"
    // 删除确认对话框开关键
    private const val KEY_DELETE_CONFIRMATION = "pref_key_delete_confirmation"
    // 记住上次浏览文件夹开关键
    private const val KEY_REMEMBER_LAST_FOLDER = "pref_key_remember_last_folder"
    // 上次浏览的文件夹 ID 键
    private const val KEY_LAST_FOLDER_ID = "pref_key_last_folder_id"
    // 上次浏览的文件夹标题键
    private const val KEY_LAST_FOLDER_TITLE = "pref_key_last_folder_title"

    /**
     * 获取随机背景功能是否开启，默认关闭。
     */
    fun isRandomBackgroundEnabled(context: Context): Boolean {
        // 从默认 SharedPreferences 中读取布尔值
        return context.defaultPreferences().getBoolean(KEY_RANDOM_BACKGROUND, false)
    }

    /**
     * 设置随机背景功能开关。
     */
    fun setRandomBackgroundEnabled(context: Context, enabled: Boolean) {
        // 使用扩展函数 edit 提交修改
        context.defaultPreferences().edit { putBoolean(KEY_RANDOM_BACKGROUND, enabled) }
    }

    /**
     * 获取默认背景颜色 ID。
     * 如果读取到的值超出合法范围，返回默认背景颜色。
     */
    fun getDefaultBackgroundColor(context: Context): Int {
        val colorId = context.defaultPreferences()
            .getInt(KEY_DEFAULT_BACKGROUND, ResourceParser.BG_DEFAULT_COLOR)
        // 校验颜色 ID 是否在 0 到 resourcesSize-1 之间（因为背景资源数组索引）
        return if (colorId in 0 until ResourceParser.NoteBgResources.resourcesSize) {
            colorId
        } else {
            ResourceParser.BG_DEFAULT_COLOR
        }
    }

    /**
     * 设置默认背景颜色。
     * @param colorId 原始色彩 ID，内部会进行边界检查后存储。
     */
    fun setDefaultBackgroundColor(context: Context, colorId: Int) {
        context.defaultPreferences().edit {
            putInt(KEY_DEFAULT_BACKGROUND, getDefaultBackgroundColorId(colorId))
        }
    }

    /**
     * 获取列表的排序模式，默认按修改时间降序。
     */
    fun getListSortMode(context: Context): NotesSortMode {
        // 读取存储的整数值
        val value = context.defaultPreferences().getInt(
            KEY_LIST_SORT_MODE,
            NotesSortMode.MODIFIED_DESC.value
        )
        // 将整数值转换为枚举
        return NotesSortMode.fromValue(value)
    }

    /**
     * 设置列表排序模式。
     */
    fun setListSortMode(context: Context, sortMode: NotesSortMode) {
        // 存储枚举对应的数值
        context.defaultPreferences().edit { putInt(KEY_LIST_SORT_MODE, sortMode.value) }
    }

    /**
     * 获取删除确认对话框是否开启，默认开启。
     */
    fun isDeleteConfirmationEnabled(context: Context): Boolean {
        return context.defaultPreferences().getBoolean(KEY_DELETE_CONFIRMATION, true)
    }

    /**
     * 设置删除确认对话框开关。
     */
    fun setDeleteConfirmationEnabled(context: Context, enabled: Boolean) {
        context.defaultPreferences().edit { putBoolean(KEY_DELETE_CONFIRMATION, enabled) }
    }

    /**
     * 获取“记住上次文件夹”功能是否开启，默认开启。
     */
    fun isRememberLastFolderEnabled(context: Context): Boolean {
        return context.defaultPreferences().getBoolean(KEY_REMEMBER_LAST_FOLDER, true)
    }

    /**
     * 设置“记住上次文件夹”开关。
     * 当关闭该功能时，会自动清除已记忆的文件夹记录。
     */
    fun setRememberLastFolderEnabled(context: Context, enabled: Boolean) {
        context.defaultPreferences().edit { putBoolean(KEY_REMEMBER_LAST_FOLDER, enabled) }
        if (!enabled) {
            // 关闭时清除旧记录，避免下次误读
            clearRememberedFolder(context)
        }
    }

    /**
     * 获取上次记住的文件夹 ID。
     * 如果“记住上次文件夹”功能关闭，则直接返回根文件夹 ID。
     */
    fun getRememberedFolderId(context: Context): Long {
        if (!isRememberLastFolderEnabled(context)) {
            return Notes.ID_ROOT_FOLDER.toLong()
        }
        // 读取存储的文件夹 ID，若未存储过则默认为根文件夹
        return context.defaultPreferences()
            .getLong(KEY_LAST_FOLDER_ID, Notes.ID_ROOT_FOLDER.toLong())
    }

    /**
     * 获取上次记住的文件夹标题。
     * 如果功能关闭，返回空字符串。
     */
    fun getRememberedFolderTitle(context: Context): String {
        if (!isRememberLastFolderEnabled(context)) {
            return ""
        }
        // 读取字符串，若未存储过则返回空字符串
        return context.defaultPreferences().getString(KEY_LAST_FOLDER_TITLE, "").orEmpty()
    }

    /**
     * 记录最后浏览的文件夹信息（ID 和标题）。
     * 如果“记住上次文件夹”功能关闭，会主动清除记录后返回。
     */
    fun rememberLastFolder(context: Context, folderId: Long, folderTitle: String) {
        if (!isRememberLastFolderEnabled(context)) {
            // 功能关闭，清理所有可能遗留的记录
            clearRememberedFolder(context)
            return
        }

        // 写入文件夹 ID 和标题
        context.defaultPreferences().edit {
            putLong(KEY_LAST_FOLDER_ID, folderId)
            putString(KEY_LAST_FOLDER_TITLE, folderTitle)
        }
    }

    /**
     * 清除已记住的文件夹记录，重置为根文件夹和空标题。
     */
    fun clearRememberedFolder(context: Context) {
        context.defaultPreferences().edit {
            putLong(KEY_LAST_FOLDER_ID, Notes.ID_ROOT_FOLDER.toLong())
            putString(KEY_LAST_FOLDER_TITLE, "")
        }
    }

    /**
     * 私有方法：对传入的背景颜色 ID 进行边界检查。
     * 若超出合法范围，返回默认背景颜色 ID。
     */
    private fun getDefaultBackgroundColorId(colorId: Int): Int {
        return if (colorId in 0 until ResourceParser.NoteBgResources.resourcesSize) {
            colorId
        } else {
            ResourceParser.BG_DEFAULT_COLOR
        }
    }
}