package com.anisync.android.presentation.mal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anisync.android.data.mal.account.MalAccountCredentialStore
import com.anisync.android.data.mal.api.MalApiFailure
import com.anisync.android.data.mal.api.MalApiFailureKind
import com.anisync.android.data.mal.api.MalApiResult
import com.anisync.android.data.mal.api.MalCatalogMedia
import com.anisync.android.data.mal.api.MalCatalogRepository
import com.anisync.android.data.mal.api.MalMediaKey
import com.anisync.android.data.mal.api.MalSeason
import com.anisync.android.domain.tracking.TrackingMediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class MalCatalogContentSource {
    RANKING,
    SEASONAL,
    SEARCH,
    CACHE,
}

enum class MalDiscoveryMode {
    RANKING,
    SEASONAL,
}

data class MalCatalogUiState(
    val mediaType: TrackingMediaType = TrackingMediaType.ANIME,
    val query: String = "",
    val entries: List<MalCatalogMedia> = emptyList(),
    val discoveryMode: MalDiscoveryMode = MalDiscoveryMode.RANKING,
    val source: MalCatalogContentSource = MalCatalogContentSource.RANKING,
    val loading: Boolean = true,
    val error: MalApiFailure? = null,
) {
    val showingOfflineCache: Boolean
        get() = source == MalCatalogContentSource.CACHE && error != null
}

@HiltViewModel
class MalCatalogViewModel @Inject constructor(
    private val repository: MalCatalogRepository,
    private val accounts: MalAccountCredentialStore,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        MalCatalogUiState(
            mediaType = savedStateHandle.get<String>(KEY_MEDIA_TYPE)
                ?.let { runCatching { TrackingMediaType.valueOf(it) }.getOrNull() }
                ?: TrackingMediaType.ANIME,
            query = savedStateHandle[KEY_QUERY] ?: "",
            discoveryMode = savedStateHandle.get<String>(KEY_DISCOVERY_MODE)
                ?.let { runCatching { MalDiscoveryMode.valueOf(it) }.getOrNull() }
                ?: MalDiscoveryMode.RANKING,
        )
    )
    val uiState: StateFlow<MalCatalogUiState> = _uiState.asStateFlow()
    private var requestJob: Job? = null

    init {
        if (_uiState.value.mediaType == TrackingMediaType.MANGA &&
            _uiState.value.discoveryMode == MalDiscoveryMode.SEASONAL
        ) {
            savedStateHandle[KEY_DISCOVERY_MODE] = MalDiscoveryMode.RANKING.name
            _uiState.update { it.copy(discoveryMode = MalDiscoveryMode.RANKING) }
        }
        loadCurrentSurface()
    }

    fun setQuery(value: String) {
        val bounded = value.take(MAX_QUERY_LENGTH)
        if (_uiState.value.query == bounded) return
        requestJob?.cancel()
        savedStateHandle[KEY_QUERY] = bounded
        _uiState.update {
            it.copy(
                query = bounded,
                entries = emptyList(),
                loading = false,
                error = null,
            )
        }
    }

    fun setMediaType(value: TrackingMediaType) {
        if (_uiState.value.mediaType == value) return
        val discoveryMode = if (value == TrackingMediaType.MANGA) {
            MalDiscoveryMode.RANKING
        } else {
            _uiState.value.discoveryMode
        }
        savedStateHandle[KEY_MEDIA_TYPE] = value.name
        savedStateHandle[KEY_DISCOVERY_MODE] = discoveryMode.name
        _uiState.update {
            it.copy(
                mediaType = value,
                entries = emptyList(),
                error = null,
                discoveryMode = discoveryMode,
            )
        }
        loadCurrentSurface()
    }

    fun selectDiscovery(mode: MalDiscoveryMode) {
        if (mode == MalDiscoveryMode.SEASONAL && _uiState.value.mediaType != TrackingMediaType.ANIME) {
            return
        }
        savedStateHandle[KEY_DISCOVERY_MODE] = mode.name
        savedStateHandle[KEY_QUERY] = ""
        _uiState.update {
            it.copy(
                query = "",
                discoveryMode = mode,
                entries = emptyList(),
                error = null,
            )
        }
        loadCurrentSurface()
    }

    fun submit() = loadCurrentSurface()

    fun retry() = submit()

    private fun loadCurrentSurface() {
        when {
            _uiState.value.query.isNotBlank() -> search()
            _uiState.value.discoveryMode == MalDiscoveryMode.SEASONAL -> loadSeasonal()
            else -> loadRanking()
        }
    }

    private fun loadRanking() {
        requestJob?.cancel()
        val state = _uiState.value
        requestJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    entries = emptyList(),
                    loading = true,
                    error = null,
                    source = MalCatalogContentSource.RANKING,
                )
            }
            val account = accounts.activeAccount()
            if (account == null) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = MalApiFailure(MalApiFailureKind.NOT_AUTHENTICATED),
                    )
                }
                return@launch
            }
            when (val result = repository.ranking(account.localAccountId, state.mediaType)) {
                is MalApiResult.Success -> _uiState.update {
                    if (it.mediaType == state.mediaType && it.query.isBlank() &&
                        it.discoveryMode == MalDiscoveryMode.RANKING
                    ) {
                        it.copy(entries = result.value.entries, loading = false, error = null)
                    } else {
                        it
                    }
                }
                is MalApiResult.Failure -> _uiState.update {
                    if (it.mediaType == state.mediaType && it.query.isBlank() &&
                        it.discoveryMode == MalDiscoveryMode.RANKING
                    ) {
                        it.copy(loading = false, error = result.error)
                    } else {
                        it
                    }
                }
            }
        }
    }

    private fun loadSeasonal() {
        requestJob?.cancel()
        val state = _uiState.value
        if (state.mediaType != TrackingMediaType.ANIME) {
            loadRanking()
            return
        }
        requestJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    entries = emptyList(),
                    loading = true,
                    error = null,
                    source = MalCatalogContentSource.SEASONAL,
                )
            }
            val account = accounts.activeAccount()
            if (account == null) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = MalApiFailure(MalApiFailureKind.NOT_AUTHENTICATED),
                    )
                }
                return@launch
            }
            val today = LocalDate.now()
            when (val result = repository.seasonal(
                account.localAccountId,
                today.year,
                malSeasonForMonth(today.monthValue),
            )) {
                is MalApiResult.Success -> _uiState.update {
                    if (it.mediaType == TrackingMediaType.ANIME && it.query.isBlank() &&
                        it.discoveryMode == MalDiscoveryMode.SEASONAL
                    ) {
                        it.copy(entries = result.value.entries, loading = false, error = null)
                    } else {
                        it
                    }
                }
                is MalApiResult.Failure -> _uiState.update {
                    if (it.mediaType == TrackingMediaType.ANIME && it.query.isBlank() &&
                        it.discoveryMode == MalDiscoveryMode.SEASONAL
                    ) {
                        it.copy(loading = false, error = result.error)
                    } else {
                        it
                    }
                }
            }
        }
    }

    private fun search() {
        requestJob?.cancel()
        val state = _uiState.value
        val query = state.query.trim()
        if (query.isEmpty()) {
            loadCurrentSurface()
            return
        }
        requestJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    entries = emptyList(),
                    loading = true,
                    error = null,
                    source = MalCatalogContentSource.SEARCH,
                )
            }
            val cached = repository.cachedSearch(state.mediaType, query)
            if (cached.isNotEmpty()) {
                _uiState.update {
                    if (it.mediaType == state.mediaType && it.query.trim() == query) {
                        it.copy(entries = cached, source = MalCatalogContentSource.CACHE)
                    } else {
                        it
                    }
                }
            }
            val account = accounts.activeAccount()
            if (account == null) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = MalApiFailure(MalApiFailureKind.NOT_AUTHENTICATED),
                        source = if (cached.isEmpty()) {
                            MalCatalogContentSource.SEARCH
                        } else {
                            MalCatalogContentSource.CACHE
                        },
                    )
                }
                return@launch
            }
            when (val result = repository.search(
                account.localAccountId,
                state.mediaType,
                query,
            )) {
                is MalApiResult.Success -> _uiState.update {
                    if (it.mediaType == state.mediaType && it.query.trim() == query) {
                        it.copy(
                            entries = result.value.entries,
                            loading = false,
                            error = null,
                            source = MalCatalogContentSource.SEARCH,
                        )
                    } else {
                        it
                    }
                }
                is MalApiResult.Failure -> _uiState.update {
                    if (it.mediaType == state.mediaType && it.query.trim() == query) {
                        it.copy(
                            loading = false,
                            error = result.error,
                            source = if (cached.isEmpty()) {
                                MalCatalogContentSource.SEARCH
                            } else {
                                MalCatalogContentSource.CACHE
                            },
                        )
                    } else {
                        it
                    }
                }
            }
        }
    }

    companion object {
        private const val KEY_QUERY = "mal_catalog_query"
        private const val KEY_MEDIA_TYPE = "mal_catalog_media_type"
        private const val KEY_DISCOVERY_MODE = "mal_catalog_discovery_mode"
        private const val MAX_QUERY_LENGTH = 200
    }
}

