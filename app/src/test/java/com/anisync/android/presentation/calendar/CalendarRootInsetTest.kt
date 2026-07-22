package com.anisync.android.presentation.calendar

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarRootInsetTest {
    @Test
    fun rootCalendarAddsVisibleMainBarInset() {
        assertEquals(104.dp, calendarBottomContentPadding(24.dp, 80.dp, true))
    }

    @Test
    fun pushedCalendarKeepsOnlyItsOwnPadding() {
        assertEquals(24.dp, calendarBottomContentPadding(24.dp, 80.dp, false))
    }

    @Test
    fun railRootDoesNotInventBottomSpace() {
        assertEquals(24.dp, calendarBottomContentPadding(24.dp, 0.dp, true))
    }
}
