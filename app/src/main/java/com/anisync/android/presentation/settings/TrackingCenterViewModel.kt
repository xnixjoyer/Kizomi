package com.anisync.android.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anisync.android.data.identity.MediaIdentityResult
import com.anisync.android.data.identity.MediaIdentityStore
import com.anisync.android.data.tracking.TrackingProviderConflict
import com.anisync.android.data.tracking.TrackingSagaOperation
import com.anisync.android.data.tracking.TrackingSagaRepository
import com.anisync.android.domain.tracking.TrackingProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrackingCenterUiState(
    val operations: List<TrackingSagaOperation> = emptyList(),
    val conflicts: List<TrackingProviderConflict> = emptyList(),
    val unresolvedIdentityCount: Int = 0,
    val conflictingIdentityCount: Int = 0,
    val loadingIdentityIssues: Boolean = true,
)

@HiltViewModel
class TrackingCenterViewModel @Inject constructor(
    private val saga: TrackingSagaRepository,
    private val identities: MediaIdentityStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TrackingCenterUiState())
    val uiState: StateFlow<TrackingCenterUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(saga.observeOperations(), saga.observeConflicts()) { operations, conflicts ->
                operations to conflicts
            }.collect { (operations, conflicts) ->
                _uiState.update { it.copy(operations = operations, conflicts = conflicts) }
            }
        }
        refreshIdentityIssues()
    }

    fun retryFailed(operationId: String, provider: TrackingProvider) {
        viewModelScope.launch {
            saga.retryFailed(operationId, setOf(provider))
        }
    }

    fun refreshIdentityIssues() {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingIdentityIssues = true) }
            val unresolvedCount = when (val result = identities.listUnresolved()) {
                is MediaIdentityResult.Success -> result.value.size
                else -> 0
            }
            val conflictCount = when (val result = identities.listConflicting()) {
                is MediaIdentityResult.Success -> result.value.size
                else -> 0
            }
            _uiState.update {
                it.copy(
                    unresolvedIdentityCount = unresolvedCount,
                    conflictingIdentityCount = conflictCount,
                    loadingIdentityIssues = false,
                )
            }
        }
    }
}
