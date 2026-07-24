package com.anisync.android.presentation

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.anisync.android.R
import com.anisync.android.data.MainNavigationDestination
import com.anisync.android.data.NavBarStyle
import com.anisync.android.data.replacementForHiddenMainNavigationDestination
import com.anisync.android.domain.provider.ActiveProvider
import com.anisync.android.presentation.components.alert.ProvideToastManager
import com.anisync.android.presentation.components.alert.TopToastHost
import com.anisync.android.presentation.components.navigation.CompactNavBar
import com.anisync.android.presentation.components.navigation.CompactNavBarItem
import com.anisync.android.presentation.mal.MalSharedNavHost
import com.anisync.android.presentation.navigation.AniSyncNavHost
import com.anisync.android.presentation.navigation.CalendarRoot
import com.anisync.android.presentation.navigation.Discover
import com.anisync.android.presentation.navigation.Feed
import com.anisync.android.presentation.navigation.Forum
import com.anisync.android.presentation.navigation.Library
import com.anisync.android.presentation.navigation.MainDestinationBadgeKind
import com.anisync.android.presentation.navigation.MainDestinationRegistry
import com.anisync.android.presentation.navigation.MediaDetails
import com.anisync.android.presentation.navigation.Profile
import com.anisync.android.presentation.navigation.navigateSafely
import com.anisync.android.presentation.navigation.resolveProviderMainNavigation
import com.anisync.android.presentation.util.LocalAdaptiveInfo
import com.anisync.android.presentation.util.LocalMainNavBarInset
import com.anisync.android.presentation.util.LocalMainNavBarSuppressor
import com.anisync.android.presentation.util.LocalRailFabState
import com.anisync.android.presentation.util.LocalStatusBarColor
import com.anisync.android.presentation.util.MainNavBarSuppressor
import com.anisync.android.presentation.util.RailFab
import com.anisync.android.presentation.util.RailFabState
import com.anisync.android.ui.theme.LocalAppDimensions
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

private data class BottomNavItem(
    val titleResId: Int,
    val contentDescriptionResId: Int,
    val route: Any,
    val routeClass: KClass<out Any>,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val badgeKind: MainDestinationBadgeKind,
    /**
     * Stable key persisted as the "last visited tab" for cold-launch restore.
     * Every configurable destination, including Profile, has a stable key.
     */
    val persistKey: String,
)

/**
 * The top-level destinations, shared by every navigation container (bottom bar, rail) so the
 * destination set, ordering, and badge logic never diverge across form factors.
 */
@Composable
private fun rememberMainNavItems(
    order: List<String>,
    visible: Set<String>,
): List<BottomNavItem> = remember(order, visible) {
    val items = MainDestinationRegistry.entries.map { spec ->
        BottomNavItem(
            spec.labelRes,
            spec.contentDescriptionRes,
            spec.route,
            spec.routeClass,
            spec.selectedIcon,
            spec.unselectedIcon,
            badgeKind = spec.badgeKind,
            persistKey = spec.key,
        )
    }
    val byKey = items.associateBy { it.persistKey }
    order.mapNotNull(byKey::get).filter { it.persistKey in visible }
}

