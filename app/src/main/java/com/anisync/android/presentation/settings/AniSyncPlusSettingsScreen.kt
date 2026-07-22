package com.anisync.android.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisync.android.R
import com.anisync.android.data.MainNavigationDestination
import com.anisync.android.data.MainNavigationStartMode
import com.anisync.android.data.UiDensity
import com.anisync.android.presentation.util.LocalAppSettings

@Composable
fun AniSyncPlusSettingsScreen(
    onBackClick: () -> Unit,
    onNavigateAppearance: () -> Unit = {},
    onNavigateCommunityScores: () -> Unit = {},
    onNavigateDiagnostics: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    SettingsScreenScaffold(
        title = stringResource(R.string.settings_anisync_plus),
        onBackClick = onBackClick,
        modifier = modifier
    ) {
        SettingsSectionLabel(stringResource(R.string.public_settings_experience))
        SettingsGroup {
            SettingsItem(
                title = stringResource(R.string.public_settings_appearance),
                subtitle = stringResource(R.string.public_settings_appearance_desc),
                icon = Icons.Outlined.Palette,
                onClick = onNavigateAppearance
            )
            SettingsItem(
                title = stringResource(R.string.public_settings_scores),
                subtitle = stringResource(R.string.public_settings_scores_desc),
                icon = Icons.Outlined.StarOutline,
                onClick = onNavigateCommunityScores
            )
        }
        SettingsSectionLabel(stringResource(R.string.public_settings_support))
        SettingsGroup {
            SettingsItem(
                title = stringResource(R.string.public_settings_diagnostics),
                subtitle = stringResource(R.string.public_settings_diagnostics_desc),
                icon = Icons.Outlined.BugReport,
                onClick = onNavigateDiagnostics
            )
        }
    }
}

