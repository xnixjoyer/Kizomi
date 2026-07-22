package com.anisync.android.data.mal.api

import com.anisync.android.data.mal.oauth.MalAuthenticatedResponse
import com.anisync.android.data.mal.oauth.MalAuthenticatedResult
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MalListApiTest {
    @Test
    fun `anime page sends official fields and maps MAL-native row without AniList id`() = runTest {
        val requests = mutableListOf<okhttp3.Request>()
        val api = api(requests) {
            success(
                """
                {
                  "data": [{
                    "node": {
                      "id": 42,
                      "title": "Fixture Anime",
                      "main_picture": {"medium": "https://img/medium", "large": "https://img/large"},
                      "alternative_titles": {"synonyms": ["Alt"], "en": "English", "ja": "日本語"},
                      "start_date": "2026-01-01",
                      "synopsis": "Summary",
                      "mean": 8.5,
                      "rank": 10,
                      "popularity": 20,
                      "status": "currently_airing",
                      "num_episodes": 12,
                      "genres": [{"id": 1, "name": "Action"}]
                    },
                    "list_status": {
                      "status": "watching",
                      "score": 8,
                      "num_episodes_watched": 4,
                      "is_rewatching": false,
                      "num_times_rewatched": 1,
                      "comments": "private-note-sentinel",
                      "start_date": "2026-07-01",
                      "updated_at": "2026-07-22T10:00:00Z"
                    }
                  }],
                  "paging": {}
                }
                """.trimIndent()
            )
        }

        val result = api.firstPage(
            "account",
            TrackingMediaType.ANIME,
            status = "watching",
            limit = 250,
        )
        val page = (result as MalApiResult.Success).value
        val requestUrl = requests.single().url
        val entry = page.entries.single()

        assertEquals("/v2/users/@me/animelist", requestUrl.encodedPath)
        assertEquals("250", requestUrl.queryParameter("limit"))
        assertEquals("watching", requestUrl.queryParameter("status"))
        assertTrue(requestUrl.queryParameter("fields")?.contains("list_status") == true)
        assertEquals(42L, entry.malId)
        assertEquals(TrackingStatus.CURRENT, entry.desiredState.status)
        assertEquals(4, entry.desiredState.progress)
        assertEquals(80.0, entry.desiredState.score100)
        assertEquals(1, entry.desiredState.repeatCount)
        assertEquals(listOf("Alt", "English", "日本語"), entry.alternativeTitles)
        assertNull(page.nextPageUrl)
        assertFalse(entry.toString().contains("private-note-sentinel"))
        assertFalse(entry.desiredState.toString().contains("private-note-sentinel"))
    }

    @Test
    fun `manga page maps chapters volumes reread and plan status`() = runTest {
        val api = api(mutableListOf()) {
            success(
                """
                {"data":[{"node":{"id":77,"title":"Manga"},"list_status":{
                  "status":"plan_to_read","score":0,"num_chapters_read":44,
                  "num_volumes_read":8,"is_rereading":true,"num_times_reread":2
                }}],"paging":{}}
                """.trimIndent()
            )
        }

        val entry = ((api.firstPage("account", TrackingMediaType.MANGA) as MalApiResult.Success)
            .value.entries.single())

        assertEquals(TrackingStatus.REPEATING, entry.desiredState.status)
        assertEquals(44, entry.desiredState.progress)
        assertEquals(8, entry.desiredState.progressSecondary)
        assertEquals(2, entry.desiredState.repeatCount)
        assertNull(entry.desiredState.score100)
    }

    @Test
    fun `server paging URL is followed only on the exact configured origin and base path`() = runTest {
        var calls = 0
        val api = MalListApi(
            executeAuthenticated = { _, requestFactory ->
                calls++
                requestFactory()
                success("{\"data\":[],\"paging\":{}}")
            },
            requestFactory = MalListRequestFactory(),
        )

        val hostile = api.nextPage(
            "account",
            TrackingMediaType.ANIME,
            "https://attacker.invalid/v2/users/@me/animelist?offset=100",
        )
        val wrongPath = api.nextPage(
            "account",
            TrackingMediaType.ANIME,
            "https://api.myanimelist.net/oauth/steal",
        )

        assertEquals(MalApiFailureKind.INVALID_PAGING_URL, (hostile as MalApiResult.Failure).error.kind)
        assertEquals(MalApiFailureKind.INVALID_PAGING_URL, (wrongPath as MalApiResult.Failure).error.kind)
        assertEquals(0, calls)
    }

    @Test
    fun `MockWebServer paging follows an accepted next link and maps both pages`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val next = server.url("/v2/users/@me/animelist?offset=1").toString()
            server.enqueue(
                MockResponse().setBody(
                    "{\"data\":[${row(1, "First")}],\"paging\":{\"next\":\"$next\"}}"
                )
            )
            server.enqueue(
                MockResponse().setBody(
                    "{\"data\":[${row(2, "Second")}],\"paging\":{}}"
                )
            )
            val httpClient = OkHttpClient()
            val api = MalListApi(
                executeAuthenticated = { _, requestFactory ->
                    httpClient.newCall(requestFactory()).execute().use { response ->
                        MalAuthenticatedResult.Success(
                            MalAuthenticatedResponse(
                                response.code,
                                response.headers,
                                response.body?.string().orEmpty(),
                            )
                        )
                    }
                },
                requestFactory = MalListRequestFactory(server.url("/v2/")),
            )

            val first = (api.firstPage("account", TrackingMediaType.ANIME) as MalApiResult.Success).value
            val second = (api.nextPage(
                "account",
                TrackingMediaType.ANIME,
                requireNotNull(first.nextPageUrl),
            ) as MalApiResult.Success).value

            assertEquals(1L, first.entries.single().malId)
            assertEquals(2L, second.entries.single().malId)
            assertEquals("/v2/users/@me/animelist", server.takeRequest().requestUrl?.encodedPath)
            assertEquals("1", server.takeRequest().requestUrl?.queryParameter("offset"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `rate limit and malformed rows are typed without exposing provider body`() = runTest {
        var response = MalAuthenticatedResult.Success(
            MalAuthenticatedResponse(
                statusCode = 429,
                headers = Headers.headersOf("Retry-After", "12"),
                body = "server-private-body",
            )
        )
        val api = MalListApi(
            executeAuthenticated = { _, _ -> response },
            requestFactory = MalListRequestFactory(),
        )
        val limited = api.firstPage("account", TrackingMediaType.ANIME)
        assertEquals(
            MalApiFailure(MalApiFailureKind.RATE_LIMITED, 429, 12_000),
            (limited as MalApiResult.Failure).error,
        )
        assertFalse(limited.toString().contains("server-private-body"))

        response = success(
            """{"data":[{"node":{"id":1,"title":"Bad"},"list_status":{"status":"unknown"}}]}"""
        )
        val malformed = api.firstPage("account", TrackingMediaType.ANIME)
        assertEquals(MalApiFailureKind.INVALID_RESPONSE, (malformed as MalApiResult.Failure).error.kind)
    }

    @Test
    fun `cancellation propagates instead of becoming a retry result`() = runTest {
        val api = MalListApi(
            executeAuthenticated = { _, _ -> throw CancellationException("stop") },
            requestFactory = MalListRequestFactory(),
        )
        var propagated = false
        try {
            api.firstPage("account", TrackingMediaType.ANIME)
        } catch (_: CancellationException) {
            propagated = true
        }
        assertTrue(propagated)
    }

    @Test
    fun `large response preserves every distinct MAL-native row`() = runTest {
        val rows = List(1_000) { index ->
            """{"node":{"id":${index + 1},"title":"Title $index"},"list_status":{"status":"watching","num_episodes_watched":$index}}"""
        }
        val api = api(mutableListOf()) {
            success("{\"data\":[${rows.joinToString(",")}],\"paging\":{}}")
        }

        val page = (api.firstPage("account", TrackingMediaType.ANIME, limit = 1_000)
            as MalApiResult.Success).value

        assertEquals(1_000, page.entries.size)
        assertEquals(1L, page.entries.first().malId)
        assertEquals(1_000L, page.entries.last().malId)
    }

    private fun api(
        requests: MutableList<okhttp3.Request>,
        response: () -> MalAuthenticatedResult,
    ) = MalListApi(
        executeAuthenticated = { _, requestFactory ->
            requests += requestFactory()
            response()
        },
        requestFactory = MalListRequestFactory("http://localhost/v2/".toHttpUrl()),
    )

    private fun success(body: String): MalAuthenticatedResult.Success =
        MalAuthenticatedResult.Success(
            MalAuthenticatedResponse(200, Headers.headersOf(), body)
        )

    private fun row(id: Long, title: String): String =
        """{"node":{"id":$id,"title":"$title"},"list_status":{"status":"watching"}}"""
}
