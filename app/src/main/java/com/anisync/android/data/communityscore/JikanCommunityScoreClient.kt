package com.anisync.android.data.communityscore

import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private const val JIKAN_BASE_URL = "https://api.jikan.moe/v4/"

data class JikanAnimeSnapshot(
    val malId: Int,
    val score: Double?,
    val scoredBy: Int?,
    val rank: Int?,
    val title: String?,
    val titleEnglish: String?,
    val titleJapanese: String?,
    val year: Int?,
    val format: String?,
    val episodes: Int?
)

sealed interface JikanFetchResult {
    data class Success(
        val snapshot: JikanAnimeSnapshot,
        val etag: String?,
        val lastModified: String?
    ) : JikanFetchResult

    data class NotModified(val etag: String?, val lastModified: String?) : JikanFetchResult
    data object NotFound : JikanFetchResult
    data class Failure(val error: JikanFailure) : JikanFetchResult
}

enum class JikanFailureType {
    OFFLINE, TRANSPORT, TIMEOUT, RATE_LIMITED, TEMPORARY_SERVER, INVALID_RESPONSE, PERMANENT
}

data class JikanFailure(
    val type: JikanFailureType,
    val httpStatus: Int? = null,
    val retryAfterMillis: Long? = null
)

/** Dedicated, serialized read-only client. It never carries AniList auth or MyAnimeList credentials. */
class JikanCommunityScoreClient(
    private val client: OkHttpClient,
    private val userAgent: String,
    private val baseUrl: HttpUrl = JIKAN_BASE_URL.toHttpUrl(),
    private val minimumRequestIntervalMillis: Long = MIN_REQUEST_INTERVAL_MS,
    private val maximumRequestsPerWindow: Int = MAX_REQUESTS_PER_WINDOW,
    private val requestWindowMillis: Long = REQUEST_WINDOW_MS
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val requestGate = Mutex()
    private var lastRequestStartedAtElapsedMillis = 0L
    private val requestStartsElapsedMillis = ArrayDeque<Long>()

    suspend fun fetchAnime(
        malId: Int,
        etag: String? = null,
        lastModified: String? = null
    ): JikanFetchResult = serializedRequest {
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegments("anime/$malId").build())
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
            .apply {
                etag?.takeIf(String::isNotBlank)?.let { header("If-None-Match", it) }
                lastModified?.takeIf(String::isNotBlank)?.let { header("If-Modified-Since", it) }
            }
            .build()

        try {
            execute(request) { code, body, responseEtag, responseLastModified, retryAfter ->
                when (code) {
                    200 -> runCatching {
                        json.decodeFromString<JikanAnimeResponse>(body).data.toSnapshot()
                    }.fold(
                        onSuccess = { JikanFetchResult.Success(it, responseEtag, responseLastModified) },
                        onFailure = {
                            JikanFetchResult.Failure(JikanFailure(JikanFailureType.INVALID_RESPONSE, 200))
                        }
                    )
                    304 -> JikanFetchResult.NotModified(
                        responseEtag ?: etag,
                        responseLastModified ?: lastModified
                    )
                    404 -> JikanFetchResult.NotFound
                    429 -> JikanFetchResult.Failure(
                        JikanFailure(JikanFailureType.RATE_LIMITED, code, parseRetryAfterMillis(retryAfter))
                    )
                    500, 502, 503, 504 -> JikanFetchResult.Failure(
                        JikanFailure(JikanFailureType.TEMPORARY_SERVER, code)
                    )
                    else -> JikanFetchResult.Failure(JikanFailure(JikanFailureType.PERMANENT, code))
                }
            }
        } catch (error: IOException) {
            JikanFetchResult.Failure(classifyJikanTransportFailure(error))
        }
    }

    suspend fun searchAnime(query: String, limit: Int = 5): JikanSearchResult = serializedRequest {
        val url = baseUrl.newBuilder()
            .addPathSegment("anime")
            .addQueryParameter("q", query.trim())
            .addQueryParameter("limit", limit.coerceIn(1, 10).toString())
            .addQueryParameter("sfw", "true")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
            .build()

        try {
            execute(request) { code, body, _, _, retryAfter ->
                when (code) {
                    200 -> runCatching {
                        json.decodeFromString<JikanAnimeSearchResponse>(body).data.map(JikanAnimeDto::toSnapshot)
                    }.fold(
                        onSuccess = JikanSearchResult::Success,
                        onFailure = {
                            JikanSearchResult.Failure(JikanFailure(JikanFailureType.INVALID_RESPONSE, 200))
                        }
                    )
                    429 -> JikanSearchResult.Failure(
                        JikanFailure(JikanFailureType.RATE_LIMITED, code, parseRetryAfterMillis(retryAfter))
                    )
                    500, 502, 503, 504 -> JikanSearchResult.Failure(
                        JikanFailure(JikanFailureType.TEMPORARY_SERVER, code)
                    )
                    else -> JikanSearchResult.Failure(JikanFailure(JikanFailureType.PERMANENT, code))
                }
            }
        } catch (error: IOException) {
            JikanSearchResult.Failure(classifyJikanTransportFailure(error))
        }
    }

    private suspend fun <T> serializedRequest(block: suspend () -> T): T = requestGate.withLock {
        var elapsed = elapsedRealtimeMillis()
        val waitMillis = (minimumRequestIntervalMillis - (elapsed - lastRequestStartedAtElapsedMillis))
            .coerceAtLeast(0L)
        if (waitMillis > 0) delay(waitMillis)
        elapsed = elapsedRealtimeMillis()
        pruneRequestWindow(elapsed)
        if (requestStartsElapsedMillis.size >= maximumRequestsPerWindow.coerceAtLeast(1)) {
            val oldest = requestStartsElapsedMillis.first()
            val windowWait = (requestWindowMillis - (elapsed - oldest)).coerceAtLeast(1L)
            delay(windowWait)
            elapsed = elapsedRealtimeMillis()
            pruneRequestWindow(elapsed)
        }
        lastRequestStartedAtElapsedMillis = elapsed
        requestStartsElapsedMillis.addLast(elapsed)
        block()
    }

    private fun pruneRequestWindow(now: Long) {
        while (
            requestStartsElapsedMillis.isNotEmpty() &&
            now - requestStartsElapsedMillis.first() >= requestWindowMillis
        ) {
            requestStartsElapsedMillis.removeFirst()
        }
    }

    private suspend fun <T> execute(
        request: Request,
        mapper: (
            code: Int,
            body: String,
            etag: String?,
            lastModified: String?,
            retryAfter: String?
        ) -> T
    ): T = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWith(kotlin.Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val mapped = runCatching {
                        mapper(
                            response.code,
                            response.body?.string().orEmpty(),
                            response.header("ETag"),
                            response.header("Last-Modified"),
                            response.header("Retry-After")
                        )
                    }
                    if (continuation.isActive) continuation.resumeWith(mapped)
                }
            }
        })
    }

    private fun elapsedRealtimeMillis(): Long = System.nanoTime() / 1_000_000L

    private fun parseRetryAfterMillis(value: String?): Long? {
        return value?.trim()?.toLongOrNull()?.coerceAtLeast(0L)?.times(1_000L)
    }

    private companion object {
        // Jikan documents 3 requests/second. 350 ms leaves a small scheduling margin.
        const val MIN_REQUEST_INTERVAL_MS = 350L
        const val MAX_REQUESTS_PER_WINDOW = 60
        const val REQUEST_WINDOW_MS = 60_000L
    }
}

