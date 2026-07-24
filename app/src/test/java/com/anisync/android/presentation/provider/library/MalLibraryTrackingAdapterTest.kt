package com.anisync.android.presentation.provider.library

import com.anisync.android.data.tracking.MalLibraryConfirmedSnapshot
import com.anisync.android.data.tracking.MalLibraryTrackingState
import com.anisync.android.data.tracking.MalTrackingCommandInput
import com.anisync.android.domain.tracking.TrackingEnqueueReceipt
import com.anisync.android.domain.tracking.TrackingEnqueueResult
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingField
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingProvider
import com.anisync.android.domain.tracking.TrackingStatus
import com.anisync.android.domain.tracking.TrackingTargetState
import com.anisync.android.presentation.model.MediaListItemPresentation
import com.anisync.android.presentation.model.PresentationMediaType
import com.anisync.android.presentation.model.ProviderMediaIdentity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MalLibraryTrackingAdapterTest {
    @Test
    fun `one edit creates exactly one MAL command containing only changed fields`() = runTest {
        val captured = mutableListOf<MalTrackingCommandInput>()
        val adapter = MalLibraryTrackingAdapter { input ->
            captured += input
            TrackingEnqueueResult.Accepted(receipt())
        }
        val original = item(malId = 101L, progress = 2)

        val accepted = adapter.submit(
            original,
            MalLibraryEditDraft.from(original).copy(progress = 3),
        ) as MalLibraryEditOutcome.Accepted

        assertEquals(1, captured.size)
        assertEquals(101L, captured.single().malMediaId)
        assertEquals(setOf(TrackingField.PROGRESS), captured.single().fields)
        assertEquals(3, accepted.displayedItem.progress)
        assertEquals(2, accepted.rollbackItem.progress)
        assertEquals(TrackingProvider.MYANIMELIST, accepted.receipt.provider)
    }

    @Test
    fun `manga identity carries chapter and volume fields while anime rejects volume progress`() = runTest {
        val captured = mutableListOf<MalTrackingCommandInput>()
        val adapter = MalLibraryTrackingAdapter { input ->
            captured += input
            TrackingEnqueueResult.Accepted(receipt())
        }
        val manga = item(
            malId = 202L,
            mediaType = PresentationMediaType.MANGA,
            progress = 5,
            secondaryProgress = 1,
        )
        val anime = item(malId = 203L, progress = 5)

        val mangaOutcome = adapter.submit(
            manga,
            MalLibraryEditDraft.from(manga).copy(progress = 6, secondaryProgress = 2),
        )
        val animeOutcome = adapter.submit(
            anime,
            MalLibraryEditDraft.from(anime).copy(secondaryProgress = 1),
        )

        assertTrue(mangaOutcome is MalLibraryEditOutcome.Accepted)
        assertEquals(202L, captured.single().malMediaId)
        assertEquals(
            setOf(TrackingField.PROGRESS, TrackingField.PROGRESS_SECONDARY),
            captured.single().fields,
        )
        assertEquals(2, captured.single().desired.progressSecondary)
        assertEquals(
            TrackingFailureKind.UNSUPPORTED_FIELD,
            (animeOutcome as MalLibraryEditOutcome.Rejected).reason,
        )
        assertEquals(1, captured.size)
    }

    @Test
    fun `accepted pending delivered and confirmed are four distinct states`() = runTest {
        val delivery = MutableSharedFlow<MalLibraryTrackingState>(extraBufferCapacity = 4)
        val adapter = lifecycleAdapter(delivery)
        val original = item(malId = 301L, progress = 1)
        val accepted = adapter.submit(
            original,
            MalLibraryEditDraft.from(original).copy(progress = 2),
        ) as MalLibraryEditOutcome.Accepted
        val events = async(UnconfinedTestDispatcher(testScheduler)) {
            adapter.observe(accepted).take(3).toList()
        }

        delivery.emit(MalLibraryTrackingState.Pending(TrackingTargetState.PENDING, 0))
        delivery.emit(MalLibraryTrackingState.Delivered(attemptCount = 1))
        delivery.emit(
            MalLibraryTrackingState.Confirmed(
                snapshot(malId = 301L, progress = 2)
            )
        )
        runCurrent()

        val lifecycle = events.await()
        assertTrue(lifecycle[0] is MalLibraryEditLifecycle.Pending)
        assertTrue(lifecycle[1] is MalLibraryEditLifecycle.Delivered)
        val confirmed = lifecycle[2] as MalLibraryEditLifecycle.ProviderConfirmed
        assertTrue(confirmed.matchesRequestedState)
        assertEquals(2, confirmed.displayedItem.progress)
    }

    @Test
    fun `accepted late terminal failure emits failure then rollback`() = runTest {
        val delivery = MutableSharedFlow<MalLibraryTrackingState>(extraBufferCapacity = 2)
        val adapter = lifecycleAdapter(delivery)
        val original = item(malId = 302L, progress = 4)
        val accepted = adapter.submit(
            original,
            MalLibraryEditDraft.from(original).copy(progress = 9),
        ) as MalLibraryEditOutcome.Accepted
        val events = async(UnconfinedTestDispatcher(testScheduler)) {
            adapter.observe(accepted).take(2).toList()
        }

        delivery.emit(
            MalLibraryTrackingState.TerminalFailure(
                reason = TrackingFailureKind.PERMANENT,
                targetState = TrackingTargetState.FAILED,
            )
        )
        runCurrent()

        val lifecycle = events.await()
        val failure = lifecycle[0] as MalLibraryEditLifecycle.PermanentFailure
        val rollback = lifecycle[1] as MalLibraryEditLifecycle.RolledBack
        assertEquals(TrackingTargetState.FAILED, failure.terminalState)
        assertEquals(4, rollback.displayedItem.progress)
        assertEquals(9, rollback.failedOptimisticItem.progress)
    }

    @Test
    fun `retry exhaustion is terminal and rolls back`() = runTest {
        val lifecycle = terminalLifecycle(
            reason = TrackingFailureKind.RETRY_BUDGET_EXHAUSTED,
            state = TrackingTargetState.FAILED,
        )
        val rollback = lifecycle.last() as MalLibraryEditLifecycle.RolledBack
        assertEquals(TrackingFailureKind.RETRY_BUDGET_EXHAUSTED, rollback.reason)
        assertEquals(TrackingTargetState.FAILED, rollback.terminalState)
    }

    @Test
    fun `supersession is terminal and rolls back obsolete optimistic state`() = runTest {
        val lifecycle = terminalLifecycle(
            reason = TrackingFailureKind.PERMANENT,
            state = TrackingTargetState.SUPERSEDED,
        )
        val rollback = lifecycle.last() as MalLibraryEditLifecycle.RolledBack
        assertEquals(TrackingTargetState.SUPERSEDED, rollback.terminalState)
        assertEquals(3, rollback.displayedItem.progress)
    }

    @Test
    fun `provider read-back mismatch reconciles to provider value`() = runTest {
        val delivery = MutableSharedFlow<MalLibraryTrackingState>(extraBufferCapacity = 2)
        val adapter = lifecycleAdapter(delivery)
        val original = item(malId = 304L, progress = 5)
        val accepted = adapter.submit(
            original,
            MalLibraryEditDraft.from(original).copy(progress = 10, score100 = 85.0),
        ) as MalLibraryEditOutcome.Accepted
        val event = async(UnconfinedTestDispatcher(testScheduler)) {
            adapter.observe(accepted).take(1).toList().single()
        }

        delivery.emit(
            MalLibraryTrackingState.Confirmed(
                snapshot(malId = 304L, progress = 8, score100 = 90.0)
            )
        )
        runCurrent()

        val confirmed = event.await() as MalLibraryEditLifecycle.ProviderConfirmed
        assertFalse(confirmed.matchesRequestedState)
        assertEquals(8, confirmed.displayedItem.progress)
        assertEquals(90.0, confirmed.displayedItem.score100)
    }

    @Test
    fun `delete creates one MAL tombstone and durable success requires confirmed absence`() = runTest {
        val captured = mutableListOf<MalTrackingCommandInput>()
        val delivery = MutableSharedFlow<MalLibraryTrackingState>(extraBufferCapacity = 2)
        val adapter = MalLibraryTrackingAdapter(
            enqueueMal = { input ->
                captured += input
                TrackingEnqueueResult.Accepted(receipt())
            },
            observeDelivery = { delivery },
        )
        val original = item(malId = 305L, progress = 7)
        val accepted = adapter.delete(original) as MalLibraryEditOutcome.Accepted
        val events = async(UnconfinedTestDispatcher(testScheduler)) {
            adapter.observe(accepted).take(2).toList()
        }

        delivery.emit(MalLibraryTrackingState.Delivered(1))
        delivery.emit(
            MalLibraryTrackingState.Confirmed(
                snapshot(malId = 305L, progress = 0, deleted = true)
            )
        )
        runCurrent()

        assertEquals(1, captured.size)
        assertEquals(setOf(TrackingField.DELETE), captured.single().fields)
        assertTrue(captured.single().deleteIntent)
        val lifecycle = events.await()
        assertTrue(lifecycle[0] is MalLibraryEditLifecycle.Delivered)
        val confirmed = lifecycle[1] as MalLibraryEditLifecycle.ProviderConfirmed
        assertTrue(confirmed.deleted)
        assertTrue(confirmed.matchesRequestedState)
    }

    @Test
    fun `enqueue rejection no-op AniList identity and invalid date fail before delivery`() = runTest {
        val original = item(malId = 401L, progress = 4)
        val rejected = MalLibraryTrackingAdapter {
            TrackingEnqueueResult.Rejected(TrackingFailureKind.OFFLINE)
        }.submit(
            original,
            MalLibraryEditDraft.from(original).copy(progress = 9),
        ) as MalLibraryEditOutcome.Rejected
        assertTrue(rejected.retryable)
        assertEquals(4, rejected.displayedItem.progress)

        var calls = 0
        val noOp = MalLibraryTrackingAdapter {
            calls++
            TrackingEnqueueResult.Accepted(receipt())
        }.submit(original, MalLibraryEditDraft.from(original))
        assertTrue(noOp is MalLibraryEditOutcome.NoChange)
        assertEquals(0, calls)

        var captured: MalTrackingCommandInput? = null
        val aniListItem = original.copy(
            card = MediaListItemPresentation(
                identity = ProviderMediaIdentity.AniList(401, PresentationMediaType.ANIME),
                title = "AniList row",
                coverUrl = null,
                progress = 4,
            )
        )
        val identityRejected = MalLibraryTrackingAdapter { input ->
            captured = input
            TrackingEnqueueResult.Accepted(receipt())
        }.submit(aniListItem, MalLibraryEditDraft.from(aniListItem).copy(progress = 5))
        assertTrue(identityRejected is MalLibraryEditOutcome.Rejected)
        assertNull(captured)

        var invalidDateRejected = false
        try {
            MalLibraryEditDraft.from(original).copy(startedAt = "2026-02-30")
        } catch (_: IllegalArgumentException) {
            invalidDateRejected = true
        }
        assertTrue(invalidDateRejected)
    }

    private suspend fun TestScope.terminalLifecycle(
        reason: TrackingFailureKind,
        state: TrackingTargetState,
    ): List<MalLibraryEditLifecycle> {
        val delivery = MutableSharedFlow<MalLibraryTrackingState>(extraBufferCapacity = 2)
        val adapter = lifecycleAdapter(delivery)
        val original = item(malId = 303L, progress = 3)
        val accepted = adapter.submit(
            original,
            MalLibraryEditDraft.from(original).copy(progress = 8),
        ) as MalLibraryEditOutcome.Accepted
        val events = async(UnconfinedTestDispatcher(testScheduler)) {
            adapter.observe(accepted).take(2).toList()
        }
        delivery.emit(MalLibraryTrackingState.TerminalFailure(reason, state))
        runCurrent()
        return events.await()
    }

    private fun lifecycleAdapter(
        delivery: MutableSharedFlow<MalLibraryTrackingState>,
    ) = MalLibraryTrackingAdapter(
        enqueueMal = { TrackingEnqueueResult.Accepted(receipt()) },
        observeDelivery = { delivery },
    )

    private fun item(
        malId: Long,
        mediaType: PresentationMediaType = PresentationMediaType.ANIME,
        progress: Int = 0,
        secondaryProgress: Int? = null,
    ) = ProviderLibraryItem(
        card = MediaListItemPresentation(
            identity = ProviderMediaIdentity.MyAnimeList(malId, mediaType),
            title = "Title $malId",
            coverUrl = null,
            progress = progress,
        ),
        status = ProviderLibraryStatus.PLANNING,
        progress = progress,
        secondaryProgress = secondaryProgress,
        fetchedAtEpochMillis = 1L,
    )

    private fun snapshot(
        malId: Long,
        progress: Int,
        score100: Double? = null,
        deleted: Boolean = false,
    ) = MalLibraryConfirmedSnapshot(
        providerMediaId = malId,
        mediaType = TrackingMediaType.ANIME,
        title = "Confirmed $malId",
        coverUrl = null,
        status = if (deleted) null else TrackingStatus.PLANNING,
        progress = progress,
        progressSecondary = null,
        score100 = score100,
        repeatCount = 0,
        startedAt = null,
        completedAt = null,
        providerUpdatedAtEpochMillis = 100L,
        fetchedAtEpochMillis = 200L,
        deleted = deleted,
    )

    private fun receipt(operationId: String = "operation") = TrackingEnqueueReceipt(
        operationId = operationId,
        generation = 1L,
        deduplicated = false,
        provider = TrackingProvider.MYANIMELIST,
        targetState = TrackingTargetState.PENDING,
    )
}
