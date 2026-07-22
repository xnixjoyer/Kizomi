package com.anisync.android.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * MyAnimeList account metadata only.
 *
 * OAuth credentials are deliberately stored in the dedicated encrypted MAL token vault. The token
 * reference fields below are opaque generation pointers and never contain access or refresh tokens.
 */
@Entity(
    tableName = "mal_accounts",
    indices = [
        Index(value = ["malUserId"], unique = true),
        Index(value = ["isActive"]),
        Index(value = ["updatedAtEpochMillis"]),
    ],
)
data class MalAccountEntity(
    @PrimaryKey
    val localAccountId: String,
    val provider: String,
    val malUserId: Long?,
    val username: String?,
    val displayName: String?,
    val avatarUrl: String?,
    val accessTokenRef: String?,
    val refreshTokenRef: String?,
    val tokenGeneration: Long,
    val tokenExpiresAtEpochMillis: Long?,
    val scopes: String,
    val tokenStatus: String,
    val isActive: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