@Composable
fun AniSyncPlusAppearanceSettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AniSyncPlusSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val appSettings = LocalAppSettings.current
    val compactCalendarChrome by appSettings.compactCalendarChrome.collectAsStateWithLifecycle()
    val topShortcutOrder by appSettings.topShortcutOrder.collectAsStateWithLifecycle()
    val visibleTopShortcuts by appSettings.visibleTopShortcuts.collectAsStateWithLifecycle()

    SettingsScreenScaffold(
        title = stringResource(R.string.public_settings_appearance),
        onBackClick = onBackClick,
        modifier = modifier
    ) {
        SettingsSectionLabel(stringResource(R.string.public_settings_density))
        SettingsGroup {
            UiDensity.entries.forEach { density ->
                RadioSettingsItem(
                    title = densityTitle(density),
                    selected = state.uiDensity == density,
                    onClick = { viewModel.onAction(AniSyncPlusSettingsAction.SetUiDensity(density)) }
                )
            }
            SwitchSettingsItem(
                title = stringResource(R.string.public_settings_edge_to_edge),
                subtitle = stringResource(R.string.public_settings_edge_to_edge_desc),
                checked = state.detailEdgeToEdgeEnabled,
                onCheckedChange = {
                    viewModel.onAction(AniSyncPlusSettingsAction.SetDetailEdgeToEdge(it))
                }
            )
            SwitchSettingsItem(
                title = stringResource(R.string.public_settings_compact_calendar),
                subtitle = stringResource(R.string.public_settings_compact_calendar_desc),
                checked = compactCalendarChrome,
                onCheckedChange = appSettings::setCompactCalendarChrome
            )
        }

        SettingsSectionLabel(stringResource(R.string.public_settings_navigation))
        SettingsGroup {
            state.mainNavigationOrder.forEachIndexed { index, key ->
                NavigationDestinationRow(
                    key = key,
                    checked = key in state.visibleMainNavigation,
                    canHide = key !in MainNavigationDestination.requiredVisibleKeys &&
                        (state.visibleMainNavigation.size > 1 || key !in state.visibleMainNavigation),
                    canMoveUp = index > 0,
                    canMoveDown = index < state.mainNavigationOrder.lastIndex,
                    onVisibilityChange = {
                        viewModel.onAction(AniSyncPlusSettingsAction.SetMainNavigationVisible(key, it))
                    },
                    onMove = {
                        viewModel.onAction(AniSyncPlusSettingsAction.MoveMainNavigation(key, it))
                    }
                )
            }
        }

        SettingsSectionLabel(stringResource(R.string.public_settings_shortcuts))
        SettingsGroup {
            topShortcutOrder.forEachIndexed { index, key ->
                NavigationDestinationRow(
                    key = key,
                    checked = key in visibleTopShortcuts,
                    canHide = true,
                    canMoveUp = index > 0,
                    canMoveDown = index < topShortcutOrder.lastIndex,
                    onVisibilityChange = { appSettings.setTopShortcutVisible(key, it) },
                    onMove = { appSettings.moveTopShortcut(key, it) }
                )
            }
        }

        SettingsSectionLabel(stringResource(R.string.public_settings_startup))
        SettingsGroup {
            RadioSettingsItem(
                title = stringResource(R.string.public_settings_startup_last),
                selected = state.mainNavigationStartMode == MainNavigationStartMode.LAST_OPENED,
                onClick = {
                    viewModel.onAction(
                        AniSyncPlusSettingsAction.SetMainNavigationStartMode(MainNavigationStartMode.LAST_OPENED)
                    )
                }
            )
            RadioSettingsItem(
                title = stringResource(R.string.public_settings_startup_fixed),
                selected = state.mainNavigationStartMode == MainNavigationStartMode.FIXED,
                onClick = {
                    viewModel.onAction(
                        AniSyncPlusSettingsAction.SetMainNavigationStartMode(MainNavigationStartMode.FIXED)
                    )
                }
            )
            if (state.mainNavigationStartMode == MainNavigationStartMode.FIXED) {
                state.mainNavigationOrder.filter(state.visibleMainNavigation::contains).forEach { key ->
                    RadioSettingsItem(
                        title = navigationTitle(key),
                        selected = state.fixedMainNavigationStart == key,
                        onClick = {
                            viewModel.onAction(AniSyncPlusSettingsAction.SetFixedMainNavigationStart(key))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AniSyncPlusDiagnosticsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AniSyncPlusSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreenScaffold(
        title = stringResource(R.string.public_settings_diagnostics),
        onBackClick = onBackClick,
        modifier = modifier
    ) {
        SettingsGroup {
            DiagnosticItem(
                title = stringResource(R.string.public_diagnostic_score_mode),
                value = state.communityScoreMode.name
            )
            DiagnosticItem(
                title = stringResource(R.string.public_diagnostic_score_runtime),
                value = "requests=${state.communityScoreRuntime.requests} · cache=${state.communityScoreRuntime.cacheHits} · errors=${state.communityScoreRuntime.failures}"
            )
            DiagnosticItem(
                title = stringResource(R.string.public_diagnostic_score_cache),
                value = "entries=${state.communityScoreCache.entries} · fresh=${state.communityScoreCache.freshEntries} · stale=${state.communityScoreCache.staleEntries}"
            )
            DiagnosticItem(
                title = stringResource(R.string.public_diagnostic_graph_cache),
                value = "graphs=${state.franchiseGraphCacheCount} · latest=${state.franchiseGraphLatestFetchEpochMillis ?: "none"}"
            )
            SettingsItem(
                title = stringResource(R.string.public_diagnostic_refresh),
                onClick = { viewModel.onAction(AniSyncPlusSettingsAction.RefreshDiagnostics) }
            )
        }
    }
}

@Composable
private fun NavigationDestinationRow(
    key: String,
    checked: Boolean,
    canHide: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
    onMove: (Int) -> Unit
) {
    SettingsItem(
        title = navigationTitle(key),
        onClick = { if (canHide || !checked) onVisibilityChange(!checked) },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.public_move_up)
                    )
                }
                IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.public_move_down)
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onVisibilityChange,
                    enabled = canHide || !checked,
                    colors = appSwitchColors()
                )
            }
        }
    )
}

@Composable
fun DiagnosticItem(title: String, value: String) {
    SettingsItem(title = title, subtitle = value, onClick = {})
}

@Composable
private fun densityTitle(density: UiDensity): String = when (density) {
    UiDensity.COMPACT -> stringResource(R.string.public_density_compact)
    UiDensity.STANDARD -> stringResource(R.string.public_density_standard)
    UiDensity.LARGE -> stringResource(R.string.public_density_large)
}

@Composable
private fun navigationTitle(key: String): String = when (key) {
    "library" -> stringResource(R.string.nav_library)
    "discover" -> stringResource(R.string.nav_discover)
    "feed" -> stringResource(R.string.nav_feed)
    "forum" -> stringResource(R.string.nav_forum)
    "profile" -> stringResource(R.string.nav_profile)
    "calendar" -> stringResource(R.string.calendar_title)
    else -> key
}
