package com.anisync.android.domain

/**
 * A single anime episode airing entry used by the shared calendar presentation.
 * Provider-specific metadata is converted before it reaches this public model.
 */
data class AiringEpisode(
    val id: Int,
    val episode: Int,
    val airingAt: Long,
    val mediaId: Int,
    val titleRomaji: String?,
    val titleEnglish: String?,
    val titleNative: String?,
    val titleUserPreferred: String,
    val coverImageUrl: String?,
    val format: String?,
    val averageScore: Int?,
    val isOnList: Boolean,
    val listStatus: LibraryStatus?,
    val isAdult: Boolean
)
