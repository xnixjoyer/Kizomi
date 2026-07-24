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
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRedactorTest {
    @Test
    fun `every sensitive value class redacts a realistic fixture`() {
        realisticFixtures.forEach { (valueClass, fixture) ->
            val redacted = DiagnosticRedactor.redact(fixture, valueClass)
            assertEquals(DiagnosticRedactor.REDACTED, redacted)
            assertFalse(redacted.contains(fixture))
        }
    }

    @Test
    fun `free form categories reject realistic URLs tokens codes verifiers and identifiers`() {
        realisticFixtures.values.forEach { fixture ->
            assertEquals(DiagnosticRedactor.REDACTED, DiagnosticRedactor.sanitizeCategory(fixture))
        }
        assertEquals(
            DiagnosticRedactor.REDACTED,
            DiagnosticRedactor.sanitizeCategory(
                "Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.fake.payload.signature",
            ),
        )
        assertEquals(
            DiagnosticRedactor.REDACTED,
            DiagnosticRedactor.sanitizeCategory(
                "https://myanimelist.net/v1/oauth2/authorize?code=FAKE-AUTH-CODE-7391",
            ),
        )
    }

    @Test
    fun `exported and copied diagnostics retain typed state but omit every realistic secret`() {
        val fixture = realisticFixtures
        val export = SanitizedDiagnosticExporter.format(
            IntegrationDiagnosticsSnapshot(
                build = DiagnosticsBuildMetadata(
                    versionName = "3.2.0-debug",
                    versionCode = 20L,
                    buildType = "debug",
                    sourceRevision = fixture.getValue(SensitiveDiagnosticValueClass.ACCOUNT_IDENTIFIER),
                    oauthEnvironment = fixture.getValue(SensitiveDiagnosticValueClass.CLIENT_IDENTIFIER),
                    redirectScheme = fixture.getValue(SensitiveDiagnosticValueClass.CALLBACK_URL),
                    redirectHost = fixture.getValue(SensitiveDiagnosticValueClass.USERNAME),
                    redirectPath = fixture.getValue(SensitiveDiagnosticValueClass.AUTHORIZATION_CODE),
                    clientIdPresent = true,
                    databaseSchemaVersion = 28,
                ),
                session = DiagnosticsSessionMetadata(
                    activeProvider = ActiveProvider.MAL_ONLY,
                    transitionPhase = ProviderTransitionPhase.IDLE,
                    configuration = DiagnosticAvailability.AVAILABLE,
                    sessionState = DiagnosticSessionState.CONNECTED,
                    pendingOAuthTransaction = DiagnosticAvailability.UNKNOWN,
                    tokenVaultHealth = DiagnosticAvailability.AVAILABLE,
                    accountRecordPresent = true,
                    lastSuccessfulRestoreEpochMillis = 1L,
                    lastRefreshOutcome = fixture.getValue(SensitiveDiagnosticValueClass.ACCESS_TOKEN),
                    lastRefreshEpochMillis = 2L,
                ),
                runtime = DiagnosticsRuntimeMetrics(
                    activeProviderRequestCount = 2L,
                    blockedInactiveProviderRequestCount = 0L,
                    lastSuccessfulRequestCategory =
                        fixture.getValue(SensitiveDiagnosticValueClass.RAW_PROVIDER_RESPONSE),
                    lastFailureCategory =
                        fixture.getValue(SensitiveDiagnosticValueClass.REFRESH_TOKEN),
                    lastFailureHttpClass = "4xx",
                    lastProviderChangeResult =
                        fixture.getValue(SensitiveDiagnosticValueClass.OAUTH_STATE),
                ),
                parity = listOf(
                    DiagnosticParityItem(
                        fixture.getValue(SensitiveDiagnosticValueClass.PKCE_VERIFIER),
                        DiagnosticParityStatus.IMPLEMENTED_AND_TESTED,
                    ),
                ),
                checklist = listOf(
                    DiagnosticChecklistItem(
                        fixture.getValue(SensitiveDiagnosticValueClass.PKCE_CHALLENGE),
                        true,
                        fixture.getValue(SensitiveDiagnosticValueClass.PERSONAL_LIST_CONTENT),
                    ),
                ),
                capturedAtEpochMillis = 3L,
            ),
        )

        assertTrue(export.contains("activeProvider=MAL_ONLY"))
        assertTrue(export.contains("pendingOAuth=UNKNOWN"))
        assertTrue(export.contains("clientIdPresent=true"))
        realisticFixtures.values.forEach { secret ->
            assertFalse("Secret fixture leaked into copied export", export.contains(secret))
        }
        listOf(
            "FAKE-ACCESS-7f9c",
            "FAKE-REFRESH-0d31",
            "FAKE-AUTH-CODE-7391",
            "fake-private-user",
            "Cowboy Bebop",
            "https://",
        ).forEach { secretFragment ->
            assertFalse("Secret fragment leaked into copied export", export.contains(secretFragment))
        }
    }

    private companion object {
        val realisticFixtures = mapOf(
            SensitiveDiagnosticValueClass.ACCESS_TOKEN to
                "access_token=FAKE-ACCESS-7f9c.eyJhbGciOiJSUzI1NiJ9.payload.signature",
            SensitiveDiagnosticValueClass.REFRESH_TOKEN to
                "refresh_token=FAKE-REFRESH-0d31.def50200.long-refresh-material",
            SensitiveDiagnosticValueClass.AUTHORIZATION_CODE to
                "authorization_code=FAKE-AUTH-CODE-7391-SplxlOBeZQQYbYS6WxSbIA",
            SensitiveDiagnosticValueClass.PKCE_VERIFIER to
                "pkce_verifier=FAKE-VERIFIER-abcdefghijklmnopqrstuvwxyz0123456789-._~",
            SensitiveDiagnosticValueClass.PKCE_CHALLENGE to
                "pkce_challenge=FAKE-CHALLENGE-c2hhMjU2LWJhc2U2NHVybA",
            SensitiveDiagnosticValueClass.OAUTH_STATE to
                "oauth_state=FAKE-STATE-3d17c1b2a9e84f60",
            SensitiveDiagnosticValueClass.CLIENT_IDENTIFIER to
                "client_id=FAKE-PUBLIC-CLIENT-1234567890abcdef",
            SensitiveDiagnosticValueClass.ACCOUNT_IDENTIFIER to
                "account_id=987654321012345678",
            SensitiveDiagnosticValueClass.CALLBACK_URL to
                "anisyncplus-debug://oauth/mal/callback?code=FAKE-AUTH-CODE-7391",
            SensitiveDiagnosticValueClass.RAW_PROVIDER_RESPONSE to
                "raw_response={\"id\":987654321,\"name\":\"fake-private-user\"}",
            SensitiveDiagnosticValueClass.PERSONAL_LIST_CONTENT to
                "personal_list=Cowboy Bebop|WATCHING|episode=17|score=9",
            SensitiveDiagnosticValueClass.USERNAME to
                "username=fake-private-user@example.test",
        )
    }
}
