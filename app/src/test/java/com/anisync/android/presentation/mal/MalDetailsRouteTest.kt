package com.anisync.android.presentation.mal

import com.anisync.android.data.mal.api.MalMediaKey
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.presentation.navigation.MalNativeDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MalDetailsRouteTest {
    @Test
    fun `typed anime route restores the same MAL media identity`() {
        val route = MalNativeDetails(
            mediaType = TrackingMediaType.ANIME.name,
            malId = 5114L,
        )

        assertEquals(
            MalMediaKey(TrackingMediaType.ANIME, 5114L),
            malMediaKeyFromRoute(route.mediaType, route.malId),
        )
    }

    @Test
    fun `typed manga route restores the same MAL media identity`() {
        val route = MalNativeDetails(
            mediaType = TrackingMediaType.MANGA.name,
            malId = 13L,
        )

        assertEquals(
            MalMediaKey(TrackingMediaType.MANGA, 13L),
            malMediaKeyFromRoute(route.mediaType, route.malId),
        )
    }

    @Test
    fun `missing malformed and non-positive route values are rejected without constructing a key`() {
        assertNull(malMediaKeyFromRoute(null, 1L))
        assertNull(malMediaKeyFromRoute("ANIM", 1L))
        assertNull(malMediaKeyFromRoute(TrackingMediaType.ANIME.name, null))
        assertNull(malMediaKeyFromRoute(TrackingMediaType.ANIME.name, 0L))
        assertNull(malMediaKeyFromRoute(TrackingMediaType.MANGA.name, -1L))
    }

    @Test
    fun `invalid route creates a recoverable non-loading details state`() {
        val state = malDetailsInitialState("invalid", 0L)

        assertNull(state.key)
        assertFalse(state.loading)
        assertEquals(
            MalDetailsRouteError.INVALID_MEDIA_IDENTITY,
            state.routeError,
        )
    }
}
