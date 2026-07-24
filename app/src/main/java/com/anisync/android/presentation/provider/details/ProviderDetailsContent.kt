package com.anisync.android.presentation.provider.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisync.android.presentation.model.ProviderMediaIdentity

data class ProviderDetailsStrings(
    val loading: String,
    val retryAction: String,
    val editListAction: String,
    val alternativeTitles: String,
    val listState: String,
    val synopsis: String,
    val facts: String,
    val statistics: String,
    val genres: String,
    val creators: String,
    val studios: String,
    val background: String,
    val relations: String,
    val recommendations: String,
    val format: String,
    val status: String,
    val startDate: String,
    val endDate: String,
    val episodes: String,
    val chapters: String,
    val volumes: String,
    val score: String,
    val rank: String,
    val popularity: String,
    val progress: String,
    val secondaryProgress: String,
    val poster: String,
    val openDetails: String,
    val stale: String,
    val failureMessages: Map<ProviderDetailsFailure, String>,
    val genericFailure: String,
)

@Composable
fun ProviderDetailsContent(
    state: ProviderDetailsUiState,
    strings: ProviderDetailsStrings,
    onRetry: () -> Unit,
    onRelatedClick: (ProviderMediaIdentity) -> Unit,
    onEditListEntry: ((ProviderMediaIdentity) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val details = state.details
    when {
        details == null && state.isLoading -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f))
                Text(strings.loading)
            }
        }
        details == null -> DetailsFailure(
            text = strings.failureMessages[state.failure] ?: strings.genericFailure,
            retryLabel = strings.retryAction,
            onRetry = onRetry,
            modifier = modifier.fillMaxSize(),
        )
        else -> ProviderDetailsBody(
            details = details,
            state = state,
            strings = strings,
            onRetry = onRetry,
            onRelatedClick = onRelatedClick,
            onEditListEntry = onEditListEntry,
            modifier = modifier,
        )
    }
}

@Composable
private fun ProviderDetailsBody(
    details: ProviderMediaDetailsPresentation,
    state: ProviderDetailsUiState,
    strings: ProviderDetailsStrings,
    onRetry: () -> Unit,
    onRelatedClick: (ProviderMediaIdentity) -> Unit,
    onEditListEntry: ((ProviderMediaIdentity) -> Unit)?,
    modifier: Modifier,
) {
    val sections = details.visibleSections(editAvailable = onEditListEntry != null)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            val hero = details.heroImageUrl ?: details.coverUrl
            if (hero != null) {
                AsyncImage(
                    model = hero,
                    contentDescription = "${strings.poster}: ${details.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                )
            }
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = details.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (state.isRefreshing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (state.isStale) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = strings.stale,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Button(onClick = onRetry) {
                                Text(strings.retryAction)
                            }
                        }
                    }
                }
                if (ProviderDetailsSection.ALTERNATIVE_TITLES in sections) {
                    DetailsTextSection(
                        title = strings.alternativeTitles,
                        text = details.alternativeTitles.joinToString(" · "),
                    )
                }
                if (ProviderDetailsSection.LIST_STATE in sections) {
                    ListStateSection(
                        details = details,
                        strings = strings,
                        onEditListEntry = onEditListEntry,
                    )
                }
                if (ProviderDetailsSection.FACTS in sections) {
                    DetailsTextSection(
                        title = strings.facts,
                        text = details.factLines(strings).joinToString("\n"),
                    )
                }
                if (ProviderDetailsSection.STATISTICS in sections) {
                    DetailsTextSection(
                        title = strings.statistics,
                        text = details.statisticLines(strings).joinToString(" · "),
                    )
                }
                if (ProviderDetailsSection.GENRES in sections) {
                    DetailsTextSection(
                        title = strings.genres,
                        text = details.genres.joinToString(" · "),
                    )
                }
                if (ProviderDetailsSection.CREDITS in sections) {
                    val credits = buildList {
                        if (details.creators.isNotEmpty()) {
                            add("${strings.creators}: ${details.creators.joinToString(" · ")}")
                        }
                        if (details.studios.isNotEmpty()) {
                            add("${strings.studios}: ${details.studios.joinToString(" · ")}")
                        }
                    }
                    DetailsTextSection(
                        title = strings.creators,
                        text = credits.joinToString("\n"),
                    )
                }
                if (ProviderDetailsSection.SYNOPSIS in sections) {
                    DetailsTextSection(
                        title = strings.synopsis,
                        text = requireNotNull(details.synopsis),
                    )
                }
                if (ProviderDetailsSection.BACKGROUND in sections) {
                    DetailsTextSection(
                        title = strings.background,
                        text = requireNotNull(details.background),
                    )
                }
            }
        }
        if (ProviderDetailsSection.RELATIONS in sections) {
            item {
                RelatedMediaRail(
                    title = strings.relations,
                    items = details.relations,
                    strings = strings,
                    onClick = onRelatedClick,
                )
            }
        }
        if (ProviderDetailsSection.RECOMMENDATIONS in sections) {
            item {
                RelatedMediaRail(
                    title = strings.recommendations,
                    items = details.recommendations,
                    strings = strings,
                    onClick = onRelatedClick,
                )
            }
        }
    }
}

