package com.anisync.android.data.mal.api

import android.content.Context
import androidx.room.Room
import com.anisync.android.data.identity.MediaIdentityRepository
import com.anisync.android.data.identity.SystemMediaIdentityClock
import com.anisync.android.data.identity.UuidMediaIdentityIdGenerator
import com.anisync.android.data.local.AppDatabase
import com.anisync.android.data.mal.oauth.MalAuthenticatedFailureReason
import com.anisync.android.data.mal.oauth.MalAuthenticatedResponse
import com.anisync.android.data.mal.oauth.MalAuthenticatedResult
import com.anisync.android.domain.tracking.TrackingMediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MalLibraryRepositoryTest {
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
    fun `multi-page refresh deduplicates rows and creates MAL-native identities`() = runTest {
        val responses = ArrayDeque(
            listOf(
                page(
                    rows = listOf(row(1, "One", 2), row(2, "Two", 3)),
                    next = "https://api.myanimelist.net/v2/users/@me/animelist?offset=2",
                ),
                page(rows = listOf(row(2, "Two duplicate", 3), row(3, "Three", 4))),
            )
        )
        val repository = repository { responses.removeFirst() }

        val result = repository.refresh("account-a", TrackingMediaType.ANIME, pageSize = 2)
        val library = repository.observeLibrary("account-a", TrackingMediaType.ANIME).first()

        assertEquals(MalLibraryRefreshResult.Success(3, 0, 2), result)
        assertEquals(listOf(1L, 3L, 2L), library.map { it.malId })
        assertEquals(3, database.mediaIdentityDao().countLocalIdentities())
        assertEquals(3, database.mediaIdentityDao().countProviderIdentities())
        assertNull(database.mediaIdentityDao().findByProviderId("ANILIST", 1, "ANIME"))
        assertNotNull(database.mediaIdentityDao().findByProviderId("MYANIMELIST", 1, "ANIME"))
        assertEquals(
            "SUCCEEDED",
            database.trackingDao().getLibraryRefreshState("account-a", "ANIME")?.state,
        )
        assertTrue(
            database.trackingDao()
                .getLibraryRefreshEntries("account-a", "ANIME", 1)
                .isEmpty()
        )
    }

    @Test
    fun `failed refresh preserves last-good cache and records typed failure`() = runTest {
        val responses = ArrayDeque<MalAuthenticatedResult>()
        responses += page(listOf(row(10, "Ten", 1), row(11, "Eleven", 2)))
        val repository = repository { responses.removeFirst() }
        assertTrue(repository.refresh("account", TrackingMediaType.ANIME) is MalLibraryRefreshResult.Success)
        val successfulState = database.trackingDao().getLibraryRefreshState("account", "ANIME")

        responses += page(
            listOf(row(10, "Changed but incomplete", 9), row(12, "New but incomplete", 1)),
            next = "https://api.myanimelist.net/v2/users/@me/animelist?offset=2",
        )
        responses += MalAuthenticatedResult.Success(
            MalAuthenticatedResponse(503, Headers.headersOf(), "provider-error")
        )
        val failed = repository.refresh("account", TrackingMediaType.ANIME)

        failed as MalLibraryRefreshResult.Failure
        assertEquals(MalApiFailureKind.TRANSIENT_SERVER, failed.error.kind)
        assertEquals(2, failed.preservedEntryCount)
        val preserved = repository.observeLibrary("account", TrackingMediaType.ANIME).first()
        assertEquals(listOf(11L, 10L), preserved.map { it.malId })
        assertEquals("Ten", preserved.single { it.malId == 10L }.title)
        assertEquals(1, preserved.single { it.malId == 10L }.state.progress)
        val state = database.trackingDao().getLibraryRefreshState("account", "ANIME")
        assertEquals("FAILED", state?.state)
        assertEquals(successfulState?.lastSuccessAtEpochMillis, state?.lastSuccessAtEpochMillis)
    }

    @Test
    fun `cancellation clears staging and remains structured control flow`() = runTest {
        val repository = repository {
            throw CancellationException("cancelled")
        }
        var propagated = false
        try {
            repository.refresh("account", TrackingMediaType.MANGA)
        } catch (_: CancellationException) {
            propagated = true
        }

        assertTrue(propagated)
        assertEquals(
            "CANCELLED",
            database.trackingDao().getLibraryRefreshState("account", "MANGA")?.state,
        )
        assertTrue(
            database.trackingDao()
                .getLibraryRefreshEntries("account", "MANGA", 1)
                .isEmpty()
        )
    }

    @Test
    fun `delete local account data removes snapshots and refresh metadata`() = runTest {
        val repository = repository { page(listOf(row(15, "Fifteen", 5))) }
        repository.refresh("account", TrackingMediaType.ANIME)

        repository.deleteLocalAccountData("account")

        assertTrue(repository.observeLibrary("account", TrackingMediaType.ANIME).first().isEmpty())
        assertNull(database.trackingDao().getLibraryRefreshState("account", "ANIME"))
    }

    private fun repository(response: () -> MalAuthenticatedResult) = MalLibraryRepository(
        database,
        database.trackingDao(),
        MalListApi(
            executeAuthenticated = { _, requestFactory ->
                requestFactory()
                response()
            },
            requestFactory = MalListRequestFactory(),
        ),
        MediaIdentityRepository(
            database,
            database.mediaIdentityDao(),
            SystemMediaIdentityClock(),
            UuidMediaIdentityIdGenerator(),
        ),
    )

    private fun page(rows: List<String>, next: String? = null): MalAuthenticatedResult.Success {
        val paging = next?.let { "{\"next\":\"$it\"}" } ?: "{}"
        return MalAuthenticatedResult.Success(
            MalAuthenticatedResponse(
                200,
                Headers.headersOf(),
                "{\"data\":[${rows.joinToString(",")}],\"paging\":$paging}",
            )
        )
    }

    private fun row(id: Long, title: String, progress: Int): String =
        """{"node":{"id":$id,"title":"$title"},"list_status":{"status":"watching","num_episodes_watched":$progress,"updated_at":"2026-07-22T10:00:00Z"}}"""
}
