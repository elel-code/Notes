package net.micode.notes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.micode.notes.R
import net.micode.notes.tool.NotesSortMode
import net.micode.notes.tool.ResourceParser

/**
 * 设置页面的 UI 状态数据类
 * 
 * 存储应用的所有用户偏好设置项的值。
 * 使用不可变数据类，确保状态一致性。
 * 
 * @property randomBackgroundEnabled 新建便签时是否随机选择背景颜色
 * @property defaultBackgroundColor 默认新建便签的背景颜色 ID（随机关闭时使用）
 * @property listSortMode 列表排序模式（最近修改优先/最早修改优先/按标题排序）
 * @property deleteConfirmationEnabled 删除操作前是否显示确认对话框
 * @property rememberLastFolderEnabled 是否记住上次打开的文件夹（应用重启后恢复）
 */
data class NotesSettingsUiState(
    val randomBackgroundEnabled: Boolean = false,
    val defaultBackgroundColor: Int = ResourceParser.BG_DEFAULT_COLOR,
    val listSortMode: NotesSortMode = NotesSortMode.MODIFIED_DESC,
    val deleteConfirmationEnabled: Boolean = true,
    val rememberLastFolderEnabled: Boolean = true
)

/**
 * 设置页面主屏幕
 * 
 * 这是应用设置界面的核心 Composable 组件，负责显示和管理所有偏好设置项。
 * 
 * 主要功能：
 * 1. 随机背景开关 - 控制新建便签时是否随机选择背景颜色
 * 2. 默认颜色选择器 - 5 种颜色可选（黄、蓝、白、绿、红）
 * 3. 列表排序选择器 - 3 种排序方式可选
 * 4. 删除确认开关
 * 5. 记住上次文件夹开关
 * 
 * UI 设计特点：
 * - 使用圆角卡片 (Card) 组织每个设置项
 * - 开关设置项使用 Switch 组件
 * - 颜色选择使用圆形预览色块 + 文字标签
 * - 排序选择使用单选按钮式卡片
 * - 背景使用与应用列表相同的纸张纹理
 * 
 * @param state 当前设置状态
 * @param onBack 返回按钮回调
 * @param onToggleRandomBackground 随机背景开关切换回调
 * @param onSelectDefaultBackgroundColor 默认背景颜色选择回调
 * @param onSelectListSortMode 列表排序模式选择回调
 * @param onToggleDeleteConfirmation 删除确认开关切换回调
 * @param onToggleRememberLastFolder 记住上次文件夹开关切换回调
 */
