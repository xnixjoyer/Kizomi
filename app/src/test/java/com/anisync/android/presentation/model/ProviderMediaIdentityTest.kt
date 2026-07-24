package com.anisync.android.presentation.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ProviderMediaIdentityTest {
    @Test
    fun `equal numeric values never alias across providers`() {
        val aniList = ProviderMediaIdentity.AniList(
            mediaId = 5114,
            mediaType = PresentationMediaType.ANIME,
        )
        val mal = ProviderMediaIdentity.MyAnimeList(
            malId = 5114L,
            mediaType = PresentationMediaType.ANIME,
        )

        assertNotEquals(aniList, mal)
        assertNotEquals(aniList.stableKey, mal.stableKey)
        assertEquals("ANILIST:ANIME:5114", aniList.stableKey)
        assertEquals("MYANIMELIST:ANIME:5114", mal.stableKey)
    }

    @Test
    fun `media type remains part of provider identity`() {
        val anime = ProviderMediaIdentity.MyAnimeList(13L, PresentationMediaType.ANIME)
        val manga = ProviderMediaIdentity.MyAnimeList(13L, PresentationMediaType.MANGA)

        assertNotEquals(anime, manga)
        assertNotEquals(anime.stableKey, manga.stableKey)
    }

    @Test
    fun `provider identities reject missing or fake ids`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProviderMediaIdentity.AniList(0, PresentationMediaType.ANIME)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProviderMediaIdentity.MyAnimeList(-1L, PresentationMediaType.MANGA)
        }
    }

    @Test
    fun `presentation normalizes progress and unknown totals without inventing data`() {
        val item = MediaListItemPresentation(
            identity = ProviderMediaIdentity.AniList(1, PresentationMediaType.ANIME),
            title = "Fixture",
            coverUrl = null,
            progress = -3,
            total = 0,
        )

        assertEquals(0, item.normalizedProgress)
        assertNull(item.normalizedTotal)
    }
}
