package com.anisync.android.presentation.library

import com.anisync.android.domain.LibraryEntry
import com.anisync.android.domain.LibraryStatus
import com.anisync.android.type.MediaType

/**
 * Original AniList Watching-library release semantics.
 *
 * AniList's next episode N means N-1 episodes have already aired. When AniList has no next-airing
 * value (for example a finished series), the known AniList total is the best available released count.
 */
internal fun calculateEpisodesBehind(entry: LibraryEntry, mediaType: MediaType): Int? {
    if (mediaType != MediaType.ANIME || entry.status != LibraryStatus.CURRENT) return null
    val releasedEpisodes = entry.nextAiringEpisode?.minus(1) ?: entry.totalEpisodes ?: return null
    return (releasedEpisodes - entry.progress).takeIf { it > 0 }
}

/**
 * Computes the current AniList countdown without mutating or replacing the cached AniList fields.
 */
internal fun calculateAniListTimeUntilAiring(
    entry: LibraryEntry,
    nowEpochSeconds: Long
): Int? {
    val absolute = entry.nextAiringEpisodeTime
    if (absolute != null) {
        val effectiveNow = nowEpochSeconds.takeIf { it > 0L } ?: System.currentTimeMillis() / 1000L
        return (absolute - effectiveNow)
            .takeIf { it > 0L }
            ?.coerceAtMost(Int.MAX_VALUE.toLong())
            ?.toInt()
    }
    return entry.timeUntilAiring?.takeIf { it > 0 }
}
