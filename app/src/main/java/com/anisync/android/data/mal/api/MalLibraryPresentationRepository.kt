package com.anisync.android.data.mal.api

import com.anisync.android.data.identity.MediaIdentityProvider
import com.anisync.android.data.local.dao.TrackingDao
import com.anisync.android.data.local.entity.MalMediaCacheEntity
import com.anisync.android.data.local.entity.ProviderTrackingSnapshotEntity
import com.anisync.android.domain.tracking.TrackingDesiredState
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * MAL-native library row enriched with the provider catalog fields needed by the shared Library UI.
 * Provider and local identities remain structurally separate; no AniList identifier is synthesized.
 */
data class MalLibraryPresentationRecord(
    val localMediaId: String,
    val malId: Long,
    val mediaType: TrackingMediaType,
    val title: String,
    val alternativeTitles: List<String>,
    val coverUrl: String?,
    val state: TrackingDesiredState,
    val totalPrimary: Int?,
    val totalSecondary: Int?,
    val mediaStartDate: String?,
    val providerUpdatedAtEpochMillis: Long?,
    val fetchedAtEpochMillis: Long,
) {
    init {
        require(localMediaId.isNotBlank())
        require(malId > 0L)
        require(title.isNotBlank())
        require(totalPrimary == null || totalPrimary >= 0)
        require(totalSecondary == null || totalSecondary >= 0)
    }

    override fun toString(): String =
        "MalLibraryPresentationRecord(localMediaId=<redacted>, malId=$malId, " +
            "mediaType=${mediaType.name}, title=<redacted>, alternativeTitles=<redacted>, " +
            "coverUrl=<redacted>, state=<redacted>, totalPrimary=${totalPrimary ?: "none"}, " +
            "totalSecondary=${totalSecondary ?: "none"}, mediaStartDate=${mediaStartDate ?: "none"}, " +
            "providerUpdatedAtEpochMillis=${providerUpdatedAtEpochMillis ?: "none"}, " +
            "fetchedAtEpochMillis=$fetchedAtEpochMillis)"
}

/**
 * Read-only projection over the existing atomic MAL refresh cache. It performs no network access and
 * cannot contact AniList. The active MAL account id must be supplied by the caller.
 */
@Singleton
class MalLibraryPresentationRepository @Inject constructor(
    private val dao: TrackingDao,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observeLibrary(
        localAccountId: String,
        mediaType: TrackingMediaType,
    ): Flow<List<MalLibraryPresentationRecord>> {
        require(localAccountId.isNotBlank())
        return dao.observeActiveSnapshots(
            provider = MediaIdentityProvider.MYANIMELIST.name,
            providerAccountId = localAccountId,
            mediaType = mediaType.name,
        ).mapLatest { snapshots ->
            snapshots.mapNotNull { snapshot ->
                val cache = dao.getMalMedia(snapshot.providerMediaId, snapshot.mediaType)
                snapshot.toMalLibraryPresentationRecord(cache, json)
            }
        }
    }
}

internal fun ProviderTrackingSnapshotEntity.toMalLibraryPresentationRecord(
    cache: MalMediaCacheEntity?,
    json: Json = Json { ignoreUnknownKeys = true },
): MalLibraryPresentationRecord? {
    if (provider != MediaIdentityProvider.MYANIMELIST.name || isDeleted) return null
    val type = runCatching { TrackingMediaType.valueOf(mediaType) }.getOrNull() ?: return null
    val normalizedStatus = runCatching { TrackingStatus.valueOf(status) }.getOrNull() ?: return null
    if (providerMediaId <= 0L || localMediaId.isBlank() || title.isBlank()) return null

    val alternativeTitles = cache?.alternativeTitlesJson
        ?.let { encoded -> runCatching { json.decodeFromString<List<String>>(encoded) }.getOrNull() }
        .orEmpty()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()

    val totalPrimary = when (type) {
        TrackingMediaType.ANIME -> cache?.episodeCount
        TrackingMediaType.MANGA -> cache?.chapterCount
    }?.takeIf { it >= 0 }
    val totalSecondary = if (type == TrackingMediaType.MANGA) {
        cache?.volumeCount?.takeIf { it >= 0 }
    } else {
        null
    }

    return MalLibraryPresentationRecord(
        localMediaId = localMediaId,
        malId = providerMediaId,
        mediaType = type,
        title = title.trim(),
        alternativeTitles = alternativeTitles,
        coverUrl = coverUrl ?: cache?.mainPictureLarge ?: cache?.mainPictureMedium,
        state = TrackingDesiredState(
            status = normalizedStatus,
            progress = progress.coerceAtLeast(0),
            progressSecondary = progressSecondary?.coerceAtLeast(0),
            score100 = score?.coerceIn(0.0, 100.0),
            repeatCount = repeatCount.coerceAtLeast(0),
            notes = notes,
            startedAt = startedAt,
            completedAt = completedAt,
        ),
        totalPrimary = totalPrimary,
        totalSecondary = totalSecondary,
        mediaStartDate = cache?.startDate,
        providerUpdatedAtEpochMillis = providerUpdatedAtEpochMillis,
        fetchedAtEpochMillis = fetchedAtEpochMillis,
    )
}
