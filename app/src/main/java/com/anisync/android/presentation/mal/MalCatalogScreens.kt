package com.anisync.android.presentation.mal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.anisync.android.R
import com.anisync.android.data.mal.api.MalCatalogMedia
import com.anisync.android.data.mal.api.MalMediaKey
import com.anisync.android.domain.tracking.TrackingMediaType
import java.util.Locale

internal fun malCatalogCardMinWidthDp(fontScale: Float): Int =
    (144f * fontScale.coerceIn(1f, 1.4f)).toInt().coerceIn(144, 202)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MalCatalogScreen(
    onBackClick: () -> Unit,
    onMediaClick: (MalMediaKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MalCatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cardWidth = malCatalogCardMinWidthDp(LocalDensity.current.fontScale).dp
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mal_catalog_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.mediaType == TrackingMediaType.ANIME,
                        onClick = { viewModel.setMediaType(TrackingMediaType.ANIME) },
                        label = { Text(stringResource(R.string.mal_catalog_anime)) },
                    )
                }
                item {
                    FilterChip(
                        selected = state.mediaType == TrackingMediaType.MANGA,
                        onClick = { viewModel.setMediaType(TrackingMediaType.MANGA) },
                        label = { Text(stringResource(R.string.mal_catalog_manga)) },
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
                        selected = state.discoveryMode == MalDiscoveryMode.RANKING &&
                            state.query.isBlank(),
                        onClick = { viewModel.selectDiscovery(MalDiscoveryMode.RANKING) },
                        label = { Text(stringResource(R.string.mal_catalog_ranking_chip)) },
                    )
                }
                if (state.mediaType == TrackingMediaType.ANIME) {
                    item {
                        FilterChip(
                            selected = state.discoveryMode == MalDiscoveryMode.SEASONAL &&
                                state.query.isBlank(),
                            onClick = { viewModel.selectDiscovery(MalDiscoveryMode.SEASONAL) },
                            label = { Text(stringResource(R.string.mal_catalog_seasonal_chip)) },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text(stringResource(R.string.mal_catalog_search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.submit() }),
                trailingIcon = {
                    IconButton(onClick = viewModel::submit) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.mal_catalog_search_action),
                        )
                    }
                },
            )
            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (state.showingOfflineCache) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        stringResource(R.string.mal_catalog_cached_notice),
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            when {
                state.entries.isNotEmpty() -> {
                    Text(
                        text = when (state.source) {
                            MalCatalogContentSource.RANKING ->
                                stringResource(R.string.mal_catalog_ranking)
                            MalCatalogContentSource.SEASONAL ->
                                stringResource(R.string.mal_catalog_seasonal)
                            MalCatalogContentSource.SEARCH,
                            MalCatalogContentSource.CACHE ->
                                stringResource(R.string.mal_catalog_search_action)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(cardWidth),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.entries, key = { it.key.stableValue }) { media ->
                            MalCatalogCard(media, onClick = { onMediaClick(media.key) })
                        }
                    }
                }
                state.error != null && !state.loading -> MalErrorState(
                    errorKind = state.error?.kind?.name ?: "UNKNOWN",
                    onRetry = viewModel::retry,
                    modifier = Modifier.weight(1f),
                )
                !state.loading -> Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.mal_catalog_empty))
                }
            }
        }
    }
}

@Composable
private fun MalCatalogCard(
    media: MalCatalogMedia,
    onClick: () -> Unit,
) {
    val clickLabel = stringResource(R.string.mal_catalog_open_details, media.title)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = clickLabel,
                onClick = onClick,
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        AsyncImage(
            model = media.pictureLarge ?: media.pictureMedium,
            contentDescription = stringResource(R.string.mal_catalog_poster, media.title),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f),
        )
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = media.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            val metadata = listOfNotNull(
                media.mediaFormat?.replace('_', ' ')?.uppercase(Locale.ROOT),
                media.meanScore?.let { String.format(Locale.ROOT, "%.1f", it) },
                media.rankingPosition?.let { "#$it" },
            ).joinToString(" · ")
            if (metadata.isNotEmpty()) {
                Text(
                    metadata,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MalDetailsScreen(
    onBackClick: () -> Unit,
    onRelatedClick: (MalMediaKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MalDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mal_details_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.details != null -> MalDetailsContent(
                details = requireNotNull(state.details),
                errorKind = state.error?.kind?.name,
                onRetry = viewModel::refresh,
                onRelatedClick = onRelatedClick,
                modifier = Modifier.padding(innerPadding),
            )
            state.loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f))
            }
            else -> MalErrorState(
                errorKind = state.error?.kind?.name ?: "UNKNOWN",
                onRetry = viewModel::refresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }
}