@Composable
fun NotesPreferenceScreen(
    state: NotesSettingsUiState,
    onBack: () -> Unit,
    onToggleRandomBackground: (Boolean) -> Unit,
    onSelectDefaultBackgroundColor: (Int) -> Unit,
    onSelectListSortMode: (NotesSortMode) -> Unit,
    onToggleDeleteConfirmation: (Boolean) -> Unit,
    onToggleRememberLastFolder: (Boolean) -> Unit
) {
    // 主容器：填充整个屏幕
    Box(modifier = Modifier.fillMaxSize()) {
        // 背景层：使用与应用列表相同的纸张纹理背景
        ResourceDrawableBackground(
            resId = R.drawable.list_background,
            modifier = Modifier.fillMaxSize()
        )
        
        // 主内容列
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()  // 适配状态栏高度
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 返回按钮
                TextButton(onClick = onBack) {
                    Text(text = stringResource(R.string.action_back))  // "返回"
                }
                // 标题
                Text(
                    text = stringResource(R.string.preferences_title),  // "设置"
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // 设置项列表（可滚动区域）
            Column(
                modifier = Modifier
                    .weight(1f)  // 占据剩余空间
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)  // 设置项之间间距 14dp
            ) {
                // 1. 随机背景开关
                SettingSwitchCard(
                    title = stringResource(R.string.preferences_bg_random_appear_title),
                    summary = if (state.randomBackgroundEnabled) {
                        stringResource(R.string.state_enabled)  // "已开启"
                    } else {
                        stringResource(R.string.state_disabled)  // "已关闭"
                    },
                    checked = state.randomBackgroundEnabled,
                    onCheckedChange = onToggleRandomBackground
                )

                // 2. 默认背景颜色选择
                Card(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 标题
                        Text(
                            text = stringResource(R.string.preferences_default_color_title),  // "默认新建便签颜色"
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        // 当前选中的颜色名称
                        Text(
                            text = colorLabel(state.defaultBackgroundColor),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        // 5 种颜色选项（水平排列）
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            noteColorOptions().forEach { option ->
                                ColorOptionButton(
                                    modifier = Modifier.weight(1f),  // 等宽分布
                                    option = option,
                                    selected = option.colorId == state.defaultBackgroundColor,
                                    onClick = { onSelectDefaultBackgroundColor(option.colorId) }
                                )
                            }
                        }
                    }
                }

                // 3. 列表排序选择
                Card(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.preferences_sort_title),  // "列表排序"
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        // 3 种排序选项（垂直排列）
                        sortOptions().forEach { option ->
                            SelectionRow(
                                label = stringResource(option.labelRes),
                                selected = state.listSortMode == option.sortMode,
                                onClick = { onSelectListSortMode(option.sortMode) }
                            )
                        }
                    }
                }

                // 4. 删除确认开关
                SettingSwitchCard(
                    title = stringResource(R.string.preferences_delete_confirmation_title),  // "删除前确认"
                    summary = if (state.deleteConfirmationEnabled) {
                        stringResource(R.string.state_enabled)
                    } else {
                        stringResource(R.string.state_disabled)
                    },
                    checked = state.deleteConfirmationEnabled,
                    onCheckedChange = onToggleDeleteConfirmation
                )

                // 5. 记住上次文件夹开关
                SettingSwitchCard(
                    title = stringResource(R.string.preferences_remember_last_folder_title),  // "记住上次打开的文件夹"
                    summary = if (state.rememberLastFolderEnabled) {
                        stringResource(R.string.state_enabled)
                    } else {
                        stringResource(R.string.state_disabled)
                    },
                    checked = state.rememberLastFolderEnabled,
                    onCheckedChange = onToggleRememberLastFolder
                )
            }

            // 底部占位符（适配导航栏）
            Spacer(
                modifier = Modifier
                    .navigationBarsPadding()
                    .size(1.dp)
            )
        }
    }
}

/**
 * 设置开关卡片组件
 * 
 * 用于显示带标题、摘要和开关按钮的设置项。
 * 常用于开启/关闭功能的设置项。
 * 
 * @param title 标题文字
 * @param summary 摘要/状态描述文字
 * @param checked 开关当前状态
 * @param onCheckedChange 状态变更回调
 */
