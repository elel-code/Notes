package net.micode.notes.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.micode.notes.data.Notes
import net.micode.notes.data.NotesRepository
import net.micode.notes.data.room.NoteListRow
import net.micode.notes.tool.NotesPreferences

/**
 * 便签列表项的 UI 数据类
 * 
 * 这是从数据库 NoteListRow 转换而来的 UI 层数据模型，
 * 专门用于 Compose UI 的展示。
 * 
 * @property id 项目ID（便签ID或文件夹ID）
 * @property title 标题（便签内容摘要或文件夹名称）
 * @property type 类型（笔记、文件夹、系统文件夹等）
 * @property folderId 所属文件夹ID
 * @property modifiedDate 最后修改时间（毫秒时间戳）
 * @property notesCount 便签数量（仅文件夹有用）
 * @property bgColorId 背景颜色ID
 * @property hasAttachment 是否有附件
 * @property hasAlert 是否有提醒
 * @property widget 关联的桌面小部件属性
 */
data class NotesListItemUi(
    val id: Long,
    val title: String,
    val type: Int,
    val folderId: Long,
    val modifiedDate: Long,
    val notesCount: Int,
    val bgColorId: Int,
    val hasAttachment: Boolean,
    val hasAlert: Boolean,
    val widget: AppWidgetAttribute
) {
    /**
     * 是否为文件夹
     * 
     * TYPE_FOLDER: 普通文件夹
     * TYPE_SYSTEM: 系统文件夹（如通话记录文件夹）
     */
    val isFolder: Boolean
        get() = type == Notes.TYPE_FOLDER || type == Notes.TYPE_SYSTEM
}

/**
 * 便签列表的 UI 状态数据类
 * 
 * 使用不可变数据类存储列表界面的所有状态，
 * 通过 StateFlow 传递给 Compose UI。
 * 
 * @property currentFolderId 当前打开的文件夹ID
 * @property currentFolderTitle 当前文件夹标题
 * @property searchText 搜索关键词
 * @property items 列表项集合
 * @property selectedIds 已选中的项目ID集合
 * @property isLoading 是否正在加载数据
 */
data class NotesListUiState(
    val currentFolderId: Long = Notes.ID_ROOT_FOLDER.toLong(),
    val currentFolderTitle: String = "",
    val searchText: String = "",
    val items: List<NotesListItemUi> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val isLoading: Boolean = false
) {
    /**
     * 是否在根目录
     */
    val isRoot: Boolean
        get() = currentFolderId == Notes.ID_ROOT_FOLDER.toLong()

    /**
     * 是否在通话记录文件夹
     */
    val isCallRecordFolder: Boolean
        get() = currentFolderId == Notes.ID_CALL_RECORD_FOLDER.toLong()
}

/**
 * 便签列表 ViewModel
 * 
 * 负责管理便签列表界面的所有状态和业务逻辑。
 * 
 * 主要职责：
 * 1. 加载和刷新列表数据（支持按文件夹、搜索关键词、排序模式）
 * 2. 管理文件夹导航（打开文件夹、返回根目录）
 * 3. 管理多选模式（选中/取消选中、清除选择）
 * 4. 批量删除便签
 * 5. 文件夹的创建、重命名、删除
 * 6. 搜索功能
 * 7. 记住上次打开的文件夹
 * 
 * 技术特点：
 * - 使用 StateFlow 管理 UI 状态
 * - 使用 Kotlin Coroutines 处理异步操作
 * - 使用 queryGeneration 机制防止数据竞态
 * 
 * @author MiCode Open Source Community
 */
class NotesListViewModel : ViewModel() {
    
    // ==================== 状态管理 ====================
    
    /**
     * UI 状态的可变 StateFlow（仅限内部修改）
     */
    private val _uiState = MutableStateFlow(NotesListUiState())
    
    /**
     * UI 状态的不可变 StateFlow（对外暴露）
     * 
     * UI 层通过 collectAsStateWithLifecycle 收集此流
     */
    val uiState = _uiState.asStateFlow()

    /**
     * 查询代数计数器
     * 
     * 用于防止并发查询时旧数据覆盖新数据的问题。
     * 每次发起新查询时 increment，查询返回时对比是否仍然是当前代。
     * 如果不匹配，说明已经有更新的查询发起，丢弃当前结果。
     */
    private var queryGeneration = 0L

    // ==================== 数据加载与刷新 ====================
    
