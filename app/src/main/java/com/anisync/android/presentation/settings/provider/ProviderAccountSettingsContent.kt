package com.anisync.android.presentation.settings.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.anisync.android.R
import com.anisync.android.domain.provider.ActiveProvider
import com.anisync.android.presentation.settings.SettingsGroup
import com.anisync.android.presentation.settings.SettingsItem
import com.anisync.android.presentation.settings.SettingsSectionLabel
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
fun ProviderAccountSettingsContent(
    state: ProviderAccountSettingsUiState,
    onAction: (ProviderAccountAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsSectionLabel(text = stringResource(R.string.provider_account_section_summary))
        ProviderAccountSummaryCard(state)

        SettingsSectionLabel(text = stringResource(R.string.provider_account_section_data))
        SettingsGroup {
            if (ProviderAccountAction.DISCONNECT_AND_DELETE_LOCAL_DATA in state.availableActions) {
                SettingsItem(
                    title = stringResource(R.string.provider_account_disconnect_delete_title),
                    subtitle = stringResource(R.string.provider_account_disconnect_delete_summary),
                    enabled = !state.busy,
                    onClick = {
                        onAction(ProviderAccountAction.DISCONNECT_AND_DELETE_LOCAL_DATA)
                    },
                )
            }
            if (ProviderAccountAction.CHANGE_PROVIDER in state.availableActions) {
                SettingsItem(
                    title = stringResource(R.string.provider_account_change_provider_title),
                    subtitle = stringResource(R.string.provider_account_change_provider_summary),
                    enabled = !state.busy,
                    onClick = { onAction(ProviderAccountAction.CHANGE_PROVIDER) },
                )
            }
            if (ProviderAccountAction.REVOKE_MAL_CONSENT in state.availableActions) {
                SettingsItem(
                    title = stringResource(R.string.provider_account_revoke_mal_consent_title),
                    subtitle = stringResource(R.string.provider_account_revoke_mal_consent_summary),
                    enabled = !state.busy,
                    onClick = { onAction(ProviderAccountAction.REVOKE_MAL_CONSENT) },
                )
            }
        }

        Text(
            text = stringResource(R.string.provider_account_shared_settings_notice),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        state.error?.let {
            Text(
                text = when (it) {
                    ProviderAccountSettingsError.LOCAL_ACTION_FAILED ->
                        stringResource(R.string.provider_account_local_action_failed)
                    ProviderAccountSettingsError.LOCAL_STATE_UNAVAILABLE ->
                        stringResource(R.string.provider_account_local_state_unavailable)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        if (state.busy) {
            Spacer(modifier = Modifier.height(4.dp))
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun ProviderAccountSummaryCard(state: ProviderAccountSettingsUiState) {
    val providerLabel = providerLabel(state.activeProvider)
    val sessionLabel = sessionLabel(state.sessionState)
    val semanticDescription = stringResource(
        R.string.provider_account_status_content_description,
        providerLabel,
        sessionLabel,
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = semanticDescription },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = providerLabel,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = sessionLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = if (state.accountRecordPresent) {
                    stringResource(R.string.provider_account_record_present)
                } else {
                    stringResource(R.string.provider_account_record_absent)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.expiryEpochMillis?.let { expiry ->
                Text(
                    text = stringResource(
                        R.string.provider_account_expiry,
                        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(expiry)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun providerLabel(provider: ActiveProvider): String = when (provider) {
    ActiveProvider.ANILIST_ONLY -> stringResource(R.string.provider_account_anilist)
    ActiveProvider.MAL_ONLY -> stringResource(R.string.provider_account_myanimelist)
    ActiveProvider.UNCONFIGURED -> stringResource(R.string.provider_account_not_configured)
}

@Composable
private fun sessionLabel(state: ProviderSessionDisplayState): String = when (state) {
    ProviderSessionDisplayState.NOT_CONFIGURED ->
        stringResource(R.string.provider_account_session_not_configured)
    ProviderSessionDisplayState.TRANSITIONING ->
        stringResource(R.string.provider_account_session_transitioning)
    ProviderSessionDisplayState.CONNECTED ->
        stringResource(R.string.provider_account_session_connected)
    ProviderSessionDisplayState.EXPIRING ->
        stringResource(R.string.provider_account_session_expiring)
    ProviderSessionDisplayState.MISSING ->
        stringResource(R.string.provider_account_session_missing)
    ProviderSessionDisplayState.EXPIRED ->
        stringResource(R.string.provider_account_session_expired)
    ProviderSessionDisplayState.CORRUPT ->
        stringResource(R.string.provider_account_session_corrupt)
    ProviderSessionDisplayState.KEYSTORE_RESET ->
        stringResource(R.string.provider_account_session_keystore_reset)
    ProviderSessionDisplayState.UNKNOWN ->
        stringResource(R.string.provider_account_session_unknown)
}
