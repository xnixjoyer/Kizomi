package com.anisync.android.data.calendar

import com.anisync.android.domain.AiringEpisode
import com.anisync.android.domain.Result
import com.anisync.android.domain.calendar.CalendarDateRange
import com.anisync.android.domain.calendar.CalendarProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CalendarProviderRegistryTest {
    @Test
    fun registrySelectsHighestPriorityIndependentOfSetIterationOrder() {
        val publicProvider = fakeProvider("anilist", priority = 0)
        val extensionProvider = fakeProvider("extension", priority = 100)
        val registry = DefaultCalendarProviderRegistry(linkedSetOf(publicProvider, extensionProvider))

        assertEquals(listOf("extension", "anilist"), registry.availableProviders.map { it.providerId })
        assertEquals(extensionProvider, registry.defaultProvider())
    }

    @Test
    fun providerIdBreaksPriorityTiesDeterministically() {
        val zeta = fakeProvider("zeta", priority = 0)
        val alpha = fakeProvider("alpha", priority = 0)
        val registry = DefaultCalendarProviderRegistry(linkedSetOf(zeta, alpha))

        assertEquals(alpha, registry.defaultProvider())
    }

    @Test
    fun dateRangeRejectsEmptyOrReversedIntervals() {
        assertThrows(IllegalArgumentException::class.java) { CalendarDateRange(10L, 10L) }
        assertThrows(IllegalArgumentException::class.java) { CalendarDateRange(11L, 10L) }
    }

    private fun fakeProvider(id: String, priority: Int) = object : CalendarProvider {
        override val providerId: String = id
        override val priority: Int = priority
        override suspend fun getEntries(
            range: CalendarDateRange,
            forceRefresh: Boolean
        ): Result<List<AiringEpisode>> = Result.Success(emptyList())
    }
}
