package com.anisync.android.presentation.navigation

import com.anisync.android.data.MainNavigationDestination
import com.anisync.android.domain.provider.ActiveProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderMainNavigationPolicyTest {
    private val configuredOrder = listOf(
        MainNavigationDestination.FEED.key,
        MainNavigationDestination.FORUM.key,
        MainNavigationDestination.DISCOVER.key,
        MainNavigationDestination.LIBRARY.key,
        MainNavigationDestination.PROFILE.key,
    )

    @Test
    fun `MAL exposes only supported shared-shell roots`() {
        val supported = supportedMainDestinationKeys(ActiveProvider.MAL_ONLY)

        assertEquals(
            setOf(
                MainNavigationDestination.LIBRARY.key,
                MainNavigationDestination.DISCOVER.key,
                MainNavigationDestination.PROFILE.key,
            ),
            supported,
        )
        assertFalse(MainNavigationDestination.FEED.key in supported)
        assertFalse(MainNavigationDestination.FORUM.key in supported)
    }

    @Test
    fun `MAL filters preferences without changing their configured ordering semantics`() {
        val resolved = resolveProviderMainNavigation(
            provider = ActiveProvider.MAL_ONLY,
            configuredOrder = configuredOrder,
            configuredVisible = setOf(
                MainNavigationDestination.FEED.key,
                MainNavigationDestination.DISCOVER.key,
                MainNavigationDestination.PROFILE.key,
            ),
            requestedStartKey = MainNavigationDestination.PROFILE.key,
        )

        assertEquals(
            listOf(
                MainNavigationDestination.DISCOVER.key,
                MainNavigationDestination.LIBRARY.key,
                MainNavigationDestination.PROFILE.key,
            ),
            resolved.orderedKeys,
        )
        assertEquals(
            setOf(
                MainNavigationDestination.DISCOVER.key,
                MainNavigationDestination.PROFILE.key,
            ),
            resolved.visibleKeys,
        )
        assertEquals(MainNavigationDestination.PROFILE.key, resolved.startKey)
        assertEquals(MainNavigationDestination.FEED.key, configuredOrder.first())
    }

    @Test
    fun `unsupported saved MAL start falls back to first visible supported root`() {
        val resolved = resolveProviderMainNavigation(
            provider = ActiveProvider.MAL_ONLY,
            configuredOrder = configuredOrder,
            configuredVisible = setOf(
                MainNavigationDestination.DISCOVER.key,
                MainNavigationDestination.PROFILE.key,
            ),
            requestedStartKey = MainNavigationDestination.FEED.key,
        )

        assertEquals(MainNavigationDestination.DISCOVER.key, resolved.startKey)
    }

    @Test
    fun `MAL creates a temporary supported fallback when every configured visible root is unsupported`() {
        val resolved = resolveProviderMainNavigation(
            provider = ActiveProvider.MAL_ONLY,
            configuredOrder = configuredOrder,
            configuredVisible = setOf(
                MainNavigationDestination.FEED.key,
                MainNavigationDestination.FORUM.key,
            ),
            requestedStartKey = MainNavigationDestination.FORUM.key,
        )

        assertEquals(setOf(MainNavigationDestination.DISCOVER.key), resolved.visibleKeys)
        assertEquals(MainNavigationDestination.DISCOVER.key, resolved.startKey)
    }

    @Test
    fun `AniList keeps its configured supported roots and start`() {
        val visible = setOf(
            MainNavigationDestination.LIBRARY.key,
            MainNavigationDestination.DISCOVER.key,
            MainNavigationDestination.FEED.key,
            MainNavigationDestination.FORUM.key,
            MainNavigationDestination.PROFILE.key,
        )
        val resolved = resolveProviderMainNavigation(
            provider = ActiveProvider.ANILIST_ONLY,
            configuredOrder = configuredOrder,
            configuredVisible = visible,
            requestedStartKey = MainNavigationDestination.FORUM.key,
        )

        assertEquals(configuredOrder, resolved.orderedKeys)
        assertEquals(visible, resolved.visibleKeys)
        assertEquals(MainNavigationDestination.FORUM.key, resolved.startKey)
    }

    @Test
    fun `unconfigured provider cannot enter shared shell`() {
        val failure = runCatching {
            resolveProviderMainNavigation(
                provider = ActiveProvider.UNCONFIGURED,
                configuredOrder = configuredOrder,
                configuredVisible = configuredOrder.toSet(),
                requestedStartKey = null,
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }
}
