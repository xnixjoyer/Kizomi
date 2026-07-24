package com.anisync.android.data.mal.oauth

import com.anisync.android.data.mal.account.MalAccount
import com.anisync.android.data.mal.account.MalAccountCredentialStore
import com.anisync.android.data.mal.account.MalAccountFailureReason
import com.anisync.android.data.mal.account.MalAccountProfile
import com.anisync.android.data.mal.account.MalAccountResult
import com.anisync.android.data.mal.account.MalTokenSet
import com.anisync.android.data.mal.account.MalTokenStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

enum class MalAuthFailureReason {
    CONFIGURATION_UNAVAILABLE,
    ACCOUNT_NOT_FOUND,
    SESSION_STORE_FAILED,
    NO_PENDING_SESSION,
    CALLBACK_REPLAY,
    CALLBACK_REDIRECT_MISMATCH,
    STATE_MISMATCH,
    SESSION_EXPIRED,
    MISSING_AUTHORIZATION_CODE,
    PROVIDER_ACCESS_DENIED,
    PROVIDER_ERROR,
    INVALID_GRANT,
    INVALID_CLIENT,
    RATE_LIMITED,
    SERVER_ERROR,
    TIMEOUT,
    TRANSPORT,
    CANCELLED,
    MALFORMED_RESPONSE,
    PERMANENT_HTTP_ERROR,
    ACCOUNT_PERSISTENCE_FAILED,
    ACCOUNT_SESSION_CHANGED,
}

sealed interface MalAuthState {
    data class Disconnected(val configured: Boolean) : MalAuthState

    data class AwaitingCallback(val sessionId: String) : MalAuthState {
        override fun toString(): String =
            "MalAuthState.AwaitingCallback(sessionId=<redacted>)"
    }

    data class Processing(val sessionId: String) : MalAuthState {
        override fun toString(): String =
            "MalAuthState.Processing(sessionId=<redacted>)"
    }

    data class Connected(val account: MalAccount) : MalAuthState {
        override fun toString(): String =
            "MalAuthState.Connected(account=<redacted>)"
    }

    data class ReLoginRequired(val localAccountId: String) : MalAuthState {
        override fun toString(): String =
            "MalAuthState.ReLoginRequired(localAccountId=<redacted>)"
    }

    data class Error(
        val reason: MalAuthFailureReason,
        val localAccountId: String? = null,
        val retryAfterSeconds: Long? = null,
    ) : MalAuthState {
        override fun toString(): String =
            "MalAuthState.Error(reason=${reason.name}, localAccountId=<redacted>, " +
                "retryAfterSeconds=${retryAfterSeconds ?: "none"})"
    }
}

sealed interface MalLoginStartResult {
    data class Success(
        val authorizationUrl: String,
        val sessionId: String,
    ) : MalLoginStartResult {
        override fun toString(): String =
            "MalLoginStartResult.Success(authorizationUrl=<redacted>, sessionId=<redacted>)"
    }

    data class Failure(val reason: MalAuthFailureReason) : MalLoginStartResult
}

sealed interface MalCallbackResult {
    data class Success(val account: MalAccount) : MalCallbackResult {
        override fun toString(): String =
            "MalCallbackResult.Success(account=<redacted>)"
    }

    data class Failure(
        val reason: MalAuthFailureReason,
        val retryAfterSeconds: Long? = null,
    ) : MalCallbackResult
}

