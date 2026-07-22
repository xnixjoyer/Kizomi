package com.anisync.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anisync.android.data.local.entity.FranchiseGraphEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FranchiseGraphDao {
    @Query("SELECT * FROM franchise_graphs WHERE rootMediaId = :rootMediaId LIMIT 1")
    fun observe(rootMediaId: Int): Flow<FranchiseGraphEntity?>

    @Query("SELECT * FROM franchise_graphs WHERE rootMediaId = :rootMediaId LIMIT 1")
    suspend fun get(rootMediaId: Int): FranchiseGraphEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FranchiseGraphEntity)

    @Query("SELECT COUNT(*) FROM franchise_graphs")
    fun observeCount(): Flow<Int>

    @Query("SELECT MAX(fetchedAtEpochMillis) FROM franchise_graphs")
    fun observeLatestFetch(): Flow<Long?>
}
