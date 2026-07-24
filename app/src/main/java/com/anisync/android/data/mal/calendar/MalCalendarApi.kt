package com.anisync.android.data.mal.calendar

import com.anisync.android.data.mal.api.MalApiFailure
import com.anisync.android.data.mal.api.MalApiFailureKind
import com.anisync.android.data.mal.api.MalApiResult
import com.anisync.android.data.mal.api.MalSeason
import com.anisync.android.data.mal.oauth.AuthenticatedMalClient
import com.anisync.android.data.mal.oauth.MalAuthenticatedFailureReason
import com.anisync.android.data.mal.oauth.MalAuthenticatedResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

private const val MAL_CALENDAR_BASE_URL = "https://api.myanimelist.net/v2/"

internal class MalCalendarRequestFactory(
    private val baseUrl: HttpUrl = MAL_CALENDAR_BASE_URL.toHttpUrl(),
) {
    fun seasonal(
        year: Int,
        season: MalSeason,
        limit: Int,
        offset: Int,
    ): Request {
        require(year in 1917..2100)
        require(limit in 1..MAX_PAGE_SIZE)
        require(offset >= 0)
        val url = baseUrl.newBuilder()
            .addPathSegments("anime/season/$year/${season.wireValue}")
            .addQueryParameter("sort", "anime_num_list_users")
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("fields", CALENDAR_FIELDS)
            .build()
        return get(url)
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
        val relativePath = next.encodedPath.removePrefix(basePath)
        if (!SEASON_PATH.matches(relativePath)) return null
        val fields = next.queryParameter("fields") ?: return null
        if (fields != CALENDAR_FIELDS) return null
        return get(next)
    }

    private fun get(url: HttpUrl): Request = Request.Builder()
        .url(url)
        .header("Accept", "application/json")
        .get()
        .build()

    companion object {
        const val DEFAULT_PAGE_SIZE = 100
        private const val MAX_PAGE_SIZE = 100
        private val SEASON_PATH = Regex("anime/season/\\d{4}/(winter|spring|summer|fall)")
        const val CALENDAR_FIELDS =
            "id,title,main_picture,start_date,end_date,status,media_type,num_episodes,broadcast,my_list_status"
    }
}

data class MalCalendarPage(
    val entries: List<MalCalendarMedia>,
    val nextPageUrl: String?,
)

data class MalCalendarMedia(
    val malId: Long,
    val title: String,
    val pictureMedium: String?,
    val pictureLarge: String?,
    val startDate: String?,
    val endDate: String?,
    val status: String?,
    val mediaType: String?,
    val episodeCount: Int?,
    val broadcastDayOfWeek: String?,
    val broadcastStartTime: String?,
    val isOnList: Boolean,
) {
    override fun toString(): String =
        "MalCalendarMedia(malId=$malId, title=<redacted>, pictures=<redacted>, " +
            "startDate=${startDate ?: "none"}, endDate=${endDate ?: "none"}, " +
            "status=${status ?: "none"}, mediaType=${mediaType ?: "none"}, " +
            "episodeCount=${episodeCount ?: "none"}, broadcast=<redacted>, isOnList=$isOnList)"
}

