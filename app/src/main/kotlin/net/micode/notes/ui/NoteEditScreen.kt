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

data class ReminderDialogUiState(
    val initialDate: Long = System.currentTimeMillis()
)

private val HeaderActionColor = Color(0xFF7A6A43)
private val HeaderTitleColor = Color(0xFF231C12)
private val HeaderSubtitleColor = Color(0xAA3D3427)
private val EditorControlSurfaceColor = Color(0xF0E5D6A8)
private val EditorTextColor = Color(0xFF2A261F)
private val EditorHintColor = Color(0x88544E43)

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
    Box(modifier = Modifier.fillMaxSize()) {
        if (state.bgResId != 0) {
            ResourceDrawableBackground(
                resId = state.bgResId,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFFF3BF))
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
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
                        LegacyAssetIcon(
                            resId = R.drawable.title_alert,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(
                            text = DateUtils.getRelativeTimeSpanString(
                                state.alertDate,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS
                            ).toString(),
                            modifier = Modifier.weight(1f),
                            color = HeaderSubtitleColor
                        )
                        RemoveReminderAction(onClick = onClearReminder)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            ) {
                LegacyAssetIcon(
                    resId = R.drawable.bg_color_btn_mask,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val scrollState = rememberScrollState()
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
                            if (state.content.isEmpty()) {
                                Text(
                                    text = if (state.isChecklistMode) {
                                        stringResource(R.string.checklist_placeholder)
                                    } else {
                                        stringResource(R.string.note_editor_placeholder)
                                    },
                                    color = EditorHintColor,
                                    fontSize = fontSizeFor(state.fontSizeId)
                                )
                            }
                            innerTextField()
                        }
                    )
                }
                LegacyAssetIcon(
                    resId = R.drawable.bg_color_btn_mask,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                )
            }

            EditorControls(
                state = state,
                onSelectBackground = onSelectBackground,
                onSelectFontSize = onSelectFontSize
            )
        }

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

        reminderDialogState?.let { dialogState ->
            ReminderDialog(
                state = dialogState,
                onDismiss = onDismissReminderDialog,
                onConfirm = onConfirmReminder
            )
        }
    }
}

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
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        if (state.titleBgResId != 0) {
            ResourceDrawableBackground(
                resId = state.titleBgResId,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xDDECDCB6))
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderActionText(
                text = stringResource(R.string.action_back),
                onClick = onBack
            )
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
            Box {
                HeaderActionText(
                    text = stringResource(R.string.action_more),
                    onClick = { expanded = true }
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.notelist_menu_new)) },
                        onClick = {
                            expanded = false
                            onNewNote()
                        }
                    )
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
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(
                                    if (state.isChecklistMode) {
                                        R.string.menu_normal_mode
                                    } else {
                                        R.string.menu_list_mode
                                    }
                                )
                            )
                        },
                        onClick = {
                            expanded = false
                            onToggleChecklist()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.menu_share)) },
                        onClick = {
                            expanded = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.menu_export_as_txt)) },
                        onClick = {
                            expanded = false
                            onExportText()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.menu_generate_long_image)) },
                        onClick = {
                            expanded = false
                            onSaveLongImage()
                        }
                    )
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
                                        R.string.menu_remove_remind
                                    } else {
                                        R.string.menu_alert
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

@Composable
private fun ReminderDialog(
    state: ReminderDialogUiState,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val context = LocalContext.current
    val initialCalendar = remember(state.initialDate) {
        Calendar.getInstance().apply {
            timeInMillis = state.initialDate
        }
    }
    var year by remember(state.initialDate) {
        mutableIntStateOf(initialCalendar.get(Calendar.YEAR))
    }
    var month by remember(state.initialDate) {
        mutableIntStateOf(initialCalendar.get(Calendar.MONTH))
    }
    var dayOfMonth by remember(state.initialDate) {
        mutableIntStateOf(initialCalendar.get(Calendar.DAY_OF_MONTH))
    }
    var selectedHourOfDay by remember(state.initialDate) {
        mutableIntStateOf(initialCalendar.get(Calendar.HOUR_OF_DAY))
    }
    var selectedMinute by remember(state.initialDate) {
        mutableIntStateOf(initialCalendar.get(Calendar.MINUTE))
    }
    val is24Hour = remember(context) { AndroidDateFormat.is24HourFormat(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.menu_alert)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

@Composable
private fun EditorControls(
    state: NoteEditUiState,
    onSelectBackground: (Int) -> Unit,
    onSelectFontSize: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = EditorControlSurfaceColor,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                                            Color(0xFF6A4A1E)
                                        } else {
                                            Color(0x66443A2D)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelectBackground(bgId) }
                            ) {
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
                                LegacyAssetIcon(
                                    resId = option.drawableRes,
                                    contentDescription = stringResource(option.labelRes),
                                    modifier = Modifier.size(width = 24.dp, height = 28.dp)
                                )
                                Text(
                                    text = stringResource(option.labelRes),
                                    fontSize = 10.sp,
                                    lineHeight = 10.sp,
                                    textAlign = TextAlign.Center,
                                    color = Color(0xFF5A5348)
                                )
                            }
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

@Composable
private fun fontOptions(): List<FontOption> {
    return listOf(
        FontOption(ResourceParser.TEXT_SMALL, R.drawable.font_small, R.string.menu_font_small),
        FontOption(ResourceParser.TEXT_MEDIUM, R.drawable.font_normal, R.string.menu_font_normal),
        FontOption(ResourceParser.TEXT_LARGE, R.drawable.font_large, R.string.menu_font_large),
        FontOption(ResourceParser.TEXT_SUPER, R.drawable.font_super, R.string.menu_font_super)
    )
}

private data class FontOption(
    val fontId: Int,
    @param:DrawableRes val drawableRes: Int,
    @param:StringRes val labelRes: Int
)

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

private fun fontSizeFor(fontSizeId: Int) = when (fontSizeId) {
    ResourceParser.TEXT_SMALL -> 15.sp
    ResourceParser.TEXT_MEDIUM -> 18.sp
    ResourceParser.TEXT_LARGE -> 22.sp
    ResourceParser.TEXT_SUPER -> 26.sp
    else -> 18.sp
}

private fun colorForBackground(bgColorId: Int) = when (bgColorId) {
    ResourceParser.YELLOW -> Color(0xFFEECF68)
    ResourceParser.BLUE -> Color(0xFF8DB6E9)
    ResourceParser.WHITE -> Color(0xFFF5F2E8)
    ResourceParser.GREEN -> Color(0xFFA1C88C)
    ResourceParser.RED -> Color(0xFFE59882)
    else -> Color(0xFFEECF68)
}
