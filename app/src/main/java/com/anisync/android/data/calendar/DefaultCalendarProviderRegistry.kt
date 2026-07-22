package com.anisync.android.data.calendar

import com.anisync.android.domain.calendar.CalendarProvider
import com.anisync.android.domain.calendar.CalendarProviderRegistry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultCalendarProviderRegistry @Inject constructor(
    providers: Set<@JvmSuppressWildcards CalendarProvider>
) : CalendarProviderRegistry {
    override val availableProviders: List<CalendarProvider> = providers.sortedWith(
        compareByDescending<CalendarProvider> { it.priority }
            .thenBy { it.providerId }
    )

    override fun defaultProvider(): CalendarProvider =
        availableProviders.firstOrNull()
            ?: error("At least one calendar provider must be registered")
}
