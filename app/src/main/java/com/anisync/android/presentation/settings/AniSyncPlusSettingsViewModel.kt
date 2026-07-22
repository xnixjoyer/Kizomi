package com.anisync.android.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anisync.android.data.AppSettings
import com.anisync.android.data.CommunityScoreMode
import com.anisync.android.data.MainNavigationDestination
import com.anisync.android.data.MainNavigationStartMode
import com.anisync.android.data.UiDensity
import com.anisync.android.data.local.dao.FranchiseGraphDao
import com.anisync.android.domain.CommunityScoreCacheStats
import com.anisync.android.domain.CommunityScoreRepository
import com.anisync.android.domain.CommunityScoreRuntimeStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AniSyncPlusSettingsUiState(
    val uiDensity: UiDensity = UiDensity.STANDARD,
    val detailEdgeToEdgeEnabled: Boolean = false,
    val mainNavigationOrder: List<String> = MainNavigationDestination.defaultOrder,
    val visibleMainNavigation: Set<String> = MainNavigationDestination.defaultVisibleKeys,
    val mainNavigationStartMode: MainNavigationStartMode = MainNavigationStartMode.LAST_OPENED,
    val fixedMainNavigationStart: String = MainNavigationDestination.LIBRARY.key,
    val communityScoreMode: CommunityScoreMode = CommunityScoreMode.ANILIST,
    val communityScoreRuntime: CommunityScoreRuntimeStats = CommunityScoreRuntimeStats(),
    val communityScoreCache: CommunityScoreCacheStats = CommunityScoreCacheStats(),
    val franchiseGraphCacheCount: Int = 0,
    val franchiseGraphLatestFetchEpochMillis: Long? = null
)

sealed interface AniSyncPlusSettingsAction {
    data class SetUiDensity(val density: UiDensity) : AniSyncPlusSettingsAction
    data class SetDetailEdgeToEdge(val enabled: Boolean) : AniSyncPlusSettingsAction
    data class SetMainNavigationVisible(val key: String, val visible: Boolean) : AniSyncPlusSettingsAction
    data class SetMainNavigationOrder(val order: List<String>) : AniSyncPlusSettingsAction
    data class MoveMainNavigation(val key: String, val offset: Int) : AniSyncPlusSettingsAction
    data class SetMainNavigationStartMode(val mode: MainNavigationStartMode) : AniSyncPlusSettingsAction
    data class SetFixedMainNavigationStart(val key: String) : AniSyncPlusSettingsAction
    data object RefreshDiagnostics : AniSyncPlusSettingsAction
}

private data class NavigationPreferenceState(
    val order: List<String>,
    val visible: Set<String>,
    val startMode: MainNavigationStartMode,
    val fixedStart: String
)

private data class CustomizationState(
    val density: UiDensity,
    val detailEdgeToEdge: Boolean,
    val navigation: NavigationPreferenceState
)

private data class FeatureDiagnosticState(
    val scoreMode: CommunityScoreMode,
    val scoreRuntime: CommunityScoreRuntimeStats,
    val scoreCache: CommunityScoreCacheStats,
    val graphCacheCount: Int,
    val graphLatestFetch: Long?
)

@HiltViewModel
class AniSyncPlusSettingsViewModel @Inject constructor(
    private val appSettings: AppSettings,
    private val communityScoreRepository: CommunityScoreRepository,
    franchiseGraphDao: FranchiseGraphDao
) : ViewModel() {
    private val communityScoreCache = MutableStateFlow(CommunityScoreCacheStats())

    private val navigationPreferenceState = combine(
        appSettings.mainNavigationOrder,
        appSettings.visibleMainNavigation,
        appSettings.mainNavigationStartMode,
        appSettings.fixedMainNavigationStart
    ) { order, visible, startMode, fixedStart ->
        NavigationPreferenceState(order, visible, startMode, fixedStart)
    }

    private val customizationState = combine(
        appSettings.uiDensity,
        appSettings.detailEdgeToEdgeEnabled,
        navigationPreferenceState
    ) { density, edgeToEdge, navigation ->
        CustomizationState(density, edgeToEdge, navigation)
    }

    private val featureDiagnosticState = combine(
        appSettings.communityScoreMode,
        communityScoreRepository.runtimeStats,
        communityScoreCache,
        franchiseGraphDao.observeCount(),
        franchiseGraphDao.observeLatestFetch()
    ) { scoreMode, scoreRuntime, scoreCache, graphCount, graphLatest ->
        FeatureDiagnosticState(scoreMode, scoreRuntime, scoreCache, graphCount, graphLatest)
    }

    val uiState = combine(customizationState, featureDiagnosticState) { customization, features ->
        AniSyncPlusSettingsUiState(
            uiDensity = customization.density,
            detailEdgeToEdgeEnabled = customization.detailEdgeToEdge,
            mainNavigationOrder = customization.navigation.order,
            visibleMainNavigation = customization.navigation.visible,
            mainNavigationStartMode = customization.navigation.startMode,
            fixedMainNavigationStart = customization.navigation.fixedStart,
            communityScoreMode = features.scoreMode,
            communityScoreRuntime = features.scoreRuntime,
            communityScoreCache = features.scoreCache,
            franchiseGraphCacheCount = features.graphCacheCount,
            franchiseGraphLatestFetchEpochMillis = features.graphLatestFetch
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AniSyncPlusSettingsUiState()
    )

    init {
        refreshDiagnostics()
    }

    fun onAction(action: AniSyncPlusSettingsAction) {
        when (action) {
            is AniSyncPlusSettingsAction.SetUiDensity -> appSettings.setUiDensity(action.density)
            is AniSyncPlusSettingsAction.SetDetailEdgeToEdge ->
                appSettings.setDetailEdgeToEdgeEnabled(action.enabled)
            is AniSyncPlusSettingsAction.SetMainNavigationVisible ->
                appSettings.setMainNavigationVisible(action.key, action.visible)
            is AniSyncPlusSettingsAction.SetMainNavigationOrder ->
                appSettings.setMainNavigationOrder(action.order)
            is AniSyncPlusSettingsAction.MoveMainNavigation ->
                appSettings.moveMainNavigationDestination(action.key, action.offset)
            is AniSyncPlusSettingsAction.SetMainNavigationStartMode ->
                appSettings.setMainNavigationStartMode(action.mode)
            is AniSyncPlusSettingsAction.SetFixedMainNavigationStart ->
                appSettings.setFixedMainNavigationStart(action.key)
            AniSyncPlusSettingsAction.RefreshDiagnostics -> refreshDiagnostics()
        }
    }

    private fun refreshDiagnostics() {
        viewModelScope.launch {
            communityScoreCache.value = communityScoreRepository.cacheStats()
        }
    }
}
