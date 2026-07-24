package com.anisync.android.presentation.diagnostics

import com.anisync.android.data.diagnostics.DiagnosticChecklistItem
import com.anisync.android.data.diagnostics.DiagnosticParityItem
import com.anisync.android.data.diagnostics.DiagnosticParityStatus

object DiagnosticsParityRegistry {
    val knownKeys: Set<String> = setOf(
        "authentication_session",
        "discover_search",
        "details",
        "library_tracking",
        "profile_settings",
        "calendar_widgets",
        "accessibility_adaptive_ui",
        "tests_device_acceptance",
    )

    fun defaultItems(): List<DiagnosticParityItem> = listOf(
        DiagnosticParityItem(
            key = "authentication_session",
            // OAuth Bearer and X-MAL-CLIENT-ID are source-confirmed, but end-to-end route/device
            // acceptance and Integrator wiring are not complete on this isolated worker branch.
            status = DiagnosticParityStatus.IN_PROGRESS,
        ),
        DiagnosticParityItem(
            key = "discover_search",
            status = DiagnosticParityStatus.IN_PROGRESS,
        ),
        DiagnosticParityItem(
            key = "details",
            status = DiagnosticParityStatus.IN_PROGRESS,
        ),
        DiagnosticParityItem(
            key = "library_tracking",
            status = DiagnosticParityStatus.IN_PROGRESS,
        ),
        DiagnosticParityItem(
            key = "profile_settings",
            status = DiagnosticParityStatus.IN_PROGRESS,
        ),
        DiagnosticParityItem(
            key = "calendar_widgets",
            status = DiagnosticParityStatus.IN_PROGRESS,
        ),
        DiagnosticParityItem(
            key = "accessibility_adaptive_ui",
            status = DiagnosticParityStatus.DEVICE_VERIFICATION_PENDING,
        ),
        DiagnosticParityItem(
            key = "tests_device_acceptance",
            status = DiagnosticParityStatus.DEVICE_VERIFICATION_PENDING,
        ),
    )

    fun checklist(
        configurationPresent: Boolean,
        redirectConfigured: Boolean,
        accountRestored: Boolean,
        blockedInactiveRequestCount: Long?,
    ): List<DiagnosticChecklistItem> = listOf(
        DiagnosticChecklistItem("mal_configuration_present", configurationPresent),
        DiagnosticChecklistItem("oauth_redirect_registered", redirectConfigured),
        DiagnosticChecklistItem("account_restored_after_restart", accountRestored),
        DiagnosticChecklistItem("discover_request_succeeds", false, "device_verification_pending"),
        DiagnosticChecklistItem("details_route_succeeds", false, "device_verification_pending"),
        DiagnosticChecklistItem("library_request_succeeds", false, "device_verification_pending"),
        DiagnosticChecklistItem("tracking_write_read_back", false, "device_verification_pending"),
        DiagnosticChecklistItem("provider_deletion_returns_to_onboarding", false, "device_verification_pending"),
        DiagnosticChecklistItem(
            key = "blocked_inactive_attempt_counter_available",
            passed = blockedInactiveRequestCount != null,
            detail = if (blockedInactiveRequestCount == null) {
                "instrumentation_unavailable"
            } else {
                "counter_available"
            },
        ),
        DiagnosticChecklistItem(
            key = "inactive_provider_traffic_zero",
            passed = null,
            detail = "unavailable_without_boundary_instrumentation",
        ),
        DiagnosticChecklistItem("shared_ui_migration", false, "in_progress"),
    )
}
