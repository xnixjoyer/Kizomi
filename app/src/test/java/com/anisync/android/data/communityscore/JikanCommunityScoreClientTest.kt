package com.anisync.android.data.communityscore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class JikanCommunityScoreClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: JikanCommunityScoreClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = JikanCommunityScoreClient(
            client = OkHttpClient(),
            userAgent = "AniSyncPlus-Test/1",
            baseUrl = server.url("/v4/"),
            minimumRequestIntervalMillis = 0
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun fetchAnimeDecodesScoreAndKeepsConditionalMetadata() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "score-v1")
                .setBody(
                    """
                    {
                      "data": {
                        "mal_id": 52991,
                        "score": 8.73,
                        "scored_by": 424242,
                        "rank": 42,
                        "title": "Sousou no Frieren",
                        "title_english": "Frieren: Beyond Journey's End",
                        "year": 2023,
                        "type": "TV",
                        "episodes": 28
                      }
                    }
                    """.trimIndent()
                )
        )

        val result = client.fetchAnime(52991, etag = "old", lastModified = "yesterday")

        assertTrue(result is JikanFetchResult.Success)
        val success = result as JikanFetchResult.Success
        assertEquals(52991, success.snapshot.malId)
        assertEquals(8.73, success.snapshot.score ?: 0.0, 0.0001)
        assertEquals(424242, success.snapshot.scoredBy)
        assertEquals("score-v1", success.etag)
        val request = server.takeRequest()
        assertEquals("/v4/anime/52991", request.path)
        assertEquals("old", request.getHeader("If-None-Match"))
        assertEquals("yesterday", request.getHeader("If-Modified-Since"))
        assertEquals("AniSyncPlus-Test/1", request.getHeader("User-Agent"))
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun fetchAnimeMapsNotModifiedAndRateLimitWithoutLosingRetryHint() = runTest {
        server.enqueue(MockResponse().setResponseCode(304))
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "12"))

        val notModified = client.fetchAnime(1, etag = "cached-etag")
        val rateLimited = client.fetchAnime(2)

        assertEquals(JikanFetchResult.NotModified("cached-etag", null), notModified)
        assertEquals(
            JikanFetchResult.Failure(
                JikanFailure(JikanFailureType.RATE_LIMITED, 429, 12_000)
            ),
            rateLimited
        )
    }

    @Test
    fun temporaryServerFailuresAreTypedAndNeverExposeResponseBodies() = runTest {
        listOf(500, 502, 503, 504).forEach { code ->
            server.enqueue(MockResponse().setResponseCode(code).setBody("secret socket detail"))
            val result = client.searchAnime("Frieren")
            assertEquals(
                JikanSearchResult.Failure(
                    JikanFailure(JikanFailureType.TEMPORARY_SERVER, code)
                ),
                result
            )
        }
    }

    @Test
    fun invalidJsonIsTyped() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json"))

        assertEquals(
            JikanSearchResult.Failure(
                JikanFailure(JikanFailureType.INVALID_RESPONSE, 200)
            ),
            client.searchAnime("Frieren")
        )
    }

    @Test
    fun transportExceptionsAreClassifiedWithoutUsingTheirMessages() {
        assertEquals(
            JikanFailure(JikanFailureType.OFFLINE),
            classifyJikanTransportFailure(UnknownHostException("credential=secret"))
        )
        assertEquals(
            JikanFailure(JikanFailureType.TIMEOUT),
            classifyJikanTransportFailure(SocketTimeoutException("private endpoint"))
        )
        assertEquals(
            JikanFailure(JikanFailureType.TRANSPORT),
            classifyJikanTransportFailure(SocketException("Software caused connection abort"))
        )
        assertEquals(
            JikanFailure(JikanFailureType.TRANSPORT),
            classifyJikanTransportFailure(IOException("raw transport detail"))
        )
    }

    @Test
    fun cancellingSearchCancelsTheUnderlyingOkHttpCall() = runTest {
        val cancelled = CountDownLatch(1)
        val okhttp = OkHttpClient.Builder()
            .eventListener(object : EventListener() {
                override fun callFailed(call: Call, ioe: IOException) {
                    if (call.isCanceled()) cancelled.countDown()
                }
            })
            .build()
        val cancellableClient = JikanCommunityScoreClient(
            client = okhttp,
            userAgent = "AniSyncPlus-Test/1",
            baseUrl = server.url("/v4/"),
            minimumRequestIntervalMillis = 0
        )
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val request = async(Dispatchers.IO) { cancellableClient.searchAnime("Frieren") }
        assertTrue(server.takeRequest(2, TimeUnit.SECONDS) != null)
        request.cancelAndJoin()

        assertTrue(request.isCancelled)
        assertTrue(cancelled.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun manualSearchIsBoundedAndDecodesCandidates() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "data": [
                    {"mal_id": 1, "score": 8.2, "title": "Cowboy Bebop", "year": 1998, "type": "TV", "episodes": 26},
                    {"mal_id": 4037, "score": 8.38, "title": "Cowboy Bebop: Tengoku no Tobira", "year": 2001, "type": "Movie", "episodes": 1}
                  ]
                }
                """.trimIndent()
            )
        )

        val result = client.searchAnime("Cowboy Bebop", limit = 99)

        assertTrue(result is JikanSearchResult.Success)
        assertEquals(2, (result as JikanSearchResult.Success).candidates.size)
        val request = server.takeRequest()
        assertEquals("Cowboy Bebop", request.requestUrl?.queryParameter("q"))
        assertEquals("10", request.requestUrl?.queryParameter("limit"))
        assertEquals("true", request.requestUrl?.queryParameter("sfw"))
        assertNull(request.getHeader("Authorization"))
    }
}
