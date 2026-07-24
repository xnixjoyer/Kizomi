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
    fun `opening dashboard reads local snapshot and performs zero network calls`() =
        runTest(dispatcher) {
            val source = RecordingLocalSource()
            val viewModel = DebugIntegrationDashboardViewModel(source, SavedStateHandle())

            advanceUntilIdle()

            assertEquals(1, source.localReads)
            assertEquals(0, source.networkCalls)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun `expanded sections survive view model recreation`() = runTest(dispatcher) {
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
        assertEquals(0, source.networkCalls)
    }

    private class RecordingLocalSource : IntegrationDiagnosticsSnapshotSource {
        var localReads = 0
        var networkCalls = 0

        override suspend fun snapshot(): IntegrationDiagnosticsSnapshot {
            localReads += 1
            return IntegrationDiagnosticsSnapshot(
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
}
