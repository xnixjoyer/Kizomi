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
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingProvider
import com.anisync.android.domain.tracking.TrackingStatus
import com.anisync.android.domain.tracking.TrackingTargetState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class TrackingReconciliationPersistenceTest {
    private lateinit var database: AppDatabase
    private lateinit var context: Context
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        databaseName = "tracking-reconciliation-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        database = openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun `preview and ready item survive process database recreation`() = runTest {
        database.mediaIdentityDao().insertLocalIdentity(
            LocalMediaIdentityEntity("local", TrackingMediaType.ANIME.name, 1L, 1L)
        )
        database.mediaIdentityDao().insertProviderIdentity(
            ProviderMediaIdentityEntity(
                localMediaId = "local",
                provider = TrackingProvider.MYANIMELIST.name,
                providerMediaId = 200L,
                mediaType = TrackingMediaType.ANIME.name,
                mappingSource = "MANUAL_CONFIRMATION",
                verificationStatus = "CONFIRMED",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
            )
        )
        database.trackingDao().upsertSnapshot(snapshot())
        val service = service()
        val created = service.createMissingOnlyPreview(
            TrackingMediaType.ANIME,
            ReconciliationDirection(TrackingProvider.ANILIST, TrackingProvider.MYANIMELIST),
            "ani",
            "mal",
        ) as ReconciliationCreateResult.Created
        val before = service.observePlan(created.planId).first()
        assertNotNull(before)
        assertEquals(1, before!!.counts.ready)
        database.close()

        database = openDatabase()
        val after = service().observePlan(created.planId).first()

        assertNotNull(after)
        assertEquals(ReconciliationPlanState.PREVIEW, after!!.state)
        assertEquals(1, after.counts.ready)
        assertEquals(ReconciliationAction.ONLY_SOURCE, after.items.single().action)
    }

    @Test
    fun `pausing retains immutable preview for later resume`() = runTest {
        database.mediaIdentityDao().insertLocalIdentity(
            LocalMediaIdentityEntity("local", TrackingMediaType.ANIME.name, 1L, 1L)
        )
        database.mediaIdentityDao().insertProviderIdentity(
            ProviderMediaIdentityEntity(
                localMediaId = "local",
                provider = TrackingProvider.MYANIMELIST.name,
                providerMediaId = 200L,
                mediaType = TrackingMediaType.ANIME.name,
                mappingSource = "MANUAL_CONFIRMATION",
                verificationStatus = "CONFIRMED",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
            )
        )
        database.trackingDao().upsertSnapshot(snapshot())
        val service = service()
        val created = service.createMissingOnlyPreview(
            TrackingMediaType.ANIME,
            ReconciliationDirection(TrackingProvider.ANILIST, TrackingProvider.MYANIMELIST),
            "ani",
            "mal",
        ) as ReconciliationCreateResult.Created

        assertTrue(service.pause(created.planId))
        val paused = service.observePlan(created.planId).first()!!

        assertEquals(ReconciliationPlanState.PAUSED, paused.state)
        assertEquals(1, paused.counts.ready)
    }

    private fun openDatabase() = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
        .allowMainThreadQueries()
        .build()

    private fun service() = TrackingReconciliationService(
        database,
        database.trackingReconciliationDao(),
        database.trackingDao(),
        TrackingCommandService(
            ensureLocalIdentity = { type, mediaId ->
                MediaIdentityResult.Success(LocalMediaIdentity("local-$mediaId", type, 1L, 1L))
            },
            activeAniListAccountId = { "ani" },
            activeMalAccountId = { "mal" },
            isMalConfigured = { true },
            enqueueCommand = { _: TrackingCommandDraft, targets: List<TrackingCommandTarget> ->
                TrackingEnqueueResult.Accepted(
                    TrackingEnqueueReceipt(
                        "unused",
                        1L,
                        false,
                        targets.associate { it.provider to TrackingTargetState.PENDING },
                    )
                )
            },
        ),
    )

    private fun snapshot() = ProviderTrackingSnapshotEntity(
        provider = TrackingProvider.ANILIST.name,
        providerAccountId = "ani",
        localMediaId = "local",
        providerMediaId = 100L,
        providerListEntryId = 1_100L,
        mediaType = TrackingMediaType.ANIME.name,
        title = "Title",
        coverUrl = null,
        status = TrackingStatus.CURRENT.name,
        progress = 4,
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
