package com.anisync.android.data.diagnostics

import com.anisync.android.presentation.diagnostics.DiagnosticRedactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrationDiagnosticsRecorderTest {
    @Test
    fun `new recorder keeps every unwired runtime metric unknown`() {
        val snapshot = IntegrationDiagnosticsRecorder().runtimeSnapshot()

        assertNull(snapshot.activeProviderRequestCount)
        assertNull(snapshot.blockedInactiveProviderRequestCount)
        assertNull(snapshot.activeWorkerCount)
        assertNull(snapshot.providerBoundWidgetCount)
        assertNull(snapshot.networkKillSwitchEnabled)
        assertNull(snapshot.cacheHitCount)
        assertNull(snapshot.cacheMissCount)
        assertNull(snapshot.coalescedRequestCount)
        assertNull(snapshot.retryCount)
        assertNull(snapshot.writeCount)
        assertNull(snapshot.pendingTrackingCommandCount)
    }

    @Test
    fun `recorder establishes known counters only after matching boundary events`() {
        val recorder = IntegrationDiagnosticsRecorder()

        recorder.recordActiveProviderRequest("library_read", nowEpochMillis = 10L)
        recorder.recordBlockedInactiveProviderRequest()
        recorder.recordCacheHit()
        recorder.recordCacheMiss()
        recorder.recordCoalescedRequest()
        recorder.recordRetry()
        recorder.recordWrite()
        recorder.recordRequestFailure(
            category = "https://example.test/private?access_token=secret",
            httpStatus = 429,
            nowEpochMillis = 20L,
        )
        recorder.setPendingTrackingCommandCount(-1L)
        recorder.setActiveWorkerCount(2L)
        recorder.setProviderBoundWidgetCount(1L)
        recorder.setNetworkKillSwitchEnabled(true)

        val snapshot = recorder.runtimeSnapshot()

        assertEquals(1L, snapshot.activeProviderRequestCount)
        assertEquals(1L, snapshot.blockedInactiveProviderRequestCount)
        assertEquals(1L, snapshot.cacheHitCount)
        assertEquals(1L, snapshot.cacheMissCount)
        assertEquals(1L, snapshot.coalescedRequestCount)
        assertEquals(1L, snapshot.retryCount)
        assertEquals(1L, snapshot.writeCount)
        assertEquals(0L, snapshot.pendingTrackingCommandCount)
        assertEquals(2L, snapshot.activeWorkerCount)
        assertEquals(1L, snapshot.providerBoundWidgetCount)
        assertTrue(snapshot.networkKillSwitchEnabled == true)
        assertEquals(DiagnosticRedactor.REDACTED, snapshot.lastFailureCategory)
        assertEquals("4xx", snapshot.lastFailureHttpClass)
    }

    @Test
    fun `explicitly set zero is distinguishable from unavailable instrumentation`() {
        val recorder = IntegrationDiagnosticsRecorder()

        assertNull(recorder.runtimeSnapshot().activeWorkerCount)
        recorder.setActiveWorkerCount(0L)

        assertEquals(0L, recorder.runtimeSnapshot().activeWorkerCount)
    }
}
