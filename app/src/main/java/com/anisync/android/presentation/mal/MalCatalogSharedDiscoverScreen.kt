package com.anisync.android.presentation.mal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisync.android.R
import com.anisync.android.presentation.model.ProviderMediaIdentity
import com.anisync.android.presentation.provider.discover.ProviderDiscoverContent
import com.anisync.android.presentation.provider.discover.ProviderDiscoverFailure
import com.anisync.android.presentation.provider.discover.ProviderDiscoverStrings

@Composable
fun MalCatalogSharedDiscoverScreen(
    onMediaClick: (ProviderMediaIdentity) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MalCatalogSharedDiscoverViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProviderDiscoverContent(
        state = state,
        strings = malDiscoverStrings(),
        onMediaTypeSelected = viewModel::selectMediaType,
        onFeedSelected = viewModel::selectFeed,
        onQueryChanged = viewModel::setQuery,
        onSearch = viewModel::submitSearch,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onMediaClick = onMediaClick,
        modifier = modifier,
    )
}

@Composable
private fun malDiscoverStrings(): ProviderDiscoverStrings = ProviderDiscoverStrings(
    title = stringResource(R.string.mal_shared_discover_title),
    anime = stringResource(R.string.mal_shared_discover_anime),
    manga = stringResource(R.string.mal_shared_discover_manga),
    top = stringResource(R.string.mal_shared_discover_top),
    popular = stringResource(R.string.mal_shared_discover_popular),
    currentSeason = stringResource(R.string.mal_shared_discover_current_season),
    searchLabel = stringResource(R.string.mal_shared_discover_search_label),
    searchAction = stringResource(R.string.mal_shared_discover_search_action),
    refreshAction = stringResource(R.string.mal_shared_discover_refresh),
    retryAction = stringResource(R.string.mal_shared_discover_retry),
    loadMoreAction = stringResource(R.string.mal_shared_discover_load_more),
    loading = stringResource(R.string.mal_shared_discover_loading),
    loadingMore = stringResource(R.string.mal_shared_discover_loading_more),
    empty = stringResource(R.string.mal_shared_discover_empty),
    stale = stringResource(R.string.mal_shared_discover_stale),
    failureMessages = mapOf(
        ProviderDiscoverFailure.AUTHENTICATION_REQUIRED to
            stringResource(R.string.mal_shared_error_authentication),
        ProviderDiscoverFailure.RATE_LIMITED to
            stringResource(R.string.mal_shared_error_rate_limited),
        ProviderDiscoverFailure.OFFLINE to stringResource(R.string.mal_shared_error_offline),
        ProviderDiscoverFailure.TIMEOUT to stringResource(R.string.mal_shared_error_timeout),
        ProviderDiscoverFailure.TEMPORARY to stringResource(R.string.mal_shared_error_temporary),
        ProviderDiscoverFailure.INVALID_RESPONSE to
            stringResource(R.string.mal_shared_error_invalid_response),
        ProviderDiscoverFailure.UNKNOWN to stringResource(R.string.mal_shared_error_unknown),
    ),
    genericFailure = stringResource(R.string.mal_shared_error_unknown),
)
