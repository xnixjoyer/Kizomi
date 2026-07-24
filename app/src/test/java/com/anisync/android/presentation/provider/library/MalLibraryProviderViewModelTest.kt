package com.anisync.android.presentation.provider.library

import com.anisync.android.data.mal.api.MalApiFailure
import com.anisync.android.data.mal.api.MalApiFailureKind
import com.anisync.android.data.mal.api.MalLibraryPresentationRecord
import com.anisync.android.data.mal.api.MalLibraryRefreshResult
import com.anisync.android.data.tracking.MalLibraryConfirmedSnapshot
import com.anisync.android.data.tracking.MalLibraryTrackingState
import com.anisync.android.data.tracking.MalTrackingCommandInput
import com.anisync.android.domain.tracking.TrackingDesiredState
import com.anisync.android.domain.tracking.TrackingEnqueueReceipt
import com.anisync.android.domain.tracking.TrackingEnqueueResult
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingProvider
import com.anisync.android.domain.tracking.TrackingStatus
import com.anisync.android.domain.tracking.TrackingTargetState
import com.anisync.android.presentation.model.PresentationMediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MalLibraryProviderViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `missing account produces explicit unauthenticated refresh state`() = runTest {
        val viewModel = viewModel(activeAccountId = { null })

        advanceUntilIdle()

        assertEquals(MalApiFailureKind.NOT_AUTHENTICATED, viewModel.uiState.value.lastFailure?.kind)
        assertFalse(viewModel.uiState.value.snapshot.isRefreshing)
    }

    @Test
    fun `anime manga switch changes observation and triggers provider refresh`() = runTest {
        val anime = MutableStateFlow(listOf(record(1L, TrackingMediaType.ANIME, 2)))
        val manga = MutableStateFlow(listOf(record(2L, TrackingMediaType.MANGA, 3)))
        val refreshes = mutableListOf<TrackingMediaType>()
        val viewModel = viewModel(
            observeLibrary = { _, type -> if (type == TrackingMediaType.ANIME) anime else manga },
            refreshLibrary = { _, type ->
                refreshes += type
                MalLibraryRefreshResult.Success(1, 0, 1)
            },
        )
        advanceUntilIdle()

        viewModel.onAction(
            MalLibraryProviderAction.SelectMediaType(PresentationMediaType.MANGA)
        )
        advanceUntilIdle()

        assertEquals(listOf(TrackingMediaType.ANIME, TrackingMediaType.MANGA), refreshes)
        assertEquals(PresentationMediaType.MANGA, viewModel.uiState.value.snapshot.query.mediaType)
        assertEquals(3, viewModel.uiState.value.snapshot.visibleItems.single().progress)
    }

    @Test
    fun `stale last good content remains visible during refresh failure`() = runTest {
        val records = MutableStateFlow(
            listOf(record(3L, TrackingMediaType.ANIME, progress = 4, fetchedAt = 1L))
        )
        val viewModel = viewModel(
            observeLibrary = { _, _ -> records },
            refreshLibrary = { _, _ ->
                MalLibraryRefreshResult.Failure(
                    error = MalApiFailure(MalApiFailureKind.OFFLINE),
                    preservedEntryCount = 1,
                )
            },
        )

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.snapshot.hasStaleContent)
        assertEquals(1, viewModel.uiState.value.snapshot.visibleItems.size)
        assertEquals(MalApiFailureKind.OFFLINE, viewModel.uiState.value.lastFailure?.kind)
    }

    @Test
    fun `accepted pending delivered and confirmed remain distinct and only confirmed clears overlay`() = runTest {
        val records = MutableStateFlow(listOf(record(4L, TrackingMediaType.ANIME, 4)))
        val delivery = MutableSharedFlow<MalLibraryTrackingState>(extraBufferCapacity = 4)
        val viewModel = viewModel(
            observeLibrary = { _, _ -> records },
            tracking = tracking(delivery),
        )
        advanceUntilIdle()
        val original = viewModel.uiState.value.snapshot.visibleItems.single()
        val events = mutableListOf<MalLibraryEditLifecycle>()
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.editOutcomes.take(4).toList(events)
        }

        viewModel.onAction(
            MalLibraryProviderAction.SubmitEdit(
                original,
                MalLibraryEditDraft.from(original).copy(progress = 9),
            )
        )
        runCurrent()
        assertTrue(events[0] is MalLibraryEditLifecycle.EnqueueAccepted)
        assertEquals(9, viewModel.uiState.value.snapshot.visibleItems.single().progress)

        delivery.emit(MalLibraryTrackingState.Pending(TrackingTargetState.PENDING, 0))
        runCurrent()
        assertTrue(events[1] is MalLibraryEditLifecycle.Pending)
        assertEquals(9, viewModel.uiState.value.snapshot.visibleItems.single().progress)

        delivery.emit(MalLibraryTrackingState.Delivered(1))
        runCurrent()
        assertTrue(events[2] is MalLibraryEditLifecycle.Delivered)
        assertEquals(9, viewModel.uiState.value.snapshot.visibleItems.single().progress)

        records.value = listOf(record(4L, TrackingMediaType.ANIME, progress = 9, fetchedAt = 200L))
        delivery.emit(MalLibraryTrackingState.Confirmed(snapshot(4L, 9)))
        advanceUntilIdle()
        assertTrue(events[3] is MalLibraryEditLifecycle.ProviderConfirmed)
        assertEquals(9, viewModel.uiState.value.snapshot.visibleItems.single().progress)
        collection.cancel()
    }

    @Test
    fun `late terminal retry exhaustion rolls back optimistic state`() = runTest {
        val records = MutableStateFlow(listOf(record(5L, TrackingMediaType.ANIME, 5)))
        val delivery = MutableSharedFlow<MalLibraryTrackingState>(extraBufferCapacity = 2)
        val viewModel = viewModel(
            observeLibrary = { _, _ -> records },
            tracking = tracking(delivery),
        )
        advanceUntilIdle()
        val original = viewModel.uiState.value.snapshot.visibleItems.single()
        val events = mutableListOf<MalLibraryEditLifecycle>()
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.editOutcomes.take(3).toList(events)
        }

        viewModel.onAction(
            MalLibraryProviderAction.SubmitEdit(
                original,
                MalLibraryEditDraft.from(original).copy(progress = 10),
            )
        )
        runCurrent()
        delivery.emit(
            MalLibraryTrackingState.TerminalFailure(
                TrackingFailureKind.RETRY_BUDGET_EXHAUSTED,
                TrackingTargetState.FAILED,
            )
        )
        advanceUntilIdle()

        assertTrue(events[0] is MalLibraryEditLifecycle.EnqueueAccepted)
        assertTrue(events[1] is MalLibraryEditLifecycle.PermanentFailure)
        val rollback = events[2] as MalLibraryEditLifecycle.RolledBack
        assertEquals(TrackingFailureKind.RETRY_BUDGET_EXHAUSTED, rollback.reason)
        assertEquals(5, viewModel.uiState.value.snapshot.visibleItems.single().progress)
        collection.cancel()
    }

    @Test
    fun `superseded operation rolls back obsolete optimistic state`() = runTest {
        val records = MutableStateFlow(listOf(record(6L, TrackingMediaType.ANIME, 6)))
        val delivery = MutableSharedFlow<MalLibraryTrackingState>(extraBufferCapacity = 2)
        val viewModel = viewModel(
            observeLibrary = { _, _ -> records },
            tracking = tracking(delivery),
        )
        advanceUntilIdle()
        val original = viewModel.uiState.value.snapshot.visibleItems.single()

        viewModel.onAction(
            MalLibraryProviderAction.SubmitEdit(
                original,
                MalLibraryEditDraft.from(original).copy(progress = 11),
            )
        )
        runCurrent()
        delivery.emit(
            MalLibraryTrackingState.TerminalFailure(
                TrackingFailureKind.PERMANENT,
                TrackingTargetState.SUPERSEDED,
            )
        )
        advanceUntilIdle()

        val lifecycle = viewModel.uiState.value.editStates[original.identity.stableKey]
            as MalLibraryEditLifecycle.RolledBack
        assertEquals(TrackingTargetState.SUPERSEDED, lifecycle.terminalState)
        assertEquals(6, viewModel.uiState.value.snapshot.visibleItems.single().progress)
    }

    @Test
    fun `provider mismatch replaces optimistic state with confirmed provider snapshot`() = runTest {
        val records = MutableStateFlow(listOf(record(7L, TrackingMediaType.ANIME, 7)))
        val delivery = MutableSharedFlow<MalLibraryTrackingState>(extraBufferCapacity = 2)
        val viewModel = viewModel(
            observeLibrary = { _, _ -> records },
            tracking = tracking(delivery),
        )
        advanceUntilIdle()
        val original = viewModel.uiState.value.snapshot.visibleItems.single()

        viewModel.onAction(
            MalLibraryProviderAction.SubmitEdit(
                original,
                MalLibraryEditDraft.from(original).copy(progress = 12),
            )
        )
        runCurrent()
        records.value = listOf(record(7L, TrackingMediaType.ANIME, progress = 8, fetchedAt = 200L))
        delivery.emit(MalLibraryTrackingState.Confirmed(snapshot(7L, 8)))
        advanceUntilIdle()

        val confirmed = viewModel.uiState.value.editStates[original.identity.stableKey]
            as MalLibraryEditLifecycle.ProviderConfirmed
        assertFalse(confirmed.matchesRequestedState)
        assertEquals(8, viewModel.uiState.value.snapshot.visibleItems.single().progress)
    }

    @Test
    fun `retry and delete actions preserve one MAL target per action`() = runTest {
        val records = MutableStateFlow(listOf(record(8L, TrackingMediaType.ANIME, 8)))
        val captured = mutableListOf<MalTrackingCommandInput>()
        var rejectFirst = true
        val tracking = MalLibraryTrackingAdapter(
            enqueueMal = { input ->
                captured += input
                if (rejectFirst) {
                    rejectFirst = false
                    TrackingEnqueueResult.Rejected(TrackingFailureKind.OFFLINE)
                } else {
                    TrackingEnqueueResult.Accepted(receipt("operation-${captured.size}"))
                }
            },
            observeDelivery = { MutableSharedFlow() },
        )
        val viewModel = viewModel(observeLibrary = { _, _ -> records }, tracking = tracking)
        advanceUntilIdle()
        val original = viewModel.uiState.value.snapshot.visibleItems.single()
        val draft = MalLibraryEditDraft.from(original).copy(progress = 9)

        viewModel.onAction(MalLibraryProviderAction.SubmitEdit(original, draft))
        runCurrent()
        viewModel.onAction(MalLibraryProviderAction.RetryEdit(original, draft))
        runCurrent()
        viewModel.onAction(MalLibraryProviderAction.Delete(original))
        runCurrent()

        assertEquals(3, captured.size)
        assertEquals(1, captured.count { it.deleteIntent })
        assertTrue(captured.all { it.malMediaId == 8L })
    }

    private fun viewModel(
        observeLibrary: (String, TrackingMediaType) -> Flow<List<MalLibraryPresentationRecord>> =
            { _, _ -> MutableStateFlow(emptyList()) },
        refreshLibrary: suspend (String, TrackingMediaType) -> MalLibraryRefreshResult =
            { _, _ -> MalLibraryRefreshResult.Success(0, 0, 1) },
        activeAccountId: suspend () -> String? = { "account" },
        tracking: MalLibraryTrackingAdapter = MalLibraryTrackingAdapter {
            TrackingEnqueueResult.Accepted(receipt())
        },
    ) = MalLibraryProviderViewModel(
        observeLibrary = observeLibrary,
        refreshLibrary = refreshLibrary,
        activeAccountId = activeAccountId,
        tracking = tracking,
    )

    private fun tracking(
        delivery: MutableSharedFlow<MalLibraryTrackingState>,
    ) = MalLibraryTrackingAdapter(
        enqueueMal = { TrackingEnqueueResult.Accepted(receipt()) },
        observeDelivery = { delivery },
    )

    private fun record(
        malId: Long,
        type: TrackingMediaType,
        progress: Int,
        fetchedAt: Long = System.currentTimeMillis(),
    ) = MalLibraryPresentationRecord(
        localMediaId = "local-$malId",
        malId = malId,
        mediaType = type,
        title = "Title $malId",
        alternativeTitles = emptyList(),
        coverUrl = null,
        state = TrackingDesiredState(
            status = TrackingStatus.PLANNING,
            progress = progress,
        ),
        totalPrimary = null,
        totalSecondary = null,
        mediaStartDate = null,
        providerUpdatedAtEpochMillis = null,
        fetchedAtEpochMillis = fetchedAt,
    )

    private fun snapshot(
        malId: Long,
        progress: Int,
        deleted: Boolean = false,
    ) = MalLibraryConfirmedSnapshot(
        providerMediaId = malId,
        mediaType = TrackingMediaType.ANIME,
        title = "Confirmed $malId",
        coverUrl = null,
        status = if (deleted) null else TrackingStatus.PLANNING,
        progress = progress,
        progressSecondary = null,
        score100 = null,
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
