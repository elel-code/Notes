package net.micode.notes.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import net.micode.notes.R
import net.micode.notes.data.Notes
import net.micode.notes.tool.ResourceParser
import java.text.DateFormat
import java.util.Date

/**
 * 文件夹对话框的 UI 状态数据类
 * 
 * @param create true 表示创建新文件夹，false 表示重命名现有文件夹
 * @param name 文件夹名称（输入框的值）
 * @param folder 正在操作的文件夹（重命名时需要，创建时为 null）
 */
data class FolderDialogUiState(
    val create: Boolean,
    val name: String,
    val folder: NotesListItemUi? = null
)

/**
 * 便签列表主屏幕
 * 
 * 这是便签应用的核心列表界面，负责展示所有便签和文件夹。
 * 
 * 主要功能：
 * 1. 展示当前文件夹下的所有便签和子文件夹
 * 2. 搜索框：按关键词搜索便签
 * 3. 顶部栏：显示标题、返回按钮、设置按钮
 * 4. 底部栏：新建便签、新建文件夹、批量删除
 * 5. 支持多选模式：长按便签进入选择模式
 * 6. 文件夹菜单：重命名、删除文件夹
 * 
 * @param state 列表 UI 状态
 * @param folderDialogState 文件夹对话框状态（null 表示不可见）
 * @param onBack 返回回调
 * @param onSearchTextChange 搜索文本变更回调
 * @param onItemClick 列表项点击回调
 * @param onItemLongClick 列表项长按回调
 * @param onCreateNote 新建便签回调
 * @param onCreateFolder 创建文件夹回调
 * @param onDeleteSelection 删除选中项回调
 * @param onClearSelection 清除选择回调
 * @param onRenameFolder 重命名文件夹回调
 * @param onDeleteFolder 删除文件夹回调
 * @param onOpenSettings 打开设置回调
 * @param onFolderDialogNameChange 文件夹对话框名称变更回调
 * @param onDismissFolderDialog 关闭文件夹对话框回调
 * @param onConfirmFolderDialog 确认文件夹对话框回调
 */
@Composable
fun NotesListScreen(
    state: NotesListUiState,
    folderDialogState: FolderDialogUiState?,
    onBack: () -> Unit,
    onSearchTextChange: (String) -> Unit,
    onItemClick: (NotesListItemUi) -> Unit,
    onItemLongClick: (NotesListItemUi) -> Unit,
    onCreateNote: () -> Unit,
    onCreateFolder: () -> Unit,
    onDeleteSelection: () -> Unit,
    onClearSelection: () -> Unit,
    onRenameFolder: (NotesListItemUi) -> Unit,
    onDeleteFolder: (NotesListItemUi) -> Unit,
    onOpenSettings: () -> Unit,
    onFolderDialogNameChange: (String) -> Unit,
    onDismissFolderDialog: () -> Unit,
    onConfirmFolderDialog: () -> Unit
) {
    val selectionActive = state.selectedIds.isNotEmpty()  // 是否处于选择模式
    
    // 根据当前状态确定屏幕标题
    val screenTitle = when {
        selectionActive -> pluralStringResource(
            R.plurals.menu_select_title,
            state.selectedIds.size,
            state.selectedIds.size
        )  // 选择模式：显示"选中了 X 项"
        state.isRoot -> stringResource(R.string.app_name)  // 根目录：显示"便签"
        state.isCallRecordFolder -> stringResource(R.string.call_record_folder_name)  // 通话记录文件夹
        else -> state.currentFolderTitle.ifBlank { stringResource(R.string.app_name) }  // 其他文件夹
    }

    // 处理系统返回键
    BackHandler(enabled = selectionActive || !state.isRoot) {
        onBack()
    }

    // 主容器
    Box(modifier = Modifier.fillMaxSize()) {
        // 背景层：使用列表背景图片
        ResourceDrawableBackground(
            resId = R.drawable.list_background,
            modifier = Modifier.fillMaxSize()
        )
        
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部栏
            NotesHeader(
                title = screenTitle,
                showBack = selectionActive || !state.isRoot,  // 选择模式或非根目录时显示返回
                showSettings = state.isRoot && !selectionActive,  // 根目录且非选择模式时显示设置
                onBack = onBack,
                onOpenSettings = onOpenSettings
            )
            
            // 搜索框
            SearchBar(
                value = state.searchText,
                onValueChange = onSearchTextChange
            )
            
            // 主要内容区域（列表或空状态）
            Box(modifier = Modifier.weight(1f)) {
                if (state.items.isEmpty() && !state.isLoading) {
                    // 空状态（无数据）
                    EmptyState(isCallRecordFolder = state.isCallRecordFolder)
                } else {
                    // 列表视图
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = state.items,
                            key = { item -> item.id }  // 使用 ID 作为唯一标识
                        ) { item ->
                            NoteListRow(
                                item = item,
                                isSelected = item.id in state.selectedIds,
                                selectionActive = selectionActive,
                                onClick = { onItemClick(item) },
                                onLongClick = { onItemLongClick(item) },
                                onRenameFolder = { onRenameFolder(item) },
                                onDeleteFolder = { onDeleteFolder(item) }
                            )
                        }
                        item {
                            Footer(
                                itemCount = state.items.size,
                                isCallRecordFolder = state.isCallRecordFolder,
                                showAddHint = !selectionActive && !state.isCallRecordFolder
                            )
                        }
                    }
                }
            }
            
            // 底部操作栏
            BottomBar(
                state = state,
                onCreateNote = onCreateNote,
                onCreateFolder = onCreateFolder,
                onDeleteSelection = onDeleteSelection,
                onClearSelection = onClearSelection
            )
        }

        // 文件夹对话框（创建/重命名）
        folderDialogState?.let { dialogState ->
            AlertDialog(
                onDismissRequest = onDismissFolderDialog,
                title = {
                    Text(
                        text = stringResource(
                            if (dialogState.create) {
                                R.string.menu_create_folder      // "新建文件夹"
                            } else {
                                R.string.menu_folder_change_name // "修改文件夹名称"
                            }
                        )
                    )
                },
                text = {
                    OutlinedTextField(
                        value = dialogState.name,
                        onValueChange = onFolderDialogNameChange,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(text = stringResource(R.string.hint_foler_name)) }  // "请输入名称"
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = onConfirmFolderDialog,
                        enabled = dialogState.name.isNotBlank()  // 名称为空时禁用确认按钮
                    ) {
                        Text(text = stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissFolderDialog) {
                        Text(text = stringResource(android.R.string.cancel))
                    }
                }
            )
        }
    }
}

