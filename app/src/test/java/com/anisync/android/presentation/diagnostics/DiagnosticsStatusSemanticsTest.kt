package com.anisync.android.presentation.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticsStatusSemanticsTest {
    @Test
    fun `status row description includes safe label and value`() {
        assertEquals(
            "Token vault: AVAILABLE",
            DiagnosticsStatusSemantics.contentDescription("Token vault", "AVAILABLE"),
        )
    }

    @Test
    fun `status row description redacts unsafe values`() {
        assertEquals(
            "Callback: <redacted>",
            DiagnosticsStatusSemantics.contentDescription(
                "Callback",
                "https://example.test/callback?code=secret",
            ),
        )
    }
}
