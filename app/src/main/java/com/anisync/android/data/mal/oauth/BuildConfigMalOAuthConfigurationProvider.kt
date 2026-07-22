package com.anisync.android.data.mal.oauth

import com.anisync.android.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

interface MalOAuthConfigurationSource {
    val capability: MalOAuthCapability
    val isLoginConfigured: Boolean
    fun diagnostic(): MalOAuthDiagnostic
}

@Singleton
class BuildConfigMalOAuthConfigurationProvider @Inject constructor() : MalOAuthConfigurationSource {
    override val capability: MalOAuthCapability by lazy(LazyThreadSafetyMode.PUBLICATION) {
        MalOAuthConfigurationValidator.validate(
            MalOAuthConfigurationInput(
                environment = BuildConfig.MAL_OAUTH_ENVIRONMENT,
                clientId = BuildConfig.MAL_OAUTH_CLIENT_ID,
                redirectUri = BuildConfig.MAL_OAUTH_REDIRECT_URI,
                pkceMethod = BuildConfig.MAL_OAUTH_PKCE_METHOD,
            )
        )
    }

    override val isLoginConfigured: Boolean
        get() = capability is MalOAuthCapability.Configured

    override fun diagnostic(): MalOAuthDiagnostic =
        MalOAuthConfigurationValidator.diagnostic(capability)
}