/** Shared tab navigation: single-top, save/restore the tab back stack, then persist the tab. */
private fun NavHostController.navigateToMainTab(
    route: Any,
    persistKey: String,
    onTabSelected: (String) -> Unit,
) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    onTabSelected(persistKey)
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen(
    builtAtEpoch: Int = 0,
    viewModel: MainScreenViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val providerState by viewModel.providerState.collectAsStateWithLifecycle()
    val activeProvider = providerState.activeProvider

    LaunchedEffect(navController, activeProvider) {
        if (activeProvider != ActiveProvider.ANILIST_ONLY) return@LaunchedEffect
        val activity = context as? com.anisync.android.MainActivity ?: return@LaunchedEffect
        activity.newIntents.collect { intent ->
            navController.handleDeepLink(intent)
        }
    }
    // Cross-account notification deep links belong to AniList account switching only.
    LaunchedEffect(navController, activeProvider) {
        if (activeProvider != ActiveProvider.ANILIST_ONLY) return@LaunchedEffect
        val activity = context as? com.anisync.android.MainActivity ?: return@LaunchedEffect
        activity.pendingDeepLink.filterNotNull().collect { pending ->
            if (pending.epoch == builtAtEpoch) {
                navController.handleDeepLink(pending.intent)
                activity.consumePendingDeepLink()
            }
        }
    }
    // AniList ranking/genre/tag launch requests must never enter the MAL graph.
    LaunchedEffect(navController, activeProvider) {
        if (activeProvider != ActiveProvider.ANILIST_ONLY) return@LaunchedEffect
        val mainTabClasses = MainDestinationRegistry.mainTabClasses
        viewModel.discoverSearchNavigations.collect {
            while (
                navController.currentBackStackEntry?.destination
                    ?.let { dest -> mainTabClasses.any { dest.hasRoute(it) } } == false
            ) {
                if (!navController.popBackStack()) break
            }
            navController.navigateToMainTab(Discover, "discover", viewModel::onMainTabSelected)
            navController.popBackStack(route = Discover, inclusive = false)
        }
    }

    val unreadNotificationCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
    val navBarStyle by viewModel.navBarStyle.collectAsStateWithLifecycle()
    val navBarShowLabels by viewModel.navBarShowLabels.collectAsStateWithLifecycle()
    val navBarCornerRadius by viewModel.navBarCornerRadius.collectAsStateWithLifecycle()
    val configuredNavigationOrder by viewModel.mainNavigationOrder.collectAsStateWithLifecycle()
    val configuredVisibleNavigation by viewModel.visibleMainNavigation.collectAsStateWithLifecycle()

    if (activeProvider == ActiveProvider.UNCONFIGURED) return

    val providerNavigation = remember(
        activeProvider,
        configuredNavigationOrder,
        configuredVisibleNavigation,
        viewModel.startTabKey,
    ) {
        resolveProviderMainNavigation(
            provider = activeProvider,
            configuredOrder = configuredNavigationOrder,
            configuredVisible = configuredVisibleNavigation,
            requestedStartKey = viewModel.startTabKey,
        )
    }
    val mainNavigationOrder = providerNavigation.orderedKeys
    val visibleMainNavigation = providerNavigation.visibleKeys
    val navItems = rememberMainNavItems(mainNavigationOrder, visibleMainNavigation)
    val navBarSuppressor = remember { MainNavBarSuppressor() }
    val startDestination: Any = remember(providerNavigation.startKey) {
        MainDestinationRegistry.routeForKey(providerNavigation.startKey)
    }
    val providerUnreadNotificationCount = if (activeProvider == ActiveProvider.ANILIST_ONLY) {
        unreadNotificationCount
    } else {
        0
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshNotificationBadge()
    }

    // Navigation container per Material 3: a navigation bar on compact width (phone portrait) AND on
    // compact height (< 480dp, e.g. a phone in landscape) where a vertical rail's destinations can't
    // fit and would overflow; the navigation rail only when the window has room in both axes.
    val adaptive = LocalAdaptiveInfo.current

    // Drive the global status-bar protection scrim (painted in MainActivity).
    val statusBarColorHolder = LocalStatusBarColor.current
    val backgroundColor = MaterialTheme.colorScheme.background
    val usesBottomBar = adaptive.isCompact || adaptive.isCompactHeight
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    // React to both preference mutations and provider capability changes. The projection does not
    // rewrite durable settings; it only redirects an unsupported currently visible root.
    LaunchedEffect(activeProvider, visibleMainNavigation, mainNavigationOrder) {
        val destination = navController.currentBackStackEntry?.destination
        val currentKey = MainDestinationRegistry.keyFor(destination)
        val replacementKey = replacementForHiddenMainNavigationDestination(
            currentKey = currentKey,
            order = mainNavigationOrder,
            visible = visibleMainNavigation,
        )
        if (replacementKey != null) {
            val fallback = navItems.first { it.persistKey == replacementKey }
            navController.navigateToMainTab(
                fallback.route,
                fallback.persistKey,
                viewModel::onMainTabSelected,
            )
        }
    }
    val isBackgroundRoot by remember(usesBottomBar) {
        derivedStateOf {
            if (!usesBottomBar) return@derivedStateOf false
            MainDestinationRegistry.specFor(currentBackStackEntry?.destination)
                ?.usesBackgroundChrome == true
        }
    }
    LaunchedEffect(isBackgroundRoot, backgroundColor) {
        statusBarColorHolder.value = if (isBackgroundRoot) backgroundColor else Color.Unspecified
    }

    ProvideToastManager(toastManager = viewModel.toastManager) {
        CompositionLocalProvider(LocalMainNavBarSuppressor provides navBarSuppressor) {
            if (adaptive.isCompact || adaptive.isCompactHeight) {
                CompactNavLayout(
                    navController = navController,
                    startDestination = startDestination,
                    navItems = navItems,
                    mainNavigationOrder = mainNavigationOrder,
                    activeProvider = activeProvider,
                    unreadNotificationCount = providerUnreadNotificationCount,
                    navBarStyle = navBarStyle,
                    navBarShowLabels = navBarShowLabels,
                    navBarCornerRadius = navBarCornerRadius,
                    onTabSelected = viewModel::onMainTabSelected,
                    toastHost = { TopToastHost(toastManager = viewModel.toastManager) },
                )
            } else {
                RailNavLayout(
                    navController = navController,
                    startDestination = startDestination,
                    navItems = navItems,
                    mainNavigationOrder = mainNavigationOrder,
                    activeProvider = activeProvider,
                    unreadNotificationCount = providerUnreadNotificationCount,
                    onTabSelected = viewModel::onMainTabSelected,
                    toastHost = { TopToastHost(toastManager = viewModel.toastManager) },
                )
            }
        }
    }
}