@Singleton
class MalCalendarApi internal constructor(
    private val executeAuthenticated: suspend (
        localAccountId: String,
        requestFactory: () -> Request,
    ) -> MalAuthenticatedResult,
    private val requestFactory: MalCalendarRequestFactory,
) {
    @Inject
    constructor(client: AuthenticatedMalClient) : this(
        executeAuthenticated = client::execute,
        requestFactory = MalCalendarRequestFactory(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun seasonal(
        localAccountId: String,
        year: Int,
        season: MalSeason,
        limit: Int = MalCalendarRequestFactory.DEFAULT_PAGE_SIZE,
        offset: Int = 0,
    ): MalApiResult<MalCalendarPage> = execute(
        localAccountId = localAccountId,
        request = requestFactory.seasonal(year, season, limit, offset),
    )

    suspend fun nextPage(
        localAccountId: String,
        nextPageUrl: String,
    ): MalApiResult<MalCalendarPage> {
        val request = requestFactory.nextPage(nextPageUrl)
            ?: return MalApiResult.Failure(
                MalApiFailure(MalApiFailureKind.INVALID_PAGING_URL)
            )
        return execute(localAccountId, request)
    }

    private suspend fun execute(
        localAccountId: String,
        request: Request,
    ): MalApiResult<MalCalendarPage> = try {
        when (val result = executeAuthenticated(localAccountId) { request }) {
            is MalAuthenticatedResult.Failure -> MalApiResult.Failure(result.toApiFailure())
            is MalAuthenticatedResult.Success -> {
                val httpFailure = result.response.toHttpFailureOrNull()
                if (httpFailure != null) {
                    MalApiResult.Failure(httpFailure)
                } else {
                    parsePage(result.response.body, result.response.statusCode)
                }
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        MalApiResult.Failure(MalApiFailure(MalApiFailureKind.INVALID_RESPONSE))
    }

    private fun parsePage(body: String, statusCode: Int): MalApiResult<MalCalendarPage> {
        val wire = runCatching { json.decodeFromString<WirePage>(body) }.getOrNull()
            ?: return MalApiResult.Failure(
                MalApiFailure(MalApiFailureKind.INVALID_RESPONSE, statusCode)
            )
        val mapped = wire.data.map { edge ->
            val node = edge.node
            if (node.id <= 0L || node.title.isBlank()) {
                return MalApiResult.Failure(
                    MalApiFailure(MalApiFailureKind.INVALID_RESPONSE, statusCode)
                )
            }
            MalCalendarMedia(
                malId = node.id,
                title = node.title,
                pictureMedium = node.mainPicture?.medium,
                pictureLarge = node.mainPicture?.large,
                startDate = node.startDate,
                endDate = node.endDate,
                status = node.status,
                mediaType = node.mediaType,
                episodeCount = node.numEpisodes,
                broadcastDayOfWeek = node.broadcast?.dayOfTheWeek,
                broadcastStartTime = node.broadcast?.startTime,
                isOnList = node.myListStatus != null,
            )
        }
        val next = wire.paging?.next?.trim()?.takeIf(String::isNotEmpty)
        if (next != null && requestFactory.nextPage(next) == null) {
            return MalApiResult.Failure(
                MalApiFailure(MalApiFailureKind.INVALID_PAGING_URL, statusCode)
            )
        }
        return MalApiResult.Success(MalCalendarPage(mapped, next))
    }

    private fun MalAuthenticatedResult.Failure.toApiFailure(): MalApiFailure = when (reason) {
        MalAuthenticatedFailureReason.ACCOUNT_NOT_FOUND ->
            MalApiFailure(MalApiFailureKind.ACCOUNT_NOT_FOUND)
        MalAuthenticatedFailureReason.TOKEN_UNAVAILABLE ->
            MalApiFailure(MalApiFailureKind.NOT_AUTHENTICATED)
        MalAuthenticatedFailureReason.REFRESH_FAILED,
        MalAuthenticatedFailureReason.RELOGIN_REQUIRED ->
            MalApiFailure(MalApiFailureKind.RELOGIN_REQUIRED)
        MalAuthenticatedFailureReason.OFFLINE ->
            MalApiFailure(MalApiFailureKind.OFFLINE)
        MalAuthenticatedFailureReason.TIMEOUT ->
            MalApiFailure(MalApiFailureKind.TIMEOUT)
        MalAuthenticatedFailureReason.TRANSPORT ->
            MalApiFailure(MalApiFailureKind.TRANSPORT)
        MalAuthenticatedFailureReason.CANCELLED ->
            MalApiFailure(MalApiFailureKind.CANCELLED)
    }

    private fun com.anisync.android.data.mal.oauth.MalAuthenticatedResponse.toHttpFailureOrNull(): MalApiFailure? {
        if (statusCode in 200..299) return null
        return when (statusCode) {
            401 -> MalApiFailure(MalApiFailureKind.RELOGIN_REQUIRED, statusCode)
            404 -> MalApiFailure(MalApiFailureKind.PERMANENT, statusCode)
            429 -> MalApiFailure(
                kind = MalApiFailureKind.RATE_LIMITED,
                httpStatus = statusCode,
                retryAfterMillis = headers["Retry-After"]
                    ?.trim()
                    ?.toLongOrNull()
                    ?.coerceAtLeast(0L)
                    ?.times(1_000L),
            )
            in 500..599 -> MalApiFailure(MalApiFailureKind.TRANSIENT_SERVER, statusCode)
            else -> MalApiFailure(MalApiFailureKind.PERMANENT, statusCode)
        }
    }

    @Serializable
    private data class WirePage(
        val data: List<WireEdge>,
        val paging: WirePaging? = null,
    )

    @Serializable
    private data class WirePaging(val next: String? = null)

    @Serializable
    private data class WireEdge(val node: WireMedia)

    @Serializable
    private data class WireMedia(
        val id: Long,
        val title: String,
        @SerialName("main_picture") val mainPicture: WirePicture? = null,
        @SerialName("start_date") val startDate: String? = null,
        @SerialName("end_date") val endDate: String? = null,
        val status: String? = null,
        @SerialName("media_type") val mediaType: String? = null,
        @SerialName("num_episodes") val numEpisodes: Int? = null,
        val broadcast: WireBroadcast? = null,
        @SerialName("my_list_status") val myListStatus: WireListStatus? = null,
    )

    @Serializable
    private data class WirePicture(
        val medium: String? = null,
        val large: String? = null,
    )

    @Serializable
    private data class WireBroadcast(
        @SerialName("day_of_the_week") val dayOfTheWeek: String? = null,
        @SerialName("start_time") val startTime: String? = null,
    )

    @Serializable
    private data class WireListStatus(
        val status: String? = null,
    )
}