/**
 * 顶部栏组件
 * 
 * @param title 标题文字
 * @param showBack 是否显示返回按钮
 * @param showSettings 是否显示设置按钮
 * @param onBack 返回回调
 * @param onOpenSettings 打开设置回调
 */
@Composable
private fun NotesHeader(
    title: String,
    showBack: Boolean,
    showSettings: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()  // 适配状态栏高度
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧区域：返回按钮或占位符
        if (showBack) {
            TextButton(onClick = onBack) {
                Text(text = stringResource(R.string.action_back))  // "返回"
            }
        } else {
            Spacer(modifier = Modifier.width(12.dp))
        }

        // 中间标题（可伸缩，最多1行）
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            color = Color(0xFF1F1A14)  // 深棕色
        )

        // 右侧区域：设置按钮或占位符
        if (showSettings) {
            TextButton(onClick = onOpenSettings) {
                Text(text = stringResource(R.string.menu_setting))  // "设置"
            }
        } else {
            Spacer(modifier = Modifier.width(12.dp))
        }
    }
}

/**
 * 搜索框组件
 * 
 * 使用 BasicTextField 实现，支持搜索输入
 * 键盘确认按钮为"搜索"（ImeAction.Search）
 * 
 * @param value 当前的搜索文本
 * @param onValueChange 文本变更回调
 */
@Composable
private fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .border(width = 1.dp, color = Color(0x33B58B4B), shape = shape),  // 浅金色边框
        shape = shape,
        color = Color(0x55FFF7E8)  // 半透米色背景
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),  // 搜索按键
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF2A261F)),
            cursorBrush = SolidColor(Color(0xFF8D5F23)),  // 棕色光标
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    // 空内容时显示提示文字
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_hint),  // "搜索便签"
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0x886E6253)
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

