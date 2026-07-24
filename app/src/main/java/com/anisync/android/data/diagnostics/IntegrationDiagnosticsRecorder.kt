package com.anisync.android.data.diagnostics

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntegrationDiagnosticsRecorder @Inject constructor() {
    private val activeProviderRequests = AtomicLong()
    private val blockedInactiveProviderRequests = AtomicLong()
    private val activeWorkers = AtomicLong()
    private val providerBoundWidgets = AtomicLong()
    private val cacheHits = AtomicLong()
    private val cacheMisses = AtomicLong()
    private val coalescedRequests = AtomicLong()
    private val retries = AtomicLong()
    private val writes = AtomicLong()
    private val pendingTrackingCommands = AtomicLong()
    private val networkKillSwitch = AtomicReference(false)
    private val lastSuccessfulRequest = AtomicReference<DiagnosticEvent?>(null)
    private val lastFailure = AtomicReference<DiagnosticFailureEvent?>(null)
    private val lastWriteReadBack = AtomicReference<Long?>(null)
    private val lastProviderChangeResult = AtomicReference<String?>(null)
    private val lastSuccessfulRestore = AtomicReference<Long?>(null)
    private val lastRefresh = AtomicReference<DiagnosticEvent?>(null)

    fun recordActiveProviderRequest(
        category: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ) {
        activeProviderRequests.incrementAndGet()
        lastSuccessfulRequest.set(
            DiagnosticEvent(DiagnosticRedactor.sanitizeCategory(category), nowEpochMillis),
        )
    }

    fun recordBlockedInactiveProviderRequest() {
        blockedInactiveProviderRequests.incrementAndGet()
    }

    fun recordRequestFailure(
        category: String,
        httpStatus: Int?,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ) {
        lastFailure.set(
            DiagnosticFailureEvent(
                category = DiagnosticRedactor.sanitizeCategory(category),
                httpClass = httpStatus?.takeIf { it in 100..599 }?.let { "${it / 100}xx" },
                epochMillis = nowEpochMillis,
            ),
        )
    }

    fun recordCacheHit() {
        cacheHits.incrementAndGet()
    }

    fun recordCacheMiss() {
        cacheMisses.incrementAndGet()
    }

    fun recordCoalescedRequest() {
        coalescedRequests.incrementAndGet()
    }

    fun recordRetry() {
        retries.incrementAndGet()
    }

    fun recordWrite() {
        writes.incrementAndGet()
    }

    fun recordSuccessfulWriteReadBack(nowEpochMillis: Long = System.currentTimeMillis()) {
        lastWriteReadBack.set(nowEpochMillis)
    }

    fun recordSuccessfulRestore(nowEpochMillis: Long = System.currentTimeMillis()) {
        lastSuccessfulRestore.set(nowEpochMillis)
    }

    fun recordRefreshOutcome(
        category: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ) {
        lastRefresh.set(
            DiagnosticEvent(DiagnosticRedactor.sanitizeCategory(category), nowEpochMillis),
        )
    }

    fun recordProviderChangeResult(result: String) {
        lastProviderChangeResult.set(DiagnosticRedactor.sanitizeCategory(result))
    }

    fun setActiveWorkerCount(count: Long) {
        activeWorkers.set(count.coerceAtLeast(0L))
    }

    fun setProviderBoundWidgetCount(count: Long) {
        providerBoundWidgets.set(count.coerceAtLeast(0L))
    }

    fun setPendingTrackingCommandCount(count: Long) {
        pendingTrackingCommands.set(count.coerceAtLeast(0L))
    }

    fun setNetworkKillSwitchEnabled(enabled: Boolean) {
        networkKillSwitch.set(enabled)
    }

    fun runtimeSnapshot(): DiagnosticsRuntimeMetrics {
        val success = lastSuccessfulRequest.get()
        val failure = lastFailure.get()
        return DiagnosticsRuntimeMetrics(
            activeProviderRequestCount = activeProviderRequests.get(),
            blockedInactiveProviderRequestCount = blockedInactiveProviderRequests.get(),
            activeWorkerCount = activeWorkers.get(),
            providerBoundWidgetCount = providerBoundWidgets.get(),
            networkKillSwitchEnabled = networkKillSwitch.get(),
            cacheHitCount = cacheHits.get(),
            cacheMissCount = cacheMisses.get(),
            coalescedRequestCount = coalescedRequests.get(),
            retryCount = retries.get(),
            writeCount = writes.get(),
            pendingTrackingCommandCount = pendingTrackingCommands.get(),
            lastSuccessfulRequestCategory = success?.category,
            lastSuccessfulRequestEpochMillis = success?.epochMillis,
            lastFailureCategory = failure?.category,
            lastFailureHttpClass = failure?.httpClass,
            lastFailureEpochMillis = failure?.epochMillis,
            lastSuccessfulWriteReadBackEpochMillis = lastWriteReadBack.get(),
            lastProviderChangeResult = lastProviderChangeResult.get(),
        )
    }

    fun lastSuccessfulRestoreEpochMillis(): Long? = lastSuccessfulRestore.get()

    fun lastRefreshOutcome(): Pair<String, Long>? = lastRefresh.get()?.let {
        it.category to it.epochMillis
    }

    private data class DiagnosticEvent(
        val category: String,
        val epochMillis: Long,
    )

    private data class DiagnosticFailureEvent(
        val category: String,
        val httpClass: String?,
        val epochMillis: Long,
    )
}
