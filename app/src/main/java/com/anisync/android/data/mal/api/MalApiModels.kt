package com.anisync.android.data.mal.api

import com.anisync.android.domain.tracking.TrackingDesiredState
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingProvider
import kotlinx.serialization.Serializable

enum class MalApiFailureKind {
    ACCOUNT_NOT_FOUND,
    NOT_AUTHENTICATED,
    RELOGIN_REQUIRED,
    RATE_LIMITED,
    OFFLINE,
    TIMEOUT,
    TRANSPORT,
    TRANSIENT_SERVER,
    INVALID_RESPONSE,
    INVALID_PAGING_URL,
    PAGING_LOOP,
    LIMIT_EXCEEDED,
    PERMANENT,
    STORAGE,
    CANCELLED,
}

data class MalApiFailure(
    val kind: MalApiFailureKind,
    val httpStatus: Int? = null,
    val retryAfterMillis: Long? = null,
) {
    val retryable: Boolean
        get() = kind in setOf(
            MalApiFailureKind.RATE_LIMITED,
            MalApiFailureKind.OFFLINE,
            MalApiFailureKind.TIMEOUT,
            MalApiFailureKind.TRANSPORT,
            MalApiFailureKind.TRANSIENT_SERVER,
        )

    override fun toString(): String =
        "MalApiFailure(kind=${kind.name}, httpStatus=${httpStatus ?: "none"}, retryAfterMillis=${retryAfterMillis ?: "none"})"
}

sealed interface MalApiResult<out T> {
    data class Success<T>(val value: T) : MalApiResult<T> {
        override fun toString(): String = "MalApiResult.Success(value=<redacted>)"
    }

    data class Failure(val error: MalApiFailure) : MalApiResult<Nothing>
}

data class MalListPage(
    val entries: List<MalListEntry>,
    val nextPageUrl: String?,
)

@Serializable
data class MalListEntry(
    val malId: Long,
    val mediaType: TrackingMediaType,
    val title: String,
    val alternativeTitles: List<String>,
    val synopsis: String?,
    val pictureMedium: String?,
    val pictureLarge: String?,
    val meanScore: Double?,
    val rank: Int?,
    val popularity: Int?,
    val mediaStatus: String?,
    val startDate: String?,
    val endDate: String?,
    val episodeCount: Int?,
    val chapterCount: Int?,
    val volumeCount: Int?,
    val genres: List<String>,
    val desiredState: TrackingDesiredState,
    val providerUpdatedAtEpochMillis: Long?,
    val rawMediaJson: String,
    val rawListStatusJson: String,
) {
    override fun toString(): String =
        "MalListEntry(malId=$malId, mediaType=${mediaType.name}, title=<redacted>, desiredState=<redacted>, providerUpdatedAtEpochMillis=${providerUpdatedAtEpochMillis ?: "none"})"
}

sealed interface MalImportResult {
    data class Success(
        val importedEntries: Int,
        val removedEntries: Int,
        val pageCount: Int,
    ) : MalImportResult

    data class Failure(
        val error: MalApiFailure,
        val preservedEntryCount: Int,
    ) : MalImportResult
}

data class MalLibraryItem(
    val localMediaId: String,
    val malId: Long,
    val mediaType: TrackingMediaType,
    val title: String,
    val coverUrl: String?,
    val state: TrackingDesiredState,
    val fetchedAtEpochMillis: Long,
) {
    override fun toString(): String =
        "MalLibraryItem(localMediaId=<redacted>, malId=$malId, mediaType=${mediaType.name}, " +
            "title=<redacted>, coverUrl=<redacted>, state=<redacted>, " +
            "fetchedAtEpochMillis=$fetchedAtEpochMillis)"
}

/** Provider-aware navigation identity. It never aliases a MAL-only row to an AniList integer. */
@Serializable
data class MalMediaKey(
    val mediaType: TrackingMediaType,
    val malId: Long,
) {
    init {
        require(malId > 0L)
    }

    val stableValue: String
        get() = "${TrackingProvider.MYANIMELIST.name}:${mediaType.name}:$malId"
}

@Serializable
data class MalRelatedMedia(
    val key: MalMediaKey,
    val title: String,
    val pictureUrl: String?,
    val relationship: String?,
) {
    override fun toString(): String =
        "MalRelatedMedia(key=${key.stableValue}, title=<redacted>, pictureUrl=<redacted>, " +
            "relationship=${relationship ?: "none"})"
}

/** MAL-native search, details, ranking, and seasonal representation. */
@Serializable
data class MalCatalogMedia(
    val key: MalMediaKey,
    val title: String,
    val alternativeTitles: List<String> = emptyList(),
    val synopsis: String? = null,
    val pictureMedium: String? = null,
    val pictureLarge: String? = null,
    val pictureGallery: List<String> = emptyList(),
    val meanScore: Double? = null,
    val rank: Int? = null,
    val popularity: Int? = null,
    val mediaStatus: String? = null,
    val mediaFormat: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val episodeCount: Int? = null,
    val chapterCount: Int? = null,
    val volumeCount: Int? = null,
    val genres: List<String> = emptyList(),
    val background: String? = null,
    val listState: TrackingDesiredState? = null,
    val related: List<MalRelatedMedia> = emptyList(),
    val recommendations: List<MalRelatedMedia> = emptyList(),
    val rankingPosition: Int? = null,
    val isDetailed: Boolean = false,
    val fetchedAtEpochMillis: Long,
    val rawJson: String,
) {
    override fun toString(): String =
        "MalCatalogMedia(key=${key.stableValue}, title=<redacted>, synopsis=<redacted>, " +
            "images=<redacted>, background=<redacted>, listState=<redacted>, related=${related.size}, " +
            "recommendations=${recommendations.size}, rankingPosition=${rankingPosition ?: "none"}, " +
            "isDetailed=$isDetailed, fetchedAtEpochMillis=$fetchedAtEpochMillis, rawJson=<redacted>)"
}

data class MalCatalogPage(
    val entries: List<MalCatalogMedia>,
    val nextPageUrl: String?,
)

enum class MalSeason(val wireValue: String) {
    WINTER("winter"),
    SPRING("spring"),
    SUMMER("summer"),
    FALL("fall"),
}
