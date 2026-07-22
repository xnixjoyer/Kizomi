package com.anisync.android.data.mal.oauth

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URI
import java.util.concurrent.TimeUnit

class MalOAuthTransportTest {
    private lateinit var server: MockWebServer
    private val clock = MutableMalOAuthClock(10_000L)

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
    fun `authorization code exchange parses tokens and sends no client secret`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"token_type":"Bearer","expires_in":3600,"access_token":"access-fixture","refresh_token":"refresh-fixture","scope":"read write"}"""
                )
        )
        val service = service()

        val result = service.exchangeAuthorizationCode(
            configuration(),
            code = "code-fixture",
            verifier = "verifier-fixture",
        )

        assertTrue(result is MalOAuthTransportResult.Success)
        result as MalOAuthTransportResult.Success
        assertEquals("access-fixture", result.value.accessToken)
        assertEquals("refresh-fixture", result.value.refreshToken)
        assertEquals(3_610_000L, result.value.expiresAtEpochMillis)
        assertEquals(setOf("read", "write"), result.value.scopes)

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("client_id=public-client"))
        assertTrue(body.contains("grant_type=authorization_code"))
        assertTrue(body.contains("code=code-fixture"))
        assertTrue(body.contains("code_verifier=verifier-fixture"))
        assertTrue(body.contains("redirect_uri="))
        assertFalse(body.contains("client_secret"))
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `refresh accepts omitted rotated refresh token`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"token_type":"Bearer","expires_in":60,"access_token":"new-access","scope":"read"}"""
                )
        )

        val result = service().refresh(configuration(), "old-refresh")

        assertTrue(result is MalOAuthTransportResult.Success)
        result as MalOAuthTransportResult.Success
        assertNull(result.value.refreshToken)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("grant_type=refresh_token"))
        assertTrue(body.contains("refresh_token=old-refresh"))
        assertFalse(body.contains("client_secret"))
    }

    @Test
    fun `provider and HTTP failures are typed without response body leakage`() = runTest {
        val fixtures = listOf(
            MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant","error_description":"sensitive"}""") to
                MalOAuthTransportFailureReason.INVALID_GRANT,
            MockResponse().setResponseCode(401).setBody("""{"error":"invalid_client"}""") to
                MalOAuthTransportFailureReason.INVALID_CLIENT,
            MockResponse().setResponseCode(429).addHeader("Retry-After", "17").setBody("limited") to
                MalOAuthTransportFailureReason.RATE_LIMITED,
            MockResponse().setResponseCode(503).setBody("server detail") to
                MalOAuthTransportFailureReason.SERVER_ERROR,
        )

        fixtures.forEach { (response, expected) ->
            server.enqueue(response)
            val result = service().refresh(configuration(), "refresh-fixture")
            assertEquals(expected, (result as MalOAuthTransportResult.Failure).reason)
            assertFalse(result.toString().contains("sensitive"))
            assertFalse(result.toString().contains("server detail"))
        }
    }

    @Test
    fun `malformed success response is rejected`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"expires_in":3600}"""))

        val result = service().refresh(configuration(), "refresh-fixture")

        assertEquals(
            MalOAuthTransportFailureReason.MALFORMED_RESPONSE,
            (result as MalOAuthTransportResult.Failure).reason,
        )
    }

    @Test
    fun `read timeout is typed and does not expose token`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBodyDelay(2, TimeUnit.SECONDS)
                .setBody(
                    """{"token_type":"Bearer","expires_in":60,"access_token":"late-access"}"""
                )
        )
        val client = OkHttpClient.Builder()
            .readTimeout(100, TimeUnit.MILLISECONDS)
            .build()

        val result = service(client).refresh(configuration(), "refresh-timeout-fixture")

        assertEquals(
            MalOAuthTransportFailureReason.TIMEOUT,
            (result as MalOAuthTransportResult.Failure).reason,
        )
        assertFalse(result.toString().contains("refresh-timeout-fixture"))
        assertFalse(result.toString().contains("late-access"))
    }

    private fun service(client: OkHttpClient = OkHttpClient()): OkHttpMalOAuthTokenService =
        OkHttpMalOAuthTokenService(
            client = client,
            requestFactory = MalOAuthRequestFactory(
                authorizeEndpoint = server.url("/authorize"),
                tokenEndpoint = server.url("/token"),
            ),
            clock = clock,
        )

    private fun configuration() = MalOAuthConfiguration(
        environment = MalOAuthEnvironment.DEBUG,
        clientId = "public-client",
        redirectUri = URI(MalOAuthEnvironment.DEBUG.redirectUri),
        pkceMethod = MalPkceMethod.PLAIN,
    )
}
