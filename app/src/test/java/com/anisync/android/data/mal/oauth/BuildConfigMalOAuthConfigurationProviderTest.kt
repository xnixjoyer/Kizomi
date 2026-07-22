package com.anisync.android.data.mal.oauth

import com.anisync.android.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class BuildConfigMalOAuthConfigurationProviderTest {
    @Test
    fun `provider maps the compiled variant without exposing the client id`() {
        val provider = BuildConfigMalOAuthConfigurationProvider()
        val environment = MalOAuthEnvironment.fromBuildValue(BuildConfig.MAL_OAUTH_ENVIRONMENT)

        assertNotNull(environment)
        assertEquals(environment, provider.capability.environment)
        assertEquals(environment?.redirectUri, BuildConfig.MAL_OAUTH_REDIRECT_URI)

        val diagnostic = provider.diagnostic().toString()
        if (BuildConfig.MAL_OAUTH_CLIENT_ID.isNotBlank()) {
            assertFalse(diagnostic.contains(BuildConfig.MAL_OAUTH_CLIENT_ID))
        }
    }
}
