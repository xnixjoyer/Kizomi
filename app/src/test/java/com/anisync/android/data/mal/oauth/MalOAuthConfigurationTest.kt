package com.anisync.android.data.mal.oauth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MalOAuthConfigurationTest {
    @Test
    fun `all environment contracts validate and remain distinct`() {
        val configured = MalOAuthEnvironment.entries.map { environment ->
            val capability = validate(environment)
            assertTrue(capability is MalOAuthCapability.Configured)
            (capability as MalOAuthCapability.Configured).configuration
        }

        assertEquals(3, configured.map { it.redirectUri }.toSet().size)
        assertEquals(3, configured.map { it.environment }.toSet().size)
        assertNotEquals(configured[0].redirectUri, configured[1].redirectUri)
        assertNotEquals(configured[1].redirectUri, configured[2].redirectUri)
    }

    @Test
    fun `public client id is normalized once without appearing in diagnostics`() {
        val capability = MalOAuthConfigurationValidator.validate(
            input(
                environment = MalOAuthEnvironment.DEBUG,
                clientId = "  $TEST_CLIENT_ID  ",
            )
        ) as MalOAuthCapability.Configured

        assertEquals(TEST_CLIENT_ID, capability.configuration.clientId)
        assertFalse(capability.toString().contains(TEST_CLIENT_ID))
        assertFalse(
            MalOAuthConfigurationValidator.diagnostic(capability)
                .toString()
                .contains(TEST_CLIENT_ID)
        )
    }

    @Test
    fun `missing client id fails closed with typed reason`() {
        val capability = MalOAuthConfigurationValidator.validate(
            input(
                environment = MalOAuthEnvironment.DEBUG,
                clientId = "   ",
            )
        )

        assertEquals(
            MalOAuthCapability.Unavailable(
                environment = MalOAuthEnvironment.DEBUG,
                reason = MalOAuthUnavailableReason.MISSING_CLIENT_ID,
            ),
            capability,
        )
    }

    @Test
    fun `redirect from another environment is rejected`() {
        val capability = MalOAuthConfigurationValidator.validate(
            input(
                environment = MalOAuthEnvironment.STABLE,
                redirectUri = MalOAuthEnvironment.PREVIEW.redirectUri,
            )
        )

        assertEquals(
            MalOAuthUnavailableReason.REDIRECT_MISMATCH,
            (capability as MalOAuthCapability.Unavailable).reason,
        )
    }

    @Test
    fun `scheme host and path mismatches are rejected`() {
        val invalidRedirects = listOf(
            "wrong://oauth/mal/callback",
            "anisyncplus-debug://wrong/mal/callback",
            "anisyncplus-debug://oauth/wrong",
        )

        invalidRedirects.forEach { redirect ->
            val capability = MalOAuthConfigurationValidator.validate(
                input(
                    environment = MalOAuthEnvironment.DEBUG,
                    redirectUri = redirect,
                )
            )
            assertEquals(
                MalOAuthUnavailableReason.REDIRECT_MISMATCH,
                (capability as MalOAuthCapability.Unavailable).reason,
            )
        }
    }

    @Test
    fun `configured redirect cannot contain query fragment user info or port`() {
        val invalidRedirects = listOf(
            "anisyncplus-debug://oauth/mal/callback?code=fixture",
            "anisyncplus-debug://oauth/mal/callback#fragment",
            "anisyncplus-debug://user@oauth/mal/callback",
            "anisyncplus-debug://oauth:1234/mal/callback",
        )

        invalidRedirects.forEach { redirect ->
            val capability = MalOAuthConfigurationValidator.validate(
                input(
                    environment = MalOAuthEnvironment.DEBUG,
                    redirectUri = redirect,
                )
            )
            assertTrue(capability is MalOAuthCapability.Unavailable)
            assertFalse(capability is MalOAuthCapability.Configured)
        }
    }

    @Test
    fun `unknown environment and PKCE values fail explicitly`() {
        val unknownEnvironment = MalOAuthConfigurationValidator.validate(
            MalOAuthConfigurationInput(
                environment = "nightly",
                clientId = TEST_CLIENT_ID,
                redirectUri = MalOAuthEnvironment.DEBUG.redirectUri,
                pkceMethod = MalPkceMethod.PLAIN.name,
            )
        )
        assertEquals(
            MalOAuthUnavailableReason.UNKNOWN_ENVIRONMENT,
            (unknownEnvironment as MalOAuthCapability.Unavailable).reason,
        )

        val unsupportedPkce = MalOAuthConfigurationValidator.validate(
            input(
                environment = MalOAuthEnvironment.DEBUG,
                pkceMethod = "weak-custom-method",
            )
        )
        assertEquals(
            MalOAuthUnavailableReason.UNSUPPORTED_PKCE_METHOD,
            (unsupportedPkce as MalOAuthCapability.Unavailable).reason,
        )
    }

    @Test
    fun `configuration API contains no secret-bearing field`() {
        val fieldNames = sequenceOf(
            MalOAuthConfiguration::class.java,
            MalOAuthConfigurationInput::class.java,
            MalOAuthDiagnostic::class.java,
        ).flatMap { it.declaredFields.asSequence() }
            .map { it.name.lowercase() }
            .toList()

        assertTrue(fieldNames.none { "secret" in it })
    }

    @Test
    fun `diagnostics and string representations redact client and sensitive samples`() {
        val sensitiveSamples = listOf(
            TEST_CLIENT_ID,
            "access-token-fixture",
            "refresh-token-fixture",
            "authorization-code-fixture",
            "oauth-state-fixture",
            "pkce-verifier-fixture",
        )
        val input = input(
            environment = MalOAuthEnvironment.PREVIEW,
            clientId = TEST_CLIENT_ID,
        )
        val capability = MalOAuthConfigurationValidator.validate(input)
        val configuration = (capability as MalOAuthCapability.Configured).configuration
        val rendered = listOf(
            input.toString(),
            configuration.toString(),
            capability.toString(),
            MalOAuthConfigurationValidator.diagnostic(capability).toString(),
        ).joinToString("\n")

        sensitiveSamples.forEach { sensitive ->
            assertFalse("rendered output leaked $sensitive", rendered.contains(sensitive))
        }
        assertTrue(rendered.contains("<redacted>"))
        assertTrue(rendered.contains("PREVIEW"))
    }

    @Test
    fun `unavailable diagnostic exposes only sanitized category`() {
        val capability = MalOAuthConfigurationValidator.validate(
            input(
                environment = MalOAuthEnvironment.STABLE,
                clientId = "",
            )
        )
        val diagnostic = MalOAuthConfigurationValidator.diagnostic(capability)

        assertFalse(diagnostic.configured)
        assertEquals("STABLE", diagnostic.environment)
        assertEquals("anisyncplus://oauth", diagnostic.redirectOrigin)
        assertEquals("/mal/callback", diagnostic.redirectPath)
        assertEquals("MISSING_CLIENT_ID", diagnostic.unavailableReason)
        assertFalse(diagnostic.toString().contains(TEST_CLIENT_ID))
    }

    private fun validate(environment: MalOAuthEnvironment): MalOAuthCapability =
        MalOAuthConfigurationValidator.validate(input(environment = environment))

    private fun input(
        environment: MalOAuthEnvironment,
        clientId: String = TEST_CLIENT_ID,
        redirectUri: String = environment.redirectUri,
        pkceMethod: String = MalPkceMethod.PLAIN.name,
    ) = MalOAuthConfigurationInput(
        environment = environment.buildValue,
        clientId = clientId,
        redirectUri = redirectUri,
        pkceMethod = pkceMethod,
    )

    private companion object {
        const val TEST_CLIENT_ID = "public-client-id-fixture"
    }
}
