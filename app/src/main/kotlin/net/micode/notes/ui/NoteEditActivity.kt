package net.micode.notes.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.micode.notes.R
import net.micode.notes.data.Notes
import net.micode.notes.data.Notes.TextNote
import net.micode.notes.data.NotesRepository
import net.micode.notes.model.WorkingNote
import net.micode.notes.tool.NotesPreferences
import net.micode.notes.tool.PendingIntentCompat
import net.micode.notes.tool.ResourceParser
import net.micode.notes.tool.defaultPreferences
import net.micode.notes.widget.NoteWidgetUpdater

/**
 * 便签编辑活动
 * 
 * 这是整个便签应用的核心编辑界面，负责便签的创建、编辑、删除、分享等功能。
 * 
 * 主要功能：
 * 1. 便签编辑：支持普通文本和待办清单两种编辑模式
 * 2. 便签管理：新建、保存、删除便签
 * 3. 提醒功能：设置/取消闹钟提醒
 * 4. 分享导出：分享文本、导出TXT、生成图片
 * 5. 桌面快捷方式：将便签添加到桌面
 * 6. 个性化设置：背景颜色、字体大小
 * 
 * 技术特点：
 * - 使用 Jetpack Compose 构建 UI
 * - 使用 ViewModel 管理数据
 * - 支持 Activity 重启后恢复状态
 * - 支持多种 Intent 启动模式（查看/新建）
 * 
 * @author MiCode Open Source Community
 */
class NoteEditActivity : ComponentActivity() {
    
    // ==================== 成员变量 ====================
    
    /**
     * 当前正在编辑的工作便签
     * WorkingNote 封装了便签的数据操作逻辑
     */
    private lateinit var workingNote: WorkingNote
    
    /**
     * 共享偏好设置实例
     * 存储用户的个性化设置（如字体大小）
     */
    private lateinit var sharedPrefs: SharedPreferences
    
    /**
     * UI 状态（使用 Compose 的状态管理）
     * mutableStateOf 会自动触发 UI 重组
     */
    private var uiState by mutableStateOf(NoteEditUiState())
    
    /**
     * 删除确认对话框是否可见
     */
    private var deleteDialogVisible by mutableStateOf(false)
    
    /**
     * 提醒设置对话框状态
     * null 表示不显示对话框
     */
    private var reminderDialogState by mutableStateOf<ReminderDialogUiState?>(null)

    /**
     * 便签编辑页面的 ViewModel
     * 负责处理导出、生成长图等耗时操作
     */
    private val noteEditViewModel: NoteEditViewModel by lazy {
        ViewModelProvider(this)[NoteEditViewModel::class.java]
    }
    
    /**
     * 便签数据仓库
     * 提供数据库访问接口
     */
    private val notesRepository: NotesRepository by lazy {
        NotesRepository(applicationContext)
    }

    // ==================== 生命周期方法 ====================
    
