package net.micode.notes.ui

import android.widget.ImageView
import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import net.micode.notes.R
import net.micode.notes.tool.ResourceParser
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import android.text.format.DateFormat as AndroidDateFormat

/**
 * 便签编辑界面的 UI 状态数据类
 * 
 * 使用不可变数据类存储界面的所有状态，便于 Compose 重组和状态管理
 * 
 * @property content 便签文本内容
 * @property bgColorId 背景颜色 ID（对应 ResourceParser 中的常量）
 * @property bgResId 背景图片资源 ID（0 表示不使用图片背景）
 * @property titleBgResId 标题栏背景图片资源 ID
 * @property fontSizeId 字体大小 ID
 * @property modifiedDate 最后修改时间（毫秒时间戳）
 * @property alertDate 提醒时间（0 表示未设置提醒）
 * @property isChecklistMode 是否为待办清单模式
 */
data class NoteEditUiState(
    val content: String = "",
    val bgColorId: Int = ResourceParser.BG_DEFAULT_COLOR,
    val bgResId: Int = 0,
    val titleBgResId: Int = 0,
    val fontSizeId: Int = ResourceParser.BG_DEFAULT_FONT_SIZE,
    val modifiedDate: Long = 0L,
    val alertDate: Long = 0L,
    val isChecklistMode: Boolean = false
)

/**
 * 提醒对话框的 UI 状态数据类
 * 
 * @property initialDate 初始显示的日期时间（毫秒时间戳）
 */
data class ReminderDialogUiState(
    val initialDate: Long = System.currentTimeMillis()
)

// ==================== 主题颜色常量 ====================
private val HeaderActionColor = Color(0xFF7A6A43)      // 顶部栏操作按钮文字颜色
private val HeaderTitleColor = Color(0xFF231C12)      // 顶部栏标题文字颜色
private val HeaderSubtitleColor = Color(0xAA3D3427)   // 顶部栏副标题文字颜色（半透明）
private val EditorControlSurfaceColor = Color(0xF0E5D6A8)  // 底部控制面板背景色
private val EditorTextColor = Color(0xFF2A261F)       // 编辑器文字颜色
private val EditorHintColor = Color(0x88544E43)       // 编辑器占位提示文字颜色（半透明）

/**
 * 便签编辑主屏幕
 * 
 * 这是便签编辑界面的核心 Composable 组件，负责：
 * 1. 显示便签编辑区域（支持普通文本和清单模式）
 * 2. 顶部操作栏（返回、更多菜单、标题、修改时间）
 * 3. 提醒信息显示
 * 4. 底部控制面板（背景颜色、字体大小选择）
 * 5. 删除确认对话框
 * 6. 提醒设置对话框
 * 
 * @param state 当前 UI 状态
 * @param deleteDialogVisible 删除确认对话框是否可见
 * @param reminderDialogState 提醒对话框状态（null 表示不可见）
 * @param onBack 返回按钮点击回调
 * @param onContentChange 内容变更回调
 * @param onNewNote 新建便签回调
 * @param onDelete 删除便签回调（触发前）
 * @param onDismissDeleteDialog 关闭删除对话框回调
 * @param onConfirmDelete 确认删除回调
 * @param onToggleChecklist 切换清单模式回调
 * @param onShare 分享便签回调
 * @param onExportText 导出为文本回调
 * @param onSaveLongImage 保存为长图回调
 * @param onSendToDesktop 发送到桌面回调
 * @param onSetReminder 设置提醒回调
 * @param onDismissReminderDialog 关闭提醒对话框回调
 * @param onConfirmReminder 确认提醒回调（返回选中的时间戳）
 * @param onClearReminder 清除提醒回调
 * @param onSelectBackground 选择背景颜色回调
 * @param onSelectFontSize 选择字体大小回调
 */
