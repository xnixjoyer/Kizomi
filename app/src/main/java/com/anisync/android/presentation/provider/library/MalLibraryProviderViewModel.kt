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
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.presentation.model.PresentationMediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
    data class RetryEdit(
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
    val editStates: Map<String, MalLibraryEditLifecycle> = emptyMap(),
)

private data class MalLibraryBaseState(
    val records: List<MalLibraryPresentationRecord>,
    val query: ProviderLibraryQuery,
    val refreshing: Boolean,
    val failure: MalApiFailure?,
    val pageCount: Int?,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MalLibraryProviderViewModel internal constructor(
    private val observeLibrary: (
        localAccountId: String,
        mediaType: TrackingMediaType,
    ) -> Flow<List<MalLibraryPresentationRecord>>,
    private val refreshLibrary: suspend (
        localAccountId: String,
        mediaType: TrackingMediaType,
    ) -> MalLibraryRefreshResult,
    private val activeAccountId: suspend () -> String?,
    private val tracking: MalLibraryTrackingAdapter,
) : ViewModel() {
    @Inject
    constructor(
        presentationRepository: MalLibraryPresentationRepository,
        refreshRepository: MalLibraryRepository,
        accounts: MalAccountCredentialStore,
        tracking: MalLibraryTrackingAdapter,
    ) : this(
        observeLibrary = presentationRepository::observeLibrary,
        refreshLibrary = { localAccountId, mediaType ->
            refreshRepository.refresh(localAccountId, mediaType)
        },
        activeAccountId = { accounts.activeAccount()?.localAccountId },
        tracking = tracking,
    )

    private val query = MutableStateFlow(ProviderLibraryQuery())
    private val records = MutableStateFlow<List<MalLibraryPresentationRecord>>(emptyList())
    private val refreshing = MutableStateFlow(false)
    private val lastFailure = MutableStateFlow<MalApiFailure?>(null)
    private val lastPageCount = MutableStateFlow<Int?>(null)
    private val optimisticItems = MutableStateFlow<Map<String, ProviderLibraryItem>>(emptyMap())
    private val editStates = MutableStateFlow<Map<String, MalLibraryEditLifecycle>>(emptyMap())
    private val mutableState = MutableStateFlow(MalLibraryProviderUiState())
    val uiState: StateFlow<MalLibraryProviderUiState> = mutableState.asStateFlow()

    private val mutableEditOutcomes = MutableSharedFlow<MalLibraryEditLifecycle>(
        extraBufferCapacity = 16,
    )
    val editOutcomes: SharedFlow<MalLibraryEditLifecycle> = mutableEditOutcomes.asSharedFlow()

    init {
        observeRecords()
        observeState()
        refresh()
    }

    fun onAction(action: MalLibraryProviderAction) {
        when (action) {
            MalLibraryProviderAction.Refresh -> refresh()
            is MalLibraryProviderAction.SelectMediaType -> {
                query.update { it.copy(mediaType = action.mediaType, searchQuery = "") }
                refresh()
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
            is MalLibraryProviderAction.RetryEdit -> submitEdit(action.item, action.draft)
            is MalLibraryProviderAction.Delete -> delete(action.item)
        }
    }

    private fun observeRecords() {
        viewModelScope.launch {
            query.map { it.mediaType }
                .distinctUntilChanged()
                .flatMapLatest { mediaType ->
                    flow {
                        val accountId = activeAccountId()
                        if (accountId == null) {
                            emit(emptyList())
                        } else {
                            emitAll(
                                observeLibrary(
                                    accountId,
                                    mediaType.toTrackingMediaType(),
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
                MalLibraryBaseState(
                    records = currentRecords,
                    query = currentQuery,
                    refreshing = isRefreshing,
                    failure = failure,
                    pageCount = pageCount,
                )
            }.combine(optimisticItems) { base, optimistic ->
                base to optimistic
            }.combine(editStates) { (base, optimistic), currentEditStates ->
                val providerItems = base.records.map(MalLibraryPresentationAdapter::map)
                val reconciledItems = providerItems.map { item ->
                    optimistic[item.identity.stableKey] ?: item
                }
                MalLibraryProviderUiState(
                    snapshot = buildProviderLibrarySnapshot(
                        items = reconciledItems,
                        query = base.query,
                        isRefreshing = base.refreshing,
                        errorMessage = base.failure?.kind?.name,
                    ),
                    lastRefreshPageCount = base.pageCount,
                    lastFailure = base.failure,
                    editStates = currentEditStates,
                )
            }.collect(mutableState::emit)
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            lastFailure.value = null
            val accountId = activeAccountId()
            if (accountId == null) {
                lastFailure.value = MalApiFailure(MalApiFailureKind.NOT_AUTHENTICATED)
                refreshing.value = false
                return@launch
            }
            when (
                val result = refreshLibrary(
                    accountId,
                    query.value.mediaType.toTrackingMediaType(),
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
            handleImmediateOutcome(tracking.submit(item, draft))
        }
    }

    private fun delete(item: ProviderLibraryItem) {
        viewModelScope.launch {
            handleImmediateOutcome(tracking.delete(item))
        }
    }

    private suspend fun handleImmediateOutcome(outcome: MalLibraryEditOutcome) {
        when (outcome) {
            is MalLibraryEditOutcome.NoChange -> publish(
                MalLibraryEditLifecycle.NoChange(outcome.displayedItem)
            )
            is MalLibraryEditOutcome.Rejected -> publish(
                MalLibraryEditLifecycle.ValidationFailure(
                    displayedItem = outcome.displayedItem,
                    retryDraft = outcome.retryDraft,
                    reason = outcome.reason,
                    retryable = outcome.retryable,
                )
            )
            is MalLibraryEditOutcome.Accepted -> {
                publish(
                    MalLibraryEditLifecycle.EnqueueAccepted(
                        displayedItem = outcome.displayedItem,
                        rollbackItem = outcome.rollbackItem,
                        retryDraft = outcome.retryDraft,
                        receipt = outcome.receipt,
                        deleteIntent = outcome.deleteIntent,
                    )
                )
                tracking.observe(outcome).collect(::publish)
            }
        }
    }

    private suspend fun publish(lifecycle: MalLibraryEditLifecycle) {
        when (lifecycle) {
            is MalLibraryEditLifecycle.EnqueueAccepted -> {
                if (!lifecycle.deleteIntent) {
                    optimisticItems.update { current ->
                        current + (lifecycle.identityKey to lifecycle.displayedItem)
                    }
                }
            }
            is MalLibraryEditLifecycle.ProviderConfirmed,
            is MalLibraryEditLifecycle.RolledBack -> optimisticItems.update { current ->
                current - lifecycle.identityKey
            }
            is MalLibraryEditLifecycle.NoChange,
            is MalLibraryEditLifecycle.ValidationFailure,
            is MalLibraryEditLifecycle.Pending,
            is MalLibraryEditLifecycle.RetryableFailure,
            is MalLibraryEditLifecycle.PermanentFailure -> Unit
        }
        editStates.update { current -> current + (lifecycle.identityKey to lifecycle) }
        mutableEditOutcomes.emit(lifecycle)
    }
}
