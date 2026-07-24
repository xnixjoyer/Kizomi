package com.anisync.android.presentation.provider.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anisync.android.data.mal.account.MalAccountCredentialStore
import com.anisync.android.data.mal.api.MalApiFailure
import com.anisync.android.data.mal.api.MalApiFailureKind
import com.anisync.android.data.mal.api.MalLibraryPresentationRecord
import com.anisync.android.data.mal.api.MalLibraryPresentationRepository
import com.anisync.android.data.mal.api.MalLibraryRefreshResult
import com.anisync.android.data.mal.api.MalLibraryRepository
import com.anisync.android.presentation.model.PresentationMediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface MalLibraryProviderAction {
    data object Refresh : MalLibraryProviderAction
    data class SelectMediaType(val mediaType: PresentationMediaType) : MalLibraryProviderAction
    data class Search(val query: String) : MalLibraryProviderAction
    data class FilterStatuses(val statuses: Set<ProviderLibraryStatus>) : MalLibraryProviderAction
    data class Sort(
        val sort: ProviderLibrarySort,
        val ascending: Boolean,
    ) : MalLibraryProviderAction
    data class SetLayout(val layout: ProviderLibraryLayout) : MalLibraryProviderAction
    data class SubmitEdit(
        val item: ProviderLibraryItem,
        val draft: MalLibraryEditDraft,
    ) : MalLibraryProviderAction
    data class Delete(val item: ProviderLibraryItem) : MalLibraryProviderAction
}

data class MalLibraryProviderUiState(
    val snapshot: ProviderLibrarySnapshot = buildProviderLibrarySnapshot(
        items = emptyList(),
        query = ProviderLibraryQuery(),
    ),
    val lastRefreshPageCount: Int? = null,
    val lastFailure: MalApiFailure? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MalLibraryProviderViewModel @Inject constructor(
    private val presentationRepository: MalLibraryPresentationRepository,
    private val refreshRepository: MalLibraryRepository,
    private val accounts: MalAccountCredentialStore,
    private val tracking: MalLibraryTrackingAdapter,
) : ViewModel() {
    private val query = MutableStateFlow(ProviderLibraryQuery())
    private val records = MutableStateFlow<List<MalLibraryPresentationRecord>>(emptyList())
    private val refreshing = MutableStateFlow(false)
    private val lastFailure = MutableStateFlow<MalApiFailure?>(null)
    private val lastPageCount = MutableStateFlow<Int?>(null)
    private val mutableState = MutableStateFlow(MalLibraryProviderUiState())
    val uiState: StateFlow<MalLibraryProviderUiState> = mutableState.asStateFlow()

    private val mutableEditOutcomes = MutableSharedFlow<MalLibraryEditOutcome>(extraBufferCapacity = 1)
    val editOutcomes: SharedFlow<MalLibraryEditOutcome> = mutableEditOutcomes.asSharedFlow()

    init {
        observeRecords()
        observeState()
        refresh()
    }

    fun onAction(action: MalLibraryProviderAction) {
        when (action) {
            MalLibraryProviderAction.Refresh -> refresh()
            is MalLibraryProviderAction.SelectMediaType -> query.update {
                it.copy(mediaType = action.mediaType, searchQuery = "")
            }
            is MalLibraryProviderAction.Search -> query.update { it.copy(searchQuery = action.query) }
            is MalLibraryProviderAction.FilterStatuses -> query.update {
                it.copy(statuses = action.statuses)
            }
            is MalLibraryProviderAction.Sort -> query.update {
                it.copy(sort = action.sort, ascending = action.ascending)
            }
            is MalLibraryProviderAction.SetLayout -> query.update { it.copy(layout = action.layout) }
            is MalLibraryProviderAction.SubmitEdit -> submitEdit(action.item, action.draft)
            is MalLibraryProviderAction.Delete -> delete(action.item)
        }
    }

    private fun observeRecords() {
        viewModelScope.launch {
            query.map { it.mediaType }
                .distinctUntilChanged()
                .flatMapLatest { mediaType ->
                    flow {
                        val account = accounts.activeAccount()
                        if (account == null) {
                            emit(emptyList())
                        } else {
                            emitAll(
                                presentationRepository.observeLibrary(
                                    localAccountId = account.localAccountId,
                                    mediaType = mediaType.toTrackingMediaType(),
                                )
                            )
                        }
                    }
                }
                .collect(records::emit)
        }
    }

    private fun observeState() {
        viewModelScope.launch {
            combine(
                records,
                query,
                refreshing,
                lastFailure,
                lastPageCount,
            ) { currentRecords, currentQuery, isRefreshing, failure, pageCount ->
                MalLibraryProviderUiState(
                    snapshot = MalLibraryPresentationAdapter.snapshot(
                        records = currentRecords,
                        query = currentQuery,
                        isRefreshing = isRefreshing,
                        errorMessage = failure?.kind?.name,
                    ),
                    lastRefreshPageCount = pageCount,
                    lastFailure = failure,
                )
            }.collect(mutableState::emit)
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            lastFailure.value = null
            val account = accounts.activeAccount()
            if (account == null) {
                lastFailure.value = MalApiFailure(MalApiFailureKind.NOT_AUTHENTICATED)
                refreshing.value = false
                return@launch
            }
            when (
                val result = refreshRepository.refresh(
                    localAccountId = account.localAccountId,
                    mediaType = query.value.mediaType.toTrackingMediaType(),
                )
            ) {
                is MalLibraryRefreshResult.Success -> {
                    lastPageCount.value = result.pageCount
                    lastFailure.value = null
                }
                is MalLibraryRefreshResult.Failure -> lastFailure.value = result.error
            }
            refreshing.value = false
        }
    }

    private fun submitEdit(item: ProviderLibraryItem, draft: MalLibraryEditDraft) {
        viewModelScope.launch {
            mutableEditOutcomes.emit(tracking.submit(item, draft))
        }
    }

    private fun delete(item: ProviderLibraryItem) {
        viewModelScope.launch {
            mutableEditOutcomes.emit(tracking.delete(item))
        }
    }
}
