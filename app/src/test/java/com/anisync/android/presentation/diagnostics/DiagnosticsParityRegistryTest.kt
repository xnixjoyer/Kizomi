package com.anisync.android.presentation.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsParityRegistryTest {
    @Test
    fun `default parity items match the canonical typed key set`() {
        val items = DiagnosticsParityRegistry.defaultItems()

        assertEquals(DiagnosticsParityRegistry.knownKeys, items.map { it.key }.toSet())
        assertEquals(items.size, items.map { it.key }.distinct().size)
    }

    @Test
    fun `initial acceptance checklist keys are unique and complete`() {
        val checklist = DiagnosticsParityRegistry.checklist(
            configurationPresent = true,
            redirectConfigured = true,
            accountRestored = true,
            blockedInactiveRequestCount = 0L,
        )
        val keys = checklist.map { it.key }

        assertEquals(keys.size, keys.distinct().size)
        assertTrue("mal_configuration_present" in keys)
        assertTrue("oauth_redirect_registered" in keys)
        assertTrue("account_restored_after_restart" in keys)
        assertTrue("discover_request_succeeds" in keys)
        assertTrue("details_route_succeeds" in keys)
        assertTrue("library_request_succeeds" in keys)
        assertTrue("tracking_write_read_back" in keys)
        assertTrue("provider_deletion_returns_to_onboarding" in keys)
        assertTrue("inactive_provider_request_count_zero" in keys)
        assertTrue("shared_ui_migration" in keys)
    }
}
