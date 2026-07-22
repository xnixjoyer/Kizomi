package com.anisync.android.data.tracking

import android.content.Context
import androidx.room.Room
import com.anisync.android.data.identity.LocalMediaIdentity
import com.anisync.android.data.identity.MediaIdentityResult
import com.anisync.android.data.local.AppDatabase
import com.anisync.android.data.local.entity.LocalMediaIdentityEntity
import com.anisync.android.data.local.entity.ProviderMediaIdentityEntity
import com.anisync.android.data.local.entity.ProviderTrackingSnapshotEntity
import com.anisync.android.domain.tracking.TrackingCommandDraft
import com.anisync.android.domain.tracking.TrackingCommandTarget
import com.anisync.android.domain.tracking.TrackingEnqueueReceipt
import com.anisync.android.domain.tracking.TrackingEnqueueResult
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingProvider
import com.anisync.android.domain.tracking.TrackingStatus
import com.anisync.android.domain.tracking.TrackingTargetState
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
class TrackingReconciliationExecutionTest {
    private lateinit var database: AppDatabase

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
    fun `execution enqueues creation for only source and never delete or existing target`() = runTest {
        seedMappedSource("missing", 1L, 101L)
        seedMappedSource("appeared", 2L, 102L)
        val enqueued = mutableListOf<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>()
        val service = service(enqueued)
        val created = service.createMissingOnlyPreview(
            TrackingMediaType.ANIME,
            ReconciliationDirection(TrackingProvider.ANILIST, TrackingProvider.MYANIMELIST),
            "ani",
            "mal",
        ) as ReconciliationCreateResult.Created
        database.trackingDao().upsertSnapshot(
            snapshot("appeared", TrackingProvider.MYANIMELIST, "mal", 102L, 8)
        )

        val result = service.executeMissingOnly(created.planId)!!

        assertEquals(1, enqueued.size)
        val (draft, targets) = enqueued.single()
        assertEquals("missing", draft.localMediaId)
        assertFalse(draft.deleteIntent)
        assertFalse(com.anisync.android.domain.tracking.TrackingField.DELETE in draft.fields)
        assertEquals(TrackingProvider.MYANIMELIST, targets.single().provider)
        assertEquals("mal", targets.single().providerAccountId)
        assertEquals(101L, targets.single().providerMediaId)
        assertEquals(1, result.items.count { it.state == ReconciliationItemState.SKIPPED_PRESENT })
        assertEquals(1, result.items.count { it.state == ReconciliationItemState.ENQUEUED })
    }

    @Test
    fun `account switch produces persisted blocker instead of redirecting target`() = runTest {
        seedMappedSource("missing", 1L, 101L)
        val enqueued = mutableListOf<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>()
        val service = service(enqueued, activeMal = "other-mal")
        val created = service.createMissingOnlyPreview(
            TrackingMediaType.ANIME,
            ReconciliationDirection(TrackingProvider.ANILIST, TrackingProvider.MYANIMELIST),
            "ani",
            "mal",
        ) as ReconciliationCreateResult.Created

        val result = service.executeMissingOnly(created.planId)!!

        assertEquals(1, enqueued.size)
        val target = enqueued.single().second.single()
        assertEquals("mal", target.providerAccountId)
        assertEquals(TrackingFailureKind.MISSING_ACCOUNT, target.blocker)
        assertEquals(ReconciliationPlanState.PARTIAL_FAILURE, result.state)
        assertEquals(TrackingFailureKind.MISSING_ACCOUNT, result.items.single().lastError)
    }

    @Test
    fun `reverse direction creates only missing AniList entry`() = runTest {
        insertLocal("reverse")
        insertIdentity("reverse", TrackingProvider.ANILIST, 77L)
        database.trackingDao().upsertSnapshot(
            snapshot("reverse", TrackingProvider.MYANIMELIST, "mal", 88L, 12)
        )
        val enqueued = mutableListOf<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>()
        val service = service(enqueued)
        val created = service.createMissingOnlyPreview(
            TrackingMediaType.ANIME,
            ReconciliationDirection(TrackingProvider.MYANIMELIST, TrackingProvider.ANILIST),
            "mal",
            "ani",
        ) as ReconciliationCreateResult.Created

        service.executeMissingOnly(created.planId)

        val (draft, targets) = enqueued.single()
        assertEquals(12, draft.desired.progress)
        assertEquals(TrackingProvider.ANILIST, targets.single().provider)
        assertEquals("ani", targets.single().providerAccountId)
        assertEquals(77L, targets.single().providerMediaId)
    }

    private fun service(
        enqueued: MutableList<Pair<TrackingCommandDraft, List<TrackingCommandTarget>>>,
        activeMal: String? = "mal",
    ) = TrackingReconciliationService(
        database,
        database.trackingReconciliationDao(),
        database.trackingDao(),
        TrackingCommandService(
            ensureLocalIdentity = { type, mediaId ->
                MediaIdentityResult.Success(LocalMediaIdentity("local-$mediaId", type, 1L, 1L))
            },
            activeAniListAccountId = { "ani" },
            activeMalAccountId = { activeMal },
            isMalConfigured = { true },
            enqueueCommand = { draft, targets ->
                enqueued += draft to targets
                val states = targets.associate { target ->
                    target.provider to if (target.blocker == null) {
                        TrackingTargetState.PENDING
                    } else {
                        TrackingTargetState.BLOCKED
                    }
                }
                val operationId = "operation-${enqueued.size}"
                if (targets.any { it.blocker != null }) {
                    // Mirror the production outbox state so the service can read the typed blocker.
                    TrackingOutboxRepository(
                        database,
                        database.trackingDao(),
                        TrackingCommandCodec(),
                        object : TrackingOutboxScheduler { override fun enqueue() = Unit },
                    ).enqueue(draft, targets)
                } else {
                    TrackingEnqueueResult.Accepted(
                        TrackingEnqueueReceipt(operationId, enqueued.size.toLong(), false, states)
                    )
                }
            },
        ),
    )

    private suspend fun seedMappedSource(local: String, sourceMediaId: Long, targetMediaId: Long) {
        insertLocal(local)
        insertIdentity(local, TrackingProvider.MYANIMELIST, targetMediaId)
        database.trackingDao().upsertSnapshot(
            snapshot(local, TrackingProvider.ANILIST, "ani", sourceMediaId, 5)
        )
    }

    private suspend fun insertLocal(local: String) {
        database.mediaIdentityDao().insertLocalIdentity(
            LocalMediaIdentityEntity(local, TrackingMediaType.ANIME.name, 1L, 1L)
        )
    }

    private suspend fun insertIdentity(local: String, provider: TrackingProvider, mediaId: Long) {
        database.mediaIdentityDao().insertProviderIdentity(
            ProviderMediaIdentityEntity(
                localMediaId = local,
                provider = provider.name,
                providerMediaId = mediaId,
                mediaType = TrackingMediaType.ANIME.name,
                mappingSource = "MANUAL_CONFIRMATION",
                verificationStatus = "CONFIRMED",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
            )
        )
    }

    private fun snapshot(
        local: String,
        provider: TrackingProvider,
        account: String,
        mediaId: Long,
        progress: Int,
    ) = ProviderTrackingSnapshotEntity(
        provider = provider.name,
        providerAccountId = account,
        localMediaId = local,
        providerMediaId = mediaId,
        providerListEntryId = mediaId + 1_000L,
        mediaType = TrackingMediaType.ANIME.name,
        title = local,
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
