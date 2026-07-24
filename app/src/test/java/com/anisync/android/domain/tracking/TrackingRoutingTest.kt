package com.anisync.android.domain.tracking

import com.anisync.android.domain.provider.ActiveProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingRoutingTest {
    private val resolver = TrackingRouteResolver()

    @Test
    fun `unconfigured resolves no target`() {
        val route = resolver.resolve(
            activeProvider = ActiveProvider.UNCONFIGURED,
            accounts = TrackingAccountSelection("ani", "mal"),
            identities = TrackingIdentitySelection(10, 20),
            network = ProviderNetworkPolicy(),
        )
        assertNull(route.target)
        assertFalse(route.fullyExecutable)
    }

    @Test
    fun `AniList active resolves exactly AniList even when MAL data exists`() {
        val route = resolver.resolve(
            activeProvider = ActiveProvider.ANILIST_ONLY,
            accounts = TrackingAccountSelection("ani", "mal"),
            identities = TrackingIdentitySelection(10, 20),
            network = ProviderNetworkPolicy(),
        )
        assertEquals(TrackingProvider.ANILIST, route.target?.provider)
        assertEquals("ani", route.target?.providerAccountId)
        assertEquals(10L, route.target?.providerMediaId)
        assertTrue(route.fullyExecutable)
    }

    @Test
    fun `MAL active resolves exactly MAL and respects MAL kill switch`() {
        val route = resolver.resolve(
            activeProvider = ActiveProvider.MAL_ONLY,
            accounts = TrackingAccountSelection("ani", "mal"),
            identities = TrackingIdentitySelection(10, 20),
            network = ProviderNetworkPolicy(allowAniList = true, allowMyAnimeList = false),
        )
        assertEquals(TrackingProvider.MYANIMELIST, route.target?.provider)
        assertEquals(TrackingFailureKind.NETWORK_BLOCKED, route.target?.blocker)
        assertFalse(route.fullyExecutable)
    }

    @Test
    fun `missing active-provider account and identity fail closed`() {
        val missingAccount = resolver.resolve(
            ActiveProvider.MAL_ONLY,
            TrackingAccountSelection(),
            TrackingIdentitySelection(myAnimeListId = 20),
            ProviderNetworkPolicy(),
        )
        assertEquals(TrackingFailureKind.MISSING_ACCOUNT, missingAccount.target?.blocker)

        val missingIdentity = resolver.resolve(
            ActiveProvider.ANILIST_ONLY,
            TrackingAccountSelection(aniListAccountId = "ani"),
            TrackingIdentitySelection(),
            ProviderNetworkPolicy(),
        )
        assertEquals(TrackingFailureKind.MISSING_IDENTITY, missingIdentity.target?.blocker)
    }
}