    /**
     * Activity 创建时的回调
     * 
     * 初始化流程：
     * 1. 配置窗口（支持边到边显示）
     * 2. 加载用户偏好设置
     * 3. 根据 Intent 初始化 Activity 状态（新建/编辑便签）
     * 4. 注册返回键回调（保存后退出）
     * 5. 设置 Compose UI 界面
     * 
     * @param savedInstanceState 保存的实例状态，用于 Activity 重建后恢复便签 ID
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 配置窗口：让内容延伸到系统栏区域（边到边显示）
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // 获取共享偏好设置实例
        sharedPrefs = defaultPreferences()
        
        // 判断是否有保存的状态（如屏幕旋转后恢复）
        // 如果有，从 savedInstanceState 中恢复便签 ID 并构建 Intent
        val initialIntent = if (savedInstanceState?.containsKey(Intent.EXTRA_UID) == true) {
            Intent(Intent.ACTION_VIEW).apply {
                putExtra(Intent.EXTRA_UID, savedInstanceState.getLong(Intent.EXTRA_UID))
            }
        } else {
            intent  // 使用原始 Intent
        }

        // 初始化 Activity 状态（加载便签数据）
        // 返回 false 表示初始化失败（如便签不存在），需要关闭 Activity
        if (!initActivityState(initialIntent)) {
            finish()
            return
        }

        // 注册返回键回调
        // 用户按返回键时，先保存便签再退出
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveNote()   // 保存便签内容
                finish()     // 关闭 Activity
            }
        })

        // 设置 Compose UI
        setContent {
            MaterialTheme {
                NoteEditScreen(
                    state = uiState,
                    deleteDialogVisible = deleteDialogVisible,
                    reminderDialogState = reminderDialogState,
                    onBack = {
                        saveNote()
                        finish()
                    },
                    onContentChange = { uiState = uiState.copy(content = it) },
                    onNewNote = { createNewNote() },
                    onDelete = { requestDeleteCurrentNote() },
                    onDismissDeleteDialog = { deleteDialogVisible = false },
                    onConfirmDelete = { confirmDeleteCurrentNote() },
                    onToggleChecklist = { toggleChecklistMode() },
                    onShare = { shareCurrentNote() },
                    onExportText = { exportCurrentNoteAsTxt() },
                    onSaveLongImage = { saveCurrentNoteAsLongImage() },
                    onSendToDesktop = { sendToDesktop() },
                    onSetReminder = {
                        reminderDialogState = ReminderDialogUiState(
                            initialDate = uiState.alertDate.takeIf { it > 0L }
                                ?: System.currentTimeMillis()
                        )
                    },
                    onDismissReminderDialog = { reminderDialogState = null },
                    onConfirmReminder = { date ->
                        reminderDialogState = null
                        workingNote.setAlertDate(date)
                        updateAlarm(date, true)
                        uiState = uiState.copy(alertDate = date)
                    },
                    onClearReminder = { clearReminder() },
                    onSelectBackground = { bgColorId ->
                        workingNote.bgColorId = bgColorId
                        syncUiStateFromWorkingNote(uiState.content)
                    },
                    onSelectFontSize = { fontSizeId ->
                        sharedPrefs.edit { putInt(PREFERENCE_FONT_SIZE, fontSizeId) }
                        uiState = uiState.copy(fontSizeId = fontSizeId)
                    }
                )
            }
        }
    }

    /**
     * 处理新的 Intent（当 Activity 已存在时）
     * 
     * 应用场景：
     * - 从桌面快捷方式打开便签
     * - 从其他应用通过 Intent 打开
     * - 通知栏点击提醒
     * 
     * @param intent 新的 Intent 对象
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 重新初始化 Activity 状态
        if (initActivityState(intent)) {
            // 同步 UI 状态
            syncUiStateFromWorkingNote()
        }
    }

    /**
     * Activity 恢复显示时的回调
     * 
     * 确保 UI 显示最新的便签内容
     */
    override fun onResume() {
        super.onResume()
        syncUiStateFromWorkingNote(uiState.content)
    }

    /**
     * Activity 暂停时的回调
     * 
     * 自动保存便签内容，防止数据丢失
     */
    override fun onPause() {
        super.onPause()
        saveNote()
    }

