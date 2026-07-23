package com.anisync.android.data.mal.api

import com.anisync.android.data.mal.oauth.AuthenticatedMalClient
import com.anisync.android.data.mal.oauth.MalAuthenticatedFailureReason
import com.anisync.android.data.mal.oauth.MalAuthenticatedResult
import com.anisync.android.data.mal.oauth.MalRefreshFailureReason
import com.anisync.android.domain.tracking.TrackingDesiredState
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingStatus
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val MAL_API_BASE_URL = "https://api.myanimelist.net/v2/"

internal class MalListRequestFactory(
    private val baseUrl: HttpUrl = MAL_API_BASE_URL.toHttpUrl(),
) {
    fun firstPage(
        mediaType: TrackingMediaType,
        status: String? = null,
        limit: Int = DEFAULT_PAGE_SIZE,
    ): Request {
        require(limit in 1..MAX_PAGE_SIZE) { "MAL page limit must be in 1..$MAX_PAGE_SIZE" }
        val category = if (mediaType == TrackingMediaType.ANIME) "animelist" else "mangalist"
        val url = baseUrl.newBuilder()
            .addPathSegments("users/@me/$category")
            .addQueryParameter("fields", LIST_FIELDS)
            .addQueryParameter("limit", limit.toString())
            .apply { status?.trim()?.takeIf(String::isNotEmpty)?.let { addQueryParameter("status", it) } }
            .build()
        return Request.Builder().url(url).header("Accept", "application/json").build()
    }

    fun nextPage(rawUrl: String): Request? {
        val next = rawUrl.toHttpUrlOrNull() ?: return null
        val basePath = baseUrl.encodedPath.trimEnd('/') + "/"
        if (next.scheme != baseUrl.scheme ||
            next.host != baseUrl.host ||
            next.port != baseUrl.port ||
            next.username.isNotEmpty() ||
            next.password.isNotEmpty() ||
            next.fragment != null ||
            !next.encodedPath.startsWith(basePath)
        ) {
            return null
        }
        return Request.Builder().url(next).header("Accept", "application/json").build()
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 100
        private const val MAX_PAGE_SIZE = 1_000
        private const val LIST_FIELDS =
            "id,title,main_picture,alternative_titles,start_date,end_date,synopsis," +
                "mean,rank,popularity,status,num_episodes,num_chapters,num_volumes,genres," +
                "list_status"
    }
}

