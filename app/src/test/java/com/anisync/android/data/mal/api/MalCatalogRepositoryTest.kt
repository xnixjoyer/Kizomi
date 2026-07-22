package com.anisync.android.data.mal.api

import android.content.Context
import androidx.room.Room
import com.anisync.android.data.local.AppDatabase
import com.anisync.android.data.mal.oauth.MalAuthenticatedFailureReason
import com.anisync.android.data.mal.oauth.MalAuthenticatedResponse
import com.anisync.android.data.mal.oauth.MalAuthenticatedResult
import com.anisync.android.domain.tracking.TrackingMediaType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MalCatalogRepositoryTest {
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
    fun `search to details to cached reopen works with AniList request fail fake`() = runTest {
        val responses = ArrayDeque<MalAuthenticatedResult>(
            listOf(
                success(
                    """{"data":[{"node":{"id":501,"title":"Search result"}}],"paging":{}}"""
                ),
                success(
                    """{"id":501,"title":"Detailed title","synopsis":"Cached synopsis","mean":8.8,"my_list_status":{"status":"watching","num_episodes_watched":6}}"""
                ),
                success(
                    """{"data":[{"node":{"id":501,"title":"Thin search row"}}],"paging":{}}"""
                ),
            )
        )
        val requestedHosts = mutableListOf<String>()
        val repository = repository { request ->
            requestedHosts += request.url.host
            check(request.url.host != "graphql.anilist.co") { "AniList fallback is forbidden" }
            responses.removeFirst()
        }

        val search = repository.search("account", TrackingMediaType.ANIME, "Search")
        val key = (search as MalApiResult.Success).value.entries.single().key
        val details = repository.refreshDetails("account", key)
        repository.search("account", TrackingMediaType.ANIME, "Thin")
        val observed = repository.observeDetails(key).first()

        val liveDetails = (details as MalApiResult.Success).value
        assertEquals(6, liveDetails.listState?.progress)
        assertEquals("Detailed title", observed?.title)
        assertEquals("Cached synopsis", observed?.synopsis)
        assertTrue(observed?.isDetailed == true)
        assertNull(observed?.listState)
        assertTrue(requestedHosts.all { it == "localhost" })

        val offlineRepository = repository {
            MalAuthenticatedResult.Failure(
                MalAuthenticatedFailureReason.OFFLINE,
                localAccountId = "account",
            )
        }
        val reopened = offlineRepository.cachedDetails(key)
        val failedRefresh = offlineRepository.refreshDetails("account", key)

        assertNotNull(reopened)
        assertEquals("Cached synopsis", reopened?.synopsis)
        assertNull(reopened?.listState)
        assertEquals(MalApiFailureKind.OFFLINE, (failedRefresh as MalApiResult.Failure).error.kind)
        assertFalse(reopened.toString().contains("Cached synopsis"))
        val stored = database.trackingDao().getMalMedia(key.malId, key.mediaType.name)
        assertFalse(stored?.rawJson.orEmpty().contains("num_episodes_watched"))
    }

    @Test
    fun `cached search is empty-safe and includes MAL-only details cache`() = runTest {
        val repository = repository {
            success(
                """{"id":700,"title":"MAL only searchable","genres":[{"name":"Mystery"}]}"""
            )
        }
        val key = MalMediaKey(TrackingMediaType.MANGA, 700)
        repository.refreshDetails("account", key)

        assertTrue(repository.cachedSearch(TrackingMediaType.MANGA, "   ").isEmpty())
        val cached = repository.cachedSearch(TrackingMediaType.MANGA, "only")
        assertEquals(listOf(key), cached.map { it.key })
        assertEquals(listOf("Mystery"), cached.single().genres)
    }

    private fun repository(
        response: (okhttp3.Request) -> MalAuthenticatedResult,
    ): MalCatalogRepository {
        val api = MalCatalogApi(
            executeAuthenticated = { _, requestFactory -> response(requestFactory()) },
            requestFactory = MalCatalogRequestFactory("http://localhost/v2/".toHttpUrl()),
            nowEpochMillis = { 10_000L },
        )
        return MalCatalogRepository(database.trackingDao(), api)
    }

    private fun success(body: String) = MalAuthenticatedResult.Success(
        MalAuthenticatedResponse(200, Headers.headersOf(), body)
    )
}
