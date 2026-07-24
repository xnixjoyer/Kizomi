package com.anisync.android.presentation.diagnostics

import com.anisync.android.data.diagnostics.DiagnosticsDashboardSection
import com.anisync.android.data.diagnostics.IntegrationDiagnosticsSnapshot

enum class IntegrationDiagnosticsDashboardError {
    LOCAL_SNAPSHOT_UNAVAILABLE,
}

data class IntegrationDiagnosticsDashboardUiState(
    val snapshot: IntegrationDiagnosticsSnapshot? = null,
    val isLoading: Boolean = true,
    val expandedSections: Set<DiagnosticsDashboardSection> =
        DiagnosticsDashboardSection.entries.toSet(),
    val error: IntegrationDiagnosticsDashboardError? = null,
)
