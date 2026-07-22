package com.anisync.android.data

/** Stable, app-wide layout density. Android's font scale remains independent and fully respected. */
enum class UiDensity {
    COMPACT,
    STANDARD,
    LARGE
}

/**
 * Community-score sources rendered on media cards and details.
 *
 * AniList remains the default and the only source that is available without enabling the
 * read-only MyAnimeList/Jikan Beta path. Persist enum names, never localized labels.
 */
enum class CommunityScoreMode {
    ANILIST,
    MYANIMELIST,
    BOTH;

    val usesMyAnimeList: Boolean
        get() = this != ANILIST

    val usesAniList: Boolean
        get() = this != MYANIMELIST
}

enum class MainNavigationStartMode {
    LAST_OPENED,
    FIXED
}

enum class DestinationLaunchSupport {
    ROOT_ONLY,
    ROOT_AND_PUSHED
}

/**
 * Stable destination metadata used by persistence and repaired before it reaches UI navigation.
 * Localized labels, icons and concrete route objects stay in the UI registry, while all durable
 * behavior is defined here exactly once.
 */
enum class MainNavigationDestination(
    val key: String,
    val allowedAsMainTab: Boolean = true,
    val allowedAsTopShortcut: Boolean = false,
    val visibleByDefault: Boolean = true,
    val eligibleAsStart: Boolean = true,
    val launchSupport: DestinationLaunchSupport = DestinationLaunchSupport.ROOT_ONLY
) {
    LIBRARY("library"),
    DISCOVER("discover"),
    FEED("feed"),
    FORUM("forum"),
    PROFILE("profile"),
    CALENDAR(
        key = "calendar",
        allowedAsTopShortcut = true,
        visibleByDefault = false,
        launchSupport = DestinationLaunchSupport.ROOT_AND_PUSHED
    );
companion object {
        val defaultOrder: List<String> = entries
            .filter(MainNavigationDestination::allowedAsMainTab)
            .map(MainNavigationDestination::key)
        val validKeys: Set<String> = entries.mapTo(linkedSetOf(), MainNavigationDestination::key)
        val defaultVisibleKeys: Set<String> = entries
            .filter { it.allowedAsMainTab && it.visibleByDefault }
            .mapTo(linkedSetOf(), MainNavigationDestination::key)
        // Profile owns the user-facing Settings entry point and therefore remains visible.
        val requiredVisibleKeys: Set<String> = setOf(PROFILE.key)
        val topShortcutKeys: Set<String> = entries
            .filter(MainNavigationDestination::allowedAsTopShortcut)
            .mapTo(linkedSetOf(), MainNavigationDestination::key)
    }
}

fun normalizeTopShortcutOrder(rawOrder: Iterable<String>): List<String> {
    val normalized = rawOrder.filterTo(linkedSetOf()) { it in MainNavigationDestination.topShortcutKeys }
    MainNavigationDestination.entries
        .filter(MainNavigationDestination::allowedAsTopShortcut)
        .map(MainNavigationDestination::key)
        .forEach(normalized::add)
    return normalized.toList()
}

/**
 * Repairs a persisted order without discarding valid user choices: unknown/duplicate IDs are
 * removed and newly introduced destinations are appended in the product default order.
 */
fun normalizeMainNavigationOrder(rawOrder: Iterable<String>): List<String> {
    val valid = MainNavigationDestination.validKeys
    val normalized = rawOrder.filterTo(linkedSetOf()) { it in valid }
    MainNavigationDestination.defaultOrder.forEach(normalized::add)
    return normalized.toList()
}

/** Ensures corrupted preferences can never hide every top-level destination. */
fun normalizeVisibleMainNavigation(
    rawVisible: Iterable<String>,
    normalizedOrder: List<String>
): Set<String> {
    val visible = rawVisible.filterTo(linkedSetOf()) { it in MainNavigationDestination.validKeys }
    visible += MainNavigationDestination.requiredVisibleKeys
    if (visible.isEmpty()) visible += normalizedOrder.firstOrNull() ?: MainNavigationDestination.PROFILE.key
    return visible
}

fun moveMainNavigationDestination(
    order: List<String>,
    key: String,
    offset: Int
): List<String> {
    val normalized = normalizeMainNavigationOrder(order).toMutableList()
    val from = normalized.indexOf(key)
    if (from < 0) return normalized
    val to = (from + offset).coerceIn(normalized.indices)
    if (from != to) {
        val value = normalized.removeAt(from)
        normalized.add(to, value)
    }
    return normalized
}

/** Deterministic cold-start fallback used by both production code and preference tests. */
fun resolveMainNavigationStartKey(
    order: List<String>,
    visible: Set<String>,
    mode: MainNavigationStartMode,
    fixedKey: String?,
    lastOpenedKey: String?
): String {
    val normalizedOrder = normalizeMainNavigationOrder(order)
    val normalizedVisible = normalizeVisibleMainNavigation(visible, normalizedOrder)
    val firstVisible = normalizedOrder.first { it in normalizedVisible }
    return when (mode) {
        MainNavigationStartMode.LAST_OPENED -> lastOpenedKey?.takeIf { it in normalizedVisible } ?: firstVisible
        MainNavigationStartMode.FIXED -> fixedKey?.takeIf { it in normalizedVisible } ?: firstVisible
    }
}

/** Returns a replacement only when a preference edit hid the tab currently on screen. */
fun replacementForHiddenMainNavigationDestination(
    currentKey: String?,
    order: List<String>,
    visible: Set<String>
): String? {
    if (currentKey == null || currentKey in visible) return null
    val normalizedOrder = normalizeMainNavigationOrder(order)
    val normalizedVisible = normalizeVisibleMainNavigation(visible, normalizedOrder)
    return normalizedOrder.first { it in normalizedVisible }
}
