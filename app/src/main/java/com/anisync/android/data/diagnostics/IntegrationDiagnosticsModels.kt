package com.anisync.android.data.diagnostics

import com.anisync.android.domain.provider.ActiveProvider
import com.anisync.android.domain.provider.ProviderTransitionPhase

enum class DiagnosticAvailability {
    AVAILABLE,
    UNAVAILABLE,
    ABSENT,
    UNKNOWN,
}

enum class DiagnosticSessionState {
    NOT_CONFIGURED,
    CONNECTED,
    REFRESHABLE_EXPIRY,
    RELOGIN_REQUIRED,
    MISSING,
    EXPIRED,
    CORRUPT,
    KEYSTORE_RESET,
    UNKNOWN,
}

enum class DiagnosticParityStatus {
    IMPLEMENTED_AND_TESTED,
    DEVICE_VERIFICATION_PENDING,
    IN_PROGRESS,
    BLOCKED_BY_PROVIDER,
    UNAVAILABLE_FOR_PROVIDER,
}

enum class DiagnosticsDashboardSection {
    BUILD_AND_SOURCE,
    AUTHENTICATION,
    PROVIDER_ISOLATION,
    REQUEST_AND_CACHE,
    FEATURE_COVERAGE,
    ACCEPTANCE_CHECKLIST,
}

data class DiagnosticsBuildMetadata(
    val versionName: String,
    val versionCode: Long,
    val buildType: String,
    val sourceRevision: String?,
    val oauthEnvironment: String,
    val redirectScheme: String,
    val redirectHost: String,
    val redirectPath: String,
    val clientIdPresent: Boolean,
    val databaseSchemaVersion: Int?,
)

data class DiagnosticsSessionMetadata(
    val activeProvider: ActiveProvider,
    val transitionPhase: ProviderTransitionPhase,
    val configuration: DiagnosticAvailability,
    val sessionState: DiagnosticSessionState,
    val pendingOAuthTransaction: DiagnosticAvailability,
    val tokenVaultHealth: DiagnosticAvailability,
    val accountRecordPresent: Boolean,
    val lastSuccessfulRestoreEpochMillis: Long?,
    val lastRefreshOutcome: String?,
    val lastRefreshEpochMillis: Long?,
)

data class DiagnosticsRuntimeMetrics(
    val activeProviderRequestCount: Long = 0L,
    val blockedInactiveProviderRequestCount: Long = 0L,
    val activeWorkerCount: Long = 0L,
    val providerBoundWidgetCount: Long = 0L,
    val networkKillSwitchEnabled: Boolean = false,
    val cacheHitCount: Long = 0L,
    val cacheMissCount: Long = 0L,
    val coalescedRequestCount: Long = 0L,
    val retryCount: Long = 0L,
    val writeCount: Long = 0L,
    val pendingTrackingCommandCount: Long = 0L,
    val lastSuccessfulRequestCategory: String? = null,
    val lastSuccessfulRequestEpochMillis: Long? = null,
    val lastFailureCategory: String? = null,
    val lastFailureHttpClass: String? = null,
    val lastFailureEpochMillis: Long? = null,
    val lastSuccessfulWriteReadBackEpochMillis: Long? = null,
    val lastProviderChangeResult: String? = null,
)

data class DiagnosticParityItem(
    val key: String,
    val status: DiagnosticParityStatus,
)

data class DiagnosticChecklistItem(
    val key: String,
    val passed: Boolean,
    val detail: String? = null,
)

data class IntegrationDiagnosticsSnapshot(
    val build: DiagnosticsBuildMetadata,
    val session: DiagnosticsSessionMetadata,
    val runtime: DiagnosticsRuntimeMetrics,
    val parity: List<DiagnosticParityItem>,
    val checklist: List<DiagnosticChecklistItem>,
    val capturedAtEpochMillis: Long,
)

interface IntegrationDiagnosticsSnapshotSource {
    /** Reads local persisted/in-memory state only. Implementations must not perform provider calls. */
    suspend fun snapshot(): IntegrationDiagnosticsSnapshot
}
