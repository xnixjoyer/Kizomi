package com.anisync.android.presentation.calendar.provider

import com.anisync.android.domain.calendar.provider.ProviderCalendarCapability
import com.anisync.android.domain.calendar.provider.ProviderCalendarEntry
import com.anisync.android.domain.calendar.provider.ProviderCalendarLoadResult
import com.anisync.android.domain.calendar.provider.ProviderCalendarNotice
import com.anisync.android.domain.calendar.provider.ProviderCalendarPrecision
import com.anisync.android.domain.calendar.provider.ProviderCalendarUnavailableReason
import com.anisync.android.domain.provider.ActiveProvider
import com.anisync.android.presentation.model.PresentationMediaType
import com.anisync.android.presentation.model.ProviderMediaIdentity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ProviderCalendarPresentationItem(
    val identity: ProviderMediaIdentity,
    val title: String,
    val coverUrl: String?,
    val scheduledAtEpochSeconds: Long,
    val episodeNumber: Int?,
    val isOnList: Boolean,
    val precision: ProviderCalendarPrecision,
)

data class ProviderCalendarPresentationDay(
    val date: LocalDate,
    val entries: List<ProviderCalendarPresentationItem>,
)

sealed interface ProviderCalendarPresentationState {
    data class Content(
        val days: List<ProviderCalendarPresentationDay>,
        val capabilities: Set<ProviderCalendarCapability>,
        val notices: Set<ProviderCalendarNotice>,
        val fetchedAtEpochMillis: Long,
    ) : ProviderCalendarPresentationState

    data class Unavailable(
        val reason: ProviderCalendarUnavailableReason,
    ) : ProviderCalendarPresentationState

    data class Error(
        val retryable: Boolean,
        val retryAfterMillis: Long?,
    ) : ProviderCalendarPresentationState
}

object ProviderCalendarPresentationMapper {
    fun map(
        result: ProviderCalendarLoadResult,
        zoneId: ZoneId,
    ): ProviderCalendarPresentationState = when (result) {
        is ProviderCalendarLoadResult.Unavailable ->
            ProviderCalendarPresentationState.Unavailable(result.reason)
        is ProviderCalendarLoadResult.Failure ->
            ProviderCalendarPresentationState.Error(result.retryable, result.retryAfterMillis)
        is ProviderCalendarLoadResult.Content -> {
            val mapped = result.entries.mapNotNull(::mapEntry)
            val days = mapped
                .groupBy { item ->
                    Instant.ofEpochSecond(item.scheduledAtEpochSeconds)
                        .atZone(zoneId)
                        .toLocalDate()
                }
                .toSortedMap()
                .map { (date, entries) ->
                    ProviderCalendarPresentationDay(
                        date = date,
                        entries = entries.sortedBy(ProviderCalendarPresentationItem::scheduledAtEpochSeconds),
                    )
                }
            ProviderCalendarPresentationState.Content(
                days = days,
                capabilities = result.capabilities,
                notices = result.notices,
                fetchedAtEpochMillis = result.fetchedAtEpochMillis,
            )
        }
    }

    private fun mapEntry(entry: ProviderCalendarEntry): ProviderCalendarPresentationItem? {
        val identity = when (entry.provider) {
            ActiveProvider.MAL_ONLY -> ProviderMediaIdentity.MyAnimeList(
                malId = entry.providerMediaId,
                mediaType = entry.mediaType.toPresentationType(),
            )
            ActiveProvider.ANILIST_ONLY -> {
                if (entry.providerMediaId > Int.MAX_VALUE) return null
                ProviderMediaIdentity.AniList(
                    mediaId = entry.providerMediaId.toInt(),
                    mediaType = entry.mediaType.toPresentationType(),
                )
            }
            ActiveProvider.UNCONFIGURED -> return null
        }
        return ProviderCalendarPresentationItem(
            identity = identity,
            title = entry.title,
            coverUrl = entry.coverUrl,
            scheduledAtEpochSeconds = entry.scheduledAtEpochSeconds,
            episodeNumber = entry.episodeNumber,
            isOnList = entry.isOnList,
            precision = entry.precision,
        )
    }

    private fun com.anisync.android.domain.calendar.provider.ProviderCalendarMediaType.toPresentationType():
        PresentationMediaType = when (this) {
        com.anisync.android.domain.calendar.provider.ProviderCalendarMediaType.ANIME ->
            PresentationMediaType.ANIME
        com.anisync.android.domain.calendar.provider.ProviderCalendarMediaType.MANGA ->
            PresentationMediaType.MANGA
    }
}
