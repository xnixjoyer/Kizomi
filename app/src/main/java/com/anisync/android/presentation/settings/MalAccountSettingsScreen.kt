package com.anisync.android.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisync.android.R
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingMode
import com.anisync.android.util.AppLinksUtil

@Composable
fun MalAccountSettingsScreen(
    onBackClick: () -> Unit,
    onBrowseMal: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MalAccountSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MalAccountSettingsEffect.OpenBrowser -> {
                    AppLinksUtil.openInBrowser(context, effect.authorizationUrl)
                }
            }
        }
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.settings_myanimelist_account),
        onBackClick = onBackClick,
        modifier = modifier,
    ) {
        SettingsSectionLabel(stringResource(R.string.tracking_provider_policy_section))
        Text(
            text = stringResource(R.string.tracking_provider_policy_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TrackingModeGroup(
            title = stringResource(R.string.tracking_provider_anime),
            selected = state.animeTrackingMode,
            onSelected = { mode ->
                viewModel.setTrackingMode(TrackingMediaType.ANIME, mode)
            },
        )
        TrackingModeGroup(
            title = stringResource(R.string.tracking_provider_manga),
            selected = state.mangaTrackingMode,
            onSelected = { mode ->
                viewModel.setTrackingMode(TrackingMediaType.MANGA, mode)
            },
        )
        if (!state.configured &&
            (state.animeTrackingMode != TrackingMode.ANILIST_ONLY ||
                state.mangaTrackingMode != TrackingMode.ANILIST_ONLY)
        ) {
            Text(
                text = stringResource(R.string.tracking_provider_mal_blocked),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        SettingsSectionLabel(stringResource(R.string.mal_account_status_section))
        SettingsItem(
            title = statusTitle(state.connectionState),
            subtitle = statusSubtitle(state),
            icon = if (state.connectionState == MalAccountConnectionState.CONNECTED) {
                Icons.Outlined.Link
            } else {
                Icons.Outlined.Lock
            },
            onClick = {},
            enabled = false,
            trailingContent = {
                if (state.isBusy) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                }
            },
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state.connectionState) {
                MalAccountConnectionState.NOT_CONFIGURED -> {
                    Text(
                        text = stringResource(R.string.mal_login_not_configured_detail),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MalAccountConnectionState.CONNECTED -> {
                    Button(
                        onClick = onBrowseMal,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Explore, contentDescription = null)
                        Text(
                            text = stringResource(R.string.mal_browse_catalog),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    OutlinedButton(
                        onClick = viewModel::logout,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Logout, contentDescription = null)
                        Text(
                            text = stringResource(R.string.mal_logout_local),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                MalAccountConnectionState.ERROR -> {
                    Button(
                        onClick = viewModel::retryPendingCompletion,
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.mal_retry_sign_in))
                    }
                    OutlinedButton(
                        onClick = viewModel::dismissError,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
                else -> {
                    Button(
                        onClick = viewModel::connect,
                        enabled = state.configured && !state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (state.connectionState == MalAccountConnectionState.RELOGIN_REQUIRED) {
                                stringResource(R.string.mal_relogin)
                            } else {
                                stringResource(R.string.mal_connect)
                            }
                        )
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.mal_browser_security_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrackingModeGroup(
    title: String,
    selected: TrackingMode,
    onSelected: (TrackingMode) -> Unit,
) {
    SettingsSectionLabel(title)
    SettingsGroup {
        TrackingMode.entries.forEach { mode ->
            RadioSettingsItem(
                title = trackingModeTitle(mode),
                subtitle = trackingModeDescription(mode),
                selected = selected == mode,
                onClick = { onSelected(mode) },
            )
        }
    }
}

@Composable
private fun trackingModeTitle(mode: TrackingMode): String = when (mode) {
    TrackingMode.ANILIST_ONLY -> stringResource(R.string.tracking_mode_anilist)
    TrackingMode.MYANIMELIST_ONLY -> stringResource(R.string.tracking_mode_mal)
    TrackingMode.DUAL -> stringResource(R.string.tracking_mode_dual)
}

@Composable
private fun trackingModeDescription(mode: TrackingMode): String = when (mode) {
    TrackingMode.ANILIST_ONLY -> stringResource(R.string.tracking_mode_anilist_desc)
    TrackingMode.MYANIMELIST_ONLY -> stringResource(R.string.tracking_mode_mal_desc)
    TrackingMode.DUAL -> stringResource(R.string.tracking_mode_dual_desc)
}

@Composable
private fun statusTitle(state: MalAccountConnectionState): String = when (state) {
    MalAccountConnectionState.NOT_CONFIGURED -> stringResource(R.string.mal_login_not_configured)
    MalAccountConnectionState.DISCONNECTED -> stringResource(R.string.mal_disconnected)
    MalAccountConnectionState.OPENING_BROWSER -> stringResource(R.string.mal_opening_browser)
    MalAccountConnectionState.AWAITING_CALLBACK -> stringResource(R.string.mal_awaiting_callback)
    MalAccountConnectionState.PROCESSING -> stringResource(R.string.mal_processing_login)
    MalAccountConnectionState.CONNECTED -> stringResource(R.string.mal_connected)
    MalAccountConnectionState.RELOGIN_REQUIRED -> stringResource(R.string.mal_relogin_required)
    MalAccountConnectionState.ERROR -> stringResource(R.string.mal_login_failed)
}

@Composable
private fun statusSubtitle(state: MalAccountSettingsUiState): String? = when {
    state.connectionState == MalAccountConnectionState.CONNECTED && state.displayName != null ->
        state.displayName
    state.connectionState == MalAccountConnectionState.CONNECTED ->
        stringResource(R.string.mal_connected_local_account)
    state.connectionState == MalAccountConnectionState.ERROR && state.retryAfterSeconds != null ->
        stringResource(R.string.mal_retry_after_seconds, state.retryAfterSeconds)
    state.connectionState == MalAccountConnectionState.ERROR ->
        stringResource(R.string.mal_error_sanitized, state.failureReason?.name ?: "UNKNOWN")
    else -> null
}
