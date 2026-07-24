package com.anisync.android.presentation.provider.discover

import com.anisync.android.presentation.model.MediaListItemPresentation
import com.anisync.android.presentation.model.PresentationMediaType

enum class ProviderDiscoverFeed {
    TOP,
    POPULAR,
    CURRENT_SEASON,
}

enum class ProviderDiscoverFailure {
    AUTHENTICATION_REQUIRED,
    RATE_LIMITED,
    OFFLINE,
    TIMEOUT,
    TEMPORARY,
    INVALID_RESPONSE,
    UNKNOWN,
}

data class ProviderDiscoverUiState(
    val mediaType: PresentationMediaType = PresentationMediaType.ANIME,
    val selectedFeed: ProviderDiscoverFeed = ProviderDiscoverFeed.TOP,
    val query: String = "",
    val items: List<MediaListItemPresentation> = emptyList(),
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isStale: Boolean = false,
    val canLoadMore: Boolean = false,
    val failure: ProviderDiscoverFailure? = null,
) {
    val isSearchActive: Boolean
        get() = query.isNotBlank()

    val isEmpty: Boolean
        get() = !isInitialLoading && items.isEmpty() && failure == null

    fun supports(feed: ProviderDiscoverFeed): Boolean =
        feed != ProviderDiscoverFeed.CURRENT_SEASON || mediaType == PresentationMediaType.ANIME
}

fun ProviderDiscoverUiState.beginInitialLoad(
    clearContent: Boolean,
    refreshing: Boolean,
): ProviderDiscoverUiState = copy(
    items = if (clearContent) emptyList() else items,
    isInitialLoading = clearContent || items.isEmpty(),
    isRefreshing = refreshing && items.isNotEmpty(),
    isLoadingMore = false,
    isStale = false,
    canLoadMore = if (clearContent) false else canLoadMore,
    failure = null,
)

fun ProviderDiscoverUiState.showCachedItems(
    cachedItems: List<MediaListItemPresentation>,
): ProviderDiscoverUiState = if (cachedItems.isEmpty()) {
    this
} else {
    copy(
        items = cachedItems.distinctBy { it.identity.stableKey },
        isInitialLoading = false,
        isStale = true,
        failure = null,
    )
}

fun ProviderDiscoverUiState.completeInitialLoad(
    loadedItems: List<MediaListItemPresentation>,
    canLoadMore: Boolean,
): ProviderDiscoverUiState = copy(
    items = loadedItems.distinctBy { it.identity.stableKey },
    isInitialLoading = false,
    isRefreshing = false,
    isLoadingMore = false,
    isStale = false,
    canLoadMore = canLoadMore,
    failure = null,
)

fun ProviderDiscoverUiState.failInitialLoad(
    failure: ProviderDiscoverFailure,
): ProviderDiscoverUiState = copy(
    isInitialLoading = false,
    isRefreshing = false,
    isLoadingMore = false,
    isStale = items.isNotEmpty(),
    failure = failure,
)

fun ProviderDiscoverUiState.beginAppend(): ProviderDiscoverUiState =
    if (!canLoadMore || isLoadingMore) {
        this
    } else {
        copy(isLoadingMore = true, failure = null)
    }

fun ProviderDiscoverUiState.completeAppend(
    loadedItems: List<MediaListItemPresentation>,
    canLoadMore: Boolean,
): ProviderDiscoverUiState = copy(
    items = (items + loadedItems).distinctBy { it.identity.stableKey },
    isLoadingMore = false,
    isStale = false,
    canLoadMore = canLoadMore,
    failure = null,
)

fun ProviderDiscoverUiState.failAppend(
    failure: ProviderDiscoverFailure,
): ProviderDiscoverUiState = copy(
    isLoadingMore = false,
    failure = failure,
)
