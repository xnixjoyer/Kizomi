package com.anisync.android.data.tracking

import android.content.Context
import androidx.room.Room
import com.anisync.android.data.identity.LocalMediaIdentity
import com.anisync.android.data.identity.MediaIdentityResult
import com.anisync.android.data.local.AppDatabase
import com.anisync.android.data.local.entity.LocalMediaIdentityEntity
import com.anisync.android.data.local.entity.ProviderTrackingSnapshotEntity
import com.anisync.android.domain.tracking.TrackingCommandDraft
import com.anisync.android.domain.tracking.TrackingCommandTarget
import com.anisync.android.domain.tracking.TrackingConfirmedSnapshot
import com.anisync.android.domain.tracking.TrackingDeliveryResult
import com.anisync.android.domain.tracking.TrackingDesiredState
import com.anisync.android.domain.tracking.TrackingEnqueueReceipt
import com.anisync.android.domain.tracking.TrackingEnqueueResult
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingField
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingOperationState
import com.anisync.android.domain.tracking.TrackingProvider
import com.anisync.android.domain.tracking.TrackingProviderAdapter
import com.anisync.android.domain.tracking.TrackingProviderRequest
import com.anisync.android.domain.tracking.TrackingStatus
import com.anisync.android.domain.tracking.TrackingTargetState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TrackingSagaRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var scheduler: RecordingScheduler

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scheduler = RecordingScheduler()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `partial failure exposes each provider and retry reopens only failed provider`() = runTest {
        database.mediaIdentityDao().insertLocalIdentity(
            LocalMediaIdentityEntity("local-anime", "ANIME", 1L, 1L)
        )
        val outbox = TrackingOutboxRepository(
            database,
            database.trackingDao(),
            TrackingCommandCodec(),
            scheduler,
        )
        val receipt = (outbox.enqueue(
            TrackingCommandDraft(
                localMediaId = "local-anime",
                mediaType = TrackingMediaType.ANIME,
                desired = TrackingDesiredState(TrackingStatus.CURRENT, 5),
                fields = setOf(TrackingField.STATUS, TrackingField.PROGRESS),
            ),
            listOf(
                TrackingCommandTarget(TrackingProvider.ANILIST, "ani", 10L),
                TrackingCommandTarget(TrackingProvider.MYANIMELIST, "mal", 20L),
            ),
        ) as TrackingEnqueueResult.Accepted).receipt
        val aniList = adapter(TrackingProvider.ANILIST) { request ->
            TrackingDeliveryResult.Success(
                TrackingConfirmedSnapshot(state = request.command.draft.desired)
            )
        }
        val mal = adapter(TrackingProvider.MYANIMELIST) {
            TrackingDeliveryResult.TerminalFailure(TrackingFailureKind.VALIDATION, 422)
        }
        TrackingOutboxExecutor(
            database.trackingDao(),
            TrackingCommandCodec(),
            setOf(aniList, mal),
        ).drain(maxDeliveries = 2)

        val saga = repository()
        val operation = saga.observeOperations().first().single()
        assertEquals(TrackingOperationState.PARTIAL_FAILURE, operation.state)
        assertEquals(TrackingTargetState.SUCCEEDED, operation.targets.single {
            it.provider == TrackingProvider.ANILIST
        }.state)
        assertEquals(TrackingFailureKind.VALIDATION, operation.targets.single {
            it.provider == TrackingProvider.MYANIMELIST
        }.failureKind)
        scheduler.calls = 0

        val retried = saga.retryFailed(
            receipt.operationId,
            setOf(TrackingProvider.ANILIST, TrackingProvider.MYANIMELIST),
        )
        val targets = database.trackingDao().getTargets(receipt.operationId).associateBy { it.provider }
        assertEquals(1, retried)
        assertEquals("SUCCEEDED", targets.getValue("ANILIST").state)
        assertEquals("RETRYING", targets.getValue("MYANIMELIST").state)
        assertEquals(0, targets.getValue("MYANIMELIST").attemptCount)
        assertEquals("PENDING", database.trackingDao().getOperation(receipt.operationId)?.state)
        assertEquals(1, scheduler.calls)

        var aniListCalls = 0
        var malCalls = 0
        val mustNotRepeatAniList = adapter(TrackingProvider.ANILIST) { request ->
            aniListCalls++
            TrackingDeliveryResult.Success(
                TrackingConfirmedSnapshot(state = request.command.draft.desired)
            )
        }
        val successfulMalRetry = adapter(TrackingProvider.MYANIMELIST) { request ->
            malCalls++
            TrackingDeliveryResult.Success(
                TrackingConfirmedSnapshot(state = request.command.draft.desired)
            )
        }
        TrackingOutboxExecutor(
            database.trackingDao(),
            TrackingCommandCodec(),
            setOf(mustNotRepeatAniList, successfulMalRetry),
        ).drain(maxDeliveries = 2)

        assertEquals(0, aniListCalls)
        assertEquals(1, malCalls)
        val completed = saga.observeOperations().first().single()
        assertEquals(TrackingOperationState.SUCCEEDED, completed.state)
        assertTrue(completed.targets.all { it.state == TrackingTargetState.SUCCEEDED })
    }

    @Test
    fun `conflict projection compares snapshots without exposing notes or account ids`() {
        val equal = snapshot(TrackingProvider.ANILIST, progress = 5, notes = "private-a")
        val different = snapshot(
            TrackingProvider.MYANIMELIST,
            progress = 7,
            score = 80.0,
            notes = "private-b",
        )
        val conflict = buildConflicts(listOf(equal, different)).single()

        assertTrue(TrackingConflictField.PROGRESS in conflict.differingFields)
        assertTrue(TrackingConflictField.SCORE in conflict.differingFields)
        assertTrue(TrackingConflictField.NOTES in conflict.differingFields)
        assertFalse(conflict.toString().contains("private-a"))
        assertFalse(conflict.toString().contains("private-b"))
        assertFalse(conflict.toString().contains("private-account"))
        assertTrue(buildConflicts(listOf(equal, equal.copy(provider = "MYANIMELIST"))).isEmpty())
    }

    @Test
    fun `aggregate saga state never reports full success for pending blocked or failed target`() {
        val cases = mapOf(
            listOf(TrackingTargetState.SUCCEEDED, TrackingTargetState.SUCCEEDED) to
                TrackingOperationState.SUCCEEDED,
            listOf(TrackingTargetState.SUCCEEDED, TrackingTargetState.FAILED) to
                TrackingOperationState.PARTIAL_FAILURE,
            listOf(TrackingTargetState.FAILED, TrackingTargetState.SUCCEEDED) to
                TrackingOperationState.PARTIAL_FAILURE,
            listOf(TrackingTargetState.SUCCEEDED, TrackingTargetState.BLOCKED) to
                TrackingOperationState.PARTIAL_FAILURE,
            listOf(TrackingTargetState.SUCCEEDED, TrackingTargetState.PENDING) to
                TrackingOperationState.PARTIAL,
            listOf(TrackingTargetState.SUCCEEDED, TrackingTargetState.RETRYING) to
                TrackingOperationState.PARTIAL,
            listOf(TrackingTargetState.BLOCKED, TrackingTargetState.BLOCKED) to
                TrackingOperationState.BLOCKED,
            listOf(TrackingTargetState.FAILED, TrackingTargetState.FAILED) to
                TrackingOperationState.FAILED,
        )
        cases.forEach { (targets, expected) ->
            assertEquals(expected, aggregateTrackingOperationState(targets))
        }
    }

    private fun repository() = TrackingSagaRepository(
        database.trackingDao(),
        database.trackingConflictDao(),
        scheduler,
        testCommandService(),
    )

    private fun testCommandService() = TrackingCommandService(
        ensureLocalIdentity = { type, mediaId ->
            MediaIdentityResult.Success(LocalMediaIdentity("local-$mediaId", type, 1L, 1L))
        },
        activeAniListAccountId = { "ani" },
        activeMalAccountId = { "mal" },
        isMalConfigured = { true },
        enqueueCommand = { _, targets ->
            TrackingEnqueueResult.Accepted(
                TrackingEnqueueReceipt(
                    operationId = "unused",
                    generation = 1L,
                    deduplicated = false,
                    targetStates = targets.associate { it.provider to TrackingTargetState.PENDING },
                )
            )
        },
    )

    private fun snapshot(
        provider: TrackingProvider,
        progress: Int,
        score: Double? = 70.0,
        notes: String? = null,
    ) = ProviderTrackingSnapshotEntity(
        provider = provider.name,
        providerAccountId = "private-account-${provider.name}",
        localMediaId = "local-anime",
        providerMediaId = if (provider == TrackingProvider.ANILIST) 10L else 20L,
        providerListEntryId = null,
        mediaType = TrackingMediaType.ANIME.name,
        title = "Title",
        coverUrl = null,
        status = TrackingStatus.CURRENT.name,
        progress = progress,
        progressSecondary = null,
        score = score,
        repeatCount = 0,
        notes = notes,
        startedAt = null,
        completedAt = null,
        providerUpdatedAtEpochMillis = 1L,
        fetchedAtEpochMillis = 1L,
        rawProviderFieldsJson = "{}",
        isDeleted = false,
    )

    private fun adapter(
        provider: TrackingProvider,
        result: suspend (TrackingProviderRequest) -> TrackingDeliveryResult,
    ) = object : TrackingProviderAdapter {
        override val provider = provider
        override suspend fun apply(request: TrackingProviderRequest) = result(request)
    }

    private class RecordingScheduler : TrackingOutboxScheduler {
        var calls = 0
        override fun enqueue() {
            calls++
        }
    }
}
