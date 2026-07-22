package com.anisync.android.di

import com.anisync.android.data.network.TokenBucket
import com.anisync.android.data.util.ApiError
import com.anisync.android.presentation.components.alert.ToastManager
import com.apollographql.apollo.api.http.HttpHeader
import com.apollographql.apollo.api.http.HttpMethod
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.network.http.HttpInterceptorChain
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuthorizationInterceptorTest {
    private lateinit var interceptor: AuthorizationInterceptor
    private val request = HttpRequest.Builder(HttpMethod.Post, "https://graphql.anilist.co/").build()

    @Before
    fun setUp() {
        interceptor = AuthorizationInterceptor(
            tokenProvider = { null },
            sessionExpiredHandler = {},
            toastManager = ToastManager(),
            tokenBucket = TokenBucket()
        )
    }

    @Test
    fun first429BodyIsClosedBeforeSingleSuccessfulRetry() = runTest {
        val first = trackedResponse(429, headers = listOf(HttpHeader("Retry-After", "0")))
        val second = trackedResponse(200)
        val chain = QueueChain(first.response, second.response)

        val result = interceptor.intercept(request, chain)

        assertEquals(200, result.statusCode)
        assertEquals(2, chain.callCount)
        assertTrue(first.source.closed)
        assertFalse(second.source.closed)

        result.body?.close()
        assertTrue(second.source.closed)
    }

    @Test
    fun second429IsClosedAndNoThirdRequestIsMade() {
        val first = trackedResponse(429, headers = listOf(HttpHeader("Retry-After", "0")))
        val second = trackedResponse(429, headers = listOf(HttpHeader("Retry-After", "15")))
        val chain = QueueChain(first.response, second.response)

        assertThrows(ApiError.RateLimited::class.java) {
            runTest { interceptor.intercept(request, chain) }
        }

        assertEquals(2, chain.callCount)
        assertTrue(first.source.closed)
        assertTrue(second.source.closed)
    }

    private fun trackedResponse(
        status: Int,
        headers: List<HttpHeader> = emptyList()
    ): TrackedResponse {
        val source = CloseTrackingSource(Buffer().writeUtf8("{}"))
        val builder = HttpResponse.Builder(status)
            .body(source.buffer())
        headers.forEach { builder.addHeader(it.name, it.value) }
        return TrackedResponse(builder.build(), source)
    }

    private data class TrackedResponse(
        val response: HttpResponse,
        val source: CloseTrackingSource
    )

    private class QueueChain(vararg responses: HttpResponse) : HttpInterceptorChain {
        private val queued = ArrayDeque(responses.toList())
        var callCount: Int = 0
            private set

        override suspend fun proceed(request: HttpRequest): HttpResponse {
            callCount++
            return queued.removeFirstOrNull() ?: error("Unexpected extra retry")
        }
    }

    private class CloseTrackingSource(delegate: Source) : ForwardingSource(delegate) {
        var closed: Boolean = false
            private set

        override fun close() {
            closed = true
            super.close()
        }
    }
}