    /**
     * 保存 Activity 状态（用于屏幕旋转等配置变更）
     * 
     * @param outState 用于保存状态的 Bundle
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // 确保便签已保存到数据库
        if (!workingNote.existInDatabase()) {
            saveNote()
        }
        // 保存便签 ID 以便重建时恢复
        outState.putLong(Intent.EXTRA_UID, workingNote.noteId)
    }

    // ==================== Activity 状态初始化 ====================
    
    /**
     * 初始化 Activity 状态
     * 
     * 根据 Intent 的 Action 类型决定操作：
     * - ACTION_VIEW：打开已有便签进行查看/编辑
     * - ACTION_INSERT_OR_EDIT：创建新便签或编辑现有便签
     * 
     * 此方法处理两种场景：
     * 1. 查看已有便签：检查便签是否存在，加载数据
     * 2. 新建便签：创建新的空便签，支持传入文件夹ID、小部件ID等参数
     * 
     * @param intent 启动 Activity 的 Intent
     * @return true 表示初始化成功，false 表示失败（会关闭 Activity）
     */
    private fun initActivityState(intent: Intent): Boolean {
        // 处理 ACTION_VIEW：查看已有便签
        @Suppress("DEPRECATION")
        val note = if (intent.action == Intent.ACTION_VIEW) {
            // 获取便签 ID
            var noteId = intent.getLongExtra(Intent.EXTRA_UID, 0)

            // 验证便签是否可见（未被删除且不在回收站）
            val visible = runBlocking(Dispatchers.IO) {
                notesRepository.isVisibleNote(noteId, Notes.TYPE_NOTE)
            }
            if (!visible) {
                // 便签不存在，跳转到列表页并提示错误
                startActivity(Intent(this, NotesListActivity::class.java))
                showToast(R.string.error_note_not_exist)
                return false
            }

            // 配置软键盘：初始隐藏，调整布局大小
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
            
            // 加载便签数据
            loadWorkingNote(noteId) ?: return false
            
        // 处理 ACTION_INSERT_OR_EDIT：新建或编辑便签
        } else if (intent.action == Intent.ACTION_INSERT_OR_EDIT) {
            // 获取可选参数
            val folderId = intent.getLongExtra(Notes.INTENT_EXTRA_FOLDER_ID, 0)      // 目标文件夹 ID
            val widgetId = intent.getIntExtra(
                Notes.INTENT_EXTRA_WIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )  // 来源小部件 ID
            val widgetType = intent.getIntExtra(
                Notes.INTENT_EXTRA_WIDGET_TYPE,
                Notes.TYPE_WIDGET_INVALIDE
            )  // 小部件类型
            val bgResId = intent.getIntExtra(
                Notes.INTENT_EXTRA_BACKGROUND_ID,
                ResourceParser.getDefaultBgId(this)
            )  // 背景颜色资源 ID

            // 通话便签相关数据
            val phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)  // 电话号码
            val callDate = intent.getLongExtra(Notes.INTENT_EXTRA_CALL_DATE, 0) // 通话时间
            
            // 创建便签（可能是通话便签或普通便签）
            val createdNote = if (callDate != 0L && phoneNumber != null) {
                // 通话便签：先查询是否已存在相同通话的便签
                val noteId = runBlocking(Dispatchers.IO) {
                    notesRepository.getNoteIdByPhoneNumberAndCallDate(phoneNumber, callDate)
                }
                if (noteId > 0) {
                    // 已存在则加载现有便签
                    loadWorkingNote(noteId) ?: return false
                } else {
                    // 不存在则创建新的通话便签
                    WorkingNote.createEmptyNote(this, folderId, widgetId, widgetType, bgResId).apply {
                        convertToCallNote(phoneNumber, callDate)
                    }
                }
            } else {
                // 普通便签：直接创建空便签
                WorkingNote.createEmptyNote(this, folderId, widgetId, widgetType, bgResId)
            }

            // 配置软键盘：显示键盘，调整布局大小
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            )
            createdNote
            
        } else {
            // 不支持的 Action
            Log.e(TAG, "Intent not specified action, should not support")
            return false
        }

        // 保存便签引用并同步 UI 状态
        workingNote = note
        deleteDialogVisible = false
        reminderDialogState = null
        syncUiStateFromWorkingNote()
        return true
    }

    /**
     * 根据 ID 加载工作便签
     * 
     * @param noteId 便签 ID
     * @return WorkingNote 实例，加载失败时返回 null
     */
    private fun loadWorkingNote(noteId: Long): WorkingNote? {
        return runCatching { WorkingNote.load(this, noteId) }
            .onFailure { error ->
                Log.e(TAG, "load note failed with note id $noteId", error)
            }
            .getOrNull()
    }

    // ==================== UI 状态同步 ====================
    
    /**
     * 同步 UI 状态
     * 
     * 将 WorkingNote 中的数据同步到 Compose UI 状态中
     * 
     * @param contentOverride 可选的覆盖内容（用于保留用户未保存的输入）
     */
    private fun syncUiStateFromWorkingNote(contentOverride: String? = null) {
        // 获取字体大小设置（默认值使用资源文件中定义的默认字体）
        val fontSizeId = sharedPrefs.getInt(PREFERENCE_FONT_SIZE, ResourceParser.BG_DEFAULT_FONT_SIZE)
            .takeIf { it < 4 }  // 确保值在有效范围内（0-3）
            ?: ResourceParser.BG_DEFAULT_FONT_SIZE

        // 更新 UI 状态
        uiState = NoteEditUiState(
            content = contentOverride ?: workingNote.content.orEmpty(),  // 便签内容
            bgColorId = workingNote.bgColorId,                           // 背景颜色 ID
            bgResId = workingNote.bgColorResId,                          // 背景资源 ID
            titleBgResId = workingNote.titleBgResId,                     // 标题栏背景资源 ID
            fontSizeId = fontSizeId,                                      // 字体大小 ID
            modifiedDate = if (workingNote.modifiedDate > 0L) {          // 最后修改时间
                workingNote.modifiedDate
            } else {
                System.currentTimeMillis()
            },
            alertDate = workingNote.alertDate,                           // 提醒时间
            isChecklistMode = workingNote.checkListMode == TextNote.MODE_CHECK_LIST  // 是否为清单模式
        )
    }

    // ==================== 清单模式转换 ====================
    
    /**
     * 切换清单模式
     * 
     * 在普通文本模式和待办清单模式之间切换
     * - 切换到清单模式：为每行文本添加复选框符号（□）
     * - 切换到普通模式：移除复选框符号
     */
    private fun toggleChecklistMode() {
        val enableChecklist = workingNote.checkListMode != TextNote.MODE_CHECK_LIST
        val updatedContent = if (enableChecklist) {
            toChecklistText(uiState.content)   // 转换为清单格式
        } else {
            fromChecklistText(uiState.content) // 转换为普通格式
        }
        workingNote.checkListMode = if (enableChecklist) TextNote.MODE_CHECK_LIST else 0
        uiState = uiState.copy(
            content = updatedContent,
            isChecklistMode = enableChecklist
        )
    }

    /**
     * 将普通文本转换为清单文本
     * 
     * 格式：每行前添加 "□" 符号
     * 
     * @param text 普通文本
     * @return 清单格式文本
     */
    private fun toChecklistText(text: String): String {
        val normalized = fromChecklistText(text)
        return normalized.lineSequence()
            .filter { it.isNotBlank() }  // 过滤空行
            .joinToString("\n") { "$TAG_UNCHECKED ${it.trim()}" }
    }

    /**
     * 将清单文本转换为普通文本
     * 
     * 移???每行前的 "□" 或 "√" 符号
     * 
     * @param text 清单格式文本
     * @return 普通文本
     */
    private fun fromChecklistText(text: String): String {
        return text.lineSequence()
            .joinToString("\n") { line ->
                line.removePrefix("$TAG_CHECKED ")
                    .removePrefix("$TAG_UNCHECKED ")
            }
            .trimEnd()  // 移除末尾空行
    }

    // ==================== 便签删除操作 ====================
    
    /**
     * 删除当前便签
     * 
     * 执行删除逻辑：
     * 1. 从数据库删除便签记录
     * 2. 标记 WorkingNote 为已删除
     * 3. 更新桌面小部件（如果关联了小部件）
     */
    private fun deleteCurrentNote() {
        if (workingNote.existInDatabase()) {
            val id = workingNote.noteId
            val deleted = if (id != Notes.ID_ROOT_FOLDER.toLong()) {
                runBlocking(Dispatchers.IO) {
                    notesRepository.deleteNotes(setOf(id))  // 删除便签
                }
            } else {
                false  // 根文件夹不能删除
            }
            if (!deleted) {
                Log.e(TAG, "Delete note failed")
            }
        }
        workingNote.markDeleted()  // 标记为已删除
        updateWidgetIfNeeded()      // 更新小部件
    }

    /**
     * 请求删除当前便签（显示确认对话框）
     * 
     * 根据用户设置决定是否显示确认对话框
     */
    private fun requestDeleteCurrentNote() {
        if (NotesPreferences.isDeleteConfirmationEnabled(this)) {
            deleteDialogVisible = true  // 显示确认对话框
        } else {
            confirmDeleteCurrentNote()  // 直接删除
        }
    }

    /**
     * 确认删除当前便签
     */
    private fun confirmDeleteCurrentNote() {
        deleteDialogVisible = false
        setResult(RESULT_OK)
        deleteCurrentNote()
        finish()
    }

    // ==================== 提醒功能 ====================
    
    /**
     * 清除提醒
     * 
     * 取消已设置的闹钟提醒
     */
    private fun clearReminder() {
        reminderDialogState = null
        workingNote.setAlertDate(0)      // 清除提醒时间
        updateAlarm(0, false)            // 取消闹钟
        uiState = uiState.copy(alertDate = 0L)
    }

    /**
     * 更新闹钟设置
     * 
     * 使用 AlarmManager 设置或取消闹钟
     * 
     * @param date 提醒时间（毫秒时间戳）
     * @param set true 表示设置闹钟，false 表示取消闹钟
     */
    private fun updateAlarm(date: Long, set: Boolean) {
        // 确保便签已保存到数据库
        if (!workingNote.existInDatabase()) {
            saveNote()
        }
        if (workingNote.noteId <= 0) {
            showToast(R.string.error_note_empty_for_clock)
            return
        }

        // 创建 PendingIntent
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra(Intent.EXTRA_UID, workingNote.noteId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntentCompat.immutableFlag()  // Android 12+ 需要声明可变性
        )
        
        // 设置或取消闹钟
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        if (!set) {
            alarmManager.cancel(pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, date, pendingIntent)
        }
    }

    // ==================== 便签创建与管理 ====================
    
    /**
     * 创建新便签
     * 
     * 保存当前便签后，启动一个新的 NoteEditActivity 用于创建便签
     */
    private fun createNewNote() {
        saveNote()
        finish()
        startActivity(
            Intent(this, NoteEditActivity::class.java).apply {
                action = Intent.ACTION_INSERT_OR_EDIT
                putExtra(Notes.INTENT_EXTRA_FOLDER_ID, workingNote.folderId)  // 在同一文件夹下创建
            }
        )
    }

    /**
     * 保存便签
     * 
     * @return true 保存成功，false 保存失败
     */
    private fun saveNote(): Boolean {
        workingNote.setWorkingText(uiState.content)  // 设置内容
        val saved = workingNote.saveNote()            // 保存到数据库
        if (saved) {
            setResult(RESULT_OK)                      // 设置成功结果
            uiState = uiState.copy(modifiedDate = workingNote.modifiedDate)  // 更新修改时间
            updateWidgetIfNeeded()                     // 更新小部件
        }
        return saved
    }

    // ==================== 分享与导出功能 ====================
    
    /**
     * 分享当前便签
     * 
     * 使用系统分享功能，支持发送到其他应用
     */
    private fun shareCurrentNote() {
        val text = currentNoteTextForShare
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
            type = "text/plain"
        }
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.share_note_chooser_title)))
        } catch (error: ActivityNotFoundException) {
            Log.e(TAG, "Share note failed", error)
            showToast(R.string.error_no_share_app)
        }
    }

    /**
     * 导出当前便签为 TXT 文件
     * 
     * 将便签内容以纯文本格式保存到存储设备
     */
    private fun exportCurrentNoteAsTxt() {
        val noteText = currentNoteTextForExport
        if (noteText.isBlank()) {
            showToast(R.string.error_note_empty_for_export)
            return
        }

        noteEditViewModel.exportCurrentNoteAsTxt(applicationContext, noteText) { exported ->
            showToast(if (exported) R.string.success_sdcard_export else R.string.error_sdcard_export)
        }
    }

    /**
     * 保存当前便签为长图片
     * 
     * 将便签内容生成为图片并保存到相册
     */
    private fun saveCurrentNoteAsLongImage() {
        val noteText = currentNoteTextForLongImage
        if (noteText.isBlank()) {
            showToast(R.string.error_note_empty_for_export)
            return
        }

        noteEditViewModel.saveCurrentNoteAsLongImage(
            applicationContext,
            noteText,
            currentEditorTextSize(),
            workingNote.bgColorResId,
            resources.displayMetrics.widthPixels
        ) { saved ->
            showToast(
                if (saved) R.string.success_long_image_saved else R.string.error_long_image_save_failed
            )
        }
    }

    // ==================== 桌面快捷方式 ====================
    
    /**
     * 创建桌面快捷方式
     * 
     * 将当前便签以快捷方式的形式添加到桌面
     * 
     * @deprecated Android 26+ 不再支持使用广播添加快捷方式，
     * 应使用 ShortcutManager 框架
     */
    @Suppress("DEPRECATION")
    private fun sendToDesktop() {
        // 确保便签已保存（需要有效的 noteId）
        if (!workingNote.existInDatabase()) {
            saveNote()
        }

        if (workingNote.noteId <= 0) {
            showToast(R.string.error_note_empty_for_send_to_desktop)
            return
        }

        // 创建快捷方式 Intent
        val sender = Intent()
        val shortcutIntent = Intent(this, NoteEditActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(Intent.EXTRA_UID, workingNote.noteId)
        }
        
        // 设置快捷方式属性
        sender.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)        // 点击后打开的 Intent
        sender.putExtra(Intent.EXTRA_SHORTCUT_NAME, makeShortcutIconTitle(uiState.content))  // 快捷方式名称
        sender.putExtra(
            Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
            Intent.ShortcutIconResource.fromContext(this, R.drawable.icon_app)  // 图标
        )
        sender.putExtra("duplicate", true)  // 允许重复创建
        sender.action = "com.android.launcher.action.INSTALL_SHORTCUT"  // 安装快捷方式的广播
        
        // 发送广播创建快捷方式
        sendBroadcast(sender)
        showToast(R.string.info_note_enter_desktop)
    }

    // ==================== 小部件更新 ====================
    
    /**
     * 更新桌面小部件
     * 
     * 如果当前便签关联了桌面小部件，则更新小部件显示
     */
    private fun updateWidgetIfNeeded() {
        if (workingNote.widgetId == AppWidgetManager.INVALID_APPWIDGET_ID ||
            workingNote.widgetType == Notes.TYPE_WIDGET_INVALIDE
        ) {
            return  // 未关联小部件
        }
        NoteWidgetUpdater.updateWidget(this, workingNote.widgetId, workingNote.widgetType)
    }

    // ==================== 辅助方法与属性 ====================
    
    /**
     * 获取当前编辑器的文本大小（像素）
     * 
     * 根据用户设置的字体大小 ID 计算实际像素值
     * 
     * @return 文本大小（像素）
     */
    private fun currentEditorTextSize(): Float {
        val spValue = when (uiState.fontSizeId) {
            ResourceParser.TEXT_SMALL -> 15   // 小号字体
            ResourceParser.TEXT_MEDIUM -> 18  // 正常字体
            ResourceParser.TEXT_LARGE -> 22   // 大号字体
            ResourceParser.TEXT_SUPER -> 26   // 超大字体
            else -> 18                        // 默认正常字体
        }
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            spValue.toFloat(),
            resources.displayMetrics
        )
    }

    /**
     * 用于导出的便签文本（转换为普通文本）
     * 
     * 导出时使用纯文本格式，移除清单符号
     */
    private val currentNoteTextForExport: String
        get() = fromChecklistText(uiState.content)

    /**
     * 用于分享的便签文本（保持原始格式）
     */
    private val currentNoteTextForShare: String
        get() = uiState.content

    /**
     * 用于生成长图片的便签文本（保持原始格式）
     */
    private val currentNoteTextForLongImage: String
        get() = uiState.content

    /**
     * 生成快捷方式标题
     * 
     * @param content 便签内容
     * @return 截断后的标题（最多10个字符）
     */
    private fun makeShortcutIconTitle(content: String): String {
        val title = content.replace(TAG_CHECKED, "").replace(TAG_UNCHECKED, "")
        return if (title.length > SHORTCUT_ICON_TITLE_MAX_LEN) {
            title.substring(0, SHORTCUT_ICON_TITLE_MAX_LEN)
        } else {
            title
        }
    }

    /**
     * 显示 Toast 消息
     * 
     * @param resId 字符串资源 ID
     * @param duration 显示时长（Toast.LENGTH_SHORT 或 Toast.LENGTH_LONG）
     */
    private fun showToast(resId: Int, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, resId, duration).show()
    }

    // ==================== 伴生对象 ====================
    
    companion object {
        private const val TAG = "NoteEditActivity"                      // 日志标签
        private const val PREFERENCE_FONT_SIZE = "pref_font_size"       // 字体大小偏好设置的 Key
        private const val SHORTCUT_ICON_TITLE_MAX_LEN = 10              // 快捷方式标题最大长度
        
        /**
         * 已选中复选框符号（√）
         * 用于清单模式中表示已完成的项目
         */
        val TAG_CHECKED: String = '\u221A'.toString()
        
        /**
         * 未选中复选框符号（□）
         * 用于清单模式中表示未完成的项目
         */
        val TAG_UNCHECKED: String = '\u25A1'.toString()
    }
}