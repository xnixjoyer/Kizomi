package com.anisync.android.data.mal.api

import com.anisync.android.data.mal.oauth.AuthenticatedMalClient
import com.anisync.android.data.mal.oauth.MalAuthenticatedResult
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
import javax.inject.Inject
import javax.inject.Singleton

private const val MAL_CATALOG_BASE_URL = "https://api.myanimelist.net/v2/"

internal class MalCatalogRequestFactory(
    private val baseUrl: HttpUrl = MAL_CATALOG_BASE_URL.toHttpUrl(),
) {
    fun search(
        mediaType: TrackingMediaType,
        query: String,
        limit: Int,
        offset: Int,
    ): Request {
        val normalizedQuery = query.trim().also { require(it.isNotEmpty()) }
        require(limit in 1..MAX_PAGE_SIZE)
        require(offset >= 0)
        val url = baseUrl.newBuilder()
            .addPathSegment(mediaType.pathSegment())
            .addQueryParameter("q", normalizedQuery)
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("fields", CATALOG_FIELDS)
            .build()
        return get(url)
    }

    fun details(mediaType: TrackingMediaType, malId: Long): Request {
        require(malId > 0L)
        val url = baseUrl.newBuilder()
            .addPathSegment(mediaType.pathSegment())
            .addPathSegment(malId.toString())
            .addQueryParameter("fields", DETAIL_FIELDS)
            .build()
        return get(url)
    }

    fun ranking(
        mediaType: TrackingMediaType,
        rankingType: String,
        limit: Int,
        offset: Int,
    ): Request {
        val normalizedRanking = rankingType.trim().also {
            require(RANKING_TYPE.matches(it))
        }
        require(limit in 1..MAX_PAGE_SIZE)
        require(offset >= 0)
        val url = baseUrl.newBuilder()
            .addPathSegment(mediaType.pathSegment())
            .addPathSegment("ranking")
            .addQueryParameter("ranking_type", normalizedRanking)
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("fields", CATALOG_FIELDS)
            .build()
        return get(url)
    }

    fun seasonal(
        year: Int,
        season: MalSeason,
        sort: String,
        limit: Int,
        offset: Int,
    ): Request {
        require(year in 1917..2100)
        val normalizedSort = sort.trim().also { require(it in SEASON_SORTS) }
        require(limit in 1..MAX_PAGE_SIZE)
        require(offset >= 0)
        val url = baseUrl.newBuilder()
            .addPathSegments("anime/season/$year/${season.wireValue}")
            .addQueryParameter("sort", normalizedSort)
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("fields", CATALOG_FIELDS)
            .build()
        return get(url)
    }

    fun nextPage(mediaType: TrackingMediaType, rawUrl: String): Request? {
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
        val typePath = mediaType.pathSegment()
        val validPath = relativePath == typePath ||
            relativePath == "$typePath/ranking" ||
            (mediaType == TrackingMediaType.ANIME && SEASON_PATH.matches(relativePath))
        if (!validPath) return null
        return get(next)
    }

    private fun get(url: HttpUrl): Request = Request.Builder()
        .url(url)
        .header("Accept", "application/json")
        .get()
        .build()

    private fun TrackingMediaType.pathSegment(): String = when (this) {
        TrackingMediaType.ANIME -> "anime"
        TrackingMediaType.MANGA -> "manga"
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 50
        private const val MAX_PAGE_SIZE = 100
        private val RANKING_TYPE = Regex("[a-z_]{1,32}")
        private val SEASON_PATH = Regex("anime/season/\\d{4}/(winter|spring|summer|fall)")
        private val SEASON_SORTS = setOf("anime_score", "anime_num_list_users")
        private const val CATALOG_FIELDS =
            "id,title,main_picture,alternative_titles,start_date,end_date,synopsis,mean,rank," +
                "popularity,status,media_type,num_episodes,num_chapters,num_volumes,genres," +
                "my_list_status"
        private const val DETAIL_FIELDS =
            "$CATALOG_FIELDS,pictures,background,related_anime,related_manga,recommendations"
    }
}

