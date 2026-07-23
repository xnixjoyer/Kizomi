package com.anisync.android.data.mal.api

import android.content.Context
import androidx.room.Room
import com.anisync.android.data.identity.MediaIdentityRepository
import com.anisync.android.data.identity.SystemMediaIdentityClock
import com.anisync.android.data.identity.UuidMediaIdentityIdGenerator
import com.anisync.android.data.local.AppDatabase
import com.anisync.android.data.local.entity.MalImportEntryEntity
import com.anisync.android.data.mal.oauth.MalAuthenticatedResponse
import com.anisync.android.data.mal.oauth.MalAuthenticatedFailureReason
import com.anisync.android.data.mal.oauth.MalAuthenticatedResult
import com.anisync.android.domain.tracking.TrackingMediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    fun `multi-page import deduplicates rows and creates MAL-only identities`() = runTest {
        val responses = ArrayDeque(
            listOf(
                page(
                    rows = listOf(row(1, "One", progress = 2), row(2, "Two", progress = 3)),
                    next = "https://api.myanimelist.net/v2/users/@me/animelist?offset=2",
                ),
                page(rows = listOf(row(2, "Two duplicate", 3), row(3, "Three", 4))),
            )
        )
        val repository = repository { responses.removeFirst() }

        val result = repository.refresh("account-a", TrackingMediaType.ANIME, pageSize = 2)
        val library = repository.observeLibrary("account-a", TrackingMediaType.ANIME).first()

        assertEquals(MalImportResult.Success(3, 0, 2), result)
        assertEquals(listOf(1L, 3L, 2L), library.map { it.malId })
        assertTrue(library.none { it.toString().contains(it.title) })
        assertEquals(3, database.mediaIdentityDao().countLocalIdentities())
        assertEquals(3, database.mediaIdentityDao().countProviderIdentities())
        assertNull(
            database.mediaIdentityDao().findByProviderId("ANILIST", 1, "ANIME")
        )
        assertNotNull(
            database.mediaIdentityDao().findByProviderId("MYANIMELIST", 1, "ANIME")
        )
        assertEquals("SUCCEEDED", database.trackingDao().getImportState("account-a", "ANIME")?.state)
    }

    @Test
    fun `failed refresh preserves last-good cache and records typed failure`() = runTest {
        val responses = ArrayDeque<MalAuthenticatedResult>()
        responses += page(
            listOf(row(10, "Ten", 1), row(11, "Eleven", 2))
        )
        val repository = repository { responses.removeFirst() }
        assertTrue(repository.refresh("account", TrackingMediaType.ANIME) is MalImportResult.Success)
        val successfulState = database.trackingDao().getImportState("account", "ANIME")

        responses += page(
            listOf(row(10, "Changed but incomplete", 9), row(12, "New but incomplete", 1)),
            next = "https://api.myanimelist.net/v2/users/@me/animelist?offset=2",
        )
        responses += MalAuthenticatedResult.Success(
            MalAuthenticatedResponse(
                503,
                Headers.headersOf(),
                "private-provider-error",
            )
        )
        val failed = repository.refresh("account", TrackingMediaType.ANIME)

        failed as MalImportResult.Failure
        assertEquals(MalApiFailureKind.TRANSIENT_SERVER, failed.error.kind)
        assertEquals(2, failed.preservedEntryCount)
        val preserved = repository.observeLibrary("account", TrackingMediaType.ANIME).first()
        assertEquals(listOf(11L, 10L), preserved.map { it.malId })
        assertEquals("Ten", preserved.single { it.malId == 10L }.title)
        assertEquals(1, preserved.single { it.malId == 10L }.state.progress)
        val state = database.trackingDao().getImportState("account", "ANIME")
        assertEquals("FAILED", state?.state)
        assertEquals(successfulState?.lastSuccessAtEpochMillis, state?.lastSuccessAtEpochMillis)
    }

    @Test
    fun `successful refresh promotes a complete generation and removes only missing rows`() = runTest {
        val responses = ArrayDeque<MalAuthenticatedResult>(
            listOf(
                page(listOf(row(13, "Old", 1), row(14, "Keep", 2))),
                page(listOf(row(14, "Updated", 7))),
            )
        )
        val repository = repository { responses.removeFirst() }
        repository.refresh("account", TrackingMediaType.ANIME)

        val result = repository.refresh("account", TrackingMediaType.ANIME)
        val library = repository.observeLibrary("account", TrackingMediaType.ANIME).first()

        assertEquals(MalImportResult.Success(1, 1, 1), result)
        assertEquals(listOf(14L), library.map { it.malId })
        assertEquals("Updated", library.single().title)
        assertEquals(7, library.single().state.progress)
    }

    @Test
    fun `offline refresh returns typed retryable failure while cached library stays readable`() = runTest {
        var online = true
        val repository = repository {
            if (online) {
                page(listOf(row(15, "Offline cache", 3)))
            } else {
                MalAuthenticatedResult.Failure(
                    MalAuthenticatedFailureReason.OFFLINE,
                    localAccountId = "account",
                )
            }
        }
        repository.refresh("account", TrackingMediaType.ANIME)
        online = false

        val failure = repository.refresh("account", TrackingMediaType.ANIME)

        failure as MalImportResult.Failure
        assertEquals(MalApiFailureKind.OFFLINE, failure.error.kind)
        assertTrue(failure.error.retryable)
        assertEquals("Offline cache", repository.observeLibrary("account", TrackingMediaType.ANIME).first().single().title)
    }

    @Test
    fun `account caches are isolated and local account delete does not remove other account`() = runTest {
        var title = "A"
        val repository = repository { page(listOf(row(20, title, 5))) }
        repository.refresh("account-a", TrackingMediaType.ANIME)
        title = "B"
        repository.refresh("account-b", TrackingMediaType.ANIME)

        assertEquals("A", repository.observeLibrary("account-a", TrackingMediaType.ANIME).first().single().title)
        assertEquals("B", repository.observeLibrary("account-b", TrackingMediaType.ANIME).first().single().title)
        repository.deleteLocalAccountData("account-a")
        assertTrue(repository.observeLibrary("account-a", TrackingMediaType.ANIME).first().isEmpty())
        assertEquals(1, repository.observeLibrary("account-b", TrackingMediaType.ANIME).first().size)
        assertNull(database.trackingDao().getImportState("account-a", "ANIME"))
    }

    @Test
    fun `paging loop fails safely without deleting imported or previous rows`() = runTest {
        val loop = "https://api.myanimelist.net/v2/users/@me/animelist?offset=1"
        val responses = ArrayDeque<MalAuthenticatedResult>(
            listOf(
                page(listOf(row(29, "Previous last-good", 2))),
                page(listOf(row(30, "Loop", 1)), next = loop),
                page(listOf(row(30, "Loop duplicate", 1)), next = loop),
            )
        )
        val repository = repository { responses.removeFirst() }
        assertTrue(repository.refresh("account", TrackingMediaType.ANIME) is MalImportResult.Success)

        val result = repository.refresh("account", TrackingMediaType.ANIME)

        assertEquals(MalApiFailureKind.PAGING_LOOP, (result as MalImportResult.Failure).error.kind)
        assertEquals(1, result.preservedEntryCount)
        val preserved = repository.observeLibrary("account", TrackingMediaType.ANIME).first()
        assertEquals(listOf(29L), preserved.map { it.malId })
        assertEquals("Previous last-good", preserved.single().title)
    }

    @Test
    fun `cancellation is persisted and propagated`() = runTest {
        val repository = MalLibraryRepository(
            database,
            database.trackingDao(),
            MalListApi(
                executeAuthenticated = { _, _ -> throw CancellationException("stop") },
                requestFactory = MalListRequestFactory(),
            ),
            identityRepository(),
        )
        var propagated = false
        try {
            repository.refresh("account", TrackingMediaType.MANGA)
        } catch (_: CancellationException) {
            propagated = true
        }
        assertTrue(propagated)
        assertEquals("CANCELLED", database.trackingDao().getImportState("account", "MANGA")?.state)
    }

    @Test
    fun `process-death RUNNING checkpoint resumes from persisted next page`() = runTest {
        val nextUrl = "https://api.myanimelist.net/v2/users/@me/animelist?offset=1"
        val firstRepository = repository { page(listOf(row(40, "Before death", 1))) }
        assertTrue(firstRepository.refresh("account", TrackingMediaType.ANIME) is MalImportResult.Success)
        val successful = requireNotNull(database.trackingDao().getImportState("account", "ANIME"))
        val generation = successful.generation + 1
        val generationStartedAt = successful.lastAttemptAtEpochMillis + 1
        val identity = requireNotNull(
            database.mediaIdentityDao().findByProviderId("MYANIMELIST", 40, "ANIME")
        )
        val stagedEntry = listEntry(40, "Before death", 1)
        database.trackingDao().upsertImportEntries(
            listOf(
                MalImportEntryEntity(
                    localAccountId = "account",
                    mediaType = "ANIME",
                    generation = generation,
                    malId = 40,
                    localMediaId = identity.localMediaId,
                    payloadJson = Json.encodeToString(stagedEntry),
                )
            )
        )
        database.trackingDao().upsertImportState(
            successful.copy(
                state = "RUNNING",
                generation = generation,
                nextPageUrl = nextUrl,
                importedCount = 1,
                lastAttemptAtEpochMillis = generationStartedAt,
                lastErrorKind = null,
            )
        )

        var resumedRequest: okhttp3.Request? = null
        val resumedRepository = MalLibraryRepository(
            database,
            database.trackingDao(),
            MalListApi(
                executeAuthenticated = { _, requestFactory ->
                    resumedRequest = requestFactory()
                    page(listOf(row(41, "After death", 2)))
                },
                requestFactory = MalListRequestFactory(),
            ),
            identityRepository(),
        )
        val resumed = resumedRepository.refresh("account", TrackingMediaType.ANIME)

        assertEquals(MalImportResult.Success(2, 0, 1), resumed)
        assertEquals("1", resumedRequest?.url?.queryParameter("offset"))
        assertEquals(2, resumedRepository.observeLibrary("account", TrackingMediaType.ANIME).first().size)
        assertEquals(generation, database.trackingDao().getImportState("account", "ANIME")?.generation)
        assertTrue(database.trackingDao().getImportEntries("account", "ANIME", generation).isEmpty())
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
        identityRepository(),
    )

    private fun identityRepository() = MediaIdentityRepository(
        database,
        database.mediaIdentityDao(),
        SystemMediaIdentityClock(),
        UuidMediaIdentityIdGenerator(),
    )

    private fun page(
        rows: List<String>,
        next: String? = null,
    ): MalAuthenticatedResult.Success {
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

    private fun listEntry(id: Long, title: String, progress: Int) = MalListEntry(
        malId = id,
        mediaType = TrackingMediaType.ANIME,
        title = title,
        alternativeTitles = emptyList(),
        synopsis = null,
        pictureMedium = null,
        pictureLarge = null,
        meanScore = null,
        rank = null,
        popularity = null,
        mediaStatus = null,
        startDate = null,
        endDate = null,
        episodeCount = null,
        chapterCount = null,
        volumeCount = null,
        genres = emptyList(),
        desiredState = com.anisync.android.domain.tracking.TrackingDesiredState(
            status = com.anisync.android.domain.tracking.TrackingStatus.CURRENT,
            progress = progress,
        ),
        providerUpdatedAtEpochMillis = 1L,
        rawMediaJson = "{}",
        rawListStatusJson = "{}",
    )
}
