package com.anisync.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCustomizationTest {
    @Test
    fun destinationsAreNormalizedWithoutUnknownOrDuplicateKeys() {
        assertEquals(
            listOf("profile", "library", "feed", "discover", "forum", "calendar"),
            normalizeMainNavigationOrder(
                listOf("profile", "library", "unknown", "feed", "profile", "discover", "forum", "calendar")
            )
        )
    }

    @Test
    fun calendarIsOptionalAndAvailableAsShortcut() {
        assertFalse(MainNavigationDestination.CALENDAR.visibleByDefault)
        assertTrue(MainNavigationDestination.CALENDAR.allowedAsTopShortcut)
        assertEquals(
            setOf("calendar"),
            MainNavigationDestination.topShortcutKeys
        )
    }

    @Test
    fun shortcutOrderIsRepairedDeterministically() {
        assertEquals(
            listOf("calendar"),
            normalizeTopShortcutOrder(listOf("unknown", "calendar", "calendar"))
        )
    }
}
