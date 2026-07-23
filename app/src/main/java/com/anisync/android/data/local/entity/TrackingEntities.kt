package com.anisync.android.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Last state confirmed by the single active provider. */
@Entity(
    tableName = "provider_tracking_snapshots",
    primaryKeys = ["provider", "providerAccountId", "localMediaId"],
    foreignKeys = [
        ForeignKey(
            entity = LocalMediaIdentityEntity::class,
            parentColumns = ["id"],
            childColumns = ["localMediaId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        )
    ],
    indices = [
        Index(value = ["localMediaId"]),
        Index(value = ["provider", "providerAccountId", "mediaType"]),
        Index(value = ["provider", "providerAccountId", "providerMediaId", "mediaType"], unique = true),
    ],
)
data class ProviderTrackingSnapshotEntity(
    val provider: String,
    val providerAccountId: String,
    val localMediaId: String,
    val providerMediaId: Long,
    val providerListEntryId: Long?,
    val mediaType: String,
    val title: String,
    val coverUrl: String?,
    val status: String,
    val progress: Int,
    val progressSecondary: Int?,
    val score: Double?,
    val repeatCount: Int,
    val notes: String?,
    val startedAt: String?,
    val completedAt: String?,
    val providerUpdatedAtEpochMillis: Long?,
    val fetchedAtEpochMillis: Long,
    val isDeleted: Boolean,
)

/** One immutable absolute desired state. */
@Entity(
    tableName = "tracking_operations",
    foreignKeys = [
        ForeignKey(
            entity = LocalMediaIdentityEntity::class,
            parentColumns = ["id"],
            childColumns = ["localMediaId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        )
    ],
    indices = [
        Index(value = ["localMediaId"]),
        Index(value = ["logicalKey", "generation"], unique = true),
        Index(value = ["deduplicationKey"]),
        Index(value = ["state", "updatedAtEpochMillis"]),
    ],
    primaryKeys = ["operationId"],
)
data class TrackingOperationEntity(
    val operationId: String,
    val logicalKey: String,
    val localMediaId: String,
    val mediaType: String,
    val generation: Long,
    val deduplicationKey: String,
    val commandJson: String,
    val fieldMask: String,
    val isTombstone: Boolean,
    val state: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

/** Exactly one fail-closed delivery target for an operation. */
@Entity(
    tableName = "tracking_operation_targets",
    primaryKeys = ["operationId"],
    foreignKeys = [
        ForeignKey(
            entity = TrackingOperationEntity::class,
            parentColumns = ["operationId"],
            childColumns = ["operationId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION,
        )
    ],
    indices = [
        Index(value = ["state", "nextAttemptAtEpochMillis", "updatedAtEpochMillis"]),
        Index(value = ["provider", "providerAccountId", "providerMediaId"]),
    ],
)
data class TrackingOperationTargetEntity(
    val operationId: String,
    val provider: String,
    val providerAccountId: String?,
    val providerMediaId: Long?,
    val state: String,
    val attemptCount: Int,
    val nextAttemptAtEpochMillis: Long,
    val leaseToken: String?,
    val leaseExpiresAtEpochMillis: Long?,
    val lastErrorKind: String?,
    val lastHttpStatus: Int?,
    val retryAfterMillis: Long?,
    val remoteRevision: String?,
    val updatedAtEpochMillis: Long,
)

/** Minimal normalized provider-global MAL catalog cache. */
@Entity(
    tableName = "mal_media_cache",
    primaryKeys = ["malId", "mediaType"],
    indices = [
        Index(value = ["mediaType", "title"]),
        Index(value = ["mediaType", "fetchedAtEpochMillis"]),
    ],
)
data class MalMediaCacheEntity(
    val malId: Long,
    val mediaType: String,
    val title: String,
    val alternativeTitlesJson: String,
    val synopsis: String?,
    val mainPictureMedium: String?,
    val mainPictureLarge: String?,
    val pictureGalleryJson: String,
    val meanScore: Double?,
    val rank: Int?,
    val popularity: Int?,
    val mediaStatus: String?,
    val mediaFormat: String?,
    val startDate: String?,
    val endDate: String?,
    val episodeCount: Int?,
    val chapterCount: Int?,
    val volumeCount: Int?,
    val genresJson: String,
    val background: String?,
    val relatedJson: String,
    val recommendationsJson: String,
    val rankingPosition: Int?,
    val isDetailed: Boolean,
    val fetchedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
)

/** Account/type-scoped last-good list refresh metadata. */
@Entity(
    tableName = "mal_library_refresh_states",
    primaryKeys = ["localAccountId", "mediaType"],
    indices = [Index(value = ["state", "lastAttemptAtEpochMillis"])],
)
data class MalLibraryRefreshStateEntity(
    val localAccountId: String,
    val mediaType: String,
    val state: String,
    val generation: Long,
    val nextPageUrl: String?,
    val itemCount: Int,
    val lastAttemptAtEpochMillis: Long,
    val lastSuccessAtEpochMillis: Long?,
    val lastErrorKind: String?,
)

/** Normalized staging row for one atomic MAL list refresh. */
@Entity(
    tableName = "mal_library_refresh_entries",
    primaryKeys = ["localAccountId", "mediaType", "generation", "malId"],
    indices = [
        Index(value = ["localAccountId", "mediaType", "generation"]),
        Index(value = ["localMediaId"]),
    ],
)
data class MalLibraryRefreshEntryEntity(
    val localAccountId: String,
    val mediaType: String,
    val generation: Long,
    val malId: Long,
    val localMediaId: String,
    val title: String,
    val alternativeTitlesJson: String,
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
    val genresJson: String,
    val status: String,
    val progress: Int,
    val progressSecondary: Int?,
    val score100: Double?,
    val repeatCount: Int,
    val notes: String?,
    val startedAt: String?,
    val completedAt: String?,
    val providerUpdatedAtEpochMillis: Long?,
)
