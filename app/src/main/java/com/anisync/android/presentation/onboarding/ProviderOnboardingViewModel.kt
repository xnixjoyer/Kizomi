package com.anisync.android.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anisync.android.data.AniListAuth
import com.anisync.android.data.mal.oauth.MalAuthFailureReason
import com.anisync.android.data.mal.oauth.MalAuthRepository
import com.anisync.android.data.mal.oauth.MalLoginStartResult
import com.anisync.android.data.privacy.MalConsentStore
import com.anisync.android.data.provider.ActiveProviderStore
import com.anisync.android.data.provider.ProviderSessionCoordinator
import com.anisync.android.domain.provider.ActiveProvider
import com.anisync.android.domain.provider.ProviderRuntimeState
import com.anisync.android.domain.provider.ProviderTransitionPhase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed interface ProviderOnboardingEffect {
    data class OpenBrowser(val url: String) : ProviderOnboardingEffect {
        override fun toString(): String = "ProviderOnboardingEffect.OpenBrowser(url=<redacted>)"
    }
    data class Error(val reason: String) : ProviderOnboardingEffect
}

@HiltViewModel
class ProviderOnboardingViewModel @Inject constructor(
    private val coordinator: ProviderSessionCoordinator,
    private val providerStore: ActiveProviderStore,
    private val malAuth: MalAuthRepository,
    private val consentStore: MalConsentStore,
) : ViewModel() {
    val providerState: StateFlow<ProviderRuntimeState> = providerStore.state
    val consentRecord = consentStore.record

    private val _effects = MutableSharedFlow<ProviderOnboardingEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<ProviderOnboardingEffect> = _effects.asSharedFlow()

    fun signInWithAniList() {
        if (providerState.value.transitionPhase == ProviderTransitionPhase.LEGACY_SELECTION_REQUIRED) {
            chooseLegacy(ActiveProvider.ANILIST_ONLY)
            return
        }
        runCatching { coordinator.beginLogin(ActiveProvider.ANILIST_ONLY) }
            .onSuccess { _effects.tryEmit(ProviderOnboardingEffect.OpenBrowser(AniListAuth.AUTH_URL)) }
            .onFailure { _effects.tryEmit(ProviderOnboardingEffect.Error("Unable to start AniList sign-in")) }
    }

    fun signInWithMal(consentChecked: Boolean) {
        if (providerState.value.transitionPhase == ProviderTransitionPhase.LEGACY_SELECTION_REQUIRED) {
            chooseLegacy(ActiveProvider.MAL_ONLY)
            return
        }
        if (!consentChecked && !consentStore.isCurrentConsentAccepted()) {
            _effects.tryEmit(ProviderOnboardingEffect.Error("Consent is required before MyAnimeList sign-in"))
            return
        }
        if (consentChecked && !consentStore.isCurrentConsentAccepted()) consentStore.accept()
        viewModelScope.launch {
            runCatching { coordinator.beginLogin(ActiveProvider.MAL_ONLY) }
                .onFailure {
                    _effects.emit(ProviderOnboardingEffect.Error("Unable to start MyAnimeList sign-in"))
                    return@launch
                }
            when (val result = malAuth.startLogin()) {
                is MalLoginStartResult.Success -> {
                    _effects.emit(ProviderOnboardingEffect.OpenBrowser(result.authorizationUrl))
                }
                is MalLoginStartResult.Failure -> {
                    coordinator.cancelLogin()
                    _effects.emit(ProviderOnboardingEffect.Error(result.reason.userMessage()))
                }
            }
        }
    }

    fun revokeMalConsent() = consentStore.revoke()

    private fun chooseLegacy(provider: ActiveProvider) {
        viewModelScope.launch {
            runCatching { coordinator.selectLegacyProvider(provider) }
                .onFailure {
                    _effects.emit(ProviderOnboardingEffect.Error("Local account migration failed"))
                }
        }
    }
}

private fun MalAuthFailureReason.userMessage(): String = when (this) {
    MalAuthFailureReason.CONFIGURATION_UNAVAILABLE -> "MyAnimeList sign-in is not configured in this build"
    else -> "MyAnimeList sign-in could not be started"
}
