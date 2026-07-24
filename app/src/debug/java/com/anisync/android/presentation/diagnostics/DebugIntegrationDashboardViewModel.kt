package com.anisync.android.presentation.diagnostics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anisync.android.data.diagnostics.DiagnosticsDashboardSection
import com.anisync.android.data.diagnostics.IntegrationDiagnosticsSnapshotSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DebugIntegrationDashboardViewModel @Inject constructor(
    private val snapshotSource: IntegrationDiagnosticsSnapshotSource,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        IntegrationDiagnosticsDashboardUiState(
            expandedSections = restoredSections(),
        ),
    )
    val uiState: StateFlow<IntegrationDiagnosticsDashboardUiState> = _uiState.asStateFlow()

    init {
        refreshLocalSnapshot()
    }

    fun refreshLocalSnapshot() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { snapshotSource.snapshot() }
                .onSuccess { snapshot ->
                    _uiState.value = _uiState.value.copy(
                        snapshot = snapshot,
                        isLoading = false,
                        error = null,
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = IntegrationDiagnosticsDashboardError.LOCAL_SNAPSHOT_UNAVAILABLE,
                    )
                }
        }
    }

    fun toggleSection(section: DiagnosticsDashboardSection) {
        val updated = _uiState.value.expandedSections.toMutableSet().apply {
            if (!add(section)) remove(section)
        }.toSet()
        savedStateHandle[KEY_EXPANDED_SECTIONS] = ArrayList(updated.map(Enum<*>::name))
        _uiState.value = _uiState.value.copy(expandedSections = updated)
    }

    fun sanitizedExport(): String = _uiState.value.snapshot
        ?.let(SanitizedDiagnosticExporter::format)
        .orEmpty()

    private fun restoredSections(): Set<DiagnosticsDashboardSection> {
        val names = savedStateHandle.get<ArrayList<String>>(KEY_EXPANDED_SECTIONS)
            ?: return DiagnosticsDashboardSection.entries.toSet()
        return names.mapNotNull { name ->
            runCatching { DiagnosticsDashboardSection.valueOf(name) }.getOrNull()
        }.toSet()
    }

    private companion object {
        const val KEY_EXPANDED_SECTIONS = "diagnostics_expanded_sections"
    }
}
