package com.anisync.android.data.tracking

import com.anisync.android.data.identity.LocalMediaIdentity
import com.anisync.android.data.identity.LocalMediaType
import com.anisync.android.data.identity.MediaIdentityResult
import com.anisync.android.domain.provider.ActiveProvider
import com.anisync.android.domain.tracking.ProviderNetworkPolicy
import com.anisync.android.domain.tracking.TrackingDesiredState
import com.anisync.android.domain.tracking.TrackingEnqueueResult
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingField
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackingNetworkNullTest {
    @Test
    fun `MAL mode performs zero AniList identity account and enqueue work for AniList command`() = runTest {
        var calls = 0
        val service = TrackingCommandService(
            activeProvider = { ActiveProvider.MAL_ONLY },
            ensureAniListIdentity = { _, _ -> calls++; identity() },
            ensureMalIdentity = { _, _ -> identity() },
            activeAniListAccountId = { calls++; "ani" },
            activeMalAccountId = { "mal" },
            providerNetworkPolicy = { ProviderNetworkPolicy() },
            isMalConfigured = { true },
            enqueueCommand = { _, _ -> calls++; error("must not enqueue") },
        )
        val result = service.enqueueAniList(
            AniListTrackingCommandInput(
                1, null, TrackingMediaType.ANIME,
                TrackingDesiredState(TrackingStatus.CURRENT, 1),
                setOf(TrackingField.STATUS),
            )
        )
        assertEquals(
            TrackingEnqueueResult.Rejected(TrackingFailureKind.PROVIDER_NOT_CONFIGURED),
            result,
        )
        assertEquals(0, calls)
    }

    @Test
    fun `AniList mode performs zero MAL identity account and enqueue work for MAL command`() = runTest {
        var calls = 0
        val service = TrackingCommandService(
            activeProvider = { ActiveProvider.ANILIST_ONLY },
            ensureAniListIdentity = { _, _ -> identity() },
            ensureMalIdentity = { _, _ -> calls++; identity() },
            activeAniListAccountId = { "ani" },
            activeMalAccountId = { calls++; "mal" },
            providerNetworkPolicy = { ProviderNetworkPolicy() },
            isMalConfigured = { calls++; true },
            enqueueCommand = { _, _ -> calls++; error("must not enqueue") },
        )
        val result = service.enqueueMal(
            MalTrackingCommandInput(
                1, TrackingMediaType.ANIME,
                TrackingDesiredState(TrackingStatus.CURRENT, 1),
                setOf(TrackingField.STATUS),
            )
        )
        assertEquals(
            TrackingEnqueueResult.Rejected(TrackingFailureKind.PROVIDER_NOT_CONFIGURED),
            result,
        )
        assertEquals(0, calls)
    }

    private fun identity() = MediaIdentityResult.Success(
        LocalMediaIdentity("local", LocalMediaType.ANIME, 1, 1)
    )
}
