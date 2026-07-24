package com.anisync.android.presentation.mal

import com.anisync.android.data.mal.api.MalApiFailure
import com.anisync.android.data.mal.api.MalApiFailureKind
import com.anisync.android.data.mal.api.MalCatalogMedia
import com.anisync.android.data.mal.api.MalMediaKey
import com.anisync.android.data.mal.api.MalRelatedMedia
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.presentation.model.MediaListItemPresentation
import com.anisync.android.presentation.model.PresentationMediaType
import com.anisync.android.presentation.model.ProviderMediaIdentity
import com.anisync.android.presentation.provider.details.ProviderDetailsFailure
import com.anisync.android.presentation.provider.details.ProviderDetailsListState
import com.anisync.android.presentation.provider.details.ProviderDetailsUiState
import com.anisync.android.presentation.provider.details.ProviderMediaDetailsPresentation
import com.anisync.android.presentation.provider.details.ProviderRelatedMediaPresentation
import com.anisync.android.presentation.provider.discover.ProviderDiscoverFailure

fun MalMediaKey.toProviderMediaIdentity(): ProviderMediaIdentity.MyAnimeList =
    ProviderMediaIdentity.MyAnimeList(
        malId = malId,
        mediaType = mediaType.toPresentationMediaType(),
    )

fun MalCatalogMedia.toDiscoverPresentation(): MediaListItemPresentation =
    MediaListItemPresentation(
        identity = key.toProviderMediaIdentity(),
        title = title,
        coverUrl = pictureLarge ?: pictureMedium,
        progress = listState?.progress,
        total = when (key.mediaType) {
            TrackingMediaType.ANIME -> episodeCount
            TrackingMediaType.MANGA -> chapterCount
        },
    )

fun MalCatalogMedia.toDetailsPresentation(): ProviderMediaDetailsPresentation =
    ProviderMediaDetailsPresentation(
        identity = key.toProviderMediaIdentity(),
        title = title,
        alternativeTitles = alternativeTitles
            .map(String::trim)
            .filter(String::isNotEmpty)
            .filterNot { it.equals(title, ignoreCase = true) }
            .distinct(),
        coverUrl = pictureLarge ?: pictureMedium,
        heroImageUrl = pictureGallery.firstOrNull() ?: pictureLarge ?: pictureMedium,
        synopsis = synopsis?.trim()?.takeIf(String::isNotEmpty),
        background = background?.trim()?.takeIf(String::isNotEmpty),
        format = mediaFormat.toDisplayLabel(),
        status = mediaStatus.toDisplayLabel(),
        startDate = startDate,
        endDate = endDate,
        episodeCount = episodeCount,
        chapterCount = chapterCount,
        volumeCount = volumeCount,
        score = meanScore,
        rank = rank,
        popularity = popularity,
        genres = genres.map(String::trim).filter(String::isNotEmpty).distinct(),
        creators = emptyList(),
        studios = emptyList(),
        listState = listState?.let { state ->
            ProviderDetailsListState(
                status = state.status?.name?.toDisplayLabel(),
                progress = state.progress,
                secondaryProgress = state.progressSecondary,
                score100 = state.score100,
            )
        },
        relations = related.map(MalRelatedMedia::toDetailsPresentation),
        recommendations = recommendations.map(MalRelatedMedia::toDetailsPresentation),
    )

fun MalDetailsUiState.toProviderDetailsUiState(): ProviderDetailsUiState =
    ProviderDetailsUiState(
        details = details?.toDetailsPresentation(),
        isLoading = loading,
        isRefreshing = loading && details != null,
        failure = when {
            routeError != null -> ProviderDetailsFailure.INVALID_IDENTITY
            error != null -> error.toProviderDetailsFailure()
            else -> null
        },
    )

fun MalApiFailure.toProviderDiscoverFailure(): ProviderDiscoverFailure = when (kind) {
    MalApiFailureKind.ACCOUNT_NOT_FOUND,
    MalApiFailureKind.NOT_AUTHENTICATED,
    MalApiFailureKind.RELOGIN_REQUIRED,
    -> ProviderDiscoverFailure.AUTHENTICATION_REQUIRED
    MalApiFailureKind.RATE_LIMITED -> ProviderDiscoverFailure.RATE_LIMITED
    MalApiFailureKind.OFFLINE -> ProviderDiscoverFailure.OFFLINE
    MalApiFailureKind.TIMEOUT -> ProviderDiscoverFailure.TIMEOUT
    MalApiFailureKind.TRANSPORT,
    MalApiFailureKind.TRANSIENT_SERVER,
    -> ProviderDiscoverFailure.TEMPORARY
    MalApiFailureKind.INVALID_RESPONSE,
    MalApiFailureKind.INVALID_PAGING_URL,
    MalApiFailureKind.PAGING_LOOP,
    -> ProviderDiscoverFailure.INVALID_RESPONSE
    MalApiFailureKind.LIMIT_EXCEEDED,
    MalApiFailureKind.PERMANENT,
    MalApiFailureKind.STORAGE,
    MalApiFailureKind.CANCELLED,
    -> ProviderDiscoverFailure.UNKNOWN
}

fun MalApiFailure.toProviderDetailsFailure(): ProviderDetailsFailure = when (kind) {
    MalApiFailureKind.ACCOUNT_NOT_FOUND,
    MalApiFailureKind.NOT_AUTHENTICATED,
    MalApiFailureKind.RELOGIN_REQUIRED,
    -> ProviderDetailsFailure.AUTHENTICATION_REQUIRED
    MalApiFailureKind.RATE_LIMITED -> ProviderDetailsFailure.RATE_LIMITED
    MalApiFailureKind.OFFLINE -> ProviderDetailsFailure.OFFLINE
    MalApiFailureKind.TIMEOUT -> ProviderDetailsFailure.TIMEOUT
    MalApiFailureKind.TRANSPORT,
    MalApiFailureKind.TRANSIENT_SERVER,
    -> ProviderDetailsFailure.TEMPORARY
    MalApiFailureKind.INVALID_RESPONSE,
    MalApiFailureKind.INVALID_PAGING_URL,
    MalApiFailureKind.PAGING_LOOP,
    -> ProviderDetailsFailure.INVALID_RESPONSE
    MalApiFailureKind.LIMIT_EXCEEDED,
    MalApiFailureKind.PERMANENT,
    MalApiFailureKind.STORAGE,
    MalApiFailureKind.CANCELLED,
    -> ProviderDetailsFailure.UNKNOWN
}

private fun MalRelatedMedia.toDetailsPresentation(): ProviderRelatedMediaPresentation =
    ProviderRelatedMediaPresentation(
        identity = key.toProviderMediaIdentity(),
        title = title,
        coverUrl = pictureUrl,
        relationship = relationship.toDisplayLabel(),
    )

private fun TrackingMediaType.toPresentationMediaType(): PresentationMediaType = when (this) {
    TrackingMediaType.ANIME -> PresentationMediaType.ANIME
    TrackingMediaType.MANGA -> PresentationMediaType.MANGA
}

private fun String?.toDisplayLabel(): String? = this
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.replace('_', ' ')
    ?.lowercase()
    ?.replaceFirstChar { it.titlecase() }
