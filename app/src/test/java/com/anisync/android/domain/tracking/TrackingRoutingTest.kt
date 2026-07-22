package com.anisync.android.domain.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingRoutingTest {
    private val resolver = TrackingRouteResolver()

    @Test
    fun `anime and manga routes are independent and default to AniList`() {
        val defaults = PerMediaTrackingPolicy()
        val split = PerMediaTrackingPolicy(
            animeMode = TrackingMode.MYANIMELIST_ONLY,
            mangaMode = TrackingMode.ANILIST_ONLY,
        )
        assertEquals(TrackingMode.ANILIST_ONLY, defaults.modeFor(TrackingMediaType.ANIME))
        assertEquals(TrackingMode.MYANIMELIST_ONLY, split.modeFor(TrackingMediaType.ANIME))
        assertEquals(TrackingMode.ANILIST_ONLY, split.modeFor(TrackingMediaType.MANGA))
    }

    @Test
    fun `MAL-only route remains executable with hard AniList network block`() {
        val route = resolver.resolve(
            TrackingMediaType.ANIME,
            PerMediaTrackingPolicy(
                animeMode = TrackingMode.MYANIMELIST_ONLY,
                mangaMode = TrackingMode.MYANIMELIST_ONLY,
            ),
            TrackingAccountSelection(myAnimeListAccountId = "mal"),
            TrackingIdentitySelection(myAnimeListId = 20),
            ProviderNetworkPolicy(allowAniList = false),
        )
        assertTrue(route.fullyExecutable)
        assertEquals(listOf(TrackingProvider.MYANIMELIST), route.targets.map { it.provider })
    }

    @Test
    fun `dual route persists blocked provider rather than silently downgrading`() {
        val route = resolver.resolve(
            TrackingMediaType.MANGA,
            PerMediaTrackingPolicy(mangaMode = TrackingMode.DUAL),
            TrackingAccountSelection(aniListAccountId = "ani", myAnimeListAccountId = "mal"),
            TrackingIdentitySelection(aniListId = 10, myAnimeListId = 20),
            ProviderNetworkPolicy(allowAniList = false),
        )
        assertFalse(route.fullyExecutable)
        assertEquals(2, route.targets.size)
        assertEquals(
            TrackingFailureKind.NETWORK_BLOCKED,
            route.targets.single { it.provider == TrackingProvider.ANILIST }.blocker,
        )
        assertEquals(null, route.targets.single { it.provider == TrackingProvider.MYANIMELIST }.blocker)
    }
}
