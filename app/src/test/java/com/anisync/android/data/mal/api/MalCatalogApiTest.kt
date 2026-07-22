package com.anisync.android.data.mal.api

import com.anisync.android.data.mal.oauth.MalAuthenticatedResponse
import com.anisync.android.data.mal.oauth.MalAuthenticatedResult
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingStatus
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MalCatalogApiTest {
    @Test
    fun `MAL-only search uses official endpoint and maps list status without AniList decoration`() = runTest {
        val requests = mutableListOf<Request>()
        val api = api(requests) {
            success(
                """
                {"data":[{"node":{
                  "id":42,"title":"MAL native","main_picture":{"large":"https://image"},
                  "mean":8.2,"rank":11,"popularity":12,"media_type":"tv",
                  "num_episodes":24,"genres":[{"name":"Drama"}],
                  "my_list_status":{"status":"watching","score":9,"num_episodes_watched":5,
                    "comments":"private-catalog-note"}
                }}],"paging":{}}
                """.trimIndent()
            )
        }

        val result = api.search("account", TrackingMediaType.ANIME, "native title", limit = 25)
        val media = (result as MalApiResult.Success).value.entries.single()
        val request = requests.single()

        assertEquals("/v2/anime", request.url.encodedPath)
        assertEquals("native title", request.url.queryParameter("q"))
        assertEquals("25", request.url.queryParameter("limit"))
        assertTrue(request.url.queryParameter("fields")?.contains("my_list_status") == true)
        assertEquals("MYANIMELIST:ANIME:42", media.key.stableValue)
        assertEquals(TrackingStatus.CURRENT, media.listState?.status)
        assertEquals(5, media.listState?.progress)
        assertEquals(90.0, media.listState?.score100)
        assertNull(media.key.stableValue.toLongOrNull())
        assertFalse(media.toString().contains("private-catalog-note"))
    }

    @Test
    fun `details tolerates missing optional fields and keeps typed related media`() = runTest {
        val requests = mutableListOf<Request>()
        val api = api(requests) {
            success(
                """
                {"id":77,"title":"Sparse manga",
                 "pictures":[{"large":"https://image/one"}],
                 "background":"Publication background",
                 "related_anime":[{"node":{"id":88,"title":"Anime relation"},
                   "relation_type_formatted":"Adaptation"}],
                 "recommendations":[{"node":{"id":99,"title":"Recommended"},
                   "num_recommendations":3}]}
                """.trimIndent()
            )
        }

        val result = api.details("account", MalMediaKey(TrackingMediaType.MANGA, 77))
        val details = (result as MalApiResult.Success).value

        assertEquals("/v2/manga/77", requests.single().url.encodedPath)
        assertNull(details.synopsis)
        assertNull(details.pictureLarge)
        assertEquals(listOf("https://image/one"), details.pictureGallery)
        assertEquals("Publication background", details.background)
        assertEquals("MYANIMELIST:ANIME:88", details.related.single().key.stableValue)
        assertEquals("Adaptation", details.related.single().relationship)
        assertEquals("MYANIMELIST:MANGA:99", details.recommendations.single().key.stableValue)
    }

    @Test
    fun `ranking and seasonal requests expose rank and remain MAL native`() = runTest {
        val requests = mutableListOf<Request>()
        val responses = ArrayDeque(
            listOf(
                success(
                    """{"data":[{"node":{"id":1,"title":"Ranked"},"ranking":{"rank":4}}],"paging":{}}"""
                ),
                success(
                    """{"data":[{"node":{"id":2,"title":"Seasonal"}}],"paging":{}}"""
                ),
            )
        )
        val api = api(requests) { responses.removeFirst() }

        val ranked = api.ranking("account", TrackingMediaType.MANGA, "bypopularity", 10)
        val seasonal = api.seasonal("account", 2026, MalSeason.SUMMER, limit = 20)

        assertEquals(4, (ranked as MalApiResult.Success).value.entries.single().rankingPosition)
        assertEquals("/v2/manga/ranking", requests[0].url.encodedPath)
        assertEquals("bypopularity", requests[0].url.queryParameter("ranking_type"))
        assertEquals("/v2/anime/season/2026/summer", requests[1].url.encodedPath)
        assertEquals("anime_num_list_users", requests[1].url.queryParameter("sort"))
        assertTrue(seasonal is MalApiResult.Success)
    }

    @Test
    fun `hostile server paging URL is rejected before authenticated execution`() = runTest {
        var calls = 0
        val api = MalCatalogApi(
            executeAuthenticated = { _, requestFactory ->
                calls++
                requestFactory()
                success("{\"data\":[],\"paging\":{}}")
            },
            requestFactory = MalCatalogRequestFactory(),
            nowEpochMillis = { 1L },
        )

        val result = api.nextPage(
            "account",
            TrackingMediaType.ANIME,
            "https://evil.invalid/v2/anime?offset=1",
        )

        assertEquals(MalApiFailureKind.INVALID_PAGING_URL, (result as MalApiResult.Failure).error.kind)
        assertEquals(0, calls)
    }

    @Test
    fun `same-origin paging cannot cross media type or enter another MAL endpoint`() = runTest {
        var calls = 0
        val api = MalCatalogApi(
            executeAuthenticated = { _, requestFactory ->
                calls++
                requestFactory()
                success("{\"data\":[],\"paging\":{}}")
            },
            requestFactory = MalCatalogRequestFactory("https://api.myanimelist.net/v2/".toHttpUrl()),
            nowEpochMillis = { 1L },
        )

        val crossType = api.nextPage(
            "account",
            TrackingMediaType.ANIME,
            "https://api.myanimelist.net/v2/manga?offset=50",
        )
        val accountEndpoint = api.nextPage(
            "account",
            TrackingMediaType.ANIME,
            "https://api.myanimelist.net/v2/users/@me/animelist?offset=50",
        )

        assertEquals(MalApiFailureKind.INVALID_PAGING_URL, (crossType as MalApiResult.Failure).error.kind)
        assertEquals(MalApiFailureKind.INVALID_PAGING_URL, (accountEndpoint as MalApiResult.Failure).error.kind)
        assertEquals(0, calls)
    }

    private fun api(
        requests: MutableList<Request>,
        response: () -> MalAuthenticatedResult,
    ) = MalCatalogApi(
        executeAuthenticated = { _, requestFactory ->
            requests += requestFactory()
            response()
        },
        requestFactory = MalCatalogRequestFactory("http://localhost/v2/".toHttpUrl()),
        nowEpochMillis = { 1234L },
    )

    private fun success(body: String) = MalAuthenticatedResult.Success(
        MalAuthenticatedResponse(200, Headers.headersOf(), body)
    )
}
