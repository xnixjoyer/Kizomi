package com.anisync.android.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisync.android.R
import com.anisync.android.data.tracking.TrackingProviderConflict
import com.anisync.android.data.tracking.TrackingSagaOperation
import com.anisync.android.data.tracking.TrackingSagaTarget
import com.anisync.android.domain.tracking.TrackingProvider

@Composable
fun TrackingCenterScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrackingCenterViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    state.resolutionFailure?.let { failure ->
        AlertDialog(
            onDismissRequest = viewModel::dismissResolutionFailure,
            title = { Text(stringResource(R.string.tracking_center_resolution_failed)) },
            text = {
                Text(
                    stringResource(
                        R.string.tracking_center_resolution_failed_detail,
                        failure.name,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissResolutionFailure) {
                    Text(stringResource(R.string.tracking_center_resolution_dismiss))
                }
            },
        )
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.tracking_center_title),
        onBackClick = onBackClick,
        modifier = modifier,
    ) {
        SettingsSectionLabel(stringResource(R.string.tracking_center_identity_section))
        SettingsGroup {
            SettingsItem(
                title = stringResource(R.string.tracking_center_identity_issues),
                subtitle = stringResource(
                    R.string.tracking_center_identity_counts,
                    state.unresolvedIdentityCount,
                    state.conflictingIdentityCount,
                ),
                onClick = viewModel::refreshIdentityIssues,
            )
        }

        SettingsSectionLabel(stringResource(R.string.tracking_center_operations_section))
        if (state.operations.isEmpty()) {
            Text(
                stringResource(R.string.tracking_center_operations_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.operations.forEach { operation ->
                OperationCard(operation, viewModel::retryFailed)
            }
        }

        SettingsSectionLabel(stringResource(R.string.tracking_center_conflicts_section))
        if (state.conflicts.isEmpty()) {
            Text(
                stringResource(R.string.tracking_center_conflicts_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.conflicts.forEach { conflict ->
                ConflictCard(conflict, viewModel::resolveConflict)
            }
        }
    }
}

@Composable
private fun OperationCard(
    operation: TrackingSagaOperation,
    retry: (String, TrackingProvider) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(
                    R.string.tracking_center_operation_summary,
                    operation.mediaType.name,
                    operation.state.name,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                operation.fieldNames.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            operation.targets.forEach { target ->
                TargetRow(target) { retry(operation.operationId, target.provider) }
            }
        }
    }
}

@Composable
private fun TargetRow(
    target: TrackingSagaTarget,
    retry: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(
                    R.string.tracking_center_provider_result,
                    target.provider.name,
                    target.state.name,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            target.failureKind?.let { failure ->
                Text(
                    stringResource(
                        R.string.tracking_center_failure_detail,
                        failure.name,
                        target.attemptCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (target.retryableByUser) {
            Button(onClick = retry) {
                Text(stringResource(R.string.tracking_center_retry_provider, target.provider.name))
            }
        }
    }
}

@Composable
private fun ConflictCard(
    conflict: TrackingProviderConflict,
    resolve: (TrackingProviderConflict, TrackingProvider) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(conflict.title, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    R.string.tracking_center_conflict_fields,
                    conflict.differingFields.joinToString(", ") { it.name },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.tracking_center_no_auto_resolution),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            ResolutionAction(
                source = TrackingProvider.ANILIST,
                conflict = conflict,
                onClick = { resolve(conflict, TrackingProvider.ANILIST) },
            )
            ResolutionAction(
                source = TrackingProvider.MYANIMELIST,
                conflict = conflict,
                onClick = { resolve(conflict, TrackingProvider.MYANIMELIST) },
            )
        }
    }
}

@Composable
private fun ResolutionAction(
    source: TrackingProvider,
    conflict: TrackingProviderConflict,
    onClick: () -> Unit,
) {
    val blockedFields = conflict.blockedFieldsWhenUsing(source)
    val blocker = conflict.resolutionBlockerWhenUsing(source)
    OutlinedButton(
        onClick = onClick,
        enabled = blocker == null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            stringResource(
                if (source == TrackingProvider.ANILIST) {
                    R.string.tracking_center_use_anilist
                } else {
                    R.string.tracking_center_use_mal
                }
            )
        )
    }
    when {
        blockedFields.isNotEmpty() -> Text(
            stringResource(
                R.string.tracking_center_resolution_blocked_fields,
                blockedFields.joinToString(", ") { it.name },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        blocker != null -> Text(
            stringResource(
                R.string.tracking_center_resolution_blocked_reason,
                blocker.name,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
