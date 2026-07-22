package com.anisync.android.presentation.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anisync.android.domain.FranchiseGraph
import com.anisync.android.domain.FranchiseGraphRepository
import com.anisync.android.domain.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FranchiseUniverseUiState(
    val graph: FranchiseGraph? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class FranchiseUniverseViewModel @Inject constructor(
    private val repository: FranchiseGraphRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val mediaId: Int = checkNotNull(savedStateHandle["mediaId"])
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<FranchiseUniverseUiState> = combine(
        repository.observe(mediaId),
        refreshing,
        error
    ) { graph, isRefreshing, message ->
        FranchiseUniverseUiState(
            graph = graph,
            isLoading = graph == null && isRefreshing,
            isRefreshing = isRefreshing,
            error = message
        )
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        FranchiseUniverseUiState()
    )

    init {
        refresh(force = false)
    }

    fun refresh(force: Boolean = true) {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            error.value = null
            try {
                when (val result = repository.refresh(mediaId, force)) {
                    is Result.Success -> Unit
                    is Result.Error -> error.value = result.message
                }
            } finally {
                refreshing.value = false
            }
        }
    }
}
