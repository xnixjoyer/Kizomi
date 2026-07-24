package com.anisync.android.presentation.mal

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anisync.android.data.mal.account.MalAccountCredentialStore
import com.anisync.android.data.mal.api.MalApiFailure
import com.anisync.android.data.mal.api.MalApiFailureKind
import com.anisync.android.data.mal.api.MalLibraryItem
import com.anisync.android.data.mal.api.MalLibraryRefreshResult
import com.anisync.android.data.mal.api.MalLibraryRepository
import com.anisync.android.data.provider.ProviderSessionCoordinator
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.presentation.MainScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Compatibility entry point retained while callers migrate. MAL now enters the same adaptive
 * [MainScreen] scaffold as AniList; provider capability policy chooses the MAL-native root graph.
 */
@Composable
fun MalProviderMainScreen() {
    MainScreen()
}

data class MalLibraryUiState(
    val mediaType: TrackingMediaType = TrackingMediaType.ANIME,
    val entries: List<MalLibraryItem> = emptyList(),
    val loading: Boolean = false,
    val error: MalApiFailure? = null,
)

@HiltViewModel
class MalLibraryViewModel @Inject constructor(
    private val repository: MalLibraryRepository,
    private val accounts: MalAccountCredentialStore,
) : ViewModel() {
    private val selectedType = MutableStateFlow(TrackingMediaType.ANIME)
    private val mutableState = MutableStateFlow(MalLibraryUiState())
    val uiState: StateFlow<MalLibraryUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                selectedType,
                selectedType.flatMapLatest { type ->
                    val account = accounts.activeAccount()
                    if (account == null) flowOf(emptyList())
                    else repository.observeLibrary(account.localAccountId, type)
                },
            ) { type, entries -> type to entries }
                .collect { (type, entries) ->
                    mutableState.update { it.copy(mediaType = type, entries = entries) }
                }
        }
        refresh()
    }

    fun selectType(type: TrackingMediaType) {
        selectedType.value = type
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            val account = accounts.activeAccount()
            if (account == null) {
                mutableState.update {
                    it.copy(
                        loading = false,
                        error = MalApiFailure(MalApiFailureKind.NOT_AUTHENTICATED),
                    )
                }
                return@launch
            }
            when (val result = repository.refresh(account.localAccountId, selectedType.value)) {
                is MalLibraryRefreshResult.Success -> mutableState.update {
                    it.copy(loading = false)
                }
                is MalLibraryRefreshResult.Failure -> mutableState.update {
                    it.copy(loading = false, error = result.error)
                }
            }
        }
    }
}

@HiltViewModel
class MalProviderAccountViewModel @Inject constructor(
    private val coordinator: ProviderSessionCoordinator,
) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun disconnectAndDelete() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            runCatching { coordinator.disconnectAndDeleteAllLocalProviderData() }
            _busy.value = false
        }
    }
}
