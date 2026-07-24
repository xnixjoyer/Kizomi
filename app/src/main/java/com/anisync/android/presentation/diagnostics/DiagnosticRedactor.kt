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
    const val UNKNOWN = DiagnosticCategorySanitizer.UNKNOWN

    fun redact(
        @Suppress("UNUSED_PARAMETER") value: String?,
        @Suppress("UNUSED_PARAMETER") valueClass: SensitiveDiagnosticValueClass,
    ): String = REDACTED

    fun sanitizeCategory(value: String?): String =
        DiagnosticCategorySanitizer.sanitizeCategory(value)

    fun sanitizeMetadata(value: String?): String =
        DiagnosticCategorySanitizer.sanitizeMetadata(value)
}

/** The only entry points for untrusted strings that may become visible dashboard text. */
object DiagnosticPresentationBoundary {
    fun category(value: String?): String = DiagnosticRedactor.sanitizeCategory(value)

    fun metadata(value: String?): String = DiagnosticRedactor.sanitizeMetadata(value)
}

object SanitizedDiagnosticExporter {
    fun format(snapshot: IntegrationDiagnosticsSnapshot): String = buildString {
        appendLine("Kizomi integration diagnostics")
        appendLine("captured=${formatEpoch(snapshot.capturedAtEpochMillis)}")
        appendLine(
            "version=${DiagnosticRedactor.sanitizeMetadata(snapshot.build.versionName)} " +
                "(${snapshot.build.versionCode})",
        )
        appendLine("buildType=${DiagnosticRedactor.sanitizeMetadata(snapshot.build.buildType)}")
        appendLine("source=${DiagnosticRedactor.sanitizeMetadata(snapshot.build.sourceRevision)}")
        appendLine(
            "oauthEnvironment=${DiagnosticRedactor.sanitizeMetadata(snapshot.build.oauthEnvironment)}",
        )
        appendLine(
            "redirectScheme=${DiagnosticRedactor.sanitizeMetadata(snapshot.build.redirectScheme)}",
        )
        appendLine("redirectHost=${DiagnosticRedactor.sanitizeMetadata(snapshot.build.redirectHost)}")
        appendLine("redirectPath=${DiagnosticRedactor.sanitizeMetadata(snapshot.build.redirectPath)}")
        appendLine("clientIdPresent=${snapshot.build.clientIdPresent}")
        appendLine("databaseSchemaVersion=${known(snapshot.build.databaseSchemaVersion)}")
        appendLine("activeProvider=${snapshot.session.activeProvider.name}")
        appendLine("transitionPhase=${snapshot.session.transitionPhase.name}")
        appendLine("configuration=${snapshot.session.configuration.name}")
        appendLine("sessionState=${snapshot.session.sessionState.name}")
        appendLine("pendingOAuth=${snapshot.session.pendingOAuthTransaction.name}")
        appendLine("vaultHealth=${snapshot.session.tokenVaultHealth.name}")
        appendLine("accountRecordPresent=${snapshot.session.accountRecordPresent}")
        appendLine("lastRestore=${formatEpoch(snapshot.session.lastSuccessfulRestoreEpochMillis)}")
        appendLine(
            "lastRefreshOutcome=${DiagnosticRedactor.sanitizeCategory(snapshot.session.lastRefreshOutcome)}",
        )
        appendLine("lastRefresh=${formatEpoch(snapshot.session.lastRefreshEpochMillis)}")
        appendLine("activeRequests=${known(snapshot.runtime.activeProviderRequestCount)}")
        appendLine(
            "blockedInactiveRequests=${known(snapshot.runtime.blockedInactiveProviderRequestCount)}",
        )
        appendLine("activeWorkers=${known(snapshot.runtime.activeWorkerCount)}")
        appendLine("providerBoundWidgets=${known(snapshot.runtime.providerBoundWidgetCount)}")
        appendLine("networkKillSwitch=${known(snapshot.runtime.networkKillSwitchEnabled)}")
        appendLine("cacheHits=${known(snapshot.runtime.cacheHitCount)}")
        appendLine("cacheMisses=${known(snapshot.runtime.cacheMissCount)}")
        appendLine("coalescedRequests=${known(snapshot.runtime.coalescedRequestCount)}")
        appendLine("retries=${known(snapshot.runtime.retryCount)}")
        appendLine("writes=${known(snapshot.runtime.writeCount)}")
        appendLine(
            "pendingTrackingCommands=${known(snapshot.runtime.pendingTrackingCommandCount)}",
        )
        appendLine(
            "lastRequestCategory=" +
                DiagnosticRedactor.sanitizeCategory(snapshot.runtime.lastSuccessfulRequestCategory),
        )
        appendLine("lastRequest=${formatEpoch(snapshot.runtime.lastSuccessfulRequestEpochMillis)}")
        appendLine(
            "lastFailureCategory=" +
                DiagnosticRedactor.sanitizeCategory(snapshot.runtime.lastFailureCategory),
        )
        appendLine(
            "lastFailureHttpClass=" +
                DiagnosticRedactor.sanitizeCategory(snapshot.runtime.lastFailureHttpClass),
        )
        appendLine("lastFailure=${formatEpoch(snapshot.runtime.lastFailureEpochMillis)}")
        appendLine(
            "lastWriteReadBack=" +
                formatEpoch(snapshot.runtime.lastSuccessfulWriteReadBackEpochMillis),
        )
        appendLine(
            "lastProviderChange=" +
                DiagnosticRedactor.sanitizeCategory(snapshot.runtime.lastProviderChangeResult),
        )
        appendLine("parity:")
        snapshot.parity.forEach { item ->
            appendLine("- ${DiagnosticRedactor.sanitizeCategory(item.key)}=${item.status.name}")
        }
        appendLine("checklist:")
        snapshot.checklist.forEach { item ->
            appendLine(
                "- ${DiagnosticRedactor.sanitizeCategory(item.key)}=${known(item.passed)};" +
                    "detail=${DiagnosticRedactor.sanitizeCategory(item.detail)}",
            )
        }
    }

    private fun known(value: Any?): String = value?.toString() ?: DiagnosticRedactor.UNKNOWN

    private fun formatEpoch(epochMillis: Long?): String = epochMillis
        ?.takeIf { it > 0L }
        ?.let { DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(it)) }
        ?: DiagnosticRedactor.UNKNOWN
}

/** Formatting helper for any future diagnostic log statement; raw snapshots must never be logged. */
object SanitizedDiagnosticLogFormatter {
    fun format(snapshot: IntegrationDiagnosticsSnapshot): String =
        SanitizedDiagnosticExporter.format(snapshot)
            .lineSequence()
            .joinToString(separator = "; ", prefix = "integration_diagnostics: ")
}

object DiagnosticsStatusSemantics {
    fun contentDescription(
        label: String,
        value: String,
        valueIsAlreadySafe: Boolean = false,
    ): String {
        val safeValue = if (valueIsAlreadySafe) value else DiagnosticRedactor.sanitizeMetadata(value)
        return "$label: $safeValue"
    }
}
