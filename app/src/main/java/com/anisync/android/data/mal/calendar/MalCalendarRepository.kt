package com.anisync.android.data.mal.calendar

import com.anisync.android.data.mal.api.MalApiFailure
import com.anisync.android.data.mal.api.MalApiResult
import com.anisync.android.data.mal.api.MalSeason
import com.anisync.android.domain.calendar.provider.ProviderCalendarCapability
import com.anisync.android.domain.calendar.provider.ProviderCalendarEntry
import com.anisync.android.domain.calendar.provider.ProviderCalendarLoadResult
import com.anisync.android.domain.calendar.provider.ProviderCalendarMediaType
import com.anisync.android.domain.calendar.provider.ProviderCalendarNotice
import com.anisync.android.domain.calendar.provider.ProviderCalendarPrecision
import com.anisync.android.domain.calendar.provider.ProviderCalendarQuery
import com.anisync.android.domain.calendar.provider.ProviderCalendarSession
import com.anisync.android.domain.calendar.provider.ProviderCalendarSource
import com.anisync.android.domain.calendar.provider.ProviderCalendarUnavailableReason
import com.anisync.android.domain.provider.ActiveProvider
import kotlinx.coroutines.sync.Mutex
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MalCalendarRepository internal constructor(
    private val api: MalCalendarApi,
    private val nowEpochMillis: () -> Long,
) : ProviderCalendarSource {
    @Inject
    constructor(api: MalCalendarApi) : this(api, System::currentTimeMillis)

    override val provider: ActiveProvider = ActiveProvider.MAL_ONLY
    private val mutex = Mutex()
    private var cache: CacheEntry? = null

    override suspend fun load(
        session: ProviderCalendarSession,
        query: ProviderCalendarQuery,
        forceRefresh: Boolean,
    ): ProviderCalendarLoadResult {
        if (!session.providerTrafficAllowed || session.runtimeProvider != ActiveProvider.MAL_ONLY) {
            return ProviderCalendarLoadResult.Unavailable(
                if (session.runtimeProvider == ActiveProvider.UNCONFIGURED) {
                    ProviderCalendarUnavailableReason.UNCONFIGURED
                } else {
                    ProviderCalendarUnavailableReason.PROVIDER_UNSUPPORTED
                }
            )
        }
        val accountKey = session.accountKey
            ?: return ProviderCalendarLoadResult.Unavailable(
                ProviderCalendarUnavailableReason.AUTHENTICATION_REQUIRED
            )
        if (query.endEpochSeconds - query.startEpochSeconds > MAX_QUERY_SECONDS) {
            return ProviderCalendarLoadResult.Failure(
                reason = "calendar_range_too_large",
                retryable = false,
            )
        }
        if (runCatching { ZoneId.of(query.zoneId) }.isFailure) {
            return ProviderCalendarLoadResult.Failure(
                reason = "invalid_calendar_zone",
                retryable = false,
            )
        }

        mutex.lock()
        return try {
            val now = nowEpochMillis()
            val cached = cache
            if (!forceRefresh && cached != null &&
                cached.accountKey == accountKey &&
                cached.query == query &&
                now - cached.savedAtEpochMillis < CACHE_TTL_MILLIS
            ) {
                cached.result
            } else {
                val result = loadFresh(accountKey, query, now)
                if (result is ProviderCalendarLoadResult.Content) {
                    cache = CacheEntry(accountKey, query, now, result)
                }
                result
            }
        } finally {
            mutex.unlock()
        }
    }

    suspend fun purgeMemory() {
        mutex.lock()
        try {
            cache = null
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun loadFresh(
        accountKey: String,
        query: ProviderCalendarQuery,
        fetchedAt: Long,
    ): ProviderCalendarLoadResult {
        val allMedia = mutableListOf<MalCalendarMedia>()
        val notices = linkedSetOf(
            ProviderCalendarNotice.EXACT_EPISODE_SCHEDULE_UNAVAILABLE,
            ProviderCalendarNotice.AIRING_NOTIFICATIONS_UNAVAILABLE,
            ProviderCalendarNotice.RECURRING_SLOT_MAY_CHANGE,
        )
        var firstFailure: MalApiFailure? = null

        for (season in seasonsFor(query)) {
            val seasonFetch = fetchSeason(accountKey, season)
            allMedia += seasonFetch.entries
            if (seasonFetch.failure != null || seasonFetch.truncated) {
                if (firstFailure == null) firstFailure = seasonFetch.failure
                notices += ProviderCalendarNotice.PARTIAL_PROVIDER_RESPONSE
            }
        }

        if (allMedia.isEmpty() && firstFailure != null) {
            return ProviderCalendarLoadResult.Failure(
                reason = firstFailure.kind.name,
                retryable = firstFailure.retryable,
                retryAfterMillis = firstFailure.retryAfterMillis,
            )
        }

        val metadataStates = allMedia.map { it.broadcastMetadataState() }
        val completeMetadataCount = metadataStates.count { it == BroadcastMetadataState.COMPLETE }
        if (allMedia.isNotEmpty() && completeMetadataCount == 0) {
            notices += ProviderCalendarNotice.BROADCAST_METADATA_UNAVAILABLE
        }
        if (metadataStates.any { it == BroadcastMetadataState.PARTIAL } ||
            completeMetadataCount in 1 until allMedia.size
        ) {
            notices += ProviderCalendarNotice.PARTIAL_BROADCAST_METADATA
        }

        val entries = allMedia
            .asSequence()
            .flatMap { media -> projectBroadcastSlots(media, query).asSequence() }
            .distinctBy(ProviderCalendarEntry::stableKey)
            .sortedBy(ProviderCalendarEntry::scheduledAtEpochSeconds)
            .toList()

        val capabilities = linkedSetOf(
            ProviderCalendarCapability.SEASONAL_CATALOG,
            ProviderCalendarCapability.BACKGROUND_REFRESH,
            ProviderCalendarCapability.WIDGET_SNAPSHOT,
        )
        if (completeMetadataCount > 0) {
            capabilities += ProviderCalendarCapability.RECURRING_BROADCAST_SLOTS
        }

        return ProviderCalendarLoadResult.Content(
            entries = entries,
            capabilities = capabilities,
            notices = notices,
            fetchedAtEpochMillis = fetchedAt,
        )
    }

    private suspend fun fetchSeason(
        accountKey: String,
        seasonKey: SeasonKey,
    ): SeasonFetch {
        val entries = mutableListOf<MalCalendarMedia>()
        var page = 0
        var nextPageUrl: String? = null
        while (page < MAX_PAGES_PER_SEASON) {
            val result = if (page == 0) {
                api.seasonal(
                    localAccountId = accountKey,
                    year = seasonKey.year,
                    season = seasonKey.season,
                )
            } else {
                api.nextPage(accountKey, requireNotNull(nextPageUrl))
            }
            when (result) {
                is MalApiResult.Failure -> return SeasonFetch(entries, result.error, truncated = false)
                is MalApiResult.Success -> {
                    entries += result.value.entries
                    nextPageUrl = result.value.nextPageUrl
                    if (nextPageUrl == null) return SeasonFetch(entries, null, truncated = false)
                }
            }
            page++
        }
        return SeasonFetch(entries, null, truncated = nextPageUrl != null)
    }

    private fun seasonsFor(query: ProviderCalendarQuery): List<SeasonKey> {
        val start = Instant.ofEpochSecond(query.startEpochSeconds)
            .atZone(JAPAN_ZONE)
            .toLocalDate()
            .withDayOfMonth(1)
        val end = Instant.ofEpochSecond(query.endEpochSeconds - 1L)
            .atZone(JAPAN_ZONE)
            .toLocalDate()
            .withDayOfMonth(1)
        val seasons = linkedSetOf<SeasonKey>()
        var cursor = start
        while (!cursor.isAfter(end)) {
            seasons += SeasonKey(cursor.year, seasonForMonth(cursor.monthValue))
            cursor = cursor.plusMonths(1)
        }
        return seasons.toList()
    }

    private fun projectBroadcastSlots(
        media: MalCalendarMedia,
        query: ProviderCalendarQuery,
    ): List<ProviderCalendarEntry> {
        val day = media.broadcastDayOfWeek.toDayOfWeek() ?: return emptyList()
        val time = media.broadcastStartTime.toLocalTimeOrNull() ?: return emptyList()
        val rangeStartDate = Instant.ofEpochSecond(query.startEpochSeconds)
            .atZone(JAPAN_ZONE)
            .toLocalDate()
        val rangeEndDate = Instant.ofEpochSecond(query.endEpochSeconds - 1L)
            .atZone(JAPAN_ZONE)
            .toLocalDate()
        val mediaStart = parseDateFloor(media.startDate) ?: rangeStartDate
        val mediaEnd = parseDateCeiling(media.endDate) ?: rangeEndDate
        val firstDate = maxOf(rangeStartDate, mediaStart)
            .with(TemporalAdjusters.nextOrSame(day))
        val lastDate = minOf(rangeEndDate, mediaEnd)
        if (firstDate.isAfter(lastDate)) return emptyList()

        val entries = mutableListOf<ProviderCalendarEntry>()
        var date = firstDate
        while (!date.isAfter(lastDate)) {
            val instant = LocalDateTime.of(date, time)
                .atZone(JAPAN_ZONE)
                .toInstant()
            val epochSeconds = instant.epochSecond
            if (epochSeconds >= query.startEpochSeconds && epochSeconds < query.endEpochSeconds) {
                entries += ProviderCalendarEntry(
                    provider = ActiveProvider.MAL_ONLY,
                    providerMediaId = media.malId,
                    mediaType = ProviderCalendarMediaType.ANIME,
                    title = media.title,
                    coverUrl = media.pictureLarge ?: media.pictureMedium,
                    scheduledAtEpochSeconds = epochSeconds,
                    episodeNumber = null,
                    isOnList = media.isOnList,
                    precision = ProviderCalendarPrecision.RECURRING_BROADCAST_SLOT,
                    sourceTimeZoneId = JAPAN_ZONE.id,
                )
            }
            date = date.plusWeeks(1)
        }
        return entries
    }

    private fun MalCalendarMedia.broadcastMetadataState(): BroadcastMetadataState {
        val hasDay = !broadcastDayOfWeek.isNullOrBlank()
        val hasTime = !broadcastStartTime.isNullOrBlank()
        return when {
            hasDay && hasTime -> BroadcastMetadataState.COMPLETE
            hasDay || hasTime -> BroadcastMetadataState.PARTIAL
            else -> BroadcastMetadataState.MISSING
        }
    }

    private fun String?.toDayOfWeek(): DayOfWeek? = when (this?.trim()?.lowercase()) {
        "monday" -> DayOfWeek.MONDAY
        "tuesday" -> DayOfWeek.TUESDAY
        "wednesday" -> DayOfWeek.WEDNESDAY
        "thursday" -> DayOfWeek.THURSDAY
        "friday" -> DayOfWeek.FRIDAY
        "saturday" -> DayOfWeek.SATURDAY
        "sunday" -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun String?.toLocalTimeOrNull(): LocalTime? {
        val value = this?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return TIME_FORMATS.firstNotNullOfOrNull { formatter ->
            runCatching { LocalTime.parse(value, formatter) }.getOrNull()
        }
    }

    private fun parseDateFloor(value: String?): LocalDate? = parseFlexibleDate(value, ceiling = false)

    private fun parseDateCeiling(value: String?): LocalDate? = parseFlexibleDate(value, ceiling = true)

    private fun parseFlexibleDate(value: String?, ceiling: Boolean): LocalDate? {
        val normalized = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return try {
            when (normalized.length) {
                4 -> Year.parse(normalized).let { if (ceiling) it.atMonth(12).atEndOfMonth() else it.atDay(1) }
                7 -> YearMonth.parse(normalized).let { if (ceiling) it.atEndOfMonth() else it.atDay(1) }
                else -> LocalDate.parse(normalized)
            }
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun seasonForMonth(month: Int): MalSeason = when (month) {
        in 1..3 -> MalSeason.WINTER
        in 4..6 -> MalSeason.SPRING
        in 7..9 -> MalSeason.SUMMER
        else -> MalSeason.FALL
    }

    private enum class BroadcastMetadataState {
        COMPLETE,
        PARTIAL,
        MISSING,
    }

    private data class SeasonKey(val year: Int, val season: MalSeason)

    private data class SeasonFetch(
        val entries: List<MalCalendarMedia>,
        val failure: MalApiFailure?,
        val truncated: Boolean,
    )

    private data class CacheEntry(
        val accountKey: String,
        val query: ProviderCalendarQuery,
        val savedAtEpochMillis: Long,
        val result: ProviderCalendarLoadResult.Content,
    )

    companion object {
        val BROADCAST_SOURCE_ZONE: ZoneId = ZoneId.of("Asia/Tokyo")
        private val JAPAN_ZONE: ZoneId = BROADCAST_SOURCE_ZONE
        private val TIME_FORMATS = listOf(
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("HH:mm:ss"),
        )
        private const val MAX_PAGES_PER_SEASON = 2
        private const val CACHE_TTL_MILLIS = 6L * 60L * 60L * 1_000L
        private const val MAX_QUERY_SECONDS = 62L * 24L * 60L * 60L
    }
}
