package com.anisync.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.anisync.android.data.local.entity.MalLibraryRefreshStateEntity
import com.anisync.android.data.local.entity.MalLibraryRefreshEntryEntity
import com.anisync.android.data.local.entity.MalMediaCacheEntity
import com.anisync.android.data.local.entity.ProviderTrackingSnapshotEntity
import com.anisync.android.data.local.entity.TrackingOperationEntity
import com.anisync.android.data.local.entity.TrackingOperationTargetEntity
import kotlinx.coroutines.flow.Flow

/** Durable storage boundary for provider snapshots, the command journal, and reconciliation. */
@Dao
interface TrackingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: ProviderTrackingSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshots(snapshots: List<ProviderTrackingSnapshotEntity>)

    @Query(
        "SELECT * FROM provider_tracking_snapshots " +
            "WHERE provider = :provider AND providerAccountId = :providerAccountId " +
            "AND mediaType = :mediaType ORDER BY title COLLATE NOCASE, providerMediaId"
    )
    fun observeSnapshots(
        provider: String,
        providerAccountId: String,
        mediaType: String,
    ): Flow<List<ProviderTrackingSnapshotEntity>>

    @Query(
        "SELECT * FROM provider_tracking_snapshots " +
            "WHERE provider = :provider AND providerAccountId = :providerAccountId " +
            "AND mediaType = :mediaType AND isDeleted = 0 " +
            "ORDER BY title COLLATE NOCASE, providerMediaId"
    )
    fun observeActiveSnapshots(
        provider: String,
        providerAccountId: String,
        mediaType: String,
    ): Flow<List<ProviderTrackingSnapshotEntity>>

    @Query(
        "SELECT * FROM provider_tracking_snapshots " +
            "WHERE provider = :provider AND providerAccountId = :providerAccountId " +
            "AND providerMediaId = :providerMediaId AND mediaType = :mediaType LIMIT 1"
    )
    suspend fun findSnapshotByProviderId(
        provider: String,
        providerAccountId: String,
        providerMediaId: Long,
        mediaType: String,
    ): ProviderTrackingSnapshotEntity?

    @Query(
        "SELECT * FROM provider_tracking_snapshots " +
            "WHERE provider = :provider AND providerAccountId = :providerAccountId " +
            "AND localMediaId = :localMediaId LIMIT 1"
    )
    suspend fun findSnapshot(
        provider: String,
        providerAccountId: String,
        localMediaId: String,
    ): ProviderTrackingSnapshotEntity?

    @Query(
        "DELETE FROM provider_tracking_snapshots " +
            "WHERE provider = :provider AND providerAccountId = :providerAccountId " +
            "AND mediaType = :mediaType AND fetchedAtEpochMillis < :generationStartedAtEpochMillis"
    )
    suspend fun deleteSnapshotsMissingFromGeneration(
        provider: String,
        providerAccountId: String,
        mediaType: String,
        generationStartedAtEpochMillis: Long,
    ): Int

    @Query(
        "DELETE FROM provider_tracking_snapshots " +
            "WHERE provider = :provider AND providerAccountId = :providerAccountId"
    )
    suspend fun deleteSnapshotsForAccount(provider: String, providerAccountId: String): Int

    @Query(
        "SELECT COUNT(*) FROM provider_tracking_snapshots " +
            "WHERE provider = :provider AND providerAccountId = :providerAccountId " +
            "AND mediaType = :mediaType AND isDeleted = 0"
    )
    suspend fun countActiveSnapshots(
        provider: String,
        providerAccountId: String,
        mediaType: String,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOperation(operation: TrackingOperationEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTarget(target: TrackingOperationTargetEntity)

    @Transaction
    suspend fun insertOperationWithTarget(
        operation: TrackingOperationEntity,
        target: TrackingOperationTargetEntity,
    ) {
        insertOperation(operation)
        insertTarget(target)
    }

    @Query("SELECT COALESCE(MAX(generation), 0) FROM tracking_operations WHERE logicalKey = :logicalKey")
    suspend fun latestGeneration(logicalKey: String): Long

    @Query("SELECT * FROM tracking_operations WHERE operationId = :operationId LIMIT 1")
    suspend fun getOperation(operationId: String): TrackingOperationEntity?

    @Query(
        "SELECT * FROM tracking_operations WHERE logicalKey = :logicalKey " +
            "ORDER BY generation DESC LIMIT 1"
    )
    suspend fun latestOperation(logicalKey: String): TrackingOperationEntity?

    @Query(
        "SELECT * FROM tracking_operations WHERE deduplicationKey = :deduplicationKey " +
            "AND state NOT IN ('SUCCEEDED', 'FAILED', 'SUPERSEDED') " +
            "ORDER BY generation DESC LIMIT 1"
    )
    suspend fun findUnsettledByDeduplicationKey(
        deduplicationKey: String,
    ): TrackingOperationEntity?

    @Query("SELECT * FROM tracking_operation_targets WHERE operationId = :operationId LIMIT 1")
    suspend fun getTarget(operationId: String): TrackingOperationTargetEntity?

    @Query(
        "SELECT * FROM tracking_operation_targets ORDER BY updatedAtEpochMillis DESC, provider"
    )
    fun observeAllTargets(): Flow<List<TrackingOperationTargetEntity>>

    @Query(
        "SELECT target.* FROM tracking_operation_targets target " +
            "JOIN tracking_operations operation ON operation.operationId = target.operationId " +
            "WHERE target.state IN ('PENDING', 'RETRYING') " +
            "AND target.nextAttemptAtEpochMillis <= :nowEpochMillis " +
            "AND (target.leaseExpiresAtEpochMillis IS NULL OR target.leaseExpiresAtEpochMillis < :nowEpochMillis) " +
            "AND NOT EXISTS (" +
            "  SELECT 1 FROM tracking_operation_targets activeTarget " +
            "  JOIN tracking_operations activeOperation ON activeOperation.operationId = activeTarget.operationId " +
            "  WHERE activeOperation.logicalKey = operation.logicalKey " +
            "  AND activeTarget.state = 'RUNNING' " +
            "  AND activeTarget.leaseExpiresAtEpochMillis >= :nowEpochMillis" +
            ") " +
            "ORDER BY operation.createdAtEpochMillis, target.provider LIMIT :limit"
    )
    suspend fun findDeliverableTargets(
        nowEpochMillis: Long,
        limit: Int,
    ): List<TrackingOperationTargetEntity>

    @Query(
        "UPDATE tracking_operation_targets SET state = 'RUNNING', leaseToken = :leaseToken, " +
            "leaseExpiresAtEpochMillis = :leaseExpiresAtEpochMillis, attemptCount = attemptCount + 1, " +
            "updatedAtEpochMillis = :nowEpochMillis " +
            "WHERE operationId = :operationId AND provider = :provider " +
            "AND state IN ('PENDING', 'RETRYING') " +
            "AND (leaseExpiresAtEpochMillis IS NULL OR leaseExpiresAtEpochMillis < :nowEpochMillis)"
    )
    suspend fun claimTarget(
        operationId: String,
        provider: String,
        leaseToken: String,
        leaseExpiresAtEpochMillis: Long,
        nowEpochMillis: Long,
    ): Int

    @Query(
        "UPDATE tracking_operation_targets SET state = :state, nextAttemptAtEpochMillis = :nextAttemptAtEpochMillis, " +
            "leaseToken = NULL, leaseExpiresAtEpochMillis = NULL, lastErrorKind = :lastErrorKind, " +
            "lastHttpStatus = :lastHttpStatus, retryAfterMillis = :retryAfterMillis, " +
            "remoteRevision = :remoteRevision, updatedAtEpochMillis = :nowEpochMillis " +
            "WHERE operationId = :operationId AND provider = :provider AND leaseToken = :leaseToken"
    )
    suspend fun finishClaimedTarget(
        operationId: String,
        provider: String,
        leaseToken: String,
        state: String,
        nextAttemptAtEpochMillis: Long,
        lastErrorKind: String?,
        lastHttpStatus: Int?,
        retryAfterMillis: Long?,
        remoteRevision: String?,
        nowEpochMillis: Long,
    ): Int

    @Query(
        "UPDATE tracking_operation_targets SET state = 'RETRYING', leaseToken = NULL, " +
            "leaseExpiresAtEpochMillis = NULL, nextAttemptAtEpochMillis = :nowEpochMillis, " +
            "lastErrorKind = 'LEASE_EXPIRED', updatedAtEpochMillis = :nowEpochMillis " +
            "WHERE state = 'RUNNING' AND leaseExpiresAtEpochMillis < :nowEpochMillis"
    )
    suspend fun recoverExpiredLeases(nowEpochMillis: Long): Int

    @Query(
        "UPDATE tracking_operation_targets SET state = 'SUPERSEDED', leaseToken = NULL, " +
            "leaseExpiresAtEpochMillis = NULL, updatedAtEpochMillis = :nowEpochMillis " +
            "WHERE operationId IN (SELECT operationId FROM tracking_operations " +
            "WHERE logicalKey = :logicalKey AND generation < :newGeneration) " +
            "AND state IN ('PENDING', 'RETRYING', 'BLOCKED')"
    )
    suspend fun supersedeWaitingTargets(
        logicalKey: String,
        newGeneration: Long,
        nowEpochMillis: Long,
    ): Int

    @Query(
        "UPDATE tracking_operations SET state = :state, updatedAtEpochMillis = :nowEpochMillis " +
            "WHERE operationId = :operationId"
    )
    suspend fun updateOperationState(operationId: String, state: String, nowEpochMillis: Long): Int

    @Query(
        "UPDATE tracking_operation_targets SET state = 'RETRYING', attemptCount = 0, " +
            "nextAttemptAtEpochMillis = :nowEpochMillis, leaseToken = NULL, " +
            "leaseExpiresAtEpochMillis = NULL, lastErrorKind = NULL, lastHttpStatus = NULL, " +
            "retryAfterMillis = NULL, updatedAtEpochMillis = :nowEpochMillis " +
            "WHERE operationId = :operationId AND provider = :provider AND state = 'FAILED'"
    )
    suspend fun retryFailedTarget(
        operationId: String,
        provider: String,
        nowEpochMillis: Long,
    ): Int

    @Query(
        "SELECT * FROM tracking_operations WHERE state NOT IN ('SUCCEEDED', 'SUPERSEDED') " +
            "ORDER BY updatedAtEpochMillis DESC"
    )
    fun observeUnsettledOperations(): Flow<List<TrackingOperationEntity>>

    @Query(
        "SELECT * FROM tracking_operations WHERE state != 'SUPERSEDED' " +
            "ORDER BY updatedAtEpochMillis DESC LIMIT 100"
    )
    fun observeRecentOperations(): Flow<List<TrackingOperationEntity>>

    @Query(
        "SELECT * FROM provider_tracking_snapshots WHERE isDeleted = 0 " +
            "ORDER BY localMediaId, provider"
    )
    fun observeAllActiveSnapshots(): Flow<List<ProviderTrackingSnapshotEntity>>

    @Query(
        "SELECT * FROM provider_tracking_snapshots WHERE isDeleted = 0 " +
            "ORDER BY localMediaId, provider"
    )
    suspend fun getAllActiveSnapshots(): List<ProviderTrackingSnapshotEntity>

    @Query(
        "SELECT COUNT(*) FROM tracking_operation_targets " +
            "WHERE operationId = :operationId AND state = :state"
    )
    suspend fun countTargetsInState(operationId: String, state: String): Int

    @Query(
        "SELECT COUNT(*) FROM tracking_operation_targets " +
            "WHERE state IN ('PENDING', 'RUNNING', 'RETRYING')"
    )
    suspend fun countUnsettledDeliveries(): Int

    @Query(
        "DELETE FROM tracking_operations WHERE updatedAtEpochMillis < :beforeEpochMillis " +
            "AND state IN ('SUCCEEDED', 'SUPERSEDED')"
    )
    suspend fun pruneSettledOperations(beforeEpochMillis: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMalMedia(items: List<MalMediaCacheEntity>)

    @Query("SELECT * FROM mal_media_cache WHERE malId = :malId AND mediaType = :mediaType LIMIT 1")
    fun observeMalMedia(malId: Long, mediaType: String): Flow<MalMediaCacheEntity?>

    @Query("SELECT * FROM mal_media_cache WHERE malId = :malId AND mediaType = :mediaType LIMIT 1")
    suspend fun getMalMedia(malId: Long, mediaType: String): MalMediaCacheEntity?

    @Query(
        "SELECT * FROM mal_media_cache WHERE mediaType = :mediaType " +
            "AND title LIKE '%' || :query || '%' ORDER BY meanScore DESC, title COLLATE NOCASE LIMIT :limit"
    )
    suspend fun searchCachedMalMedia(mediaType: String, query: String, limit: Int): List<MalMediaCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLibraryRefreshState(state: MalLibraryRefreshStateEntity)

    @Query(
        "SELECT * FROM mal_library_refresh_states " +
            "WHERE localAccountId = :localAccountId AND mediaType = :mediaType LIMIT 1"
    )
    suspend fun getLibraryRefreshState(
        localAccountId: String,
        mediaType: String,
    ): MalLibraryRefreshStateEntity?

    @Query("DELETE FROM mal_library_refresh_states WHERE localAccountId = :localAccountId")
    suspend fun deleteLibraryRefreshStates(localAccountId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLibraryRefreshEntries(entries: List<MalLibraryRefreshEntryEntity>)

    @Query(
        "SELECT * FROM mal_library_refresh_entries WHERE localAccountId = :localAccountId " +
            "AND mediaType = :mediaType AND generation = :generation ORDER BY malId"
    )
    suspend fun getLibraryRefreshEntries(
        localAccountId: String,
        mediaType: String,
        generation: Long,
    ): List<MalLibraryRefreshEntryEntity>

    @Query(
        "SELECT malId FROM mal_library_refresh_entries WHERE localAccountId = :localAccountId " +
            "AND mediaType = :mediaType AND generation = :generation"
    )
    suspend fun getLibraryRefreshEntryIds(
        localAccountId: String,
        mediaType: String,
        generation: Long,
    ): List<Long>

    @Query(
        "DELETE FROM mal_library_refresh_entries WHERE localAccountId = :localAccountId " +
            "AND mediaType = :mediaType"
    )
    suspend fun deleteLibraryRefreshEntries(localAccountId: String, mediaType: String): Int

    @Query("DELETE FROM mal_library_refresh_entries WHERE localAccountId = :localAccountId")
    suspend fun deleteLibraryRefreshEntriesForAccount(localAccountId: String): Int

    @Query("DELETE FROM tracking_operation_targets")
    suspend fun deleteAllOperationTargets(): Int

    @Query("DELETE FROM tracking_operations")
    suspend fun deleteAllOperations(): Int

    @Query("DELETE FROM provider_tracking_snapshots")
    suspend fun deleteAllProviderSnapshots(): Int

    @Query("DELETE FROM mal_media_cache")
    suspend fun deleteAllMalMediaCache(): Int

    @Query("DELETE FROM mal_library_refresh_states")
    suspend fun deleteAllMalLibraryRefreshStates(): Int

    @Query("DELETE FROM mal_library_refresh_entries")
    suspend fun deleteAllMalLibraryRefreshEntries(): Int

    @Transaction
    suspend fun purgeAllProviderBoundState() {
        deleteAllOperationTargets()
        deleteAllOperations()
        deleteAllProviderSnapshots()
        deleteAllMalLibraryRefreshEntries()
        deleteAllMalLibraryRefreshStates()
    }

    @Transaction
    suspend fun purgeMalLocalData() {
        deleteAllMalMediaCache()
        deleteAllMalLibraryRefreshEntries()
        deleteAllMalLibraryRefreshStates()
        deleteSnapshotsForProvider("MYANIMELIST")
        deleteTargetsForProvider("MYANIMELIST")
        deleteOrphanOperations()
    }

    @Query("DELETE FROM provider_tracking_snapshots WHERE provider = :provider")
    suspend fun deleteSnapshotsForProvider(provider: String): Int

    @Query("DELETE FROM tracking_operation_targets WHERE provider = :provider")
    suspend fun deleteTargetsForProvider(provider: String): Int

    @Query("DELETE FROM tracking_operations WHERE NOT EXISTS (SELECT 1 FROM tracking_operation_targets target WHERE target.operationId = tracking_operations.operationId)")
    suspend fun deleteOrphanOperations(): Int

}
