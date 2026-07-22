package com.anisync.android.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anisync.android.data.AppSettings
import com.anisync.android.data.mal.oauth.MalAuthFailureReason
import com.anisync.android.data.mal.oauth.MalAuthRepository
import com.anisync.android.data.mal.oauth.MalAuthState
import com.anisync.android.data.mal.oauth.MalLoginStartResult
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MalAccountConnectionState {
    NOT_CONFIGURED,
    DISCONNECTED,
    OPENING_BROWSER,
    AWAITING_CALLBACK,
    PROCESSING,
    CONNECTED,
    RELOGIN_REQUIRED,
    ERROR,
}

data class MalAccountSettingsUiState(
    val connectionState: MalAccountConnectionState = MalAccountConnectionState.DISCONNECTED,
    val configured: Boolean = false,
    val localAccountId: String? = null,
    val displayName: String? = null,
    val failureReason: MalAuthFailureReason? = null,
    val retryAfterSeconds: Long? = null,
    val animeTrackingMode: TrackingMode = TrackingMode.ANILIST_ONLY,
    val mangaTrackingMode: TrackingMode = TrackingMode.ANILIST_ONLY,
) {
    val isBusy: Boolean
        get() = connectionState == MalAccountConnectionState.OPENING_BROWSER ||
            connectionState == MalAccountConnectionState.AWAITING_CALLBACK ||
            connectionState == MalAccountConnectionState.PROCESSING
}

sealed interface MalAccountSettingsEffect {
    data class OpenBrowser(val authorizationUrl: String) : MalAccountSettingsEffect {
        override fun toString(): String =
            "MalAccountSettingsEffect.OpenBrowser(authorizationUrl=<redacted>)"
    }
}

@HiltViewModel
class MalAccountSettingsViewModel @Inject constructor(
    private val authRepository: MalAuthRepository,
    private val appSettings: AppSettings,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MalAccountSettingsUiState())
    val uiState: StateFlow<MalAccountSettingsUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<MalAccountSettingsEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<MalAccountSettingsEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            authRepository.state.collectLatest { state ->
                _uiState.value = state.toMalAccountSettingsUiState(_uiState.value)
            }
        }
        viewModelScope.launch {
            authRepository.refreshState()
        }
        viewModelScope.launch {
            appSettings.animeTrackingMode.collectLatest { mode ->
                _uiState.update { it.copy(animeTrackingMode = mode) }
            }
        }
        viewModelScope.launch {
            appSettings.mangaTrackingMode.collectLatest { mode ->
                _uiState.update { it.copy(mangaTrackingMode = mode) }
            }
        }
    }

    fun connect() {
        val targetAccountId = _uiState.value.localAccountId
        _uiState.update {
            it.copy(
                connectionState = MalAccountConnectionState.OPENING_BROWSER,
                failureReason = null,
                retryAfterSeconds = null,
            )
        }
        viewModelScope.launch {
            when (val result = authRepository.startLogin(targetAccountId)) {
                is MalLoginStartResult.Success -> {
                    _effects.emit(MalAccountSettingsEffect.OpenBrowser(result.authorizationUrl))
                }
                is MalLoginStartResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            connectionState = if (result.reason == MalAuthFailureReason.CONFIGURATION_UNAVAILABLE) {
                                MalAccountConnectionState.NOT_CONFIGURED
                            } else {
                                MalAccountConnectionState.ERROR
                            },
                            failureReason = result.reason,
                        )
                    }
                }
            }
        }
    }

    fun logout() {
        val localAccountId = _uiState.value.localAccountId ?: return
        viewModelScope.launch {
            authRepository.logout(localAccountId)
        }
    }

    fun retryPendingCompletion() {
        viewModelScope.launch {
            authRepository.resumePendingLogin()
        }
    }

    fun dismissError() {
        viewModelScope.launch {
            authRepository.cancelPendingLogin()
        }
    }

    fun setTrackingMode(mediaType: TrackingMediaType, mode: TrackingMode) {
        appSettings.setTrackingMode(mediaType, mode)
    }
}

internal fun MalAuthState.toMalAccountSettingsUiState(
    previous: MalAccountSettingsUiState = MalAccountSettingsUiState(),
): MalAccountSettingsUiState = when (this) {
    is MalAuthState.Disconnected -> previous.copy(
        connectionState = if (configured) {
            MalAccountConnectionState.DISCONNECTED
        } else {
            MalAccountConnectionState.NOT_CONFIGURED
        },
        configured = configured,
        localAccountId = null,
        displayName = null,
        failureReason = null,
        retryAfterSeconds = null,
    )
    is MalAuthState.AwaitingCallback -> previous.copy(
        connectionState = MalAccountConnectionState.AWAITING_CALLBACK,
        failureReason = null,
    )
    is MalAuthState.Processing -> previous.copy(
        connectionState = MalAccountConnectionState.PROCESSING,
        failureReason = null,
    )
    is MalAuthState.Connected -> previous.copy(
        connectionState = MalAccountConnectionState.CONNECTED,
        configured = true,
        localAccountId = account.localAccountId,
        displayName = account.profile.displayName ?: account.profile.username,
        failureReason = null,
        retryAfterSeconds = null,
    )
    is MalAuthState.ReLoginRequired -> previous.copy(
        connectionState = MalAccountConnectionState.RELOGIN_REQUIRED,
        configured = true,
        localAccountId = localAccountId,
        displayName = null,
        failureReason = null,
        retryAfterSeconds = null,
    )
    is MalAuthState.Error -> previous.copy(
        connectionState = if (reason == MalAuthFailureReason.CONFIGURATION_UNAVAILABLE) {
            MalAccountConnectionState.NOT_CONFIGURED
        } else {
            MalAccountConnectionState.ERROR
        },
        configured = reason != MalAuthFailureReason.CONFIGURATION_UNAVAILABLE,
        localAccountId = localAccountId ?: previous.localAccountId,
        failureReason = reason,
        retryAfterSeconds = retryAfterSeconds,
    )
}
