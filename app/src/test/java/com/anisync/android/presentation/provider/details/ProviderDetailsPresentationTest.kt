package com.anisync.android.presentation.provider.details

import com.anisync.android.presentation.model.PresentationMediaType
import com.anisync.android.presentation.model.ProviderMediaIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderDetailsPresentationTest {
    @Test
    fun `missing optional fields hide every optional section`() {
        val details = ProviderMediaDetailsPresentation(
            identity = animeIdentity(1L),
            title = "Title",
        )

        assertTrue(details.visibleSections(editAvailable = false).isEmpty())
    }

    @Test
    fun `documented fields expose only sections backed by data`() {
        val related = ProviderRelatedMediaPresentation(
            identity = mangaIdentity(13L),
            title = "Related manga",
            coverUrl = null,
            relationship = "Adaptation",
        )
        val details = ProviderMediaDetailsPresentation(
            identity = animeIdentity(5114L),
            title = "Fullmetal Alchemist",
            alternativeTitles = listOf("Hagane no Renkinjutsushi"),
            synopsis = "Synopsis",
            background = "Background",
            format = "Tv",
            status = "Finished airing",
            startDate = "2009-04-05",
            endDate = "2010-07-04",
            episodeCount = 64,
            score = 9.1,
            rank = 1,
            popularity = 3,
            genres = listOf("Action"),
            creators = listOf("Creator"),
            studios = listOf("Studio"),
            listState = ProviderDetailsListState(
                status = "Completed",
                progress = 64,
                score100 = 90.0,
            ),
            relations = listOf(related),
            recommendations = listOf(related),
        )

        val visible = details.visibleSections(editAvailable = true)

        assertEquals(ProviderDetailsSection.entries.toSet(), visible)
        assertEquals(mangaIdentity(13L), details.relations.single().identity)
    }

    @Test
    fun `edit entry point can be shown before a list state exists`() {
        val details = ProviderMediaDetailsPresentation(
            identity = mangaIdentity(2L),
            title = "Manga",
        )

        assertFalse(
            ProviderDetailsSection.LIST_STATE in details.visibleSections(editAvailable = false),
        )
        assertTrue(
            ProviderDetailsSection.LIST_STATE in details.visibleSections(editAvailable = true),
        )
    }

    private fun animeIdentity(id: Long): ProviderMediaIdentity.MyAnimeList =
        ProviderMediaIdentity.MyAnimeList(id, PresentationMediaType.ANIME)

    private fun mangaIdentity(id: Long): ProviderMediaIdentity.MyAnimeList =
        ProviderMediaIdentity.MyAnimeList(id, PresentationMediaType.MANGA)
}
