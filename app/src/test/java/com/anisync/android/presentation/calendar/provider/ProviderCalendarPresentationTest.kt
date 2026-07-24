package com.anisync.android.presentation.calendar.provider

import com.anisync.android.domain.calendar.provider.ProviderCalendarCapability
import com.anisync.android.domain.calendar.provider.ProviderCalendarEntry
import com.anisync.android.domain.calendar.provider.ProviderCalendarLoadResult
import com.anisync.android.domain.calendar.provider.ProviderCalendarMediaType
import com.anisync.android.domain.calendar.provider.ProviderCalendarNotice
import com.anisync.android.domain.calendar.provider.ProviderCalendarPrecision
import com.anisync.android.domain.calendar.provider.ProviderCalendarUnavailableReason
import com.anisync.android.domain.provider.ActiveProvider
import com.anisync.android.presentation.model.ProviderMediaIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class ProviderCalendarPresentationTest {
    @Test
    fun `MAL recurring slot keeps typed identity timezone precision and capability notices`() {
        val result = ProviderCalendarLoadResult.Content(
            entries = listOf(
                ProviderCalendarEntry(
                    provider = ActiveProvider.MAL_ONLY,
                    providerMediaId = 42L,
                    mediaType = ProviderCalendarMediaType.ANIME,
                    title = "Title",
                    coverUrl = null,
                    scheduledAtEpochSeconds = 1_800_000_000L,
                    episodeNumber = null,
                    isOnList = true,
                    precision = ProviderCalendarPrecision.RECURRING_BROADCAST_SLOT,
                    sourceTimeZoneId = "Asia/Tokyo",
                )
            ),
            capabilities = setOf(ProviderCalendarCapability.RECURRING_BROADCAST_SLOTS),
            notices = setOf(
                ProviderCalendarNotice.EXACT_EPISODE_SCHEDULE_UNAVAILABLE,
                ProviderCalendarNotice.AIRING_NOTIFICATIONS_UNAVAILABLE,
            ),
            fetchedAtEpochMillis = 1L,
        )

        val state = ProviderCalendarPresentationMapper.map(result, ZoneId.of("UTC"))
            as ProviderCalendarPresentationState.Content
        val item = state.days.single().entries.single()

        assertTrue(item.identity is ProviderMediaIdentity.MyAnimeList)
        assertEquals("MYANIMELIST:ANIME:42", item.identity.stableKey)
        assertNull(item.episodeNumber)
        assertEquals(ProviderCalendarPrecision.RECURRING_BROADCAST_SLOT, item.precision)
        assertEquals("Asia/Tokyo", item.sourceTimeZoneId)
        assertTrue(ProviderCalendarNotice.EXACT_EPISODE_SCHEDULE_UNAVAILABLE in state.notices)
    }

    @Test
    fun `unsupported capability maps to unavailable without synthetic entries`() {
        val state = ProviderCalendarPresentationMapper.map(
            ProviderCalendarLoadResult.Unavailable(
                ProviderCalendarUnavailableReason.EXACT_EPISODE_SCHEDULE_UNSUPPORTED
            ),
            ZoneId.of("UTC"),
        )

        assertEquals(
            ProviderCalendarUnavailableReason.EXACT_EPISODE_SCHEDULE_UNSUPPORTED,
            (state as ProviderCalendarPresentationState.Unavailable).reason,
        )
    }
}
