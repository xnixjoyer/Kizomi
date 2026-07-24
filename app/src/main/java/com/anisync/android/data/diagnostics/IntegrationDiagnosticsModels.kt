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
) {
    override fun toString(): String = buildString {
        append("DiagnosticsBuildMetadata(")
        append("versionName=").append(DiagnosticCategorySanitizer.sanitizeMetadata(versionName))
        append(", versionCode=").append(versionCode)
        append(", buildType=").append(DiagnosticCategorySanitizer.sanitizeMetadata(buildType))
        append(", sourceRevision=").append(DiagnosticCategorySanitizer.sanitizeMetadata(sourceRevision))
        append(", oauthEnvironment=").append(DiagnosticCategorySanitizer.sanitizeMetadata(oauthEnvironment))
        append(", redirectScheme=").append(DiagnosticCategorySanitizer.sanitizeMetadata(redirectScheme))
        append(", redirectHost=").append(DiagnosticCategorySanitizer.sanitizeMetadata(redirectHost))
        append(", redirectPath=").append(DiagnosticCategorySanitizer.sanitizeMetadata(redirectPath))
        append(", clientIdPresent=").append(clientIdPresent)
        append(", databaseSchemaVersion=").append(databaseSchemaVersion ?: DiagnosticCategorySanitizer.UNKNOWN)
        append(')')
    }
}

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
) {
    override fun toString(): String = buildString {
        append("DiagnosticsSessionMetadata(")
        append("activeProvider=").append(activeProvider.name)
        append(", transitionPhase=").append(transitionPhase.name)
        append(", configuration=").append(configuration.name)
        append(", sessionState=").append(sessionState.name)
        append(", pendingOAuthTransaction=").append(pendingOAuthTransaction.name)
        append(", tokenVaultHealth=").append(tokenVaultHealth.name)
        append(", accountRecordPresent=").append(accountRecordPresent)
        append(", lastSuccessfulRestoreEpochMillis=")
            .append(lastSuccessfulRestoreEpochMillis ?: DiagnosticCategorySanitizer.UNKNOWN)
        append(", lastRefreshOutcome=")
            .append(DiagnosticCategorySanitizer.sanitizeCategory(lastRefreshOutcome))
        append(", lastRefreshEpochMillis=")
            .append(lastRefreshEpochMillis ?: DiagnosticCategorySanitizer.UNKNOWN)
        append(')')
    }
}

data class DiagnosticsRuntimeMetrics(
    val activeProviderRequestCount: Long? = null,
    val blockedInactiveProviderRequestCount: Long? = null,
    val activeWorkerCount: Long? = null,
    val providerBoundWidgetCount: Long? = null,
    val networkKillSwitchEnabled: Boolean? = null,
    val cacheHitCount: Long? = null,
    val cacheMissCount: Long? = null,
    val coalescedRequestCount: Long? = null,
    val retryCount: Long? = null,
    val writeCount: Long? = null,
    val pendingTrackingCommandCount: Long? = null,
    val lastSuccessfulRequestCategory: String? = null,
    val lastSuccessfulRequestEpochMillis: Long? = null,
    val lastFailureCategory: String? = null,
    val lastFailureHttpClass: String? = null,
    val lastFailureEpochMillis: Long? = null,
    val lastSuccessfulWriteReadBackEpochMillis: Long? = null,
    val lastProviderChangeResult: String? = null,
) {
    override fun toString(): String = buildString {
        append("DiagnosticsRuntimeMetrics(")
        append("activeProviderRequestCount=").append(known(activeProviderRequestCount))
        append(", blockedInactiveProviderRequestCount=").append(known(blockedInactiveProviderRequestCount))
        append(", activeWorkerCount=").append(known(activeWorkerCount))
        append(", providerBoundWidgetCount=").append(known(providerBoundWidgetCount))
        append(", networkKillSwitchEnabled=").append(networkKillSwitchEnabled ?: DiagnosticCategorySanitizer.UNKNOWN)
        append(", cacheHitCount=").append(known(cacheHitCount))
        append(", cacheMissCount=").append(known(cacheMissCount))
        append(", coalescedRequestCount=").append(known(coalescedRequestCount))
        append(", retryCount=").append(known(retryCount))
        append(", writeCount=").append(known(writeCount))
        append(", pendingTrackingCommandCount=").append(known(pendingTrackingCommandCount))
        append(", lastSuccessfulRequestCategory=")
            .append(DiagnosticCategorySanitizer.sanitizeCategory(lastSuccessfulRequestCategory))
        append(", lastSuccessfulRequestEpochMillis=")
            .append(lastSuccessfulRequestEpochMillis ?: DiagnosticCategorySanitizer.UNKNOWN)
        append(", lastFailureCategory=")
            .append(DiagnosticCategorySanitizer.sanitizeCategory(lastFailureCategory))
        append(", lastFailureHttpClass=")
            .append(DiagnosticCategorySanitizer.sanitizeCategory(lastFailureHttpClass))
        append(", lastFailureEpochMillis=")
            .append(lastFailureEpochMillis ?: DiagnosticCategorySanitizer.UNKNOWN)
        append(", lastSuccessfulWriteReadBackEpochMillis=")
            .append(lastSuccessfulWriteReadBackEpochMillis ?: DiagnosticCategorySanitizer.UNKNOWN)
        append(", lastProviderChangeResult=")
            .append(DiagnosticCategorySanitizer.sanitizeCategory(lastProviderChangeResult))
        append(')')
    }

    private fun known(value: Long?): Any = value ?: DiagnosticCategorySanitizer.UNKNOWN
}

data class DiagnosticParityItem(
    val key: String,
    val status: DiagnosticParityStatus,
) {
    override fun toString(): String =
        "DiagnosticParityItem(key=${DiagnosticCategorySanitizer.sanitizeCategory(key)}, status=${status.name})"
}

data class DiagnosticChecklistItem(
    val key: String,
    val passed: Boolean?,
    val detail: String? = null,
) {
    override fun toString(): String = buildString {
        append("DiagnosticChecklistItem(key=")
            .append(DiagnosticCategorySanitizer.sanitizeCategory(key))
        append(", passed=").append(passed ?: DiagnosticCategorySanitizer.UNKNOWN)
        append(", detail=").append(DiagnosticCategorySanitizer.sanitizeCategory(detail))
        append(')')
    }
}

data class IntegrationDiagnosticsSnapshot(
    val build: DiagnosticsBuildMetadata,
    val session: DiagnosticsSessionMetadata,
    val runtime: DiagnosticsRuntimeMetrics,
    val parity: List<DiagnosticParityItem>,
    val checklist: List<DiagnosticChecklistItem>,
    val capturedAtEpochMillis: Long,
) {
    override fun toString(): String = buildString {
        append("IntegrationDiagnosticsSnapshot(build=").append(build)
        append(", session=").append(session)
        append(", runtime=").append(runtime)
        append(", parity=").append(parity)
        append(", checklist=").append(checklist)
        append(", capturedAtEpochMillis=").append(capturedAtEpochMillis)
        append(')')
    }
}

interface IntegrationDiagnosticsSnapshotSource {
    /** Reads local persisted/in-memory state only. Implementations must not perform provider calls. */
    suspend fun snapshot(): IntegrationDiagnosticsSnapshot
}
