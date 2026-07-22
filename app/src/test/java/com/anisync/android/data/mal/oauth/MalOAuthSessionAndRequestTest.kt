package com.anisync.android.data.mal.oauth

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class MalOAuthSessionAndRequestTest {
    @Test
    fun `PKCE verifier state and challenge satisfy the public-client contract`() {
        val generator = MalPkceGenerator()
        val first = generator.create(MalPkceMethod.PLAIN)
        val second = generator.create(MalPkceMethod.PLAIN)

        assertEquals(MalPkceGenerator.VERIFIER_LENGTH, first.verifier.length)
        assertTrue(first.verifier.all { it.isLetterOrDigit() || it in "-._~" })
        assertEquals(first.verifier, first.challenge)
        assertNotEquals(first.verifier, second.verifier)
        assertNotEquals(first.state, second.state)
        assertTrue(first.state.length >= 32)
    }

    @Test
    fun `S256 challenge matches RFC 7636 vector`() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            MalPkceGenerator.challenge(
                "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
                MalPkceMethod.S256,
            ),
        )
    }

    @Test
    fun `authorization URL uses exact configuration and contains no secret`() {
        val configuration = configuration()
        val session = session()
        val factory = MalOAuthRequestFactory(
            authorizeEndpoint = "https://example.test/authorize".toHttpUrl(),
            tokenEndpoint = "https://example.test/token".toHttpUrl(),
        )

        val url = factory.authorizationUrl(configuration, session)

        assertEquals("code", url.queryParameter("response_type"))
        assertEquals("public-client", url.queryParameter("client_id"))
        assertEquals("challenge-fixture", url.queryParameter("code_challenge"))
        assertEquals("plain", url.queryParameter("code_challenge_method"))
        assertEquals("state-fixture", url.queryParameter("state"))
        assertEquals(configuration.redirectUri.toString(), url.queryParameter("redirect_uri"))
        assertFalse(url.queryParameterNames.contains("client_secret"))
    }

    @Test
    fun `token requests use required public-client fields and never client secret`() {
        val configuration = configuration()
        val factory = MalOAuthRequestFactory(
            authorizeEndpoint = "https://example.test/authorize".toHttpUrl(),
            tokenEndpoint = "https://example.test/token".toHttpUrl(),
        )

        val exchange = factory.authorizationCodeRequest(
            configuration,
            code = "code-fixture",
            verifier = "verifier-fixture",
        ).body as FormBody
        val exchangeFields = exchange.asMap()
        assertEquals("public-client", exchangeFields["client_id"])
        assertEquals("authorization_code", exchangeFields["grant_type"])
        assertEquals("code-fixture", exchangeFields["code"])
        assertEquals("verifier-fixture", exchangeFields["code_verifier"])
        assertEquals(configuration.redirectUri.toString(), exchangeFields["redirect_uri"])
        assertFalse(exchangeFields.containsKey("client_secret"))

        val refresh = factory.refreshRequest(configuration, "refresh-fixture").body as FormBody
        val refreshFields = refresh.asMap()
        assertEquals("public-client", refreshFields["client_id"])
        assertEquals("refresh_token", refreshFields["grant_type"])
        assertEquals("refresh-fixture", refreshFields["refresh_token"])
        assertFalse(refreshFields.containsKey("client_secret"))
    }

    @Test
    fun `session and PKCE string forms redact sensitive values`() {
        val rendered = listOf(
            session().toString(),
            MalPkceMaterial("verifier-fixture", "challenge-fixture", "state-fixture").toString(),
        ).joinToString("\n")

        assertFalse(rendered.contains("verifier-fixture"))
        assertFalse(rendered.contains("challenge-fixture"))
        assertFalse(rendered.contains("state-fixture"))
        assertTrue(rendered.contains("<redacted>"))
    }

    private fun configuration() = MalOAuthConfiguration(
        environment = MalOAuthEnvironment.DEBUG,
        clientId = "public-client",
        redirectUri = URI(MalOAuthEnvironment.DEBUG.redirectUri),
        pkceMethod = MalPkceMethod.PLAIN,
    )

    private fun session() = MalOAuthSession(
        sessionId = "session-fixture",
        environment = MalOAuthEnvironment.DEBUG,
        redirectUri = MalOAuthEnvironment.DEBUG.redirectUri,
        pkceMethod = MalPkceMethod.PLAIN,
        verifier = "verifier-fixture",
        challenge = "challenge-fixture",
        state = "state-fixture",
        createdAtEpochMillis = 1_000L,
        expiresAtEpochMillis = 2_000L,
    )

    private fun FormBody.asMap(): Map<String, String> =
        (0 until size).associate { index -> name(index) to value(index) }
}
