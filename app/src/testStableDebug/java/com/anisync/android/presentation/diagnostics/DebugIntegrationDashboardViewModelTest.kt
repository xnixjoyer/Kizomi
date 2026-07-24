package com.anisync.android.presentation.diagnostics

import androidx.lifecycle.SavedStateHandle
import com.anisync.android.data.diagnostics.DiagnosticAvailability
import com.anisync.android.data.diagnostics.DiagnosticSessionState
import com.anisync.android.data.diagnostics.DiagnosticsBuildMetadata
import com.anisync.android.data.diagnostics.DiagnosticsDashboardSection
import com.anisync.android.data.diagnostics.DiagnosticsRuntimeMetrics
import com.anisync.android.data.diagnostics.DiagnosticsSessionMetadata
import com.anisync.android.data.diagnostics.IntegrationDiagnosticsSnapshot
import com.anisync.android.data.diagnostics.IntegrationDiagnosticsSnapshotSource
import com.anisync.android.domain.provider.ActiveProvider
import com.anisync.android.domain.provider.ProviderTransitionPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebugIntegrationDashboardViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `opening and local reload perform zero network calls and keep unknowns truthful`() =
        runTest(dispatcher) {
            val source = RecordingLocalSource()
            val viewModel = DebugIntegrationDashboardViewModel(source, SavedStateHandle())

            advanceUntilIdle()
            viewModel.refreshLocalSnapshot()
            advanceUntilIdle()

            val snapshot = viewModel.uiState.value.snapshot
            assertNotNull(snapshot)
            assertEquals(2, source.localReads)
            assertEquals(0, source.networkCalls)
            assertFalse(viewModel.uiState.value.isLoading)
            assertNull(snapshot?.build?.sourceRevision)
            assertEquals(
                DiagnosticAvailability.UNKNOWN,
                snapshot?.session?.pendingOAuthTransaction,
            )
            assertEquals(DiagnosticsRuntimeMetrics(), snapshot?.runtime)
            assertNull(snapshot?.runtime?.blockedInactiveProviderRequestCount)
        }

    @Test
    fun `malformed local snapshot failure is recoverable on local reload`() = runTest(dispatcher) {
        val source = RecoveringLocalSource()
        val viewModel = DebugIntegrationDashboardViewModel(source, SavedStateHandle())

        advanceUntilIdle()
        assertEquals(
            IntegrationDiagnosticsDashboardError.LOCAL_SNAPSHOT_UNAVAILABLE,
            viewModel.uiState.value.error,
        )
        assertNull(viewModel.uiState.value.snapshot)

        viewModel.refreshLocalSnapshot()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertNotNull(viewModel.uiState.value.snapshot)
        assertEquals(2, source.localReads)
        assertEquals(0, source.networkCalls)
    }

    @Test
    fun `copied diagnostics omit marker-free token code ID URL payload and private content`() =
        runTest(dispatcher) {
            val secrets = listOf(
                "eyJhbGciOiJSUzI1NiJ9.ZmFrZS1jb3B5LWFjY2Vzcw.c2lnbmF0dXJl",
                "def50200FAKEcopyrefreshmaterial729e6f5c2a44c0f8d5b1a9e7c3f0",
                "SplxlOBeZQQYbYS6WxSbIA",
                "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk-copy-verifier",
                "1234567890abcdef1234567890abcdef",
                "987654321012345678",
                "anisyncplus-debug://oauth/mal/callback?code=SplxlOBeZQQYbYS6WxSbIA",
                "{\"id\":987654321,\"title\":\"Cowboy Bebop\",\"user\":\"private-user\"}",
                "Cowboy Bebop The Movie episode 17 score 9",
                "private-user@example.test",
            )
            val base = defaultSnapshot()
            val snapshot = base.copy(
                build = base.build.copy(
                    sourceRevision = secrets[0],
                    oauthEnvironment = secrets[1],
                    redirectScheme = secrets[6],
                    redirectHost = secrets[4],
                    redirectPath = secrets[2],
                ),
                session = base.session.copy(lastRefreshOutcome = secrets[3]),
                runtime = DiagnosticsRuntimeMetrics(
                    lastSuccessfulRequestCategory = secrets[7],
                    lastFailureCategory = secrets[8],
                    lastProviderChangeResult = secrets[9],
                ),
            )
            val source = RecordingLocalSource(snapshot)
            val viewModel = DebugIntegrationDashboardViewModel(source, SavedStateHandle())

            advanceUntilIdle()
            val copied = viewModel.sanitizedExport()

            assertTrue(copied.contains("activeProvider=UNCONFIGURED"))
            assertTrue(copied.contains("pendingOAuth=UNKNOWN"))
            assertTrue(copied.contains("blockedInactiveRequests=unknown"))
            assertTrue(copied.contains(DiagnosticRedactor.REDACTED))
            secrets.forEach { secret ->
                assertFalse("Copied diagnostics leaked $secret", copied.contains(secret))
            }
            listOf(
                "ZmFrZS1jb3B5",
                "FAKEcopyrefresh",
                "SplxlOBeZQQYbYS6WxSbIA",
                "dBjftJeZ4CVP",
                "1234567890abcdef",
                "987654321012345678",
                "anisyncplus-debug://",
                "Cowboy Bebop",
                "private-user",
            ).forEach { secretFragment ->
                assertFalse("Copied diagnostics leaked $secretFragment", copied.contains(secretFragment))
            }
            assertEquals(0, source.networkCalls)
        }

    @Test
    fun `expanded sections survive recreation and malformed saved names are ignored`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val source = RecordingLocalSource()
            val first = DebugIntegrationDashboardViewModel(source, handle)
            advanceUntilIdle()

            first.toggleSection(DiagnosticsDashboardSection.BUILD_AND_SOURCE)
            assertFalse(
                DiagnosticsDashboardSection.BUILD_AND_SOURCE in
                    first.uiState.value.expandedSections,
            )

            val recreated = DebugIntegrationDashboardViewModel(source, handle)
            advanceUntilIdle()
            assertFalse(
                DiagnosticsDashboardSection.BUILD_AND_SOURCE in
                    recreated.uiState.value.expandedSections,
            )

            val malformedHandle = SavedStateHandle(
                mapOf(
                    "diagnostics_expanded_sections" to
                        arrayListOf("NOT_A_SECTION", DiagnosticsDashboardSection.AUTHENTICATION.name),
                ),
            )
            val malformedRestored = DebugIntegrationDashboardViewModel(source, malformedHandle)
            advanceUntilIdle()

            assertEquals(
                setOf(DiagnosticsDashboardSection.AUTHENTICATION),
                malformedRestored.uiState.value.expandedSections,
            )
            assertEquals(0, source.networkCalls)
        }

    private class RecordingLocalSource(
        private val snapshotValue: IntegrationDiagnosticsSnapshot = defaultSnapshot(),
    ) : IntegrationDiagnosticsSnapshotSource {
        var localReads = 0
        var networkCalls = 0

        override suspend fun snapshot(): IntegrationDiagnosticsSnapshot {
            localReads += 1
            return snapshotValue
        }
    }

    private class RecoveringLocalSource : IntegrationDiagnosticsSnapshotSource {
        var localReads = 0
        var networkCalls = 0

        override suspend fun snapshot(): IntegrationDiagnosticsSnapshot {
            localReads += 1
            if (localReads == 1) error("malformed local state")
            return defaultSnapshot()
        }
    }

    private companion object {
        fun defaultSnapshot(): IntegrationDiagnosticsSnapshot = IntegrationDiagnosticsSnapshot(
            build = DiagnosticsBuildMetadata(
                versionName = "test",
                versionCode = 1L,
                buildType = "debug",
                sourceRevision = null,
                oauthEnvironment = "DEBUG",
                redirectScheme = "test",
                redirectHost = "oauth",
                redirectPath = "/mal/callback",
                clientIdPresent = false,
                databaseSchemaVersion = 28,
            ),
            session = DiagnosticsSessionMetadata(
                activeProvider = ActiveProvider.UNCONFIGURED,
                transitionPhase = ProviderTransitionPhase.IDLE,
                configuration = DiagnosticAvailability.UNKNOWN,
                sessionState = DiagnosticSessionState.NOT_CONFIGURED,
                pendingOAuthTransaction = DiagnosticAvailability.UNKNOWN,
                tokenVaultHealth = DiagnosticAvailability.ABSENT,
                accountRecordPresent = false,
                lastSuccessfulRestoreEpochMillis = null,
                lastRefreshOutcome = null,
                lastRefreshEpochMillis = null,
            ),
            runtime = DiagnosticsRuntimeMetrics(),
            parity = emptyList(),
            checklist = emptyList(),
            capturedAtEpochMillis = 1L,
        )
    }
}
