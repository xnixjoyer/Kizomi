package com.anisync.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anisync.android.data.local.entity.CommunityScoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommunityScoreDao {
    @Query("SELECT * FROM community_scores WHERE aniListMediaId IN (:aniListMediaIds)")
    fun observeByAniListIds(aniListMediaIds: List<Int>): Flow<List<CommunityScoreEntity>>

    @Query("SELECT * FROM community_scores WHERE aniListMediaId = :aniListMediaId LIMIT 1")
    fun observeByAniListId(aniListMediaId: Int): Flow<CommunityScoreEntity?>

    @Query("SELECT * FROM community_scores WHERE aniListMediaId = :aniListMediaId LIMIT 1")
    suspend fun getByAniListId(aniListMediaId: Int): CommunityScoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CommunityScoreEntity)

    @Query("SELECT COUNT(*) FROM community_scores")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM community_scores WHERE expiresAtEpochMillis > :now")
    suspend fun countFresh(now: Long): Int

    @Query("SELECT COUNT(*) FROM community_scores WHERE expiresAtEpochMillis <= :now")
    suspend fun countStale(now: Long): Int

    @Query("SELECT COUNT(*) FROM community_scores WHERE unavailable = 1")
    suspend fun countUnavailable(): Int

    @Query("DELETE FROM community_scores")
    suspend fun clear()
}
