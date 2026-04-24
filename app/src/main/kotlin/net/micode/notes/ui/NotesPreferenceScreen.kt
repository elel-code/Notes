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

data class NotesSettingsUiState(
    val randomBackgroundEnabled: Boolean = false,
    val defaultBackgroundColor: Int = ResourceParser.BG_DEFAULT_COLOR,
    val listSortMode: NotesSortMode = NotesSortMode.MODIFIED_DESC,
    val deleteConfirmationEnabled: Boolean = true,
    val rememberLastFolderEnabled: Boolean = true
)

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
    Box(modifier = Modifier.fillMaxSize()) {
        ResourceDrawableBackground(
            resId = R.drawable.list_background,
            modifier = Modifier.fillMaxSize()
        )
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text(text = stringResource(R.string.action_back))
                }
                Text(
                    text = stringResource(R.string.preferences_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SettingSwitchCard(
                    title = stringResource(R.string.preferences_bg_random_appear_title),
                    summary = if (state.randomBackgroundEnabled) {
                        stringResource(R.string.state_enabled)
                    } else {
                        stringResource(R.string.state_disabled)
                    },
                    checked = state.randomBackgroundEnabled,
                    onCheckedChange = onToggleRandomBackground
                )

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
                        Text(
                            text = stringResource(R.string.preferences_default_color_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = colorLabel(state.defaultBackgroundColor),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            noteColorOptions().forEach { option ->
                                ColorOptionButton(
                                    modifier = Modifier.weight(1f),
                                    option = option,
                                    selected = option.colorId == state.defaultBackgroundColor,
                                    onClick = { onSelectDefaultBackgroundColor(option.colorId) }
                                )
                            }
                        }
                    }
                }

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
                            text = stringResource(R.string.preferences_sort_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        sortOptions().forEach { option ->
                            SelectionRow(
                                label = stringResource(option.labelRes),
                                selected = state.listSortMode == option.sortMode,
                                onClick = { onSelectListSortMode(option.sortMode) }
                            )
                        }
                    }
                }

                SettingSwitchCard(
                    title = stringResource(R.string.preferences_delete_confirmation_title),
                    summary = if (state.deleteConfirmationEnabled) {
                        stringResource(R.string.state_enabled)
                    } else {
                        stringResource(R.string.state_disabled)
                    },
                    checked = state.deleteConfirmationEnabled,
                    onCheckedChange = onToggleDeleteConfirmation
                )

                SettingSwitchCard(
                    title = stringResource(R.string.preferences_remember_last_folder_title),
                    summary = if (state.rememberLastFolderEnabled) {
                        stringResource(R.string.state_enabled)
                    } else {
                        stringResource(R.string.state_disabled)
                    },
                    checked = state.rememberLastFolderEnabled,
                    onCheckedChange = onToggleRememberLastFolder
                )
            }

            Spacer(
                modifier = Modifier
                    .navigationBarsPadding()
                    .size(1.dp)
            )
        }
    }
}

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
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

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
            Color(0x1AB58B4B)
        } else {
            Color(0x0FFFFFFF)
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) Color(0xFF8D5F23) else Color(0x33B58B4B)
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
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(option.previewColor)
            )
            Text(
                text = stringResource(option.labelRes),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

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
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            Color(0xFF8D5F23)
                        } else {
                            Color(0x33B58B4B)
                        }
                    )
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun colorLabel(colorId: Int): String {
    return stringResource(noteColorOptions().firstOrNull { it.colorId == colorId }?.labelRes ?: R.string.color_yellow)
}

private data class NoteColorOption(
    val colorId: Int,
    val previewColor: Color,
    val labelRes: Int
)

private data class SortOption(
    val sortMode: NotesSortMode,
    val labelRes: Int
)

private fun noteColorOptions(): List<NoteColorOption> {
    return listOf(
        NoteColorOption(ResourceParser.YELLOW, Color(0xFFFFF3BF), R.string.color_yellow),
        NoteColorOption(ResourceParser.BLUE, Color(0xFFDCEBFF), R.string.color_blue),
        NoteColorOption(ResourceParser.WHITE, Color(0xFFF7F4EC), R.string.color_white),
        NoteColorOption(ResourceParser.GREEN, Color(0xFFE1F2D8), R.string.color_green),
        NoteColorOption(ResourceParser.RED, Color(0xFFFFDED7), R.string.color_red)
    )
}

private fun sortOptions(): List<SortOption> {
    return listOf(
        SortOption(NotesSortMode.MODIFIED_DESC, R.string.preferences_sort_modified_desc),
        SortOption(NotesSortMode.MODIFIED_ASC, R.string.preferences_sort_modified_asc),
        SortOption(NotesSortMode.TITLE_ASC, R.string.preferences_sort_title_asc)
    )
}
