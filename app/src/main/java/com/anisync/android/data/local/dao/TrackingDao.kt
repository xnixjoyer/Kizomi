package com.anisync.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.anisync.android.data.local.entity.MalImportStateEntity
import com.anisync.android.data.local.entity.MalMediaCacheEntity
import com.anisync.android.data.local.entity.ProviderTrackingSnapshotEntity
import com.anisync.android.data.local.entity.TrackingOperationEntity
import com.anisync.android.data.local.entity.TrackingOperationTargetEntity
import com.anisync.android.data.local.entity.TrackingReconciliationItemEntity
import com.anisync.android.data.local.entity.TrackingReconciliationPlanEntity
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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOperation(operation: TrackingOperationEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTargets(targets: List<TrackingOperationTargetEntity>)

    @Transaction
    suspend fun insertOperationWithTargets(
        operation: TrackingOperationEntity,
        targets: List<TrackingOperationTargetEntity>,
    ) {
        insertOperation(operation)
        insertTargets(targets)
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

    @Query(
        "SELECT * FROM tracking_operation_targets " +
            "WHERE operationId = :operationId ORDER BY provider"
    )
    suspend fun getTargets(operationId: String): List<TrackingOperationTargetEntity>

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
        "SELECT * FROM tracking_operations WHERE state NOT IN ('SUCCEEDED', 'SUPERSEDED') " +
            "ORDER BY updatedAtEpochMillis DESC"
    )
    fun observeUnsettledOperations(): Flow<List<TrackingOperationEntity>>

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

    @Query(
        "SELECT * FROM mal_media_cache WHERE mediaType = :mediaType " +
            "AND title LIKE '%' || :query || '%' ORDER BY meanScore DESC, title COLLATE NOCASE LIMIT :limit"
    )
    suspend fun searchCachedMalMedia(mediaType: String, query: String, limit: Int): List<MalMediaCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertImportState(state: MalImportStateEntity)

    @Query("SELECT * FROM mal_import_states WHERE localAccountId = :localAccountId AND mediaType = :mediaType LIMIT 1")
    suspend fun getImportState(localAccountId: String, mediaType: String): MalImportStateEntity?

    @Query("DELETE FROM mal_import_states WHERE localAccountId = :localAccountId")
    suspend fun deleteImportStates(localAccountId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReconciliationPlan(plan: TrackingReconciliationPlanEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReconciliationItems(items: List<TrackingReconciliationItemEntity>)

    @Transaction
    suspend fun insertReconciliation(
        plan: TrackingReconciliationPlanEntity,
        items: List<TrackingReconciliationItemEntity>,
    ) {
        insertReconciliationPlan(plan)
        insertReconciliationItems(items)
    }

    @Query("SELECT * FROM tracking_reconciliation_plans WHERE planId = :planId LIMIT 1")
    fun observeReconciliationPlan(planId: String): Flow<TrackingReconciliationPlanEntity?>

    @Query(
        "SELECT * FROM tracking_reconciliation_items WHERE planId = :planId " +
            "ORDER BY action, itemKey"
    )
    fun observeReconciliationItems(planId: String): Flow<List<TrackingReconciliationItemEntity>>

    @Query(
        "UPDATE tracking_reconciliation_items SET state = :state, operationId = :operationId, " +
            "lastErrorKind = :lastErrorKind, updatedAtEpochMillis = :nowEpochMillis " +
            "WHERE planId = :planId AND itemKey = :itemKey"
    )
    suspend fun updateReconciliationItem(
        planId: String,
        itemKey: String,
        state: String,
        operationId: String?,
        lastErrorKind: String?,
        nowEpochMillis: Long,
    ): Int

    @Query(
        "UPDATE tracking_reconciliation_plans SET state = :state, updatedAtEpochMillis = :nowEpochMillis " +
            "WHERE planId = :planId"
    )
    suspend fun updateReconciliationPlan(planId: String, state: String, nowEpochMillis: Long): Int
}
