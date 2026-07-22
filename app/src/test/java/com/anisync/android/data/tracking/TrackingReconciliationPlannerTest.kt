package com.anisync.android.data.tracking

import android.content.Context
import androidx.room.Room
import com.anisync.android.data.identity.LocalMediaIdentity
import com.anisync.android.data.identity.MediaIdentityResult
import com.anisync.android.data.local.AppDatabase
import com.anisync.android.data.local.entity.LocalMediaIdentityEntity
import com.anisync.android.data.local.entity.ProviderMediaIdentityEntity
import com.anisync.android.data.local.entity.ProviderMediaIdentityIssueEntity
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

@RunWith(RobolectricTestRunner::class)
class TrackingReconciliationPlannerTest {
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
    fun `preview classifies every state and makes only missing mapped source ready`() = runTest {
        val locals = listOf("equal", "different", "only-source", "only-target", "unmapped", "conflict")
        for (local in locals) insertLocal(local)
        listOf("equal", "different", "only-source", "conflict").forEachIndexed { index, local ->
            insertIdentity(local, TrackingProvider.MYANIMELIST, 100L + index)
        }
        database.mediaIdentityDao().insertIssue(
            ProviderMediaIdentityIssueEntity(
                localMediaId = "conflict",
                provider = TrackingProvider.MYANIMELIST.name,
                providerMediaId = 103L,
                mediaType = TrackingMediaType.ANIME.name,
                mappingSource = "ANILIST_ID_MAL",
                verificationStatus = "CONFLICTING",
                reason = "TEST_CONFLICT",
                sourceTable = null,
                sourceRowKey = null,
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
            )
        )
        database.trackingDao().upsertSnapshots(
            listOf(
                snapshot("equal", TrackingProvider.ANILIST, "ani", 1L, 2),
                snapshot("equal", TrackingProvider.MYANIMELIST, "mal", 100L, 2),
                snapshot("different", TrackingProvider.ANILIST, "ani", 2L, 3),
                snapshot("different", TrackingProvider.MYANIMELIST, "mal", 101L, 7),
                snapshot("only-source", TrackingProvider.ANILIST, "ani", 3L, 4),
                snapshot("only-target", TrackingProvider.MYANIMELIST, "mal", 200L, 5),
                snapshot("unmapped", TrackingProvider.ANILIST, "ani", 4L, 6),
                snapshot("conflict", TrackingProvider.ANILIST, "ani", 5L, 8),
            )
        )
        val service = service()

        val created = service.createMissingOnlyPreview(
            TrackingMediaType.ANIME,
            ReconciliationDirection(TrackingProvider.ANILIST, TrackingProvider.MYANIMELIST),
            "ani",
            "mal",
        ) as ReconciliationCreateResult.Created
        val plan = service.observePlan(created.planId).first()!!

        assertEquals(1, plan.counts.equal)
        assertEquals(1, plan.counts.different)
        assertEquals(1, plan.counts.onlySource)
        assertEquals(1, plan.counts.onlyTarget)
        assertEquals(1, plan.counts.unmapped)
        assertEquals(1, plan.counts.blocked)
        assertEquals(1, plan.counts.ready)
        assertEquals(
            ReconciliationItemState.READY,
            plan.items.single { it.action == ReconciliationAction.ONLY_SOURCE }.state,
        )
        assertTrue(plan.items.filter { it.action != ReconciliationAction.ONLY_SOURCE }
            .none { it.state == ReconciliationItemState.READY })
    }

    @Test
    fun `direction is explicit and provider exclusive source data blocks target`() = runTest {
        insertLocal("manga")
        insertIdentity("manga", TrackingProvider.MYANIMELIST, 500L, TrackingMediaType.MANGA)
        database.trackingDao().upsertSnapshot(
            snapshot(
                local = "manga",
                provider = TrackingProvider.ANILIST,
                account = "ani",
                mediaId = 50L,
                progress = 9,
                mediaType = TrackingMediaType.MANGA,
                notes = "private provider-only note",
            )
        )

        val created = service().createMissingOnlyPreview(
            TrackingMediaType.MANGA,
            ReconciliationDirection(TrackingProvider.ANILIST, TrackingProvider.MYANIMELIST),
            "ani",
            "mal",
        ) as ReconciliationCreateResult.Created
        val plan = service().observePlan(created.planId).first()!!

        assertEquals(TrackingProvider.ANILIST, plan.direction.source)
        assertEquals(TrackingProvider.MYANIMELIST, plan.direction.target)
        assertEquals(0, plan.counts.ready)
        assertEquals(1, plan.counts.blocked)
        assertEquals(
            TrackingFailureKind.UNSUPPORTED_FIELD,
            plan.items.single().lastError,
        )
    }

    private fun service() = TrackingReconciliationService(
        database,
        database.trackingReconciliationDao(),
        database.trackingDao(),
        commandService(),
    )

    private fun commandService() = TrackingCommandService(
        ensureLocalIdentity = { type, mediaId ->
            MediaIdentityResult.Success(LocalMediaIdentity("local-$mediaId", type, 1L, 1L))
        },
        activeAniListAccountId = { "ani" },
        activeMalAccountId = { "mal" },
        isMalConfigured = { true },
        enqueueCommand = { _: TrackingCommandDraft, targets: List<TrackingCommandTarget> ->
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

    private suspend fun insertLocal(id: String) {
        database.mediaIdentityDao().insertLocalIdentity(
            LocalMediaIdentityEntity(id, TrackingMediaType.ANIME.name, 1L, 1L)
        )
    }

    private suspend fun insertIdentity(
        local: String,
        provider: TrackingProvider,
        mediaId: Long,
        mediaType: TrackingMediaType = TrackingMediaType.ANIME,
    ) {
        if (database.mediaIdentityDao().getLocalIdentity(local) == null) {
            database.mediaIdentityDao().insertLocalIdentity(
                LocalMediaIdentityEntity(local, mediaType.name, 1L, 1L)
            )
        }
        database.mediaIdentityDao().insertProviderIdentity(
            ProviderMediaIdentityEntity(
                localMediaId = local,
                provider = provider.name,
                providerMediaId = mediaId,
                mediaType = mediaType.name,
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
        mediaType: TrackingMediaType = TrackingMediaType.ANIME,
        notes: String? = null,
    ) = ProviderTrackingSnapshotEntity(
        provider = provider.name,
        providerAccountId = account,
        localMediaId = local,
        providerMediaId = mediaId,
        providerListEntryId = mediaId + 1_000L,
        mediaType = mediaType.name,
        title = local,
        coverUrl = null,
        status = TrackingStatus.CURRENT.name,
        progress = progress,
        progressSecondary = null,
        score = 70.0,
        repeatCount = 0,
        notes = notes,
        startedAt = null,
        completedAt = null,
        providerUpdatedAtEpochMillis = 1L,
        fetchedAtEpochMillis = 1L,
        rawProviderFieldsJson = "{}",
        isDeleted = false,
    )
}
