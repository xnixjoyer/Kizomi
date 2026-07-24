package com.anisync.android.data.mal.api

import com.anisync.android.data.local.dao.TrackingDao
import com.anisync.android.data.local.entity.MalMediaCacheEntity
import com.anisync.android.domain.tracking.TrackingMediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MalCatalogRepository @Inject constructor(
    private val dao: TrackingDao,
    private val api: MalCatalogApi,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun observeDetails(key: MalMediaKey): Flow<MalCatalogMedia?> =
        dao.observeMalMedia(key.malId, key.mediaType.name)
            .map { it?.toModel() }
            .catch { emit(null) }

    suspend fun cachedDetails(key: MalMediaKey): MalCatalogMedia? = try {
        dao.getMalMedia(key.malId, key.mediaType.name)?.toModel()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }

    suspend fun cachedSearch(
        mediaType: TrackingMediaType,
        query: String,
        limit: Int = 50,
    ): List<MalCatalogMedia> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return emptyList()
        return try {
            dao.searchCachedMalMedia(mediaType.name, normalized, limit.coerceIn(1, 100))
                .mapNotNull { it.toModel() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun search(
        localAccountId: String,
        mediaType: TrackingMediaType,
        query: String,
        limit: Int = MalCatalogRequestFactory.DEFAULT_PAGE_SIZE,
    ): MalApiResult<MalCatalogPage> = cachePage(
        api.search(localAccountId, mediaType, query, limit),
    )

    suspend fun ranking(
        localAccountId: String,
        mediaType: TrackingMediaType,
        rankingType: String = "all",
        limit: Int = MalCatalogRequestFactory.DEFAULT_PAGE_SIZE,
    ): MalApiResult<MalCatalogPage> = cachePage(
        api.ranking(localAccountId, mediaType, rankingType, limit),
    )

    suspend fun seasonal(
        localAccountId: String,
        year: Int,
        season: MalSeason,
        limit: Int = MalCatalogRequestFactory.DEFAULT_PAGE_SIZE,
    ): MalApiResult<MalCatalogPage> = cachePage(
        api.seasonal(localAccountId, year, season, limit = limit),
    )

    suspend fun nextPage(
        localAccountId: String,
        mediaType: TrackingMediaType,
        nextPageUrl: String,
    ): MalApiResult<MalCatalogPage> = cachePage(
        api.nextPage(localAccountId, mediaType, nextPageUrl),
    )

    suspend fun refreshDetails(
        localAccountId: String,
        key: MalMediaKey,
    ): MalApiResult<MalCatalogMedia> = try {
        when (val result = api.details(localAccountId, key)) {
            is MalApiResult.Failure -> result
            is MalApiResult.Success -> {
                dao.upsertMalMedia(listOf(result.value.toEntity()))
                result
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        MalApiResult.Failure(MalApiFailure(MalApiFailureKind.STORAGE))
    }

    private suspend fun cachePage(
        result: MalApiResult<MalCatalogPage>,
    ): MalApiResult<MalCatalogPage> = try {
        when (result) {
            is MalApiResult.Failure -> result
            is MalApiResult.Success -> {
                if (result.value.entries.isNotEmpty()) {
                    val entities = result.value.entries.map { incoming ->
                        val existing = dao.getMalMedia(
                            incoming.key.malId,
                            incoming.key.mediaType.name,
                        )
                        // A search/ranking row must never erase a richer details cache entry.
                        existing?.takeIf { it.toModel()?.isDetailed == true }
                            ?: incoming.toEntity()
                    }
                    dao.upsertMalMedia(entities)
                }
                result
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        MalApiResult.Failure(MalApiFailure(MalApiFailureKind.STORAGE))
    }

    private fun MalCatalogMedia.toEntity(): MalMediaCacheEntity = MalMediaCacheEntity(
        malId = key.malId,
        mediaType = key.mediaType.name,
        title = title,
        alternativeTitlesJson = json.encodeToString(alternativeTitles),
        synopsis = synopsis,
        mainPictureMedium = pictureMedium,
        mainPictureLarge = pictureLarge,
        pictureGalleryJson = json.encodeToString(pictureGallery),
        meanScore = meanScore,
        rank = rank,
        popularity = popularity,
        mediaStatus = mediaStatus,
        mediaFormat = mediaFormat,
        startDate = startDate,
        endDate = endDate,
        episodeCount = episodeCount,
        chapterCount = chapterCount,
        volumeCount = volumeCount,
        genresJson = json.encodeToString(genres),
        background = background,
        relatedJson = json.encodeToString(related),
        recommendationsJson = json.encodeToString(recommendations),
        rankingPosition = rankingPosition,
        isDetailed = isDetailed,
        fetchedAtEpochMillis = fetchedAtEpochMillis,
        expiresAtEpochMillis = fetchedAtEpochMillis + CACHE_TTL_MILLIS,
    )

    private fun MalMediaCacheEntity.toModel(): MalCatalogMedia? {
        val type = runCatching { TrackingMediaType.valueOf(mediaType) }.getOrNull() ?: return null
        return MalCatalogMedia(
            key = MalMediaKey(type, malId),
            title = title,
            alternativeTitles = decodeList(alternativeTitlesJson),
            synopsis = synopsis,
            pictureMedium = mainPictureMedium,
            pictureLarge = mainPictureLarge,
            pictureGallery = decodeList(pictureGalleryJson),
            meanScore = meanScore,
            rank = rank,
            popularity = popularity,
            mediaStatus = mediaStatus,
            mediaFormat = mediaFormat,
            startDate = startDate,
            endDate = endDate,
            episodeCount = episodeCount,
            chapterCount = chapterCount,
            volumeCount = volumeCount,
            genres = decodeList(genresJson),
            background = background,
            related = decodeRelated(relatedJson),
            recommendations = decodeRelated(recommendationsJson),
            rankingPosition = rankingPosition,
            isDetailed = isDetailed,
            fetchedAtEpochMillis = fetchedAtEpochMillis,
        )
    }

    private fun decodeList(raw: String): List<String> =
        runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())

    private fun decodeRelated(raw: String): List<MalRelatedMedia> =
        runCatching { json.decodeFromString<List<MalRelatedMedia>>(raw) }.getOrDefault(emptyList())

    companion object {
        private const val CACHE_TTL_MILLIS = 7 * 24 * 60 * 60_000L
    }
}
