package com.anisync.android.data.tracking

import android.content.Context
import androidx.room.Room
import com.anisync.android.data.local.AppDatabase
import com.anisync.android.data.local.entity.LocalMediaIdentityEntity
import com.anisync.android.domain.tracking.TrackingCommandDraft
import com.anisync.android.domain.tracking.TrackingCommandTarget
import com.anisync.android.domain.tracking.TrackingConfirmedSnapshot
import com.anisync.android.domain.tracking.TrackingDeliveryResult
import com.anisync.android.domain.tracking.TrackingDesiredState
import com.anisync.android.domain.tracking.TrackingEnqueueResult
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingField
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingProvider
import com.anisync.android.domain.tracking.TrackingProviderAdapter
import com.anisync.android.domain.tracking.TrackingProviderRequest
import com.anisync.android.domain.tracking.TrackingStatus
import kotlinx.coroutines.CancellationException
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
class TrackingOutboxExecutorTest {
    private lateinit var database: AppDatabase
    private val codec = TrackingCommandCodec()

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `dual delivery records success and retry independently without duplicate success`() = runTest {
        seedLocal()
        val receipt = enqueue(
            listOf(
                target(TrackingProvider.ANILIST, "ani-account", 10),
                target(TrackingProvider.MYANIMELIST, "mal-account", 20),
            )
        )
        val aniList = RecordingAdapter(TrackingProvider.ANILIST) { request ->
            TrackingDeliveryResult.Success(
                TrackingConfirmedSnapshot(
                    state = request.command.draft.desired,
                    remoteRevision = "ani-revision",
                )
            )
        }
        val mal = RecordingAdapter(TrackingProvider.MYANIMELIST) {
            TrackingDeliveryResult.RetryableFailure(
                TrackingFailureKind.RATE_LIMITED,
                httpStatus = 429,
                retryAfterMillis = 60_000,
            )
        }
        val executor = TrackingOutboxExecutor(database.trackingDao(), codec, setOf(aniList, mal))

        val drained = executor.drain(maxDeliveries = 2)
        val targets = database.trackingDao().getTargets(receipt.operationId).associateBy { it.provider }

        assertEquals(1, aniList.calls)
        assertEquals(1, mal.calls)
        assertEquals("SUCCEEDED", targets.getValue("ANILIST").state)
        assertEquals("RETRYING", targets.getValue("MYANIMELIST").state)
        assertEquals("PARTIAL", database.trackingDao().getOperation(receipt.operationId)?.state)
        assertTrue(drained.hasUnsettledDeliveries)
        assertEquals(1, database.trackingDao().countTargetsInState(receipt.operationId, "SUCCEEDED"))
        assertTrue(
            database.trackingDao().findSnapshot(
                "ANILIST",
                "ani-account",
                "local-anime",
            ) != null
        )

        executor.drain(maxDeliveries = 2)
        assertEquals(1, aniList.calls)
        assertEquals(1, mal.calls)
    }

    @Test
    fun `worker kill switch blocks every provider with zero adapter calls`() = runTest {
        seedLocal()
        val receipt = enqueue(
            listOf(
                target(TrackingProvider.ANILIST, "ani-account", 10),
                target(TrackingProvider.MYANIMELIST, "mal-account", 20),
            )
        )
        val aniList = RecordingAdapter(TrackingProvider.ANILIST) {
            error("AniList adapter must not run while blocked")
        }
        val mal = RecordingAdapter(TrackingProvider.MYANIMELIST) {
            error("MAL adapter must not run while blocked")
        }
        val executor = TrackingOutboxExecutor(
            database.trackingDao(),
            codec,
            setOf(aniList, mal),
        ) { _, _ -> TrackingFailureKind.NETWORK_BLOCKED }

        val drained = executor.drain(maxDeliveries = 2)
        val targets = database.trackingDao().getTargets(receipt.operationId)

        assertEquals(0, aniList.calls)
        assertEquals(0, mal.calls)
        assertTrue(targets.all { it.state == "BLOCKED" })
        assertTrue(targets.all { it.lastErrorKind == TrackingFailureKind.NETWORK_BLOCKED.name })
        assertEquals("BLOCKED", database.trackingDao().getOperation(receipt.operationId)?.state)
        assertFalse(drained.hasUnsettledDeliveries)
    }

    @Test
    fun `delivery-time account switch blocks queued target with zero network calls`() = runTest {
        seedLocal()
        val receipt = enqueue(listOf(target(TrackingProvider.ANILIST, "old-account", 10)))
        val adapter = RecordingAdapter(TrackingProvider.ANILIST) {
            error("Adapter must not run for a stale account binding")
        }
        TrackingOutboxExecutor(
            database.trackingDao(),
            codec,
            setOf(adapter),
        ) { _, expected ->
            if (expected == "new-account") null else TrackingFailureKind.MISSING_ACCOUNT
        }.drain()

        val target = database.trackingDao().getTargets(receipt.operationId).single()
        assertEquals(0, adapter.calls)
        assertEquals("BLOCKED", target.state)
        assertEquals(TrackingFailureKind.MISSING_ACCOUNT.name, target.lastErrorKind)
    }

