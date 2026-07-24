package com.anisync.android.data.diagnostics

import com.anisync.android.presentation.diagnostics.DiagnosticRedactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrationDiagnosticsRecorderTest {
    @Test
    fun `recorder keeps counters and strips unsafe categories`() {
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
        assertTrue(snapshot.networkKillSwitchEnabled)
        assertEquals(DiagnosticRedactor.REDACTED, snapshot.lastFailureCategory)
        assertEquals("4xx", snapshot.lastFailureHttpClass)
    }
}
