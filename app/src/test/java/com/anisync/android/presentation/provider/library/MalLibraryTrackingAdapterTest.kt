package com.anisync.android.presentation.provider.library

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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertEquals(2, captured.single().desired.progressSecondary)
        assertTrue(TrackingField.PROGRESS_SECONDARY in captured.single().fields)
        assertTrue(animeOutcome is MalLibraryEditOutcome.Rejected)
        assertEquals(TrackingFailureKind.UNSUPPORTED_FIELD, (animeOutcome as MalLibraryEditOutcome.Rejected).reason)
        assertEquals(1, captured.size)
    }

    @Test
    fun `permanent rejection rolls back optimistic state`() = runTest {
        val adapter = MalLibraryTrackingAdapter {
            TrackingEnqueueResult.Rejected(TrackingFailureKind.PERMANENT)
        }
        val original = item(malId = 303L, progress = 4)

        val outcome = adapter.submit(
            original,
            MalLibraryEditDraft.from(original).copy(progress = 9),
        )

        assertTrue(outcome is MalLibraryEditOutcome.Rejected)
        val rejected = outcome as MalLibraryEditOutcome.Rejected
        assertEquals(4, rejected.displayedItem.progress)
        assertEquals(9, rejected.retryDraft.progress)
        assertFalse(rejected.retryable)
    }

    @Test
    fun `transient rejection keeps a retry draft and restores last good item`() = runTest {
        val adapter = MalLibraryTrackingAdapter {
            TrackingEnqueueResult.Rejected(TrackingFailureKind.OFFLINE)
        }
        val original = item(malId = 404L, progress = 7)

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
        val original = item(malId = 505L, progress = 1)

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
        val original = item(malId = 606L).copy(
            card = MediaListItemPresentation(
                identity = ProviderMediaIdentity.AniList(606, PresentationMediaType.ANIME),
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
        assertEquals(TrackingFailureKind.VALIDATION, (outcome as MalLibraryEditOutcome.Rejected).reason)
        assertNull(captured)
    }

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

    private fun receipt() = TrackingEnqueueReceipt(
        operationId = "operation",
        generation = 1L,
        deduplicated = false,
        provider = TrackingProvider.MYANIMELIST,
        targetState = TrackingTargetState.PENDING,
    )
}
