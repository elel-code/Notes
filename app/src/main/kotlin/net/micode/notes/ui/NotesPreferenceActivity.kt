package net.micode.notes.ui

import android.accounts.Account
import android.accounts.AccountManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import net.micode.notes.R
import net.micode.notes.gtask.remote.GTaskSyncService

class NotesPreferenceActivity : ComponentActivity() {
    private lateinit var receiver: GTaskReceiver
    private var originalAccounts: Array<Account> = emptyArray()
    private var hasAddedAccount = false
    private var uiState by mutableStateOf(NotesPreferenceUiState())
    private var dialogState by mutableStateOf<PreferenceDialogUiState?>(null)

    private val preferenceViewModel: NotesPreferenceViewModel by lazy {
        ViewModelProvider(this)[NotesPreferenceViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        receiver = GTaskReceiver()
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(GTaskSyncService.GTASK_SERVICE_BROADCAST_NAME),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        refreshUiState()

        setContent {
            MaterialTheme {
                NotesPreferenceScreen(
                    state = uiState,
                    dialogState = dialogState,
                    onBack = { finish() },
                    onAccountClick = { onSyncAccountPreferenceClicked(uiState.accountName) },
                    onToggleRandomBackground = { enabled -> setRandomBackgroundEnabled(enabled) },
                    onSyncClick = { onSyncButtonClick() },
                    onDismissDialog = { dialogState = null },
                    onSelectAccount = { accountName ->
                        setSyncAccount(accountName)
                        dialogState = null
                        refreshUiState()
                    },
                    onAddAccount = {
                        hasAddedAccount = true
                        dialogState = null
                        startActivity(
                            Intent(Settings.ACTION_ADD_ACCOUNT).apply {
                                putExtra(AUTHORITIES_FILTER_KEY, arrayOf("gmail-ls"))
                            }
                        )
                    },
                    onChangeAccount = { showSelectAccountDialog() },
                    onRemoveAccount = {
                        removeSyncAccount()
                        dialogState = null
                        refreshUiState()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (hasAddedAccount) {
            googleAccounts
                .takeIf { it.size > originalAccounts.size }
                ?.firstOrNull { accountNew ->
                    originalAccounts.none { accountOld -> accountOld.name == accountNew.name }
                }
                ?.let { setSyncAccount(it.name) }
        }

        refreshUiState()
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    private fun onSyncAccountPreferenceClicked(defaultAccount: String) {
        if (GTaskSyncService.isSyncing) {
            Toast.makeText(
                this,
                R.string.preferences_toast_cannot_change_account,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (defaultAccount.isEmpty()) {
            showSelectAccountDialog()
        } else {
            showManageAccountDialog()
        }
    }

    private fun refreshUiState(syncProgressOverride: String? = null) {
        val syncProgress = syncProgressOverride.takeUnless { it.isNullOrBlank() }
            ?: GTaskSyncService.progressString.orEmpty()
        val lastSyncTime = getLastSyncTime(this)
        uiState = NotesPreferenceUiState(
            accountName = getSyncAccountName(this),
            lastSyncText = if (lastSyncTime != 0L) {
                getString(
                    R.string.preferences_last_sync_time,
                    DateFormat.format(
                        getString(R.string.preferences_last_sync_time_format),
                        lastSyncTime
                    )
                )
            } else {
                ""
            },
            syncProgressText = syncProgress,
            isSyncing = GTaskSyncService.isSyncing,
            randomBackgroundEnabled = getRandomBackgroundEnabled()
        )
    }

    private fun getRandomBackgroundEnabled(): Boolean {
        return getSharedPreferences(PREFERENCE_NAME, MODE_PRIVATE)
            .getBoolean(PREFERENCE_SET_BG_COLOR_KEY, false)
    }

    private fun setRandomBackgroundEnabled(enabled: Boolean) {
        getSharedPreferences(PREFERENCE_NAME, MODE_PRIVATE)
            .edit { putBoolean(PREFERENCE_SET_BG_COLOR_KEY, enabled) }
        refreshUiState()
    }

    private fun onSyncButtonClick() {
        if (getSyncAccountName(this).isBlank()) {
            showSelectAccountDialog()
            return
        }

        if (GTaskSyncService.isSyncing) {
            GTaskSyncService.cancelSync(this)
        } else {
            GTaskSyncService.startSync(this)
        }
        refreshUiState()
    }

    private fun showSelectAccountDialog() {
        val accounts = googleAccounts
        val defaultAccount = getSyncAccountName(this)

        originalAccounts = accounts
        hasAddedAccount = false
        dialogState = PreferenceDialogUiState.SelectAccount(
            accounts = accounts.map(Account::name),
            selectedAccountName = defaultAccount
        )
    }

    private fun showManageAccountDialog() {
        dialogState = PreferenceDialogUiState.ManageAccount(
            accountName = getSyncAccountName(this)
        )
    }

    private val googleAccounts: Array<Account>
        get() = AccountManager.get(this).getAccountsByType("com.google")

    private fun setSyncAccount(account: String?) {
        if (getSyncAccountName(this) == account) {
            return
        }

        getSharedPreferences(PREFERENCE_NAME, MODE_PRIVATE)
            .edit { putString(PREFERENCE_SYNC_ACCOUNT_NAME, account.orEmpty()) }

        setLastSyncTime(this, 0)
        preferenceViewModel.clearSyncMetadata(applicationContext)

        Toast.makeText(
            this,
            getString(R.string.preferences_toast_success_set_accout, account),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun removeSyncAccount() {
        val settings = getSharedPreferences(PREFERENCE_NAME, MODE_PRIVATE)
        settings.edit {
            if (settings.contains(PREFERENCE_SYNC_ACCOUNT_NAME)) {
                remove(PREFERENCE_SYNC_ACCOUNT_NAME)
            }
            if (settings.contains(PREFERENCE_LAST_SYNC_TIME)) {
                remove(PREFERENCE_LAST_SYNC_TIME)
            }
        }

        preferenceViewModel.clearSyncMetadata(applicationContext)
    }

    private inner class GTaskReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val progress = if (
                intent.getBooleanExtra(GTaskSyncService.GTASK_SERVICE_BROADCAST_IS_SYNCING, false)
            ) {
                intent.getStringExtra(GTaskSyncService.GTASK_SERVICE_BROADCAST_PROGRESS_MSG)
            } else {
                null
            }
            refreshUiState(progress)
        }
    }

    companion object {
        const val PREFERENCE_NAME: String = "notes_preferences"
        const val PREFERENCE_SYNC_ACCOUNT_NAME: String = "pref_key_account_name"
        const val PREFERENCE_LAST_SYNC_TIME: String = "pref_last_sync_time"
        const val PREFERENCE_SET_BG_COLOR_KEY: String = "pref_key_bg_random_appear"
        const val PREFERENCE_SYNC_ACCOUNT_KEY: String = "pref_sync_account_key"

        private const val AUTHORITIES_FILTER_KEY = "authorities"

        fun getSyncAccountName(context: Context): String {
            return context.getSharedPreferences(PREFERENCE_NAME, MODE_PRIVATE)
                .getString(PREFERENCE_SYNC_ACCOUNT_NAME, "")
                .orEmpty()
        }

        fun setLastSyncTime(context: Context, time: Long) {
            context.getSharedPreferences(PREFERENCE_NAME, MODE_PRIVATE)
                .edit { putLong(PREFERENCE_LAST_SYNC_TIME, time) }
        }

        fun getLastSyncTime(context: Context): Long {
            return context.getSharedPreferences(PREFERENCE_NAME, MODE_PRIVATE)
                .getLong(PREFERENCE_LAST_SYNC_TIME, 0)
        }
    }
}