@Composable
private fun MalDetailsContent(
    details: MalCatalogMedia,
    errorKind: String?,
    onRetry: () -> Unit,
    onRelatedClick: (MalMediaKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            AsyncImage(
                model = details.pictureGallery.firstOrNull()
                    ?: details.pictureLarge
                    ?: details.pictureMedium,
                contentDescription = stringResource(R.string.mal_catalog_poster, details.title),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
            )
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(details.title, style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    details.meanScore?.let {
                        Text(stringResource(R.string.mal_details_score, it.toString()))
                    }
                    details.rank?.let {
                        Text(stringResource(R.string.mal_details_rank, it))
                    }
                }
                val facts = listOfNotNull(
                    details.mediaFormat?.replace('_', ' ')?.uppercase(Locale.ROOT),
                    details.mediaStatus?.replace('_', ' ')?.uppercase(Locale.ROOT),
                    details.episodeCount?.let {
                        stringResource(R.string.mal_details_episodes, it)
                    },
                    details.chapterCount?.let {
                        stringResource(R.string.mal_details_chapters, it)
                    },
                    details.volumeCount?.let {
                        stringResource(R.string.mal_details_volumes, it)
                    },
                ).joinToString(" · ")
                if (facts.isNotEmpty()) {
                    Text(
                        facts,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (details.genres.isNotEmpty()) {
                    Text(
                        details.genres.joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                details.listState?.let { listState ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(listState.status?.name.orEmpty(), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.mal_details_progress, listState.progress))
                        }
                    }
                }
                Text(
                    details.synopsis ?: stringResource(R.string.mal_details_synopsis_unavailable),
                    style = MaterialTheme.typography.bodyLarge,
                )
                details.background?.takeIf(String::isNotBlank)?.let { background ->
                    Text(
                        stringResource(R.string.mal_details_background),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(background, style = MaterialTheme.typography.bodyLarge)
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        stringResource(R.string.mal_details_anilist_unavailable),
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (errorKind != null) {
                    Text(
                        stringResource(R.string.mal_catalog_error, errorKind),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = onRetry) {
                        Text(stringResource(R.string.mal_catalog_retry))
                    }
                }
            }
        }
        val extraPictures = details.pictureGallery.drop(1)
        if (extraPictures.isNotEmpty()) {
            item {
                MalPictureRail(details.title, extraPictures)
            }
        }
        if (details.related.isNotEmpty()) {
            item {
                MalRelatedRail(
                    title = stringResource(R.string.mal_details_related),
                    items = details.related,
                    onClick = onRelatedClick,
                )
            }
        }
        if (details.recommendations.isNotEmpty()) {
            item {
                MalRelatedRail(
                    title = stringResource(R.string.mal_details_recommendations),
                    items = details.recommendations,
                    onClick = onRelatedClick,
                )
            }
        }
    }
}

@Composable
private fun MalPictureRail(
    title: String,
    pictures: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.mal_details_pictures),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(pictures, key = { it }) { picture ->
                AsyncImage(
                    model = picture,
                    contentDescription = stringResource(R.string.mal_catalog_poster, title),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(180.dp)
                        .height(260.dp),
                )
            }
        }
    }
}

@Composable
private fun MalRelatedRail(
    title: String,
    items: List<com.anisync.android.data.mal.api.MalRelatedMedia>,
    onClick: (MalMediaKey) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.key.stableValue }) { related ->
                Card(
                    modifier = Modifier
                        .width(150.dp)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(
                                R.string.mal_catalog_open_details,
                                related.title,
                            ),
                            onClick = { onClick(related.key) },
                        ),
                ) {
                    AsyncImage(
                        model = related.pictureUrl,
                        contentDescription = stringResource(
                            R.string.mal_catalog_poster,
                            related.title,
                        ),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.7f),
                    )
                    Text(
                        related.title,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        }
        Spacer(Modifier.size(4.dp))
    }
}

@Composable
private fun MalErrorState(
    errorKind: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                stringResource(R.string.mal_catalog_error, errorKind),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onRetry) {
                Text(stringResource(R.string.mal_catalog_retry))
            }
        }
    }
}
