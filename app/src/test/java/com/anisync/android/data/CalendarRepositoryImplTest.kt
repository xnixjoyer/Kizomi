package com.anisync.android.data

import com.anisync.android.domain.AiringEpisode
import com.anisync.android.domain.Result
import com.anisync.android.domain.calendar.CalendarDateRange
import com.anisync.android.domain.calendar.CalendarProvider
import com.anisync.android.domain.calendar.CalendarProviderRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarRepositoryImplTest {
    @Test
    fun repositoryDelegatesToDefaultProvider() = runTest {
        val expected = listOf(sampleEpisode())
        val provider = object : CalendarProvider {
            override val providerId: String = "test"
            override suspend fun getEntries(
                range: CalendarDateRange,
                forceRefresh: Boolean
            ): Result<List<AiringEpisode>> {
                assertEquals(10L, range.startEpochSeconds)
                assertEquals(20L, range.endEpochSeconds)
                return Result.Success(expected)
            }
        }
        val registry = object : CalendarProviderRegistry {
            override val availableProviders: List<CalendarProvider> = listOf(provider)
            override fun defaultProvider(): CalendarProvider = provider
        }

        val result = CalendarRepositoryImpl(registry).getWeekSchedule(10L, 20L)

        assertEquals(expected, (result as Result.Success).data)
    }

    private fun sampleEpisode() = AiringEpisode(
        id = 1,
        episode = 2,
        airingAt = 15L,
        mediaId = 3,
        titleRomaji = "Title",
        titleEnglish = null,
        titleNative = null,
        titleUserPreferred = "Title",
        coverImageUrl = null,
        format = "TV",
        averageScore = 80,
        isOnList = true,
        listStatus = null,
        isAdult = false
    )
}
