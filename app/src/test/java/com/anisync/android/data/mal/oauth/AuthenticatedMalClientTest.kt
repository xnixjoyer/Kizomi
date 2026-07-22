package com.anisync.android.data.mal.oauth

import com.anisync.android.data.mal.account.MalTokenSet
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.MockWebServer
import java.net.UnknownHostException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthenticatedMalClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `401 performs one refresh and retries once with centrally replaced bearer`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val fixture = fixture(this)

        val result = fixture.client.execute("local-1") {
            Request.Builder()
                .url(server.url("/resource"))
                .header("Authorization", "Bearer caller-must-not-win")
                .build()
        }

        assertTrue(result is MalAuthenticatedResult.Success)
        result as MalAuthenticatedResult.Success
        assertEquals(200, result.response.statusCode)
        assertEquals("ok", result.response.body)
        assertEquals(1, fixture.service.refreshCalls)
        assertEquals("Bearer old-access", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer refreshed-access", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `second 401 is not retried and marks relogin required`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))
        val fixture = fixture(this)

        val result = fixture.client.execute("local-1") {
            Request.Builder().url(server.url("/resource")).build()
        }

        assertEquals(
            MalAuthenticatedFailureReason.RELOGIN_REQUIRED,
            (result as MalAuthenticatedResult.Failure).reason,
        )
        assertEquals(2, server.requestCount)
        assertNull(fixture.accounts.token("local-1"))
    }

    @Test
    fun `parallel 401 requests share refresh result`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.getHeader("Authorization") == "Bearer old-access") {
                    MockResponse().setResponseCode(401)
                } else {
                    MockResponse().setResponseCode(200).setBody("ok")
                }
        }
        val fixture = fixture(this)

        val results = List(2) {
            async {
                fixture.client.execute("local-1") {
                    Request.Builder().url(server.url("/resource")) .build()
                }
            }
        }.awaitAll()

        assertTrue(results.all { it is MalAuthenticatedResult.Success })
        assertEquals(1, fixture.service.refreshCalls)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `preemptive refresh occurs for near-expiry token`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val fixture = fixture(this, expiresAt = 1_500L)

        val result = fixture.client.execute("local-1") {
            Request.Builder().url(server.url("/resource")).build()
        }

        assertTrue(result is MalAuthenticatedResult.Success)
        assertEquals(1, fixture.service.refreshCalls)
        assertEquals("Bearer refreshed-access", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `unknown host is classified as offline without leaking transport details`() = runTest {
        val offlineHttpClient = OkHttpClient.Builder()
            .dns { throw UnknownHostException("private-dns-sentinel") }
            .build()
        val fixture = fixture(this, httpClient = offlineHttpClient)

        val result = fixture.client.execute("local-1") {
            Request.Builder().url("https://offline.invalid/resource").build()
        }

        result as MalAuthenticatedResult.Failure
        assertEquals(MalAuthenticatedFailureReason.OFFLINE, result.reason)
        assertTrue(!result.toString().contains("private-dns-sentinel"))
        assertTrue(!result.toString().contains("local-1"))
    }

    private fun fixture(
        scope: kotlinx.coroutines.CoroutineScope,
        expiresAt: Long = 1_000_000L,
        httpClient: OkHttpClient = OkHttpClient(),
    ): Fixture {
        val accounts = FakeMalAccountCredentialStore()
        accounts.seed(
            tokenSet = MalTokenSet(
                accessToken = "old-access",
                refreshToken = "old-refresh",
                expiresAtEpochMillis = expiresAt,
            )
        )
        val service = FakeMalOAuthTokenService()
        val clock = MutableMalOAuthClock(1_000L)
        val coordinator = MalRefreshCoordinator(
            configurationProvider = FakeMalOAuthConfigurationSource(),
            accountStore = accounts,
            tokenService = service,
            scope = scope,
        )
        return Fixture(
            accounts = accounts,
            service = service,
            client = AuthenticatedMalClient(
                client = httpClient,
                accountStore = accounts,
                refreshCoordinator = coordinator,
                clock = clock,
            ),
        )
    }

    private data class Fixture(
        val accounts: FakeMalAccountCredentialStore,
        val service: FakeMalOAuthTokenService,
        val client: AuthenticatedMalClient,
    )
}
