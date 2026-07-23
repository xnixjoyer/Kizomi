package com.anisync.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.anisync.android.data.local.entity.ProviderMediaIdentityEntity
import com.anisync.android.data.local.entity.ProviderTrackingSnapshotEntity
import com.anisync.android.data.local.entity.TrackingReconciliationItemEntity
import com.anisync.android.data.local.entity.TrackingReconciliationPlanEntity
import kotlinx.coroutines.flow.Flow

/** Durable read/write boundary for immutable compare previews and resumable missing-only execution. */
@Dao
interface TrackingReconciliationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlan(plan: TrackingReconciliationPlanEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItems(items: List<TrackingReconciliationItemEntity>)

    @Transaction
    suspend fun insertPlanWithItems(
        plan: TrackingReconciliationPlanEntity,
        items: List<TrackingReconciliationItemEntity>,
    ) {
        insertPlan(plan)
        insertItems(items)
    }

    @Query("SELECT * FROM tracking_reconciliation_plans WHERE planId = :planId LIMIT 1")
    fun observePlan(planId: String): Flow<TrackingReconciliationPlanEntity?>

    @Query(
        "SELECT * FROM tracking_reconciliation_items WHERE planId = :planId " +
            "ORDER BY action, itemKey"
    )
    fun observeItems(planId: String): Flow<List<TrackingReconciliationItemEntity>>

    @Query("SELECT * FROM tracking_reconciliation_plans WHERE planId = :planId LIMIT 1")
    suspend fun getPlan(planId: String): TrackingReconciliationPlanEntity?

    @Query(
        "SELECT * FROM tracking_reconciliation_plans " +
            "ORDER BY updatedAtEpochMillis DESC, createdAtEpochMillis DESC LIMIT 1"
    )
    suspend fun getLatestPlan(): TrackingReconciliationPlanEntity?

    @Query(
        "SELECT * FROM tracking_reconciliation_items WHERE planId = :planId " +
            "ORDER BY action, itemKey"
    )
    suspend fun getItems(planId: String): List<TrackingReconciliationItemEntity>

    @Query(
        "SELECT * FROM provider_tracking_snapshots " +
            "WHERE provider = :provider AND providerAccountId = :accountId " +
            "AND mediaType = :mediaType AND isDeleted = 0 " +
            "ORDER BY localMediaId"
    )
    suspend fun getActiveSnapshots(
        provider: String,
        accountId: String,
        mediaType: String,
    ): List<ProviderTrackingSnapshotEntity>

    @Query(
        "SELECT * FROM provider_tracking_snapshots " +
            "WHERE provider = :provider AND providerAccountId = :accountId " +
            "AND mediaType = :mediaType AND localMediaId = :localMediaId " +
            "AND isDeleted = 0 LIMIT 1"
    )
    suspend fun findActiveSnapshot(
        provider: String,
        accountId: String,
        mediaType: String,
        localMediaId: String,
    ): ProviderTrackingSnapshotEntity?

    @Query(
        "SELECT * FROM provider_media_identities " +
            "WHERE localMediaId = :localMediaId AND provider = :provider " +
            "AND mediaType = :mediaType LIMIT 1"
    )
    suspend fun findProviderIdentity(
        localMediaId: String,
        provider: String,
        mediaType: String,
    ): ProviderMediaIdentityEntity?

    @Query(
        "SELECT COUNT(*) FROM provider_media_identity_issues " +
            "WHERE localMediaId = :localMediaId AND provider = :provider " +
            "AND mediaType = :mediaType " +
            "AND verificationStatus IN ('UNRESOLVED', 'CONFLICTING', 'REJECTED')"
    )
    suspend fun countBlockingIdentityIssues(
        localMediaId: String,
        provider: String,
        mediaType: String,
    ): Int

    @Query(
        "UPDATE tracking_reconciliation_items SET state = :state, operationId = :operationId, " +
            "lastErrorKind = :lastErrorKind, updatedAtEpochMillis = :nowEpochMillis " +
            "WHERE planId = :planId AND itemKey = :itemKey"
    )
    suspend fun updateItem(
        planId: String,
        itemKey: String,
        state: String,
        operationId: String?,
        lastErrorKind: String?,
        nowEpochMillis: Long,
    ): Int

    @Query(
        "UPDATE tracking_reconciliation_plans SET state = :state, " +
            "updatedAtEpochMillis = :nowEpochMillis WHERE planId = :planId"
    )
    suspend fun updatePlan(planId: String, state: String, nowEpochMillis: Long): Int
}
