package com.anisync.android.data.mal.oauth

import com.anisync.android.data.mal.account.MalAccount
import com.anisync.android.data.mal.account.MalAccountCredentialStore
import com.anisync.android.data.mal.account.MalAccountFailureReason
import com.anisync.android.data.mal.account.MalAccountResult
import com.anisync.android.data.mal.account.MalTokenSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class MalRefreshFailureReason {
    CONFIGURATION_UNAVAILABLE,
    ACCOUNT_NOT_FOUND,
    TOKEN_MISSING,
    REFRESH_TOKEN_MISSING,
    RELOGIN_REQUIRED,
    ACCOUNT_SESSION_CHANGED,
    PERSISTENCE_FAILED,
    RATE_LIMITED,
    SERVER_ERROR,
    TIMEOUT,
    TRANSPORT,
    CANCELLED,
    MALFORMED_RESPONSE,
    PERMANENT_HTTP_ERROR,
    INVALID_CLIENT,
}

sealed interface MalRefreshResult {
    data class Success(
        val localAccountId: String,
        val tokenGeneration: Long,
        val tokens: MalTokenSet,
    ) : MalRefreshResult {
        override fun toString(): String =
            "MalRefreshResult.Success(localAccountId=$localAccountId, tokenGeneration=$tokenGeneration, tokens=<redacted>)"
    }

    data class Failure(
        val reason: MalRefreshFailureReason,
        val localAccountId: String,
        val httpStatus: Int? = null,
        val retryAfterSeconds: Long? = null,
    ) : MalRefreshResult {
        override fun toString(): String =
            "MalRefreshResult.Failure(reason=${reason.name}, localAccountId=$localAccountId, httpStatus=${httpStatus ?: "none"}, retryAfterSeconds=${retryAfterSeconds ?: "none"})"
    }
}

