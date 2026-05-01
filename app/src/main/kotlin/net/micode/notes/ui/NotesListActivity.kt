package net.micode.notes.ui

import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.micode.notes.R
import net.micode.notes.data.Notes
import net.micode.notes.model.WorkingNote
import net.micode.notes.tool.NotesPreferences
import net.micode.notes.tool.ResourceParser
import net.micode.notes.tool.defaultPreferences
import net.micode.notes.widget.NoteWidgetUpdater

/**
 * 便签列表活动
 * 
 * 这是便签应用的 Main Activity，负责显示和管理便签的列表视图。
 * 用户在此界面可以浏览、搜索、创建、删除便签，以及管理文件夹。
 * 
 * 主要功能：
 * 1. 便签列表展示：显示当前文件夹下的所有便签和子文件夹
 * 2. 文件夹管理：创建、重命名、删除文件夹
 * 3. 批量操作：支持多选删除便签
 * 4. 搜索功能：按关键词搜索便签内容
 * 5. 导航功能：在文件夹层级间导航
 * 6. 首次启动引导：创建介绍便签
 * 
 * 技术特点：
 * - 使用 Jetpack Compose 构建 UI
 * - 使用 ViewModel 管理状态和数据
 * - 支持屏幕旋转后恢复状态
 * - 记住上次打开的文件夹
 * 
 * @author MiCode Open Source Community
 */
class NotesListActivity : ComponentActivity() {
    
    // ==================== 成员变量 ====================
    
    /**
     * 文件夹对话框状态
     * - null 表示对话框不可见
     * - 非空表示显示对话框（创建新文件夹或重命名已有文件夹）
     */
    private var folderDialogState by mutableStateOf<FolderDialogUiState?>(null)