/**
 * 列表项组件
 * 
 * 支持：
 * - 单击：打开便签/文件夹
 * - 长按：进入选择模式（仅便签，文件夹不支持长按选择）
 * - 选中边框高亮
 * - 文件夹菜单（更多操作）
 * 
 * @param item 列表项数据
 * @param isSelected 是否被选中
 * @param selectionActive 是否处于选择模式
 * @param onClick 点击回调
 * @param onLongClick 长按回调
 * @param onRenameFolder 重命名文件夹回调
 * @param onDeleteFolder 删除文件夹回调
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteListRow(
    item: NotesListItemUi,
    isSelected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRenameFolder: () -> Unit,
    onDeleteFolder: () -> Unit
) {
    var menuExpanded by remember(item.id) { mutableStateOf(false) }  // 文件夹菜单状态

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    // 只有便签支持长按选择，文件夹不支持
                    if (!item.isFolder) {
                        onLongClick()
                    }
                }
            )
            .then(
                // 选中时添加边框高亮
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = Color(0xFF8D5F23),  // 棕色边框
                        shape = RoundedCornerShape(22.dp)
                    )
                } else {
                    Modifier
                }
            ),
        color = noteSurfaceColor(item),
        tonalElevation = if (isSelected) 6.dp else 1.dp,
        shadowElevation = if (isSelected) 8.dp else 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧图标/徽章
            RowBadge(
                resId = when {
                    isSelected -> R.drawable.selected          // 选中状态显示对勾
                    item.id == Notes.ID_CALL_RECORD_FOLDER.toLong() -> R.drawable.call_record  // 通话记录文件夹图标
                    else -> null
                },
                tintColor = when {
                    item.isFolder -> Color(0xFF9C7A3D)   // 文件夹：金色
                    else -> Color(0xFFCDB78C)            // 便签：浅金色
                }
            )
            Spacer(modifier = Modifier.width(14.dp))
            
            // 中间文字区域
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rowTitle(item),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = rowSubtitle(item),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF5B5344),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // 右侧区域：文件夹菜单或删除图标
            if (item.type == Notes.TYPE_FOLDER && !selectionActive) {
                // 文件夹：更多操作菜单
                Box {
                    TextButton(onClick = { menuExpanded = true }) {
                        Text(text = stringResource(R.string.action_more))  // "更多"
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.menu_folder_change_name)) },
                            onClick = {
                                menuExpanded = false
                                onRenameFolder()
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = {
                                Image(
                                    painter = painterResource(R.drawable.menu_delete),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            text = { Text(text = stringResource(R.string.menu_folder_delete)) },
                            onClick = {
                                menuExpanded = false
                                onDeleteFolder()
                            }
                        )
                    }
                }
            } else if (isSelected) {
                // 选择模式：显示删除图标
                Image(
                    painter = painterResource(R.drawable.menu_delete),
                    contentDescription = stringResource(R.string.menu_delete),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * 列表项徽章/图标组件
 * 
 * @param resId 图标资源 ID（null 时显示纯色圆点）
 * @param tintColor 纯色圆点的颜色
 */
@Composable
private fun RowBadge(
    @DrawableRes resId: Int?,
    tintColor: Color
) {
    if (resId != null) {
        Image(
            painter = painterResource(resId),
            contentDescription = null,
            modifier = Modifier.size(width = 20.dp, height = 24.dp)
        )
    } else {
        // 默认显示纯色圆点
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(7.dp))  // 圆形
                .background(tintColor)
        )
    }
}

/**
 * 列表底部信息组件
 * 
 * 显示：
 * - 项目数量统计（如"共 5 项内容"）
 * - 操作提示（如"轻触底部按钮即可新建便签"）
 * 
 * @param itemCount 项目数量
 * @param isCallRecordFolder 是否为通话记录文件夹
 * @param showAddHint 是否显示新建提示（否则显示搜索提示）
 */
@Composable
private fun Footer(
    itemCount: Int,
    isCallRecordFolder: Boolean,
    showAddHint: Boolean
) {
    // 标题：数量统计或空状态文字
    val title = if (isCallRecordFolder) {
        if (itemCount > 0) {
            pluralStringResource(R.plurals.footer_call_notes_count, itemCount, itemCount)
        } else {
            stringResource(R.string.footer_call_notes_empty)
        }
    } else {
        if (itemCount > 0) {
            pluralStringResource(R.plurals.footer_items_count, itemCount, itemCount)
        } else {
            stringResource(R.string.footer_items_empty)
        }
    }

    // 提示文字
    val hint = if (showAddHint) {
        stringResource(R.string.footer_hint_add_note)   // "轻触底部按钮即可新建便签"
    } else {
        stringResource(R.string.footer_hint_search)     // "使用上方搜索可更快找到内容"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF6A6256)
        )
    }
}

/**
 * 空状态组件
 * 
 * 当列表为空时显示居中提示文字
 * 
 * @param isCallRecordFolder 是否为通话记录文件夹（影响提示文字）
 */
@Composable
private fun EmptyState(isCallRecordFolder: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isCallRecordFolder) {
                stringResource(R.string.footer_call_notes_empty)
            } else {
                stringResource(R.string.footer_items_empty)
            },
            style = MaterialTheme.typography.headlineSmall,
            color = Color(0xFF746B5A)
        )
    }
}

