package com.anisync.android.presentation.provider.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.anisync.android.R
import com.anisync.android.presentation.components.ProviderMediaListItem
import com.anisync.android.presentation.model.PresentationMediaType
import com.anisync.android.presentation.model.ProviderMediaIdentity

/**
 * Provider-neutral Library surface. MAL and AniList adapters supply typed items and callbacks; this
 * composable never imports provider transport models and never performs network work.
 */
@Composable
fun ProviderLibraryScreen(
    state: MalLibraryProviderUiState,
    onAction: (MalLibraryProviderAction) -> Unit,
    onOpenDetails: (ProviderMediaIdentity) -> Unit,
    onEdit: (ProviderLibraryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MediaTypeSelector(
            selected = snapshot.query.mediaType,
            onSelected = { onAction(MalLibraryProviderAction.SelectMediaType(it)) },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        OutlinedTextField(
            value = snapshot.query.searchQuery,
            onValueChange = { onAction(MalLibraryProviderAction.Search(it)) },
            label = { Text(stringResource(R.string.mal_library_search_hint)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        StatusSelector(
            selected = snapshot.query.statuses,
            onSelected = { onAction(MalLibraryProviderAction.FilterStatuses(it)) },
        )
        SortAndLayoutControls(
            query = snapshot.query,
            onAction = onAction,
        )
        state.lastFailure?.let { failure ->
            Text(
                text = stringResource(R.string.mal_library_error, failure.kind.name),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (snapshot.hasStaleContent) {
            Text(
                text = stringResource(R.string.mal_library_stale),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val grid = when (snapshot.query.layout) {
                ProviderLibraryLayout.GRID -> true
                ProviderLibraryLayout.LIST -> false
                ProviderLibraryLayout.ADAPTIVE -> maxWidth >= 600.dp
            }
            when {
                snapshot.isInitialLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                snapshot.visibleItems.isEmpty() -> EmptyLibraryState(
                    canRetry = state.lastFailure != null,
                    onRetry = { onAction(MalLibraryProviderAction.Refresh) },
                )
                grid -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 240.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    gridItems(
                        items = snapshot.visibleItems,
                        key = { it.identity.stableKey },
                    ) { item ->
                        ProviderLibraryItemCard(item, onOpenDetails, onEdit)
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = snapshot.visibleItems,
                        key = { it.identity.stableKey },
                    ) { item ->
                        ProviderLibraryItemCard(item, onOpenDetails, onEdit)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderLibraryItemCard(
    item: ProviderLibraryItem,
    onOpenDetails: (ProviderMediaIdentity) -> Unit,
    onEdit: (ProviderLibraryItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ProviderMediaListItem(
            item = item.card,
            onClick = onOpenDetails,
        )
        TextButton(
            onClick = { onEdit(item) },
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.mal_library_edit))
        }
    }
}

@Composable
private fun EmptyLibraryState(
    canRetry: Boolean,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.mal_library_empty),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (canRetry) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.mal_library_retry))
                }
            }
        }
    }
}

@Composable
private fun MediaTypeSelector(
    selected: PresentationMediaType,
    onSelected: (PresentationMediaType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PresentationMediaType.entries.forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelected(type) },
                label = {
                    Text(
                        stringResource(
                            if (type == PresentationMediaType.ANIME) {
                                R.string.mal_library_anime
                            } else {
                                R.string.mal_library_manga
                            }
                        )
                    )
                },
            )
        }
    }
}

@Composable
private fun StatusSelector(
    selected: Set<ProviderLibraryStatus>,
    onSelected: (Set<ProviderLibraryStatus>) -> Unit,
) {
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ProviderLibraryStatus.entries) { status ->
            FilterChip(
                selected = status in selected,
                onClick = {
                    onSelected(
                        if (status in selected) selected - status else selected + status
                    )
                },
                label = { Text(statusLabel(status)) },
            )
        }
    }
}

@Composable
private fun SortAndLayoutControls(
    query: ProviderLibraryQuery,
    onAction: (MalLibraryProviderAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(ProviderLibrarySort.entries) { sort ->
                FilterChip(
                    selected = query.sort == sort,
                    onClick = {
                        onAction(MalLibraryProviderAction.Sort(sort, query.ascending))
                    },
                    label = { Text(sortLabel(sort)) },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    onAction(MalLibraryProviderAction.Sort(query.sort, !query.ascending))
                },
            ) {
                Text(
                    stringResource(
                        if (query.ascending) {
                            R.string.mal_library_ascending
                        } else {
                            R.string.mal_library_descending
                        }
                    )
                )
            }
            ProviderLibraryLayout.entries.forEach { layout ->
                FilterChip(
                    selected = query.layout == layout,
                    onClick = { onAction(MalLibraryProviderAction.SetLayout(layout)) },
                    label = { Text(layoutLabel(layout)) },
                )
            }
            TextButton(onClick = { onAction(MalLibraryProviderAction.Refresh) }) {
                Text(stringResource(R.string.mal_library_refresh))
            }
        }
    }
}

@Composable
private fun statusLabel(status: ProviderLibraryStatus): String = stringResource(
    when (status) {
        ProviderLibraryStatus.CURRENT -> R.string.mal_library_status_current
        ProviderLibraryStatus.PLANNING -> R.string.mal_library_status_planning
        ProviderLibraryStatus.COMPLETED -> R.string.mal_library_status_completed
        ProviderLibraryStatus.DROPPED -> R.string.mal_library_status_dropped
        ProviderLibraryStatus.PAUSED -> R.string.mal_library_status_paused
        ProviderLibraryStatus.REPEATING -> R.string.mal_library_status_repeating
    }
)

@Composable
private fun sortLabel(sort: ProviderLibrarySort): String = stringResource(
    when (sort) {
        ProviderLibrarySort.TITLE -> R.string.mal_library_sort_title
        ProviderLibrarySort.PROGRESS -> R.string.mal_library_sort_progress
        ProviderLibrarySort.SCORE -> R.string.mal_library_sort_score
        ProviderLibrarySort.LAST_UPDATED -> R.string.mal_library_sort_updated
        ProviderLibrarySort.START_DATE -> R.string.mal_library_sort_start_date
    }
)

@Composable
private fun layoutLabel(layout: ProviderLibraryLayout): String = stringResource(
    when (layout) {
        ProviderLibraryLayout.GRID -> R.string.mal_library_layout_grid
        ProviderLibraryLayout.LIST -> R.string.mal_library_layout_list
        ProviderLibraryLayout.ADAPTIVE -> R.string.mal_library_layout_adaptive
    }
)
