package com.anisync.android.data.mal.api

import com.anisync.android.domain.tracking.TrackingDesiredState
import com.anisync.android.domain.tracking.TrackingMediaType
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
