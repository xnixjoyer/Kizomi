package com.anisync.android.domain.calendar

import com.anisync.android.domain.AiringEpisode
import com.anisync.android.domain.Result

/**
 * Provider-neutral source for calendar entries.
 *
 * Implementations are selected through dependency injection without coupling the calendar UI
 * to a specific source. Optional calendar capabilities use the separate [CalendarExtension]
 * contract and are filtered by the app-wide active provider.
 */
interface CalendarProvider {
    val providerId: String

    /** Higher values win when a derivative contributes an alternative implementation. */
    val priority: Int
        get() = 0

    suspend fun getEntries(
        range: CalendarDateRange,
        forceRefresh: Boolean = false
    ): Result<List<AiringEpisode>>
}

data class CalendarDateRange(
    val startEpochSeconds: Long,
    val endEpochSeconds: Long
) {
    init {
        require(endEpochSeconds > startEpochSeconds) {
            "Calendar range end must be greater than its start"
        }
    }
}

interface CalendarProviderRegistry {
    val availableProviders: List<CalendarProvider>
    fun defaultProvider(): CalendarProvider
}