@Composable
fun NoteEditScreen(
    state: NoteEditUiState,
    deleteDialogVisible: Boolean,
    reminderDialogState: ReminderDialogUiState?,
    onBack: () -> Unit,
    onContentChange: (String) -> Unit,
    onNewNote: () -> Unit,
    onDelete: () -> Unit,
    onDismissDeleteDialog: () -> Unit,
    onConfirmDelete: () -> Unit,
    onToggleChecklist: () -> Unit,
    onShare: () -> Unit,
    onExportText: () -> Unit,
    onSaveLongImage: () -> Unit,
    onSendToDesktop: () -> Unit,
    onSetReminder: () -> Unit,
    onDismissReminderDialog: () -> Unit,
    onConfirmReminder: (Long) -> Unit,
    onClearReminder: () -> Unit,
    onSelectBackground: (Int) -> Unit,
    onSelectFontSize: (Int) -> Unit
) {
    // 根布局：填充整个屏幕
    Box(modifier = Modifier.fillMaxSize()) {
        // 背景层：优先使用图片背景，否则使用默认黄色背景
        if (state.bgResId != 0) {
            // 使用图片背景（如旧版的主题背景）
            ResourceDrawableBackground(
                resId = state.bgResId,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // 默认背景色（米黄色）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFFF3BF))
            )
        }

        // 主内容列
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部操作栏
            NoteEditTopBar(
                state = state,
                onBack = onBack,
                onNewNote = onNewNote,
                onDelete = onDelete,
                onToggleChecklist = onToggleChecklist,
                onShare = onShare,
                onExportText = onExportText,
                onSaveLongImage = onSaveLongImage,
                onSendToDesktop = onSendToDesktop,
                onSetReminder = onSetReminder,
                onClearReminder = onClearReminder
            )

            // 提醒信息条（仅当设置了提醒时显示）
            if (state.alertDate > 0L) {
                Surface(
                    color = Color(0xCCFFF7E3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 提醒图标
                        LegacyAssetIcon(
                            resId = R.drawable.title_alert,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                        // 相对时间文本（如"5分钟后"、"明天"等）
                        Text(
                            text = DateUtils.getRelativeTimeSpanString(
                                state.alertDate,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS
                            ).toString(),
                            modifier = Modifier.weight(1f),
                            color = HeaderSubtitleColor
                        )
                        // 删除提醒按钮
                        RemoveReminderAction(onClick = onClearReminder)
                    }
                }
            }

            // 便签编辑区域
            Column(
                modifier = Modifier
                    .weight(1f)                      // 占据剩余空间
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            ) {
                // 顶部装饰线（模仿旧版纸张效果）
                LegacyAssetIcon(
                    resId = R.drawable.bg_color_btn_mask,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                )
                
                // 文本输入框容器
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val scrollState = rememberScrollState()  // 滚动状态
                    BasicTextField(
                        value = state.content,
                        onValueChange = onContentChange,
                        textStyle = TextStyle(
                            color = EditorTextColor,
                            fontSize = fontSizeFor(state.fontSizeId),
                            lineHeight = (fontSizeFor(state.fontSizeId).value * 1.5f).sp
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                            .verticalScroll(scrollState),
                        decorationBox = { innerTextField ->
                            // 空内容时显示占位提示文本
                            if (state.content.isEmpty()) {
                                Text(
                                    text = if (state.isChecklistMode) {
                                        stringResource(R.string.checklist_placeholder)   // "每行一项…"
                                    } else {
                                        stringResource(R.string.note_editor_placeholder) // "写点什么…"
                                    },
                                    color = EditorHintColor,
                                    fontSize = fontSizeFor(state.fontSizeId)
                                )
                            }
                            innerTextField()  // 实际输入框
                        }
                    )
                }
                
                // 底部装饰线
                LegacyAssetIcon(
                    resId = R.drawable.bg_color_btn_mask,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                )
            }

            // 底部控制面板（背景颜色 + 字体大小）
            EditorControls(
                state = state,
                onSelectBackground = onSelectBackground,
                onSelectFontSize = onSelectFontSize
            )
        }

        // 删除确认对话框
        if (deleteDialogVisible) {
            AlertDialog(
                onDismissRequest = onDismissDeleteDialog,
                title = { Text(text = stringResource(R.string.alert_title_delete)) },
                text = { Text(text = stringResource(R.string.alert_message_delete_note)) },
                confirmButton = {
                    TextButton(onClick = onConfirmDelete) {
                        Text(text = stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissDeleteDialog) {
                        Text(text = stringResource(android.R.string.cancel))
                    }
                }
            )
        }

        // 提醒设置对话框
        reminderDialogState?.let { dialogState ->
            ReminderDialog(
                state = dialogState,
                onDismiss = onDismissReminderDialog,
                onConfirm = onConfirmReminder
            )
        }
    }
}

/**
 * 顶部操作栏
 * 
 * 包含：
 * - 返回按钮
 * - 应用标题和最后修改时间
 * - 更多菜单（新建、删除、切换清单模式、分享、导出、生成图片、发送到桌面、提醒设置）
 * 
 * @param state 当前 UI 状态
 * @param onBack 返回回调
 * @param onNewNote 新建便签回调
 * @param onDelete 删除回调
 * @param onToggleChecklist 切换清单模式回调
 * @param onShare 分享回调
 * @param onExportText 导出文本回调
 * @param onSaveLongImage 保存长图回调
 * @param onSendToDesktop 发送到桌面回调
 * @param onSetReminder 设置提醒回调
 * @param onClearReminder 清除提醒回调
 */
@Composable
private fun NoteEditTopBar(
    state: NoteEditUiState,
    onBack: () -> Unit,
    onNewNote: () -> Unit,
    onDelete: () -> Unit,
    onToggleChecklist: () -> Unit,
    onShare: () -> Unit,
    onExportText: () -> Unit,
    onSaveLongImage: () -> Unit,
    onSendToDesktop: () -> Unit,
    onSetReminder: () -> Unit,
    onClearReminder: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }  // 更多菜单是否展开
    Box(modifier = Modifier.fillMaxWidth()) {
        // 背景层
        if (state.titleBgResId != 0) {
            ResourceDrawableBackground(
                resId = state.titleBgResId,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xDDECDCB6))  // 默认半透明米色
            )
        }

        // 顶部栏内容
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()                 // 适配状态栏高度
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 返回按钮
            HeaderActionText(
                text = stringResource(R.string.action_back),
                onClick = onBack
            )
            
            // 标题区域
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = HeaderTitleColor
                )
                Text(
                    text = DateFormat.getDateTimeInstance(
                        DateFormat.SHORT,
                        DateFormat.SHORT
                    ).format(Date(state.modifiedDate)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HeaderSubtitleColor
                )
            }
            
            // 更多菜单按钮
            Box {
                HeaderActionText(
                    text = stringResource(R.string.action_more),
                    onClick = { expanded = true }
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    // 新建便签
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.notelist_menu_new)) },
                        onClick = {
                            expanded = false
                            onNewNote()
                        }
                    )
                    // 删除
                    DropdownMenuItem(
                        leadingIcon = {
                            LegacyAssetIcon(
                                resId = R.drawable.menu_delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        text = { Text(text = stringResource(R.string.menu_delete)) },
                        onClick = {
                            expanded = false
                            onDelete()
                        }
                    )
                    // 切换清单模式（文字会动态变化）
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(
                                    if (state.isChecklistMode) {
                                        R.string.menu_normal_mode  // "退出清单模式"
                                    } else {
                                        R.string.menu_list_mode    // "进入清单模式"
                                    }
                                )
                            )
                        },
                        onClick = {
                            expanded = false
                            onToggleChecklist()
                        }
                    )
                    // 分享
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.menu_share)) },
                        onClick = {
                            expanded = false
                            onShare()
                        }
                    )
                    // 导出为 TXT
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.menu_export_as_txt)) },
                        onClick = {
                            expanded = false
                            onExportText()
                        }
                    )
                    // 保存为图片
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.menu_generate_long_image)) },
                        onClick = {
                            expanded = false
                            onSaveLongImage()
                        }
                    )
                    // 发送到桌面（快捷方式）
                    DropdownMenuItem(
                        leadingIcon = {
                            LegacyAssetIcon(
                                resId = R.drawable.menu_move,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        text = { Text(text = stringResource(R.string.menu_send_to_desktop)) },
                        onClick = {
                            expanded = false
                            onSendToDesktop()
                        }
                    )
                    // 提醒设置（文字会动态变化）
                    DropdownMenuItem(
                        leadingIcon = {
                            LegacyAssetIcon(
                                resId = R.drawable.clock,
                                contentDescription = null,
                                modifier = Modifier.size(width = 18.dp, height = 22.dp)
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(
                                    if (state.alertDate > 0L) {
                                        R.string.menu_remove_remind  // "删除提醒"
                                    } else {
                                        R.string.menu_alert          // "提醒我"
                                    }
                                )
                            )
                        },
                        onClick = {
                            expanded = false
                            if (state.alertDate > 0L) {
                                onClearReminder()
                            } else {
                                onSetReminder()
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * 顶部栏操作按钮（文字按钮）
 * 
 * 圆角矩形背景，点击时触发回调
 * 
 * @param text 按钮文字
 * @param onClick 点击回调
 */
@Composable
private fun HeaderActionText(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = HeaderActionColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 删除提醒操作按钮
 * 
 * 显示在提醒信息条右侧，点击后清除提醒
 * 
 * @param onClick 点击回调
 */
@Composable
private fun RemoveReminderAction(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegacyAssetIcon(
            resId = R.drawable.delete,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = stringResource(R.string.menu_remove_remind),
            color = HeaderActionColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 提醒设置对话框
 * 
 * 使用原生 Android 的 DatePicker 和 TimePicker 控件
 * 用户选择日期和时间后，回调返回完整的时间戳
 * 
 * @param state 对话框状态（包含初始日期）
 * @param onDismiss 关闭对话框回调
 * @param onConfirm 确认回调（返回选中的毫秒时间戳）
 */
@Composable
private fun ReminderDialog(
    state: ReminderDialogUiState,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val context = LocalContext.current
    // 根据初始日期初始化 Calendar
    val initialCalendar = remember(state.initialDate) {
        Calendar.getInstance().apply {
            timeInMillis = state.initialDate
        }
    }
    // 年
    var year by remember(state.initialDate) {
        mutableIntStateOf(initialCalendar.get(Calendar.YEAR))
    }
    // 月（Calendar 中 0 表示一月）
    var month by remember(state.initialDate) {
        mutableIntStateOf(initialCalendar.get(Calendar.MONTH))
    }
    // 日
    var dayOfMonth by remember(state.initialDate) {
        mutableIntStateOf(initialCalendar.get(Calendar.DAY_OF_MONTH))
    }
    // 小时（24小时制）
    var selectedHourOfDay by remember(state.initialDate) {
        mutableIntStateOf(initialCalendar.get(Calendar.HOUR_OF_DAY))
    }
    // 分钟
    var selectedMinute by remember(state.initialDate) {
        mutableIntStateOf(initialCalendar.get(Calendar.MINUTE))
    }
    // 是否为24小时制（根据系统设置）
    val is24Hour = remember(context) { AndroidDateFormat.is24HourFormat(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.menu_alert)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 日期选择器
                AndroidView(
                    factory = { viewContext ->
                        android.widget.DatePicker(viewContext).apply {
                            init(year, month, dayOfMonth) { _, selectedYear, selectedMonth, selectedDay ->
                                year = selectedYear
                                month = selectedMonth
                                dayOfMonth = selectedDay
                            }
                        }
                    },
                    update = { datePicker ->
                        datePicker.updateDate(year, month, dayOfMonth)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                // 时间选择器
                AndroidView(
                    factory = { viewContext ->
                        android.widget.TimePicker(viewContext).apply {
                            setIs24HourView(is24Hour)
                            hour = selectedHourOfDay
                            minute = selectedMinute
                            setOnTimeChangedListener { _, newHour, newMinute ->
                                selectedHourOfDay = newHour
                                selectedMinute = newMinute
                            }
                        }
                    },
                    update = { timePicker ->
                        timePicker.setIs24HourView(is24Hour)
                        if (timePicker.hour != selectedHourOfDay) {
                            timePicker.hour = selectedHourOfDay
                        }
                        if (timePicker.minute != selectedMinute) {
                            timePicker.minute = selectedMinute
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // 构建选中的时间戳
                    val calendar = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        set(Calendar.HOUR_OF_DAY, selectedHourOfDay)
                        set(Calendar.MINUTE, selectedMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onConfirm(calendar.timeInMillis)
                }
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    )
}

/**
 * 底部控制面板
 * 
 * 包含：
 * - 背景颜色选择器（5种颜色：黄、蓝、白、绿、红）
 * - 字体大小选择器（4档：小、正常、大、超大）
 * 
 * @param state 当前 UI 状态
 * @param onSelectBackground 选择背景颜色回调
 * @param onSelectFontSize 选择字体大小回调
 */
@Composable
private fun EditorControls(
    state: NoteEditUiState,
    onSelectBackground: (Int) -> Unit,
    onSelectFontSize: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = EditorControlSurfaceColor,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)  // 顶部圆角
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()  // 适配导航栏高度
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 背景颜色选择行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 调色板图标
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LegacyAssetIcon(
                        resId = R.drawable.bg_btn_set_color,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // 颜色选项容器（带背景图片）
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(62.dp)
                ) {
                    LegacyAssetIcon(
                        resId = R.drawable.note_edit_color_selector_panel,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        fillBounds = true
                    )
                    // 5个颜色选项
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            ResourceParser.YELLOW,
                            ResourceParser.BLUE,
                            ResourceParser.WHITE,
                            ResourceParser.GREEN,
                            ResourceParser.RED
                        ).forEach { bgId ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colorForBackground(bgId))
                                    .border(
                                        width = if (state.bgColorId == bgId) 2.dp else 1.dp,
                                        color = if (state.bgColorId == bgId) {
                                            Color(0xFF6A4A1E)  // 深棕色边框（选中）
                                        } else {
                                            Color(0x66443A2D)  // 浅棕色边框（未选中）
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelectBackground(bgId) }
                            ) {
                                // 选中标记（对勾图标）
                                if (state.bgColorId == bgId) {
                                    LegacyAssetIcon(
                                        resId = R.drawable.selected,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(end = 2.dp)
                                            .size(width = 16.dp, height = 21.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 字体大小选择行
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
            ) {
                LegacyAssetIcon(
                    resId = R.drawable.font_size_selector_bg,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    fillBounds = true
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    fontOptions().forEach { option ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectFontSize(option.fontId) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(top = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                // 字体大小图标（A 带大小标识）
                                LegacyAssetIcon(
                                    resId = option.drawableRes,
                                    contentDescription = stringResource(option.labelRes),
                                    modifier = Modifier.size(width = 24.dp, height = 28.dp)
                                )
                                // 文字标签
                                Text(
                                    text = stringResource(option.labelRes),
                                    fontSize = 10.sp,
                                    lineHeight = 10.sp,
                                    textAlign = TextAlign.Center,
                                    color = Color(0xFF5A5348)
                                )
                            }
                            // 选中标记
                            if (state.fontSizeId == option.fontId) {
                                LegacyAssetIcon(
                                    resId = R.drawable.selected,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 2.dp)
                                        .size(width = 16.dp, height = 21.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 字体选项列表
 * 
 * @return 包含四档字体选项的列表
 */
@Composable
private fun fontOptions(): List<FontOption> {
    return listOf(
        FontOption(ResourceParser.TEXT_SMALL, R.drawable.font_small, R.string.menu_font_small),
        FontOption(ResourceParser.TEXT_MEDIUM, R.drawable.font_normal, R.string.menu_font_normal),
        FontOption(ResourceParser.TEXT_LARGE, R.drawable.font_large, R.string.menu_font_large),
        FontOption(ResourceParser.TEXT_SUPER, R.drawable.font_super, R.string.menu_font_super)
    )
}

/**
 * 字体选项数据类
 * 
 * @param fontId 字体大小 ID
 * @param drawableRes 图标资源 ID
 * @param labelRes 文字标签资源 ID
 */
private data class FontOption(
    val fontId: Int,
    @param:DrawableRes val drawableRes: Int,
    @param:StringRes val labelRes: Int
)

/**
 * 传统资源图标（使用 AndroidView 加载旧版资源）
 * 
 * 为了兼容旧版 UI 资源，使用 ImageView 来显示传统的 drawable 资源
 * 这些资源可能是 .9.png 或普通图片
 * 
 * @param resId 图片资源 ID
 * @param contentDescription 内容描述（无障碍）
 * @param modifier Modifier
 * @param fillBounds 是否填充边界（true 使用 FIT_XY，false 使用 FIT_CENTER）
 */
@Composable
private fun LegacyAssetIcon(
    @DrawableRes resId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fillBounds: Boolean = false
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply {
                scaleType = if (fillBounds) {
                    ImageView.ScaleType.FIT_XY
                } else {
                    ImageView.ScaleType.FIT_CENTER
                }
                adjustViewBounds = !fillBounds
                setImageResource(resId)
                this.contentDescription = contentDescription
            }
        },
        update = { view ->
            view.scaleType = if (fillBounds) {
                ImageView.ScaleType.FIT_XY
            } else {
                ImageView.ScaleType.FIT_CENTER
            }
            view.adjustViewBounds = !fillBounds
            view.setImageResource(resId)
            view.contentDescription = contentDescription
        }
    )
}

/**
 * 根据字体大小 ID 获取对应的文字大小（sp）
 * 
 * @param fontSizeId 字体大小 ID
 * @return 对应的字体大小（sp 单位）
 */
private fun fontSizeFor(fontSizeId: Int) = when (fontSizeId) {
    ResourceParser.TEXT_SMALL -> 15.sp
    ResourceParser.TEXT_MEDIUM -> 18.sp
    ResourceParser.TEXT_LARGE -> 22.sp
    ResourceParser.TEXT_SUPER -> 26.sp
    else -> 18.sp  // 默认正常字体
}

/**
 * 根据背景颜色 ID 获取对应的颜色值
 * 
 * @param bgColorId 背景颜色 ID（ResourceParser 中的常量）
 * @return 对应的 Color 对象
 */
private fun colorForBackground(bgColorId: Int) = when (bgColorId) {
    ResourceParser.YELLOW -> Color(0xFFEECF68)  // 黄色
    ResourceParser.BLUE -> Color(0xFF8DB6E9)    // 蓝色
    ResourceParser.WHITE -> Color(0xFFF5F2E8)   // 白色
    ResourceParser.GREEN -> Color(0xFFA1C88C)   // 绿色
    ResourceParser.RED -> Color(0xFFE59882)     // 红色
    else -> Color(0xFFEECF68)                   // 默认黄色
}