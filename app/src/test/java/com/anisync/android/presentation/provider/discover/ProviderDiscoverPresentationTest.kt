package com.anisync.android.presentation.provider.discover

import com.anisync.android.presentation.model.MediaListItemPresentation
import com.anisync.android.presentation.model.PresentationMediaType
import com.anisync.android.presentation.model.ProviderMediaIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderDiscoverPresentationTest {
    @Test
    fun `season feed is available for anime and hidden for manga`() {
        val anime = ProviderDiscoverUiState(mediaType = PresentationMediaType.ANIME)
        val manga = ProviderDiscoverUiState(mediaType = PresentationMediaType.MANGA)

        assertTrue(anime.supports(ProviderDiscoverFeed.CURRENT_SEASON))
        assertFalse(manga.supports(ProviderDiscoverFeed.CURRENT_SEASON))
    }

    @Test
    fun `ranking paging keeps typed identity and removes duplicate rows`() {
        val first = animeItem(5114L, "Fullmetal Alchemist")
        val second = animeItem(9253L, "Steins;Gate")
        val initial = ProviderDiscoverUiState(
            selectedFeed = ProviderDiscoverFeed.TOP,
        ).completeInitialLoad(
            loadedItems = listOf(first),
            canLoadMore = true,
        )

        val appended = initial.beginAppend().completeAppend(
            loadedItems = listOf(first, second),
            canLoadMore = false,
        )

        assertEquals(listOf(first, second), appended.items)
        assertEquals(
            ProviderMediaIdentity.MyAnimeList(5114L, PresentationMediaType.ANIME),
            appended.items.first().identity,
        )
        assertFalse(appended.canLoadMore)
    }

    @Test
    fun `season paging failure preserves already loaded content`() {
        val item = animeItem(21L, "One Piece")
        val state = ProviderDiscoverUiState(
            selectedFeed = ProviderDiscoverFeed.CURRENT_SEASON,
        ).completeInitialLoad(
            loadedItems = listOf(item),
            canLoadMore = true,
        ).beginAppend().failAppend(ProviderDiscoverFailure.OFFLINE)

        assertEquals(listOf(item), state.items)
        assertEquals(ProviderDiscoverFailure.OFFLINE, state.failure)
        assertFalse(state.isLoadingMore)
    }

    @Test
    fun `search cache is stale until a successful network page replaces it`() {
        val cached = mangaItem(13L, "One Piece")
        val fresh = mangaItem(2L, "Berserk")
        val loading = ProviderDiscoverUiState(
            mediaType = PresentationMediaType.MANGA,
            query = "manga",
        ).beginInitialLoad(clearContent = true, refreshing = false)
            .showCachedItems(listOf(cached))

        assertTrue(loading.isStale)
        assertEquals(listOf(cached), loading.items)

        val completed = loading.completeInitialLoad(
            loadedItems = listOf(fresh),
            canLoadMore = true,
        )

        assertFalse(completed.isStale)
        assertEquals(listOf(fresh), completed.items)
        assertTrue(completed.canLoadMore)
    }

    private fun animeItem(id: Long, title: String): MediaListItemPresentation =
        MediaListItemPresentation(
            identity = ProviderMediaIdentity.MyAnimeList(id, PresentationMediaType.ANIME),
            title = title,
            coverUrl = null,
        )

    private fun mangaItem(id: Long, title: String): MediaListItemPresentation =
        MediaListItemPresentation(
            identity = ProviderMediaIdentity.MyAnimeList(id, PresentationMediaType.MANGA),
            title = title,
            coverUrl = null,
        )
}
