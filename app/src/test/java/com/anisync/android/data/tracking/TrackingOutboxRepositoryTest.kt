package com.anisync.android.data.tracking

import android.content.Context
import androidx.room.Room
import com.anisync.android.data.local.AppDatabase
import com.anisync.android.data.local.entity.LocalMediaIdentityEntity
import com.anisync.android.domain.tracking.TrackingCommandDraft
import com.anisync.android.domain.tracking.TrackingCommandTarget
import com.anisync.android.domain.tracking.TrackingDesiredState
import com.anisync.android.domain.tracking.TrackingEnqueueResult
import com.anisync.android.domain.tracking.TrackingField
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingProvider
import com.anisync.android.domain.tracking.TrackingStatus
import com.anisync.android.domain.tracking.TrackingTargetState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TrackingOutboxRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var scheduler: RecordingScheduler
    private lateinit var repository: TrackingOutboxRepository

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scheduler = RecordingScheduler()
        repository = TrackingOutboxRepository(
            database,
            database.trackingDao(),
            TrackingCommandCodec(),
            scheduler,
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `intent is durable before scheduling and duplicate enqueue reuses unsettled operation`() = runTest {
        seedLocal("local-anime")

        val first = accepted(repository.enqueue(draft(progress = 4), malTarget()))
        val duplicate = accepted(repository.enqueue(draft(progress = 4), malTarget()))

        assertEquals(first.operationId, duplicate.operationId)
        assertFalse(first.deduplicated)
        assertTrue(duplicate.deduplicated)
        assertEquals(1, scheduler.calls)
        assertEquals(1L, database.trackingDao().latestGeneration(logicalKey()))
        assertEquals(TrackingTargetState.PENDING, duplicate.targetState)
        assertTrue(database.trackingDao().getOperation(first.operationId)?.commandJson?.isNotBlank() == true)
    }

    @Test
    fun `new absolute state supersedes waiting generation but never cancels running delivery`() = runTest {
        seedLocal("local-anime")
        val first = accepted(repository.enqueue(draft(progress = 4), malTarget()))
        val now = System.currentTimeMillis()
        assertEquals(
            1,
            database.trackingDao().claimTarget(
                first.operationId,
                TrackingProvider.MYANIMELIST.name,
                "active-lease",
                now + 60_000,
                now,
            ),
        )

        val second = accepted(repository.enqueue(draft(progress = 5), malTarget()))

        assertNotEquals(first.operationId, second.operationId)
        assertEquals(2L, second.generation)
        assertEquals("RUNNING", database.trackingDao().getTarget(first.operationId)?.state)
        assertEquals("PENDING", database.trackingDao().getTarget(second.operationId)?.state)
    }

    @Test
    fun `new state replaces queued state and delete is a durable tombstone`() = runTest {
        seedLocal("local-anime")
        val first = accepted(repository.enqueue(draft(progress = 1), malTarget()))
        val second = accepted(repository.enqueue(draft(progress = 2), malTarget()))
        val tombstone = accepted(
            repository.enqueue(
                TrackingCommandDraft(
                    localMediaId = "local-anime",
                    mediaType = TrackingMediaType.ANIME,
                    desired = TrackingDesiredState(status = null, progress = 0),
                    fields = setOf(TrackingField.DELETE),
                    deleteIntent = true,
                ),
                malTarget(),
            )
        )

        assertEquals("SUPERSEDED", database.trackingDao().getTarget(first.operationId)?.state)
        assertEquals("SUPERSEDED", database.trackingDao().getTarget(second.operationId)?.state)
        assertTrue(database.trackingDao().getOperation(tombstone.operationId)?.isTombstone == true)
        assertEquals(3L, tombstone.generation)
    }

    @Test
    fun `account keys isolate generations and blocked target is explicit and unscheduled`() = runTest {
        seedLocal("local-anime")
        val first = accepted(repository.enqueue(draft(1), malTarget("account-a")))
        val second = accepted(repository.enqueue(draft(1), malTarget("account-b")))
        val blocked = accepted(
            repository.enqueue(
                draft(2),
                TrackingCommandTarget(
                    provider = TrackingProvider.ANILIST,
                    providerAccountId = null,
                    providerMediaId = 10,
                    blocker = com.anisync.android.domain.tracking.TrackingFailureKind.MISSING_ACCOUNT,
                ),
            )
        )

        assertEquals(1L, first.generation)
        assertEquals(1L, second.generation)
        assertEquals(TrackingTargetState.BLOCKED, blocked.targetState)
        assertEquals(2, scheduler.calls)
    }

    @Test
    fun `concurrent duplicate input produces exactly one operation`() = runTest {
        seedLocal("local-anime")
        val receipts = List(8) {
            async { accepted(repository.enqueue(draft(7), malTarget())) }
        }.awaitAll()

        assertEquals(1, receipts.map { it.operationId }.distinct().size)
        assertEquals(1L, database.trackingDao().latestGeneration(logicalKey()))
    }

    private suspend fun seedLocal(id: String) {
        database.mediaIdentityDao().insertLocalIdentity(
            LocalMediaIdentityEntity(id, "ANIME", 1L, 1L)
        )
    }

    private fun draft(progress: Int) = TrackingCommandDraft(
        localMediaId = "local-anime",
        mediaType = TrackingMediaType.ANIME,
        desired = TrackingDesiredState(TrackingStatus.CURRENT, progress),
        fields = setOf(TrackingField.STATUS, TrackingField.PROGRESS),
    )

    private fun malTarget(account: String = "mal-account") = TrackingCommandTarget(
        provider = TrackingProvider.MYANIMELIST,
        providerAccountId = account,
        providerMediaId = 42L,
    )

    private fun logicalKey(account: String = "mal-account") =
        "ANIME:local-anime:MYANIMELIST=$account"

    private fun accepted(result: TrackingEnqueueResult) =
        (result as TrackingEnqueueResult.Accepted).receipt

    private class RecordingScheduler : TrackingOutboxScheduler {
        var calls = 0
        override fun enqueue() {
            calls++
        }
    }
}
