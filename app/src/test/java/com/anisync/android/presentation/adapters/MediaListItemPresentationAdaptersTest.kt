package com.anisync.android.presentation.adapters

import com.anisync.android.data.TitleLanguage
import com.anisync.android.data.mal.api.MalLibraryItem
import com.anisync.android.domain.LibraryEntry
import com.anisync.android.domain.LibraryStatus
import com.anisync.android.domain.tracking.TrackingDesiredState
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingStatus
import com.anisync.android.presentation.model.PresentationMediaType
import com.anisync.android.presentation.model.ProviderMediaIdentity
import com.anisync.android.type.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaListItemPresentationAdaptersTest {
    @Test
    fun `AniList anime mapping preserves identity title cover progress and episode total`() {
        val entry = aniListEntry(
            mediaId = 101,
            progress = 4,
            totalEpisodes = 12,
            totalChapters = 99,
        )

        val item = entry.toMediaListItemPresentation(
            mediaType = MediaType.ANIME,
            titleLanguage = TitleLanguage.ENGLISH,
            resolvedCoverUrl = "https://images.test/anime.jpg",
        )

        assertEquals(
            ProviderMediaIdentity.AniList(101, PresentationMediaType.ANIME),
            item.identity,
        )
        assertEquals("English fixture", item.title)
        assertEquals("https://images.test/anime.jpg", item.coverUrl)
        assertEquals(4, item.progress)
        assertEquals(12, item.total)
    }

    @Test
    fun `AniList manga mapping uses native title and chapter total`() {
        val entry = aniListEntry(
            mediaId = 202,
            progress = 8,
            totalEpisodes = 24,
            totalChapters = 40,
        )

        val item = entry.toMediaListItemPresentation(
            mediaType = MediaType.MANGA,
            titleLanguage = TitleLanguage.NATIVE,
            resolvedCoverUrl = null,
        )

        assertEquals(
            ProviderMediaIdentity.AniList(202, PresentationMediaType.MANGA),
            item.identity,
        )
        assertEquals("ネイティブ", item.title)
        assertNull(item.coverUrl)
        assertEquals(40, item.total)
    }

    @Test
    fun `MAL anime and manga mapping preserves provider-native type and id`() {
        val anime = malLibraryItem(
            malId = 5114L,
            mediaType = TrackingMediaType.ANIME,
            progress = 20,
            coverUrl = "https://images.test/mal-anime.jpg",
        ).toMediaListItemPresentation()
        val manga = malLibraryItem(
            malId = 13L,
            mediaType = TrackingMediaType.MANGA,
            progress = 7,
            coverUrl = null,
        ).toMediaListItemPresentation()

        assertEquals(
            ProviderMediaIdentity.MyAnimeList(5114L, PresentationMediaType.ANIME),
            anime.identity,
        )
        assertEquals(20, anime.progress)
        assertEquals("https://images.test/mal-anime.jpg", anime.coverUrl)
        assertNull(anime.total)

        assertEquals(
            ProviderMediaIdentity.MyAnimeList(13L, PresentationMediaType.MANGA),
            manga.identity,
        )
        assertEquals(7, manga.progress)
        assertNull(manga.coverUrl)
        assertNull(manga.total)
    }

    private fun aniListEntry(
        mediaId: Int,
        progress: Int,
        totalEpisodes: Int?,
        totalChapters: Int?,
    ) = LibraryEntry(
        id = mediaId + 1_000,
        mediaId = mediaId,
        titleRomaji = "Romaji fixture",
        titleEnglish = "English fixture",
        titleNative = "ネイティブ",
        titleUserPreferred = "Preferred fixture",
        coverUrl = "https://images.test/fallback.jpg",
        progress = progress,
        totalEpisodes = totalEpisodes,
        totalChapters = totalChapters,
        totalVolumes = null,
        type = null,
        status = LibraryStatus.CURRENT,
    )

    private fun malLibraryItem(
        malId: Long,
        mediaType: TrackingMediaType,
        progress: Int,
        coverUrl: String?,
    ) = MalLibraryItem(
        localMediaId = "local-$malId-${mediaType.name}",
        malId = malId,
        mediaType = mediaType,
        title = "MAL fixture $malId",
        coverUrl = coverUrl,
        state = TrackingDesiredState(
            status = TrackingStatus.CURRENT,
            progress = progress,
        ),
        fetchedAtEpochMillis = 1234L,
    )
}
