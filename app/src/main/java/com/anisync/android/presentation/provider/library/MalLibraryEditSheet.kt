package com.anisync.android.presentation.provider.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.anisync.android.R
import com.anisync.android.presentation.components.AppModalBottomSheet
import com.anisync.android.presentation.model.PresentationMediaType

/**
 * Capability-aware shared list editor for MAL-backed rows. Unsupported MAL fields are deliberately
 * absent, and all values are validated before a typed [MalLibraryEditDraft] leaves the sheet.
 */
@Composable
fun MalLibraryEditSheet(
    item: ProviderLibraryItem,
    onDismiss: () -> Unit,
    onSave: (MalLibraryEditDraft) -> Unit,
    onDelete: () -> Unit,
) {
    val initial = remember(item.identity.stableKey) { MalLibraryEditDraft.from(item) }
    var status by rememberSaveable(item.identity.stableKey) { mutableStateOf(initial.status) }
    var progress by rememberSaveable(item.identity.stableKey) {
        mutableStateOf(initial.progress.toString())
    }
    var secondaryProgress by rememberSaveable(item.identity.stableKey) {
        mutableStateOf(initial.secondaryProgress?.toString().orEmpty())
    }
    var score by rememberSaveable(item.identity.stableKey) {
        mutableStateOf(initial.score100?.toString().orEmpty())
    }
    var repeatCount by rememberSaveable(item.identity.stableKey) {
        mutableStateOf(initial.repeatCount.toString())
    }
    var startedAt by rememberSaveable(item.identity.stableKey) {
        mutableStateOf(initial.startedAt.orEmpty())
    }
    var completedAt by rememberSaveable(item.identity.stableKey) {
        mutableStateOf(initial.completedAt.orEmpty())
    }
    var confirmDelete by remember { mutableStateOf(false) }

    val parsedProgress = progress.toIntOrNull()
    val parsedSecondary = secondaryProgress.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
    val parsedScore = score.trim().takeIf(String::isNotEmpty)?.toDoubleOrNull()
    val parsedRepeat = repeatCount.toIntOrNull()
    val normalizedStarted = startedAt.trim().takeIf(String::isNotEmpty)
    val normalizedCompleted = completedAt.trim().takeIf(String::isNotEmpty)
    val isManga = item.identity.mediaType == PresentationMediaType.MANGA
    val valid = parsedProgress != null && parsedProgress >= 0 &&
        (!isManga || secondaryProgress.isBlank() || (parsedSecondary != null && parsedSecondary >= 0)) &&
        (score.isBlank() || (parsedScore != null && parsedScore in 0.0..100.0)) &&
        parsedRepeat != null && parsedRepeat >= 0 &&
        normalizedStarted.isValidOptionalIsoDate() && normalizedCompleted.isValidOptionalIsoDate()

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.mal_library_edit_title, item.card.title),
                style = MaterialTheme.typography.headlineSmall,
            )
            StatusChips(
                selected = status,
                onSelected = { status = it },
            )
            NumericField(
                value = progress,
                onValueChange = { progress = it.filter(Char::isDigit) },
                label = stringResource(R.string.mal_library_progress),
            )
            if (isManga) {
                NumericField(
                    value = secondaryProgress,
                    onValueChange = { secondaryProgress = it.filter(Char::isDigit) },
                    label = stringResource(R.string.mal_library_volume_progress),
                )
            }
            NumericField(
                value = score,
                onValueChange = { next ->
                    score = next.filter { it.isDigit() || it == '.' }.take(6)
                },
                label = stringResource(R.string.mal_library_score),
                decimal = true,
            )
            NumericField(
                value = repeatCount,
                onValueChange = { repeatCount = it.filter(Char::isDigit) },
                label = stringResource(R.string.mal_library_repeat_count),
            )
            OutlinedTextField(
                value = startedAt,
                onValueChange = { startedAt = it.take(10) },
                label = { Text(stringResource(R.string.mal_library_started_at)) },
                isError = !normalizedStarted.isValidOptionalIsoDate(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = completedAt,
                onValueChange = { completedAt = it.take(10) },
                label = { Text(stringResource(R.string.mal_library_completed_at)) },
                isError = !normalizedCompleted.isValidOptionalIsoDate(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text(
                        text = stringResource(R.string.mal_library_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.mal_library_cancel))
                    }
                    Button(
                        enabled = valid,
                        onClick = {
                            onSave(
                                MalLibraryEditDraft(
                                    status = status,
                                    progress = requireNotNull(parsedProgress),
                                    secondaryProgress = if (isManga) parsedSecondary else null,
                                    score100 = parsedScore,
                                    repeatCount = requireNotNull(parsedRepeat),
                                    startedAt = normalizedStarted,
                                    completedAt = normalizedCompleted,
                                )
                            )
                        },
                    ) {
                        Text(stringResource(R.string.mal_library_save))
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.mal_library_delete)) },
            text = { Text(item.card.title) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.mal_library_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.mal_library_cancel))
                }
            },
        )
    }
}

@Composable
private fun StatusChips(
    selected: ProviderLibraryStatus,
    onSelected: (ProviderLibraryStatus) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProviderLibraryStatus.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { status ->
                    FilterChip(
                        selected = selected == status,
                        onClick = { onSelected(status) },
                        label = { Text(editStatusLabel(status)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NumericField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun editStatusLabel(status: ProviderLibraryStatus): String = stringResource(
    when (status) {
        ProviderLibraryStatus.CURRENT -> R.string.mal_library_status_current
        ProviderLibraryStatus.PLANNING -> R.string.mal_library_status_planning
        ProviderLibraryStatus.COMPLETED -> R.string.mal_library_status_completed
        ProviderLibraryStatus.DROPPED -> R.string.mal_library_status_dropped
        ProviderLibraryStatus.PAUSED -> R.string.mal_library_status_paused
        ProviderLibraryStatus.REPEATING -> R.string.mal_library_status_repeating
    }
)

private fun String?.isValidOptionalIsoDate(): Boolean =
    this == null || Regex("\\d{4}-\\d{2}-\\d{2}").matches(this)