@Singleton
class MalListApi internal constructor(
    private val executeAuthenticated: suspend (
        localAccountId: String,
        requestFactory: () -> Request,
    ) -> MalAuthenticatedResult,
    private val requestFactory: MalListRequestFactory,
) {
    @Inject
    constructor(client: AuthenticatedMalClient) : this(
        executeAuthenticated = client::execute,
        requestFactory = MalListRequestFactory(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun firstPage(
        localAccountId: String,
        mediaType: TrackingMediaType,
        status: String? = null,
        limit: Int = MalListRequestFactory.DEFAULT_PAGE_SIZE,
    ): MalApiResult<MalListPage> = execute(
        localAccountId = localAccountId,
        mediaType = mediaType,
        request = requestFactory.firstPage(mediaType, status, limit),
    )

    suspend fun nextPage(
        localAccountId: String,
        mediaType: TrackingMediaType,
        nextPageUrl: String,
    ): MalApiResult<MalListPage> {
        val request = requestFactory.nextPage(nextPageUrl)
            ?: return MalApiResult.Failure(MalApiFailure(MalApiFailureKind.INVALID_PAGING_URL))
        return execute(localAccountId, mediaType, request)
    }

    private suspend fun execute(
        localAccountId: String,
        mediaType: TrackingMediaType,
        request: Request,
    ): MalApiResult<MalListPage> = try {
        when (val result = executeAuthenticated(localAccountId) { request }) {
            is MalAuthenticatedResult.Failure -> MalApiResult.Failure(result.toMalApiFailure())
            is MalAuthenticatedResult.Success -> parseResponse(
                mediaType = mediaType,
                statusCode = result.response.statusCode,
                retryAfter = result.response.headers["Retry-After"],
                body = result.response.body,
            )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        MalApiResult.Failure(MalApiFailure(MalApiFailureKind.INVALID_RESPONSE))
    }

    private fun parseResponse(
        mediaType: TrackingMediaType,
        statusCode: Int,
        retryAfter: String?,
        body: String,
    ): MalApiResult<MalListPage> {
        if (statusCode !in 200..299) {
            return MalApiResult.Failure(
                MalApiFailure(
                    kind = when {
                        statusCode == 401 -> MalApiFailureKind.RELOGIN_REQUIRED
                        statusCode == 429 -> MalApiFailureKind.RATE_LIMITED
                        statusCode in 500..599 -> MalApiFailureKind.TRANSIENT_SERVER
                        else -> MalApiFailureKind.PERMANENT
                    },
                    httpStatus = statusCode,
                    retryAfterMillis = retryAfter?.trim()?.toLongOrNull()
                        ?.coerceAtLeast(0L)?.times(1_000L),
                )
            )
        }
        val wire = runCatching { json.decodeFromString<WireListPage>(body) }.getOrNull()
            ?: return MalApiResult.Failure(
                MalApiFailure(MalApiFailureKind.INVALID_RESPONSE, statusCode)
            )
        val entries = mutableListOf<MalListEntry>()
        for (edge in wire.data) {
            val mapped = runCatching { edge.toModel(mediaType, json) }.getOrNull()
                ?: return MalApiResult.Failure(
                    MalApiFailure(MalApiFailureKind.INVALID_RESPONSE, statusCode)
                )
            entries += mapped
        }
        val nextPageUrl = wire.paging?.next?.trim()?.takeIf(String::isNotEmpty)
        if (nextPageUrl != null && requestFactory.nextPage(nextPageUrl) == null) {
            return MalApiResult.Failure(
                MalApiFailure(MalApiFailureKind.INVALID_PAGING_URL, statusCode)
            )
        }
        return MalApiResult.Success(
            MalListPage(
                entries = entries,
                nextPageUrl = nextPageUrl,
            )
        )
    }

    @Serializable
    private data class WireListPage(
        val data: List<WireListEdge>,
        val paging: WirePaging? = null,
    )

    @Serializable
    private data class WirePaging(val next: String? = null)

    @Serializable
    private data class WireListEdge(
        val node: WireMedia,
        @SerialName("list_status") val listStatus: WireListStatus,
    )

    @Serializable
    private data class WireMedia(
        val id: Long,
        val title: String,
        @SerialName("main_picture") val mainPicture: WirePicture? = null,
        @SerialName("alternative_titles") val alternativeTitles: WireAlternativeTitles? = null,
        @SerialName("start_date") val startDate: String? = null,
        @SerialName("end_date") val endDate: String? = null,
        val synopsis: String? = null,
        val mean: Double? = null,
        val rank: Int? = null,
        val popularity: Int? = null,
        val status: String? = null,
        @SerialName("num_episodes") val numEpisodes: Int? = null,
        @SerialName("num_chapters") val numChapters: Int? = null,
        @SerialName("num_volumes") val numVolumes: Int? = null,
        val genres: List<WireGenre> = emptyList(),
    )

    @Serializable
    private data class WirePicture(val medium: String? = null, val large: String? = null)

    @Serializable
    private data class WireAlternativeTitles(
        val synonyms: List<String> = emptyList(),
        val en: String? = null,
        val ja: String? = null,
    )

    @Serializable
    private data class WireGenre(val id: Long? = null, val name: String)

    @Serializable
    private data class WireListStatus(
        val status: String,
        val score: Int? = null,
        @SerialName("num_episodes_watched") val numEpisodesWatched: Int? = null,
        @SerialName("num_chapters_read") val numChaptersRead: Int? = null,
        @SerialName("num_volumes_read") val numVolumesRead: Int? = null,
        @SerialName("is_rewatching") val isRewatching: Boolean? = null,
        @SerialName("is_rereading") val isRereading: Boolean? = null,
        @SerialName("num_times_rewatched") val numTimesRewatched: Int? = null,
        @SerialName("num_times_reread") val numTimesReread: Int? = null,
        val comments: String? = null,
        @SerialName("start_date") val startDate: String? = null,
        @SerialName("finish_date") val finishDate: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null,
    )

    private fun WireListEdge.toModel(
        mediaType: TrackingMediaType,
        json: Json,
    ): MalListEntry {
        require(node.id > 0L)
        val normalizedTitle = node.title.trim().also { require(it.isNotEmpty()) }
        val repeating = when (mediaType) {
            TrackingMediaType.ANIME -> listStatus.isRewatching == true
            TrackingMediaType.MANGA -> listStatus.isRereading == true
        }
        val status = if (repeating) {
            TrackingStatus.REPEATING
        } else {
            listStatus.status.toTrackingStatus(mediaType)
        }
        val progress = when (mediaType) {
            TrackingMediaType.ANIME -> listStatus.numEpisodesWatched ?: 0
            TrackingMediaType.MANGA -> listStatus.numChaptersRead ?: 0
        }
        val repeatCount = when (mediaType) {
            TrackingMediaType.ANIME -> listStatus.numTimesRewatched ?: 0
            TrackingMediaType.MANGA -> listStatus.numTimesReread ?: 0
        }
        val score100 = listStatus.score?.also { require(it in 0..10) }
            ?.takeIf { it > 0 }
            ?.times(10.0)
        return MalListEntry(
            malId = node.id,
            mediaType = mediaType,
            title = normalizedTitle,
            alternativeTitles = buildList {
                addAll(node.alternativeTitles?.synonyms.orEmpty())
                node.alternativeTitles?.en?.let(::add)
                node.alternativeTitles?.ja?.let(::add)
            }.map(String::trim).filter(String::isNotEmpty).distinct(),
            synopsis = node.synopsis,
            pictureMedium = node.mainPicture?.medium,
            pictureLarge = node.mainPicture?.large,
            meanScore = node.mean,
            rank = node.rank,
            popularity = node.popularity,
            mediaStatus = node.status,
            startDate = node.startDate,
            endDate = node.endDate,
            episodeCount = node.numEpisodes,
            chapterCount = node.numChapters,
            volumeCount = node.numVolumes,
            genres = node.genres.map { it.name.trim() }.filter(String::isNotEmpty).distinct(),
            desiredState = TrackingDesiredState(
                status = status,
                progress = progress,
                progressSecondary = if (mediaType == TrackingMediaType.MANGA) {
                    listStatus.numVolumesRead
                } else {
                    null
                },
                score100 = score100,
                repeatCount = repeatCount,
                notes = listStatus.comments,
                startedAt = listStatus.startDate,
                completedAt = listStatus.finishDate,
            ),
            providerUpdatedAtEpochMillis = listStatus.updatedAt?.let { value ->
                runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
            },
            rawMediaJson = json.encodeToString(node),
            rawListStatusJson = json.encodeToString(listStatus),
        )
    }

    private fun String.toTrackingStatus(mediaType: TrackingMediaType): TrackingStatus = when (this) {
        "watching" -> {
            require(mediaType == TrackingMediaType.ANIME)
            TrackingStatus.CURRENT
        }
        "reading" -> {
            require(mediaType == TrackingMediaType.MANGA)
            TrackingStatus.CURRENT
        }
        "plan_to_watch" -> {
            require(mediaType == TrackingMediaType.ANIME)
            TrackingStatus.PLANNING
        }
        "plan_to_read" -> {
            require(mediaType == TrackingMediaType.MANGA)
            TrackingStatus.PLANNING
        }
        "completed" -> TrackingStatus.COMPLETED
        "dropped" -> TrackingStatus.DROPPED
        "on_hold" -> TrackingStatus.PAUSED
        else -> error("Unsupported MAL list status")
    }
}

internal fun MalAuthenticatedResult.Failure.toMalApiFailure(): MalApiFailure = MalApiFailure(
    kind = refreshFailure?.toApiFailureKind() ?: when (reason) {
        MalAuthenticatedFailureReason.ACCOUNT_NOT_FOUND -> MalApiFailureKind.ACCOUNT_NOT_FOUND
        MalAuthenticatedFailureReason.TOKEN_UNAVAILABLE -> MalApiFailureKind.NOT_AUTHENTICATED
        MalAuthenticatedFailureReason.REFRESH_FAILED -> MalApiFailureKind.TRANSPORT
        MalAuthenticatedFailureReason.RELOGIN_REQUIRED -> MalApiFailureKind.RELOGIN_REQUIRED
        MalAuthenticatedFailureReason.OFFLINE -> MalApiFailureKind.OFFLINE
        MalAuthenticatedFailureReason.TIMEOUT -> MalApiFailureKind.TIMEOUT
        MalAuthenticatedFailureReason.TRANSPORT -> MalApiFailureKind.TRANSPORT
        MalAuthenticatedFailureReason.CANCELLED -> MalApiFailureKind.CANCELLED
    },
)

internal fun com.anisync.android.data.mal.oauth.MalAuthenticatedResponse.toHttpFailureOrNull(): MalApiFailure? {
    if (statusCode in 200..299) return null
    return MalApiFailure(
        kind = when {
            statusCode == 401 -> MalApiFailureKind.RELOGIN_REQUIRED
            statusCode == 429 -> MalApiFailureKind.RATE_LIMITED
            statusCode in 500..599 -> MalApiFailureKind.TRANSIENT_SERVER
            else -> MalApiFailureKind.PERMANENT
        },
        httpStatus = statusCode,
        retryAfterMillis = headers["Retry-After"]?.trim()?.toLongOrNull()
            ?.coerceAtLeast(0L)?.times(1_000L),
    )
}

private fun MalRefreshFailureReason.toApiFailureKind(): MalApiFailureKind = when (this) {
    MalRefreshFailureReason.ACCOUNT_NOT_FOUND -> MalApiFailureKind.ACCOUNT_NOT_FOUND
    MalRefreshFailureReason.TOKEN_MISSING,
    MalRefreshFailureReason.REFRESH_TOKEN_MISSING -> MalApiFailureKind.NOT_AUTHENTICATED
    MalRefreshFailureReason.RELOGIN_REQUIRED,
    MalRefreshFailureReason.INVALID_CLIENT,
    MalRefreshFailureReason.CONFIGURATION_UNAVAILABLE -> MalApiFailureKind.RELOGIN_REQUIRED
    MalRefreshFailureReason.RATE_LIMITED -> MalApiFailureKind.RATE_LIMITED
    MalRefreshFailureReason.SERVER_ERROR -> MalApiFailureKind.TRANSIENT_SERVER
    MalRefreshFailureReason.TIMEOUT -> MalApiFailureKind.TIMEOUT
    MalRefreshFailureReason.TRANSPORT -> MalApiFailureKind.TRANSPORT
    MalRefreshFailureReason.CANCELLED -> MalApiFailureKind.CANCELLED
    MalRefreshFailureReason.MALFORMED_RESPONSE -> MalApiFailureKind.INVALID_RESPONSE
    MalRefreshFailureReason.ACCOUNT_SESSION_CHANGED,
    MalRefreshFailureReason.PERSISTENCE_FAILED,
    MalRefreshFailureReason.PERMANENT_HTTP_ERROR -> MalApiFailureKind.PERMANENT
}
