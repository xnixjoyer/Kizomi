package com.anisync.android.presentation.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisync.android.data.diagnostics.DiagnosticsDashboardSection
import com.anisync.android.data.diagnostics.IntegrationDiagnosticsSnapshot
import com.anisync.android.presentation.settings.SettingsScreenScaffold
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
fun DebugIntegrationDashboardScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DebugIntegrationDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    SettingsScreenScaffold(
        title = "Integration diagnostics",
        onBackClick = onBackClick,
        modifier = modifier,
    ) {
        Text(
            text = "Read-only local state. Opening this screen performs no provider request.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = viewModel::refreshLocalSnapshot,
                modifier = Modifier.weight(1f),
            ) {
                Text("Reload local state")
            }
            FilledTonalButton(
                onClick = {
                    clipboard.setText(AnnotatedString(viewModel.sanitizedExport()))
                },
                enabled = state.snapshot != null,
                modifier = Modifier.weight(1f),
            ) {
                Text("Copy sanitized summary")
            }
        }

        if (state.isLoading) {
            CircularProgressIndicator()
        }

        state.error?.let {
            Text(
                text = "Local diagnostics are unavailable.",
                color = MaterialTheme.colorScheme.error,
            )
        }

        state.snapshot?.let { snapshot ->
            DashboardContents(
                snapshot = snapshot,
                expandedSections = state.expandedSections,
                onToggleSection = viewModel::toggleSection,
            )
        }
    }
}

