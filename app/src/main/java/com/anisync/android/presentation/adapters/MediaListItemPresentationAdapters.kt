package com.anisync.android.presentation.adapters

import com.anisync.android.data.TitleLanguage
import com.anisync.android.data.mal.api.MalLibraryItem
import com.anisync.android.domain.LibraryEntry
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.presentation.model.MediaListItemPresentation
import com.anisync.android.presentation.model.PresentationMediaType
import com.anisync.android.presentation.model.ProviderMediaIdentity
import com.anisync.android.type.MediaType
import com.anisync.android.util.getTitle

fun LibraryEntry.toMediaListItemPresentation(
    mediaType: MediaType,
    titleLanguage: TitleLanguage,
    resolvedCoverUrl: String? = coverUrl,
): MediaListItemPresentation = MediaListItemPresentation(
    identity = ProviderMediaIdentity.AniList(
        mediaId = mediaId,
        mediaType = mediaType.toPresentationMediaType(),
    ),
    title = getTitle(titleLanguage),
    coverUrl = resolvedCoverUrl,
    progress = progress,
    total = when (mediaType) {
        MediaType.ANIME -> totalEpisodes
        MediaType.MANGA -> totalChapters
        else -> null
    },
)

fun MalLibraryItem.toMediaListItemPresentation(): MediaListItemPresentation =
    MediaListItemPresentation(
        identity = ProviderMediaIdentity.MyAnimeList(
            malId = malId,
            mediaType = mediaType.toPresentationMediaType(),
        ),
        title = title,
        coverUrl = coverUrl,
        progress = state.progress,
        total = null,
    )

private fun MediaType.toPresentationMediaType(): PresentationMediaType = when (this) {
    MediaType.ANIME -> PresentationMediaType.ANIME
    MediaType.MANGA -> PresentationMediaType.MANGA
    else -> error("Unsupported media type for shared presentation: $this")
}

private fun TrackingMediaType.toPresentationMediaType(): PresentationMediaType = when (this) {
    TrackingMediaType.ANIME -> PresentationMediaType.ANIME
    TrackingMediaType.MANGA -> PresentationMediaType.MANGA
}
