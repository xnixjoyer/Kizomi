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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisync.android.R
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
        title = stringResource(R.string.diagnostics_screen_title),
        onBackClick = onBackClick,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.diagnostics_local_only_notice),
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
                Text(stringResource(R.string.diagnostics_reload_local_state))
            }
            FilledTonalButton(
                onClick = {
                    clipboard.setText(AnnotatedString(viewModel.sanitizedExport()))
                },
                enabled = state.snapshot != null,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.diagnostics_copy_sanitized_summary))
            }
        }

        if (state.isLoading) {
            CircularProgressIndicator()
        }

        state.error?.let {
            Text(
                text = stringResource(R.string.diagnostics_local_unavailable),
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
    val unknown = stringResource(R.string.diagnostics_value_unknown)

    DashboardSection(
        title = stringResource(R.string.diagnostics_section_build_source),
        section = DiagnosticsDashboardSection.BUILD_AND_SOURCE,
        expanded = DiagnosticsDashboardSection.BUILD_AND_SOURCE in expandedSections,
        onToggle = onToggleSection,
    ) {
        StatusRow(
            stringResource(R.string.diagnostics_label_version),
            stringResource(
                R.string.diagnostics_value_version,
                DiagnosticPresentationBoundary.metadata(snapshot.build.versionName),
                snapshot.build.versionCode,
            ),
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_build_type),
            DiagnosticPresentationBoundary.metadata(snapshot.build.buildType),
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_source_revision),
            snapshot.build.sourceRevision
                ?.let(DiagnosticPresentationBoundary::metadata)
                ?: stringResource(R.string.diagnostics_value_not_embedded),
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_oauth_environment),
            DiagnosticPresentationBoundary.metadata(snapshot.build.oauthEnvironment),
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_redirect_scheme),
            DiagnosticPresentationBoundary.metadata(snapshot.build.redirectScheme),
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_redirect_host),
            DiagnosticPresentationBoundary.metadata(snapshot.build.redirectHost),
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_redirect_path),
            DiagnosticPresentationBoundary.metadata(snapshot.build.redirectPath),
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_client_id_present),
            snapshot.build.clientIdPresent.toString(),
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_database_schema),
            snapshot.build.databaseSchemaVersion?.toString() ?: unknown,
        )
    }

    DashboardSection(
        title = stringResource(R.string.diagnostics_section_authentication),
        section = DiagnosticsDashboardSection.AUTHENTICATION,
        expanded = DiagnosticsDashboardSection.AUTHENTICATION in expandedSections,
        onToggle = onToggleSection,
    ) {
        StatusRow(
            stringResource(R.string.diagnostics_label_active_provider),
            snapshot.session.activeProvider.name,
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_transition_phase),
            snapshot.session.transitionPhase.name,
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_configuration),
            snapshot.session.configuration.name,
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_session),
            snapshot.session.sessionState.name,
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_pending_oauth),
            snapshot.session.pendingOAuthTransaction.name,
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_token_vault),
            snapshot.session.tokenVaultHealth.name,
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_account_record_present),
            snapshot.session.accountRecordPresent.toString(),
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_last_restoration),
            formatEpoch(snapshot.session.lastSuccessfulRestoreEpochMillis, unknown),
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_last_refresh_outcome),
            snapshot.session.lastRefreshOutcome
                ?.let(DiagnosticPresentationBoundary::category)
                ?: unknown,
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_last_refresh),
            formatEpoch(snapshot.session.lastRefreshEpochMillis, unknown),
        )
    }

    DashboardSection(
        title = stringResource(R.string.diagnostics_section_provider_isolation),
        section = DiagnosticsDashboardSection.PROVIDER_ISOLATION,
        expanded = DiagnosticsDashboardSection.PROVIDER_ISOLATION in expandedSections,
        onToggle = onToggleSection,
    ) {
        StatusRow(
            stringResource(R.string.diagnostics_label_active_provider_requests),
            formatMetric(snapshot.runtime.activeProviderRequestCount, unknown),
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_blocked_inactive_requests),
            formatMetric(snapshot.runtime.blockedInactiveProviderRequestCount, unknown),
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_active_workers),
            formatMetric(snapshot.runtime.activeWorkerCount, unknown),
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_provider_bound_widgets),
            formatMetric(snapshot.runtime.providerBoundWidgetCount, unknown),
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_network_kill_switch),
            snapshot.runtime.networkKillSwitchEnabled?.toString() ?: unknown,
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_last_provider_change),
            snapshot.runtime.lastProviderChangeResult
                ?.let(DiagnosticPresentationBoundary::category)
                ?: unknown,
        )
    }

    DashboardSection(
        title = stringResource(R.string.diagnostics_section_request_cache),
        section = DiagnosticsDashboardSection.REQUEST_AND_CACHE,
        expanded = DiagnosticsDashboardSection.REQUEST_AND_CACHE in expandedSections,
        onToggle = onToggleSection,
    ) {
        StatusRow(
            stringResource(R.string.diagnostics_label_last_request_category),
            snapshot.runtime.lastSuccessfulRequestCategory
                ?.let(DiagnosticPresentationBoundary::category)
                ?: unknown,
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_last_request),
            formatEpoch(snapshot.runtime.lastSuccessfulRequestEpochMillis, unknown),
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_last_failure_category),
            snapshot.runtime.lastFailureCategory
                ?.let(DiagnosticPresentationBoundary::category)
                ?: unknown,
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_last_failure_http_class),
            snapshot.runtime.lastFailureHttpClass
                ?.let(DiagnosticPresentationBoundary::category)
                ?: unknown,
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_last_failure),
            formatEpoch(snapshot.runtime.lastFailureEpochMillis, unknown),
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_cache_hits),
            formatMetric(snapshot.runtime.cacheHitCount, unknown),
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_cache_misses),
            formatMetric(snapshot.runtime.cacheMissCount, unknown),
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_coalesced_requests),
            formatMetric(snapshot.runtime.coalescedRequestCount, unknown),
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_retries),
            formatMetric(snapshot.runtime.retryCount, unknown),
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_writes),
            formatMetric(snapshot.runtime.writeCount, unknown),
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_pending_tracking_commands),
            formatMetric(snapshot.runtime.pendingTrackingCommandCount, unknown),
        )
        StatusRow(
            stringResource(R.string.diagnostics_label_last_write_read_back),
            formatEpoch(snapshot.runtime.lastSuccessfulWriteReadBackEpochMillis, unknown),
        )
    }

    DashboardSection(
        title = stringResource(R.string.diagnostics_section_feature_coverage),
        section = DiagnosticsDashboardSection.FEATURE_COVERAGE,
        expanded = DiagnosticsDashboardSection.FEATURE_COVERAGE in expandedSections,
        onToggle = onToggleSection,
    ) {
        snapshot.parity.forEach { item ->
            StatusRow(DiagnosticPresentationBoundary.category(item.key), item.status.name)
        }
    }

    val pass = stringResource(R.string.diagnostics_value_pass)
    val pending = stringResource(R.string.diagnostics_value_pending)
    DashboardSection(
        title = stringResource(R.string.diagnostics_section_acceptance_checklist),
        section = DiagnosticsDashboardSection.ACCEPTANCE_CHECKLIST,
        expanded = DiagnosticsDashboardSection.ACCEPTANCE_CHECKLIST in expandedSections,
        onToggle = onToggleSection,
    ) {
        snapshot.checklist.forEach { item ->
            val value = buildString {
                append(
                    when (item.passed) {
                        true -> pass
                        false -> pending
                        null -> unknown
                    },
                )
                item.detail?.let {
                    append(" — ").append(DiagnosticPresentationBoundary.category(it))
                }
            }
            StatusRow(DiagnosticPresentationBoundary.category(item.key), value)
        }
        StatusRow(
            stringResource(R.string.diagnostics_label_snapshot_captured),
            formatEpoch(snapshot.capturedAtEpochMillis, unknown),
        )
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
                    text = stringResource(
                        if (expanded) {
                            R.string.diagnostics_section_expanded
                        } else {
                            R.string.diagnostics_section_collapsed
                        },
                        title,
                    ),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = DiagnosticsStatusSemantics.contentDescription(
                    label = label,
                    value = value,
                    valueIsAlreadySafe = true,
                )
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun formatMetric(value: Long?, unknown: String): String = value?.toString() ?: unknown

private fun formatEpoch(epochMillis: Long?, unknown: String): String = epochMillis
    ?.takeIf { it > 0L }
    ?.let { DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(it)) }
    ?: unknown
