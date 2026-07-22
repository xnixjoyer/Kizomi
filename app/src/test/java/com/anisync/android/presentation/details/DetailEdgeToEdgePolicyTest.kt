package com.anisync.android.presentation.details

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailEdgeToEdgePolicyTest {
    @Test
    fun `Beta only activates on compact non-pane details`() {
        assertFalse(isDetailEdgeToEdgeActive(false, isCompactWindow = true, isPaneRoot = false))
        assertFalse(isDetailEdgeToEdgeActive(true, isCompactWindow = false, isPaneRoot = false))
        assertFalse(isDetailEdgeToEdgeActive(true, isCompactWindow = true, isPaneRoot = true))
        assertTrue(isDetailEdgeToEdgeActive(true, isCompactWindow = true, isPaneRoot = false))
    }

    @Test
    fun `banner keeps icons light until opaque app bar takes over`() {
        assertFalse(
            detailStatusBarUsesDarkIcons(
                bannerVisible = true,
                isScrolled = false,
                surfaceUsesDarkIcons = true
            )
        )
        assertTrue(
            detailStatusBarUsesDarkIcons(
                bannerVisible = true,
                isScrolled = true,
                surfaceUsesDarkIcons = true
            )
        )
        assertFalse(
            detailStatusBarUsesDarkIcons(
                bannerVisible = false,
                isScrolled = true,
                surfaceUsesDarkIcons = false
            )
        )
    }
}
