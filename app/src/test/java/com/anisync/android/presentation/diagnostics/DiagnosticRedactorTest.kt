package com.anisync.android.presentation.diagnostics

import com.anisync.android.data.diagnostics.DiagnosticAvailability
import com.anisync.android.data.diagnostics.DiagnosticChecklistItem
import com.anisync.android.data.diagnostics.DiagnosticParityItem
import com.anisync.android.data.diagnostics.DiagnosticParityStatus
import com.anisync.android.data.diagnostics.DiagnosticSessionState
import com.anisync.android.data.diagnostics.DiagnosticsBuildMetadata
import com.anisync.android.data.diagnostics.DiagnosticsRuntimeMetrics
import com.anisync.android.data.diagnostics.DiagnosticsSessionMetadata
import com.anisync.android.data.diagnostics.IntegrationDiagnosticsSnapshot
import com.anisync.android.domain.provider.ActiveProvider
import com.anisync.android.domain.provider.ProviderTransitionPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DiagnosticRedactorTest {
    @Test
    fun `every sensitive value class is always redacted`() {
        SensitiveDiagnosticValueClass.entries.forEach { valueClass ->
            assertEquals(
                DiagnosticRedactor.REDACTED,
                DiagnosticRedactor.redact("sensitive-fixture", valueClass),
            )
        }
    }

    @Test
    fun `free form categories reject URLs tokens and identifiers`() {
        listOf(
            "https://example.test/callback?code=secret",
            "Authorization: Bearer secret-token",
            "access_token=secret-token",
            "client_id=public-but-hidden",
            "username=private-user",
            "12345678901234567890123456789012345678901234567890",
        ).forEach { fixture ->
            assertEquals(DiagnosticRedactor.REDACTED, DiagnosticRedactor.sanitizeCategory(fixture))
        }
    }

    @Test
    fun `sanitized export contains typed state but no sensitive fixtures`() {
        val export = SanitizedDiagnosticExporter.format(
            IntegrationDiagnosticsSnapshot(
                build = DiagnosticsBuildMetadata(
                    versionName = "3.2.0-debug",
                    versionCode = 20L,
                    buildType = "debug",
                    sourceRevision = "abcdef12",
                    oauthEnvironment = "DEBUG",
                    redirectScheme = "anisyncplus-debug",
                    redirectHost = "oauth",
                    redirectPath = "/mal/callback",
                    clientIdPresent = true,
                    databaseSchemaVersion = 28,
                ),
                session = DiagnosticsSessionMetadata(
                    activeProvider = ActiveProvider.MAL_ONLY,
                    transitionPhase = ProviderTransitionPhase.IDLE,
                    configuration = DiagnosticAvailability.AVAILABLE,
                    sessionState = DiagnosticSessionState.CONNECTED,
                    pendingOAuthTransaction = DiagnosticAvailability.ABSENT,
                    tokenVaultHealth = DiagnosticAvailability.AVAILABLE,
                    accountRecordPresent = true,
                    lastSuccessfulRestoreEpochMillis = 1L,
                    lastRefreshOutcome = "profile_read",
                    lastRefreshEpochMillis = 2L,
                ),
                runtime = DiagnosticsRuntimeMetrics(
                    activeProviderRequestCount = 2L,
                    blockedInactiveProviderRequestCount = 0L,
                    lastSuccessfulRequestCategory = "library_read",
                    lastFailureCategory = "none",
                ),
                parity = listOf(
                    DiagnosticParityItem(
                        "authentication_session",
                        DiagnosticParityStatus.IMPLEMENTED_AND_TESTED,
                    ),
                ),
                checklist = listOf(
                    DiagnosticChecklistItem("inactive_provider_request_count_zero", true),
                ),
                capturedAtEpochMillis = 3L,
            ),
        )

        assertFalse(export.contains("access-token-fixture"))
        assertFalse(export.contains("refresh-token-fixture"))
        assertFalse(export.contains("authorization-code-fixture"))
        assertFalse(export.contains("pkce-verifier-fixture"))
        assertFalse(export.contains("oauth-state-fixture"))
        assertFalse(export.contains("full-account-id-fixture"))
        assertFalse(export.contains("private-list-content-fixture"))
        assertFalse(export.contains("https://"))
    }
}
