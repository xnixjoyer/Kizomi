package com.anisync.android.data.communityscore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class JikanCommunityScoreFailureTest {
    @Test
    fun `transport exceptions are classified without retaining raw messages`() {
        val cases = listOf(
            UnknownHostException("private dns details") to JikanFailureType.OFFLINE,
            SocketTimeoutException("private timeout details") to JikanFailureType.TIMEOUT,
            InterruptedIOException("private call timeout details") to JikanFailureType.TIMEOUT,
            SocketException("Software caused connection abort") to JikanFailureType.TRANSPORT,
            IOException("private transport details") to JikanFailureType.TRANSPORT
        )

        cases.forEach { (exception, expected) ->
            val failure = classifyJikanTransportFailure(exception)
            assertEquals(expected, failure.type)
            assertEquals(null, failure.httpStatus)
            assertEquals(null, failure.retryAfterMillis)
            assertTrue(failure.toString().contains("private").not())
            assertTrue(failure.toString().contains("Software caused").not())
        }
    }

    @Test
    fun `500 502 503 and 504 are temporary server failures`() = withServer { server ->
        val client = client(server)

        listOf(500, 502, 503, 504).forEach { status ->
            server.enqueue(MockResponse().setResponseCode(status).setBody("not for the UI"))
            val failure = runBlocking { client.searchAnime("Frieren") }.failure()
            assertEquals(JikanFailureType.TEMPORARY_SERVER, failure.type)
            assertEquals(status, failure.httpStatus)
        }
    }

    @Test
    fun `429 exposes only typed rate limit metadata`() = withServer { server ->
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "7")
                .setBody("server details must stay private")
        )

        val failure = runBlocking { client(server).searchAnime("Frieren") }.failure()

        assertEquals(JikanFailureType.RATE_LIMITED, failure.type)
        assertEquals(429, failure.httpStatus)
        assertEquals(7_000L, failure.retryAfterMillis)
        assertTrue(failure.toString().contains("server details").not())
    }

    @Test
    fun `invalid response is typed and retry can succeed`() = withServer { server ->
        server.enqueue(MockResponse().setBody("{not-json"))
        server.enqueue(
            MockResponse().setBody(
                """
                {"data":[{"mal_id":52991,"title":"Sousou no Frieren","score":8.9}]}
                """.trimIndent()
            )
        )
        val client = client(server)

        val first = runBlocking { client.searchAnime("Frieren") }
        val second = runBlocking { client.searchAnime("Frieren") }

        assertEquals(JikanFailureType.INVALID_RESPONSE, first.failure().type)
        assertTrue(second is JikanSearchResult.Success)
        assertEquals(52991, (second as JikanSearchResult.Success).candidates.single().malId)
    }

    @Test
    fun `manual retry can fail again without losing typed status`() = withServer { server ->
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(504))
        val client = client(server)

        val first = runBlocking { client.searchAnime("Frieren") }.failure()
        val second = runBlocking { client.searchAnime("Frieren") }.failure()

        assertEquals(JikanFailureType.TEMPORARY_SERVER, first.type)
        assertEquals(500, first.httpStatus)
        assertEquals(JikanFailureType.TEMPORARY_SERVER, second.type)
        assertEquals(504, second.httpStatus)
    }

    @Test
    fun `call timeout is classified as timeout`() = withServer { server ->
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val okHttp = OkHttpClient.Builder()
            .callTimeout(150, TimeUnit.MILLISECONDS)
            .build()
        val client = client(server, okHttp)

        val failure = runBlocking { client.searchAnime("Frieren") }.failure()

        assertEquals(JikanFailureType.TIMEOUT, failure.type)
    }

    @Test
    fun `cancelling coroutine cancels the in flight request instead of creating a UI failure`() = withServer { server ->
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val client = client(server)

        runBlocking {
            val request = launch(Dispatchers.IO) { client.searchAnime("Frieren") }
            withTimeout(2_000L) {
                while (server.requestCount == 0) delay(10L)
            }

            request.cancelAndJoin()

            assertTrue(request.isCancelled)
        }
    }

    private fun client(
        server: MockWebServer,
        okHttpClient: OkHttpClient = OkHttpClient()
    ) = JikanCommunityScoreClient(
        client = okHttpClient,
        userAgent = "AniSyncPlus-Test/1",
        baseUrl = server.url("/v4/"),
        minimumRequestIntervalMillis = 0,
        maximumRequestsPerWindow = 100
    )

    private fun JikanSearchResult.failure(): JikanFailure {
        assertTrue(this is JikanSearchResult.Failure)
        return (this as JikanSearchResult.Failure).error
    }

    private fun withServer(block: (MockWebServer) -> Unit) {
        val server = MockWebServer()
        server.start()
        try {
            block(server)
        } finally {
            server.shutdown()
        }
    }
}
