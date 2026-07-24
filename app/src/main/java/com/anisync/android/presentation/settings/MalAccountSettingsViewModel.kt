package com.anisync.android.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anisync.android.data.account.AccountStore
import com.anisync.android.data.mal.account.MalAccount
import com.anisync.android.data.mal.account.MalAccountRepository
import com.anisync.android.data.privacy.MalConsentStore
import com.anisync.android.data.provider.ActiveProviderStore
import com.anisync.android.presentation.settings.provider.ProviderAccountAction
import com.anisync.android.presentation.settings.provider.ProviderAccountSettingsActionDispatcher
import com.anisync.android.presentation.settings.provider.ProviderAccountSettingsError
import com.anisync.android.presentation.settings.provider.ProviderAccountSettingsMapper
import com.anisync.android.presentation.settings.provider.ProviderAccountSettingsUiState
import com.anisync.android.presentation.settings.provider.ProviderAccountSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MalAccountSettingsViewModel @Inject constructor(
    providerStore: ActiveProviderStore,
    accountStore: AccountStore,
    private val malAccounts: MalAccountRepository,
    private val actionDispatcher: ProviderAccountSettingsActionDispatcher,
    private val consentStore: MalConsentStore,
) : ViewModel() {
    private val malAccount = MutableStateFlow<MalAccount?>(null)
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<ProviderAccountSettingsError?>(null)

    private val accountSnapshot = combine(
        accountStore.activeAccount,
        malAccount,
    ) { aniListAccount, currentMalAccount ->
        ProviderAccountSnapshot(
            malAccount = currentMalAccount,
            aniListAccountPresent = aniListAccount != null,
            aniListAccountExpired = aniListAccount?.isExpired == true,
        )
    }

    val uiState: StateFlow<ProviderAccountSettingsUiState> = combine(
        providerStore.state,
        accountSnapshot,
        consentStore.record,
        busy,
        error,
    ) { providerState, accounts, consent, isBusy, currentError ->
        ProviderAccountSettingsMapper.map(
            providerState = providerState,
            accountSnapshot = accounts,
            malConsentStored = consent != null,
            busy = isBusy,
            error = currentError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ProviderAccountSettingsMapper.map(
            providerState = providerStore.snapshot(),
            accountSnapshot = ProviderAccountSnapshot(
                aniListAccountPresent = accountStore.activeAccount.value != null,
                aniListAccountExpired = accountStore.activeAccount.value?.isExpired == true,
            ),
            malConsentStored = consentStore.record.value != null,
            busy = false,
            error = null,
        ),
    )

    init {
        viewModelScope.launch { refreshMalAccount() }
    }

    fun onAction(action: ProviderAccountAction) {
        when (action) {
            ProviderAccountAction.REVOKE_MAL_CONSENT -> revokeMalConsent()
            ProviderAccountAction.DISCONNECT_AND_DELETE_LOCAL_DATA,
            ProviderAccountAction.CHANGE_PROVIDER -> runDestructiveAction(action)
        }
    }

    fun disconnectAndChangeProvider() = onAction(ProviderAccountAction.CHANGE_PROVIDER)

    fun disconnectAndDeleteLocalData() =
        onAction(ProviderAccountAction.DISCONNECT_AND_DELETE_LOCAL_DATA)

    fun revokeMalConsent() {
        error.value = null
        runCatching { consentStore.revoke() }
            .onFailure { error.value = ProviderAccountSettingsError.LOCAL_ACTION_FAILED }
    }

    private fun runDestructiveAction(action: ProviderAccountAction) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            error.value = null
            runCatching { actionDispatcher.execute(action) }
                .onFailure { error.value = ProviderAccountSettingsError.LOCAL_ACTION_FAILED }
            refreshMalAccount()
            busy.value = false
        }
    }

    private suspend fun refreshMalAccount() {
        val result = runCatching { malAccounts.activeAccount() }
        malAccount.value = result.getOrNull()
        if (result.isFailure) {
            error.value = ProviderAccountSettingsError.LOCAL_STATE_UNAVAILABLE
        }
    }
}
