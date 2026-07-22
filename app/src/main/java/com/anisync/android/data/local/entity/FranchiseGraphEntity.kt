package com.anisync.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "franchise_graphs")
data class FranchiseGraphEntity(
    @PrimaryKey val rootMediaId: Int,
    val payloadJson: String,
    val fetchedAtEpochMillis: Long
)