    @Test
    fun `successful absolute delivery is idempotent across executor recreation`() = runTest {
        seedLocal()
        val receipt = enqueue(listOf(target(TrackingProvider.MYANIMELIST, "mal", 20)))
        val adapter = RecordingAdapter(TrackingProvider.MYANIMELIST) { request ->
            TrackingDeliveryResult.Success(TrackingConfirmedSnapshot(state = request.command.draft.desired))
        }

        TrackingOutboxExecutor(database.trackingDao(), codec, setOf(adapter)).drain()
        TrackingOutboxExecutor(database.trackingDao(), codec, setOf(adapter)).drain()

        assertEquals(1, adapter.calls)
        assertEquals("SUCCEEDED", database.trackingDao().getOperation(receipt.operationId)?.state)
        assertEquals(0, database.trackingDao().countUnsettledDeliveries())
    }

    @Test
    fun `missing provider adapter is blocked and never reported as success`() = runTest {
        seedLocal()
        val receipt = enqueue(listOf(target(TrackingProvider.MYANIMELIST, "mal", 20)))

        val drained = TrackingOutboxExecutor(database.trackingDao(), codec, emptySet()).drain()
        val target = database.trackingDao().getTargets(receipt.operationId).single()

        assertEquals("BLOCKED", target.state)
        assertEquals(TrackingFailureKind.PROVIDER_NOT_CONFIGURED.name, target.lastErrorKind)
        assertEquals("BLOCKED", database.trackingDao().getOperation(receipt.operationId)?.state)
        assertFalse(drained.hasUnsettledDeliveries)
    }

    @Test
    fun `cancellation leaves a lease for process-death recovery instead of concurrent redelivery`() = runTest {
        seedLocal()
        val receipt = enqueue(listOf(target(TrackingProvider.MYANIMELIST, "mal", 20)))
        val adapter = RecordingAdapter(TrackingProvider.MYANIMELIST) {
            throw CancellationException("worker stopped")
        }
        var propagated = false
        try {
            TrackingOutboxExecutor(database.trackingDao(), codec, setOf(adapter)).drain()
        } catch (_: CancellationException) {
            propagated = true
        }

        val target = database.trackingDao().getTargets(receipt.operationId).single()
        assertTrue(propagated)
        assertEquals("RUNNING", target.state)
        assertTrue(target.leaseToken != null)
    }

    @Test
    fun `committed command survives database and executor recreation`() = runTest {
        database.close()
        val context: Context = RuntimeEnvironment.getApplication()
        val databaseFile: File = context.getDatabasePath("tracking-outbox-restart-test.db")
        context.deleteDatabase(databaseFile.name)
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseFile.name)
            .allowMainThreadQueries()
            .build()
        seedLocal()
        val receipt = enqueue(listOf(target(TrackingProvider.MYANIMELIST, "mal", 20)))
        database.close()

        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseFile.name)
            .allowMainThreadQueries()
            .build()
        val adapter = RecordingAdapter(TrackingProvider.MYANIMELIST) { request ->
            TrackingDeliveryResult.Success(TrackingConfirmedSnapshot(state = request.command.draft.desired))
        }
        TrackingOutboxExecutor(
            database.trackingDao(),
            TrackingCommandCodec(),
            setOf(adapter),
        ).drain()

        assertEquals(1, adapter.calls)
        assertEquals("SUCCEEDED", database.trackingDao().getOperation(receipt.operationId)?.state)
        database.close()
        context.deleteDatabase(databaseFile.name)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private suspend fun seedLocal() {
        database.mediaIdentityDao().insertLocalIdentity(
            LocalMediaIdentityEntity("local-anime", "ANIME", 1L, 1L)
        )
    }

    private suspend fun enqueue(targets: List<TrackingCommandTarget>) =
        ((TrackingOutboxRepository(
            database,
            database.trackingDao(),
            codec,
            object : TrackingOutboxScheduler { override fun enqueue() = Unit },
        ).enqueue(
            TrackingCommandDraft(
                localMediaId = "local-anime",
                mediaType = TrackingMediaType.ANIME,
                desired = TrackingDesiredState(TrackingStatus.CURRENT, progress = 4),
                fields = setOf(TrackingField.STATUS, TrackingField.PROGRESS),
            ),
            targets,
        )) as TrackingEnqueueResult.Accepted).receipt

    private fun target(
        provider: TrackingProvider,
        account: String,
        mediaId: Long,
    ) = TrackingCommandTarget(provider, account, mediaId)

    private class RecordingAdapter(
        override val provider: TrackingProvider,
        private val result: suspend (TrackingProviderRequest) -> TrackingDeliveryResult,
    ) : TrackingProviderAdapter {
        var calls = 0
        override suspend fun apply(request: TrackingProviderRequest): TrackingDeliveryResult {
            calls++
            return result(request)
        }
    }
}
