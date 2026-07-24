package com.anisync.android.presentation.provider.library

import com.anisync.android.data.local.entity.ProviderTrackingSnapshotEntity
import com.anisync.android.data.tracking.MalTrackingCommandInput
import com.anisync.android.domain.tracking.TrackingEnqueueReceipt
import com.anisync.android.domain.tracking.TrackingEnqueueResult
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingField
import com.anisync.android.domain.tracking.TrackingProvider
import com.anisync.android.domain.tracking.TrackingTargetState
import com.anisync.android.presentation.model.MediaListItemPresentation
import com.anisync.android.presentation.model.PresentationMediaType
import com.anisync.android.presentation.model.ProviderMediaIdentity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
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
    fun `one edit creates exactly one MAL command with no AniList identity`() = runTest {
        val captured = mutableListOf<MalTrackingCommandInput>()
        val adapter = MalLibraryTrackingAdapter { input ->
            captured += input
            TrackingEnqueueResult.Accepted(receipt())
        }
        val original = item(malId = 101L, progress = 2)

        val outcome = adapter.submit(
            original = original,
            draft = MalLibraryEditDraft.from(original).copy(
                status = ProviderLibraryStatus.CURRENT,
                progress = 3,
                score100 = 80.0,
                startedAt = "2026-01-02",
            ),
        )

        assertEquals(1, captured.size)
        assertEquals(101L, captured.single().malMediaId)
        assertEquals(
            setOf(
                TrackingField.STATUS,
                TrackingField.PROGRESS,
                TrackingField.SCORE,
                TrackingField.STARTED_AT,
            ),
            captured.single().fields,
        )
        assertTrue(outcome is MalLibraryEditOutcome.Accepted)
        val accepted = outcome as MalLibraryEditOutcome.Accepted
        assertEquals(3, accepted.displayedItem.progress)
        assertEquals(2, accepted.rollbackItem.progress)
        assertEquals(TrackingProvider.MYANIMELIST, accepted.receipt.provider)
        assertFalse(accepted.deleteIntent)
    }

    @Test
    fun `manga edit carries chapter and volume progress while anime rejects secondary progress`() = runTest {
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
        assertEquals(2, captured.single().desired.progressSecondary)
        assertTrue(TrackingField.PROGRESS_SECONDARY in captured.single().fields)
        assertTrue(animeOutcome is MalLibraryEditOutcome.Rejected)
        assertEquals(
            TrackingFailureKind.UNSUPPORTED_FIELD,
            (animeOutcome as MalLibraryEditOutcome.Rejected).reason,
        )
        assertEquals(1, captured.size)
    }

    @Test
    fun `enqueue accepted and pending are distinct states`() = runTest {
        val delivery = MutableSharedFlow<MalLibraryDeliveryState>(extraBufferCapacity = 4)
        val adapter = lifecycleAdapter(delivery)
        val original = item(malId = 301L, progress = 1)
        val accepted = adapter.submit(
            original,
            MalLibraryEditDraft.from(original).copy(progress = 2),
        ) as MalLibraryEditOutcome.Accepted

        assertEquals(TrackingTargetState.PENDING, accepted.receipt.targetState)
        val event = async(UnconfinedTestDispatcher(testScheduler)) {
            adapter.observe(accepted).take(1).toList().single()
        }
        delivery.emit(MalLibraryDeliveryState.Pending(TrackingTargetState.PENDING, 0))
        runCurrent()

        val pending = event.await() as MalLibraryEditLifecycle.Pending
        assertEquals(2, pending.displayedItem.progress)
        assertEquals(1, pending.rollbackItem.progress)
        assertEquals(0, pending.attemptCount)
    }

    @Test
    fun `retryable delivery failure remains pending and keeps retry draft`() = runTest {
        val delivery = MutableSharedFlow<MalLibraryDeliveryState>(extraBufferCapacity = 4)
        val adapter = lifecycleAdapter(delivery)
        val original = item(malId = 302L, progress = 3)
        val accepted = adapter.submit(
            original,
            MalLibraryEditDraft.from(original).copy(progress = 4),
        ) as MalLibraryEditOutcome.Accepted
        val event = async(UnconfinedTestDispatcher(testScheduler)) {
            adapter.observe(accepted).take(1).toList().single()
        }

        delivery.emit(
            MalLibraryDeliveryState.RetryableFailure(
                reason = TrackingFailureKind.RATE_LIMITED,
                attemptCount = 2,
                retryAfterMillis = 12_000L,
            )
        )
        runCurrent()

        val retryable = event.await() as MalLibraryEditLifecycle.RetryableFailure
        assertEquals(4, retryable.displayedItem.progress)
        assertEquals(4, retryable.retryDraft.progress)
        assertEquals(TrackingFailureKind.RATE_LIMITED, retryable.reason)
        assertEquals(12_000L, retryable.retryAfterMillis)
    }

    @Test
    fun `accepted command that later fails permanently emits failure then rollback`() = runTest {
        val delivery = MutableSharedFlow<MalLibraryDeliveryState>(extraBufferCapacity = 4)
        val adapter = lifecycleAdapter(delivery)
        val original = item(malId = 303L, progress = 4)
        val accepted = adapter.submit(
            original,
            MalLibraryEditDraft.from(original).copy(progress = 9),
        ) as MalLibraryEditOutcome.Accepted
        val events = async(UnconfinedTestDispatcher(testScheduler)) {
            adapter.observe(accepted).take(2).toList()
        }

        delivery.emit(MalLibraryDeliveryState.PermanentFailure(TrackingFailureKind.PERMANENT))
        runCurrent()

        val lifecycle = events.await()
        val failure = lifecycle[0] as MalLibraryEditLifecycle.PermanentFailure
        val rollback = lifecycle[1] as MalLibraryEditLifecycle.RolledBack
        assertEquals(9, failure.displayedItem.progress)
        assertEquals(4, failure.rollbackItem.progress)
        assertEquals(4, rollback.displayedItem.progress)
        assertEquals(9, rollback.failedOptimisticItem.progress)
        assertEquals(TrackingFailureKind.PERMANENT, rollback.reason)
    }

    @Test
    fun `provider confirmed read-back reconciles mismatch instead of claiming requested state`() = runTest {
        val delivery = MutableSharedFlow<MalLibraryDeliveryState>(extraBufferCapacity = 4)
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
            MalLibraryDeliveryState.ProviderConfirmed(
                snapshot(
                    malId = 304L,
                    progress = 8,
                    score100 = 90.0,
                )
            )
        )
        runCurrent()

        val confirmed = event.await() as MalLibraryEditLifecycle.ProviderConfirmed
        assertEquals(8, confirmed.displayedItem.progress)
        assertEquals(90.0, confirmed.displayedItem.score100)
        assertFalse(confirmed.matchesRequestedState)
        assertFalse(confirmed.deleted)
    }

    @Test
    fun `matching provider read-back is the only durable success state`() = runTest {
        val delivery = MutableSharedFlow<MalLibraryDeliveryState>(extraBufferCapacity = 4)
        val adapter = lifecycleAdapter(delivery)
        val original = item(malId = 305L, progress = 5)
        val accepted = adapter.submit(
            original,
            MalLibraryEditDraft.from(original).copy(progress = 6, score100 = 85.0),
        ) as MalLibraryEditOutcome.Accepted
        val event = async(UnconfinedTestDispatcher(testScheduler)) {
            adapter.observe(accepted).take(1).toList().single()
        }

        delivery.emit(
            MalLibraryDeliveryState.ProviderConfirmed(
                snapshot(
                    malId = 305L,
                    progress = 6,
                    score100 = 90.0,
                )
            )
        )
        runCurrent()

        val confirmed = event.await() as MalLibraryEditLifecycle.ProviderConfirmed
        assertTrue(confirmed.matchesRequestedState)
        assertEquals(6, confirmed.displayedItem.progress)
    }

    @Test
    fun `delete creates one MAL tombstone target and waits for confirmed deletion`() = runTest {
        val captured = mutableListOf<MalTrackingCommandInput>()
        val delivery = MutableSharedFlow<MalLibraryDeliveryState>(extraBufferCapacity = 4)
        val adapter = MalLibraryTrackingAdapter(
            enqueueMal = { input ->
                captured += input
                TrackingEnqueueResult.Accepted(receipt())
            },
            observeDelivery = { delivery },
        )
        val original = item(malId = 306L, progress = 7)
        val accepted = adapter.delete(original) as MalLibraryEditOutcome.Accepted
        val event = async(UnconfinedTestDispatcher(testScheduler)) {
            adapter.observe(accepted).take(1).toList().single()
        }

        assertEquals(1, captured.size)
        assertEquals(setOf(TrackingField.DELETE), captured.single().fields)
        assertTrue(captured.single().deleteIntent)
        delivery.emit(
            MalLibraryDeliveryState.ProviderConfirmed(
                snapshot(malId = 306L, progress = 0, deleted = true)
            )
        )
        runCurrent()

        val confirmed = event.await() as MalLibraryEditLifecycle.ProviderConfirmed
        assertTrue(confirmed.deleted)
        assertTrue(confirmed.matchesRequestedState)
    }

    @Test
    fun `immediate permanent rejection restores last good item`() = runTest {
        val adapter = MalLibraryTrackingAdapter {
            TrackingEnqueueResult.Rejected(TrackingFailureKind.PERMANENT)
        }
        val original = item(malId = 307L, progress = 4)

        val outcome = adapter.submit(
            original,
            MalLibraryEditDraft.from(original).copy(progress = 9),
        ) as MalLibraryEditOutcome.Rejected

        assertEquals(4, outcome.displayedItem.progress)
        assertEquals(9, outcome.retryDraft.progress)
        assertFalse(outcome.retryable)
    }

    @Test
    fun `transient enqueue rejection keeps retry draft and restores last good item`() = runTest {
        val adapter = MalLibraryTrackingAdapter {
            TrackingEnqueueResult.Rejected(TrackingFailureKind.OFFLINE)
        }
        val original = item(malId = 308L, progress = 7)

        val outcome = adapter.submit(
            original,
            MalLibraryEditDraft.from(original).copy(progress = 8),
        ) as MalLibraryEditOutcome.Rejected

        assertEquals(7, outcome.displayedItem.progress)
        assertEquals(8, outcome.retryDraft.progress)
        assertTrue(outcome.retryable)
    }

    @Test
    fun `unchanged draft performs no write`() = runTest {
        var calls = 0
        val adapter = MalLibraryTrackingAdapter {
            calls++
            TrackingEnqueueResult.Accepted(receipt())
        }
        val original = item(malId = 309L, progress = 1)

        val outcome = adapter.submit(original, MalLibraryEditDraft.from(original))

        assertTrue(outcome is MalLibraryEditOutcome.NoChange)
        assertEquals(0, calls)
    }

    @Test
    fun `AniList identity is rejected before any MAL command is created`() = runTest {
        var captured: MalTrackingCommandInput? = null
        val adapter = MalLibraryTrackingAdapter { input ->
            captured = input
            TrackingEnqueueResult.Accepted(receipt())
        }
        val original = item(malId = 310L).copy(
            card = MediaListItemPresentation(
                identity = ProviderMediaIdentity.AniList(310, PresentationMediaType.ANIME),
                title = "AniList row",
                coverUrl = null,
                progress = 0,
            )
        )

        val outcome = adapter.submit(
            original,
            MalLibraryEditDraft.from(original).copy(progress = 1),
        )

        assertTrue(outcome is MalLibraryEditOutcome.Rejected)
        assertEquals(
            TrackingFailureKind.VALIDATION,
            (outcome as MalLibraryEditOutcome.Rejected).reason,
        )
        assertNull(captured)
    }

    @Test
    fun `invalid calendar date is rejected before command construction`() {
        val original = item(malId = 311L)
        var rejected = false

        try {
            MalLibraryEditDraft.from(original).copy(startedAt = "2026-02-30")
        } catch (_: IllegalArgumentException) {
            rejected = true
        }

        assertTrue(rejected)
        assertTrue("2024-02-29".isValidMalLibraryDate())
        assertFalse("2025-02-29".isValidMalLibraryDate())
    }

    private fun lifecycleAdapter(
        delivery: MutableSharedFlow<MalLibraryDeliveryState>,
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
    ) = ProviderTrackingSnapshotEntity(
        provider = TrackingProvider.MYANIMELIST.name,
        providerAccountId = "account",
        localMediaId = "local-$malId",
        providerMediaId = malId,
        providerListEntryId = null,
        mediaType = "ANIME",
        title = "Confirmed $malId",
        coverUrl = null,
        status = if (deleted) "DELETED" else "PLANNING",
        progress = progress,
        progressSecondary = null,
        score = score100,
        repeatCount = 0,
        notes = null,
        startedAt = null,
        completedAt = null,
        providerUpdatedAtEpochMillis = 100L,
        fetchedAtEpochMillis = 200L,
        isDeleted = deleted,
    )

    private fun receipt() = TrackingEnqueueReceipt(
        operationId = "operation",
        generation = 1L,
        deduplicated = false,
        provider = TrackingProvider.MYANIMELIST,
        targetState = TrackingTargetState.PENDING,
    )
}