@Composable
private fun ListStateSection(
    details: ProviderMediaDetailsPresentation,
    strings: ProviderDetailsStrings,
    onEditListEntry: ((ProviderMediaIdentity) -> Unit)?,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = strings.listState,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            details.listState?.let { listState ->
                listState.status?.takeIf(String::isNotBlank)?.let { status ->
                    Text(status)
                }
                Text("${strings.progress}: ${listState.progress}")
                listState.secondaryProgress?.let { secondary ->
                    Text("${strings.secondaryProgress}: $secondary")
                }
                listState.score100?.let { score ->
                    Text("${strings.score}: $score")
                }
            }
            if (onEditListEntry != null) {
                Button(onClick = { onEditListEntry(details.identity) }) {
                    Text(strings.editListAction)
                }
            }
        }
    }
}

@Composable
private fun DetailsTextSection(
    title: String,
    text: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun RelatedMediaRail(
    title: String,
    items: List<ProviderRelatedMediaPresentation>,
    strings: ProviderDetailsStrings,
    onClick: (ProviderMediaIdentity) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.identity.stableKey }) { related ->
                Card(
                    modifier = Modifier
                        .width(150.dp)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "${strings.openDetails}: ${related.title}",
                            onClick = { onClick(related.identity) },
                        ),
                ) {
                    AsyncImage(
                        model = related.coverUrl,
                        contentDescription = "${strings.poster}: ${related.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.7f),
                    )
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = related.title,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                        )
                        related.relationship?.takeIf(String::isNotBlank)?.let { relationship ->
                            Text(
                                text = relationship,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsFailure(
    text: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = onRetry) {
                Text(retryLabel)
            }
        }
    }
}

private fun ProviderMediaDetailsPresentation.factLines(
    strings: ProviderDetailsStrings,
): List<String> = buildList {
    format?.takeIf(String::isNotBlank)?.let { add("${strings.format}: $it") }
    status?.takeIf(String::isNotBlank)?.let { add("${strings.status}: $it") }
    startDate?.takeIf(String::isNotBlank)?.let { add("${strings.startDate}: $it") }
    endDate?.takeIf(String::isNotBlank)?.let { add("${strings.endDate}: $it") }
    episodeCount?.let { add("${strings.episodes}: $it") }
    chapterCount?.let { add("${strings.chapters}: $it") }
    volumeCount?.let { add("${strings.volumes}: $it") }
}

private fun ProviderMediaDetailsPresentation.statisticLines(
    strings: ProviderDetailsStrings,
): List<String> = buildList {
    score?.let { add("${strings.score}: $it") }
    rank?.let { add("${strings.rank}: #$it") }
    popularity?.let { add("${strings.popularity}: #$it") }
}
