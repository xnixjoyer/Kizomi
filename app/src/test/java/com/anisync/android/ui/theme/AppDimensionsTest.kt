package com.anisync.android.ui.theme

import androidx.compose.ui.unit.dp
import com.anisync.android.data.UiDensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDimensionsTest {
    @Test
    fun `standard tokens retain the established layout`() {
        val standard = appDimensionsFor(UiDensity.STANDARD)

        assertEquals(64.dp, standard.collapsedTopBarHeight)
        assertEquals(170.dp, standard.expandedTopBarHeight)
        assertEquals(16.dp, standard.screenHorizontalPadding)
        assertEquals(24.dp, standard.sectionHorizontalPadding)
        assertEquals(10.dp, standard.calendarCardPadding)
        assertEquals(56.dp, standard.listItemMinHeight)
    }

    @Test
    fun `compact standard and large scale semantic footprint monotonically`() {
        val compact = appDimensionsFor(UiDensity.COMPACT)
        val standard = appDimensionsFor(UiDensity.STANDARD)
        val large = appDimensionsFor(UiDensity.LARGE)

        assertTrue(compact.collapsedTopBarHeight < standard.collapsedTopBarHeight)
        assertTrue(standard.collapsedTopBarHeight < large.collapsedTopBarHeight)
        assertTrue(compact.sectionSpacing < standard.sectionSpacing)
        assertTrue(standard.sectionSpacing < large.sectionSpacing)
        assertTrue(compact.sectionHorizontalPadding < standard.sectionHorizontalPadding)
        assertTrue(standard.sectionHorizontalPadding < large.sectionHorizontalPadding)
        assertTrue(compact.calendarCardPadding < standard.calendarCardPadding)
        assertTrue(standard.calendarCardPadding < large.calendarCardPadding)
    }

    @Test
    fun `all modes retain usable touch targets and positive dimensions`() {
        UiDensity.entries.forEach { density ->
            val dimensions = appDimensionsFor(density)
            assertTrue(dimensions.compactIconButtonSize >= 48.dp)
            assertTrue(dimensions.listItemMinHeight >= 48.dp)
            assertTrue(dimensions.navigationIndicatorHeight > 0.dp)
            assertTrue(dimensions.dialogPadding > 0.dp)
        }
    }
}
