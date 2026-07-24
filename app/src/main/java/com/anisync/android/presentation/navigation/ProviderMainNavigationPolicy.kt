package com.anisync.android.presentation.navigation

import com.anisync.android.data.MainNavigationDestination
import com.anisync.android.domain.provider.ActiveProvider

/**
 * Provider-aware projection of the user's durable navigation preferences.
 *
 * The stored order/visibility is never rewritten when a provider cannot support a destination.
 * Instead, the active provider receives a filtered view and a deterministic temporary fallback.
 */
data class ProviderMainNavigation(
    val orderedKeys: List<String>,
    val visibleKeys: Set<String>,
    val startKey: String,
)

internal fun supportedMainDestinationKeys(provider: ActiveProvider): Set<String> = when (provider) {
    ActiveProvider.ANILIST_ONLY -> MainDestinationRegistry.entries
        .asSequence()
        .filter { it.allowedAsMainTab }
        .mapTo(linkedSetOf()) { it.key }
    ActiveProvider.MAL_ONLY -> linkedSetOf(
        MainNavigationDestination.LIBRARY.key,
        MainNavigationDestination.DISCOVER.key,
        MainNavigationDestination.PROFILE.key,
    )
    ActiveProvider.UNCONFIGURED -> emptySet()
}

internal fun resolveProviderMainNavigation(
    provider: ActiveProvider,
    configuredOrder: List<String>,
    configuredVisible: Set<String>,
    requestedStartKey: String?,
): ProviderMainNavigation {
    require(provider != ActiveProvider.UNCONFIGURED) {
        "The shared app shell requires a configured provider"
    }

    val supported = supportedMainDestinationKeys(provider)
    val registryOrder = MainDestinationRegistry.entries
        .asSequence()
        .filter { it.allowedAsMainTab }
        .map { it.key }
        .toList()
    val orderedKeys = (configuredOrder + registryOrder)
        .distinct()
        .filter(supported::contains)
    val fallbackKey = requireNotNull(orderedKeys.firstOrNull()) {
        "A configured provider must expose at least one main destination"
    }
    val requestedVisible = configuredVisible.intersect(supported)
    val visibleKeys = if (requestedVisible.isEmpty()) {
        setOf(fallbackKey)
    } else {
        requestedVisible
    }
    val startKey = requestedStartKey
        ?.takeIf(visibleKeys::contains)
        ?: orderedKeys.first(visibleKeys::contains)

    return ProviderMainNavigation(
        orderedKeys = orderedKeys,
        visibleKeys = visibleKeys,
        startKey = startKey,
    )
}