/** Kept independent from exception messages so diagnostics and UI never expose socket details. */
internal fun classifyJikanTransportFailure(error: IOException): JikanFailure {
    val type = when (error) {
        is UnknownHostException -> JikanFailureType.OFFLINE
        is SocketTimeoutException, is InterruptedIOException -> JikanFailureType.TIMEOUT
        is SocketException -> JikanFailureType.TRANSPORT
        else -> JikanFailureType.TRANSPORT
    }
    return JikanFailure(type)
}

sealed interface JikanSearchResult {
    data class Success(val candidates: List<JikanAnimeSnapshot>) : JikanSearchResult
    data class Failure(val error: JikanFailure) : JikanSearchResult
}

@Serializable
private data class JikanAnimeResponse(val data: JikanAnimeDto)

@Serializable
private data class JikanAnimeSearchResponse(val data: List<JikanAnimeDto> = emptyList())

@Serializable
private data class JikanAnimeDto(
    @SerialName("mal_id") val malId: Int,
    val score: Double? = null,
    @SerialName("scored_by") val scoredBy: Int? = null,
    val rank: Int? = null,
    val title: String? = null,
    @SerialName("title_english") val titleEnglish: String? = null,
    @SerialName("title_japanese") val titleJapanese: String? = null,
    val year: Int? = null,
    val type: String? = null,
    val episodes: Int? = null
) {
    fun toSnapshot(): JikanAnimeSnapshot = JikanAnimeSnapshot(
        malId = malId,
        score = score?.takeIf { it > 0.0 },
        scoredBy = scoredBy?.takeIf { it > 0 },
        rank = rank?.takeIf { it > 0 },
        title = title,
        titleEnglish = titleEnglish,
        titleJapanese = titleJapanese,
        year = year,
        format = type,
        episodes = episodes
    )
}
