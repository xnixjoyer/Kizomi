package com.anisync.android.data.mal.calendar

import com.anisync.android.data.mal.oauth.MalAuthenticatedResponse
import com.anisync.android.data.mal.oauth.MalAuthenticatedResult
import com.anisync.android.domain.calendar.provider.ProviderCalendarCapability
import com.anisync.android.domain.calendar.provider.ProviderCalendarLoadResult
import com.anisync.android.domain.calendar.provider.ProviderCalendarNotice
import com.anisync.android.domain.calendar.provider.ProviderCalendarPrecision
import com.anisync.android.domain.calendar.provider.ProviderCalendarQuery
import com.anisync.android.domain.calendar.provider.ProviderCalendarSession
import com.anisync.android.domain.provider.ActiveProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class MalCalendarRepositoryTest {
    @Test
    fun `broadcast metadata projects recurring JST slot without inventing episode number`() = runTest {
        val api = api {
            success(page(day = "friday", time = "23:30"))
        }
        val repository = MalCalendarRepository(api) { 10_000L }
        val start = LocalDateTime.of(2026, 7, 24, 0, 0)
            .atZone(ZoneId.of("Asia/Tokyo"))
            .toEpochSecond()
        val query = ProviderCalendarQuery(
            startEpochSeconds = start,
            endEpochSeconds = start + 7L * 24L * 60L * 60L,
            zoneId = "Europe/Madrid",
        )

        val result = repository.load(malSession(), query)
        val content = result as ProviderCalendarLoadResult.Content
        val entry = content.entries.single()

        assertEquals(42L, entry.providerMediaId)
        assertEquals(ProviderCalendarPrecision.RECURRING_BROADCAST_SLOT, entry.precision)
        assertEquals(MalCalendarRepository.BROADCAST_SOURCE_ZONE.id, entry.sourceTimeZoneId)
        assertNull(entry.episodeNumber)
        assertTrue(entry.isOnList)
        assertTrue(ProviderCalendarCapability.RECURRING_BROADCAST_SLOTS in content.capabilities)
        assertTrue(ProviderCalendarNotice.EXACT_EPISODE_SCHEDULE_UNAVAILABLE in content.notices)
        assertTrue(ProviderCalendarNotice.AIRING_NOTIFICATIONS_UNAVAILABLE in content.notices)
    }

    @Test
    fun `same request is coalesced and cached while force refresh performs a new request`() = runTest {
        var calls = 0
        val api = api {
            calls++
            delay(50)
            success(page(day = "friday", time = "23:30"))
        }
        val repository = MalCalendarRepository(api) { 10_000L }
        val query = weekQuery()

        val first = async { repository.load(malSession(), query) }
        val second = async { repository.load(malSession(), query) }
        first.await()
        second.await()
        repository.load(malSession(), query, forceRefresh = true)

        assertEquals(2, calls)
    }

    @Test
    fun `stale repository cache performs a new seasonal request`() = runTest {
        var now = 10_000L
        var calls = 0
        val repository = MalCalendarRepository(api {
            calls++
            success(page(day = "friday", time = "23:30"))
        }) { now }

        repository.load(malSession(), weekQuery())
        now += 6L * 60L * 60L * 1_000L - 1L
        repository.load(malSession(), weekQuery())
        now += 2L
        repository.load(malSession(), weekQuery())

        assertEquals(2, calls)
    }

    @Test
    fun `inactive provider and unconfigured states perform zero MAL requests`() = runTest {
        var calls = 0
        val repository = MalCalendarRepository(api {
            calls++
            success(page(day = "friday", time = "23:30"))
        }) { 10_000L }

        val aniList = repository.load(
            ProviderCalendarSession(ActiveProvider.ANILIST_ONLY, true, "ani"),
            weekQuery(),
        )
        val unconfigured = repository.load(
            ProviderCalendarSession(ActiveProvider.UNCONFIGURED, false),
            weekQuery(),
        )

        assertTrue(aniList is ProviderCalendarLoadResult.Unavailable)
        assertTrue(unconfigured is ProviderCalendarLoadResult.Unavailable)
        assertEquals(0, calls)
    }

    @Test
    fun `missing broadcast data is explicit degraded content and never falls back`() = runTest {
        val repository = MalCalendarRepository(api {
            success(page(day = null, time = null))
        }) { 10_000L }

        val content = repository.load(malSession(), weekQuery()) as ProviderCalendarLoadResult.Content

        assertTrue(content.entries.isEmpty())
        assertTrue(ProviderCalendarNotice.BROADCAST_METADATA_UNAVAILABLE in content.notices)
        assertFalse(ProviderCalendarCapability.RECURRING_BROADCAST_SLOTS in content.capabilities)
    }

    @Test
    fun `partial broadcast metadata is explicit and never synthesizes a slot`() = runTest {
        val repository = MalCalendarRepository(api {
            success(page(day = "monday", time = null))
        }) { 10_000L }

        val content = repository.load(malSession(), weekQuery()) as ProviderCalendarLoadResult.Content

        assertTrue(content.entries.isEmpty())
        assertTrue(ProviderCalendarNotice.BROADCAST_METADATA_UNAVAILABLE in content.notices)
        assertTrue(ProviderCalendarNotice.PARTIAL_BROADCAST_METADATA in content.notices)
        assertFalse(ProviderCalendarCapability.RECURRING_BROADCAST_SLOTS in content.capabilities)
    }

    @Test
    fun `calendar range above sixty two days is rejected before provider traffic`() = runTest {
        var calls = 0
        val repository = MalCalendarRepository(api {
            calls++
            success(page(day = "friday", time = "23:30"))
        }) { 10_000L }
        val start = weekQuery().startEpochSeconds
        val query = ProviderCalendarQuery(
            startEpochSeconds = start,
            endEpochSeconds = start + 63L * 24L * 60L * 60L,
            zoneId = "UTC",
        )

        val result = repository.load(malSession(), query)

        assertTrue(result is ProviderCalendarLoadResult.Failure)
        assertEquals(0, calls)
    }

    private fun malSession() = ProviderCalendarSession(
        runtimeProvider = ActiveProvider.MAL_ONLY,
        providerTrafficAllowed = true,
        accountKey = "local-account",
    )

    private fun weekQuery(): ProviderCalendarQuery {
        val start = LocalDateTime.of(2026, 7, 24, 0, 0)
            .atZone(ZoneId.of("Asia/Tokyo"))
            .toEpochSecond()
        return ProviderCalendarQuery(
            startEpochSeconds = start,
            endEpochSeconds = start + 7L * 24L * 60L * 60L,
            zoneId = "UTC",
        )
    }

    private fun api(
        response: suspend () -> MalAuthenticatedResult,
    ) = MalCalendarApi(
        executeAuthenticated = { _, requestFactory ->
            requestFactory()
            response()
        },
        requestFactory = MalCalendarRequestFactory("http://localhost/v2/".toHttpUrl()),
    )

    private fun success(body: String) = MalAuthenticatedResult.Success(
        MalAuthenticatedResponse(200, Headers.headersOf(), body)
    )

    private fun page(day: String?, time: String?): String {
        val broadcastFields = buildList {
            if (day != null) add("\"day_of_the_week\":\"$day\"")
            if (time != null) add("\"start_time\":\"$time\"")
        }
        val broadcast = if (broadcastFields.isEmpty()) {
            "null"
        } else {
            broadcastFields.joinToString(prefix = "{", postfix = "}")
        }
        return """
            {"data":[{"node":{
              "id":42,"title":"Native title","main_picture":{"large":"https://image"},
              "start_date":"2026-07-01","end_date":"2026-09-30","broadcast":$broadcast,
              "my_list_status":{"status":"watching"}
            }}],"paging":{}}
        """.trimIndent()
    }
}
