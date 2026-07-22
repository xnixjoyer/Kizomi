package com.anisync.android.data.mal.oauth

import com.anisync.android.di.MalOAuthHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val MAL_AUTHORIZE_URL = "https://myanimelist.net/v1/oauth2/authorize"
private const val MAL_TOKEN_URL = "https://myanimelist.net/v1/oauth2/token"

class MalOAuthRequestFactory internal constructor(
    private val authorizeEndpoint: HttpUrl = MAL_AUTHORIZE_URL.toHttpUrl(),
    private val tokenEndpoint: HttpUrl = MAL_TOKEN_URL.toHttpUrl(),
) {
    fun authorizationUrl(
        configuration: MalOAuthConfiguration,
        session: MalOAuthSession,
    ): HttpUrl = authorizeEndpoint.newBuilder()
        .addQueryParameter("response_type", "code")
        .addQueryParameter("client_id", configuration.clientId)
        .addQueryParameter("code_challenge", session.challenge)
        .addQueryParameter(
            "code_challenge_method",
            when (session.pkceMethod) {
                MalPkceMethod.PLAIN -> "plain"
                MalPkceMethod.S256 -> "S256"
            },
        )
        .addQueryParameter("state", session.state)
        .addQueryParameter("redirect_uri", configuration.redirectUri.toString())
        .build()

    fun authorizationCodeRequest(
        configuration: MalOAuthConfiguration,
        code: String,
        verifier: String,
    ): Request = Request.Builder()
        .url(tokenEndpoint)
        .post(
            FormBody.Builder()
                .add("client_id", configuration.clientId)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("code_verifier", verifier)
                .add("redirect_uri", configuration.redirectUri.toString())
                .build()
        )
        .build()

    fun refreshRequest(
        configuration: MalOAuthConfiguration,
        refreshToken: String,
    ): Request = Request.Builder()
        .url(tokenEndpoint)
        .post(
            FormBody.Builder()
                .add("client_id", configuration.clientId)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build()
        )
        .build()
}

data class MalOAuthTokenPayload(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochMillis: Long,
    val scopes: Set<String>,
    val tokenType: String,
) {
    override fun toString(): String =
        "MalOAuthTokenPayload(accessToken=<redacted>, refreshToken=${if (refreshToken == null) "absent" else "<redacted>"}, expiresAtEpochMillis=$expiresAtEpochMillis, scopeCount=${scopes.size}, tokenType=$tokenType)"
}

enum class MalOAuthTransportFailureReason {
    INVALID_GRANT,
    INVALID_CLIENT,
    RATE_LIMITED,
    SERVER_ERROR,
    TIMEOUT,
    TRANSPORT,
    CANCELLED,
    MALFORMED_RESPONSE,
    PERMANENT_HTTP_ERROR,
}

sealed interface MalOAuthTransportResult<out T> {
    data class Success<T>(val value: T) : MalOAuthTransportResult<T> {
        override fun toString(): String = "MalOAuthTransportResult.Success(value=<redacted>)"
    }

    data class Failure(
        val reason: MalOAuthTransportFailureReason,
        val httpStatus: Int? = null,
        val retryAfterSeconds: Long? = null,
    ) : MalOAuthTransportResult<Nothing> {
        override fun toString(): String =
            "MalOAuthTransportResult.Failure(reason=${reason.name}, httpStatus=${httpStatus ?: "none"}, retryAfterSeconds=${retryAfterSeconds ?: "none"})"
    }
}

interface MalOAuthTokenService {
    suspend fun exchangeAuthorizationCode(
        configuration: MalOAuthConfiguration,
        code: String,
        verifier: String,
    ): MalOAuthTransportResult<MalOAuthTokenPayload>

    suspend fun refresh(
        configuration: MalOAuthConfiguration,
        refreshToken: String,
    ): MalOAuthTransportResult<MalOAuthTokenPayload>
}

