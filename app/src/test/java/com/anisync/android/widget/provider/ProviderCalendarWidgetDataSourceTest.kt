package com.anisync.android.widget.provider

import com.anisync.android.domain.calendar.provider.ProviderCalendarEntry
import com.anisync.android.domain.calendar.provider.ProviderCalendarMediaType
import com.anisync.android.domain.calendar.provider.ProviderCalendarPrecision
import com.anisync.android.domain.provider.ActiveProvider
import com.anisync.android.domain.provider.ProviderRuntimeState
import com.anisync.android.domain.provider.ProviderTransitionPhase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCalendarWidgetDataSourceTest {
    @Test
    fun `widget reads only snapshot for the active provider`() = runTest {
        val store = MemoryStore(
            ProviderCalendarSnapshot(
                provider = ActiveProvider.MAL_ONLY,
                generatedAtEpochMillis = 1_000L,
                entries = listOf(entry(ActiveProvider.MAL_ONLY)),
            )
        )
        val source = ProviderCalendarWidgetDataSource(
            runtimeState = { ProviderRuntimeState(ActiveProvider.MAL_ONLY) },
            snapshotStore = store,
            nowEpochMillis = { 2_000L },
        )

        val result = source.load()

        assertTrue(result is ProviderCalendarWidgetState.Content)
        assertEquals(ActiveProvider.MAL_ONLY, (result as ProviderCalendarWidgetState.Content).provider)
        assertEquals(1, store.reads)
    }

    @Test
    fun `provider mismatch never exposes stale alternate provider snapshot`() = runTest {
        val store = MemoryStore(
            ProviderCalendarSnapshot(
                provider = ActiveProvider.MAL_ONLY,
                generatedAtEpochMillis = 1_000L,
                entries = listOf(entry(ActiveProvider.MAL_ONLY)),
            )
        )
        val source = ProviderCalendarWidgetDataSource(
            runtimeState = { ProviderRuntimeState(ActiveProvider.ANILIST_ONLY) },
            snapshotStore = store,
            nowEpochMillis = { 2_000L },
        )

        val result = source.load()

        assertEquals(
            ProviderCalendarWidgetUnavailableReason.NO_ACTIVE_PROVIDER_SNAPSHOT,
            (result as ProviderCalendarWidgetState.Unavailable).reason,
        )
    }

    @Test
    fun `unconfigured widget performs no snapshot read`() = runTest {
        val store = MemoryStore(null)
        val source = ProviderCalendarWidgetDataSource(
            runtimeState = { ProviderRuntimeState() },
            snapshotStore = store,
            nowEpochMillis = { 2_000L },
        )

        val result = source.load()

        assertEquals(
            ProviderCalendarWidgetUnavailableReason.UNCONFIGURED,
            (result as ProviderCalendarWidgetState.Unavailable).reason,
        )
        assertEquals(0, store.reads)
    }

    @Test
    fun `provider transition performs no snapshot read`() = runTest {
        val store = MemoryStore(null)
        val source = ProviderCalendarWidgetDataSource(
            runtimeState = {
                ProviderRuntimeState(
                    transitionPhase = ProviderTransitionPhase.PURGING,
                )
            },
            snapshotStore = store,
            nowEpochMillis = { 2_000L },
        )

        val result = source.load()

        assertEquals(
            ProviderCalendarWidgetUnavailableReason.PROVIDER_TRANSITION,
            (result as ProviderCalendarWidgetState.Unavailable).reason,
        )
        assertEquals(0, store.reads)
    }

    @Test
    fun `stale snapshot is explicit and does not trigger network fallback`() = runTest {
        val store = MemoryStore(
            ProviderCalendarSnapshot(
                provider = ActiveProvider.MAL_ONLY,
                generatedAtEpochMillis = 0L,
                entries = listOf(entry(ActiveProvider.MAL_ONLY)),
            )
        )
        val source = ProviderCalendarWidgetDataSource(
            runtimeState = { ProviderRuntimeState(ActiveProvider.MAL_ONLY) },
            snapshotStore = store,
            nowEpochMillis = { 10_000L },
        )

        val result = source.load(maxSnapshotAgeMillis = 100L)

        assertEquals(
            ProviderCalendarWidgetUnavailableReason.STALE_SNAPSHOT,
            (result as ProviderCalendarWidgetState.Unavailable).reason,
        )
    }

    private fun entry(provider: ActiveProvider) = ProviderCalendarEntry(
        provider = provider,
        providerMediaId = 42L,
        mediaType = ProviderCalendarMediaType.ANIME,
        title = "Title",
        coverUrl = null,
        scheduledAtEpochSeconds = 100L,
        episodeNumber = null,
        isOnList = true,
        precision = ProviderCalendarPrecision.RECURRING_BROADCAST_SLOT,
    )

    private class MemoryStore(
        private var snapshot: ProviderCalendarSnapshot?,
    ) : ProviderCalendarSnapshotStore {
        var reads = 0

        override suspend fun read(expectedProvider: ActiveProvider): ProviderCalendarSnapshot? {
            reads++
            return snapshot?.takeIf { it.provider == expectedProvider }
        }

        override suspend fun write(snapshot: ProviderCalendarSnapshot) {
            this.snapshot = snapshot
        }

        override suspend fun purge() {
            snapshot = null
        }
    }
}
