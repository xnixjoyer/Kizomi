package com.anisync.android.data

import com.anisync.android.domain.AiringEpisode
import com.anisync.android.domain.CalendarRepository
import com.anisync.android.domain.Result
import com.anisync.android.domain.calendar.CalendarDateRange
import com.anisync.android.domain.calendar.CalendarProviderRegistry
import javax.inject.Inject

/**
 * Compatibility repository used by the existing calendar presentation layer.
 *
 * The public build selects AniList through a provider registry. Private derivatives can register
 * another provider without changing this repository or the shared calendar UI.
 */
class CalendarRepositoryImpl @Inject constructor(
    providerRegistry: CalendarProviderRegistry
) : CalendarRepository {
    private val provider = providerRegistry.defaultProvider()

    override suspend fun getWeekSchedule(
        weekStartEpochSec: Long,
        weekEndEpochSec: Long
    ): Result<List<AiringEpisode>> = provider.getEntries(
        CalendarDateRange(
            startEpochSeconds = weekStartEpochSec,
            endEpochSeconds = weekEndEpochSec
        )
    )
}
