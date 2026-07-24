package com.anisync.android.presentation.mal

import com.anisync.android.data.mal.api.MalCatalogMedia
import com.anisync.android.data.mal.api.MalMediaKey
import com.anisync.android.data.mal.api.MalRelatedMedia
import com.anisync.android.domain.tracking.TrackingDesiredState
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingStatus
import com.anisync.android.presentation.model.PresentationMediaType
import com.anisync.android.presentation.model.ProviderMediaIdentity
import com.anisync.android.presentation.provider.details.ProviderDetailsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MalCatalogPresentationAdaptersTest {
    @Test
    fun `anime fixture maps documented catalogue and details fields`() {
        val media = MalCatalogMedia(
            key = MalMediaKey(TrackingMediaType.ANIME, 5114L),
            title = "Fullmetal Alchemist: Brotherhood",
            alternativeTitles = listOf("Hagane no Renkinjutsushi", "  "),
            synopsis = "Two brothers search for the Philosopher's Stone.",
            pictureMedium = "https://cdn.myanimelist.net/medium.jpg",
            pictureLarge = "https://cdn.myanimelist.net/large.jpg",
            pictureGallery = listOf("https://cdn.myanimelist.net/hero.jpg"),
            meanScore = 9.1,
            rank = 1,
            popularity = 3,
            mediaStatus = "finished_airing",
            mediaFormat = "tv",
            startDate = "2009-04-05",
            endDate = "2010-07-04",
            episodeCount = 64,
            genres = listOf("Action", "Action", "Adventure"),
            background = "Background",
            listState = TrackingDesiredState(
                status = TrackingStatus.COMPLETED,
                progress = 64,
                score100 = 90.0,
            ),
            related = listOf(
                MalRelatedMedia(
                    key = MalMediaKey(TrackingMediaType.MANGA, 25L),
                    title = "Fullmetal Alchemist",
                    pictureUrl = null,
                    relationship = "source",
                ),
            ),
            recommendations = emptyList(),
            isDetailed = true,
            fetchedAtEpochMillis = 1L,
        )

        val discover = media.toDiscoverPresentation()
        val details = media.toDetailsPresentation()

        assertEquals(
            ProviderMediaIdentity.MyAnimeList(5114L, PresentationMediaType.ANIME),
            discover.identity,
        )
        assertEquals(64, discover.progress)
        assertEquals(64, discover.total)
        assertEquals("Tv", details.format)
        assertEquals("Finished airing", details.status)
        assertEquals(listOf("Action", "Adventure"), details.genres)
        assertEquals(
            ProviderMediaIdentity.MyAnimeList(25L, PresentationMediaType.MANGA),
            details.relations.single().identity,
        )
        assertTrue(
            ProviderDetailsSection.RELATIONS in details.visibleSections(editAvailable = true),
        )
    }

    @Test
    fun `manga fixture preserves manga identity and chapter progress`() {
        val media = MalCatalogMedia(
            key = MalMediaKey(TrackingMediaType.MANGA, 13L),
            title = "One Piece",
            chapterCount = 1100,
            volumeCount = 100,
            listState = TrackingDesiredState(
                status = TrackingStatus.CURRENT,
                progress = 500,
                progressSecondary = 50,
            ),
            fetchedAtEpochMillis = 2L,
        )

        val discover = media.toDiscoverPresentation()
        val details = media.toDetailsPresentation()

        assertEquals(
            ProviderMediaIdentity.MyAnimeList(13L, PresentationMediaType.MANGA),
            discover.identity,
        )
        assertEquals(500, discover.progress)
        assertEquals(1100, discover.total)
        assertEquals(50, details.listState?.secondaryProgress)
        assertEquals(100, details.volumeCount)
    }

    @Test
    fun `null and missing fields remain absent instead of creating empty sections`() {
        val media = MalCatalogMedia(
            key = MalMediaKey(TrackingMediaType.ANIME, 1L),
            title = "Unknown",
            alternativeTitles = listOf("Unknown", ""),
            synopsis = "  ",
            mediaStatus = "",
            mediaFormat = null,
            fetchedAtEpochMillis = 3L,
        )

        val details = media.toDetailsPresentation()

        assertTrue(details.alternativeTitles.isEmpty())
        assertNull(details.synopsis)
        assertNull(details.status)
        assertFalse(ProviderDetailsSection.SYNOPSIS in details.visibleSections(false))
        assertFalse(ProviderDetailsSection.FACTS in details.visibleSections(false))
    }
}
