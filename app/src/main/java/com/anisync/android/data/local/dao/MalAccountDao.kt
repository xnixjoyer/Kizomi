package com.anisync.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.anisync.android.data.local.entity.MalAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MalAccountDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: MalAccountEntity)

    @Update
    suspend fun update(account: MalAccountEntity): Int

    @Query("SELECT * FROM mal_accounts WHERE localAccountId = :localAccountId LIMIT 1")
    suspend fun get(localAccountId: String): MalAccountEntity?

    @Query("SELECT * FROM mal_accounts WHERE localAccountId = :localAccountId LIMIT 1")
    fun observe(localAccountId: String): Flow<MalAccountEntity?>

    @Query("SELECT * FROM mal_accounts ORDER BY isActive DESC, updatedAtEpochMillis DESC, localAccountId ASC")
    suspend fun list(): List<MalAccountEntity>

    @Query("SELECT * FROM mal_accounts ORDER BY isActive DESC, updatedAtEpochMillis DESC, localAccountId ASC")
    fun observeAll(): Flow<List<MalAccountEntity>>

    @Query("SELECT * FROM mal_accounts WHERE isActive = 1 LIMIT 1")
    suspend fun active(): MalAccountEntity?

    @Query("UPDATE mal_accounts SET isActive = 0, updatedAtEpochMillis = :updatedAtEpochMillis WHERE isActive = 1")
    suspend fun clearActive(updatedAtEpochMillis: Long): Int

    @Query("UPDATE mal_accounts SET isActive = 1, updatedAtEpochMillis = :updatedAtEpochMillis WHERE localAccountId = :localAccountId")
    suspend fun activate(localAccountId: String, updatedAtEpochMillis: Long): Int

    @Transaction
    suspend fun selectActive(localAccountId: String?, updatedAtEpochMillis: Long): Boolean {
        if (localAccountId != null && get(localAccountId) == null) return false
        clearActive(updatedAtEpochMillis)
        return localAccountId == null || activate(localAccountId, updatedAtEpochMillis) == 1
    }

    @Query("DELETE FROM mal_accounts WHERE localAccountId = :localAccountId")
    suspend fun delete(localAccountId: String): Int
}
