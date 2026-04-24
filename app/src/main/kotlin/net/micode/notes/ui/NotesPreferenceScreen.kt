package net.micode.notes.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.micode.notes.R

data class NotesPreferenceUiState(
    val accountName: String = "",
    val lastSyncText: String = "",
    val syncProgressText: String = "",
    val isSyncing: Boolean = false,
    val randomBackgroundEnabled: Boolean = false
)

sealed interface PreferenceDialogUiState {
    data class SelectAccount(
        val accounts: List<String>,
        val selectedAccountName: String
    ) : PreferenceDialogUiState

    data class ManageAccount(
        val accountName: String
    ) : PreferenceDialogUiState
}

@Composable
fun NotesPreferenceScreen(
    state: NotesPreferenceUiState,
    dialogState: PreferenceDialogUiState?,
    onBack: () -> Unit,
    onAccountClick: () -> Unit,
    onToggleRandomBackground: (Boolean) -> Unit,
    onSyncClick: () -> Unit,
    onDismissDialog: () -> Unit,
    onSelectAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onChangeAccount: () -> Unit,
    onRemoveAccount: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        ResourceDrawableBackground(
            resId = R.drawable.list_background,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
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
                Card(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onAccountClick)
                            .padding(20.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.preferences_account_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.accountName.ifBlank {
                                stringResource(R.string.preferences_account_summary)
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

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
                                text = stringResource(R.string.preferences_bg_random_appear_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (state.randomBackgroundEnabled) {
                                    stringResource(R.string.state_enabled)
                                } else {
                                    stringResource(R.string.state_disabled)
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Switch(
                            checked = state.randomBackgroundEnabled,
                            onCheckedChange = onToggleRandomBackground
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = if (state.isSyncing) {
                                state.syncProgressText.ifBlank {
                                    stringResource(R.string.menu_sync)
                                }
                            } else {
                                state.lastSyncText.ifBlank {
                                    stringResource(R.string.preferences_account_summary)
                                }
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        FilledTonalButton(
                            onClick = onSyncClick,
                            enabled = state.accountName.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(
                                    if (state.isSyncing) {
                                        R.string.preferences_button_sync_cancel
                                    } else {
                                        R.string.preferences_button_sync_immediately
                                    }
                                )
                            )
                        }
                    }
                }
            }

            Spacer(
                modifier = Modifier
                    .navigationBarsPadding()
                    .size(1.dp)
            )
        }

        when (val currentDialogState = dialogState) {
            is PreferenceDialogUiState.SelectAccount -> {
                SelectAccountDialog(
                    state = currentDialogState,
                    onDismiss = onDismissDialog,
                    onSelectAccount = onSelectAccount,
                    onAddAccount = onAddAccount
                )
            }

            is PreferenceDialogUiState.ManageAccount -> {
                ManageAccountDialog(
                    state = currentDialogState,
                    onDismiss = onDismissDialog,
                    onChangeAccount = onChangeAccount,
                    onRemoveAccount = onRemoveAccount
                )
            }

            null -> Unit
        }
    }
}

@Composable
private fun SelectAccountDialog(
    state: PreferenceDialogUiState.SelectAccount,
    onDismiss: () -> Unit,
    onSelectAccount: (String) -> Unit,
    onAddAccount: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = stringResource(R.string.preferences_dialog_select_account_title))
                Text(
                    text = stringResource(R.string.preferences_dialog_select_account_tips),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6E6253)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.accounts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.preferences_account_summary),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    state.accounts.forEach { accountName ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = if (accountName == state.selectedAccountName) {
                                Color(0x26C58E35)
                            } else {
                                Color(0x12FFFFFF)
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectAccount(accountName) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = accountName == state.selectedAccountName,
                                    onClick = null
                                )
                                Text(
                                    text = accountName,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onAddAccount,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.preferences_add_account))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun ManageAccountDialog(
    state: PreferenceDialogUiState.ManageAccount,
    onDismiss: () -> Unit,
    onChangeAccount: () -> Unit,
    onRemoveAccount: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    R.string.preferences_dialog_change_account_title,
                    state.accountName
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = stringResource(R.string.preferences_dialog_change_account_warn_msg))
                FilledTonalButton(
                    onClick = onChangeAccount,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.preferences_menu_change_account))
                }
                OutlinedButton(
                    onClick = onRemoveAccount,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.preferences_menu_remove_account))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    )
}
