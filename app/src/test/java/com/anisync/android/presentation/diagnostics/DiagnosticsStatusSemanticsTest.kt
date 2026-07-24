package com.anisync.android.presentation.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DiagnosticsStatusSemanticsTest {
    @Test
    fun `status row description includes trusted localized label and typed value`() {
        assertEquals(
            "حالة المخزن: AVAILABLE",
            DiagnosticsStatusSemantics.contentDescription(
                label = "حالة المخزن",
                value = "AVAILABLE",
                valueIsAlreadySafe = true,
            ),
        )
    }

    @Test
    fun `status row description redacts callback URL before rendered semantics`() {
        val callback = "anisyncplus-debug://oauth/mal/callback?code=SplxlOBeZQQYbYS6WxSbIA"

        val semantics = DiagnosticsStatusSemantics.contentDescription("Callback", callback)

        assertEquals("Callback: <redacted>", semantics)
        assertFalse(semantics.contains("SplxlOBeZQQYbYS6WxSbIA"))
    }

    @Test
    fun `opaque codes identifiers and private titles cannot enter rendered semantics`() {
        listOf(
            "SplxlOBeZQQYbYS6WxSbIA",
            "1234567890abcdef1234567890abcdef",
            "987654321012345678",
            "Cowboy Bebop The Movie episode 17 score 9",
            "private-user@example.test",
        ).forEach { fixture ->
            val semantics = DiagnosticsStatusSemantics.contentDescription("Value", fixture)
            assertEquals("Value: <redacted>", semantics)
            assertFalse(semantics.contains(fixture))
        }
    }
}