/**
 * 底部操作栏组件
 * 
 * 根据状态显示不同的操作按钮：
 * - 选择模式：显示"取消"和"删除"按钮
 * - 正常模式：显示"新建便签"和"新建文件夹"按钮
 * 
 * @param state 列表 UI 状态
 * @param onCreateNote 新建便签回调
 * @param onCreateFolder 创建文件夹回调
 * @param onDeleteSelection 删除选中项回调
 * @param onClearSelection 清除选择回调
 */
@Composable
private fun BottomBar(
    state: NotesListUiState,
    onCreateNote: () -> Unit,
    onCreateFolder: () -> Unit,
    onDeleteSelection: () -> Unit,
    onClearSelection: () -> Unit
) {
    val selectionActive = state.selectedIds.isNotEmpty()
    val showPrimaryAction = !state.isCallRecordFolder      // 非通话文件夹显示新建按钮
    val showSecondaryActions = state.isRoot               // 根目录显示创建文件夹按钮

    // 没有任何按钮需要显示时，不渲染底部栏
    if (!selectionActive && !showPrimaryAction && !showSecondaryActions) {
        return
    }

    // 选择模式：显示取消和删除按钮
    if (selectionActive) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()  // 适配导航栏高度
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                modifier = Modifier.weight(1f),
                onClick = onClearSelection
            ) {
                Text(text = stringResource(android.R.string.cancel))  // "取消"
            }
            FilledTonalButton(
                modifier = Modifier.weight(1f),
                onClick = onDeleteSelection
            ) {
                Text(text = stringResource(R.string.menu_delete))  // "删除"
            }
        }
        return
    }

    // 正常模式：显示主要操作按钮
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showPrimaryAction) {
            NewNoteButton(
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.notelist_menu_new),  // "新建便签"
                onClick = onCreateNote
            )
        }

        if (showSecondaryActions) {
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onCreateFolder
            ) {
                Text(text = stringResource(R.string.menu_create_folder))  // "新建文件夹"
            }
        }
    }
}

/**
 * 新建便签按钮组件
 * 
 * 使用背景图片实现按下/正常状态的视觉效果
 * 
 * @param modifier 修饰符
 * @param label 按钮标签（无障碍文本）
 * @param onClick 点击回调
 */
@Composable
private fun NewNoteButton(
    modifier: Modifier = Modifier,
    label: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    
    // 根据按压状态选择背景图片
    val backgroundResId = if (pressed) {
        R.drawable.new_note_pressed   // 按下状态
    } else {
        R.drawable.new_note_normal    // 正常状态
    }

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,  // 无涟漪效果（使用图片状态代替）
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(backgroundResId),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * 获取列表项标题
 * 
 * @param item 列表项
 * @return 标题文字
 */
@Composable
private fun rowTitle(item: NotesListItemUi): String {
    return when {
        item.id == Notes.ID_CALL_RECORD_FOLDER.toLong() -> stringResource(R.string.call_record_folder_name)  // "通话便签"
        item.title.isNotBlank() -> item.title
        else -> stringResource(R.string.notelist_string_info)  // "…"
    }
}

/**
 * 获取列表项副标题
 * 
 * @param item 列表项
 * @return 副标题文字（文件夹显示文件数量，便签显示修改时间）
 */
@Composable
private fun rowSubtitle(item: NotesListItemUi): String {
    return if (item.isFolder) {
        // 文件夹：显示包含的便签数量
        stringResource(R.string.format_folder_files_count, item.notesCount)
    } else {
        // 便签：显示格式化的修改时间
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(item.modifiedDate))
    }
}

/**
 * 获取列表项的背景颜色
 * 
 * @param item 列表项
 * @return 对应的 Color 对象
 */
private fun noteSurfaceColor(item: NotesListItemUi): Color {
    return if (item.isFolder) {
        Color(0xFFE8D9B5)  // 文件夹：米棕色
    } else {
        when (item.bgColorId) {
            ResourceParser.YELLOW -> Color(0xFFFFF3BF)  // 黄色
            ResourceParser.BLUE -> Color(0xFFDCEBFF)    // 蓝色
            ResourceParser.WHITE -> Color(0xFFF7F4EC)   // 白色
            ResourceParser.GREEN -> Color(0xFFE1F2D8)   // 绿色
            ResourceParser.RED -> Color(0xFFFFDED7)     // 红色
            else -> Color(0xFFFFF3BF)                   // 默认黄色
        }
    }
}