    /**
     * 刷新当前列表
     * 
     * 根据当前状态（文件夹、搜索关键词）从数据库加载数据。
     * 
     * 执行流程：
     * 1. 递增查询代数
     * 2. 设置 isLoading = true
     * 3. 在 IO 线程中查询数据库
     * 4. 将 NoteListRow 转换为 NotesListItemUi
     * 5. 回到主线程，检查查询代数是否仍然有效
     * 6. 过滤掉已删除的选中项
     * 7. 更新 UI 状态，设置 isLoading = false
     * 
     * @param context 上下文（用于访问数据库和 SharedPreferences）
     */
    fun refresh(context: Context) {
        val generation = ++queryGeneration  // 递增代数，记录当前查询
        val currentState = _uiState.value    // 获取当前状态快照
        _uiState.update { it.copy(isLoading = true) }  // 显示加载状态

        viewModelScope.launch(Dispatchers.IO) {
            // 从数据库加载列表项
            val items = NotesRepository(context).loadListItems(
                folderId = currentState.currentFolderId,
                searchText = currentState.searchText,
                sortMode = NotesPreferences.getListSortMode(context)  // 获取用户设置的排序模式
            ).map { item ->
                item.toUi()  // 转换为 UI 数据模型
            }

            // 回到主线程更新状态
            withContext(Dispatchers.Main) {
                // 检查是否仍然是当前查询（防止旧数据覆盖新数据）
                if (generation != queryGeneration) {
                    return@withContext
                }

                // 过滤掉已被删除的选中项
                val validSelectedIds = currentState.selectedIds.intersect(items.mapTo(hashSetOf()) { it.id })
                _uiState.update {
                    it.copy(
                        items = items,
                        selectedIds = validSelectedIds,
                        isLoading = false
                    )
                }
            }
        }
    }

    // ==================== 搜索功能 ====================
    
    /**
     * 设置搜索文本
     * 
     * 当用户输入搜索关键词时调用。
     * 设置后会立即触发列表刷新。
     * 
     * @param context 上下文
     * @param searchText 搜索关键词
     */
    fun setSearchText(context: Context, searchText: String) {
        _uiState.update {
            it.copy(
                searchText = searchText,
                selectedIds = emptySet()  // 搜索时清空选中状态
            )
        }
        refresh(context)  // 立即刷新列表
    }

    // ==================== 文件夹导航 ====================
    
    /**
     * 打开文件夹
     * 
     * 进入指定的子文件夹，更新当前文件夹信息并刷新列表。
     * 
     * @param context 上下文
     * @param item 要打开的文件夹项
     */
    fun openFolder(context: Context, item: NotesListItemUi) {
        _uiState.update {
            it.copy(
                currentFolderId = item.id,
                currentFolderTitle = item.title,
                selectedIds = emptySet()  // 切换文件夹时清空选中状态
            )
        }
        refresh(context)
    }

    /**
     * 恢复文件夹（用于 Activity 重建后恢复状态）
     * 
     * 不触发刷新，只更新状态。
     * 用于从 SharedPreferences 恢复上次打开的文件夹。
     * 
     * @param folderId 文件夹 ID
     * @param folderTitle 文件夹标题
     */
    fun restoreFolder(folderId: Long, folderTitle: String) {
        _uiState.update {
            it.copy(
                currentFolderId = folderId,
                currentFolderTitle = folderTitle,
                selectedIds = emptySet()
            )
        }
    }

    /**
     * 返回根目录
     * 
     * 清空当前文件夹信息，回到根目录。
     * 
     * @param context 上下文
     */
    fun goToRoot(context: Context) {
        _uiState.update {
            it.copy(
                currentFolderId = Notes.ID_ROOT_FOLDER.toLong(),
                currentFolderTitle = "",
                selectedIds = emptySet()
            )
        }
        refresh(context)
    }

    // ==================== 多选模式 ====================
    
    /**
     * 清除所有选中项
     * 
     * 退出多选模式。
     */
    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    /**
     * 切换项目的选中状态
     * 
     * 如果项目已选中则取消选中，否则添加选中。
     * 
     * @param itemId 项目 ID
     */
    fun toggleSelection(itemId: Long) {
        _uiState.update { state ->
            val selectedIds = state.selectedIds.toMutableSet()
            if (!selectedIds.add(itemId)) {
                selectedIds.remove(itemId)  // 已存在则移除
            }
            state.copy(selectedIds = selectedIds)
        }
    }

    // ==================== 批量删除 ====================
    