@Singleton
class MalAuthRepository @Inject constructor(
    private val configurationProvider: MalOAuthConfigurationSource,
    private val sessionStore: MalOAuthSessionStore,
    private val pkceGenerator: MalPkceGenerator,
    private val sessionIdGenerator: MalOAuthSessionIdGenerator,
    private val clock: MalOAuthClock,
    private val tokenService: MalOAuthTokenService,
    private val accountStore: MalAccountCredentialStore,
) {
    private val requestFactory = MalOAuthRequestFactory()
    private val _state = MutableStateFlow<MalAuthState>(
        MalAuthState.Disconnected(configurationProvider.isLoginConfigured)
    )
    val state: StateFlow<MalAuthState> = _state.asStateFlow()

    suspend fun refreshState() {
        if (sessionStore.consumeInitializationReset()) {
            _state.value = MalAuthState.Error(MalAuthFailureReason.SESSION_STORE_FAILED)
            return
        }
        when (val pending = sessionStore.read()) {
            is MalOAuthSessionStoreResult.Failure -> {
                _state.value = MalAuthState.Error(MalAuthFailureReason.SESSION_STORE_FAILED)
            }
            is MalOAuthSessionStoreResult.Success -> {
                val session = pending.value
                if (session != null) {
                    if (session.isExpired(clock.nowEpochMillis())) {
                        consume(session)
                        _state.value = MalAuthState.Error(MalAuthFailureReason.SESSION_EXPIRED)
                    } else if (session.isCallbackStaged) {
                        completePendingLogin(session.sessionId)
                    } else {
                        _state.value = MalAuthState.AwaitingCallback(session.sessionId)
                    }
                } else {
                    val active = accountStore.activeAccount()
                    _state.value = when {
                        active == null -> MalAuthState.Disconnected(configurationProvider.isLoginConfigured)
                        active.tokenStatus == MalTokenStatus.ACTIVE ||
                            active.tokenStatus == MalTokenStatus.EXPIRED -> MalAuthState.Connected(active)
                        else -> MalAuthState.ReLoginRequired(active.localAccountId)
                    }
                }
            }
        }
    }

    suspend fun startLogin(targetLocalAccountId: String? = null): MalLoginStartResult {
        val configuration = (configurationProvider.capability as? MalOAuthCapability.Configured)
            ?.configuration
            ?: return MalLoginStartResult.Failure(MalAuthFailureReason.CONFIGURATION_UNAVAILABLE)
        val target = if (targetLocalAccountId == null) {
            null
        } else {
            when (val result = accountStore.getAccount(targetLocalAccountId)) {
                is MalAccountResult.Success -> result.value
                is MalAccountResult.Failure -> return MalLoginStartResult.Failure(
                    MalAuthFailureReason.ACCOUNT_NOT_FOUND
                )
            }
        }
        val now = clock.nowEpochMillis()
        val pkce = pkceGenerator.create(configuration.pkceMethod)
        val session = MalOAuthSession(
            sessionId = sessionIdGenerator.newSessionId(),
            environment = configuration.environment,
            redirectUri = configuration.redirectUri.toString(),
            pkceMethod = configuration.pkceMethod,
            verifier = pkce.verifier,
            challenge = pkce.challenge,
            state = pkce.state,
            createdAtEpochMillis = now,
            expiresAtEpochMillis = now + SESSION_LIFETIME_MS,
            targetLocalAccountId = target?.localAccountId,
            expectedTokenGeneration = target?.tokenGeneration,
        )
        if (sessionStore.save(session) !is MalOAuthSessionStoreResult.Success) {
            _state.value = MalAuthState.Error(MalAuthFailureReason.SESSION_STORE_FAILED)
            return MalLoginStartResult.Failure(MalAuthFailureReason.SESSION_STORE_FAILED)
        }
        val authorizationUrl = requestFactory.authorizationUrl(configuration, session).toString()
        _state.value = MalAuthState.AwaitingCallback(session.sessionId)
        return MalLoginStartResult.Success(authorizationUrl, session.sessionId)
    }

    fun isCallbackCandidate(callbackUri: String): Boolean {
        val callback = parseUri(callbackUri) ?: return false
        return MalOAuthEnvironment.entries.any { environment ->
            val expected = URI(environment.redirectUri)
            callback.scheme == expected.scheme &&
                callback.host == expected.host &&
                callback.path == expected.path
        }
    }

    suspend fun handleCallback(callbackUri: String): MalCallbackResult {
        val callback = parseUri(callbackUri)
            ?: return callbackFailure(MalAuthFailureReason.CALLBACK_REDIRECT_MISMATCH)
        val parameters = parseQuery(callback.rawQuery)
        val pending = when (val stored = sessionStore.read()) {
            is MalOAuthSessionStoreResult.Failure -> return callbackFailure(
                MalAuthFailureReason.SESSION_STORE_FAILED
            )
            is MalOAuthSessionStoreResult.Success -> stored.value
        }
        if (pending == null) {
            val callbackState = parameters["state"]
            val lastHash = (sessionStore.lastConsumedStateHash() as? MalOAuthSessionStoreResult.Success)
                ?.value
            val replay = callbackState != null && lastHash != null &&
                MalPkceGenerator.statesEqual(MalPkceGenerator.stateHash(callbackState), lastHash)
            return callbackFailure(
                if (replay) MalAuthFailureReason.CALLBACK_REPLAY
                else MalAuthFailureReason.NO_PENDING_SESSION
            )
        }

        val now = clock.nowEpochMillis()
        if (pending.isExpired(now)) {
            consume(pending)
            return callbackFailure(MalAuthFailureReason.SESSION_EXPIRED)
        }
        if (!matchesRedirect(callback, pending.redirectUri)) {
            consume(pending)
            return callbackFailure(MalAuthFailureReason.CALLBACK_REDIRECT_MISMATCH)
        }
        val actualState = parameters["state"]
        if (actualState == null || !MalPkceGenerator.statesEqual(pending.state, actualState)) {
            consume(pending)
            return callbackFailure(MalAuthFailureReason.STATE_MISMATCH)
        }

        val providerError = parameters["error"]?.trim()?.lowercase()
        if (providerError != null) {
            consume(pending)
            return callbackFailure(
                if (providerError == "access_denied") {
                    MalAuthFailureReason.PROVIDER_ACCESS_DENIED
                } else {
                    MalAuthFailureReason.PROVIDER_ERROR
                }
            )
        }
        val code = parameters["code"]?.trim().orEmpty()
        if (code.isEmpty()) {
            consume(pending)
            return callbackFailure(MalAuthFailureReason.MISSING_AUTHORIZATION_CODE)
        }

        val staged = pending.copy(
            phase = MalOAuthSessionPhase.CALLBACK_STAGED,
            authorizationCode = code,
        )
        if (sessionStore.save(staged) !is MalOAuthSessionStoreResult.Success) {
            _state.value = MalAuthState.Error(MalAuthFailureReason.SESSION_STORE_FAILED)
            return callbackFailure(MalAuthFailureReason.SESSION_STORE_FAILED)
        }
        return completePendingLogin(staged.sessionId)
    }

    suspend fun resumePendingLogin(): MalCallbackResult? {
        val session = when (val stored = sessionStore.read()) {
            is MalOAuthSessionStoreResult.Failure -> {
                _state.value = MalAuthState.Error(MalAuthFailureReason.SESSION_STORE_FAILED)
                return MalCallbackResult.Failure(MalAuthFailureReason.SESSION_STORE_FAILED)
            }
            is MalOAuthSessionStoreResult.Success -> stored.value
        } ?: run {
            refreshState()
            return null
        }
        if (session.isExpired(clock.nowEpochMillis())) {
            consume(session)
            return callbackFailure(MalAuthFailureReason.SESSION_EXPIRED)
        }
        return if (session.isCallbackStaged) {
            completePendingLogin(session.sessionId)
        } else {
            _state.value = MalAuthState.AwaitingCallback(session.sessionId)
            null
        }
    }

    suspend fun logout(localAccountId: String): MalAccountResult<MalAccount> {
        sessionStore.clearPending()
        val result = accountStore.logout(localAccountId)
        _state.value = MalAuthState.Disconnected(configurationProvider.isLoginConfigured)
        return result
    }

    suspend fun cancelPendingLogin() {
        sessionStore.clearPending()
        refreshState()
    }

    private suspend fun completePendingLogin(sessionId: String): MalCallbackResult {
        val configuration = (configurationProvider.capability as? MalOAuthCapability.Configured)
            ?.configuration
            ?: return callbackFailure(MalAuthFailureReason.CONFIGURATION_UNAVAILABLE)
        val session = when (val stored = sessionStore.read()) {
            is MalOAuthSessionStoreResult.Failure -> return callbackFailure(
                MalAuthFailureReason.SESSION_STORE_FAILED
            )
            is MalOAuthSessionStoreResult.Success -> stored.value
        }
        if (session == null || session.sessionId != sessionId || !session.isCallbackStaged) {
            return callbackFailure(MalAuthFailureReason.ACCOUNT_SESSION_CHANGED)
        }
        if (session.isExpired(clock.nowEpochMillis())) {
            consume(session)
            return callbackFailure(MalAuthFailureReason.SESSION_EXPIRED)
        }
        val code = checkNotNull(session.authorizationCode)
        _state.value = MalAuthState.Processing(session.sessionId)
        val exchange = tokenService.exchangeAuthorizationCode(
            configuration = configuration,
            code = code,
            verifier = session.verifier,
        )
        if (exchange is MalOAuthTransportResult.Failure) {
            val failure = exchange.toAuthFailure()
            if (exchange.reason in TERMINAL_EXCHANGE_FAILURES) consume(session)
            _state.value = MalAuthState.Error(
                reason = failure,
                localAccountId = session.targetLocalAccountId,
                retryAfterSeconds = exchange.retryAfterSeconds,
            )
            return MalCallbackResult.Failure(failure, exchange.retryAfterSeconds)
        }
        exchange as MalOAuthTransportResult.Success

        val latest = (sessionStore.read() as? MalOAuthSessionStoreResult.Success)?.value
        if (latest == null || latest.sessionId != session.sessionId || !latest.isCallbackStaged ||
            latest.isExpired(clock.nowEpochMillis())
        ) {
            return callbackFailure(MalAuthFailureReason.ACCOUNT_SESSION_CHANGED)
        }
        val tokens = MalTokenSet(
            accessToken = exchange.value.accessToken,
            refreshToken = exchange.value.refreshToken,
            expiresAtEpochMillis = exchange.value.expiresAtEpochMillis,
            scopes = exchange.value.scopes,
        )
        val persisted = if (session.targetLocalAccountId == null) {
            accountStore.createAccount(
                profile = MalAccountProfile(),
                tokens = tokens,
                makeActive = true,
            )
        } else {
            accountStore.replaceTokensIfGeneration(
                localAccountId = session.targetLocalAccountId,
                expectedGeneration = checkNotNull(session.expectedTokenGeneration),
                tokens = tokens,
                requireExistingCredentials = false,
            ).also { result ->
                if (result is MalAccountResult.Success) {
                    accountStore.selectActive(session.targetLocalAccountId)
                }
            }
        }
        val account = when (persisted) {
            is MalAccountResult.Success -> persisted.value
            is MalAccountResult.Failure -> {
                val reason = if (persisted.reason == MalAccountFailureReason.ACCOUNT_SESSION_CHANGED ||
                    persisted.reason == MalAccountFailureReason.ACCOUNT_NOT_FOUND
                ) {
                    MalAuthFailureReason.ACCOUNT_SESSION_CHANGED
                } else {
                    MalAuthFailureReason.ACCOUNT_PERSISTENCE_FAILED
                }
                if (reason == MalAuthFailureReason.ACCOUNT_SESSION_CHANGED) consume(session)
                _state.value = MalAuthState.Error(reason, session.targetLocalAccountId)
                return MalCallbackResult.Failure(reason)
            }
        }
        consume(session)
        _state.value = MalAuthState.Connected(account)
        return MalCallbackResult.Success(account)
    }

    private fun matchesRedirect(callback: URI, redirectUri: String): Boolean {
        val expected = parseUri(redirectUri) ?: return false
        return callback.scheme == expected.scheme &&
            callback.host == expected.host &&
            callback.path == expected.path &&
            callback.port == -1 &&
            callback.userInfo == null &&
            callback.rawFragment == null
    }

    private fun consume(session: MalOAuthSession) {
        sessionStore.consume(
            sessionId = session.sessionId,
            stateHash = MalPkceGenerator.stateHash(session.state),
        )
    }

    private fun callbackFailure(reason: MalAuthFailureReason): MalCallbackResult.Failure {
        _state.value = MalAuthState.Error(reason)
        return MalCallbackResult.Failure(reason)
    }

    private fun parseUri(value: String): URI? = runCatching { URI(value.trim()) }
        .getOrNull()
        ?.takeIf { it.isAbsolute && !it.scheme.isNullOrBlank() }

    private fun parseQuery(rawQuery: String?): Map<String, String> = runCatching {
        rawQuery.orEmpty()
            .split('&')
            .asSequence()
            .filter(String::isNotBlank)
            .mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val key = URLDecoder.decode(
                    part.substring(0, separator),
                    StandardCharsets.UTF_8.name(),
                )
                val value = URLDecoder.decode(
                    part.substring(separator + 1),
                    StandardCharsets.UTF_8.name(),
                )
                key to value
            }
            .toMap()
    }.getOrDefault(emptyMap())

    private fun MalOAuthTransportResult.Failure.toAuthFailure(): MalAuthFailureReason = when (reason) {
        MalOAuthTransportFailureReason.INVALID_GRANT -> MalAuthFailureReason.INVALID_GRANT
        MalOAuthTransportFailureReason.INVALID_CLIENT -> MalAuthFailureReason.INVALID_CLIENT
        MalOAuthTransportFailureReason.RATE_LIMITED -> MalAuthFailureReason.RATE_LIMITED
        MalOAuthTransportFailureReason.SERVER_ERROR -> MalAuthFailureReason.SERVER_ERROR
        MalOAuthTransportFailureReason.TIMEOUT -> MalAuthFailureReason.TIMEOUT
        MalOAuthTransportFailureReason.TRANSPORT -> MalAuthFailureReason.TRANSPORT
        MalOAuthTransportFailureReason.CANCELLED -> MalAuthFailureReason.CANCELLED
        MalOAuthTransportFailureReason.MALFORMED_RESPONSE -> MalAuthFailureReason.MALFORMED_RESPONSE
        MalOAuthTransportFailureReason.PERMANENT_HTTP_ERROR -> MalAuthFailureReason.PERMANENT_HTTP_ERROR
    }

    private companion object {
        const val SESSION_LIFETIME_MS = 10 * 60 * 1000L
        val TERMINAL_EXCHANGE_FAILURES = setOf(
            MalOAuthTransportFailureReason.INVALID_GRANT,
            MalOAuthTransportFailureReason.INVALID_CLIENT,
            MalOAuthTransportFailureReason.MALFORMED_RESPONSE,
            MalOAuthTransportFailureReason.PERMANENT_HTTP_ERROR,
        )
    }
}
