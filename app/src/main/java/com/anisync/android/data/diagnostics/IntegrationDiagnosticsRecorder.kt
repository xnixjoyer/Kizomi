package com.anisync.android.data.diagnostics

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntegrationDiagnosticsRecorder @Inject constructor() {
    private val activeProviderRequests = AtomicReference<Long?>(null)
    private val blockedInactiveProviderRequests = AtomicReference<Long?>(null)
    private val activeWorkers = AtomicReference<Long?>(null)
    private val providerBoundWidgets = AtomicReference<Long?>(null)
    private val cacheHits = AtomicReference<Long?>(null)
    private val cacheMisses = AtomicReference<Long?>(null)
    private val coalescedRequests = AtomicReference<Long?>(null)
    private val retries = AtomicReference<Long?>(null)
    private val writes = AtomicReference<Long?>(null)
    private val pendingTrackingCommands = AtomicReference<Long?>(null)
    private val networkKillSwitch = AtomicReference<Boolean?>(null)
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
        increment(activeProviderRequests)
        lastSuccessfulRequest.set(
            DiagnosticEvent(DiagnosticCategorySanitizer.sanitizeCategory(category), nowEpochMillis),
        )
    }

    fun recordBlockedInactiveProviderRequest() {
        increment(blockedInactiveProviderRequests)
    }

    fun recordRequestFailure(
        category: String,
        httpStatus: Int?,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ) {
        lastFailure.set(
            DiagnosticFailureEvent(
                category = DiagnosticCategorySanitizer.sanitizeCategory(category),
                httpClass = httpStatus?.takeIf { it in 100..599 }?.let { "${it / 100}xx" },
                epochMillis = nowEpochMillis,
            ),
        )
    }

    fun recordCacheHit() {
        increment(cacheHits)
    }

    fun recordCacheMiss() {
        increment(cacheMisses)
    }

    fun recordCoalescedRequest() {
        increment(coalescedRequests)
    }

    fun recordRetry() {
        increment(retries)
    }

    fun recordWrite() {
        increment(writes)
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
            DiagnosticEvent(DiagnosticCategorySanitizer.sanitizeCategory(category), nowEpochMillis),
        )
    }

    fun recordProviderChangeResult(result: String) {
        lastProviderChangeResult.set(DiagnosticCategorySanitizer.sanitizeCategory(result))
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

    private fun increment(counter: AtomicReference<Long?>) {
        while (true) {
            val current = counter.get()
            if (counter.compareAndSet(current, (current ?: 0L) + 1L)) return
        }
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