    /**
     * 便签编辑器的启动器
     * 
     * 使用 Activity Result API 启动 NoteEditActivity，
     * 并在编辑器关闭后刷新当前列表以显示最新数据。
     */
    private val noteEditorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 编辑器关闭后刷新列表（可能是新建、编辑或删除便签后）
        notesListViewModel.refresh(this)
    }

    /**
     * 便签列表的 ViewModel
     * 
     * 负责管理列表状态、数据加载、搜索、批量操作等业务逻辑
     */
    private val notesListViewModel: NotesListViewModel by lazy {
        ViewModelProvider(this)[NotesListViewModel::class.java]
    }

    // ==================== 生命周期方法 ====================
    
    /**
     * Activity 创建时的回调
     * 
     * 执行流程：
     * 1. 设置应用介绍便签（仅首次启动）
     * 2. 恢复上次打开的文件夹（如果配置了记住功能）
     * 3. 设置 Compose UI 界面
     * 
     * @param savedInstanceState 保存的实例状态，用于 Activity 重建后恢复
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 首次启动时创建介绍便签
        setAppInfoFromRawRes()
        
        // 恢复上次打开的文件夹（仅在全新启动时，不是配置变更后）
        if (savedInstanceState == null && notesListViewModel.uiState.value == NotesListUiState()) {
            restoreLastFolder()
        }

        // 设置 Compose UI
        setContent {
            MaterialTheme {
                // 收集 ViewModel 的 UI 状态流，自动感知生命周期
                val uiState by notesListViewModel.uiState.collectAsStateWithLifecycle()
                NotesListScreen(
                    state = uiState,
                    folderDialogState = folderDialogState,
                    onBack = { handleBack() },                              // 返回处理
                    onSearchTextChange = { notesListViewModel.setSearchText(this, it) },  // 搜索
                    onItemClick = { item -> handleItemClick(item) },        // 点击项目
                    onItemLongClick = { item -> notesListViewModel.toggleSelection(item.id) },  // 长按选择
                    onCreateNote = { createNewNote(uiState.currentFolderId) },  // 新建便签
                    onCreateFolder = {                                         // 创建文件夹
                        folderDialogState = FolderDialogUiState(create = true, name = "")
                    },
                    onDeleteSelection = { confirmDeleteSelected(uiState.selectedIds.size) },  // 删除选中
                    onClearSelection = { notesListViewModel.clearSelection() },  // 清除选择
                    onRenameFolder = {                                         // 重命名文件夹
                        folderDialogState = FolderDialogUiState(
                            create = false,
                            name = it.title,
                            folder = it
                        )
                    },
                    onDeleteFolder = { confirmDeleteFolder(it) },             // 删除文件夹
                    onOpenSettings = { startPreferenceActivity() },           // 打开设置
                    onFolderDialogNameChange = { name ->                      // 对话框输入变化
                        folderDialogState = folderDialogState?.copy(name = name)
                    },
                    onDismissFolderDialog = { folderDialogState = null },     // 关闭对话框
                    onConfirmFolderDialog = { confirmFolderDialog() }         // 确认对话框
                )
            }
        }
    }

    /**
     * Activity 恢复显示时的回调
     * 
     * 每次返回列表页时刷新数据，确保显示最新状态。
     * 这样可以从设置页或编辑页返回后立即看到更新。
     */
    override fun onResume() {
        super.onResume()
        notesListViewModel.refresh(this)
    }

    // ==================== 导航与交互处理 ====================
    
    /**
     * 处理返回按钮点击
     * 
     * 根据当前状态执行不同操作：
     * 1. 如果处于选择模式（有选中项）→ 退出选择模式
     * 2. 如果不在根目录 → 返回上一级文件夹
     * 3. 如果在根目录 → 关闭 Activity
     */
    private fun handleBack() {
        val state = notesListViewModel.uiState.value
        when {
            state.selectedIds.isNotEmpty() -> notesListViewModel.clearSelection()
            !state.isRoot -> {
                notesListViewModel.goToRoot(this)
                rememberFolder(Notes.ID_ROOT_FOLDER.toLong(), "")
            }
            else -> finish()
        }
    }

    /**
     * 处理列表项点击
     * 
     * 行为逻辑：
     * - 选择模式激活时：只有非文件夹项可被选择
     * - 正常模式时：点击文件夹进入子目录，点击便签打开编辑页
     * 
     * @param item 被点击的列表项
     */
    private fun handleItemClick(item: NotesListItemUi) {
        val selectionActive = notesListViewModel.uiState.value.selectedIds.isNotEmpty()
        when {
            // 选择模式 && 非文件夹 → 切换选中状态
            selectionActive && !item.isFolder -> notesListViewModel.toggleSelection(item.id)
            // 选择模式 && 文件夹 → 无操作（文件夹不能多选）
            selectionActive -> Unit
            // 正常模式 && 文件夹 → 打开文件夹
            item.isFolder -> {
                notesListViewModel.openFolder(this, item)
                rememberFolder(item.id, item.title)
            }
            // 正常模式 && 便签 → 打开便签编辑
            else -> openNote(item.id)
        }
    }

    // ==================== 便签操作 ====================
    
    /**
     * 创建新便签
     * 
     * @param folderId 目标文件夹 ID（便签创建在哪个文件夹下）
     */
    private fun createNewNote(folderId: Long) {
        val intent = Intent(this, NoteEditActivity::class.java).apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            putExtra(Notes.INTENT_EXTRA_FOLDER_ID, folderId)
        }
        noteEditorLauncher.launch(intent)  // 启动编辑器，等待返回
    }

    /**
     * 打开已有便签
     * 
     * @param noteId 便签 ID
     */
    private fun openNote(noteId: Long) {
        val intent = Intent(this, NoteEditActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(Intent.EXTRA_UID, noteId)
        }
        noteEditorLauncher.launch(intent)
    }

    // ==================== 删除操作 ====================
    
    /**
     * 确认删除选中的便签
     * 
     * 根据用户设置决定是否显示确认对话框。
     * 
     * @param selectedCount 选中的项目数量
     */
    private fun confirmDeleteSelected(selectedCount: Int) {
        if (selectedCount == 0) {
            Toast.makeText(this, getString(R.string.menu_select_none), Toast.LENGTH_SHORT).show()
            return
        }

        if (!NotesPreferences.isDeleteConfirmationEnabled(this)) {
            deleteSelectedNotes()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.alert_title_delete))
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setMessage(
                resources.getQuantityString(
                    R.plurals.alert_message_delete_notes,
                    selectedCount,
                    selectedCount
                )
            )
            .setPositiveButton(android.R.string.ok) { _, _ -> deleteSelectedNotes() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * 确认删除文件夹
     * 
     * @param folder 要删除的文件夹
     */
    private fun confirmDeleteFolder(folder: NotesListItemUi) {
        if (!NotesPreferences.isDeleteConfirmationEnabled(this)) {
            deleteFolder(folder)
            return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.alert_title_delete))
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setMessage(getString(R.string.alert_message_delete_folder))
            .setPositiveButton(android.R.string.ok) { _, _ -> deleteFolder(folder) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * 执行删除选中的便签
     * 
     * 调用 ViewModel 删除便签，然后：
     * 1. 更新桌面小部件
     * 2. 刷新列表
     */
    private fun deleteSelectedNotes() {
        notesListViewModel.deleteSelectedNotes(context = this) { widgets ->
            updateWidgets(widgets)      // 更新受影响的桌面小部件
            notesListViewModel.refresh(this)  // 刷新列表
        }
    }

    /**
     * 执行删除文件夹
     * 
     * 注意：删除文件夹会同时删除文件夹内的所有便签。
     * 
     * @param folder 要删除的文件夹
     */
    private fun deleteFolder(folder: NotesListItemUi) {
        notesListViewModel.deleteFolder(context = this, folderId = folder.id) { widgets ->
            // 如果删除的是记住的文件夹，清除记住的状态
            if (NotesPreferences.getRememberedFolderId(this) == folder.id) {
                NotesPreferences.clearRememberedFolder(this)
            }
            updateWidgets(widgets)      // 更新受影响的桌面小部件
            notesListViewModel.refresh(this)  // 刷新列表
        }
    }

    // ==================== 文件夹管理对话框 ====================
    
    /**
     * 确认文件夹对话框的操作
     * 
     * 根据对话框状态执行创建新文件夹或重命名现有文件夹。
     */
    private fun confirmFolderDialog() {
        val dialogState = folderDialogState ?: return
        notesListViewModel.saveFolder(
            context = this,
            dialogState = dialogState,
            onDuplicateName = {
                // 文件夹名称重复时的错误提示
                Toast.makeText(
                    this,
                    getString(R.string.folder_exist, dialogState.name.trim()),
                    Toast.LENGTH_LONG
                ).show()
            },
            onComplete = { success ->
                if (success) {
                    // 重命名成功后，如果当前在重命名的文件夹内且记住了位置，更新记住的名称
                    dialogState.folder?.takeIf { !dialogState.create }?.let { folder ->
                        if (NotesPreferences.getRememberedFolderId(this) == folder.id) {
                            rememberFolder(folder.id, dialogState.name.trim())
                        }
                    }
                    folderDialogState = null
                    notesListViewModel.refresh(this)
                }
            }
        )
    }

    // ==================== 小部件更新 ====================
    
    /**
     * 更新桌面小部件
     * 
     * 当便签被删除或修改时，更新所有关联的桌面小部件
     * 
     * @param widgets 需要更新的小部件列表
     */
    private fun updateWidgets(widgets: Set<AppWidgetAttribute>) {
        NoteWidgetUpdater.updateWidgets(this, widgets)
    }

    // ==================== 其他功能 ====================
    
    /**
     * 启动设置页面
     */
    private fun startPreferenceActivity() {
        startActivity(Intent(this, NotesPreferenceActivity::class.java))
    }

    /**
     * 恢复上次打开的文件夹
     * 
     * 如果用户启用了"记住上次打开的文件夹"功能，
     * 则在应用启动时自动导航到上次浏览的文件夹。
     */
    private fun restoreLastFolder() {
        val folderId = NotesPreferences.getRememberedFolderId(this)
        if (folderId == Notes.ID_ROOT_FOLDER.toLong()) {
            return  // 根文件夹不需要恢复
        }

        notesListViewModel.restoreFolder(
            folderId = folderId,
            folderTitle = NotesPreferences.getRememberedFolderTitle(this)
        )
    }

    /**
     * 记住当前文件夹（用于下次启动恢复）
     * 
     * @param folderId 文件夹 ID
     * @param folderTitle 文件夹标题
     */
    private fun rememberFolder(folderId: Long, folderTitle: String) {
        NotesPreferences.rememberLastFolder(this, folderId, folderTitle)
    }

    /**
     * 从原始资源文件设置应用介绍便签
     * 
     * 在应用首次启动时，从 R.raw.introduction 读取介绍文本，
     * 并创建一个默认便签作为初始内容。
     * 
     * 实现原理：
     * 1. 检查 SharedPreferences 是否已添加过介绍便签
     * 2. 如果是首次启动，从 raw 资源读取介绍文本
     * 3. 创建新的便签并写入介绍内容
     * 4. 标记已添加，避免重复创建
     */
    private fun setAppInfoFromRawRes() {
        val preferences = defaultPreferences()
        // 检查是否已添加过介绍便签
        if (preferences.getBoolean(PREFERENCE_ADD_INTRODUCTION, false)) {
            return
        }

        // 读取原始资源文件中的介绍文本
        val introduction = runCatching {
            resources.openRawResource(R.raw.introduction).bufferedReader().use { it.readText() }
        }.getOrElse { error ->
            Log.e(TAG, "Read introduction file error", error)
            return
        }

        // 创建一个新的便签并设置介绍内容
        val note = WorkingNote.createEmptyNote(
            this,
            Notes.ID_ROOT_FOLDER.toLong(),                      // 根文件夹
            AppWidgetManager.INVALID_APPWIDGET_ID,             // 无关联小部件
            Notes.TYPE_WIDGET_INVALIDE,                        // 无效的小部件类型
            ResourceParser.getDefaultBgId(this)               // 默认背景颜色
        )
        note.setWorkingText(introduction)  // 设置介绍文本
        if (note.saveNote()) {
            // 标记已添加，下次启动不再重复创建
            preferences.edit { putBoolean(PREFERENCE_ADD_INTRODUCTION, true) }
        } else {
            Log.e(TAG, "Save introduction note error")
        }
    }

    // ==================== 伴生对象 ====================
    
    companion object {
        private const val TAG = "NotesListActivity"                              // 日志标签
        private const val PREFERENCE_ADD_INTRODUCTION = "net.micode.notes.introduction"  // 首选项 Key
    }
}