internal fun malSeasonForMonth(month: Int): MalSeason = when (month.also { require(it in 1..12) }) {
    in 1..3 -> MalSeason.WINTER
    in 4..6 -> MalSeason.SPRING
    in 7..9 -> MalSeason.SUMMER
    else -> MalSeason.FALL
}

data class MalDetailsUiState(
    val key: MalMediaKey,
    val details: MalCatalogMedia? = null,
    val loading: Boolean = true,
    val error: MalApiFailure? = null,
) {
    val showingLastGood: Boolean
        get() = details != null && error != null
}

@HiltViewModel
class MalDetailsViewModel @Inject constructor(
    private val repository: MalCatalogRepository,
    private val accounts: MalAccountCredentialStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val key = MalMediaKey(
        mediaType = TrackingMediaType.valueOf(
            checkNotNull(savedStateHandle.get<String>("mediaType")),
        ),
        malId = checkNotNull(savedStateHandle.get<Long>("malId")),
    )
    private val _uiState = MutableStateFlow(MalDetailsUiState(key))
    val uiState: StateFlow<MalDetailsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeDetails(key).collectLatest { cached ->
                _uiState.update {
                    it.copy(
                        details = cached?.copy(
                            // The metadata cache is intentionally provider-global and strips the
                            // active account's list state. Preserve only the in-memory live value.
                            listState = it.details?.listState,
                        ),
                        loading = it.loading && cached == null,
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = it.details == null, error = null) }
            val account = accounts.activeAccount()
            if (account == null) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = MalApiFailure(MalApiFailureKind.NOT_AUTHENTICATED),
                    )
                }
                return@launch
            }
            when (val result = repository.refreshDetails(account.localAccountId, key)) {
                is MalApiResult.Success -> _uiState.update {
                    it.copy(details = result.value, loading = false, error = null)
                }
                is MalApiResult.Failure -> _uiState.update {
                    it.copy(loading = false, error = result.error)
                }
            }
        }
    }
}