@Singleton
class OkHttpMalOAuthTokenService internal constructor(
    private val client: OkHttpClient,
    private val requestFactory: MalOAuthRequestFactory,
    private val clock: MalOAuthClock,
) : MalOAuthTokenService {
    @Inject
    constructor(
        @MalOAuthHttpClient client: OkHttpClient,
        clock: MalOAuthClock,
    ) : this(
        client = client,
        requestFactory = MalOAuthRequestFactory(),
        clock = clock,
    )

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun exchangeAuthorizationCode(
        configuration: MalOAuthConfiguration,
        code: String,
        verifier: String,
    ): MalOAuthTransportResult<MalOAuthTokenPayload> = execute(
        requestFactory.authorizationCodeRequest(
            configuration = configuration,
            code = code,
            verifier = verifier,
        )
    )

    override suspend fun refresh(
        configuration: MalOAuthConfiguration,
        refreshToken: String,
    ): MalOAuthTransportResult<MalOAuthTokenPayload> = execute(
        requestFactory.refreshRequest(
            configuration = configuration,
            refreshToken = refreshToken,
        )
    )

    private suspend fun execute(request: Request): MalOAuthTransportResult<MalOAuthTokenPayload> {
        val call = client.newCall(request)
        return try {
            call.awaitMalResponse().use(::parseResponse)
        } catch (cancellation: CancellationException) {
            call.cancel()
            throw cancellation
        } catch (_: SocketTimeoutException) {
            MalOAuthTransportResult.Failure(MalOAuthTransportFailureReason.TIMEOUT)
        } catch (_: IOException) {
            MalOAuthTransportResult.Failure(
                if (call.isCanceled()) {
                    MalOAuthTransportFailureReason.CANCELLED
                } else {
                    MalOAuthTransportFailureReason.TRANSPORT
                }
            )
        }
    }

    private fun parseResponse(response: Response): MalOAuthTransportResult<MalOAuthTokenPayload> {
        val status = response.code
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val providerError = runCatching {
                json.decodeFromString<OAuthErrorResponse>(body).error?.trim()?.lowercase()
            }.getOrNull()
            val reason = when {
                providerError == "invalid_grant" -> MalOAuthTransportFailureReason.INVALID_GRANT
                providerError == "invalid_client" -> MalOAuthTransportFailureReason.INVALID_CLIENT
                status == 429 -> MalOAuthTransportFailureReason.RATE_LIMITED
                status in 500..599 -> MalOAuthTransportFailureReason.SERVER_ERROR
                else -> MalOAuthTransportFailureReason.PERMANENT_HTTP_ERROR
            }
            return MalOAuthTransportResult.Failure(
                reason = reason,
                httpStatus = status,
                retryAfterSeconds = response.header("Retry-After")?.toLongOrNull(),
            )
        }

        val parsed = runCatching { json.decodeFromString<TokenResponse>(body) }.getOrNull()
            ?: return MalOAuthTransportResult.Failure(
                reason = MalOAuthTransportFailureReason.MALFORMED_RESPONSE,
                httpStatus = status,
            )
        val accessToken = parsed.accessToken?.trim().orEmpty()
        val expiresIn = parsed.expiresIn
        val tokenType = parsed.tokenType?.trim().orEmpty()
        if (accessToken.isEmpty() || expiresIn == null || expiresIn <= 0L || tokenType.isEmpty()) {
            return MalOAuthTransportResult.Failure(
                reason = MalOAuthTransportFailureReason.MALFORMED_RESPONSE,
                httpStatus = status,
            )
        }
        val now = clock.nowEpochMillis()
        val expiresAt = if (expiresIn > (Long.MAX_VALUE - now) / 1000L) {
            Long.MAX_VALUE
        } else {
            now + expiresIn * 1000L
        }
        return MalOAuthTransportResult.Success(
            MalOAuthTokenPayload(
                accessToken = accessToken,
                refreshToken = parsed.refreshToken?.trim()?.takeIf(String::isNotEmpty),
                expiresAtEpochMillis = expiresAt,
                scopes = parsed.scope.orEmpty()
                    .split(' ')
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .toSortedSet(),
                tokenType = tokenType,
            )
        )
    }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null,
        @SerialName("token_type") val tokenType: String? = null,
        val scope: String? = null,
    )

    @Serializable
    private data class OAuthErrorResponse(
        val error: String? = null,
    )
}

internal suspend fun Call.awaitMalResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) {
                    continuation.resume(response)
                } else {
                    response.close()
                }
            }
        }
    )
}
