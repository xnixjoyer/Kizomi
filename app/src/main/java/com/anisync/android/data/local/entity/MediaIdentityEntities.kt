package com.anisync.android.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "local_media_identities")
data class LocalMediaIdentityEntity(
    @PrimaryKey val id: String,
    val mediaType: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "provider_media_identities",
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
        Index(value = ["provider", "providerMediaId", "mediaType"], unique = true),
        Index(value = ["localMediaId", "provider", "mediaType"], unique = true),
        Index(value = ["localMediaId"]),
    ],
)
data class ProviderMediaIdentityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val localMediaId: String,
    val provider: String,
    val providerMediaId: Long,
    val mediaType: String,
    val mappingSource: String,
    val verificationStatus: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "provider_media_identity_issues",
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
        Index(value = ["verificationStatus", "provider", "mediaType"]),
        Index(value = ["provider", "providerMediaId", "mediaType"]),
    ],
)
data class ProviderMediaIdentityIssueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val localMediaId: String?,
    val provider: String,
    val providerMediaId: Long?,
    val mediaType: String?,
    val mappingSource: String,
    val verificationStatus: String,
    val reason: String,
    val sourceTable: String?,
    val sourceRowKey: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
