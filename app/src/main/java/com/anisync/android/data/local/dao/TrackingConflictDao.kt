package com.anisync.android.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.anisync.android.data.local.entity.ProviderTrackingSnapshotEntity
import kotlinx.coroutines.flow.Flow

data class TrackingAccountPairBinding(
    val localMediaId: String,
    val mediaType: String,
    val aniListAccountId: String?,
    val malAccountId: String?,
)

/** Read-only account-bound snapshot view used by the conflict and reconciliation engines. */
@Dao
interface TrackingConflictDao {
    @Query(
        "SELECT * FROM provider_tracking_snapshots " +
            "ORDER BY localMediaId, mediaType, provider, providerAccountId"
    )
    fun observeAllSnapshots(): Flow<List<ProviderTrackingSnapshotEntity>>

    @Query(
        "SELECT * FROM provider_tracking_snapshots " +
            "ORDER BY localMediaId, mediaType, provider, providerAccountId"
    )
    suspend fun getAllSnapshots(): List<ProviderTrackingSnapshotEntity>

    @Query(
        "SELECT DISTINCT operation.localMediaId AS localMediaId, " +
            "operation.mediaType AS mediaType, " +
            "ani.providerAccountId AS aniListAccountId, " +
            "mal.providerAccountId AS malAccountId " +
            "FROM tracking_operations operation " +
            "JOIN tracking_operation_targets ani ON ani.operationId = operation.operationId " +
            "AND ani.provider = 'ANILIST' " +
            "JOIN tracking_operation_targets mal ON mal.operationId = operation.operationId " +
            "AND mal.provider = 'MYANIMELIST' " +
            "WHERE ani.providerAccountId IS NOT NULL AND mal.providerAccountId IS NOT NULL " +
            "ORDER BY operation.localMediaId, operation.mediaType, ani.providerAccountId, mal.providerAccountId"
    )
    fun observeDualBindings(): Flow<List<TrackingAccountPairBinding>>

    @Query(
        "SELECT DISTINCT operation.localMediaId AS localMediaId, " +
            "operation.mediaType AS mediaType, " +
            "ani.providerAccountId AS aniListAccountId, " +
            "mal.providerAccountId AS malAccountId " +
            "FROM tracking_operations operation " +
            "JOIN tracking_operation_targets ani ON ani.operationId = operation.operationId " +
            "AND ani.provider = 'ANILIST' " +
            "JOIN tracking_operation_targets mal ON mal.operationId = operation.operationId " +
            "AND mal.provider = 'MYANIMELIST' " +
            "WHERE ani.providerAccountId IS NOT NULL AND mal.providerAccountId IS NOT NULL " +
            "ORDER BY operation.localMediaId, operation.mediaType, ani.providerAccountId, mal.providerAccountId"
    )
    suspend fun getDualBindings(): List<TrackingAccountPairBinding>
}
