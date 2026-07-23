package com.anisync.android.worker

import com.anisync.android.data.tracking.TrackingDrainResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingOutboxWorkerTest {
    @Test
    fun `settled drain completes worker`() = runTest {
        assertEquals(
            TrackingOutboxWorkerDecision.SUCCESS,
            decideTrackingOutboxWork {
                TrackingDrainResult(attemptedDeliveries = 1, hasUnsettledDeliveries = false)
            },
        )
    }

    @Test
    fun `unsettled drain retries worker`() = runTest {
        assertEquals(
            TrackingOutboxWorkerDecision.RETRY,
            decideTrackingOutboxWork {
                TrackingDrainResult(attemptedDeliveries = 100, hasUnsettledDeliveries = true)
            },
        )
    }

    @Test
    fun `unexpected executor failure retries instead of acknowledging success`() = runTest {
        assertEquals(
            TrackingOutboxWorkerDecision.RETRY,
            decideTrackingOutboxWork { error("database temporarily unavailable") },
        )
    }

    @Test
    fun `worker cancellation remains structured control flow`() = runTest {
        var propagated = false
        try {
            decideTrackingOutboxWork { throw CancellationException("worker stopped") }
        } catch (_: CancellationException) {
            propagated = true
        }
        assertTrue(propagated)
    }
}
