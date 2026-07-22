package com.anisync.android.presentation.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisync.android.R
import com.anisync.android.domain.MalSearchCandidate
import com.anisync.android.domain.CommunityScoreFailure
import com.anisync.android.domain.CommunityScoreFailureType
import com.anisync.android.presentation.components.AppCircularProgressIndicator
import kotlin.math.roundToInt

@Composable
fun MalScoreResolverDialog(
    state: MalScoreResolverState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onConfirm: (MalSearchCandidate) -> Unit
) {
    if (!state.isVisible) return
    AlertDialog(
        onDismissRequest = { if (!state.isBinding) onDismiss() },
        title = { Text(stringResource(R.string.mal_resolver_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.mal_resolver_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    state.isLoading -> Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        AppCircularProgressIndicator()
                    }
                    state.candidates.isNotEmpty() -> LazyColumn(
                        modifier = Modifier.heightIn(max = 430.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.candidates, key = MalSearchCandidate::malId) { candidate ->
                            MalCandidateCard(
                                candidate = candidate,
                                enabled = !state.isBinding,
                                onConfirm = { onConfirm(candidate) }
                            )
                        }
                    }
                }
                state.error?.let { error ->
                    Text(
                        text = resolverErrorMessage(error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            if (state.error != null) {
                TextButton(onClick = onRetry, enabled = !state.isLoading && !state.isBinding) {
                    Text(stringResource(R.string.retry))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isBinding) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun resolverErrorMessage(error: CommunityScoreFailure): String = when (error.type) {
    CommunityScoreFailureType.OFFLINE -> stringResource(R.string.mal_resolver_error_offline)
    CommunityScoreFailureType.TRANSPORT -> stringResource(R.string.mal_resolver_error_transport)
    CommunityScoreFailureType.TIMEOUT -> stringResource(R.string.mal_resolver_error_timeout)
    CommunityScoreFailureType.RATE_LIMITED -> stringResource(R.string.mal_resolver_error_rate_limited)
    CommunityScoreFailureType.TEMPORARY_SERVER -> stringResource(R.string.mal_resolver_error_server)
    CommunityScoreFailureType.INVALID_RESPONSE -> stringResource(R.string.mal_resolver_error_invalid_response)
    CommunityScoreFailureType.NO_RESULTS -> stringResource(R.string.mal_resolver_error_no_results)
    CommunityScoreFailureType.PERMANENT -> stringResource(R.string.mal_resolver_error_permanent)
}

@Composable
private fun MalCandidateCard(
    candidate: MalSearchCandidate,
    enabled: Boolean,
    onConfirm: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = candidate.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(
                    candidate.year?.toString(),
                    candidate.format,
                    candidate.episodes?.let { stringResource(R.string.franchise_episode_count, it) },
                    candidate.score?.let { "MAL %.2f".format(it) }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(
                        R.string.mal_resolver_confidence,
                        (candidate.confidence * 100).roundToInt()
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Button(onClick = onConfirm, enabled = enabled) {
                    Text(stringResource(R.string.mal_resolver_use_match))
                }
            }
        }
    }
}
