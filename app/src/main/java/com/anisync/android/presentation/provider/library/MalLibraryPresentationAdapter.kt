package com.anisync.android.presentation.provider.library

import com.anisync.android.data.mal.api.MalLibraryPresentationRecord
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingStatus
import com.anisync.android.presentation.model.MediaListItemPresentation
import com.anisync.android.presentation.model.PresentationMediaType
import com.anisync.android.presentation.model.ProviderMediaIdentity

object MalLibraryPresentationAdapter {
    fun map(record: MalLibraryPresentationRecord): ProviderLibraryItem {
        val mediaType = record.mediaType.toPresentationMediaType()
        val status = requireNotNull(record.state.status).toProviderLibraryStatus()
        val identity = ProviderMediaIdentity.MyAnimeList(
            malId = record.malId,
            mediaType = mediaType,
        )
        return ProviderLibraryItem(
            card = MediaListItemPresentation(
                identity = identity,
                title = record.title,
                coverUrl = record.coverUrl,
                progress = record.state.progress,
                total = record.totalPrimary,
            ),
            alternativeTitles = record.alternativeTitles,
            status = status,
            progress = record.state.progress,
            total = record.totalPrimary,
            secondaryProgress = record.state.progressSecondary,
            secondaryTotal = record.totalSecondary,
            score100 = record.state.score100,
            repeatCount = record.state.repeatCount,
            startedAt = record.state.startedAt,
            completedAt = record.state.completedAt,
            mediaStartDate = record.mediaStartDate,
            providerUpdatedAtEpochMillis = record.providerUpdatedAtEpochMillis,
            fetchedAtEpochMillis = record.fetchedAtEpochMillis,
        )
    }

    fun snapshot(
        records: List<MalLibraryPresentationRecord>,
        query: ProviderLibraryQuery,
        isRefreshing: Boolean = false,
        errorMessage: String? = null,
        nowEpochMillis: Long = System.currentTimeMillis(),
        staleAfterMillis: Long = 24 * 60 * 60_000L,
    ): ProviderLibrarySnapshot = buildProviderLibrarySnapshot(
        items = records.map(::map),
        query = query,
        isRefreshing = isRefreshing,
        errorMessage = errorMessage,
        nowEpochMillis = nowEpochMillis,
        staleAfterMillis = staleAfterMillis,
    )
}

internal fun TrackingMediaType.toPresentationMediaType(): PresentationMediaType = when (this) {
    TrackingMediaType.ANIME -> PresentationMediaType.ANIME
    TrackingMediaType.MANGA -> PresentationMediaType.MANGA
}

internal fun PresentationMediaType.toTrackingMediaType(): TrackingMediaType = when (this) {
    PresentationMediaType.ANIME -> TrackingMediaType.ANIME
    PresentationMediaType.MANGA -> TrackingMediaType.MANGA
}

internal fun TrackingStatus.toProviderLibraryStatus(): ProviderLibraryStatus = when (this) {
    TrackingStatus.CURRENT -> ProviderLibraryStatus.CURRENT
    TrackingStatus.PLANNING -> ProviderLibraryStatus.PLANNING
    TrackingStatus.COMPLETED -> ProviderLibraryStatus.COMPLETED
    TrackingStatus.DROPPED -> ProviderLibraryStatus.DROPPED
    TrackingStatus.PAUSED -> ProviderLibraryStatus.PAUSED
    TrackingStatus.REPEATING -> ProviderLibraryStatus.REPEATING
}

internal fun ProviderLibraryStatus.toTrackingStatus(): TrackingStatus = when (this) {
    ProviderLibraryStatus.CURRENT -> TrackingStatus.CURRENT
    ProviderLibraryStatus.PLANNING -> TrackingStatus.PLANNING
    ProviderLibraryStatus.COMPLETED -> TrackingStatus.COMPLETED
    ProviderLibraryStatus.DROPPED -> TrackingStatus.DROPPED
    ProviderLibraryStatus.PAUSED -> TrackingStatus.PAUSED
    ProviderLibraryStatus.REPEATING -> TrackingStatus.REPEATING
}
