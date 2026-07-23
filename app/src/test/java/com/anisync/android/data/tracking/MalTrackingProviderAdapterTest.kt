package com.anisync.android.data.tracking

import com.anisync.android.data.mal.oauth.MalAuthenticatedFailureReason
import com.anisync.android.data.mal.oauth.MalAuthenticatedResponse
import com.anisync.android.data.mal.oauth.MalAuthenticatedResult
import com.anisync.android.domain.tracking.TrackingCommand
import com.anisync.android.domain.tracking.TrackingCommandDraft
import com.anisync.android.domain.tracking.TrackingDeliveryResult
import com.anisync.android.domain.tracking.TrackingDesiredState
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingField
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingProvider
import com.anisync.android.domain.tracking.TrackingProviderRequest
import com.anisync.android.domain.tracking.TrackingStatus
import com.anisync.android.domain.tracking.toMalIntegerScore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MalTrackingProviderAdapterTest {
    @Test
    fun `anime PATCH uses exact sparse fields headers and controlled read-back`() = runTest {
        val fixture = fixture(
            response(200, "{}"),
            response(
                200,
                """{"id":20,"title":"Read back","main_picture":{"large":"https://img.test/a.jpg"},"my_list_status":{"status":"watching","score":9,"num_episodes_watched":4,"is_rewatching":false,"num_times_rewatched":2,"start_date":"2026-01-02","updated_at":"2026-07-22T19:00:00Z"}}""",
            ),
        )
        val desired = TrackingDesiredState(
            status = TrackingStatus.CURRENT,
            progress = 4,
            score100 = 85.0,
            repeatCount = 2,
            startedAt = "2026-01-02",
        )

        val result = fixture.adapter.apply(
            request(
                desired = desired,
                fields = setOf(
                    TrackingField.STATUS,
                    TrackingField.PROGRESS,
                    TrackingField.SCORE,
                    TrackingField.REPEAT_COUNT,
                    TrackingField.STARTED_AT,
                ),
            )
        )

        assertTrue(result is TrackingDeliveryResult.Success)
        result as TrackingDeliveryResult.Success
        assertEquals(90.0, result.snapshot.state.score100)
        assertEquals(4, result.snapshot.state.progress)
        assertEquals("Read back", result.snapshot.title)
        assertEquals(2, fixture.requests.size)
        val patch = fixture.requests[0]
        assertEquals("PATCH", patch.method)
        assertEquals("/v2/anime/20/my_list_status", patch.url.encodedPath)
        assertEquals("public-client", patch.header("X-MAL-CLIENT-ID"))
        assertEquals("application/json", patch.header("Accept"))
        val body = patch.bodyText()
        assertTrue(body.contains("status=watching"))
        assertTrue(body.contains("num_watched_episodes=4"))
        assertTrue(body.contains("score=9"))
        assertTrue(body.contains("is_rewatching=false"))
        assertTrue(body.contains("num_times_rewatched=2"))
        assertTrue(body.contains("start_date=2026-01-02"))
        assertFalse(body.contains("comments"))
        val readBack = fixture.requests[1]
        assertEquals("GET", readBack.method)
        assertEquals("/v2/anime/20", readBack.url.encodedPath)
        assertEquals("id,title,main_picture,my_list_status", readBack.url.queryParameter("fields"))
        assertTrue(result.snapshot.toString().contains("Read back"))
        assertFalse(result.snapshot.toString().contains("access-token"))
    }

    @Test
    fun `manga PATCH uses chapter volume reread dates and integer score projection`() = runTest {
        val fixture = fixture(
            response(200, "{}"),
            response(
                200,
                """{"id":20,"title":"Manga","my_list_status":{"status":"reading","score":8,"num_chapters_read":44,"num_volumes_read":8,"is_rereading":true,"num_times_reread":1,"finish_date":"2026-07-01"}}""",
            ),
        )
        val result = fixture.adapter.apply(
            request(
                mediaType = TrackingMediaType.MANGA,
                desired = TrackingDesiredState(
                    status = TrackingStatus.REPEATING,
                    progress = 44,
                    progressSecondary = 8,
                    score100 = 84.0,
                    repeatCount = 1,
                    completedAt = "2026-07-01",
                ),
                fields = setOf(
                    TrackingField.STATUS,
                    TrackingField.PROGRESS,
                    TrackingField.PROGRESS_SECONDARY,
                    TrackingField.SCORE,
                    TrackingField.REPEAT_COUNT,
                    TrackingField.COMPLETED_AT,
                ),
            )
        )

        assertTrue(result is TrackingDeliveryResult.Success)
        val body = fixture.requests.first().bodyText()
        assertTrue(body.contains("status=reading"))
        assertTrue(body.contains("num_chapters_read=44"))
        assertTrue(body.contains("num_volumes_read=8"))
        assertTrue(body.contains("is_rereading=true"))
        assertTrue(body.contains("num_times_reread=1"))
        assertTrue(body.contains("finish_date=2026-07-01"))
        assertTrue(body.contains("score=8"))
    }

    @Test
    fun `DELETE is reconciled only after read-back confirms list status absent`() = runTest {
        val fixture = fixture(
            response(204, ""),
            response(200, """{"id":20,"title":"Removed"}"""),
        )

        val result = fixture.adapter.apply(
            request(
                desired = TrackingDesiredState(status = null, progress = 0),
                fields = setOf(TrackingField.DELETE),
                delete = true,
            )
        )

        assertTrue(result is TrackingDeliveryResult.Success)
        assertTrue((result as TrackingDeliveryResult.Success).snapshot.deleted)
        assertEquals("DELETE", fixture.requests[0].method)
        assertEquals("GET", fixture.requests[1].method)
    }

    @Test
    fun `unsupported provider fields fail before transport and capability matrix is explicit`() = runTest {
        val fixture = fixture()

        val result = fixture.adapter.apply(
            request(
                desired = TrackingDesiredState(
                    status = TrackingStatus.CURRENT,
                    progress = 1,
                    notes = "must not be silently projected",
                ),
                fields = setOf(TrackingField.STATUS, TrackingField.NOTES),
            )
        )

        assertEquals(
            TrackingFailureKind.UNSUPPORTED_FIELD,
            (result as TrackingDeliveryResult.TerminalFailure).kind,
        )
        assertTrue(fixture.requests.isEmpty())
        assertFalse(
            TrackingField.PROGRESS_SECONDARY in
                MalTrackingCapabilities.forMediaType(TrackingMediaType.ANIME).supportedFields
        )
        assertTrue(
            TrackingField.PROGRESS_SECONDARY in
                MalTrackingCapabilities.forMediaType(TrackingMediaType.MANGA).supportedFields
        )
        assertFalse(
            TrackingField.PRIVATE in
                MalTrackingCapabilities.forMediaType(TrackingMediaType.MANGA).supportedFields
        )
    }

    @Test
    fun `rate limit auth and malformed read-back are typed without provider bodies`() = runTest {
        val limited = fixture(
            MalAuthenticatedResult.Success(
                MalAuthenticatedResponse(
                    statusCode = 429,
                    headers = Headers.Builder().add("Retry-After", "12").build(),
                    body = "private provider body",
                )
            )
        ).adapter.apply(request())
        limited as TrackingDeliveryResult.RetryableFailure
        assertEquals(TrackingFailureKind.RATE_LIMITED, limited.kind)
        assertEquals(12_000L, limited.retryAfterMillis)

        val auth = fixture(
            MalAuthenticatedResult.Failure(
                MalAuthenticatedFailureReason.RELOGIN_REQUIRED,
                "local-account",
            )
        ).adapter.apply(request())
        assertEquals(
            TrackingFailureKind.NOT_AUTHENTICATED,
            (auth as TrackingDeliveryResult.TerminalFailure).kind,
        )

        val malformed = fixture(response(200, "{}"), response(200, "not-json"))
            .adapter.apply(request())
        assertEquals(
            TrackingFailureKind.INVALID_RESPONSE,
            (malformed as TrackingDeliveryResult.TerminalFailure).kind,
        )
        assertFalse(limited.toString().contains("private provider body"))
    }

    @Test
    fun `absolute retry repeats the same body and cancellation remains control flow`() = runTest {
        val fixture = fixture(
            response(200, "{}"),
            response(200, animeReadBack()),
            response(200, "{}"),
            response(200, animeReadBack()),
        )
        fixture.adapter.apply(request())
        fixture.adapter.apply(request(deliveryAttempt = 2))
        assertEquals(fixture.requests[0].bodyText(), fixture.requests[2].bodyText())

        val cancelled = MalTrackingProviderAdapter(
            executeAuthenticated = { _, _ -> throw CancellationException("obsolete") },
            clientId = { "public-client" },
            requests = MalTrackingRequestFactory("https://example.test/v2/".toHttpUrl()),
        )
        var propagated = false
        try {
            cancelled.apply(request())
        } catch (_: CancellationException) {
            propagated = true
        }
        assertTrue(propagated)
    }

    @Test
    fun `MAL score projection deliberately rounds to provider integer scale`() {
        assertEquals(0, 0.0.toMalIntegerScore())
        assertEquals(8, 84.0.toMalIntegerScore())
        assertEquals(9, 85.0.toMalIntegerScore())
        assertEquals(10, 100.0.toMalIntegerScore())
    }

    private fun fixture(vararg responses: MalAuthenticatedResult): Fixture {
        val queue = ArrayDeque(responses.toList())
        val captured = mutableListOf<Request>()
        return Fixture(
            requests = captured,
            adapter = MalTrackingProviderAdapter(
                executeAuthenticated = { _, requestFactory ->
                    captured += requestFactory()
                    queue.removeFirst()
                },
                clientId = { "public-client" },
                requests = MalTrackingRequestFactory("https://example.test/v2/".toHttpUrl()),
            ),
        )
    }

    private fun request(
        mediaType: TrackingMediaType = TrackingMediaType.ANIME,
        desired: TrackingDesiredState = TrackingDesiredState(
            status = TrackingStatus.CURRENT,
            progress = 4,
        ),
        fields: Set<TrackingField> = setOf(TrackingField.STATUS, TrackingField.PROGRESS),
        delete: Boolean = false,
        deliveryAttempt: Int = 1,
    ) = TrackingProviderRequest(
        command = TrackingCommand(
            operationId = "operation",
            generation = 1L,
            draft = TrackingCommandDraft(
                localMediaId = "local-media",
                mediaType = mediaType,
                desired = desired,
                fields = fields,
                deleteIntent = delete,
            ),
        ),
        provider = TrackingProvider.MYANIMELIST,
        providerAccountId = "local-account",
        providerMediaId = 20L,
        deliveryAttempt = deliveryAttempt,
    )

    private fun response(code: Int, body: String) = MalAuthenticatedResult.Success(
        MalAuthenticatedResponse(code, Headers.Builder().build(), body)
    )

    private fun animeReadBack() =
        """{"id":20,"title":"Anime","my_list_status":{"status":"watching","num_episodes_watched":4}}"""

    private fun Request.bodyText(): String = Buffer().also { buffer ->
        body?.writeTo(buffer)
    }.readUtf8()

    private data class Fixture(
        val adapter: MalTrackingProviderAdapter,
        val requests: MutableList<Request>,
    )
}
