package com.anisync.android.presentation.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MutationCoalescerTest {
    @Test
    fun `rapid submit while commit is running serializes newest target without cancellation`() = runTest {
        val commits = mutableListOf<Int>()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var firstWasCancelled = false
        val coalescer = MutationCoalescer<Int, Int>(
            scope = this,
            debounceMs = 10L,
        ) { _, value ->
            commits += value
            if (value == 1) {
                firstStarted.complete(Unit)
                try {
                    releaseFirst.await()
                } catch (cancelled: CancellationException) {
                    firstWasCancelled = true
                    throw cancelled
                }
            }
            true
        }

        coalescer.seed(49, 0)
        coalescer.submit(49, 1)
        advanceTimeBy(10L)
        runCurrent()
        firstStarted.await()

        // This is the old failure window: submit used to cancel the active Apollo mutation.
        coalescer.submit(49, 2)
        assertTrue(coalescer.isLatest(49, 2))
        releaseFirst.complete(Unit)
        advanceUntilIdle()

        assertFalse(firstWasCancelled)
        assertEquals(listOf(1, 2), commits)
    }

    @Test
    fun `burst returning to committed baseline performs no network write`() = runTest {
        val commits = mutableListOf<Int>()
        val coalescer = MutationCoalescer<Int, Int>(this, debounceMs = 10L) { _, value ->
            commits += value
            true
        }

        coalescer.seed(7, 3)
        coalescer.submit(7, 4)
        coalescer.submit(7, 3)
        advanceUntilIdle()

        assertTrue(commits.isEmpty())
    }

    @Test
    fun `newer target is retained when older serialized commit fails`() = runTest {
        val commits = mutableListOf<Int>()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val coalescer = MutationCoalescer<Int, Int>(this, debounceMs = 10L) { _, value ->
            commits += value
            if (value == 1) {
                firstStarted.complete(Unit)
                releaseFirst.await()
                false
            } else {
                true
            }
        }

        coalescer.seed(11, 0)
        coalescer.submit(11, 1)
        advanceTimeBy(10L)
        runCurrent()
        firstStarted.await()
        coalescer.submit(11, 2)
        releaseFirst.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(1, 2), commits)
    }
}
