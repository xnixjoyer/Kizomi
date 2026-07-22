package com.anisync.android.data.mal.oauth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

fun interface MalOAuthClock {
    fun nowEpochMillis(): Long
}

@Singleton
class SystemMalOAuthClock @Inject constructor() : MalOAuthClock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}

fun interface MalOAuthSessionIdGenerator {
    fun newSessionId(): String
}

@Singleton
class UuidMalOAuthSessionIdGenerator @Inject constructor() : MalOAuthSessionIdGenerator {
    override fun newSessionId(): String = UUID.randomUUID().toString()
}

enum class MalOAuthSessionPhase {
    AWAITING_CALLBACK,
    CALLBACK_STAGED,
}

data class MalPkceMaterial(
    val verifier: String,
    val challenge: String,
    val state: String,
) {
    override fun toString(): String =
        "MalPkceMaterial(verifier=<redacted>, challenge=<redacted>, state=<redacted>)"
}

data class MalOAuthSession(
    val sessionId: String,
    val environment: MalOAuthEnvironment,
    val redirectUri: String,
    val pkceMethod: MalPkceMethod,
    val verifier: String,
    val challenge: String,
    val state: String,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val targetLocalAccountId: String? = null,
    val expectedTokenGeneration: Long? = null,
    val phase: MalOAuthSessionPhase = MalOAuthSessionPhase.AWAITING_CALLBACK,
    val authorizationCode: String? = null,
) {
    init {
        require(sessionId.isNotBlank()) { "MAL OAuth session ID must not be blank" }
        require(redirectUri.isNotBlank()) { "MAL OAuth redirect URI must not be blank" }
        require(verifier.isNotBlank()) { "MAL OAuth verifier must not be blank" }
        require(challenge.isNotBlank()) { "MAL OAuth challenge must not be blank" }
        require(state.isNotBlank()) { "MAL OAuth state must not be blank" }
        require(expiresAtEpochMillis > createdAtEpochMillis) {
            "MAL OAuth session expiry must be after creation"
        }
        require((targetLocalAccountId == null) == (expectedTokenGeneration == null)) {
            "MAL OAuth re-login target and generation must be supplied together"
        }
        require(phase == MalOAuthSessionPhase.CALLBACK_STAGED || authorizationCode == null) {
            "Authorization code is valid only for a staged callback"
        }
        require(authorizationCode == null || authorizationCode.isNotBlank()) {
            "Authorization code must be null or non-blank"
        }
    }

    val isCallbackStaged: Boolean
        get() = phase == MalOAuthSessionPhase.CALLBACK_STAGED && authorizationCode != null

    fun isExpired(nowEpochMillis: Long): Boolean = nowEpochMillis >= expiresAtEpochMillis

    override fun toString(): String =
        "MalOAuthSession(sessionId=$sessionId, environment=${environment.name}, redirectUri=<redacted>, pkceMethod=${pkceMethod.name}, verifier=<redacted>, challenge=<redacted>, state=<redacted>, createdAtEpochMillis=$createdAtEpochMillis, expiresAtEpochMillis=$expiresAtEpochMillis, targetLocalAccountId=${targetLocalAccountId ?: "none"}, expectedTokenGeneration=${expectedTokenGeneration ?: "none"}, phase=${phase.name}, authorizationCode=${if (authorizationCode == null) "absent" else "<redacted>"})"
}

@Singleton
class MalPkceGenerator @Inject constructor() {
    private val random = SecureRandom()

    fun create(method: MalPkceMethod): MalPkceMaterial {
        val verifier = buildString(VERIFIER_LENGTH) {
            repeat(VERIFIER_LENGTH) {
                append(ALLOWED[random.nextInt(ALLOWED.length)])
            }
        }
        val stateBytes = ByteArray(STATE_BYTES).also(random::nextBytes)
        val state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes)
        return MalPkceMaterial(
            verifier = verifier,
            challenge = challenge(verifier, method),
            state = state,
        )
    }

    companion object {
        const val VERIFIER_LENGTH = 128
        private const val STATE_BYTES = 24
        private const val ALLOWED =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

        fun challenge(verifier: String, method: MalPkceMethod): String = when (method) {
            MalPkceMethod.PLAIN -> verifier
            MalPkceMethod.S256 -> Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256")
                    .digest(verifier.toByteArray(Charsets.US_ASCII))
            )
        }

        fun stateHash(state: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256")
                    .digest(state.toByteArray(Charsets.UTF_8))
            )

        fun statesEqual(expected: String, actual: String): Boolean = MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            actual.toByteArray(Charsets.UTF_8),
        )
    }
}
