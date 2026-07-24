package com.anisync.android.presentation.provider.library

import com.anisync.android.data.mal.api.MalLibraryPresentationRecord
import com.anisync.android.domain.tracking.TrackingDesiredState
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingStatus
import com.anisync.android.presentation.model.PresentationMediaType
import com.anisync.android.presentation.model.ProviderMediaIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MalLibraryPresentationAdapterTest {
    @Test
    fun `anime and manga records keep typed MAL identity and progress totals`() {
        val anime = MalLibraryPresentationAdapter.map(
            record(
                id = 11L,
                type = TrackingMediaType.ANIME,
                status = TrackingStatus.CURRENT,
                progress = 3,
                totalPrimary = 12,
            )
        )
        val manga = MalLibraryPresentationAdapter.map(
            record(
                id = 22L,
                type = TrackingMediaType.MANGA,
                status = TrackingStatus.PAUSED,
                progress = 8,
                secondaryProgress = 2,
                totalPrimary = 40,
                totalSecondary = 9,
            )
        )

        assertEquals(
            ProviderMediaIdentity.MyAnimeList(11L, PresentationMediaType.ANIME),
            anime.identity,
        )
        assertEquals(3, anime.progress)
        assertEquals(12, anime.normalizedTotal)
        assertEquals(
            ProviderMediaIdentity.MyAnimeList(22L, PresentationMediaType.MANGA),
            manga.identity,
        )
        assertEquals(8, manga.progress)
        assertEquals(2, manga.secondaryProgress)
        assertEquals(40, manga.normalizedTotal)
        assertEquals(9, manga.normalizedSecondaryTotal)
    }

    @Test
    fun `every MAL status maps to the matching shared status`() {
        TrackingStatus.entries.forEach { status ->
            val mapped = MalLibraryPresentationAdapter.map(
                record(id = status.ordinal + 1L, status = status)
            )
            assertEquals(status.name, mapped.status.name)
        }
    }

    @Test
    fun `zero totals stay unknown while score dates and nulls are preserved`() {
        val item = MalLibraryPresentationAdapter.map(
            record(
                id = 33L,
                progress = 0,
                totalPrimary = 0,
                score100 = null,
                startedAt = "2026-01-02",
                completedAt = null,
            )
        )

        assertNull(item.normalizedTotal)
        assertNull(item.score100)
        assertEquals("2026-01-02", item.startedAt)
        assertNull(item.completedAt)
    }

    @Test
    fun `search status media type and sort reduce deterministically`() {
        val records = listOf(
            record(
                id = 1L,
                title = "Zulu",
                alternatives = listOf("Hidden Match"),
                status = TrackingStatus.CURRENT,
                score100 = 60.0,
                updatedAt = 20L,
            ),
            record(
                id = 2L,
                title = "Alpha",
                status = TrackingStatus.COMPLETED,
                score100 = 90.0,
                updatedAt = 10L,
            ),
            record(
                id = 3L,
                title = "Manga Hidden Match",
                type = TrackingMediaType.MANGA,
                status = TrackingStatus.CURRENT,
                score100 = 100.0,
            ),
        )
        val query = ProviderLibraryQuery(
            mediaType = PresentationMediaType.ANIME,
            statuses = setOf(ProviderLibraryStatus.CURRENT),
            searchQuery = "hidden match",
            sort = ProviderLibrarySort.SCORE,
            ascending = false,
        )

        val snapshot = MalLibraryPresentationAdapter.snapshot(
            records = records,
            query = query,
            nowEpochMillis = 100L,
            staleAfterMillis = 1_000L,
        )

        assertEquals(listOf(1L), snapshot.visibleItems.map { (it.identity as ProviderMediaIdentity.MyAnimeList).malId })
        assertEquals(setOf(ProviderLibraryStatus.CURRENT), snapshot.groupedItems.keys)
        assertFalse(snapshot.hasStaleContent)
    }

    @Test
    fun `refresh error empty and stale flags remain independent`() {
        val stale = MalLibraryPresentationAdapter.snapshot(
            records = listOf(record(id = 4L, fetchedAt = 1L)),
            query = ProviderLibraryQuery(),
            isRefreshing = true,
            errorMessage = "offline",
            nowEpochMillis = 10_000L,
            staleAfterMillis = 100L,
        )
        val empty = MalLibraryPresentationAdapter.snapshot(
            records = emptyList(),
            query = ProviderLibraryQuery(),
        )

        assertTrue(stale.isRefreshing)
        assertEquals("offline", stale.errorMessage)
        assertTrue(stale.hasStaleContent)
        assertTrue(empty.isEmpty)
        assertFalse(empty.isInitialLoading)
    }

    private fun record(
        id: Long,
        title: String = "Title $id",
        alternatives: List<String> = emptyList(),
        type: TrackingMediaType = TrackingMediaType.ANIME,
        status: TrackingStatus = TrackingStatus.PLANNING,
        progress: Int = 0,
        secondaryProgress: Int? = null,
        totalPrimary: Int? = null,
        totalSecondary: Int? = null,
        score100: Double? = null,
        startedAt: String? = null,
        completedAt: String? = null,
        updatedAt: Long? = null,
        fetchedAt: Long = 50L,
    ) = MalLibraryPresentationRecord(
        localMediaId = "local-$id",
        malId = id,
        mediaType = type,
        title = title,
        alternativeTitles = alternatives,
        coverUrl = null,
        state = TrackingDesiredState(
            status = status,
            progress = progress,
            progressSecondary = secondaryProgress,
            score100 = score100,
            startedAt = startedAt,
            completedAt = completedAt,
        ),
        totalPrimary = totalPrimary,
        totalSecondary = totalSecondary,
        mediaStartDate = null,
        providerUpdatedAtEpochMillis = updatedAt,
        fetchedAtEpochMillis = fetchedAt,
    )
}
