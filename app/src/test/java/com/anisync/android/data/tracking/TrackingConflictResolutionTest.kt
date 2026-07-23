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
import com.anisync.android.domain.tracking.TrackingDesiredState
import com.anisync.android.domain.tracking.TrackingEnqueueReceipt
import com.anisync.android.domain.tracking.TrackingEnqueueResult
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingField
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingProvider
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
class TrackingConflictResolutionTest {
    private lateinit var database: AppDatabase
    private val scheduler = RecordingScheduler()

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
    fun `explicit resolution copies selected source to exact target`() = runTest {
        seedBinding()
        seedSnapshots(4, 9)
        val enqueued = mutableListOf<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>()
        val saga = repository(commandService(enqueued = enqueued))
        val conflict = saga.observeConflicts().first().single()

        val result = saga.resolveConflict(conflict, TrackingProvider.ANILIST)

        assertTrue(result is TrackingEnqueueResult.Accepted)
        val (draft, targets) = enqueued.single()
        assertEquals(4, draft.desired.progress)
        assertEquals(setOf(TrackingField.PROGRESS), draft.fields)
        assertEquals(TrackingProvider.MYANIMELIST, targets.single().provider)
        assertEquals("mal-account", targets.single().providerAccountId)
        assertEquals(20L, targets.single().providerMediaId)
        assertEquals(null, targets.single().blocker)
    }

    @Test
    fun `account switch preserves exact binding and blocks delivery`() = runTest {
        seedBinding()
        seedSnapshots(4, 9)
        val enqueued = mutableListOf<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>()
        val saga = repository(
            commandService(activeMal = "different-account", enqueued = enqueued)
        )
        val conflict = saga.observeConflicts().first().single()

        val result = saga.resolveConflict(conflict, TrackingProvider.ANILIST)

        assertTrue(result is TrackingEnqueueResult.Accepted)
        val target = enqueued.single().second.single()
        assertEquals("mal-account", target.providerAccountId)
        assertEquals(TrackingFailureKind.MISSING_ACCOUNT, target.blocker)
    }

    @Test
    fun `provider exclusive field blocks unsafe direction`() = runTest {
        seedBinding()
        database.trackingDao().upsertSnapshots(
            listOf(
                snapshot(TrackingProvider.ANILIST, "ani-account", 5, notes = "private"),
                snapshot(TrackingProvider.MYANIMELIST, "mal-account", 5),
            )
        )
        val enqueued = mutableListOf<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>()
        val saga = repository(commandService(enqueued = enqueued))
        val conflict = saga.observeConflicts().first().single()

        assertFalse(conflict.canResolveFrom(TrackingProvider.ANILIST))
        assertEquals(
            setOf(TrackingConflictField.NOTES),
            conflict.blockedFieldsWhenUsing(TrackingProvider.ANILIST),
        )
        val result = saga.resolveConflict(conflict, TrackingProvider.ANILIST)
        assertEquals(
            TrackingFailureKind.UNSUPPORTED_FIELD,
            (result as TrackingEnqueueResult.Rejected).reason,
        )
        assertTrue(enqueued.isEmpty())
    }

    @Test
    fun `deleted source enqueues exact delete only`() = runTest {
        seedBinding()
        database.trackingDao().upsertSnapshots(
            listOf(
                snapshot(TrackingProvider.ANILIST, "ani-account", 0, deleted = true),
                snapshot(TrackingProvider.MYANIMELIST, "mal-account", 9),
            )
        )
        val enqueued = mutableListOf<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>()
        val saga = repository(commandService(enqueued = enqueued))
        val conflict = saga.observeConflicts().first().single()

        val result = saga.resolveConflict(conflict, TrackingProvider.ANILIST)

        assertTrue(result is TrackingEnqueueResult.Accepted)
        val (draft, targets) = enqueued.single()
        assertTrue(draft.deleteIntent)
        assertEquals(setOf(TrackingField.DELETE), draft.fields)
        assertEquals(120L, draft.providerListEntryIds[TrackingProvider.MYANIMELIST])
        assertEquals(listOf(TrackingProvider.MYANIMELIST), targets.map { it.provider })
    }

    @Test
    fun `missing delete handle blocks action before enqueue`() = runTest {
        seedBinding()
        database.trackingDao().upsertSnapshots(
            listOf(
                snapshot(TrackingProvider.ANILIST, "ani-account", 0, deleted = true),
                snapshot(
                    TrackingProvider.MYANIMELIST,
                    "mal-account",
                    9,
                    listEntryId = null,
                ),
            )
        )
        val enqueued = mutableListOf<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>()
        val saga = repository(commandService(enqueued = enqueued))
        val conflict = saga.observeConflicts().first().single()

        assertEquals(
            TrackingFailureKind.MISSING_IDENTITY,
            conflict.resolutionBlockerWhenUsing(TrackingProvider.ANILIST),
        )
        val result = saga.resolveConflict(conflict, TrackingProvider.ANILIST)
        assertEquals(
            TrackingFailureKind.MISSING_IDENTITY,
            (result as TrackingEnqueueResult.Rejected).reason,
        )
        assertTrue(enqueued.isEmpty())
    }