@Singleton
class MalCatalogApi internal constructor(
    private val executeAuthenticated: suspend (
        localAccountId: String,
        requestFactory: () -> Request,
    ) -> MalAuthenticatedResult,
    private val requestFactory: MalCatalogRequestFactory,
    private val nowEpochMillis: () -> Long,
) {
    @Inject
    constructor(client: AuthenticatedMalClient) : this(
        executeAuthenticated = client::execute,
        requestFactory = MalCatalogRequestFactory(),
        nowEpochMillis = System::currentTimeMillis,
    )

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(
        localAccountId: String,
        mediaType: TrackingMediaType,
        query: String,
        limit: Int = MalCatalogRequestFactory.DEFAULT_PAGE_SIZE,
        offset: Int = 0,
    ): MalApiResult<MalCatalogPage> = executePage(
        localAccountId,
        mediaType,
        requestFactory.search(mediaType, query, limit, offset),
    )

    suspend fun ranking(
        localAccountId: String,
        mediaType: TrackingMediaType,
        rankingType: String = "all",
        limit: Int = MalCatalogRequestFactory.DEFAULT_PAGE_SIZE,
        offset: Int = 0,
    ): MalApiResult<MalCatalogPage> = executePage(
        localAccountId,
        mediaType,
        requestFactory.ranking(mediaType, rankingType, limit, offset),
    )

    suspend fun seasonal(
        localAccountId: String,
        year: Int,
        season: MalSeason,
        sort: String = "anime_num_list_users",
        limit: Int = MalCatalogRequestFactory.DEFAULT_PAGE_SIZE,
        offset: Int = 0,
    ): MalApiResult<MalCatalogPage> = executePage(
        localAccountId,
        TrackingMediaType.ANIME,
        requestFactory.seasonal(year, season, sort, limit, offset),
    )

    suspend fun nextPage(
        localAccountId: String,
        mediaType: TrackingMediaType,
        nextPageUrl: String,
    ): MalApiResult<MalCatalogPage> {
        val request = requestFactory.nextPage(mediaType, nextPageUrl)
            ?: return MalApiResult.Failure(MalApiFailure(MalApiFailureKind.INVALID_PAGING_URL))
        return executePage(localAccountId, mediaType, request)
    }

    suspend fun details(
        localAccountId: String,
        key: MalMediaKey,
    ): MalApiResult<MalCatalogMedia> = try {
        when (val result = executeAuthenticated(localAccountId) {
            requestFactory.details(key.mediaType, key.malId)
        }) {
            is MalAuthenticatedResult.Failure -> MalApiResult.Failure(result.toMalApiFailure())
            is MalAuthenticatedResult.Success -> {
                val failure = result.response.toHttpFailureOrNull()
                if (failure != null) {
                    MalApiResult.Failure(failure)
                } else {
                    val wire = runCatching {
                        json.decodeFromString<WireMedia>(result.response.body)
                    }.getOrNull()
                    val mapped = wire?.let {
                        runCatching {
                            it.toModel(key.mediaType, null, nowEpochMillis(), json, isDetailed = true)
                        }.getOrNull()
                    }
                    if (mapped == null || mapped.key != key) {
                        MalApiResult.Failure(
                            MalApiFailure(MalApiFailureKind.INVALID_RESPONSE, result.response.statusCode)
                        )
                    } else {
                        MalApiResult.Success(mapped)
                    }
                }
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        MalApiResult.Failure(MalApiFailure(MalApiFailureKind.INVALID_RESPONSE))
    }

    private suspend fun executePage(
        localAccountId: String,
        mediaType: TrackingMediaType,
        request: Request,
    ): MalApiResult<MalCatalogPage> = try {
        when (val result = executeAuthenticated(localAccountId) { request }) {
            is MalAuthenticatedResult.Failure -> MalApiResult.Failure(result.toMalApiFailure())
            is MalAuthenticatedResult.Success -> {
                val failure = result.response.toHttpFailureOrNull()
                if (failure != null) {
                    MalApiResult.Failure(failure)
                } else {
                    parsePage(mediaType, result.response.body, result.response.statusCode)
                }
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        MalApiResult.Failure(MalApiFailure(MalApiFailureKind.INVALID_RESPONSE))
    }

    private fun parsePage(
        mediaType: TrackingMediaType,
        body: String,
        statusCode: Int,
    ): MalApiResult<MalCatalogPage> {
        val wire = runCatching { json.decodeFromString<WirePage>(body) }.getOrNull()
            ?: return MalApiResult.Failure(
                MalApiFailure(MalApiFailureKind.INVALID_RESPONSE, statusCode)
            )
        val fetchedAt = nowEpochMillis()
        val entries = wire.data.map { edge ->
            runCatching {
                edge.node.toModel(
                    mediaType,
                    edge.ranking?.rank,
                    fetchedAt,
                    json,
                    isDetailed = false,
                )
            }.getOrNull() ?: return MalApiResult.Failure(
                MalApiFailure(MalApiFailureKind.INVALID_RESPONSE, statusCode)
            )
        }
        val next = wire.paging?.next?.trim()?.takeIf(String::isNotEmpty)
        if (next != null && requestFactory.nextPage(mediaType, next) == null) {
            return MalApiResult.Failure(
                MalApiFailure(MalApiFailureKind.INVALID_PAGING_URL, statusCode)
            )
        }
        return MalApiResult.Success(MalCatalogPage(entries, next))
    }

    @Serializable
    private data class WirePage(
        val data: List<WireEdge>,
        val paging: WirePaging? = null,
    )

    @Serializable
    private data class WirePaging(val next: String? = null)

    @Serializable
    private data class WireEdge(
        val node: WireMedia,
        val ranking: WireRanking? = null,
    )

    @Serializable
    private data class WireRanking(val rank: Int? = null)

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
        @SerialName("media_type") val mediaFormat: String? = null,
        @SerialName("num_episodes") val numEpisodes: Int? = null,
        @SerialName("num_chapters") val numChapters: Int? = null,
        @SerialName("num_volumes") val numVolumes: Int? = null,
        val genres: List<WireGenre> = emptyList(),
        val pictures: List<WirePicture> = emptyList(),
        val background: String? = null,
        @SerialName("my_list_status") val myListStatus: WireListStatus? = null,
        @SerialName("related_anime") val relatedAnime: List<WireRelated> = emptyList(),
        @SerialName("related_manga") val relatedManga: List<WireRelated> = emptyList(),
        val recommendations: List<WireRecommendation> = emptyList(),
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
    private data class WireGenre(val name: String)

    @Serializable
    private data class WireRelated(
        val node: WireRelatedNode,
        @SerialName("relation_type_formatted") val relationship: String? = null,
    )

    @Serializable
    private data class WireRecommendation(
        val node: WireRelatedNode,
        @SerialName("num_recommendations") val recommendationCount: Int? = null,
    )

    @Serializable
    private data class WireRelatedNode(
        val id: Long,
        val title: String,
        @SerialName("main_picture") val mainPicture: WirePicture? = null,
    )

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
    )

    private fun WireMedia.toModel(
        mediaType: TrackingMediaType,
        rankingPosition: Int?,
        fetchedAtEpochMillis: Long,
        json: Json,
        isDetailed: Boolean,
    ): MalCatalogMedia {
        require(id > 0L)
        val normalizedTitle = title.trim().also { require(it.isNotEmpty()) }
        return MalCatalogMedia(
            key = MalMediaKey(mediaType, id),
            title = normalizedTitle,
            alternativeTitles = buildList {
                addAll(alternativeTitles?.synonyms.orEmpty())
                alternativeTitles?.en?.let(::add)
                alternativeTitles?.ja?.let(::add)
            }.map(String::trim).filter(String::isNotEmpty).distinct(),
            synopsis = synopsis,
            pictureMedium = mainPicture?.medium,
            pictureLarge = mainPicture?.large,
            pictureGallery = buildList {
                (mainPicture?.large ?: mainPicture?.medium)?.let(::add)
                pictures.forEach { picture ->
                    (picture.large ?: picture.medium)?.let(::add)
                }
            }.map(String::trim).filter(String::isNotEmpty).distinct(),
            meanScore = mean,
            rank = rank,
            popularity = popularity,
            mediaStatus = status,
            mediaFormat = mediaFormat,
            startDate = startDate,
            endDate = endDate,
            episodeCount = numEpisodes,
            chapterCount = numChapters,
            volumeCount = numVolumes,
            genres = genres.map { it.name.trim() }.filter(String::isNotEmpty).distinct(),
            background = background,
            listState = myListStatus?.toDesiredState(mediaType),
            related = relatedAnime.map { it.toModel(TrackingMediaType.ANIME) } +
                relatedManga.map { it.toModel(TrackingMediaType.MANGA) },
            recommendations = recommendations.map {
                it.node.toRelatedModel(mediaType, "${it.recommendationCount ?: 0} recommendations")
            },
            rankingPosition = rankingPosition,
            isDetailed = isDetailed,
            fetchedAtEpochMillis = fetchedAtEpochMillis,
            rawJson = json.encodeToString(this),
        )
    }

    private fun WireRelated.toModel(mediaType: TrackingMediaType): MalRelatedMedia =
        node.toRelatedModel(mediaType, relationship)

    private fun WireRelatedNode.toRelatedModel(
        mediaType: TrackingMediaType,
        relationship: String?,
    ): MalRelatedMedia {
        require(id > 0L)
        return MalRelatedMedia(
            key = MalMediaKey(mediaType, id),
            title = title.trim().also { require(it.isNotEmpty()) },
            pictureUrl = mainPicture?.large ?: mainPicture?.medium,
            relationship = relationship,
        )
    }

    private fun WireListStatus.toDesiredState(mediaType: TrackingMediaType): TrackingDesiredState {
        val repeating = when (mediaType) {
            TrackingMediaType.ANIME -> isRewatching == true
            TrackingMediaType.MANGA -> isRereading == true
        }
        val normalizedStatus = if (repeating) {
            TrackingStatus.REPEATING
        } else {
            status.toCatalogTrackingStatus(mediaType)
        }
        return TrackingDesiredState(
            status = normalizedStatus,
            progress = when (mediaType) {
                TrackingMediaType.ANIME -> numEpisodesWatched ?: 0
                TrackingMediaType.MANGA -> numChaptersRead ?: 0
            },
            progressSecondary = if (mediaType == TrackingMediaType.MANGA) numVolumesRead else null,
            score100 = score?.also { require(it in 0..10) }?.takeIf { it > 0 }?.times(10.0),
            repeatCount = when (mediaType) {
                TrackingMediaType.ANIME -> numTimesRewatched ?: 0
                TrackingMediaType.MANGA -> numTimesReread ?: 0
            },
            notes = comments,
            startedAt = startDate,
            completedAt = finishDate,
        )
    }

    private fun String.toCatalogTrackingStatus(mediaType: TrackingMediaType): TrackingStatus =
        when (this) {
            "watching" -> TrackingStatus.CURRENT.also {
                require(mediaType == TrackingMediaType.ANIME)
            }
            "reading" -> TrackingStatus.CURRENT.also {
                require(mediaType == TrackingMediaType.MANGA)
            }
            "plan_to_watch" -> TrackingStatus.PLANNING.also {
                require(mediaType == TrackingMediaType.ANIME)
            }
            "plan_to_read" -> TrackingStatus.PLANNING.also {
                require(mediaType == TrackingMediaType.MANGA)
            }
            "completed" -> TrackingStatus.COMPLETED
            "dropped" -> TrackingStatus.DROPPED
            "on_hold" -> TrackingStatus.PAUSED
            else -> error("Unsupported MAL list status")
        }
}
