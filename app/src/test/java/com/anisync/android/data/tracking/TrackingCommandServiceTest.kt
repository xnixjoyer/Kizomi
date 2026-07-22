package com.anisync.android.data.tracking

import com.anisync.android.data.identity.LocalMediaIdentity
import com.anisync.android.data.identity.LocalMediaType
import com.anisync.android.data.identity.MediaIdentityResult
import com.anisync.android.domain.tracking.TrackingCommandDraft
import com.anisync.android.domain.tracking.TrackingCommand
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingCommandServiceTest {
    @Test
    fun `full AniList edit crosses the durable boundary exactly once with absolute state`() = runTest {
        val enqueued = mutableListOf<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>()
        val service = service(enqueued = enqueued)
        val desired = TrackingDesiredState(
            status = TrackingStatus.CURRENT,
            progress = 7,
            score100 = 82.0,
            repeatCount = 2,
            notes = "private note",
            startedAt = "2026-01-02",
            customLists = listOf("Favorites"),
            isPrivate = true,
            hiddenFromStatusLists = true,
        )

        val result = service.enqueueAniList(
            AniListTrackingCommandInput(
                aniListMediaId = 42,
                aniListListEntryId = 900,
                mediaType = TrackingMediaType.ANIME,
                desired = desired,
                fields = setOf(
                    TrackingField.STATUS,
                    TrackingField.PROGRESS,
                    TrackingField.SCORE,
                    TrackingField.REPEAT_COUNT,
                    TrackingField.NOTES,
                    TrackingField.STARTED_AT,
                    TrackingField.CUSTOM_LISTS,
                    TrackingField.PRIVATE,
                    TrackingField.HIDDEN_FROM_STATUS_LISTS,
                ),
            )
        )

        assertTrue(result is TrackingEnqueueResult.Accepted)
        assertEquals(1, enqueued.size)
        val (draft, targets) = enqueued.single()
        assertEquals("local-42", draft.localMediaId)
        assertEquals(desired, draft.desired)
        assertEquals(900L, draft.providerListEntryIds[TrackingProvider.ANILIST])
        assertEquals(listOf(TrackingProvider.ANILIST), targets.map { it.provider })
        assertEquals("7", targets.single().providerAccountId)
        assertEquals(42L, targets.single().providerMediaId)
        assertEquals(null, targets.single().blocker)
    }

    @Test
    fun `missing account and missing delete handle are persisted as explicit blockers`() = runTest {
        val withoutAccount = mutableListOf<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>()
        service(accountId = null, enqueued = withoutAccount).enqueueAniList(
            input(delete = false)
        )
        assertEquals(
            TrackingFailureKind.MISSING_ACCOUNT,
            withoutAccount.single().second.single().blocker,
        )

        val missingDeleteHandle = mutableListOf<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>()
        service(enqueued = missingDeleteHandle).enqueueAniList(input(delete = true))
        assertEquals(
            TrackingFailureKind.MISSING_IDENTITY,
            missingDeleteHandle.single().second.single().blocker,
        )
        assertTrue(missingDeleteHandle.single().first.deleteIntent)
    }

    @Test
    fun `identity failure rejects before enqueue and cancellation remains control flow`() = runTest {
        var enqueueCount = 0
        val rejected = TrackingCommandService(
            ensureLocalIdentity = { _, _ -> MediaIdentityResult.StorageFailure("test") },
            activeAniListAccountId = { "7" },
            enqueueCommand = { _, _ ->
                enqueueCount++
                accepted()
            },
        ).enqueueAniList(input(delete = false))
        assertEquals(TrackingFailureKind.STORAGE, (rejected as TrackingEnqueueResult.Rejected).reason)
        assertEquals(0, enqueueCount)

        var cancelled = false
        try {
            TrackingCommandService(
                ensureLocalIdentity = { _, _ -> throw CancellationException("obsolete") },
                activeAniListAccountId = { "7" },
                enqueueCommand = { _, _ -> accepted() },
            ).enqueueAniList(input(delete = false))
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    @Test
    fun `serialized command retains provider handle and AniList-only compatibility fields`() {
        val draft = TrackingCommandDraft(
            localMediaId = "local-42",
            mediaType = TrackingMediaType.MANGA,
            desired = TrackingDesiredState(
                status = TrackingStatus.PAUSED,
                progress = 12,
                customLists = listOf("Owned"),
                isPrivate = true,
                hiddenFromStatusLists = true,
            ),
            fields = setOf(
                TrackingField.STATUS,
                TrackingField.PROGRESS,
                TrackingField.CUSTOM_LISTS,
                TrackingField.PRIVATE,
                TrackingField.HIDDEN_FROM_STATUS_LISTS,
            ),
            providerListEntryIds = mapOf(TrackingProvider.ANILIST to 901L),
        )
        val codec = TrackingCommandCodec()
        val decoded = codec.decode(codec.encode(TrackingCommand("operation", 1L, draft)))

        assertEquals(draft, decoded.draft)
        assertEquals(901L, decoded.draft.providerListEntryIds[TrackingProvider.ANILIST])
        assertEquals(listOf("Owned"), decoded.draft.desired.customLists)
    }

    private fun service(
        accountId: String? = "7",
        enqueued: MutableList<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>,
    ) = TrackingCommandService(
        ensureLocalIdentity = { type, mediaId ->
            MediaIdentityResult.Success(
                LocalMediaIdentity("local-$mediaId", type, 1L, 1L)
            )
        },
        activeAniListAccountId = { accountId },
        enqueueCommand = { draft, targets ->
            enqueued += draft to targets
            accepted(targets.single().blocker)
        },
    )

    private fun input(delete: Boolean) = AniListTrackingCommandInput(
        aniListMediaId = 42,
        mediaType = TrackingMediaType.ANIME,
        desired = TrackingDesiredState(
            status = if (delete) null else TrackingStatus.CURRENT,
            progress = 1,
        ),
        fields = setOf(if (delete) TrackingField.DELETE else TrackingField.PROGRESS),
        deleteIntent = delete,
    )

    private fun accepted(
        blocker: TrackingFailureKind? = null,
    ): TrackingEnqueueResult.Accepted = TrackingEnqueueResult.Accepted(
        TrackingEnqueueReceipt(
            operationId = "operation",
            generation = 1,
            deduplicated = false,
            targetStates = mapOf(
                TrackingProvider.ANILIST to if (blocker == null) {
                    TrackingTargetState.PENDING
                } else {
                    TrackingTargetState.BLOCKED
                }
            ),
        )
    )
}
