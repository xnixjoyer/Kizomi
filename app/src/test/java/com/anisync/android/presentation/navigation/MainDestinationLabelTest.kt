package com.anisync.android.presentation.navigation

import com.anisync.android.R
import com.anisync.android.data.MainNavigationDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MainDestinationLabelTest {
    @Test
    fun calendarUsesCompactAndAccessibleLabels() {
        val spec = MainDestinationRegistry.byKey.getValue(MainNavigationDestination.CALENDAR.key)
        assertEquals(R.string.nav_calendar_short, spec.labelRes)
        assertEquals(R.string.calendar_title, spec.contentDescriptionRes)
        assertNotEquals(spec.labelRes, spec.contentDescriptionRes)
    }
}