@Composable
private fun SettingSwitchCard(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：标题和摘要
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            // 右侧：开关
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

/**
 * 颜色选项按钮组件
 * 
 * 用于在默认背景颜色选择器中显示单个颜色选项。
 * UI 组成：
 * - 彩色圆形预览色块
 * - 颜色名称文字（如"黄色"、"蓝色"等）
 * 
 * 视觉反馈：
 * - 选中时边框变为深棕色，且背景有浅色高亮
 * - 未选中时边框为浅棕色
 * 
 * @param modifier 修饰符
 * @param option 颜色选项数据
 * @param selected 是否被选中
 * @param onClick 点击回调
 */
@Composable
private fun ColorOptionButton(
    modifier: Modifier,
    option: NoteColorOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            Color(0x1AB58B4B)  // 选中时：半透明金色背景
        } else {
            Color(0x0FFFFFFF)  // 未选中时：完全透明
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) Color(0xFF8D5F23) else Color(0x33B58B4B)  // 深棕/浅棕
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 圆形颜色预览块
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(option.previewColor)
            )
            // 颜色名称
            Text(
                text = stringResource(option.labelRes),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * 选择行组件（单选按钮式卡片）
 * 
 * 用于排序模式选择器中显示单个选项。
 * UI 组成：
 * - 圆形选中指示器（选中时实心，未选中时空心）
 * - 选项文字标签
 * 
 * 视觉反馈：
 * - 选中时边框和背景有高亮效果
 * - 点击整个卡片区域均可触发选中
 * 
 * @param label 选项文字
 * @param selected 是否被选中
 * @param onClick 点击回调
 */
@Composable
private fun SelectionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) Color(0x1AB58B4B) else Color(0x10FFFFFF),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) Color(0xFF8D5F23) else Color(0x33B58B4B)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 圆形选中指示器
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            Color(0xFF8D5F23)  // 选中：深棕色实心圆
                        } else {
                            Color(0x33B58B4B)  // 未选中：浅棕色空心（用半透明模拟）
                        }
                    )
            )
            Spacer(modifier = Modifier.size(10.dp))
            // 选项文字
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/**
 * 获取当前默认背景颜色的显示名称
 * 
 * 根据颜色 ID 查找对应的资源字符串并返回。
 * 
 * @param colorId 颜色 ID（ResourceParser 中的常量）
 * @return 颜色名称字符串（如"黄色"、"蓝色"等）
 */
@Composable
private fun colorLabel(colorId: Int): String {
    return stringResource(
        noteColorOptions().firstOrNull { it.colorId == colorId }?.labelRes 
            ?: R.string.color_yellow  // 默认返回"黄色"
    )
}

/**
 * 颜色选项数据类
 * 
 * @param colorId 颜色 ID（ResourceParser 中的常量）
 * @param previewColor 预览色块的颜色值
 * @param labelRes 颜色名称的资源 ID
 */
private data class NoteColorOption(
    val colorId: Int,
    val previewColor: Color,
    val labelRes: Int
)

/**
 * 排序选项数据类
 * 
 * @param sortMode 排序模式枚举值
 * @param labelRes 排序方式名称的资源 ID
 */
private data class SortOption(
    val sortMode: NotesSortMode,
    val labelRes: Int
)

/**
 * 预定义的颜色选项列表
 * 
 * 包含 5 种可选颜色：黄、蓝、白、绿、红
 * 
 * @return 颜色选项列表
 */
private fun noteColorOptions(): List<NoteColorOption> {
    return listOf(
        NoteColorOption(ResourceParser.YELLOW, Color(0xFFFFF3BF), R.string.color_yellow),   // 黄色
        NoteColorOption(ResourceParser.BLUE, Color(0xFFDCEBFF), R.string.color_blue),       // 蓝色
        NoteColorOption(ResourceParser.WHITE, Color(0xFFF7F4EC), R.string.color_white),     // 白色
        NoteColorOption(ResourceParser.GREEN, Color(0xFFE1F2D8), R.string.color_green),     // 绿色
        NoteColorOption(ResourceParser.RED, Color(0xFFFFDED7), R.string.color_red)          // 红色
    )
}

/**
 * 预定义的排序选项列表
 * 
 * 包含 3 种排序方式：
 * - MODIFIED_DESC: 最近修改优先
 * - MODIFIED_ASC: 最早修改优先
 * - TITLE_ASC: 按标题排序（A-Z）
 * 
 * @return 排序选项列表
 */
private fun sortOptions(): List<SortOption> {
    return listOf(
        SortOption(NotesSortMode.MODIFIED_DESC, R.string.preferences_sort_modified_desc),  // 最近修改优先
        SortOption(NotesSortMode.MODIFIED_ASC, R.string.preferences_sort_modified_asc),    // 最早修改优先
        SortOption(NotesSortMode.TITLE_ASC, R.string.preferences_sort_title_asc)           // 按标题排序
    )
}