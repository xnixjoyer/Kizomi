package com.anisync.android.presentation.provider.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.anisync.android.presentation.components.ProviderMediaListItem
import com.anisync.android.presentation.model.PresentationMediaType
import com.anisync.android.presentation.model.ProviderMediaIdentity

data class ProviderDiscoverStrings(
    val title: String,
    val anime: String,
    val manga: String,
    val top: String,
    val popular: String,
    val currentSeason: String,
    val searchLabel: String,
    val searchAction: String,
    val refreshAction: String,
    val retryAction: String,
    val loadMoreAction: String,
    val loading: String,
    val loadingMore: String,
    val empty: String,
    val stale: String,
    val failureMessages: Map<ProviderDiscoverFailure, String>,
    val genericFailure: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDiscoverContent(
    state: ProviderDiscoverUiState,
    strings: ProviderDiscoverStrings,
    onMediaTypeSelected: (PresentationMediaType) -> Unit,
    onFeedSelected: (ProviderDiscoverFeed) -> Unit,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onMediaClick: (ProviderMediaIdentity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = strings.refreshAction,
                )
            }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = state.mediaType == PresentationMediaType.ANIME,
                    onClick = { onMediaTypeSelected(PresentationMediaType.ANIME) },
                    label = { Text(strings.anime) },
                )
            }
            item {
                FilterChip(
                    selected = state.mediaType == PresentationMediaType.MANGA,
                    onClick = { onMediaTypeSelected(PresentationMediaType.MANGA) },
                    label = { Text(strings.manga) },
                )
            }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = state.selectedFeed == ProviderDiscoverFeed.TOP && !state.isSearchActive,
                    onClick = { onFeedSelected(ProviderDiscoverFeed.TOP) },
                    label = { Text(strings.top) },
                )
            }
            item {
                FilterChip(
                    selected = state.selectedFeed == ProviderDiscoverFeed.POPULAR &&
                        !state.isSearchActive,
                    onClick = { onFeedSelected(ProviderDiscoverFeed.POPULAR) },
                    label = { Text(strings.popular) },
                )
            }
            if (state.supports(ProviderDiscoverFeed.CURRENT_SEASON)) {
                item {
                    FilterChip(
                        selected = state.selectedFeed == ProviderDiscoverFeed.CURRENT_SEASON &&
                            !state.isSearchActive,
                        onClick = { onFeedSelected(ProviderDiscoverFeed.CURRENT_SEASON) },
                        label = { Text(strings.currentSeason) },
                    )
                }
            }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            label = { Text(strings.searchLabel) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            trailingIcon = {
                IconButton(onClick = onSearch) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = strings.searchAction,
                    )
                }
            },
        )
        if (state.isRefreshing || state.isInitialLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (state.isStale) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = strings.stale,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        when {
            state.isInitialLoading && state.items.isEmpty() -> CenteredMessage(
                text = strings.loading,
                modifier = Modifier.weight(1f),
            )
            state.failure != null && state.items.isEmpty() -> FailureMessage(
                text = strings.failureMessages[state.failure] ?: strings.genericFailure,
                retryLabel = strings.retryAction,
                onRetry = onRetry,
                modifier = Modifier.weight(1f),
            )
            state.isEmpty -> CenteredMessage(
                text = strings.empty,
                modifier = Modifier.weight(1f),
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.failure != null) {
                    item {
                        FailureMessage(
                            text = strings.failureMessages[state.failure] ?: strings.genericFailure,
                            retryLabel = strings.retryAction,
                            onRetry = onRetry,
                        )
                    }
                }
                items(state.items, key = { it.identity.stableKey }) { item ->
                    ProviderMediaListItem(
                        item = item,
                        onClick = onMediaClick,
                    )
                }
                if (state.canLoadMore || state.isLoadingMore) {
                    item {
                        Button(
                            onClick = onLoadMore,
                            enabled = !state.isLoadingMore,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (state.isLoadingMore) strings.loadingMore else strings.loadMoreAction,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FailureMessage(
    text: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onRetry) {
            Text(retryLabel)
        }
    }
}

@Composable
private fun CenteredMessage(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(24.dp),
        )
    }
}