    /**
     * 删除选中的便签
     * 
     * 执行流程：
     * 1. 获取当前选中的 ID 集合
     * 2. 收集选中项关联的小部件信息
     * 3. 在 IO 线程中执行删除操作
     * 4. 删除完成后清空选中状态
     * 5. 通过回调返回受影响的小部件列表
     * 
     * @param context 上下文
     * @param onComplete 删除完成回调，返回受影响的小部件集合
     */
    fun deleteSelectedNotes(
        context: Context,
        onComplete: (Set<AppWidgetAttribute>) -> Unit
    ) {
        val selectedIds = _uiState.value.selectedIds
        if (selectedIds.isEmpty()) {
            onComplete(emptySet())
            return
        }

        // 收集需要更新的小部件
        val selectedWidgets = _uiState.value.items
            .filter { it.id in selectedIds }
            .mapTo(linkedSetOf()) { it.widget }

        viewModelScope.launch(Dispatchers.IO) {
            val success = NotesRepository(context).deleteNotes(selectedIds)

            if (!success) {
                Log.e(TAG, "Failed to delete selected notes")
            }

            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(selectedIds = emptySet()) }  // 清空选中状态
                onComplete(selectedWidgets)  // 回调通知更新小部件
            }
        }
    }

    // ==================== 文件夹管理 ====================
    
    /**
     * 删除文件夹
     * 
     * 注意：删除文件夹会同时删除文件夹内的所有便签。
     * 
     * 执行流程：
     * 1. 验证文件夹 ID 不是根目录
     * 2. 收集文件夹内所有便签关联的小部件信息
     * 3. 在 IO 线程中执行删除操作
     * 4. 删除完成后通过回调返回受影响的小部件列表
     * 
     * @param context 上下文
     * @param folderId 要删除的文件夹 ID
     * @param onComplete 删除完成回调，返回受影响的小部件集合
     */
    fun deleteFolder(
        context: Context,
        folderId: Long,
        onComplete: (Set<AppWidgetAttribute>) -> Unit
    ) {
        if (folderId == Notes.ID_ROOT_FOLDER.toLong()) {
            Log.e(TAG, "Wrong folder id, should not happen: $folderId")
            onComplete(emptySet())
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val repository = NotesRepository(context)
            // 收集文件夹内所有便签的小部件
            val widgets = repository.collectFolderWidgets(folderId)
            val success = repository.deleteFolder(folderId)

            if (!success) {
                Log.e(TAG, "Failed to delete folder $folderId")
            }

            withContext(Dispatchers.Main) {
                onComplete(widgets)
            }
        }
    }

    /**
     * 保存文件夹（创建或重命名）
     * 
     * 根据 dialogState.create 判断是创建新文件夹还是重命名现有文件夹。
     * 
     * 执行流程：
     * 1. 验证名称不为空
     * 2. 检查名称是否重复（仅当创建或名称发生变化时）
     * 3. 如果重复，调用 onDuplicateName 回调并返回
     * 4. 否则执行创建或重命名操作
     * 5. 如果重命名成功且当前正在重命名的文件夹内，更新 currentFolderTitle
     * 6. 通过 onComplete 回调返回结果
     * 
     * @param context 上下文
     * @param dialogState 对话框状态（包含操作类型、名称、文件夹信息）
     * @param onDuplicateName 名称重复时的回调
     * @param onComplete 操作完成回调，参数 Boolean 表示是否成功
     */
    fun saveFolder(
        context: Context,
        dialogState: FolderDialogUiState,
        onDuplicateName: () -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        val name = dialogState.name.trim()
        if (name.isEmpty()) {
            onComplete(false)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val repository = NotesRepository(context)
            
            // 检查名称是否重复（创建时或重命名时名称发生了变化）
            val duplicateName = (dialogState.create || !name.contentEquals(dialogState.folder?.title)) &&
                repository.hasVisibleFolderNamed(name)
            
            if (duplicateName) {
                withContext(Dispatchers.Main) {
                    onDuplicateName()  // 通知 UI 显示名称重复错误
                }
                return@launch
            }

            // 执行创建或重命名
            val success = if (dialogState.create) {
                repository.createFolder(name) > 0  // 返回文件夹 ID，大于0表示成功
            } else {
                val folderId = dialogState.folder?.id
                if (folderId == null) {
                    Log.e(TAG, "Missing folder while renaming")
                    false
                } else {
                    repository.renameFolder(folderId, name)
                }
            }

            if (!success) {
                Log.e(TAG, "Failed to save folder")
            }

            withContext(Dispatchers.Main) {
                // 如果重命名成功，且当前正在重命名的文件夹内，更新标题
                if (success && !dialogState.create && _uiState.value.currentFolderId == dialogState.folder?.id) {
                    _uiState.update { state ->
                        state.copy(currentFolderTitle = name)
                    }
                }
                onComplete(success)
            }
        }
    }

    // ==================== 伴生对象 ====================
    
    companion object {
        private const val TAG = "NotesListViewModel"  // 日志标签
    }
}

/**
 * 将数据库实体 NoteListRow 转换为 UI 数据模型 NotesListItemUi
 * 
 * 转换过程中：
 * 1. 移除标题中的复选框符号（√ 和 □）
 * 2. 处理空内容的占位符
 * 3. 转换布尔值字段
 * 
 * @return NotesListItemUi 实例
 */
private fun NoteListRow.toUi(): NotesListItemUi {
    // 移除标题中的清单符号，得到纯净的标题文本
    val title = snippet
        .replace(NoteEditActivity.TAG_CHECKED, "")    // 移除 "√" 符号
        .replace(NoteEditActivity.TAG_UNCHECKED, "")  // 移除 "□" 符号
        .trim()  // 去除首尾空白

    return NotesListItemUi(
        id = id,
        title = title,
        type = type,
        folderId = parentId,
        modifiedDate = modifiedDate,
        notesCount = notesCount,
        bgColorId = bgColorId,
        hasAttachment = hasAttachment > 0,          // 转为 Boolean
        hasAlert = alertedDate > 0L,                 // 转为 Boolean（有提醒时间表示有提醒）
        widget = AppWidgetAttribute(
            widgetId = widgetId,
            widgetType = widgetType
        )
    )
}