package com.anisync.android.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anisync.android.data.local.entity.LocalMediaIdentityEntity
import com.anisync.android.data.local.entity.ProviderMediaIdentityEntity
import com.anisync.android.data.local.entity.ProviderMediaIdentityIssueEntity

@Dao
interface MediaIdentityDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLocalIdentity(entity: LocalMediaIdentityEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProviderIdentity(entity: ProviderMediaIdentityEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIssue(entity: ProviderMediaIdentityIssueEntity): Long

    @Delete
    suspend fun removeProviderIdentity(entity: ProviderMediaIdentityEntity): Int

    @Query("SELECT * FROM local_media_identities WHERE id = :localMediaId LIMIT 1")
    suspend fun getLocalIdentity(localMediaId: String): LocalMediaIdentityEntity?

    @Query("SELECT * FROM provider_media_identities WHERE provider = :provider AND providerMediaId = :providerMediaId AND mediaType = :mediaType LIMIT 1")
    suspend fun findByProviderId(provider: String, providerMediaId: Long, mediaType: String): ProviderMediaIdentityEntity?

    @Query("SELECT * FROM provider_media_identities WHERE localMediaId = :localMediaId AND provider = :provider AND mediaType = :mediaType LIMIT 1")
    suspend fun findProviderForLocal(localMediaId: String, provider: String, mediaType: String): ProviderMediaIdentityEntity?

    @Query("SELECT * FROM provider_media_identities WHERE localMediaId = :localMediaId ORDER BY provider ASC, mediaType ASC, providerMediaId ASC")
    suspend fun getProviderIdentities(localMediaId: String): List<ProviderMediaIdentityEntity>

    @Query("SELECT * FROM provider_media_identity_issues WHERE verificationStatus = :status AND (:mediaType IS NULL OR mediaType = :mediaType) ORDER BY createdAtEpochMillis ASC, id ASC")
    suspend fun listIssues(status: String, mediaType: String?): List<ProviderMediaIdentityIssueEntity>

    @Query("SELECT * FROM provider_media_identity_issues WHERE localMediaId = :localMediaId AND provider = :provider AND providerMediaId = :providerMediaId AND mediaType = :mediaType AND verificationStatus = :status ORDER BY id DESC LIMIT 1")
    suspend fun findIssue(localMediaId: String, provider: String, providerMediaId: Long, mediaType: String, status: String): ProviderMediaIdentityIssueEntity?

    @Query("DELETE FROM provider_media_identities WHERE provider = :provider")
    suspend fun deleteProviderIdentities(provider: String): Int

    @Query("DELETE FROM provider_media_identity_issues WHERE provider = :provider")
    suspend fun deleteIdentityIssues(provider: String): Int

    @Query("DELETE FROM provider_media_identities")
    suspend fun deleteAllProviderIdentities(): Int

    @Query("DELETE FROM provider_media_identity_issues")
    suspend fun deleteAllIdentityIssues(): Int

    @Query("SELECT COUNT(*) FROM local_media_identities")
    suspend fun countLocalIdentities(): Int

    @Query("SELECT COUNT(*) FROM provider_media_identities")
    suspend fun countProviderIdentities(): Int

    @Query("SELECT COUNT(*) FROM provider_media_identity_issues")
    suspend fun countIssues(): Int
}
