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

data class FolderDialogUiState(
    val create: Boolean,
    val name: String,
    val folder: NotesListItemUi? = null
)

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
    val selectionActive = state.selectedIds.isNotEmpty()
    val screenTitle = when {
        selectionActive -> pluralStringResource(
            R.plurals.menu_select_title,
            state.selectedIds.size,
            state.selectedIds.size
        )
        state.isRoot -> stringResource(R.string.app_name)
        state.isCallRecordFolder -> stringResource(R.string.call_record_folder_name)
        else -> state.currentFolderTitle.ifBlank { stringResource(R.string.app_name) }
    }

    BackHandler(enabled = selectionActive || !state.isRoot) {
        onBack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ResourceDrawableBackground(
            resId = R.drawable.list_background,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            NotesHeader(
                title = screenTitle,
                showBack = selectionActive || !state.isRoot,
                showSettings = state.isRoot && !selectionActive,
                onBack = onBack,
                onOpenSettings = onOpenSettings
            )
            SearchBar(
                value = state.searchText,
                onValueChange = onSearchTextChange
            )
            Box(modifier = Modifier.weight(1f)) {
                if (state.items.isEmpty() && !state.isLoading) {
                    EmptyState(isCallRecordFolder = state.isCallRecordFolder)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = state.items,
                            key = { item -> item.id }
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
            BottomBar(
                state = state,
                onCreateNote = onCreateNote,
                onCreateFolder = onCreateFolder,
                onDeleteSelection = onDeleteSelection,
                onClearSelection = onClearSelection
            )
        }

        folderDialogState?.let { dialogState ->
            AlertDialog(
                onDismissRequest = onDismissFolderDialog,
                title = {
                    Text(
                        text = stringResource(
                            if (dialogState.create) {
                                R.string.menu_create_folder
                            } else {
                                R.string.menu_folder_change_name
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
                        placeholder = { Text(text = stringResource(R.string.hint_foler_name)) }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = onConfirmFolderDialog,
                        enabled = dialogState.name.isNotBlank()
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
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            TextButton(onClick = onBack) {
                Text(text = stringResource(R.string.action_back))
            }
        } else {
            Spacer(modifier = Modifier.width(12.dp))
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            color = Color(0xFF1F1A14)
        )

        if (showSettings) {
            TextButton(onClick = onOpenSettings) {
                Text(text = stringResource(R.string.menu_setting))
            }
        } else {
            Spacer(modifier = Modifier.width(12.dp))
        }
    }
}

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
            .border(width = 1.dp, color = Color(0x33B58B4B), shape = shape),
        shape = shape,
        color = Color(0x55FFF7E8)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF2A261F)),
            cursorBrush = SolidColor(Color(0xFF8D5F23)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_hint),
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
    var menuExpanded by remember(item.id) { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (!item.isFolder) {
                        onLongClick()
                    }
                }
            )
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = Color(0xFF8D5F23),
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
            RowBadge(
                resId = when {
                    isSelected -> R.drawable.selected
                    item.id == Notes.ID_CALL_RECORD_FOLDER.toLong() -> R.drawable.call_record
                    else -> null
                },
                tintColor = when {
                    item.isFolder -> Color(0xFF9C7A3D)
                    else -> Color(0xFFCDB78C)
                }
            )
            Spacer(modifier = Modifier.width(14.dp))
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
            if (item.type == Notes.TYPE_FOLDER && !selectionActive) {
                Box {
                    TextButton(onClick = { menuExpanded = true }) {
                        Text(text = stringResource(R.string.action_more))
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
                Image(
                    painter = painterResource(R.drawable.menu_delete),
                    contentDescription = stringResource(R.string.menu_delete),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

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
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(tintColor)
        )
    }
}

@Composable
private fun Footer(
    itemCount: Int,
    isCallRecordFolder: Boolean,
    showAddHint: Boolean
) {
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

    val hint = if (showAddHint) {
        stringResource(R.string.footer_hint_add_note)
    } else {
        stringResource(R.string.footer_hint_search)
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

@Composable
private fun BottomBar(
    state: NotesListUiState,
    onCreateNote: () -> Unit,
    onCreateFolder: () -> Unit,
    onDeleteSelection: () -> Unit,
    onClearSelection: () -> Unit
) {
    val selectionActive = state.selectedIds.isNotEmpty()
    val showPrimaryAction = !state.isCallRecordFolder
    val showSecondaryActions = state.isRoot

    if (!selectionActive && !showPrimaryAction && !showSecondaryActions) {
        return
    }

    if (selectionActive) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                modifier = Modifier.weight(1f),
                onClick = onClearSelection
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }
            FilledTonalButton(
                modifier = Modifier.weight(1f),
                onClick = onDeleteSelection
            ) {
                Text(text = stringResource(R.string.menu_delete))
            }
        }
        return
    }

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
                label = stringResource(R.string.notelist_menu_new),
                onClick = onCreateNote
            )
        }

        if (showSecondaryActions) {
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onCreateFolder
            ) {
                Text(text = stringResource(R.string.menu_create_folder))
            }
        }
    }
}

@Composable
private fun NewNoteButton(
    modifier: Modifier = Modifier,
    label: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val backgroundResId = if (pressed) {
        R.drawable.new_note_pressed
    } else {
        R.drawable.new_note_normal
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
                indication = null,
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

@Composable
private fun rowTitle(item: NotesListItemUi): String {
    return when {
        item.id == Notes.ID_CALL_RECORD_FOLDER.toLong() -> stringResource(R.string.call_record_folder_name)
        item.title.isNotBlank() -> item.title
        else -> stringResource(R.string.notelist_string_info)
    }
}

@Composable
private fun rowSubtitle(item: NotesListItemUi): String {
    return if (item.isFolder) {
        stringResource(R.string.format_folder_files_count, item.notesCount)
    } else {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(item.modifiedDate))
    }
}

private fun noteSurfaceColor(item: NotesListItemUi): Color {
    return if (item.isFolder) {
        Color(0xFFE8D9B5)
    } else {
        when (item.bgColorId) {
            ResourceParser.YELLOW -> Color(0xFFFFF3BF)
            ResourceParser.BLUE -> Color(0xFFDCEBFF)
            ResourceParser.WHITE -> Color(0xFFF7F4EC)
            ResourceParser.GREEN -> Color(0xFFE1F2D8)
            ResourceParser.RED -> Color(0xFFFFDED7)
            else -> Color(0xFFFFF3BF)
        }
    }
}
