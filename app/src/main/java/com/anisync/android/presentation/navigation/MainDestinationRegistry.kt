package com.anisync.android.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.anisync.android.R
import com.anisync.android.data.MainNavigationDestination
import kotlin.reflect.KClass

enum class MainDestinationBadgeKind {
    NONE,
    UNREAD_NOTIFICATIONS
}

/**
 * UI metadata for one durable [MainNavigationDestination]. Persistence behavior lives on the domain
 * enum; this registry adds only route objects and localized presentation resources.
 */
data class MainDestinationSpec<T : Any>(
    val destination: MainNavigationDestination,
    val route: T,
    val routeClass: KClass<T>,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val usesBackgroundChrome: Boolean = true,
    val badgeKind: MainDestinationBadgeKind = MainDestinationBadgeKind.NONE,
    val contentDescriptionRes: Int = labelRes
) {
    val key: String get() = destination.key
    val allowedAsMainTab: Boolean get() = destination.allowedAsMainTab
    val allowedAsTopShortcut: Boolean get() = destination.allowedAsTopShortcut
    val visibleByDefault: Boolean get() = destination.visibleByDefault
    val eligibleAsStart: Boolean get() = destination.eligibleAsStart
    val launchSupport get() = destination.launchSupport
}

/** The single UI-facing source of truth for configurable root destinations and shortcuts. */
object MainDestinationRegistry {
    val entries: List<MainDestinationSpec<out Any>> = listOf(
        MainDestinationSpec(
            MainNavigationDestination.LIBRARY,
            Library,
            Library::class,
            R.string.nav_library,
            Icons.Filled.VideoLibrary,
            Icons.Outlined.VideoLibrary
        ),
        MainDestinationSpec(
            MainNavigationDestination.DISCOVER,
            Discover,
            Discover::class,
            R.string.nav_discover,
            Icons.Filled.Explore,
            Icons.Outlined.Explore
        ),
        MainDestinationSpec(
            MainNavigationDestination.FEED,
            Feed,
            Feed::class,
            R.string.nav_feed,
            Icons.Filled.DynamicFeed,
            Icons.Outlined.DynamicFeed
        ),
        MainDestinationSpec(
            MainNavigationDestination.FORUM,
            Forum,
            Forum::class,
            R.string.nav_forum,
            Icons.Filled.Forum,
            Icons.Outlined.Forum
        ),
        MainDestinationSpec(
            MainNavigationDestination.PROFILE,
            Profile,
            Profile::class,
            R.string.nav_profile,
            Icons.Filled.Person,
            Icons.Outlined.Person,
            usesBackgroundChrome = false,
            badgeKind = MainDestinationBadgeKind.UNREAD_NOTIFICATIONS
        ),
        MainDestinationSpec(
            MainNavigationDestination.CALENDAR,
            CalendarRoot,
            CalendarRoot::class,
            R.string.nav_calendar_short,
            Icons.Filled.CalendarMonth,
            Icons.Outlined.CalendarMonth,
            usesBackgroundChrome = false,
            contentDescriptionRes = R.string.calendar_title
        ),
)

    val byKey: Map<String, MainDestinationSpec<out Any>> = entries.associateBy { it.key }
    val mainTabClasses: List<KClass<out Any>> = entries
        .filter { it.allowedAsMainTab }
        .map { it.routeClass }

    fun routeForKey(key: String?): Any = key?.let(byKey::get)?.route ?: Library

    fun specFor(destination: NavDestination?): MainDestinationSpec<out Any>? =
        entries.firstOrNull { spec -> destination?.hasRoute(spec.routeClass) == true }

    fun keyFor(destination: NavDestination?): String? = specFor(destination)?.key
}
