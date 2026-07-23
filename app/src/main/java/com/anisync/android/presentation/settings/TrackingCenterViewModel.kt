package com.anisync.android.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anisync.android.data.account.AccountStore
import com.anisync.android.data.identity.MediaIdentityResult
import com.anisync.android.data.identity.MediaIdentityStore
import com.anisync.android.data.mal.account.MalAccountCredentialStore
import com.anisync.android.data.tracking.ReconciliationCreateResult
import com.anisync.android.data.tracking.ReconciliationDirection
import com.anisync.android.data.tracking.ReconciliationPlanView
import com.anisync.android.data.tracking.TrackingProviderConflict
import com.anisync.android.data.tracking.TrackingReconciliationPlanLocator
import com.anisync.android.data.tracking.TrackingReconciliationService
import com.anisync.android.data.tracking.TrackingSagaOperation
import com.anisync.android.data.tracking.TrackingSagaRepository
import com.anisync.android.domain.tracking.TrackingEnqueueResult
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    val resolutionFailure: TrackingFailureKind? = null,
    val reconciliationPlan: ReconciliationPlanView? = null,
    val reconciliationBusy: Boolean = false,
)

@HiltViewModel
class TrackingCenterViewModel @Inject constructor(
    private val saga: TrackingSagaRepository,
    private val identities: MediaIdentityStore,
    private val reconciliation: TrackingReconciliationService,
    private val reconciliationPlanLocator: TrackingReconciliationPlanLocator,
    private val accountStore: AccountStore,
    private val malAccounts: MalAccountCredentialStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TrackingCenterUiState())
    val uiState: StateFlow<TrackingCenterUiState> = _uiState.asStateFlow()
    private var reconciliationObservation: Job? = null
    private var reconciliationExecution: Job? = null

    init {
        viewModelScope.launch {
            combine(saga.observeOperations(), saga.observeConflicts()) { operations, conflicts ->
                operations to conflicts
            }.collect { (operations, conflicts) ->
                _uiState.update { it.copy(operations = operations, conflicts = conflicts) }
            }
        }
        viewModelScope.launch {
            reconciliationPlanLocator.latestPlanId()?.let(::observeReconciliation)
        }
        refreshIdentityIssues()
    }

    fun retryFailed(operationId: String, provider: TrackingProvider) {
        viewModelScope.launch {
            saga.retryFailed(operationId, setOf(provider))
        }
    }

    fun resolveConflict(conflict: TrackingProviderConflict, source: TrackingProvider) {
        viewModelScope.launch {
            val result = saga.resolveConflict(conflict, source)
            _uiState.update { state ->
                state.copy(
                    resolutionFailure = (result as? TrackingEnqueueResult.Rejected)?.reason,
                )
            }
        }
    }

    fun previewMissingOnly(mediaType: TrackingMediaType, source: TrackingProvider) {
        viewModelScope.launch {
            _uiState.update { it.copy(reconciliationBusy = true, resolutionFailure = null) }
            val aniListAccountId = accountStore.activeAccount.value?.id?.toString()
            val malAccountId = malAccounts.activeAccount()?.localAccountId
            val sourceAccountId = when (source) {
                TrackingProvider.ANILIST -> aniListAccountId
                TrackingProvider.MYANIMELIST -> malAccountId
            }
            val target = when (source) {
                TrackingProvider.ANILIST -> TrackingProvider.MYANIMELIST
                TrackingProvider.MYANIMELIST -> TrackingProvider.ANILIST
            }
            val targetAccountId = when (target) {
                TrackingProvider.ANILIST -> aniListAccountId
                TrackingProvider.MYANIMELIST -> malAccountId
            }
            if (sourceAccountId == null || targetAccountId == null) {
                _uiState.update {
                    it.copy(
                        reconciliationBusy = false,
                        resolutionFailure = TrackingFailureKind.MISSING_ACCOUNT,
                    )
                }
                return@launch
            }
            when (val result = reconciliation.createMissingOnlyPreview(
                mediaType = mediaType,
                direction = ReconciliationDirection(source, target),
                sourceAccountId = sourceAccountId,
                targetAccountId = targetAccountId,
            )) {
                is ReconciliationCreateResult.Rejected -> _uiState.update {
                    it.copy(
                        reconciliationBusy = false,
                        resolutionFailure = result.reason,
                    )
                }
                is ReconciliationCreateResult.Created -> {
                    observeReconciliation(result.planId)
                    _uiState.update { it.copy(reconciliationBusy = false) }
                }
            }
        }
    }

    fun executeMissingOnly() {
        val planId = _uiState.value.reconciliationPlan?.planId ?: return
        if (reconciliationExecution?.isActive == true) return
        reconciliationExecution = viewModelScope.launch {
            _uiState.update { it.copy(reconciliationBusy = true, resolutionFailure = null) }
            try {
                val updated = reconciliation.executeMissingOnly(planId)
                _uiState.update {
                    it.copy(
                        reconciliationPlan = updated ?: it.reconciliationPlan,
                        resolutionFailure = if (updated == null) {
                            TrackingFailureKind.STORAGE
                        } else {
                            null
                        },
                    )
                }
            } finally {
                _uiState.update { it.copy(reconciliationBusy = false) }
            }
        }
    }

    fun pauseMissingOnly() {
        val planId = _uiState.value.reconciliationPlan?.planId ?: return
        reconciliationExecution?.cancel()
        viewModelScope.launch {
            reconciliation.pause(planId)
            reconciliation.refresh(planId)?.let { updated ->
                _uiState.update {
                    it.copy(reconciliationPlan = updated, reconciliationBusy = false)
                }
            }
        }
    }

    fun refreshMissingOnly() {
        val planId = _uiState.value.reconciliationPlan?.planId ?: return
        viewModelScope.launch {
            reconciliation.refresh(planId)?.let { updated ->
                _uiState.update { it.copy(reconciliationPlan = updated) }
            }
        }
    }

    private fun observeReconciliation(planId: String) {
        reconciliationObservation?.cancel()
        reconciliationObservation = viewModelScope.launch {
            reconciliation.observePlan(planId).collect { plan ->
                _uiState.update { it.copy(reconciliationPlan = plan) }
            }
        }
    }

    fun dismissResolutionFailure() {
        _uiState.update { it.copy(resolutionFailure = null) }
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
