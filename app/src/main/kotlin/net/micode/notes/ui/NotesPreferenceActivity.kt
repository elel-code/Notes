package net.micode.notes.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import net.micode.notes.tool.NotesPreferences

/**
 * 便签应用设置页面 Activity
 * 
 * 该 Activity 负责显示和管理应用的所有用户偏好设置。
 * 
 * 主要功能：
 * 1. 新建便签背景颜色随机 - 开关控制
 * 2. 默认新建便签颜色 - 颜色选择（黄、蓝、白、绿、红）
 * 3. 列表排序 - 排序方式选择（最近修改优先、最早修改优先、按标题排序）
 * 4. 删除前确认 - 开关控制
 * 5. 记住上次打开的文件夹 - 开关控制
 * 
 * 技术特点：
 * - 使用 Jetpack Compose 构建 UI
 * - 使用 mutableStateOf 管理 UI 状态
 * - 设置变更后立即更新 UI 状态
 * - 遵循 Material 3 设计规范
 * 
 * @author MiCode Open Source Community
 */
class NotesPreferenceActivity : ComponentActivity() {
    
    // ==================== 成员变量 ====================
    
    /**
     * 设置页面的 UI 状态
     * 
     * 使用 mutableStateOf 确保状态变化时 UI 自动重组。
     * 存储所有设置项的当前值。
     */
    private var uiState by mutableStateOf(NotesSettingsUiState())

    // ==================== 生命周期方法 ====================
    
    /**
     * Activity 创建时的回调
     * 
     * 执行流程：
     * 1. 配置窗口（边到边显示，内容延伸到系统栏区域）
     * 2. 从 SharedPreferences 加载所有设置项的状态
     * 3. 设置 Compose UI，传入状态和事件回调
     * 
     * @param savedInstanceState 保存的实例状态（本 Activity 未使用）
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 配置窗口：让内容延伸到系统栏区域（边到边显示）
        // 这样设置页面的背景可以延伸到状态栏和导航栏下方
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // 从 SharedPreferences 加载当前设置状态
        uiState = loadUiState()

        // 设置 Compose UI
        setContent {
            MaterialTheme {
                NotesPreferenceScreen(
                    state = uiState,                                        // 当前设置状态
                    onBack = { finish() },                                  // 返回按钮回调
                    onToggleRandomBackground = { enabled ->                 // 随机背景开关切换
                        NotesPreferences.setRandomBackgroundEnabled(this, enabled)
                        uiState = uiState.copy(randomBackgroundEnabled = enabled)
                    },
                    onSelectDefaultBackgroundColor = { colorId ->           // 默认背景颜色选择
                        NotesPreferences.setDefaultBackgroundColor(this, colorId)
                        uiState = uiState.copy(defaultBackgroundColor = colorId)
                    },
                    onSelectListSortMode = { sortMode ->                    // 列表排序模式选择
                        NotesPreferences.setListSortMode(this, sortMode)
                        uiState = uiState.copy(listSortMode = sortMode)
                    },
                    onToggleDeleteConfirmation = { enabled ->               // 删除确认开关切换
                        NotesPreferences.setDeleteConfirmationEnabled(this, enabled)
                        uiState = uiState.copy(deleteConfirmationEnabled = enabled)
                    },
                    onToggleRememberLastFolder = { enabled ->               // 记住上次文件夹开关切换
                        NotesPreferences.setRememberLastFolderEnabled(this, enabled)
                        uiState = uiState.copy(rememberLastFolderEnabled = enabled)
                    }
                )
            }
        }
    }

    // ==================== 私有辅助方法 ====================
    
    /**
     * 加载设置状态
     * 
     * 从 SharedPreferences 中读取所有设置项的当前值，
     * 并封装成 NotesSettingsUiState 数据类返回。
     * 
     * 读取的设置项：
     * - randomBackgroundEnabled: 新建便签背景颜色是否随机
     * - defaultBackgroundColor: 默认新建便签颜色 ID
     * - listSortMode: 列表排序模式
     * - deleteConfirmationEnabled: 删除前是否确认
     * - rememberLastFolderEnabled: 是否记住上次打开的文件夹
     * 
     * @return 包含所有设置当前值的 NotesSettingsUiState 对象
     */
    private fun loadUiState(): NotesSettingsUiState {
        return NotesSettingsUiState(
            randomBackgroundEnabled = NotesPreferences.isRandomBackgroundEnabled(this),
            defaultBackgroundColor = NotesPreferences.getDefaultBackgroundColor(this),
            listSortMode = NotesPreferences.getListSortMode(this),
            deleteConfirmationEnabled = NotesPreferences.isDeleteConfirmationEnabled(this),
            rememberLastFolderEnabled = NotesPreferences.isRememberLastFolderEnabled(this)
        )
    }
}