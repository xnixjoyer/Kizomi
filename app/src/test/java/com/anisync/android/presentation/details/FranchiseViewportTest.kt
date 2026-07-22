package com.anisync.android.presentation.details

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class FranchiseViewportTest {
    @Test
    fun extremePanAndZoomCannotEscapeViewportBounds() {
        assertEquals(
            Offset(0f, 0f),
            clampGraphTranslation(
                Offset(50_000f, 50_000f),
                viewportWidth = 400f,
                viewportHeight = 700f,
                graphWidth = 1_200f,
                graphHeight = 1_600f,
                scale = 1.55f
            )
        )
        assertEquals(
            Offset(-1_460f, -1_780f),
            clampGraphTranslation(
                Offset(-50_000f, -50_000f),
                viewportWidth = 400f,
                viewportHeight = 700f,
                graphWidth = 1_200f,
                graphHeight = 1_600f,
                scale = 1.55f
            )
        )
    }

    @Test
    fun graphSmallerThanViewportIsPinnedWithoutNegativeEmptySpace() {
        assertEquals(
            Offset.Zero,
            clampGraphTranslation(
                Offset(-500f, -500f),
                viewportWidth = 1_000f,
                viewportHeight = 1_000f,
                graphWidth = 400f,
                graphHeight = 500f,
                scale = 1f
            )
        )
    }
}
