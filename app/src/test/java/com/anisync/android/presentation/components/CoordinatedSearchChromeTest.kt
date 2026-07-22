package com.anisync.android.presentation.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinatedSearchChromeTest {
    @Test
    fun `search app bar never measures below material minimum`() {
        assertEquals(72.dp, safeSearchAppBarHeight(52.dp))
        assertEquals(72.dp, safeSearchAppBarHeight(64.dp))
        assertEquals(72.dp, safeSearchAppBarHeight(72.dp))
        assertEquals(80.dp, safeSearchAppBarHeight(80.dp))
    }

    @Test
    fun `coordinated offset is bounded and releases measured height`() {
        assertEquals(0, coordinatedSearchChromeOffset(12f, 72))
        assertEquals(-32, coordinatedSearchChromeOffset(-31.6f, 72))
        assertEquals(-72, coordinatedSearchChromeOffset(-200f, 72))
        assertEquals(128, coordinatedSearchChromeHeight(200, -72))
        assertEquals(0, coordinatedSearchChromeHeight(50, -72))
    }
}
