package com.anisync.android.data.tracking

import com.anisync.android.data.identity.LocalMediaIdentity
import com.anisync.android.data.identity.LocalMediaType
import com.anisync.android.data.identity.MediaIdentityResult
import com.anisync.android.domain.provider.ActiveProvider
import com.anisync.android.domain.tracking.ProviderNetworkPolicy
import com.anisync.android.domain.tracking.TrackingCommandDraft
import com.anisync.android.domain.tracking.TrackingCommandTarget
import com.anisync.android.domain.tracking.TrackingDesiredState
import com.anisync.android.domain.tracking.TrackingEnqueueReceipt
import com.anisync.android.domain.tracking.TrackingEnqueueResult
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingField
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingProvider
import com.anisync.android.domain.tracking.TrackingStatus
import com.anisync.android.domain.tracking.TrackingTargetState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingCommandServiceTest {
    @Test
    fun `AniList mode creates one AniList target and preserves list entry id`() = runTest {
        var draft: TrackingCommandDraft? = null
        var target: TrackingCommandTarget? = null
        val service = service(
            activeProvider = ActiveProvider.ANILIST_ONLY,
            enqueue = { capturedDraft, capturedTarget ->
                draft = capturedDraft
                target = capturedTarget
                accepted(capturedTarget.provider)
            },
        )

        val result = service.enqueueAniList(
            AniListTrackingCommandInput(
                aniListMediaId = 42,
                aniListListEntryId = 900,
                mediaType = TrackingMediaType.ANIME,
                desired = TrackingDesiredState(TrackingStatus.CURRENT, 3),
                fields = setOf(TrackingField.STATUS, TrackingField.PROGRESS),
            )
        )

        assertTrue(result is TrackingEnqueueResult.Accepted)
        assertEquals(900L, draft?.providerListEntryId)
        assertEquals(TrackingProvider.ANILIST, target?.provider)
        assertEquals(42L, target?.providerMediaId)
    }

    @Test
    fun `MAL mode creates one MAL target without consulting AniList account`() = runTest {
        var aniListAccountReads = 0
        var target: TrackingCommandTarget? = null
        val service = service(
            activeProvider = ActiveProvider.MAL_ONLY,
            activeAniListAccountId = { aniListAccountReads++; "ani" },
            enqueue = { _, captured -> target = captured; accepted(captured.provider) },
        )

        val result = service.enqueueMal(
            MalTrackingCommandInput(
                malMediaId = 99,
                mediaType = TrackingMediaType.MANGA,
                desired = TrackingDesiredState(TrackingStatus.CURRENT, 8),
                fields = setOf(TrackingField.STATUS, TrackingField.PROGRESS),
            )
        )

        assertTrue(result is TrackingEnqueueResult.Accepted)
        assertEquals(0, aniListAccountReads)
        assertEquals(TrackingProvider.MYANIMELIST, target?.provider)
        assertEquals(99L, target?.providerMediaId)
    }

    @Test
    fun `inactive provider command is rejected before identity or account access`() = runTest {
        var sensitiveCalls = 0
        val service = service(
            activeProvider = ActiveProvider.UNCONFIGURED,
            ensureAniList = { _, _ -> sensitiveCalls++; identity() },
            activeAniListAccountId = { sensitiveCalls++; "ani" },
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
        assertEquals(0, sensitiveCalls)
    }

    @Test
    fun `MAL command rejects unsupported secondary progress and missing configuration`() = runTest {
        val unsupported = service(activeProvider = ActiveProvider.MAL_ONLY).enqueueMal(
            MalTrackingCommandInput(
                1, TrackingMediaType.ANIME,
                TrackingDesiredState(TrackingStatus.CURRENT, 1, progressSecondary = 1),
                setOf(TrackingField.PROGRESS_SECONDARY),
            )
        )
        assertEquals(
            TrackingEnqueueResult.Rejected(TrackingFailureKind.UNSUPPORTED_FIELD),
            unsupported,
        )

        val unconfigured = service(
            activeProvider = ActiveProvider.MAL_ONLY,
            malConfigured = false,
        ).enqueueMal(
            MalTrackingCommandInput(
                1, TrackingMediaType.ANIME,
                TrackingDesiredState(TrackingStatus.CURRENT, 1),
                setOf(TrackingField.STATUS),
            )
        )
        assertEquals(
            TrackingEnqueueResult.Rejected(TrackingFailureKind.PROVIDER_NOT_CONFIGURED),
            unconfigured,
        )
    }

    private fun service(
        activeProvider: ActiveProvider,
        ensureAniList: suspend (LocalMediaType, Int) -> MediaIdentityResult<LocalMediaIdentity> = { _, _ -> identity() },
        ensureMal: suspend (LocalMediaType, Long) -> MediaIdentityResult<LocalMediaIdentity> = { _, _ -> identity() },
        activeAniListAccountId: () -> String? = { "ani-account" },
        activeMalAccountId: suspend () -> String? = { "mal-account" },
        malConfigured: Boolean = true,
        enqueue: suspend (TrackingCommandDraft, TrackingCommandTarget) -> TrackingEnqueueResult = { _, target -> accepted(target.provider) },
    ) = TrackingCommandService(
        activeProvider = { activeProvider },
        ensureAniListIdentity = ensureAniList,
        ensureMalIdentity = ensureMal,
        activeAniListAccountId = activeAniListAccountId,
        activeMalAccountId = activeMalAccountId,
        providerNetworkPolicy = { ProviderNetworkPolicy() },
        isMalConfigured = { malConfigured },
        enqueueCommand = enqueue,
    )

    private fun identity() = MediaIdentityResult.Success(
        LocalMediaIdentity("local", LocalMediaType.ANIME, 1, 1)
    )

    private fun accepted(provider: TrackingProvider) = TrackingEnqueueResult.Accepted(
        TrackingEnqueueReceipt(
            operationId = "operation",
            generation = 1,
            deduplicated = false,
            provider = provider,
            targetState = TrackingTargetState.PENDING,
        )
    )
}