    @Test
    fun `active source restores deleted target through one add command`() = runTest {
        seedBinding()
        database.trackingDao().upsertSnapshots(
            listOf(
                snapshot(TrackingProvider.ANILIST, "ani-account", 4),
                snapshot(TrackingProvider.MYANIMELIST, "mal-account", 0, deleted = true),
            )
        )
        val enqueued = mutableListOf<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>()
        val saga = repository(commandService(enqueued = enqueued))
        val conflict = saga.observeConflicts().first().single()

        val result = saga.resolveConflict(conflict, TrackingProvider.ANILIST)

        assertTrue(result is TrackingEnqueueResult.Accepted)
        val (draft, targets) = enqueued.single()
        assertFalse(draft.deleteIntent)
        assertEquals(4, draft.desired.progress)
        assertTrue(TrackingField.STATUS in draft.fields)
        assertTrue(TrackingField.PROGRESS in draft.fields)
        assertEquals(TrackingProvider.MYANIMELIST, targets.single().provider)
    }

    private suspend fun seedBinding() {
        database.mediaIdentityDao().insertLocalIdentity(
            LocalMediaIdentityEntity("local-anime", "ANIME", 1L, 1L)
        )
        val result = TrackingOutboxRepository(
            database,
            database.trackingDao(),
            TrackingCommandCodec(),
            scheduler,
        ).enqueue(
            TrackingCommandDraft(
                localMediaId = "local-anime",
                mediaType = TrackingMediaType.ANIME,
                desired = TrackingDesiredState(TrackingStatus.CURRENT, 1),
                fields = setOf(TrackingField.STATUS, TrackingField.PROGRESS),
            ),
            listOf(
                TrackingCommandTarget(TrackingProvider.ANILIST, "ani-account", 10L),
                TrackingCommandTarget(TrackingProvider.MYANIMELIST, "mal-account", 20L),
            ),
        )
        assertTrue(result is TrackingEnqueueResult.Accepted)
    }

    private suspend fun seedSnapshots(aniProgress: Int, malProgress: Int) {
        database.trackingDao().upsertSnapshots(
            listOf(
                snapshot(TrackingProvider.ANILIST, "ani-account", aniProgress),
                snapshot(TrackingProvider.MYANIMELIST, "mal-account", malProgress),
            )
        )
    }

    private fun repository(commands: TrackingCommandService) = TrackingSagaRepository(
        database.trackingDao(),
        database.trackingConflictDao(),
        scheduler,
        commands,
    )

    private fun commandService(
        activeMal: String? = "mal-account",
        enqueued: MutableList<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>,
    ) = TrackingCommandService(
        ensureLocalIdentity = { type, mediaId ->
            MediaIdentityResult.Success(LocalMediaIdentity("local-$mediaId", type, 1L, 1L))
        },
        activeAniListAccountId = { "ani-account" },
        activeMalAccountId = { activeMal },
        isMalConfigured = { true },
        enqueueCommand = { draft, targets ->
            enqueued += draft to targets
            TrackingEnqueueResult.Accepted(
                TrackingEnqueueReceipt(
                    operationId = "resolution-${enqueued.size}",
                    generation = enqueued.size.toLong(),
                    deduplicated = false,
                    targetStates = targets.associate { target ->
                        target.provider to if (target.blocker == null) {
                            TrackingTargetState.PENDING
                        } else {
                            TrackingTargetState.BLOCKED
                        }
                    },
                )
            )
        },
    )

    private fun snapshot(
        provider: TrackingProvider,
        account: String,
        progress: Int,
        notes: String? = null,
        deleted: Boolean = false,
        listEntryId: Long? = if (provider == TrackingProvider.ANILIST) 110L else 120L,
    ) = ProviderTrackingSnapshotEntity(
        provider = provider.name,
        providerAccountId = account,
        localMediaId = "local-anime",
        providerMediaId = if (provider == TrackingProvider.ANILIST) 10L else 20L,
        providerListEntryId = listEntryId,
        mediaType = TrackingMediaType.ANIME.name,
        title = "Title",
        coverUrl = null,
        status = if (deleted) "DELETED" else TrackingStatus.CURRENT.name,
        progress = progress,
        progressSecondary = null,
        score = if (deleted) null else 70.0,
        repeatCount = 0,
        notes = if (deleted) null else notes,
        startedAt = null,
        completedAt = null,
        providerUpdatedAtEpochMillis = 1L,
        fetchedAtEpochMillis = 1L,
        rawProviderFieldsJson = "{}",
        isDeleted = deleted,
    )

    private class RecordingScheduler : TrackingOutboxScheduler {
        override fun enqueue() = Unit
    }
}
