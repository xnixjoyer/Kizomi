package com.anisync.android.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Device-local, read-only MyAnimeList community score cache.
 *
 * The primary key is the AniList media id because every caller navigates by AniList identity.
 * [malId] is either AniList's authoritative cross-id or an explicitly confirmed manual link.
 */
@Entity(
    tableName = "community_scores",
    indices = [
        Index(value = ["malId"]),
        Index(value = ["expiresAtEpochMillis"])
    ]
)
data class CommunityScoreEntity(
    @PrimaryKey val aniListMediaId: Int,
    val malId: Int,
    val score: Double?,
    val scoredBy: Int?,
    val rank: Int?,
    val title: String?,
    val fetchedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val unavailable: Boolean = false,
    val isManualLink: Boolean = false,
    val etag: String? = null,
    val lastModified: String? = null
)