/** Shared NavHost wiring used by both the compact and rail layouts. */
@Composable
private fun MainNavHost(
    navController: NavHostController,
    startDestination: Any,
    mainNavigationOrder: List<String>,
    activeProvider: ActiveProvider,
    modifier: Modifier = Modifier,
) {
    when (activeProvider) {
        ActiveProvider.ANILIST_ONLY -> AniSyncNavHost(
            navController = navController,
            startDestination = startDestination,
            mainNavigationOrder = mainNavigationOrder,
            onMediaClick = { mediaId, sourceScreen ->
                navController.navigateSafely(MediaDetails(mediaId, sourceScreen))
            },
            modifier = modifier,
        )
        ActiveProvider.MAL_ONLY -> MalSharedNavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = modifier,
        )
        ActiveProvider.UNCONFIGURED -> Unit
    }
}

/**
 * Compact layout (phones): bottom navigation bar. Anchored bars get a real `bottomBar` slot so
 * content shrinks above them; floating bars overlay the content so scrollable regions pass through
 * the empty space beside the pill.
 */
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CompactNavLayout(
    navController: NavHostController,
    startDestination: Any,
    navItems: List<BottomNavItem>,
    mainNavigationOrder: List<String>,
    activeProvider: ActiveProvider,
    unreadNotificationCount: Int,
    navBarStyle: NavBarStyle,
    navBarShowLabels: Boolean,
    navBarCornerRadius: Float,
    onTabSelected: (String) -> Unit,
    toastHost: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (navBarStyle == NavBarStyle.ANCHORED) {
                MainBottomBar(
                    navController = navController,
                    navItems = navItems,
                    unreadNotificationCount = unreadNotificationCount,
                    style = NavBarStyle.ANCHORED,
                    showLabels = navBarShowLabels,
                    cornerRadius = navBarCornerRadius,
                    onTabSelected = onTabSelected,
                )
            }
        },
    ) { _ ->
        val dimensions = LocalAppDimensions.current
        val barInset = if (navBarShowLabels) {
            dimensions.navigationBarInsetWithLabels
        } else {
            dimensions.navigationBarInsetWithoutLabels
        }

        Box(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalMainNavBarInset provides barInset) {
                MainNavHost(
                    navController = navController,
                    startDestination = startDestination,
                    mainNavigationOrder = mainNavigationOrder,
                    activeProvider = activeProvider,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (navBarStyle == NavBarStyle.FLOATING) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                ) {
                    MainBottomBar(
                        navController = navController,
                        navItems = navItems,
                        unreadNotificationCount = unreadNotificationCount,
                        style = NavBarStyle.FLOATING,
                        showLabels = navBarShowLabels,
                        cornerRadius = navBarCornerRadius,
                        onTabSelected = onTabSelected,
                    )
                }
            }

            toastHost()
        }
    }
}

/**
 * Medium / expanded layout: navigation rail plus the same provider-aware NavHost.
 */
