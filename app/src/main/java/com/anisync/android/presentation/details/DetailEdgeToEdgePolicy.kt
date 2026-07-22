package com.anisync.android.presentation.details

/** Edge-to-edge remains a compact, non-pane detail experiment; every other route keeps defaults. */
internal fun isDetailEdgeToEdgeActive(
    preferenceEnabled: Boolean,
    isCompactWindow: Boolean,
    isPaneRoot: Boolean
): Boolean = preferenceEnabled && isCompactWindow && !isPaneRoot

/** `true` means dark status-bar icons, matching WindowInsetsController terminology. */
internal fun detailStatusBarUsesDarkIcons(
    bannerVisible: Boolean,
    isScrolled: Boolean,
    surfaceUsesDarkIcons: Boolean
): Boolean = if (bannerVisible && !isScrolled) false else surfaceUsesDarkIcons
