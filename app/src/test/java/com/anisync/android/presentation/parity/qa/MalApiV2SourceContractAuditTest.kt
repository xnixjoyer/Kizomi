package com.anisync.android.presentation.parity.qa

import com.anisync.android.data.mal.api.MalCatalogRequestFactory
import com.anisync.android.data.mal.api.MalListRequestFactory
import com.anisync.android.data.mal.api.MalSeason
import com.anisync.android.domain.tracking.TrackingMediaType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Additive audit guards derived from the owner-supplied official MAL API-v2 export recorded in
 * docs/mal-parity/MAL_API_V2_AI_REFERENCE.md on the integration branch.
 *
 * The two "known gap" tests intentionally describe current transport mismatches. Passing them does
 * not approve those mismatches; it prevents them from being silently omitted from the QA report.
 */
class MalApiV2SourceContractAuditTest {
    @Test
    fun `source confirmed catalogue endpoints ranking seasonal sorts and conservative limits`() {
        val factory = MalCatalogRequestFactory()

        val animeSearch = factory.search(TrackingMediaType.ANIME, "test", 100, 0)
        assertEquals("/v2/anime", animeSearch.url.encodedPath)
        assertEquals("100", animeSearch.url.queryParameter("limit"))
        expectIllegalArgument {
            factory.search(TrackingMediaType.ANIME, "test", 101, 0)
        }

        val animePopular = factory.ranking(
            mediaType = TrackingMediaType.ANIME,
            rankingType = "bypopularity",
            limit = 100,
            offset = 0,
        )
        assertEquals("/v2/anime/ranking", animePopular.url.encodedPath)
        assertEquals("bypopularity", animePopular.url.queryParameter("ranking_type"))

        val mangaPopular = factory.ranking(
            mediaType = TrackingMediaType.MANGA,
            rankingType = "bypopularity",
            limit = 100,
            offset = 0,
        )
        assertEquals("/v2/manga/ranking", mangaPopular.url.encodedPath)

        listOf("anime_score", "anime_num_list_users").forEach { sort ->
            val seasonal = factory.seasonal(2026, MalSeason.SUMMER, sort, 100, 0)
            assertEquals("/v2/anime/season/2026/summer", seasonal.url.encodedPath)
            assertEquals(sort, seasonal.url.queryParameter("sort"))
        }
        expectIllegalArgument {
            factory.seasonal(2026, MalSeason.SUMMER, "unsupported_sort", 100, 0)
        }
    }

    @Test
    fun `source confirmed list endpoints retain the documented maximum limit`() {
        val factory = MalListRequestFactory()

        val anime = factory.firstPage(TrackingMediaType.ANIME, limit = 1_000)
        assertEquals("/v2/users/@me/animelist", anime.url.encodedPath)
        assertEquals("1000", anime.url.queryParameter("limit"))

        val manga = factory.firstPage(TrackingMediaType.MANGA, limit = 1_000)
        assertEquals("/v2/users/@me/mangalist", manga.url.encodedPath)

        expectIllegalArgument {
            factory.firstPage(TrackingMediaType.ANIME, limit = 1_001)
        }
    }

    @Test
    fun `catalogue field union exposes only the recorded cross media source gaps`() {
        val factory = MalCatalogRequestFactory()
        val animeFields = factory.details(TrackingMediaType.ANIME, 1L).requestedFields()
        val mangaFields = factory.details(TrackingMediaType.MANGA, 1L).requestedFields()

        assertEquals(setOf("num_chapters", "num_volumes"), animeFields - SOURCE_ANIME_FIELDS)
        assertEquals(setOf("num_episodes"), mangaFields - SOURCE_MANGA_FIELDS)
    }

    @Test
    fun `tracking source keeps unconfirmed date writes and delete 404 handling visible as audit gaps`() {
        val source = readSource(
            "app/src/main/java/com/anisync/android/data/tracking/MalTrackingProviderAdapter.kt",
        )

        assertTrue(source.contains("add(\"start_date\""))
        assertTrue(source.contains("add(\"finish_date\""))
        assertTrue(
            Regex(
                "statusCode\\s*==\\s*404\\s*->\\s*TrackingDeliveryResult\\.TerminalFailure",
                RegexOption.DOT_MATCHES_ALL,
            ).containsMatchIn(source),
        )
    }

    private fun okhttp3.Request.requestedFields(): Set<String> =
        url.queryParameter("fields")
            .orEmpty()
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()

    private fun expectIllegalArgument(block: () -> Unit) {
        var thrown = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            thrown = true
        }
        assertTrue("Expected IllegalArgumentException", thrown)
    }

    private fun readSource(path: String): String = File(repositoryRoot(), path).readText()

    private fun repositoryRoot(): File =
        generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }

    private companion object {
        val SOURCE_ANIME_FIELDS = setOf(
            "id", "title", "main_picture", "alternative_titles", "start_date", "end_date",
            "synopsis", "mean", "rank", "popularity", "status", "media_type", "genres",
            "my_list_status", "num_episodes", "pictures", "background", "related_anime",
            "related_manga", "recommendations",
        )
        val SOURCE_MANGA_FIELDS = setOf(
            "id", "title", "main_picture", "alternative_titles", "start_date", "end_date",
            "synopsis", "mean", "rank", "popularity", "status", "media_type", "genres",
            "my_list_status", "num_volumes", "num_chapters", "pictures", "background",
            "related_anime", "related_manga", "recommendations",
        )
    }
}
