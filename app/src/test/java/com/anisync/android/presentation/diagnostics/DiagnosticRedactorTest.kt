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
    fun `every sensitive value class redacts a realistic marker-free fixture`() {
        realisticFixtures.forEach { (valueClass, fixture) ->
            val redacted = DiagnosticRedactor.redact(fixture, valueClass)
            assertEquals(DiagnosticRedactor.REDACTED, redacted)
            assertFalse(redacted.contains(fixture))
        }
    }

    @Test
    fun `realistic fixtures cannot enter rendered values or accessibility semantics`() {
        realisticFixtures.values.forEach { fixture ->
            val rendered = DiagnosticPresentationBoundary.metadata(fixture)
            val semantics = DiagnosticsStatusSemantics.contentDescription(
                label = "Diagnostic value",
                value = rendered,
                valueIsAlreadySafe = true,
            )

            assertEquals(DiagnosticRedactor.REDACTED, rendered)
            assertEquals("Diagnostic value: <redacted>", semantics)
            assertNoFixtureLeak(rendered)
            assertNoFixtureLeak(semantics)
        }
    }

    @Test
    fun `fixture-bearing snapshot is safe in copy export logs and every toString boundary`() {
        val snapshot = fixtureBearingSnapshot()
        val outputs = listOf(
            SanitizedDiagnosticExporter.format(snapshot),
            SanitizedDiagnosticLogFormatter.format(snapshot),
            snapshot.toString(),
            snapshot.build.toString(),
            snapshot.session.toString(),
            snapshot.runtime.toString(),
            snapshot.parity.toString(),
            snapshot.checklist.toString(),
        )

        outputs.forEach { output ->
            assertTrue("Expected explicit redaction in output", output.contains(DiagnosticRedactor.REDACTED))
            assertNoFixtureLeak(output)
        }

        val copiedExport = outputs.first()
        assertTrue(copiedExport.contains("activeProvider=MAL_ONLY"))
        assertTrue(copiedExport.contains("pendingOAuth=UNKNOWN"))
        assertTrue(copiedExport.contains("clientIdPresent=true"))
        assertTrue(copiedExport.contains("cacheHits=unknown"))
    }

    @Test
    fun `safe low-cardinality categories and structural metadata remain useful`() {
        assertEquals("library_read", DiagnosticPresentationBoundary.category("library_read"))
        assertEquals("4xx", DiagnosticPresentationBoundary.category("4xx"))
        assertEquals("debug", DiagnosticPresentationBoundary.metadata("debug"))
        assertEquals("/mal/callback", DiagnosticPresentationBoundary.metadata("/mal/callback"))
        assertEquals("3.2.0-debug", DiagnosticPresentationBoundary.metadata("3.2.0-debug"))
    }

    private fun fixtureBearingSnapshot(): IntegrationDiagnosticsSnapshot {
        val fixture = realisticFixtures
        return IntegrationDiagnosticsSnapshot(
            build = DiagnosticsBuildMetadata(
                versionName = "3.2.0-debug",
                versionCode = 20L,
                buildType = "debug",
                sourceRevision = fixture.getValue(SensitiveDiagnosticValueClass.ACCESS_TOKEN),
                oauthEnvironment = fixture.getValue(SensitiveDiagnosticValueClass.REFRESH_TOKEN),
                redirectScheme = fixture.getValue(SensitiveDiagnosticValueClass.CALLBACK_URL),
                redirectHost = fixture.getValue(SensitiveDiagnosticValueClass.CLIENT_IDENTIFIER),
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
                lastSuccessfulRestoreEpochMillis = null,
                lastRefreshOutcome = fixture.getValue(SensitiveDiagnosticValueClass.PKCE_VERIFIER),
                lastRefreshEpochMillis = null,
            ),
            runtime = DiagnosticsRuntimeMetrics(
                activeProviderRequestCount = null,
                blockedInactiveProviderRequestCount = null,
                lastSuccessfulRequestCategory =
                    fixture.getValue(SensitiveDiagnosticValueClass.RAW_PROVIDER_RESPONSE),
                lastFailureCategory = fixture.getValue(SensitiveDiagnosticValueClass.PKCE_CHALLENGE),
                lastFailureHttpClass = "4xx",
                lastProviderChangeResult = fixture.getValue(SensitiveDiagnosticValueClass.OAUTH_STATE),
            ),
            parity = listOf(
                DiagnosticParityItem(
                    fixture.getValue(SensitiveDiagnosticValueClass.ACCOUNT_IDENTIFIER),
                    DiagnosticParityStatus.IN_PROGRESS,
                ),
            ),
            checklist = listOf(
                DiagnosticChecklistItem(
                    fixture.getValue(SensitiveDiagnosticValueClass.USERNAME),
                    passed = null,
                    detail = fixture.getValue(SensitiveDiagnosticValueClass.PERSONAL_LIST_CONTENT),
                ),
            ),
            capturedAtEpochMillis = 3L,
        )
    }

    private fun assertNoFixtureLeak(output: String) {
        realisticFixtures.values.forEach { secret ->
            assertFalse("Secret fixture leaked: $secret", output.contains(secret))
        }
        distinctiveFragments.forEach { fragment ->
            assertFalse("Secret fragment leaked: $fragment", output.contains(fragment))
        }
    }

    private companion object {
        val realisticFixtures = mapOf(
            SensitiveDiagnosticValueClass.ACCESS_TOKEN to
                "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.ZmFrZS1hY2Nlc3MtcGF5bG9hZA.c2lnbmF0dXJl",
            SensitiveDiagnosticValueClass.REFRESH_TOKEN to
                "def50200FAKE0d31b729e6f5c2a44c0f8d5b1a9e7c3f0aa21refresh",
            SensitiveDiagnosticValueClass.AUTHORIZATION_CODE to
                "SplxlOBeZQQYbYS6WxSbIA",
            SensitiveDiagnosticValueClass.PKCE_VERIFIER to
                "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk-FAKE-verifier-73",
            SensitiveDiagnosticValueClass.PKCE_CHALLENGE to
                "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            SensitiveDiagnosticValueClass.OAUTH_STATE to
                "3d17c1b2a9e84f60b8c6d247e1259aa0",
            SensitiveDiagnosticValueClass.CLIENT_IDENTIFIER to
                "1234567890abcdef1234567890abcdef",
            SensitiveDiagnosticValueClass.ACCOUNT_IDENTIFIER to
                "987654321012345678",
            SensitiveDiagnosticValueClass.CALLBACK_URL to
                "anisyncplus-debug://oauth/mal/callback?code=SplxlOBeZQQYbYS6WxSbIA&state=3d17c1b2",
            SensitiveDiagnosticValueClass.RAW_PROVIDER_RESPONSE to
                "{\"id\":987654321,\"name\":\"private-user\",\"status\":\"watching\"}",
            SensitiveDiagnosticValueClass.PERSONAL_LIST_CONTENT to
                "Cowboy Bebop The Movie | watching | episode 17 | score 9",
            SensitiveDiagnosticValueClass.USERNAME to
                "private-user@example.test",
        )

        val distinctiveFragments = listOf(
            "ZmFrZS1hY2Nlc3M",
            "FAKE0d31",
            "SplxlOBeZQQYbYS6WxSbIA",
            "dBjftJeZ4CVP",
            "E9Melhoa2Owv",
            "3d17c1b2a9e84f60",
            "1234567890abcdef",
            "987654321012345678",
            "anisyncplus-debug://",
            "private-user",
            "Cowboy Bebop",
        )
    }
}