@Composable
private fun RailNavLayout(
    navController: NavHostController,
    startDestination: Any,
    navItems: List<BottomNavItem>,
    mainNavigationOrder: List<String>,
    activeProvider: ActiveProvider,
    unreadNotificationCount: Int,
    onTabSelected: (String) -> Unit,
    toastHost: @Composable () -> Unit,
) {
    val railFabState = remember { RailFabState() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        CompositionLocalProvider(LocalRailFabState provides railFabState) {
            Row(modifier = Modifier.fillMaxSize()) {
                MainWideNavigationRail(
                    navController = navController,
                    navItems = navItems,
                    unreadNotificationCount = unreadNotificationCount,
                    onTabSelected = onTabSelected,
                )
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    CompositionLocalProvider(LocalMainNavBarInset provides 0.dp) {
                        MainNavHost(
                            navController = navController,
                            startDestination = startDestination,
                            mainNavigationOrder = mainNavigationOrder,
                            activeProvider = activeProvider,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        toastHost()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MainBottomBar(
    navController: NavHostController,
    navItems: List<BottomNavItem>,
    unreadNotificationCount: Int,
    style: NavBarStyle,
    showLabels: Boolean,
    cornerRadius: Float,
    onTabSelected: (String) -> Unit,
) {
    val navBackStackEntryState = navController.currentBackStackEntryAsState()
    val navBarSuppressor = LocalMainNavBarSuppressor.current

    val isBottomBarVisible by remember(navBarSuppressor) {
        derivedStateOf {
            val dest = navBackStackEntryState.value?.destination
            val onRegisteredRoot = MainDestinationRegistry.keyFor(dest) != null
            onRegisteredRoot && navBarSuppressor?.isSuppressed != true
        }
    }

    val motionScheme = MaterialTheme.motionScheme

    val enterAnim = remember(motionScheme) {
        slideInVertically(
            initialOffsetY = { it },
            animationSpec = motionScheme.defaultSpatialSpec(),
        ) + expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = motionScheme.defaultSpatialSpec(),
        )
    }

    val exitAnim = remember(motionScheme) {
        slideOutVertically(
            targetOffsetY = { it },
            animationSpec = motionScheme.fastSpatialSpec(),
        ) + shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = motionScheme.fastSpatialSpec(),
        )
    }

    AnimatedVisibility(
        visible = isBottomBarVisible,
        enter = enterAnim,
        exit = exitAnim,
    ) {
        CompactNavBar(style = style, cornerRadius = cornerRadius) {
            val currentDestination = navBackStackEntryState.value?.destination

            navItems.forEach { item ->
                val isSelected = currentDestination?.hasRoute(item.routeClass) == true
                val showBadge =
                    item.badgeKind == MainDestinationBadgeKind.UNREAD_NOTIFICATIONS &&
                        unreadNotificationCount > 0
                val iconVector = if (isSelected) item.selectedIcon else item.unselectedIcon
                val itemTitle = stringResource(item.titleResId)
                val itemDescription = stringResource(item.contentDescriptionResId)

                CompactNavBarItem(
                    selected = isSelected,
                    onClick = {
                        if (!isSelected) {
                            navController.navigateToMainTab(
                                item.route,
                                item.persistKey,
                                onTabSelected,
                            )
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = itemDescription,
                        )
                    },
                    label = {
                        Text(
                            text = itemTitle,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    showLabel = showLabels,
                    badge = if (showBadge) {
                        {
                            ProfileNavBarIconWithBadge(
                                iconVector = iconVector,
                                title = itemDescription,
                                unreadCount = unreadNotificationCount,
                                isSelected = isSelected,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/**
 * The contextual primary action in a rail's header. Like the destination items, it follows the
 * rail's expansion: icon-only when collapsed and extended when expanded.
 */
@Composable
private fun RailHeaderFab(fab: RailFab, expanded: Boolean, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = fab.onClick,
        expanded = expanded,
        icon = {
            Icon(
                imageVector = fab.icon,
                contentDescription = if (expanded) null else fab.contentDescription,
            )
        },
        text = { Text(fab.contentDescription) },
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier,
    )
}

/** The collapsible Material 3 wide navigation rail shared by both providers. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MainWideNavigationRail(
    navController: NavHostController,
    navItems: List<BottomNavItem>,
    unreadNotificationCount: Int,
    onTabSelected: (String) -> Unit,
) {
    val navBackStackEntryState = navController.currentBackStackEntryAsState()
    val navBarSuppressor = LocalMainNavBarSuppressor.current
    val railFab = LocalRailFabState.current?.fab
    val dimensions = LocalAppDimensions.current

    val isRailVisible by remember(navBarSuppressor) {
        derivedStateOf {
            val dest = navBackStackEntryState.value?.destination
            val onRegisteredRoot = MainDestinationRegistry.keyFor(dest) != null
            onRegisteredRoot && navBarSuppressor?.isSuppressed != true
        }
    }

    val railState = rememberWideNavigationRailState()
    val scope = rememberCoroutineScope()
    val expanded = railState.targetValue == WideNavigationRailValue.Expanded

    val expandLabel = stringResource(R.string.rail_expand)
    val collapseLabel = stringResource(R.string.rail_collapse)
    val expandedStateDesc = stringResource(R.string.rail_expanded)
    val collapsedStateDesc = stringResource(R.string.rail_collapsed)

    AnimatedVisibility(
        visible = isRailVisible,
        enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
        exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
    ) {
        WideNavigationRail(
            state = railState,
            colors = WideNavigationRailDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            header = {
                Column(verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing)) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                if (expanded) railState.collapse() else railState.expand()
                            }
                        },
                        modifier = Modifier
                            .padding(start = dimensions.screenHorizontalPadding + 8.dp)
                            .semantics {
                                stateDescription =
                                    if (expanded) expandedStateDesc else collapsedStateDesc
                            },
                    ) {
                        Icon(
                            imageVector = if (expanded) {
                                Icons.AutoMirrored.Filled.MenuOpen
                            } else {
                                Icons.Filled.Menu
                            },
                            contentDescription = if (expanded) collapseLabel else expandLabel,
                        )
                    }
                    if (railFab != null) {
                        RailHeaderFab(
                            railFab,
                            expanded,
                            Modifier.padding(start = dimensions.screenHorizontalPadding + 4.dp),
                        )
                    }
                }
            },
        ) {
            val currentDestination = navBackStackEntryState.value?.destination

            navItems.forEach { item ->
                val isSelected = currentDestination?.hasRoute(item.routeClass) == true
                val showBadge =
                    item.badgeKind == MainDestinationBadgeKind.UNREAD_NOTIFICATIONS &&
                        unreadNotificationCount > 0
                val iconVector = if (isSelected) item.selectedIcon else item.unselectedIcon
                val itemTitle = stringResource(item.titleResId)
                val itemDescription = stringResource(item.contentDescriptionResId)

                WideNavigationRailItem(
                    railExpanded = expanded,
                    selected = isSelected,
                    onClick = {
                        if (!isSelected) {
                            navController.navigateToMainTab(
                                item.route,
                                item.persistKey,
                                onTabSelected,
                            )
                        }
                    },
                    icon = {
                        if (showBadge) {
                            ProfileNavBarIconWithBadge(
                                iconVector = iconVector,
                                title = itemDescription,
                                unreadCount = unreadNotificationCount,
                                isSelected = isSelected,
                            )
                        } else {
                            Icon(imageVector = iconVector, contentDescription = itemDescription)
                        }
                    },
                    label = {
                        Text(
                            text = itemTitle,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

/**
 * Profile destination icon with the inbox unread badge.
 *
 * Unselected destinations show the count; the selected destination collapses it to a dot. Counts
 * above 999 render as `999+`, and TalkBack receives one combined destination/unread description.
 */
@Composable
private fun ProfileNavBarIconWithBadge(
    iconVector: ImageVector,
    title: String,
    unreadCount: Int,
    isSelected: Boolean,
) {
    val badgeLabel = if (isSelected) {
        stringResource(R.string.notifications_unread_indicator_a11y)
    } else if (unreadCount > 999) {
        stringResource(R.string.notifications_unread_overflow_a11y)
    } else {
        pluralStringResource(
            R.plurals.notifications_unread_count_a11y,
            unreadCount,
            unreadCount,
        )
    }
    val combinedDescription = "$title, $badgeLabel"
    val badgeModifier = Modifier.semantics { contentDescription = "" }

    BadgedBox(
        badge = {
            if (isSelected) {
                Badge(modifier = badgeModifier)
            } else {
                Badge(modifier = badgeModifier) {
                    Text(text = if (unreadCount > 999) "999+" else unreadCount.toString())
                }
            }
        },
    ) {
        Icon(
            imageVector = iconVector,
            contentDescription = combinedDescription,
        )
    }
}
