package com.anisync.android.domain.calendar.provider

import com.anisync.android.domain.provider.ActiveProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCalendarRouterIsolationTest {
    private val query = ProviderCalendarQuery(1_000L, 2_000L, "UTC")

    @Test
    fun `MAL only invokes MAL and never AniList`() = runTest {
        val mal = FakeSource(ActiveProvider.MAL_ONLY)
        val aniList = FakeSource(ActiveProvider.ANILIST_ONLY)
        val router = ProviderCalendarRouter(setOf(mal, aniList))

        router.load(
            ProviderCalendarSession(ActiveProvider.MAL_ONLY, true, "mal-account"),
            query,
        )

        assertEquals(1, mal.calls)
        assertEquals(0, aniList.calls)
    }

    @Test
    fun `AniList only invokes AniList and never MAL`() = runTest {
        val mal = FakeSource(ActiveProvider.MAL_ONLY)
        val aniList = FakeSource(ActiveProvider.ANILIST_ONLY)
        val router = ProviderCalendarRouter(setOf(mal, aniList))

        router.load(
            ProviderCalendarSession(ActiveProvider.ANILIST_ONLY, true, "anilist-account"),
            query,
        )

        assertEquals(0, mal.calls)
        assertEquals(1, aniList.calls)
    }

    @Test
    fun `unconfigured and transition states perform zero provider work`() = runTest {
        val mal = FakeSource(ActiveProvider.MAL_ONLY)
        val aniList = FakeSource(ActiveProvider.ANILIST_ONLY)
        val router = ProviderCalendarRouter(setOf(mal, aniList))

        val unconfigured = router.load(
            ProviderCalendarSession(ActiveProvider.UNCONFIGURED, false),
            query,
        )
        val transition = router.load(
            ProviderCalendarSession(ActiveProvider.MAL_ONLY, false, "mal-account"),
            query,
        )

        assertTrue(unconfigured is ProviderCalendarLoadResult.Unavailable)
        assertTrue(transition is ProviderCalendarLoadResult.Unavailable)
        assertEquals(0, mal.calls)
        assertEquals(0, aniList.calls)
    }

    @Test
    fun `unsupported source never falls back to another provider`() = runTest {
        val aniList = FakeSource(ActiveProvider.ANILIST_ONLY)
        val router = ProviderCalendarRouter(setOf(aniList))

        val result = router.load(
            ProviderCalendarSession(ActiveProvider.MAL_ONLY, true, "mal-account"),
            query,
        )

        assertEquals(
            ProviderCalendarUnavailableReason.PROVIDER_UNSUPPORTED,
            (result as ProviderCalendarLoadResult.Unavailable).reason,
        )
        assertEquals(0, aniList.calls)
    }

    private class FakeSource(
        override val provider: ActiveProvider,
    ) : ProviderCalendarSource {
        var calls = 0

        override suspend fun load(
            session: ProviderCalendarSession,
            query: ProviderCalendarQuery,
            forceRefresh: Boolean,
        ): ProviderCalendarLoadResult {
            calls++
            return ProviderCalendarLoadResult.Content(
                entries = emptyList(),
                capabilities = emptySet(),
                fetchedAtEpochMillis = 1L,
            )
        }
    }
}