@Composable
private fun DashboardContents(
    snapshot: IntegrationDiagnosticsSnapshot,
    expandedSections: Set<DiagnosticsDashboardSection>,
    onToggleSection: (DiagnosticsDashboardSection) -> Unit,
) {
    DashboardSection(
        title = "Build and source",
        section = DiagnosticsDashboardSection.BUILD_AND_SOURCE,
        expanded = DiagnosticsDashboardSection.BUILD_AND_SOURCE in expandedSections,
        onToggle = onToggleSection,
    ) {
        StatusRow("Version", "${snapshot.build.versionName} (${snapshot.build.versionCode})")
        StatusRow("Build type", snapshot.build.buildType)
        StatusRow("Source revision", snapshot.build.sourceRevision ?: "not embedded")
        StatusRow("OAuth environment", snapshot.build.oauthEnvironment)
        StatusRow("Redirect scheme", snapshot.build.redirectScheme)
        StatusRow("Redirect host", snapshot.build.redirectHost)
        StatusRow("Redirect path", snapshot.build.redirectPath)
        StatusRow("Public client ID present", snapshot.build.clientIdPresent.toString())
        StatusRow(
            "Database schema",
            snapshot.build.databaseSchemaVersion?.toString() ?: "unknown",
        )
    }

    DashboardSection(
        title = "Authentication health",
        section = DiagnosticsDashboardSection.AUTHENTICATION,
        expanded = DiagnosticsDashboardSection.AUTHENTICATION in expandedSections,
        onToggle = onToggleSection,
    ) {
        StatusRow("Active provider", snapshot.session.activeProvider.name)
        StatusRow("Transition phase", snapshot.session.transitionPhase.name)
        StatusRow("Configuration", snapshot.session.configuration.name)
        StatusRow("Session", snapshot.session.sessionState.name)
        StatusRow("Pending OAuth transaction", snapshot.session.pendingOAuthTransaction.name)
        StatusRow("Token vault", snapshot.session.tokenVaultHealth.name)
        StatusRow("Account record present", snapshot.session.accountRecordPresent.toString())
        StatusRow("Last restoration", formatEpoch(snapshot.session.lastSuccessfulRestoreEpochMillis))
        StatusRow("Last refresh outcome", snapshot.session.lastRefreshOutcome ?: "none")
        StatusRow("Last refresh", formatEpoch(snapshot.session.lastRefreshEpochMillis))
    }

    DashboardSection(
        title = "Provider isolation",
        section = DiagnosticsDashboardSection.PROVIDER_ISOLATION,
        expanded = DiagnosticsDashboardSection.PROVIDER_ISOLATION in expandedSections,
        onToggle = onToggleSection,
    ) {
        StatusRow(
            "Active-provider requests",
            snapshot.runtime.activeProviderRequestCount.toString(),
        )
        StatusRow(
            "Blocked inactive-provider requests",
            snapshot.runtime.blockedInactiveProviderRequestCount.toString(),
        )
        StatusRow("Active workers", snapshot.runtime.activeWorkerCount.toString())
        StatusRow("Provider-bound widgets", snapshot.runtime.providerBoundWidgetCount.toString())
        StatusRow("Network kill switch", snapshot.runtime.networkKillSwitchEnabled.toString())
        StatusRow("Last provider change", snapshot.runtime.lastProviderChangeResult ?: "none")
    }

    DashboardSection(
        title = "Request and cache diagnostics",
        section = DiagnosticsDashboardSection.REQUEST_AND_CACHE,
        expanded = DiagnosticsDashboardSection.REQUEST_AND_CACHE in expandedSections,
        onToggle = onToggleSection,
    ) {
        StatusRow("Last request category", snapshot.runtime.lastSuccessfulRequestCategory ?: "none")
        StatusRow("Last request", formatEpoch(snapshot.runtime.lastSuccessfulRequestEpochMillis))
        StatusRow("Last failure category", snapshot.runtime.lastFailureCategory ?: "none")
        StatusRow("Last failure HTTP class", snapshot.runtime.lastFailureHttpClass ?: "none")
        StatusRow("Last failure", formatEpoch(snapshot.runtime.lastFailureEpochMillis))
        StatusRow("Cache hits", snapshot.runtime.cacheHitCount.toString())
        StatusRow("Cache misses", snapshot.runtime.cacheMissCount.toString())
        StatusRow("Coalesced requests", snapshot.runtime.coalescedRequestCount.toString())
        StatusRow("Retries", snapshot.runtime.retryCount.toString())
        StatusRow("Writes", snapshot.runtime.writeCount.toString())
        StatusRow(
            "Pending tracking commands",
            snapshot.runtime.pendingTrackingCommandCount.toString(),
        )
        StatusRow(
            "Last write/read-back",
            formatEpoch(snapshot.runtime.lastSuccessfulWriteReadBackEpochMillis),
        )
    }

    DashboardSection(
        title = "Feature coverage",
        section = DiagnosticsDashboardSection.FEATURE_COVERAGE,
        expanded = DiagnosticsDashboardSection.FEATURE_COVERAGE in expandedSections,
        onToggle = onToggleSection,
    ) {
        snapshot.parity.forEach { item -> StatusRow(item.key, item.status.name) }
    }

    DashboardSection(
        title = "Acceptance checklist",
        section = DiagnosticsDashboardSection.ACCEPTANCE_CHECKLIST,
        expanded = DiagnosticsDashboardSection.ACCEPTANCE_CHECKLIST in expandedSections,
        onToggle = onToggleSection,
    ) {
        snapshot.checklist.forEach { item ->
            val value = buildString {
                append(if (item.passed) "pass" else "pending")
                item.detail?.let { append(" — ").append(it) }
            }
            StatusRow(item.key, value)
        }
        StatusRow("Snapshot captured", formatEpoch(snapshot.capturedAtEpochMillis))
    }
}

@Composable
private fun DashboardSection(
    title: String,
    section: DiagnosticsDashboardSection,
    expanded: Boolean,
    onToggle: (DiagnosticsDashboardSection) -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            TextButton(
                onClick = { onToggle(section) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (expanded) "$title — collapse" else "$title — expand",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    val safeLabel = DiagnosticRedactor.sanitizeCategory(label)
    val safeValue = DiagnosticRedactor.sanitizeCategory(value)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = DiagnosticsStatusSemantics.contentDescription(
                    safeLabel,
                    safeValue,
                )
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = safeLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = safeValue,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun formatEpoch(epochMillis: Long?): String = epochMillis
    ?.takeIf { it > 0L }
    ?.let { DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(it)) }
    ?: "never"
