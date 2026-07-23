package com.anisync.android.data.mal.oauth

import com.anisync.android.data.mal.account.MalAccountCredentialStore
import com.anisync.android.data.mal.account.MalAccountResult
import com.anisync.android.data.mal.account.MalTokenSet
import com.anisync.android.di.MalOAuthHttpClient
import kotlinx.coroutines.CancellationException
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

data class MalAuthenticatedResponse(
    val statusCode: Int,
    val headers: Headers,
    val body: String,
) {
    override fun toString(): String =
        "MalAuthenticatedResponse(statusCode=$statusCode, headerCount=${headers.size}, body=<redacted>)"
}

enum class MalAuthenticatedFailureReason {
    ACCOUNT_NOT_FOUND,
    TOKEN_UNAVAILABLE,
    REFRESH_FAILED,
    RELOGIN_REQUIRED,
    OFFLINE,
    TIMEOUT,
    TRANSPORT,
    CANCELLED,
}

sealed interface MalAuthenticatedResult {
    data class Success(val response: MalAuthenticatedResponse) : MalAuthenticatedResult {
        override fun toString(): String =
            "MalAuthenticatedResult.Success(response=$response)"
    }

    data class Failure(
        val reason: MalAuthenticatedFailureReason,
        val localAccountId: String,
        val refreshFailure: MalRefreshFailureReason? = null,
    ) : MalAuthenticatedResult {
        override fun toString(): String =
            "MalAuthenticatedResult.Failure(reason=${reason.name}, localAccountId=<redacted>, " +
                "refreshFailure=${refreshFailure?.name ?: "none"})"
    }
}

@Singleton
class AuthenticatedMalClient @Inject constructor(
    @MalOAuthHttpClient private val client: OkHttpClient,
    private val accountStore: MalAccountCredentialStore,
    private val refreshCoordinator: MalRefreshCoordinator,
    private val clock: MalOAuthClock,
) {
    suspend fun execute(
        localAccountId: String,
        requestFactory: () -> Request,
    ): MalAuthenticatedResult {
        val account = when (val result = accountStore.getAccount(localAccountId)) {
            is MalAccountResult.Success -> result.value
            is MalAccountResult.Failure -> return failure(
                localAccountId,
                MalAuthenticatedFailureReason.ACCOUNT_NOT_FOUND,
            )
        }
        var tokens = when (val read = accountStore.readTokens(localAccountId)) {
            is MalAccountResult.Success -> read.value
            is MalAccountResult.Failure -> return failure(
                localAccountId,
                MalAuthenticatedFailureReason.TOKEN_UNAVAILABLE,
            )
        }
        var generation = account.tokenGeneration

        if (shouldRefresh(tokens)) {
            when (val refreshed = refreshCoordinator.refresh(localAccountId, generation)) {
                is MalRefreshResult.Success -> {
                    tokens = refreshed.tokens
                    generation = refreshed.tokenGeneration
                }
                is MalRefreshResult.Failure -> return refreshed.toAuthenticatedFailure()
            }
        }

        val first = executeOnce(requestFactory(), tokens.accessToken)
        if (first !is ExecuteResult.Response || first.statusCode != 401) {
            return first.toPublic(localAccountId)
        }

        when (val refreshed = refreshCoordinator.refresh(localAccountId, generation)) {
            is MalRefreshResult.Failure -> return refreshed.toAuthenticatedFailure()
            is MalRefreshResult.Success -> {
                val retry = executeOnce(
                    request = requestFactory(),
                    accessToken = refreshed.tokens.accessToken,
                )
                if (retry is ExecuteResult.Response && retry.statusCode == 401) {
                    accountStore.logout(localAccountId)
                    return failure(
                        localAccountId,
                        MalAuthenticatedFailureReason.RELOGIN_REQUIRED,
                    )
                }
                return retry.toPublic(localAccountId)
            }
        }
    }

    private fun shouldRefresh(tokens: MalTokenSet): Boolean {
        val expiresAt = tokens.expiresAtEpochMillis ?: return false
        return expiresAt <= clock.nowEpochMillis() + PREEMPTIVE_REFRESH_WINDOW_MS
    }

    private suspend fun executeOnce(
        request: Request,
        accessToken: String,
    ): ExecuteResult {
        val authorized = request.newBuilder()
            .removeHeader("Authorization")
            .header("Authorization", "Bearer $accessToken")
            .build()
        val call = client.newCall(authorized)
        return try {
            call.awaitMalResponse().use { response ->
                ExecuteResult.Response(
                    statusCode = response.code,
                    headers = response.headers,
                    body = response.body?.string().orEmpty(),
                )
            }
        } catch (cancellation: CancellationException) {
            call.cancel()
            throw cancellation
        } catch (_: SocketTimeoutException) {
            ExecuteResult.Failure(MalAuthenticatedFailureReason.TIMEOUT)
        } catch (_: UnknownHostException) {
            ExecuteResult.Failure(MalAuthenticatedFailureReason.OFFLINE)
        } catch (_: IOException) {
            ExecuteResult.Failure(
                if (call.isCanceled()) {
                    MalAuthenticatedFailureReason.CANCELLED
                } else {
                    MalAuthenticatedFailureReason.TRANSPORT
                }
            )
        }
    }

    private fun ExecuteResult.toPublic(localAccountId: String): MalAuthenticatedResult = when (this) {
        is ExecuteResult.Response -> MalAuthenticatedResult.Success(
            MalAuthenticatedResponse(statusCode, headers, body)
        )
        is ExecuteResult.Failure -> failure(localAccountId, reason)
    }

    private fun MalRefreshResult.Failure.toAuthenticatedFailure(): MalAuthenticatedResult.Failure =
        failure(
            localAccountId = localAccountId,
            reason = if (reason == MalRefreshFailureReason.RELOGIN_REQUIRED ||
                reason == MalRefreshFailureReason.REFRESH_TOKEN_MISSING ||
                reason == MalRefreshFailureReason.TOKEN_MISSING
            ) {
                MalAuthenticatedFailureReason.RELOGIN_REQUIRED
            } else {
                MalAuthenticatedFailureReason.REFRESH_FAILED
            },
            refreshFailure = reason,
        )

    private fun failure(
        localAccountId: String,
        reason: MalAuthenticatedFailureReason,
        refreshFailure: MalRefreshFailureReason? = null,
    ): MalAuthenticatedResult.Failure = MalAuthenticatedResult.Failure(
        reason = reason,
        localAccountId = localAccountId,
        refreshFailure = refreshFailure,
    )

    private sealed interface ExecuteResult {
        data class Response(
            val statusCode: Int,
            val headers: Headers,
            val body: String,
        ) : ExecuteResult

        data class Failure(
            val reason: MalAuthenticatedFailureReason,
        ) : ExecuteResult
    }

    companion object {
        private const val PREEMPTIVE_REFRESH_WINDOW_MS = 60_000L
    }
}
