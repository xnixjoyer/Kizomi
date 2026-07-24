package com.anisync.android.presentation.diagnostics

import com.anisync.android.data.diagnostics.DiagnosticCategorySanitizer
import com.anisync.android.data.diagnostics.IntegrationDiagnosticsSnapshot
import java.time.Instant
import java.time.format.DateTimeFormatter

enum class SensitiveDiagnosticValueClass {
    ACCESS_TOKEN,
    REFRESH_TOKEN,
    AUTHORIZATION_CODE,
    PKCE_VERIFIER,
    PKCE_CHALLENGE,
    OAUTH_STATE,
    CLIENT_IDENTIFIER,
    ACCOUNT_IDENTIFIER,
    CALLBACK_URL,
    RAW_PROVIDER_RESPONSE,
    PERSONAL_LIST_CONTENT,
    USERNAME,
}

object DiagnosticRedactor {
    const val REDACTED = DiagnosticCategorySanitizer.REDACTED

    fun redact(
        @Suppress("UNUSED_PARAMETER") value: String?,
        @Suppress("UNUSED_PARAMETER") valueClass: SensitiveDiagnosticValueClass,
    ): String = REDACTED

    fun sanitizeCategory(value: String?): String = DiagnosticCategorySanitizer.sanitize(value)
}

object SanitizedDiagnosticExporter {
    fun format(snapshot: IntegrationDiagnosticsSnapshot): String = buildString {
        appendLine("Kizomi integration diagnostics")
        appendLine("captured=${formatEpoch(snapshot.capturedAtEpochMillis)}")
        appendLine("version=${snapshot.build.versionName} (${snapshot.build.versionCode})")
        appendLine("buildType=${DiagnosticRedactor.sanitizeCategory(snapshot.build.buildType)}")
        appendLine("source=${DiagnosticRedactor.sanitizeCategory(snapshot.build.sourceRevision)}")
        appendLine("oauthEnvironment=${DiagnosticRedactor.sanitizeCategory(snapshot.build.oauthEnvironment)}")
        appendLine("redirectScheme=${DiagnosticRedactor.sanitizeCategory(snapshot.build.redirectScheme)}")
        appendLine("redirectHost=${DiagnosticRedactor.sanitizeCategory(snapshot.build.redirectHost)}")
        appendLine("redirectPath=${DiagnosticRedactor.sanitizeCategory(snapshot.build.redirectPath)}")
        appendLine("clientIdPresent=${snapshot.build.clientIdPresent}")
        appendLine("databaseSchemaVersion=${snapshot.build.databaseSchemaVersion ?: "unknown"}")
        appendLine("activeProvider=${snapshot.session.activeProvider.name}")
        appendLine("transitionPhase=${snapshot.session.transitionPhase.name}")
        appendLine("configuration=${snapshot.session.configuration.name}")
        appendLine("sessionState=${snapshot.session.sessionState.name}")
        appendLine("pendingOAuth=${snapshot.session.pendingOAuthTransaction.name}")
        appendLine("vaultHealth=${snapshot.session.tokenVaultHealth.name}")
        appendLine("accountRecordPresent=${snapshot.session.accountRecordPresent}")
        appendLine("lastRestore=${formatEpoch(snapshot.session.lastSuccessfulRestoreEpochMillis)}")
        appendLine("lastRefreshOutcome=${DiagnosticRedactor.sanitizeCategory(snapshot.session.lastRefreshOutcome)}")
        appendLine("lastRefresh=${formatEpoch(snapshot.session.lastRefreshEpochMillis)}")
        appendLine("activeRequests=${snapshot.runtime.activeProviderRequestCount}")
        appendLine("blockedInactiveRequests=${snapshot.runtime.blockedInactiveProviderRequestCount}")
        appendLine("activeWorkers=${snapshot.runtime.activeWorkerCount}")
        appendLine("providerBoundWidgets=${snapshot.runtime.providerBoundWidgetCount}")
        appendLine("networkKillSwitch=${snapshot.runtime.networkKillSwitchEnabled}")
        appendLine("cacheHits=${snapshot.runtime.cacheHitCount}")
        appendLine("cacheMisses=${snapshot.runtime.cacheMissCount}")
        appendLine("coalescedRequests=${snapshot.runtime.coalescedRequestCount}")
        appendLine("retries=${snapshot.runtime.retryCount}")
        appendLine("writes=${snapshot.runtime.writeCount}")
        appendLine("pendingTrackingCommands=${snapshot.runtime.pendingTrackingCommandCount}")
        appendLine("lastRequestCategory=${DiagnosticRedactor.sanitizeCategory(snapshot.runtime.lastSuccessfulRequestCategory)}")
        appendLine("lastRequest=${formatEpoch(snapshot.runtime.lastSuccessfulRequestEpochMillis)}")
        appendLine("lastFailureCategory=${DiagnosticRedactor.sanitizeCategory(snapshot.runtime.lastFailureCategory)}")
        appendLine("lastFailureHttpClass=${DiagnosticRedactor.sanitizeCategory(snapshot.runtime.lastFailureHttpClass)}")
        appendLine("lastFailure=${formatEpoch(snapshot.runtime.lastFailureEpochMillis)}")
        appendLine("lastWriteReadBack=${formatEpoch(snapshot.runtime.lastSuccessfulWriteReadBackEpochMillis)}")
        appendLine("lastProviderChange=${DiagnosticRedactor.sanitizeCategory(snapshot.runtime.lastProviderChangeResult)}")
        appendLine("parity:")
        snapshot.parity.forEach { item ->
            appendLine("- ${DiagnosticRedactor.sanitizeCategory(item.key)}=${item.status.name}")
        }
        appendLine("checklist:")
        snapshot.checklist.forEach { item ->
            appendLine(
                "- ${DiagnosticRedactor.sanitizeCategory(item.key)}=${item.passed};" +
                    "detail=${DiagnosticRedactor.sanitizeCategory(item.detail)}",
            )
        }
    }

    private fun formatEpoch(epochMillis: Long?): String = epochMillis
        ?.takeIf { it > 0L }
        ?.let { DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(it)) }
        ?: "never"
}

object DiagnosticsStatusSemantics {
    fun contentDescription(label: String, value: String): String =
        "${DiagnosticRedactor.sanitizeCategory(label)}: ${DiagnosticRedactor.sanitizeCategory(value)}"
}
