package com.anisync.android.worker.mal

import com.anisync.android.domain.calendar.provider.ProviderCalendarCapability
import com.anisync.android.domain.calendar.provider.ProviderCalendarEntry
import com.anisync.android.domain.calendar.provider.ProviderCalendarLoadResult
import com.anisync.android.domain.calendar.provider.ProviderCalendarMediaType
import com.anisync.android.domain.calendar.provider.ProviderCalendarPrecision
import com.anisync.android.domain.provider.ActiveProvider
import com.anisync.android.domain.provider.ProviderRuntimeState
import com.anisync.android.widget.provider.ProviderCalendarSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class MalCalendarRefreshCoordinatorTest {
    @Test
    fun `MAL only refresh writes MAL snapshot`() = runTest {
        var loadCalls = 0
        var written: ProviderCalendarSnapshot? = null
        val coordinator = coordinator(
            state = ProviderRuntimeState(ActiveProvider.MAL_ONLY),
            load = {
                loadCalls++
                content()
            },
            write = { written = it },
        )

        val outcome = coordinator.refresh()

        assertEquals(MalCalendarRefreshOutcome.Success, outcome)
        assertEquals(1, loadCalls)
        assertEquals(ActiveProvider.MAL_ONLY, written?.provider)
        assertEquals(1, written?.entries?.size)
    }

    @Test
    fun `AniList only and unconfigured cause zero MAL worker calls`() = runTest {
        var loadCalls = 0
        val aniList = coordinator(
            state = ProviderRuntimeState(ActiveProvider.ANILIST_ONLY),
            load = {
                loadCalls++
                content()
            },
        )
        val unconfigured = coordinator(
            state = ProviderRuntimeState(),
            load = {
                loadCalls++
                content()
            },
        )

        assertEquals(MalCalendarRefreshOutcome.SkippedInactiveProvider, aniList.refresh())
        assertEquals(MalCalendarRefreshOutcome.SkippedInactiveProvider, unconfigured.refresh())
        assertEquals(0, loadCalls)
    }

    @Test
    fun `retryable provider failure retries and permanent failure fails`() = runTest {
        val retry = coordinator(
            state = ProviderRuntimeState(ActiveProvider.MAL_ONLY),
            load = {
                ProviderCalendarLoadResult.Failure("rate_limited", true, 5_000L)
            },
        )
        val permanent = coordinator(
            state = ProviderRuntimeState(ActiveProvider.MAL_ONLY),
            load = {
                ProviderCalendarLoadResult.Failure("invalid", false)
            },
        )

        assertEquals(MalCalendarRefreshOutcome.Retry(5_000L), retry.refresh())
        assertEquals(MalCalendarRefreshOutcome.Failure, permanent.refresh())
        assertEquals(MalCalendarWorkerDecision.RETRY, decideMalCalendarWork(retry::refresh))
        assertEquals(MalCalendarWorkerDecision.FAILURE, decideMalCalendarWork(permanent::refresh))
    }

    @Test
    fun `worker cancellation remains structured control flow`() = runTest {
        var propagated = false
        try {
            decideMalCalendarWork { throw CancellationException("cancelled") }
        } catch (_: CancellationException) {
            propagated = true
        }
        assertTrue(propagated)
    }

    private fun coordinator(
        state: ProviderRuntimeState,
        load: suspend () -> ProviderCalendarLoadResult,
        write: suspend (ProviderCalendarSnapshot) -> Unit = {},
    ) = MalCalendarRefreshCoordinator(
        runtimeState = { state },
        activeAccountKey = { "local-account" },
        loadCalendar = { _, _, _ -> load() },
        writeSnapshot = write,
        nowEpochMillis = { 1_000_000L },
        zoneId = ZoneId.of("UTC"),
    )

    private fun content() = ProviderCalendarLoadResult.Content(
        entries = listOf(
            ProviderCalendarEntry(
                provider = ActiveProvider.MAL_ONLY,
                providerMediaId = 42L,
                mediaType = ProviderCalendarMediaType.ANIME,
                title = "Title",
                coverUrl = null,
                scheduledAtEpochSeconds = 2_000L,
                episodeNumber = null,
                isOnList = true,
                precision = ProviderCalendarPrecision.RECURRING_BROADCAST_SLOT,
            )
        ),
        capabilities = setOf(ProviderCalendarCapability.RECURRING_BROADCAST_SLOTS),
        fetchedAtEpochMillis = 1_000_000L,
    )
}
