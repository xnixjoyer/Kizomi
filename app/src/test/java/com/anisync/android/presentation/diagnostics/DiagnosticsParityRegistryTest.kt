package com.anisync.android.presentation.diagnostics

import com.anisync.android.data.diagnostics.DiagnosticParityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsParityRegistryTest {
    @Test
    fun `default parity items match the typed key set without overstating authentication`() {
        val items = DiagnosticsParityRegistry.defaultItems()

        assertEquals(DiagnosticsParityRegistry.knownKeys, items.map { it.key }.toSet())
        assertEquals(items.size, items.map { it.key }.distinct().size)
        assertEquals(
            DiagnosticParityStatus.IN_PROGRESS,
            items.single { it.key == "authentication_session" }.status,
        )
    }

    @Test
    fun `acceptance checklist distinguishes available counter from zero traffic proof`() {
        val unknownChecklist = DiagnosticsParityRegistry.checklist(
            configurationPresent = true,
            redirectConfigured = true,
            accountRestored = true,
            blockedInactiveRequestCount = null,
        )
        val knownZeroChecklist = DiagnosticsParityRegistry.checklist(
            configurationPresent = true,
            redirectConfigured = true,
            accountRestored = true,
            blockedInactiveRequestCount = 0L,
        )

        val keys = unknownChecklist.map { it.key }
        assertEquals(keys.size, keys.distinct().size)
        assertTrue("mal_configuration_present" in keys)
        assertTrue("oauth_redirect_registered" in keys)
        assertTrue("account_restored_after_restart" in keys)
        assertTrue("discover_request_succeeds" in keys)
        assertTrue("details_route_succeeds" in keys)
        assertTrue("library_request_succeeds" in keys)
        assertTrue("tracking_write_read_back" in keys)
        assertTrue("provider_deletion_returns_to_onboarding" in keys)
        assertTrue("blocked_inactive_attempt_counter_available" in keys)
        assertTrue("inactive_provider_traffic_zero" in keys)
        assertTrue("shared_ui_migration" in keys)

        assertFalse(
            unknownChecklist.single {
                it.key == "blocked_inactive_attempt_counter_available"
            }.passed == true,
        )
        assertTrue(
            knownZeroChecklist.single {
                it.key == "blocked_inactive_attempt_counter_available"
            }.passed == true,
        )
        assertNull(
            knownZeroChecklist.single { it.key == "inactive_provider_traffic_zero" }.passed,
        )
        assertEquals(
            "unavailable_without_boundary_instrumentation",
            knownZeroChecklist.single { it.key == "inactive_provider_traffic_zero" }.detail,
        )
    }
}
