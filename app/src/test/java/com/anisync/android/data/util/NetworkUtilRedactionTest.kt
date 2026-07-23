package com.anisync.android.data.util

import com.anisync.android.domain.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkUtilRedactionTest {
    @Test
    fun `unknown exception message and object are not exposed`() = runTest {
        val sentinel = "authorization-private-response-sentinel"
        val result = safeApiCall<Unit> { error(sentinel) } as Result.Error

        assertEquals("An unexpected error occurred.", result.message)
        assertFalse(result.message.contains(sentinel))
        assertFalse(result.toString().contains(sentinel))
        assertTrue(result.toString().contains("exception=<redacted>"))
    }

    @Test
    fun `GraphQL body text is retained only in internal exception`() = runTest {
        val sentinel = "private-graphql-body-sentinel"
        val result = safeApiCall<Unit> {
            throw ApiError.GraphQLError(listOf(sentinel), statusCode = 422)
        } as Result.Error

        assertEquals("The provider rejected the request.", result.message)
        assertEquals(422, result.code)
        assertFalse(result.toString().contains(sentinel))
    }

    @Test
    fun `forbidden is distinct from unauthenticated`() = runTest {
        val result = safeApiCall<Unit> {
            throw ApiError.Forbidden("private-permission-detail")
        } as Result.Error

        assertEquals(403, result.code)
        assertEquals("You don't have permission to do that.", result.message)
        assertFalse(result.toString().contains("private-permission-detail"))
    }

    @Test
    fun `cancellation is never converted to an error result`() = runTest {
        var propagated = false
        try {
            safeApiCall<Unit> { throw CancellationException("obsolete request") }
        } catch (_: CancellationException) {
            propagated = true
        }
        assertTrue(propagated)
    }
}
