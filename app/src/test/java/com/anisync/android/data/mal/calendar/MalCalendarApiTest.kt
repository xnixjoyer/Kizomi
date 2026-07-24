package com.anisync.android.data.mal.calendar

import com.anisync.android.data.mal.api.MalApiFailureKind
import com.anisync.android.data.mal.api.MalApiResult
import com.anisync.android.data.mal.api.MalSeason
import com.anisync.android.data.mal.oauth.MalAuthenticatedResponse
import com.anisync.android.data.mal.oauth.MalAuthenticatedResult
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MalCalendarApiTest {
    @Test
    fun `seasonal calendar request uses documented broadcast fields and maps recurring metadata`() = runTest {
        val requests = mutableListOf<Request>()
        val api = api(requests) {
            success(
                """
                {"data":[{"node":{
                  "id":42,"title":"Native title",
                  "main_picture":{"medium":"https://image/medium","large":"https://image/large"},
                  "start_date":"2026-07-01","end_date":"2026-09-30","status":"currently_airing",
                  "media_type":"tv","num_episodes":12,
                  "broadcast":{"day_of_the_week":"friday","start_time":"23:30"},
                  "my_list_status":{"status":"watching","comments":"private-note"}
                }}],"paging":{}}
                """.trimIndent()
            )
        }

        val result = api.seasonal("account", 2026, MalSeason.SUMMER)
        val media = (result as MalApiResult.Success).value.entries.single()
        val request = requests.single()

        assertEquals("/v2/anime/season/2026/summer", request.url.encodedPath)
        assertEquals("100", request.url.queryParameter("limit"))
        assertEquals(MalCalendarRequestFactory.CALENDAR_FIELDS, request.url.queryParameter("fields"))
        assertEquals("friday", media.broadcastDayOfWeek)
        assertEquals("23:30", media.broadcastStartTime)
        assertTrue(media.isOnList)
        assertFalse(media.toString().contains("private-note"))
        assertFalse(media.toString().contains("Native title"))
    }

    @Test
    fun `hostile paging URL is rejected before authenticated execution`() = runTest {
        var calls = 0
        val api = MalCalendarApi(
            executeAuthenticated = { _, requestFactory ->
                calls++
                requestFactory()
                success("{\"data\":[],\"paging\":{}}")
            },
            requestFactory = MalCalendarRequestFactory(),
        )

        val result = api.nextPage(
            "account",
            "https://evil.invalid/v2/anime/season/2026/summer?fields=${MalCalendarRequestFactory.CALENDAR_FIELDS}",
        )

        assertEquals(
            MalApiFailureKind.INVALID_PAGING_URL,
            (result as MalApiResult.Failure).error.kind,
        )
        assertEquals(0, calls)
    }

    @Test
    fun `rate limit preserves retry after without leaking response body`() = runTest {
        val api = MalCalendarApi(
            executeAuthenticated = { _, requestFactory ->
                requestFactory()
                MalAuthenticatedResult.Success(
                    MalAuthenticatedResponse(
                        statusCode = 429,
                        headers = Headers.headersOf("Retry-After", "12"),
                        body = "private provider body",
                    )
                )
            },
            requestFactory = MalCalendarRequestFactory("http://localhost/v2/".toHttpUrl()),
        )

        val result = api.seasonal("account", 2026, MalSeason.SUMMER)
        val failure = (result as MalApiResult.Failure).error

        assertEquals(MalApiFailureKind.RATE_LIMITED, failure.kind)
        assertEquals(12_000L, failure.retryAfterMillis)
        assertFalse(failure.toString().contains("private provider body"))
    }

    private fun api(
        requests: MutableList<Request>,
        response: () -> MalAuthenticatedResult,
    ) = MalCalendarApi(
        executeAuthenticated = { _, requestFactory ->
            requests += requestFactory()
            response()
        },
        requestFactory = MalCalendarRequestFactory("http://localhost/v2/".toHttpUrl()),
    )

    private fun success(body: String) = MalAuthenticatedResult.Success(
        MalAuthenticatedResponse(200, Headers.headersOf(), body)
    )
}
