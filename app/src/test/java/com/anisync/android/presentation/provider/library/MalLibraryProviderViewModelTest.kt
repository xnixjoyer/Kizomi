package com.anisync.android.presentation.provider.library

import com.anisync.android.data.local.entity.ProviderTrackingSnapshotEntity
import com.anisync.android.data.mal.api.MalLibraryPresentationRecord
import com.anisync.android.data.mal.api.MalLibraryRefreshResult
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
        val viewModel = viewModel(
            activeAccountId = { null },
        )

        advanceUntilIdle()

        assertEquals(
            com.anisync.android.data.mal.api.MalApiFailureKind.NOT_AUTHENTICATED,
            viewModel.uiState.value.lastFailure?.kind,
        )
        assertFalse(viewModel.uiState.value.snapshot.isRefreshing)
    }

    @Test
    fun `anime manga switch changes observation and triggers provider refresh`() = runTest {
        val anime = MutableStateFlow(listOf(record(1L, TrackingMediaType.ANIME, progress = 2)))
        val manga = MutableStateFlow(listOf(record(2L, TrackingMediaType.MANGA, progress = 3)))
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
        assertEquals(2L, viewModel.uiState.value.snapshot.visibleItems.single().identity.let {
            (it as com.anisync.android.presentation.model.ProviderMediaIdentity.MyAnimeList).malId
        })
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
                    error = com.anisync.android.data.mal.api.MalApiFailure(
                        com.anisync.android.data.mal.api.MalApiFailureKind.OFFLINE
                    ),
                    preservedEntryCount = 1,
                )
            },
        )

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.snapshot.hasStaleContent)
        assertEquals(1, viewModel.uiState.value.snapshot.visibleItems.size)
        assertEquals(
            com.anisync.android.data.mal.api.MalApiFailureKind.OFFLINE,
            viewModel.uiState.value.lastFailure?.kind,
        )
    }

    @Test
    fun `accepted pending late permanent failure and rollback are distinct viewmodel states`() = runTest {
        val records = MutableStateFlow(listOf(record(4L, TrackingMediaType.ANIME, progress = 4)))
        val delivery = MutableSharedFlow<MalLibraryDeliveryState>(extraBufferCapacity = 4)
        val tracking = tracking(delivery)
        val viewModel = viewModel(
            observeLibrary = { _, _ -> records },
            tracking = tracking,
        )
        advanceUntilIdle()
        val original = viewModel.uiState.value.snapshot.visibleItems.single()
        val events = mutableListOf<MalLibraryEditLifecycle>()
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.editOutcomes.take(4).toList(events)
        }

        viewModel.onAction(
            MalLibraryProviderAction.SubmitEdit(
                item = original,
                draft = MalLibraryEditDraft.from(original).copy(progress = 9),
            )
        )
        runCurrent()
        assertEquals(9, viewModel.uiState.value.snapshot.visibleItems.single().progress)
        assertTrue(events[0] is MalLibraryEditLifecycle.EnqueueAccepted)

        delivery.emit(MalLibraryDeliveryState.Pending(TrackingTargetState.PENDING, 0))
        runCurrent()
        delivery.emit(MalLibraryDeliveryState.PermanentFailure(TrackingFailureKind.PERMANENT))
        advanceUntilIdle()

        assertTrue(events[1] is MalLibraryEditLifecycle.Pending)
        assertTrue(events[2] is MalLibraryEditLifecycle.PermanentFailure)
        assertTrue(events[3] is MalLibraryEditLifecycle.RolledBack)
        assertEquals(4, viewModel.uiState.value.snapshot.visibleItems.single().progress)
        assertTrue(
            viewModel.uiState.value.editStates[original.identity.stableKey] is
                MalLibraryEditLifecycle.RolledBack
        )
        collection.cancel()
    }

    @Test
    fun `provider read back mismatch replaces optimistic state with confirmed snapshot`() = runTest {
        val records = MutableStateFlow(listOf(record(5L, TrackingMediaType.ANIME, progress = 5)))
        val delivery = MutableSharedFlow<MalLibraryDeliveryState>(extraBufferCapacity = 4)
        val viewModel = viewModel(
            observeLibrary = { _, _ -> records },
            tracking = tracking(delivery),
        )
        advanceUntilIdle()
        val original = viewModel.uiState.value.snapshot.visibleItems.single()

        viewModel.onAction(
            MalLibraryProviderAction.SubmitEdit(
                item = original,
                draft = MalLibraryEditDraft.from(original).copy(progress = 10),
            )
        )
        runCurrent()
        assertEquals(10, viewModel.uiState.value.snapshot.visibleItems.single().progress)

        records.value = listOf(record(5L, TrackingMediaType.ANIME, progress = 8, fetchedAt = 200L))
        delivery.emit(
            MalLibraryDeliveryState.ProviderConfirmed(
                snapshot(malId = 5L, progress = 8)
            )
        )
        advanceUntilIdle()

        val confirmed = viewModel.uiState.value.editStates[original.identity.stableKey]
            as MalLibraryEditLifecycle.ProviderConfirmed
        assertFalse(confirmed.matchesRequestedState)
        assertEquals(8, confirmed.displayedItem.progress)
        assertEquals(8, viewModel.uiState.value.snapshot.visibleItems.single().progress)
    }

    @Test
    fun `delete and retry actions each retain single MAL target semantics`() = runTest {
        val records = MutableStateFlow(listOf(record(6L, TrackingMediaType.ANIME, progress = 6)))
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
        val viewModel = viewModel(
            observeLibrary = { _, _ -> records },
            tracking = tracking,
        )
        advanceUntilIdle()
        val original = viewModel.uiState.value.snapshot.visibleItems.single()
        val draft = MalLibraryEditDraft.from(original).copy(progress = 7)

        viewModel.onAction(MalLibraryProviderAction.SubmitEdit(original, draft))
        runCurrent()
        viewModel.onAction(MalLibraryProviderAction.RetryEdit(original, draft))
        runCurrent()
        viewModel.onAction(MalLibraryProviderAction.Delete(original))
        runCurrent()

        assertEquals(3, captured.size)
        assertEquals(1, captured.count { it.deleteIntent })
        assertTrue(captured.all { it.malMediaId == 6L })
    }

    private fun viewModel(
        observeLibrary: (String, TrackingMediaType) -> kotlinx.coroutines.flow.Flow<List<MalLibraryPresentationRecord>> =
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
        delivery: MutableSharedFlow<MalLibraryDeliveryState>,
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
    ) = ProviderTrackingSnapshotEntity(
        provider = TrackingProvider.MYANIMELIST.name,
        providerAccountId = "account",
        localMediaId = "local-$malId",
        providerMediaId = malId,
        providerListEntryId = null,
        mediaType = TrackingMediaType.ANIME.name,
        title = "Confirmed $malId",
        coverUrl = null,
        status = TrackingStatus.PLANNING.name,
        progress = progress,
        progressSecondary = null,
        score = null,
        repeatCount = 0,
        notes = null,
        startedAt = null,
        completedAt = null,
        providerUpdatedAtEpochMillis = 100L,
        fetchedAtEpochMillis = 200L,
        isDeleted = false,
    )

    private fun receipt(operationId: String = "operation") = TrackingEnqueueReceipt(
        operationId = operationId,
        generation = 1L,
        deduplicated = false,
        provider = TrackingProvider.MYANIMELIST,
        targetState = TrackingTargetState.PENDING,
    )
}
