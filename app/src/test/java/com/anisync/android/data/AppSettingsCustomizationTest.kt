package com.anisync.android.data

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppSettingsCustomizationTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val preferences
        get() = context.getSharedPreferences("anisync_settings", Context.MODE_PRIVATE)

    @After
    fun clearPreferences() {
        preferences.edit().clear().commit()
    }

    @Test
    fun `customization defaults preserve current UI and keep Beta off`() {
        val settings = AppSettings(context)

        assertEquals(UiDensity.STANDARD, settings.uiDensity.value)
        assertFalse(settings.detailEdgeToEdgeEnabled.value)
        assertEquals(CommunityScoreMode.ANILIST, settings.communityScoreMode.value)
        assertEquals(MainNavigationDestination.defaultOrder, settings.mainNavigationOrder.value)
        assertEquals(MainNavigationDestination.defaultVisibleKeys, settings.visibleMainNavigation.value)
        assertEquals(MainNavigationStartMode.LAST_OPENED, settings.mainNavigationStartMode.value)
        assertEquals("library", settings.fixedMainNavigationStart.value)
    }

    @Test
    fun `density navigation visibility order and startup persist across recreation`() {
        val settings = AppSettings(context)
        settings.setUiDensity(UiDensity.LARGE)
        settings.setDetailEdgeToEdgeEnabled(true)
        settings.setCommunityScoreMode(CommunityScoreMode.BOTH)
        settings.moveMainNavigationDestination("profile", -4)
        assertTrue(settings.setMainNavigationVisible("feed", false))
        settings.setMainNavigationStartMode(MainNavigationStartMode.FIXED)
        settings.setFixedMainNavigationStart("profile")
        settings.setLastMainTab("profile")

        val restored = AppSettings(context)

        assertEquals(UiDensity.LARGE, restored.uiDensity.value)
        assertTrue(restored.detailEdgeToEdgeEnabled.value)
        assertEquals(CommunityScoreMode.BOTH, restored.communityScoreMode.value)
        assertEquals("profile", restored.mainNavigationOrder.value.first())
        assertFalse("feed" in restored.visibleMainNavigation.value)
        assertEquals(MainNavigationStartMode.FIXED, restored.mainNavigationStartMode.value)
        assertEquals("profile", restored.fixedMainNavigationStart.value)
        assertEquals("profile", restored.lastMainTab.value)
    }

    @Test
    fun `last visible destination cannot be hidden`() {
        val settings = AppSettings(context)
        MainNavigationDestination.defaultVisibleKeys
            .filterNot { it == MainNavigationDestination.PROFILE.key }
            .forEach { key ->
                assertTrue(settings.setMainNavigationVisible(key, false))
            }

        assertFalse(settings.setMainNavigationVisible("profile", false))
        assertEquals(setOf("profile"), settings.visibleMainNavigation.value)
    }

    @Test
    fun `corrupted preferences self heal without resetting unrelated choices`() {
        preferences.edit()
            .putString("ui_density_v2", "TINY")
            .putString("main_navigation_order_v2", "profile,unknown,profile")
            .putString("main_navigation_visible_v2", "")
            .putString("main_navigation_start_mode_v2", "BROKEN")
            .putString("main_navigation_fixed_start_v2", "unknown")
            .putBoolean("detail_edge_to_edge_beta", true)
            .putString("community_score_mode_beta_v1", "BROKEN")
            .commit()

        val settings = AppSettings(context)

        assertEquals(UiDensity.STANDARD, settings.uiDensity.value)
        assertEquals("profile", settings.mainNavigationOrder.value.first())
        assertEquals(setOf("profile"), settings.visibleMainNavigation.value)
        assertEquals(MainNavigationStartMode.LAST_OPENED, settings.mainNavigationStartMode.value)
        assertEquals("profile", settings.fixedMainNavigationStart.value)
        assertTrue(settings.detailEdgeToEdgeEnabled.value)
        assertEquals(CommunityScoreMode.ANILIST, settings.communityScoreMode.value)
    }
}
