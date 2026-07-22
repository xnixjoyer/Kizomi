package com.anisync.android.presentation.library

import com.anisync.android.domain.LibraryEntry
import com.anisync.android.domain.LibraryStatus
import com.anisync.android.type.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryAiringTest {
    @Test
    fun `next AniList episode determines episodes behind`() {
        assertEquals(1, calculateEpisodesBehind(entry(progress = 4, nextEpisode = 6), MediaType.ANIME))
        assertNull(calculateEpisodesBehind(entry(progress = 5, nextEpisode = 6), MediaType.ANIME))
    }

    @Test
    fun `AniList total is fallback when no next episode exists`() {
        assertEquals(2, calculateEpisodesBehind(entry(progress = 10, totalEpisodes = 12), MediaType.ANIME))
    }

    @Test
    fun `behind badge is limited to current anime entries`() {
        assertNull(calculateEpisodesBehind(entry(progress = 1, nextEpisode = 5, status = LibraryStatus.PLANNING), MediaType.ANIME))
        assertNull(calculateEpisodesBehind(entry(progress = 1, nextEpisode = 5), MediaType.MANGA))
    }

    @Test
    fun `absolute AniList timestamp drives dynamic countdown`() {
        assertEquals(600, calculateAniListTimeUntilAiring(entry(nextEpisode = 2, nextTime = 1_600), 1_000))
        assertNull(calculateAniListTimeUntilAiring(entry(nextEpisode = 2, nextTime = 900), 1_000))
    }

    @Test
    fun `relative AniList countdown remains fallback when absolute time is absent`() {
        assertEquals(120, calculateAniListTimeUntilAiring(entry(nextEpisode = 2, timeUntil = 120), 1_000))
        assertNull(calculateAniListTimeUntilAiring(entry(nextEpisode = 2, timeUntil = 0), 1_000))
    }

    private fun entry(
        progress: Int = 0,
        totalEpisodes: Int? = null,
        nextEpisode: Int? = null,
        timeUntil: Int? = null,
        nextTime: Long? = null,
        status: LibraryStatus = LibraryStatus.CURRENT
    ) = LibraryEntry(
        id = 1,
        mediaId = 1,
        titleRomaji = null,
        titleEnglish = null,
        titleNative = null,
        titleUserPreferred = "Test",
        coverUrl = null,
        progress = progress,
        totalEpisodes = totalEpisodes,
        totalChapters = null,
        totalVolumes = null,
        type = MediaType.ANIME,
        status = status,
        nextAiringEpisode = nextEpisode,
        timeUntilAiring = timeUntil,
        nextAiringEpisodeTime = nextTime
    )
}