@Singleton
class MalRefreshCoordinator internal constructor(
    private val configurationProvider: MalOAuthConfigurationSource,
    private val accountStore: MalAccountCredentialStore,
    private val tokenService: MalOAuthTokenService,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(
        configurationProvider: MalOAuthConfigurationSource,
        accountStore: MalAccountCredentialStore,
        tokenService: MalOAuthTokenService,
    ) : this(
        configurationProvider = configurationProvider,
        accountStore = accountStore,
        tokenService = tokenService,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    private val flightsLock = Mutex()
    private val flights = ConcurrentHashMap<String, Deferred<MalRefreshResult>>()

    suspend fun refresh(
        localAccountId: String,
        observedGeneration: Long? = null,
    ): MalRefreshResult {
        val flight = flightsLock.withLock {
            flights[localAccountId] ?: scope.async {
                performRefresh(localAccountId, observedGeneration)
            }.also { flights[localAccountId] = it }
        }
        return try {
            flight.await()
        } finally {
            if (flight.isCompleted) {
                flightsLock.withLock {
                    flights.remove(localAccountId, flight)
                }
            }
        }
    }

    private suspend fun performRefresh(
        localAccountId: String,
        observedGeneration: Long?,
    ): MalRefreshResult {
        val configuration = (configurationProvider.capability as? MalOAuthCapability.Configured)
            ?.configuration
            ?: return failure(localAccountId, MalRefreshFailureReason.CONFIGURATION_UNAVAILABLE)

        val account = accountStore.account(localAccountId)
            ?: return failure(localAccountId, MalRefreshFailureReason.ACCOUNT_NOT_FOUND)
        if (observedGeneration != null && account.tokenGeneration != observedGeneration) {
            return currentTokens(account)
        }

        val tokens = when (val read = accountStore.readTokens(localAccountId)) {
            is MalAccountResult.Success -> read.value
            is MalAccountResult.Failure -> return failure(
                localAccountId,
                when (read.reason) {
                    MalAccountFailureReason.ACCOUNT_NOT_FOUND -> MalRefreshFailureReason.ACCOUNT_NOT_FOUND
                    MalAccountFailureReason.ACCOUNT_SESSION_CHANGED -> MalRefreshFailureReason.ACCOUNT_SESSION_CHANGED
                    else -> MalRefreshFailureReason.TOKEN_MISSING
                }
            )
        }
        val refreshToken = tokens.refreshToken
            ?: return failure(localAccountId, MalRefreshFailureReason.REFRESH_TOKEN_MISSING)

        return when (val refresh = tokenService.refresh(configuration, refreshToken)) {
            is MalOAuthTransportResult.Success -> {
                val refreshed = MalTokenSet(
                    accessToken = refresh.value.accessToken,
                    refreshToken = refresh.value.refreshToken ?: refreshToken,
                    expiresAtEpochMillis = refresh.value.expiresAtEpochMillis,
                    scopes = refresh.value.scopes,
                )
                when (
                    val persisted = accountStore.replaceTokensIfGeneration(
                        localAccountId = localAccountId,
                        expectedGeneration = account.tokenGeneration,
                        tokens = refreshed,
                        requireExistingCredentials = true,
                    )
                ) {
                    is MalAccountResult.Success -> MalRefreshResult.Success(
                        localAccountId = localAccountId,
                        tokenGeneration = persisted.value.tokenGeneration,
                        tokens = refreshed,
                    )
                    is MalAccountResult.Failure -> failure(
                        localAccountId,
                        if (persisted.reason == MalAccountFailureReason.ACCOUNT_SESSION_CHANGED ||
                            persisted.reason == MalAccountFailureReason.ACCOUNT_NOT_FOUND
                        ) {
                            MalRefreshFailureReason.ACCOUNT_SESSION_CHANGED
                        } else {
                            MalRefreshFailureReason.PERSISTENCE_FAILED
                        },
                    )
                }
            }
            is MalOAuthTransportResult.Failure -> {
                if (refresh.reason == MalOAuthTransportFailureReason.INVALID_GRANT) {
                    accountStore.logout(localAccountId)
                    failure(
                        localAccountId,
                        MalRefreshFailureReason.RELOGIN_REQUIRED,
                        refresh.httpStatus,
                        refresh.retryAfterSeconds,
                    )
                } else {
                    failure(
                        localAccountId,
                        refresh.reason.toRefreshFailure(),
                        refresh.httpStatus,
                        refresh.retryAfterSeconds,
                    )
                }
            }
        }
    }

    private suspend fun currentTokens(account: MalAccount): MalRefreshResult =
        when (val read = accountStore.readTokens(account.localAccountId)) {
            is MalAccountResult.Success -> MalRefreshResult.Success(
                localAccountId = account.localAccountId,
                tokenGeneration = account.tokenGeneration,
                tokens = read.value,
            )
            is MalAccountResult.Failure -> failure(
                account.localAccountId,
                MalRefreshFailureReason.ACCOUNT_SESSION_CHANGED,
            )
        }

    private fun failure(
        localAccountId: String,
        reason: MalRefreshFailureReason,
        httpStatus: Int? = null,
        retryAfterSeconds: Long? = null,
    ): MalRefreshResult.Failure = MalRefreshResult.Failure(
        reason = reason,
        localAccountId = localAccountId,
        httpStatus = httpStatus,
        retryAfterSeconds = retryAfterSeconds,
    )

    private suspend fun MalAccountCredentialStore.account(localAccountId: String): MalAccount? =
        when (val result = getAccount(localAccountId)) {
            is MalAccountResult.Success -> result.value
            is MalAccountResult.Failure -> null
        }

    private fun MalOAuthTransportFailureReason.toRefreshFailure(): MalRefreshFailureReason = when (this) {
        MalOAuthTransportFailureReason.INVALID_GRANT -> MalRefreshFailureReason.RELOGIN_REQUIRED
        MalOAuthTransportFailureReason.INVALID_CLIENT -> MalRefreshFailureReason.INVALID_CLIENT
        MalOAuthTransportFailureReason.RATE_LIMITED -> MalRefreshFailureReason.RATE_LIMITED
        MalOAuthTransportFailureReason.SERVER_ERROR -> MalRefreshFailureReason.SERVER_ERROR
        MalOAuthTransportFailureReason.TIMEOUT -> MalRefreshFailureReason.TIMEOUT
        MalOAuthTransportFailureReason.TRANSPORT -> MalRefreshFailureReason.TRANSPORT
        MalOAuthTransportFailureReason.CANCELLED -> MalRefreshFailureReason.CANCELLED
        MalOAuthTransportFailureReason.MALFORMED_RESPONSE -> MalRefreshFailureReason.MALFORMED_RESPONSE
        MalOAuthTransportFailureReason.PERMANENT_HTTP_ERROR -> MalRefreshFailureReason.PERMANENT_HTTP_ERROR
    }
}
