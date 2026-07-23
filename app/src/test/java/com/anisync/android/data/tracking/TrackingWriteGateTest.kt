package com.anisync.android.data.tracking

import com.anisync.android.domain.tracking.ProviderNetworkPolicy
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackingWriteGateTest {
    @Test
    fun `disabled provider is blocked before account evaluation`() {
        TrackingProvider.entries.forEach { provider ->
            val policy = when (provider) {
                TrackingProvider.ANILIST -> ProviderNetworkPolicy(allowAniList = false)
                TrackingProvider.MYANIMELIST -> ProviderNetworkPolicy(allowMyAnimeList = false)
            }
            assertEquals(
                TrackingFailureKind.NETWORK_BLOCKED,
                evaluateTrackingWriteGate(provider, policy, "expected", "expected"),
            )
        }
    }

    @Test
    fun `account switch and logout fail closed for every provider`() {
        TrackingProvider.entries.forEach { provider ->
            assertEquals(
                TrackingFailureKind.MISSING_ACCOUNT,
                evaluateTrackingWriteGate(
                    provider,
                    ProviderNetworkPolicy(),
                    expectedAccountId = "queued-account",
                    activeAccountId = "new-account",
                ),
            )
            assertEquals(
                TrackingFailureKind.MISSING_ACCOUNT,
                evaluateTrackingWriteGate(
                    provider,
                    ProviderNetworkPolicy(),
                    expectedAccountId = "queued-account",
                    activeAccountId = null,
                ),
            )
        }
    }

    @Test
    fun `exact active account remains executable for every provider`() {
        TrackingProvider.entries.forEach { provider ->
            assertNull(
                evaluateTrackingWriteGate(
                    provider,
                    ProviderNetworkPolicy(),
                    expectedAccountId = "same-account",
                    activeAccountId = "same-account",
                )
            )
        }
    }

    @Test
    fun `blank persisted target account is always blocked`() {
        assertEquals(
            TrackingFailureKind.MISSING_ACCOUNT,
            evaluateTrackingWriteGate(
                TrackingProvider.ANILIST,
                ProviderNetworkPolicy(),
                expectedAccountId = " ",
                activeAccountId = " ",
            ),
        )
    }
}
