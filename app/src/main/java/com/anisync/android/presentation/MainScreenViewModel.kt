package com.anisync.android.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anisync.android.data.AppSettings
import com.anisync.android.data.NavBarStyle
import com.anisync.android.data.NotificationBadgeStore
import com.anisync.android.data.provider.ActiveProviderStore
import com.anisync.android.data.resolveMainNavigationStartKey
import com.anisync.android.domain.provider.ActiveProvider
import com.anisync.android.presentation.components.alert.ToastManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Top-level ViewModel scoped to [MainScreen]. Surfaces app-wide state
 * the shared navigation container needs while keeping provider-only work gated.
 */
@HiltViewModel
class MainScreenViewModel @Inject constructor(
    private val notificationBadgeStore: NotificationBadgeStore,
    private val appSettings: AppSettings,
    private val providerStore: ActiveProviderStore,
    val toastManager: ToastManager,
    searchLauncher: com.anisync.android.domain.DiscoverSearchLauncher,
) : ViewModel() {

    val providerState = providerStore.state
    val unreadNotificationCount: StateFlow<Int> = notificationBadgeStore.unreadCount

    /**
     * "Open Discover search with preset filters" navigation triggers. MainScreen
     * consumes these only while AniList is the active provider.
     */
    val discoverSearchNavigations: kotlinx.coroutines.flow.SharedFlow<Unit> =
        searchLauncher.navigationRequests

    val navBarStyle: StateFlow<NavBarStyle> = appSettings.navBarStyle
    val navBarShowLabels: StateFlow<Boolean> = appSettings.navBarShowLabels
    val navBarCornerRadius: StateFlow<Float> = appSettings.navBarCornerRadius
    val mainNavigationOrder: StateFlow<List<String>> = appSettings.mainNavigationOrder
    val visibleMainNavigation: StateFlow<Set<String>> = appSettings.visibleMainNavigation

    /**
     * The configured/last main tab captured once. Provider capability projection in MainScreen
     * may choose a temporary supported fallback without mutating this persisted preference.
     */
    val startTabKey: String = resolveMainNavigationStartKey(
        order = appSettings.mainNavigationOrder.value,
        visible = appSettings.visibleMainNavigation.value,
        mode = appSettings.mainNavigationStartMode.value,
        fixedKey = appSettings.fixedMainNavigationStart.value,
        lastOpenedKey = appSettings.lastMainTab.value,
    )

    /** Remember the main tab the user switched to, for the next cold launch. */
    fun onMainTabSelected(tabKey: String) {
        appSettings.setLastMainTab(tabKey)
    }

    fun refreshNotificationBadge() {
        val provider = providerStore.snapshot()
        if (provider.activeProvider != ActiveProvider.ANILIST_ONLY ||
            !provider.providerTrafficAllowed
        ) {
            return
        }
        viewModelScope.launch { notificationBadgeStore.refresh() }
    }
}
