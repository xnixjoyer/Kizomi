package com.anisync.android.di

import android.os.SystemClock
import android.util.Log
import com.anisync.android.data.AuthRepository
import com.anisync.android.data.network.TokenBucket
import com.anisync.android.data.util.ApiError
import com.anisync.android.presentation.components.alert.ToastManager
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.network.http.HttpInterceptor
import com.apollographql.apollo.network.http.HttpInterceptorChain
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Buffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HTTP interceptor for all AniList API requests.
 *
 * Responsibilities:
 * 1. **Authorization**: Attaches the Bearer token to every request
 * 2. **Rate limit tracking**: Reads `X-RateLimit-Remaining` from every response
 * 3. **Proactive throttling**: Adds a small delay when remaining requests are low
 * 4. **429 handling**: closes the first response, respects `Retry-After`, and retries once
 * 5. **401 handling**: notifies [AuthRepository] to trigger session-expired flow
 */
@Singleton
class AuthorizationInterceptor internal constructor(
    private val tokenProvider: () -> String?,
    private val sessionExpiredHandler: () -> Unit,
    private val toastManager: ToastManager,
    private val tokenBucket: TokenBucket
) : HttpInterceptor {

    @Inject
    constructor(
        authRepository: AuthRepository,
        toastManager: ToastManager,
        tokenBucket: TokenBucket
    ) : this(
        tokenProvider = authRepository::getToken,
        sessionExpiredHandler = authRepository::onSessionExpired,
        toastManager = toastManager,
        tokenBucket = tokenBucket
    )

    companion object {
        private const val TAG = "AuthInterceptor"
        private const val THROTTLE_THRESHOLD = 5
        private const val THROTTLE_DELAY_MS = 2000L
        private const val THROTTLE_NOTICE_THRESHOLD_MS = 800L
        private const val DEFAULT_RETRY_AFTER_SECONDS = 60L
        private const val MAX_RETRY_AFTER_SECONDS = 120L
        private val OPERATION_NAME_REGEX = Regex("\"operationName\"\\s*:\\s*\"([^\"]+)\"")
        private val ERROR_MESSAGE_REGEX = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"")
    }

    private val rateLimitRemaining = AtomicInteger(90)
    private val rateLimitResetAt = AtomicLong(0)

    /** Prevents several 429 responses from waking and retrying as one parallel wave. */
    private val rateLimitRetryMutex = Mutex()

    override suspend fun intercept(
        request: HttpRequest,
        chain: HttpInterceptorChain
    ): HttpResponse {
        val pacingStart = SystemClock.elapsedRealtime()
        tokenBucket.acquire()

        val hasOwnAuthHeader = request.headers.any { it.name.equals("Authorization", ignoreCase = true) }
        val authorizedRequest = if (hasOwnAuthHeader) {
            request
        } else {
            val token = tokenProvider()
            if (token != null) {
                request.newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                request
            }
        }

        val remaining = rateLimitRemaining.get()
        if (remaining in 1 until THROTTLE_THRESHOLD) {
            Log.w(TAG, "Rate limit low ($remaining remaining), throttling ${THROTTLE_DELAY_MS}ms")
            delay(THROTTLE_DELAY_MS)
        }

        if (SystemClock.elapsedRealtime() - pacingStart >= THROTTLE_NOTICE_THRESHOLD_MS) {
            toastManager.showThrottleNotice()
        }

        val response = chain.proceed(authorizedRequest)
        updateRateLimitState(response)

        return when (response.statusCode) {
            429 -> handleRateLimited(
                originalResponse = response,
                request = authorizedRequest,
                chain = chain,
                isTokenScoped = hasOwnAuthHeader
            )
            401 -> handleUnauthorized(response, authorizedRequest, isTokenScoped = hasOwnAuthHeader)
            in 500..599 -> handleServerError(response)
            else -> checkBodyForPermissionErrors(response, authorizedRequest)
        }
    }

    private val permissionGated401Operations = setOf(
        "DeleteActivity",
        "DeleteActivityReply",
        "DeleteThread",
        "DeleteThreadComment"
    )

    private fun updateRateLimitState(response: HttpResponse) {
        response.headers.forEach { header ->
            when (header.name.lowercase()) {
                "x-ratelimit-remaining" -> {
                    header.value.toIntOrNull()?.let { value ->
                        rateLimitRemaining.set(value)
                        if (value <= THROTTLE_THRESHOLD) {
                            Log.w(TAG, "Rate limit remaining: $value")
                        }
                    }
                }
                "x-ratelimit-limit" -> {
                    Log.d(TAG, "Rate limit: ${header.value}/min")
                    header.value.toIntOrNull()?.let(tokenBucket::onLimitHeader)
                }
                "x-ratelimit-reset" -> {
                    header.value.toLongOrNull()?.let(rateLimitResetAt::set)
                }
            }
        }
    }

    private suspend fun handleRateLimited(
        originalResponse: HttpResponse,
        request: HttpRequest,
        chain: HttpInterceptorChain,
        isTokenScoped: Boolean
    ): HttpResponse {
        val retryAfter = originalResponse.headers
            .firstOrNull { it.name.equals("Retry-After", ignoreCase = true) }
            ?.value?.toLongOrNull()
            ?: DEFAULT_RETRY_AFTER_SECONDS
        val cappedRetryAfter = retryAfter.coerceIn(0L, MAX_RETRY_AFTER_SECONDS)

        // Apollo's HttpResponse contract requires every non-returned body to be closed. Closing before
        // waiting releases the OkHttp connection immediately instead of leaking one connection per 429.
        originalResponse.body?.close()
        rateLimitRemaining.set(0)

        return rateLimitRetryMutex.withLock {
            Log.w(TAG, "Rate limited (429). Waiting ${cappedRetryAfter}s before retrying...")
            toastManager.showToast(
                code = 429,
                message = "AniList is rate-limiting requests. Retrying in ${cappedRetryAfter}s.",
                countdownSeconds = cappedRetryAfter
            )
            delay(cappedRetryAfter * 1000)

            val retryResponse = chain.proceed(request)
            updateRateLimitState(retryResponse)
            when (retryResponse.statusCode) {
                429 -> {
                    val secondRetryAfter = retryResponse.headers
                        .firstOrNull { it.name.equals("Retry-After", ignoreCase = true) }
                        ?.value?.toLongOrNull()
                        ?: DEFAULT_RETRY_AFTER_SECONDS
                    retryResponse.body?.close()
                    Log.e(TAG, "Still rate limited after retry. Giving up.")
                    toastManager.showToast(
                        code = 429,
                        message = "Still rate-limited after retry. Try again in ${secondRetryAfter}s.",
                        countdownSeconds = secondRetryAfter
                    )
                    throw ApiError.RateLimited(secondRetryAfter)
                }
                401 -> handleUnauthorized(retryResponse, request, isTokenScoped)
                in 500..599 -> handleServerError(retryResponse)
                else -> {
                    Log.i(TAG, "Retry successful after rate limit wait.")
                    checkBodyForPermissionErrors(retryResponse, request)
                }
            }
        }
    }

    private fun handleUnauthorized(
        response: HttpResponse,
        request: HttpRequest,
        isTokenScoped: Boolean
    ): HttpResponse {
        val operationName = extractOperationName(request)
        response.body?.close()
        if (operationName != null && operationName in permissionGated401Operations) {
            Log.w(TAG, "401 on $operationName — treating as permission denial, not session expiry.")
            throw ApiError.Forbidden()
        }
        if (isTokenScoped) {
            Log.w(TAG, "Unauthorized (401) on token-scoped request; leaving active session intact.")
            throw ApiError.Unauthorized()
        }
        Log.w(TAG, "Unauthorized (401). Token expired or revoked.")
        sessionExpiredHandler()
        throw ApiError.Unauthorized()
    }

    private fun extractOperationName(request: HttpRequest): String? {
        request.headers
            .firstOrNull { it.name.equals("X-APOLLO-OPERATION-NAME", ignoreCase = true) }
            ?.value
            ?.let { return it }

        return try {
            val body = request.body ?: return null
            val buffer = Buffer()
            body.writeTo(buffer)
            OPERATION_NAME_REGEX.find(buffer.readUtf8())?.groupValues?.get(1)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract operation name from request body", e)
            null
        }
    }

    private fun handleServerError(response: HttpResponse): HttpResponse {
        val statusCode = response.statusCode
        response.body?.close()
        Log.e(TAG, "Server error: $statusCode")
        throw ApiError.ServerError(statusCode)
    }

    private fun checkBodyForPermissionErrors(
        response: HttpResponse,
        request: HttpRequest
    ): HttpResponse {
        val bodySource = response.body ?: return response
        val body = bodySource.peek().readUtf8()
        if ("\"status\":401" !in body && "\"status\": 401" !in body) return response

        val operationName = extractOperationName(request) ?: return response
        if (operationName !in permissionGated401Operations) return response

        val message = ERROR_MESSAGE_REGEX.find(body)?.groupValues?.get(1)
            ?: "You don't have permission to do that."
        response.body?.close()
        Log.w(TAG, "HTTP 200 with 401 permission error in $operationName: $message")
        throw ApiError.Forbidden(message)
    }
}
