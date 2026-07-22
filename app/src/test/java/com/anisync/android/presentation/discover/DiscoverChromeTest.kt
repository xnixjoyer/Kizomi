package com.anisync.android.presentation.discover

import com.anisync.android.presentation.components.coordinatedSearchChromeHeight
import com.anisync.android.presentation.components.coordinatedSearchChromeOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoverChromeTest {
    @Test
    fun coordinatedOffsetIsBoundedToSearchCollapse() {
        assertEquals(0, coordinatedSearchChromeOffset(12f, 64))
        assertEquals(-31, coordinatedSearchChromeOffset(-31.4f, 64))
        assertEquals(-64, coordinatedSearchChromeOffset(-120f, 64))
    }

    @Test
    fun coordinatedHeightReleasesCollapsedSpace() {
        assertEquals(196, coordinatedSearchChromeHeight(260, -64))
        assertEquals(0, coordinatedSearchChromeHeight(40, -64))
    }
}
