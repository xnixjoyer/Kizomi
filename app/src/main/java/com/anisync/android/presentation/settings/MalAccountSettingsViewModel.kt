package com.anisync.android.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anisync.android.data.privacy.MalConsentStore
import com.anisync.android.data.provider.ActiveProviderStore
import com.anisync.android.data.provider.ProviderSessionCoordinator
import com.anisync.android.domain.provider.ProviderRuntimeState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MalAccountSettingsViewModel @Inject constructor(
    providerStore: ActiveProviderStore,
    private val coordinator: ProviderSessionCoordinator,
    private val consentStore: MalConsentStore,
) : ViewModel() {
    val providerState: StateFlow<ProviderRuntimeState> = providerStore.state
    val consentRecord = consentStore.record

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun disconnectAndChangeProvider() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            runCatching { coordinator.prepareDestructiveProviderChange() }
            _busy.value = false
        }
    }

    fun revokeMalConsent() = consentStore.revoke()
}
