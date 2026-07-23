package com.anisync.android.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Last provider-confirmed list state. Provider rows deliberately remain separate so a dual write
 * can expose divergence instead of flattening two remote truths into one value.
 */
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
    val rawProviderFieldsJson: String,
    val isDeleted: Boolean,
)

/** One immutable absolute desired state. A newer generation supersedes only work not in flight. */
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

/** Independent delivery state for one provider in a durable, non-atomic dual-write saga. */
@Entity(
    tableName = "tracking_operation_targets",
    primaryKeys = ["operationId", "provider"],
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
        Index(value = ["operationId"]),
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

/** MAL-native metadata cache used by list, search, details and discovery without AniList IDs. */
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
    val relatedJson: String,
    val recommendationsJson: String,
    val rawJson: String,
    val fetchedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
)

/** Account/type-scoped paging and last-good import metadata. */
@Entity(
    tableName = "mal_import_states",
    primaryKeys = ["localAccountId", "mediaType"],
    indices = [Index(value = ["state", "lastAttemptAtEpochMillis"])],
)
data class MalImportStateEntity(
    val localAccountId: String,
    val mediaType: String,
    val state: String,
    val generation: Long,
    val nextPageUrl: String?,
    val importedCount: Int,
    val lastAttemptAtEpochMillis: Long,
    val lastSuccessAtEpochMillis: Long?,
    val lastErrorKind: String?,
)

/**
 * Account-scoped import staging. Provider snapshots remain the last fully successful generation
 * until every page has been fetched and this staging generation is promoted atomically.
 */
@Entity(
    tableName = "mal_import_entries",
    primaryKeys = ["localAccountId", "mediaType", "generation", "malId"],
    indices = [
        Index(value = ["localAccountId", "mediaType", "generation"]),
        Index(value = ["localMediaId"]),
    ],
)
data class MalImportEntryEntity(
    val localAccountId: String,
    val mediaType: String,
    val generation: Long,
    val malId: Long,
    val localMediaId: String,
    val payloadJson: String,
) {
    override fun toString(): String =
        "MalImportEntryEntity(localAccountId=<redacted>, mediaType=$mediaType, " +
            "generation=$generation, malId=$malId, localMediaId=<redacted>, payloadJson=<redacted>)"
}

/** Immutable reconciliation preview baseline; execution can resume after process death. */
@Entity(
    tableName = "tracking_reconciliation_plans",
    indices = [Index(value = ["state", "updatedAtEpochMillis"])],
    primaryKeys = ["planId"],
)
data class TrackingReconciliationPlanEntity(
    val planId: String,
    val mode: String,
    val mediaType: String,
    val sourceAccountId: String?,
    val targetAccountId: String?,
    val state: String,
    val baselineFingerprint: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "tracking_reconciliation_items",
    primaryKeys = ["planId", "itemKey"],
    foreignKeys = [
        ForeignKey(
            entity = TrackingReconciliationPlanEntity::class,
            parentColumns = ["planId"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION,
        )
    ],
    indices = [
        Index(value = ["planId"]),
        Index(value = ["planId", "action", "state"]),
    ],
)
data class TrackingReconciliationItemEntity(
    val planId: String,
    val itemKey: String,
    val localMediaId: String?,
    val mediaType: String,
    val aniListId: Long?,
    val malId: Long?,
    val action: String,
    val state: String,
    val sourceSnapshotJson: String?,
    val targetSnapshotJson: String?,
    val commandJson: String?,
    val operationId: String?,
    val lastErrorKind: String?,
    val updatedAtEpochMillis: Long,
)
