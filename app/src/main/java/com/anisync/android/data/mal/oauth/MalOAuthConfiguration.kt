package com.anisync.android.data.mal.oauth

import java.net.URI

enum class MalOAuthEnvironment(
    val buildValue: String,
    val redirectUri: String,
) {
    DEBUG(
        buildValue = "DEBUG",
        redirectUri = "anisyncplus-debug://oauth/mal/callback",
    ),
    PREVIEW(
        buildValue = "PREVIEW",
        redirectUri = "anisyncplus-preview://oauth/mal/callback",
    ),
    STABLE(
        buildValue = "STABLE",
        redirectUri = "anisyncplus://oauth/mal/callback",
    );

    companion object {
        fun fromBuildValue(value: String): MalOAuthEnvironment? =
            entries.firstOrNull { it.buildValue == value.trim().uppercase() }
    }
}

enum class MalPkceMethod {
    PLAIN,
    S256,
}

data class MalOAuthConfigurationInput(
    val environment: String,
    val clientId: String,
    val redirectUri: String,
    val pkceMethod: String,
) {
    override fun toString(): String =
        "MalOAuthConfigurationInput(environment=${environment.trim().uppercase()}, clientId=<redacted>, redirectUri=<redacted>, pkceMethod=${pkceMethod.trim().uppercase()})"
}

class MalOAuthConfiguration internal constructor(
    val environment: MalOAuthEnvironment,
    val clientId: String,
    val redirectUri: URI,
    val pkceMethod: MalPkceMethod,
) {
    override fun toString(): String =
        "MalOAuthConfiguration(environment=${environment.name}, clientId=<redacted>, redirect=${redirectUri.scheme}://${redirectUri.host}${redirectUri.path}, pkceMethod=${pkceMethod.name})"
}

enum class MalOAuthUnavailableReason {
    UNKNOWN_ENVIRONMENT,
    MISSING_CLIENT_ID,
    INVALID_REDIRECT_URI,
    REDIRECT_MISMATCH,
    UNSUPPORTED_PKCE_METHOD,
}

sealed interface MalOAuthCapability {
    val environment: MalOAuthEnvironment?

    data class Configured(
        val configuration: MalOAuthConfiguration,
    ) : MalOAuthCapability {
        override val environment: MalOAuthEnvironment = configuration.environment

        override fun toString(): String =
            "MalOAuthCapability.Configured(environment=${environment.name})"
    }

    data class Unavailable(
        override val environment: MalOAuthEnvironment?,
        val reason: MalOAuthUnavailableReason,
    ) : MalOAuthCapability {
        override fun toString(): String =
            "MalOAuthCapability.Unavailable(environment=${environment?.name ?: "UNKNOWN"}, reason=${reason.name})"
    }
}

data class MalOAuthDiagnostic(
    val environment: String,
    val configured: Boolean,
    val redirectOrigin: String,
    val redirectPath: String,
    val unavailableReason: String?,
) {
    override fun toString(): String = buildString {
        append("MalOAuthDiagnostic(environment=")
        append(environment)
        append(", configured=")
        append(configured)
        append(", redirectOrigin=")
        append(redirectOrigin)
        append(", redirectPath=")
        append(redirectPath)
        append(", unavailableReason=")
        append(unavailableReason ?: "none")
        append(')')
    }
}

object MalOAuthConfigurationValidator {
    fun validate(input: MalOAuthConfigurationInput): MalOAuthCapability {
        val environment = MalOAuthEnvironment.fromBuildValue(input.environment)
            ?: return MalOAuthCapability.Unavailable(
                environment = null,
                reason = MalOAuthUnavailableReason.UNKNOWN_ENVIRONMENT,
            )

        val clientId = input.clientId.trim()
        if (clientId.isEmpty()) {
            return MalOAuthCapability.Unavailable(
                environment = environment,
                reason = MalOAuthUnavailableReason.MISSING_CLIENT_ID,
            )
        }

        val pkceMethod = runCatching {
            MalPkceMethod.valueOf(input.pkceMethod.trim().uppercase())
        }.getOrNull() ?: return MalOAuthCapability.Unavailable(
            environment = environment,
            reason = MalOAuthUnavailableReason.UNSUPPORTED_PKCE_METHOD,
        )

        val redirect = parseRedirect(input.redirectUri)
            ?: return MalOAuthCapability.Unavailable(
                environment = environment,
                reason = MalOAuthUnavailableReason.INVALID_REDIRECT_URI,
            )

        val expected = URI(environment.redirectUri)
        if (redirect != expected) {
            return MalOAuthCapability.Unavailable(
                environment = environment,
                reason = MalOAuthUnavailableReason.REDIRECT_MISMATCH,
            )
        }

        return MalOAuthCapability.Configured(
            MalOAuthConfiguration(
                environment = environment,
                clientId = clientId,
                redirectUri = redirect,
                pkceMethod = pkceMethod,
            )
        )
    }

    fun diagnostic(capability: MalOAuthCapability): MalOAuthDiagnostic {
        val environment = capability.environment
        val redirect = environment?.let { URI(it.redirectUri) }
        return MalOAuthDiagnostic(
            environment = environment?.name ?: "UNKNOWN",
            configured = capability is MalOAuthCapability.Configured,
            redirectOrigin = redirect?.let { "${it.scheme}://${it.host}" } ?: "unavailable",
            redirectPath = redirect?.path ?: "unavailable",
            unavailableReason = (capability as? MalOAuthCapability.Unavailable)?.reason?.name,
        )
    }

    private fun parseRedirect(value: String): URI? {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
        if (!uri.isAbsolute) return null
        if (uri.scheme.isNullOrBlank() || uri.host.isNullOrBlank()) return null
        if (uri.userInfo != null || uri.port != -1) return null
        if (uri.rawQuery != null || uri.rawFragment != null) return null
        if (uri.path.isNullOrBlank()) return null
        return uri
    }
}
