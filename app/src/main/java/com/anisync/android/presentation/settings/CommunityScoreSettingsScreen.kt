package com.anisync.android.presentation.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisync.android.R
import com.anisync.android.data.CommunityScoreMode

@Composable
fun CommunityScoreSettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CommunityScoreSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshCacheStats() }

    SettingsScreenScaffold(
        title = stringResource(R.string.anisync_plus_mal_settings),
        onBackClick = onBackClick,
        modifier = modifier
    ) {
        SettingsSectionLabel(stringResource(R.string.community_score_display))
        SettingsGroup {
            CommunityScoreMode.entries.forEach { mode ->
                RadioSettingsItem(
                    title = communityScoreModeTitle(mode),
                    subtitle = communityScoreModeDescription(mode),
                    selected = state.mode == mode,
                    onClick = { viewModel.setMode(mode) }
                )
            }
        }

        SettingsSectionLabel(stringResource(R.string.community_score_beta_info))
        SettingsGroup {
            SettingsItem(
                title = stringResource(R.string.community_score_read_only),
                subtitle = stringResource(R.string.community_score_read_only_desc),
                icon = Icons.Outlined.Info,
                enabled = false,
                onClick = {}
            )
            SettingsItem(
                title = stringResource(R.string.community_score_cache_policy),
                subtitle = stringResource(R.string.community_score_cache_policy_desc),
                icon = Icons.Outlined.Refresh,
                enabled = false,
                onClick = {}
            )
        }

        SettingsSectionLabel(stringResource(R.string.community_score_cache))
        SettingsGroup {
            DiagnosticItem(
                stringResource(R.string.community_score_cache_entries),
                "${state.cache.entries} · ${state.cache.freshEntries} fresh · " +
                    "${state.cache.staleEntries} stale · ${state.cache.unavailableEntries} unavailable"
            )
            DiagnosticItem(
                stringResource(R.string.community_score_session_requests),
                "${state.runtime.requests} requests · ${state.runtime.cacheHits} cache · " +
                    "${state.runtime.notModified} not modified · ${state.runtime.rateLimited} rate limited"
            )
            state.runtime.lastErrorType?.let {
                DiagnosticItem(
                    stringResource(R.string.anisync_plus_last_error),
                    stringResource(R.string.community_score_sanitized_error)
                )
            }
            SettingsItem(
                title = stringResource(R.string.community_score_clear_cache),
                subtitle = stringResource(R.string.community_score_clear_cache_desc),
                icon = Icons.Outlined.DeleteSweep,
                enabled = !state.clearing && state.cache.entries > 0,
                onClick = viewModel::clearCache
            )
        }
    }
}

@Composable
private fun communityScoreModeTitle(mode: CommunityScoreMode): String = when (mode) {
    CommunityScoreMode.ANILIST -> stringResource(R.string.community_score_anilist_only)
    CommunityScoreMode.MYANIMELIST -> stringResource(R.string.community_score_mal_only)
    CommunityScoreMode.BOTH -> stringResource(R.string.community_score_both)
}

@Composable
private fun communityScoreModeDescription(mode: CommunityScoreMode): String = when (mode) {
    CommunityScoreMode.ANILIST -> stringResource(R.string.community_score_anilist_only_desc)
    CommunityScoreMode.MYANIMELIST -> stringResource(R.string.community_score_mal_only_desc)
    CommunityScoreMode.BOTH -> stringResource(R.string.community_score_both_desc)
}
