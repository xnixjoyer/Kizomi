package com.anisync.android.presentation.mal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anisync.android.data.mal.account.MalAccountCredentialStore
import com.anisync.android.data.mal.api.MalApiResult
import com.anisync.android.data.mal.api.MalCatalogRepository
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.presentation.model.PresentationMediaType
import com.anisync.android.presentation.provider.discover.ProviderDiscoverFeed
import com.anisync.android.presentation.provider.discover.ProviderDiscoverUiState
import com.anisync.android.presentation.provider.discover.beginAppend
import com.anisync.android.presentation.provider.discover.beginInitialLoad
import com.anisync.android.presentation.provider.discover.completeAppend
import com.anisync.android.presentation.provider.discover.completeInitialLoad
import com.anisync.android.presentation.provider.discover.failAppend
import com.anisync.android.presentation.provider.discover.failInitialLoad
import com.anisync.android.presentation.provider.discover.showCachedItems
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MalCatalogSharedDiscoverViewModel @Inject constructor(
    private val repository: MalCatalogRepository,
    private val accounts: MalAccountCredentialStore,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ProviderDiscoverUiState(
            mediaType = savedStateHandle.get<String>(KEY_MEDIA_TYPE)
                ?.let { runCatching { PresentationMediaType.valueOf(it) }.getOrNull() }
                ?: PresentationMediaType.ANIME,
            selectedFeed = savedStateHandle.get<String>(KEY_FEED)
                ?.let { runCatching { ProviderDiscoverFeed.valueOf(it) }.getOrNull() }
                ?: ProviderDiscoverFeed.TOP,
            query = savedStateHandle[KEY_QUERY] ?: "",
        ),
    )
    val uiState: StateFlow<ProviderDiscoverUiState> = _uiState.asStateFlow()

    private var requestJob: Job? = null
    private var nextPageUrl: String? = null

    init {
        if (!_uiState.value.supports(_uiState.value.selectedFeed)) {
            savedStateHandle[KEY_FEED] = ProviderDiscoverFeed.TOP.name
            _uiState.update { it.copy(selectedFeed = ProviderDiscoverFeed.TOP) }
        }
        loadInitial(clearContent = true, refreshing = false)
    }

    fun setQuery(value: String) {
        val bounded = value.take(MAX_QUERY_LENGTH)
        if (_uiState.value.query == bounded) return
        savedStateHandle[KEY_QUERY] = bounded
        _uiState.update { it.copy(query = bounded, failure = null) }
    }

    fun submitSearch() {
        val normalized = _uiState.value.query.trim()
        savedStateHandle[KEY_QUERY] = normalized
        _uiState.update { it.copy(query = normalized) }
        loadInitial(clearContent = true, refreshing = false)
    }

    fun selectMediaType(mediaType: PresentationMediaType) {
        if (_uiState.value.mediaType == mediaType) return
        val feed = if (
            mediaType == PresentationMediaType.MANGA &&
            _uiState.value.selectedFeed == ProviderDiscoverFeed.CURRENT_SEASON
        ) {
            ProviderDiscoverFeed.TOP
        } else {
            _uiState.value.selectedFeed
        }
        savedStateHandle[KEY_MEDIA_TYPE] = mediaType.name
        savedStateHandle[KEY_FEED] = feed.name
        savedStateHandle[KEY_QUERY] = ""
        _uiState.update {
            it.copy(
                mediaType = mediaType,
                selectedFeed = feed,
                query = "",
                items = emptyList(),
                failure = null,
                canLoadMore = false,
            )
        }
        loadInitial(clearContent = true, refreshing = false)
    }

    fun selectFeed(feed: ProviderDiscoverFeed) {
        if (!_uiState.value.supports(feed)) return
        if (_uiState.value.selectedFeed == feed && _uiState.value.query.isBlank()) return
        savedStateHandle[KEY_FEED] = feed.name
        savedStateHandle[KEY_QUERY] = ""
        _uiState.update {
            it.copy(
                selectedFeed = feed,
                query = "",
                items = emptyList(),
                failure = null,
                canLoadMore = false,
            )
        }
        loadInitial(clearContent = true, refreshing = false)
    }

    fun refresh() {
        loadInitial(clearContent = false, refreshing = true)
    }

    fun retry() {
        val hasContent = _uiState.value.items.isNotEmpty()
        loadInitial(clearContent = !hasContent, refreshing = hasContent)
    }

    fun loadMore() {
        val cursor = nextPageUrl ?: return
        val state = _uiState.value
        if (!state.canLoadMore || state.isLoadingMore) return
        val signature = state.signature()
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            _uiState.update { it.beginAppend() }
            val account = accounts.activeAccount()
            if (account == null) {
                _uiState.update {
                    if (it.signature() == signature) {
                        it.failAppend(
                            com.anisync.android.presentation.provider.discover
                                .ProviderDiscoverFailure.AUTHENTICATION_REQUIRED,
                        )
                    } else {
                        it
                    }
                }
                return@launch
            }
            when (
                val result = repository.nextPage(
                    account.localAccountId,
                    signature.mediaType.toTrackingMediaType(),
                    cursor,
                )
            ) {
                is MalApiResult.Success -> {
                    if (_uiState.value.signature() == signature) {
                        nextPageUrl = result.value.nextPageUrl
                        _uiState.update {
                            if (it.signature() == signature) {
                                it.completeAppend(
                                    loadedItems = result.value.entries.map { entry ->
                                        entry.toDiscoverPresentation()
                                    },
                                    canLoadMore = result.value.nextPageUrl != null,
                                )
                            } else {
                                it
                            }
                        }
                    }
                }
                is MalApiResult.Failure -> _uiState.update {
                    if (it.signature() == signature) {
                        it.failAppend(result.error.toProviderDiscoverFailure())
                    } else {
                        it
                    }
                }
            }
        }
    }

    private fun loadInitial(
        clearContent: Boolean,
        refreshing: Boolean,
    ) {
        requestJob?.cancel()
        nextPageUrl = null
        val signature = _uiState.value.signature()
        requestJob = viewModelScope.launch {
            _uiState.update {
                if (it.signature() == signature) {
                    it.beginInitialLoad(clearContent = clearContent, refreshing = refreshing)
                } else {
                    it
                }
            }
            if (signature.query.isNotBlank()) {
                val cached = repository.cachedSearch(
                    signature.mediaType.toTrackingMediaType(),
                    signature.query,
                ).map { it.toDiscoverPresentation() }
                _uiState.update {
                    if (it.signature() == signature) it.showCachedItems(cached) else it
                }
            }
            val account = accounts.activeAccount()
            if (account == null) {
                _uiState.update {
                    if (it.signature() == signature) {
                        it.failInitialLoad(
                            com.anisync.android.presentation.provider.discover
                                .ProviderDiscoverFailure.AUTHENTICATION_REQUIRED,
                        )
                    } else {
                        it
                    }
                }
                return@launch
            }
            val result = when {
                signature.query.isNotBlank() -> repository.search(
                    account.localAccountId,
                    signature.mediaType.toTrackingMediaType(),
                    signature.query,
                )
                signature.feed == ProviderDiscoverFeed.POPULAR -> repository.ranking(
                    account.localAccountId,
                    signature.mediaType.toTrackingMediaType(),
                    rankingType = POPULAR_RANKING_TYPE,
                )
                signature.feed == ProviderDiscoverFeed.CURRENT_SEASON -> {
                    val today = LocalDate.now()
                    repository.seasonal(
                        account.localAccountId,
                        today.year,
                        malSeasonForMonth(today.monthValue),
                    )
                }
                else -> repository.ranking(
                    account.localAccountId,
                    signature.mediaType.toTrackingMediaType(),
                )
            }
            when (result) {
                is MalApiResult.Success -> {
                    if (_uiState.value.signature() == signature) {
                        nextPageUrl = result.value.nextPageUrl
                        _uiState.update {
                            if (it.signature() == signature) {
                                it.completeInitialLoad(
                                    loadedItems = result.value.entries.map { entry ->
                                        entry.toDiscoverPresentation()
                                    },
                                    canLoadMore = result.value.nextPageUrl != null,
                                )
                            } else {
                                it
                            }
                        }
                    }
                }
                is MalApiResult.Failure -> _uiState.update {
                    if (it.signature() == signature) {
                        it.failInitialLoad(result.error.toProviderDiscoverFailure())
                    } else {
                        it
                    }
                }
            }
        }
    }

    private data class RequestSignature(
        val mediaType: PresentationMediaType,
        val feed: ProviderDiscoverFeed,
        val query: String,
    )

    private fun ProviderDiscoverUiState.signature(): RequestSignature = RequestSignature(
        mediaType = mediaType,
        feed = selectedFeed,
        query = query.trim(),
    )

    private fun PresentationMediaType.toTrackingMediaType(): TrackingMediaType = when (this) {
        PresentationMediaType.ANIME -> TrackingMediaType.ANIME
        PresentationMediaType.MANGA -> TrackingMediaType.MANGA
    }

    companion object {
        private const val KEY_MEDIA_TYPE = "mal_shared_discover_media_type"
        private const val KEY_FEED = "mal_shared_discover_feed"
        private const val KEY_QUERY = "mal_shared_discover_query"
        private const val POPULAR_RANKING_TYPE = "bypopularity"
        private const val MAX_QUERY_LENGTH = 200
    }
}
