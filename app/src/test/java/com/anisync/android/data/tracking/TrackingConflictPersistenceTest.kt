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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class TrackingConflictPersistenceTest {
    private lateinit var database: AppDatabase
    private val scheduler = object : TrackingOutboxScheduler {
        override fun enqueue() = Unit
    }

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
    fun `stale conflict is rejected instead of overwriting newer snapshot`() = runTest {
        seedBindingAndSnapshots(4, 9)
        val enqueued = mutableListOf<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>()
        val saga = repository(commandService(enqueued))
        val stale = saga.observeConflicts().first().single()
        database.trackingDao().upsertSnapshot(
            snapshot(TrackingProvider.MYANIMELIST, "mal-account", 4)
        )

        val result = saga.resolveConflict(stale, TrackingProvider.ANILIST)

        assertEquals(
            TrackingFailureKind.MISSING_IDENTITY,
            (result as TrackingEnqueueResult.Rejected).reason,
        )
        assertTrue(enqueued.isEmpty())
    }

    @Test
    fun `account-bound conflict survives database recreation`() = runTest {
        database.close()
        val context: Context = RuntimeEnvironment.getApplication()
        val file: File = context.getDatabasePath("tracking-conflict-restart-test.db")
        context.deleteDatabase(file.name)
        database = Room.databaseBuilder(context, AppDatabase::class.java, file.name)
            .allowMainThreadQueries()
            .build()
        seedBindingAndSnapshots(3, 8)
        database.close()

        database = Room.databaseBuilder(context, AppDatabase::class.java, file.name)
            .allowMainThreadQueries()
            .build()
        val conflict = repository(commandService(mutableListOf())).observeConflicts().first().single()

        assertEquals("ani-account", conflict.aniListAccountId)
        assertEquals("mal-account", conflict.malAccountId)
        assertEquals(setOf(TrackingConflictField.PROGRESS), conflict.differingFields)
        database.close()
        context.deleteDatabase(file.name)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @Test
    fun `unrelated provider accounts are not cross paired`() = runTest {
        seedBindingAndSnapshots(1, 2)
        database.trackingDao().upsertSnapshot(
            snapshot(TrackingProvider.MYANIMELIST, "other-mal", 99)
        )

        val conflicts = repository(commandService(mutableListOf())).observeConflicts().first()

        assertEquals(1, conflicts.size)
        assertEquals("mal-account", conflicts.single().malAccountId)
    }

    private suspend fun seedBindingAndSnapshots(aniProgress: Int, malProgress: Int) {
        database.mediaIdentityDao().insertLocalIdentity(
            LocalMediaIdentityEntity("local-anime", "ANIME", 1L, 1L)
        )
        val bindingResult = TrackingOutboxRepository(
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
        assertTrue(bindingResult is TrackingEnqueueResult.Accepted)
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
        enqueued: MutableList<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>,
    ) = TrackingCommandService(
        ensureLocalIdentity = { type, mediaId ->
            MediaIdentityResult.Success(LocalMediaIdentity("local-$mediaId", type, 1L, 1L))
        },
        activeAniListAccountId = { "ani-account" },
        activeMalAccountId = { "mal-account" },
        isMalConfigured = { true },
        enqueueCommand = { draft, targets ->
            enqueued += draft to targets
            TrackingEnqueueResult.Accepted(
                TrackingEnqueueReceipt(
                    operationId = "resolution-${enqueued.size}",
                    generation = enqueued.size.toLong(),
                    deduplicated = false,
                    targetStates = targets.associate { it.provider to TrackingTargetState.PENDING },
                )
            )
        },
    )

    private fun snapshot(
        provider: TrackingProvider,
        account: String,
        progress: Int,
    ) = ProviderTrackingSnapshotEntity(
        provider = provider.name,
        providerAccountId = account,
        localMediaId = "local-anime",
        providerMediaId = if (provider == TrackingProvider.ANILIST) 10L else 20L,
        providerListEntryId = if (provider == TrackingProvider.ANILIST) 110L else 120L,
        mediaType = TrackingMediaType.ANIME.name,
        title = "Title",
        coverUrl = null,
        status = TrackingStatus.CURRENT.name,
        progress = progress,
        progressSecondary = null,
        score = 70.0,
        repeatCount = 0,
        notes = null,
        startedAt = null,
        completedAt = null,
        providerUpdatedAtEpochMillis = 1L,
        fetchedAtEpochMillis = 1L,
        rawProviderFieldsJson = "{}",
        isDeleted = false,
    )
}
