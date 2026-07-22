package com.anisync.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anisync.android.data.UiDensity

/** Semantic layout tokens. Text sizes deliberately stay in typography so system font scale wins. */
@Immutable
data class AppDimensions(
    val collapsedTopBarHeight: Dp,
    val expandedTopBarHeight: Dp,
    val expandedLongTitleTopBarHeight: Dp,
    val navigationBarVerticalPadding: Dp,
    val navigationIndicatorHeight: Dp,
    val navigationBarInsetWithLabels: Dp,
    val navigationBarInsetWithoutLabels: Dp,
    val screenHorizontalPadding: Dp,
    val sectionHorizontalPadding: Dp,
    val sectionSpacing: Dp,
    val cardPadding: Dp,
    val settingsRowPadding: Dp,
    val listItemMinHeight: Dp,
    val iconSize: Dp,
    val compactIconButtonSize: Dp,
    val dialogPadding: Dp,
    val calendarCardPadding: Dp,
    val calendarCoverWidth: Dp,
    val discoverHeroHeight: Dp,
    val profileBannerHeight: Dp,
    val profileCardOverlap: Dp
)

fun appDimensionsFor(density: UiDensity): AppDimensions = when (density) {
    UiDensity.COMPACT -> AppDimensions(
        collapsedTopBarHeight = 52.dp,
        expandedTopBarHeight = 132.dp,
        expandedLongTitleTopBarHeight = 156.dp,
        navigationBarVerticalPadding = 8.dp,
        navigationIndicatorHeight = 30.dp,
        navigationBarInsetWithLabels = 78.dp,
        navigationBarInsetWithoutLabels = 64.dp,
        screenHorizontalPadding = 12.dp,
        sectionHorizontalPadding = 16.dp,
        sectionSpacing = 6.dp,
        cardPadding = 10.dp,
        settingsRowPadding = 12.dp,
        listItemMinHeight = 48.dp,
        iconSize = 22.dp,
        compactIconButtonSize = 48.dp,
        dialogPadding = 16.dp,
        calendarCardPadding = 8.dp,
        calendarCoverWidth = 52.dp,
        discoverHeroHeight = 340.dp,
        profileBannerHeight = 260.dp,
        profileCardOverlap = 52.dp
    )
    UiDensity.STANDARD -> AppDimensions(
        collapsedTopBarHeight = 64.dp,
        expandedTopBarHeight = 170.dp,
        expandedLongTitleTopBarHeight = 200.dp,
        navigationBarVerticalPadding = 12.dp,
        navigationIndicatorHeight = 32.dp,
        navigationBarInsetWithLabels = 96.dp,
        navigationBarInsetWithoutLabels = 76.dp,
        screenHorizontalPadding = 16.dp,
        sectionHorizontalPadding = 24.dp,
        sectionSpacing = 8.dp,
        cardPadding = 16.dp,
        settingsRowPadding = 16.dp,
        listItemMinHeight = 56.dp,
        iconSize = 24.dp,
        compactIconButtonSize = 48.dp,
        dialogPadding = 20.dp,
        calendarCardPadding = 10.dp,
        calendarCoverWidth = 58.dp,
        discoverHeroHeight = 420.dp,
        profileBannerHeight = 320.dp,
        profileCardOverlap = 64.dp
    )
    UiDensity.LARGE -> AppDimensions(
        collapsedTopBarHeight = 72.dp,
        expandedTopBarHeight = 190.dp,
        expandedLongTitleTopBarHeight = 224.dp,
        navigationBarVerticalPadding = 16.dp,
        navigationIndicatorHeight = 36.dp,
        navigationBarInsetWithLabels = 108.dp,
        navigationBarInsetWithoutLabels = 88.dp,
        screenHorizontalPadding = 20.dp,
        sectionHorizontalPadding = 28.dp,
        sectionSpacing = 12.dp,
        cardPadding = 20.dp,
        settingsRowPadding = 20.dp,
        listItemMinHeight = 64.dp,
        iconSize = 28.dp,
        compactIconButtonSize = 52.dp,
        dialogPadding = 24.dp,
        calendarCardPadding = 14.dp,
        calendarCoverWidth = 64.dp,
        discoverHeroHeight = 480.dp,
        profileBannerHeight = 360.dp,
        profileCardOverlap = 72.dp
    )
}

val LocalAppDimensions = staticCompositionLocalOf { appDimensionsFor(UiDensity.STANDARD) }
