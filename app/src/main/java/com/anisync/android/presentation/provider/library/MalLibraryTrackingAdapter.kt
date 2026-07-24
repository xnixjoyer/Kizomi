package com.anisync.android.presentation.provider.library

import com.anisync.android.data.identity.MediaIdentityProvider
import com.anisync.android.data.local.dao.TrackingDao
import com.anisync.android.data.local.entity.ProviderTrackingSnapshotEntity
import com.anisync.android.data.tracking.MalTrackingCommandInput
import com.anisync.android.data.tracking.TrackingCommandService
import com.anisync.android.domain.tracking.TrackingDesiredState
import com.anisync.android.domain.tracking.TrackingEnqueueReceipt
import com.anisync.android.domain.tracking.TrackingEnqueueResult
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingField
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingStatus
import com.anisync.android.domain.tracking.TrackingTargetState
import com.anisync.android.domain.tracking.toMalIntegerScore
import com.anisync.android.presentation.model.ProviderMediaIdentity
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.transform

data class MalLibraryEditDraft(
    val status: ProviderLibraryStatus,
    val progress: Int,
    val secondaryProgress: Int? = null,
    val score100: Double? = null,
    val repeatCount: Int = 0,
    val startedAt: String? = null,
    val completedAt: String? = null,
) {
    init {
        require(progress >= 0)
        require(secondaryProgress == null || secondaryProgress >= 0)
        require(score100 == null || score100 in 0.0..100.0)
        require(repeatCount >= 0)
        require(startedAt.isValidMalLibraryDate())
        require(completedAt.isValidMalLibraryDate())
    }

    companion object {
        fun from(item: ProviderLibraryItem): MalLibraryEditDraft = MalLibraryEditDraft(
            status = item.status,
            progress = item.progress,
            secondaryProgress = item.secondaryProgress,
            score100 = item.score100,
            repeatCount = item.repeatCount,
            startedAt = item.startedAt,
            completedAt = item.completedAt,
        )
    }
}

sealed interface MalLibraryEditOutcome {
    val displayedItem: ProviderLibraryItem

    data class NoChange(
        override val displayedItem: ProviderLibraryItem,
    ) : MalLibraryEditOutcome

    data class Accepted(
        override val displayedItem: ProviderLibraryItem,
        val rollbackItem: ProviderLibraryItem,
        val retryDraft: MalLibraryEditDraft,
        val receipt: TrackingEnqueueReceipt,
        val requestedFields: Set<TrackingField>,
        val deleteIntent: Boolean,
    ) : MalLibraryEditOutcome

    data class Rejected(
        override val displayedItem: ProviderLibraryItem,
        val retryDraft: MalLibraryEditDraft,
        val reason: TrackingFailureKind,
        val retryable: Boolean,
    ) : MalLibraryEditOutcome
}

/**
 * Library-visible lifecycle for one edit. Enqueue acceptance and durable provider confirmation are
 * deliberately separate states: only [ProviderConfirmed] represents a MAL write followed by the
 * controlled MAL read-back and snapshot publication.
 */
sealed interface MalLibraryEditLifecycle {
    val identityKey: String

    data class NoChange(
        val displayedItem: ProviderLibraryItem,
    ) : MalLibraryEditLifecycle {
        override val identityKey: String = displayedItem.identity.stableKey
    }

    data class ValidationFailure(
        val displayedItem: ProviderLibraryItem,
        val retryDraft: MalLibraryEditDraft,
        val reason: TrackingFailureKind,
        val retryable: Boolean,
    ) : MalLibraryEditLifecycle {
        override val identityKey: String = displayedItem.identity.stableKey
    }

    data class EnqueueAccepted(
        val displayedItem: ProviderLibraryItem,
        val rollbackItem: ProviderLibraryItem,
        val retryDraft: MalLibraryEditDraft,
        val receipt: TrackingEnqueueReceipt,
        val deleteIntent: Boolean,
    ) : MalLibraryEditLifecycle {
        override val identityKey: String = displayedItem.identity.stableKey
    }

    data class Pending(
        val displayedItem: ProviderLibraryItem,
        val rollbackItem: ProviderLibraryItem,
        val retryDraft: MalLibraryEditDraft,
        val receipt: TrackingEnqueueReceipt,
        val targetState: TrackingTargetState,
        val attemptCount: Int,
    ) : MalLibraryEditLifecycle {
        override val identityKey: String = displayedItem.identity.stableKey
    }

    data class RetryableFailure(
        val displayedItem: ProviderLibraryItem,
        val rollbackItem: ProviderLibraryItem,
        val retryDraft: MalLibraryEditDraft,
        val receipt: TrackingEnqueueReceipt,
        val reason: TrackingFailureKind,
        val attemptCount: Int,
        val retryAfterMillis: Long?,
    ) : MalLibraryEditLifecycle {
        override val identityKey: String = displayedItem.identity.stableKey
    }

    data class ProviderConfirmed(
        val displayedItem: ProviderLibraryItem,
        val receipt: TrackingEnqueueReceipt,
        val matchesRequestedState: Boolean,
        val deleted: Boolean,
    ) : MalLibraryEditLifecycle {
        override val identityKey: String = displayedItem.identity.stableKey
    }

    data class PermanentFailure(
        val displayedItem: ProviderLibraryItem,
        val rollbackItem: ProviderLibraryItem,
        val retryDraft: MalLibraryEditDraft,
        val receipt: TrackingEnqueueReceipt,
        val reason: TrackingFailureKind,
    ) : MalLibraryEditLifecycle {
        override val identityKey: String = displayedItem.identity.stableKey
    }

    data class RolledBack(
        val displayedItem: ProviderLibraryItem,
        val failedOptimisticItem: ProviderLibraryItem,
        val retryDraft: MalLibraryEditDraft,
        val receipt: TrackingEnqueueReceipt,
        val reason: TrackingFailureKind,
    ) : MalLibraryEditLifecycle {
        override val identityKey: String = displayedItem.identity.stableKey
    }
}

internal sealed interface MalLibraryDeliveryState {
    data class Pending(
        val targetState: TrackingTargetState,
        val attemptCount: Int,
    ) : MalLibraryDeliveryState

    data class RetryableFailure(
        val reason: TrackingFailureKind,
        val attemptCount: Int,
        val retryAfterMillis: Long?,
    ) : MalLibraryDeliveryState

    data class ProviderConfirmed(
        val snapshot: ProviderTrackingSnapshotEntity,
    ) : MalLibraryDeliveryState

    data class PermanentFailure(
        val reason: TrackingFailureKind,
    ) : MalLibraryDeliveryState
}

/**
 * MAL-owned edit ingress and durable lifecycle adapter for the MAL Library surface. It invokes only
 * [TrackingCommandService.enqueueMal], so one user action creates one MAL target and cannot leak MAL
 * state into an AniList request. Delivery observation is read-only and uses the existing outbox and
 * provider snapshot tables without changing central tracking routing or Room schemas.
 */
@Singleton
class MalLibraryTrackingAdapter internal constructor(
    private val enqueueMal: suspend (MalTrackingCommandInput) -> TrackingEnqueueResult,
    private val observeDelivery: (MalLibraryEditOutcome.Accepted) -> Flow<MalLibraryDeliveryState>,
) {
    @Inject
    constructor(
        service: TrackingCommandService,
        dao: TrackingDao,
    ) : this(
        enqueueMal = service::enqueueMal,
        observeDelivery = { accepted -> dao.observeMalLibraryDelivery(accepted) },
    )

    internal constructor(
        enqueueMal: suspend (MalTrackingCommandInput) -> TrackingEnqueueResult,
    ) : this(
        enqueueMal = enqueueMal,
        observeDelivery = { flowOf() },
    )

    suspend fun submit(
        original: ProviderLibraryItem,
        draft: MalLibraryEditDraft,
    ): MalLibraryEditOutcome {
        val identity = original.identity as? ProviderMediaIdentity.MyAnimeList
            ?: return MalLibraryEditOutcome.Rejected(
                displayedItem = original,
                retryDraft = draft,
                reason = TrackingFailureKind.VALIDATION,
                retryable = false,
            )
        val mediaType = identity.mediaType.toTrackingMediaType()
        if (mediaType == TrackingMediaType.ANIME && draft.secondaryProgress != null) {
            return MalLibraryEditOutcome.Rejected(
                displayedItem = original,
                retryDraft = draft,
                reason = TrackingFailureKind.UNSUPPORTED_FIELD,
                retryable = false,
            )
        }

        val fields = changedFields(original, draft, mediaType)
        if (fields.isEmpty()) return MalLibraryEditOutcome.NoChange(original)

        val optimistic = original.applyDraft(draft, mediaType)
        val result = enqueueMal(
            MalTrackingCommandInput(
                malMediaId = identity.malId,
                mediaType = mediaType,
                desired = draft.toDesiredState(mediaType),
                fields = fields,
            )
        )
        return when (result) {
            is TrackingEnqueueResult.Accepted -> MalLibraryEditOutcome.Accepted(
                displayedItem = optimistic,
                rollbackItem = original,
                retryDraft = draft,
                receipt = result.receipt,
                requestedFields = fields,
                deleteIntent = false,
            )
            is TrackingEnqueueResult.Rejected -> MalLibraryEditOutcome.Rejected(
                displayedItem = original,
                retryDraft = draft,
                reason = result.reason,
                retryable = result.reason.isRetryableLibraryFailure(),
            )
        }
    }

    suspend fun delete(original: ProviderLibraryItem): MalLibraryEditOutcome {
        val identity = original.identity as? ProviderMediaIdentity.MyAnimeList
            ?: return MalLibraryEditOutcome.Rejected(
                displayedItem = original,
                retryDraft = MalLibraryEditDraft.from(original),
                reason = TrackingFailureKind.VALIDATION,
                retryable = false,
            )
        val mediaType = identity.mediaType.toTrackingMediaType()
        val retryDraft = MalLibraryEditDraft.from(original)
        val result = enqueueMal(
            MalTrackingCommandInput(
                malMediaId = identity.malId,
                mediaType = mediaType,
                desired = retryDraft.toDesiredState(mediaType),
                fields = setOf(TrackingField.DELETE),
                deleteIntent = true,
            )
        )
        return when (result) {
            is TrackingEnqueueResult.Accepted -> MalLibraryEditOutcome.Accepted(
                displayedItem = original,
                rollbackItem = original,
                retryDraft = retryDraft,
                receipt = result.receipt,
                requestedFields = setOf(TrackingField.DELETE),
                deleteIntent = true,
            )
            is TrackingEnqueueResult.Rejected -> MalLibraryEditOutcome.Rejected(
                displayedItem = original,
                retryDraft = retryDraft,
                reason = result.reason,
                retryable = result.reason.isRetryableLibraryFailure(),
            )
        }
    }

    fun observe(accepted: MalLibraryEditOutcome.Accepted): Flow<MalLibraryEditLifecycle> =
        observeDelivery(accepted).transform { state ->
            when (state) {
                is MalLibraryDeliveryState.Pending -> emit(
                    MalLibraryEditLifecycle.Pending(
                        displayedItem = accepted.displayedItem,
                        rollbackItem = accepted.rollbackItem,
                        retryDraft = accepted.retryDraft,
                        receipt = accepted.receipt,
                        targetState = state.targetState,
                        attemptCount = state.attemptCount,
                    )
                )
                is MalLibraryDeliveryState.RetryableFailure -> emit(
                    MalLibraryEditLifecycle.RetryableFailure(
                        displayedItem = accepted.displayedItem,
                        rollbackItem = accepted.rollbackItem,
                        retryDraft = accepted.retryDraft,
                        receipt = accepted.receipt,
                        reason = state.reason,
                        attemptCount = state.attemptCount,
                        retryAfterMillis = state.retryAfterMillis,
                    )
                )
                is MalLibraryDeliveryState.ProviderConfirmed -> {
                    val confirmed = state.snapshot.toConfirmedLibraryItem(accepted.rollbackItem)
                    emit(
                        MalLibraryEditLifecycle.ProviderConfirmed(
                            displayedItem = confirmed,
                            receipt = accepted.receipt,
                            matchesRequestedState = accepted.matchesRequestedState(state.snapshot),
                            deleted = state.snapshot.isDeleted,
                        )
                    )
                }
                is MalLibraryDeliveryState.PermanentFailure -> {
                    emit(
                        MalLibraryEditLifecycle.PermanentFailure(
                            displayedItem = accepted.displayedItem,
                            rollbackItem = accepted.rollbackItem,
                            retryDraft = accepted.retryDraft,
                            receipt = accepted.receipt,
                            reason = state.reason,
                        )
                    )
                    emit(
                        MalLibraryEditLifecycle.RolledBack(
                            displayedItem = accepted.rollbackItem,
                            failedOptimisticItem = accepted.displayedItem,
                            retryDraft = accepted.retryDraft,
                            receipt = accepted.receipt,
                            reason = state.reason,
                        )
                    )
                }
            }
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun TrackingDao.observeMalLibraryDelivery(
    accepted: MalLibraryEditOutcome.Accepted,
): Flow<MalLibraryDeliveryState> {
    val identity = accepted.rollbackItem.identity as ProviderMediaIdentity.MyAnimeList
    return observeAllTargets()
        .map { targets -> targets.firstOrNull { it.operationId == accepted.receipt.operationId } }
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { target ->
            val state = runCatching { TrackingTargetState.valueOf(target.state) }.getOrNull()
                ?: return@flatMapLatest flowOf(
                    MalLibraryDeliveryState.PermanentFailure(TrackingFailureKind.INVALID_RESPONSE)
                )
            when (state) {
                TrackingTargetState.PENDING,
                TrackingTargetState.RUNNING -> flowOf(
                    MalLibraryDeliveryState.Pending(
                        targetState = state,
                        attemptCount = target.attemptCount,
                    )
                )
                TrackingTargetState.RETRYING -> flowOf(
                    MalLibraryDeliveryState.RetryableFailure(
                        reason = target.lastErrorKind.toTrackingFailureOr(
                            TrackingFailureKind.TRANSIENT_SERVER
                        ),
                        attemptCount = target.attemptCount,
                        retryAfterMillis = target.retryAfterMillis,
                    )
                )
                TrackingTargetState.SUCCEEDED -> {
                    val accountId = target.providerAccountId
                    if (target.provider != MediaIdentityProvider.MYANIMELIST.name || accountId == null) {
                        flowOf(
                            MalLibraryDeliveryState.PermanentFailure(
                                TrackingFailureKind.INVALID_RESPONSE
                            )
                        )
                    } else {
                        observeSnapshots(
                            provider = MediaIdentityProvider.MYANIMELIST.name,
                            providerAccountId = accountId,
                            mediaType = identity.mediaType.toTrackingMediaType().name,
                        ).map { snapshots ->
                            snapshots.firstOrNull { snapshot ->
                                snapshot.providerMediaId == identity.malId &&
                                    snapshot.fetchedAtEpochMillis >= target.updatedAtEpochMillis
                            }
                        }.filterNotNull()
                            .take(1)
                            .map(MalLibraryDeliveryState::ProviderConfirmed)
                    }
                }
                TrackingTargetState.BLOCKED,
                TrackingTargetState.FAILED,
                TrackingTargetState.SUPERSEDED -> flowOf(
                    MalLibraryDeliveryState.PermanentFailure(
                        target.lastErrorKind.toTrackingFailureOr(
                            if (state == TrackingTargetState.SUPERSEDED) {
                                TrackingFailureKind.PERMANENT
                            } else {
                                TrackingFailureKind.RETRY_BUDGET_EXHAUSTED
                            }
                        )
                    )
                )
            }
        }
        .distinctUntilChanged()
}

private fun changedFields(
    original: ProviderLibraryItem,
    draft: MalLibraryEditDraft,
    mediaType: TrackingMediaType,
): Set<TrackingField> = buildSet {
    if (original.status != draft.status) add(TrackingField.STATUS)
    if (original.progress != draft.progress) add(TrackingField.PROGRESS)
    if (mediaType == TrackingMediaType.MANGA && original.secondaryProgress != draft.secondaryProgress) {
        add(TrackingField.PROGRESS_SECONDARY)
    }
    if (original.score100 != draft.score100) add(TrackingField.SCORE)
    if (original.repeatCount != draft.repeatCount) add(TrackingField.REPEAT_COUNT)
    if (original.startedAt != draft.startedAt) add(TrackingField.STARTED_AT)
    if (original.completedAt != draft.completedAt) add(TrackingField.COMPLETED_AT)
}

private fun MalLibraryEditDraft.toDesiredState(mediaType: TrackingMediaType): TrackingDesiredState =
    TrackingDesiredState(
        status = status.toTrackingStatus(),
        progress = progress,
        progressSecondary = if (mediaType == TrackingMediaType.MANGA) secondaryProgress else null,
        score100 = score100,
        repeatCount = repeatCount,
        startedAt = startedAt,
        completedAt = completedAt,
    )

private fun ProviderLibraryItem.applyDraft(
    draft: MalLibraryEditDraft,
    mediaType: TrackingMediaType,
): ProviderLibraryItem = copy(
    card = card.copy(progress = draft.progress),
    status = draft.status,
    progress = draft.progress,
    secondaryProgress = if (mediaType == TrackingMediaType.MANGA) draft.secondaryProgress else null,
    score100 = draft.score100,
    repeatCount = draft.repeatCount,
    startedAt = draft.startedAt,
    completedAt = draft.completedAt,
)

private fun ProviderTrackingSnapshotEntity.toConfirmedLibraryItem(
    fallback: ProviderLibraryItem,
): ProviderLibraryItem {
    if (isDeleted) return fallback
    val confirmedStatus = runCatching {
        TrackingStatus.valueOf(status).toProviderLibraryStatus()
    }.getOrDefault(fallback.status)
    return fallback.copy(
        card = fallback.card.copy(
            title = title.ifBlank { fallback.card.title },
            coverUrl = coverUrl ?: fallback.card.coverUrl,
            progress = progress,
        ),
        status = confirmedStatus,
        progress = progress.coerceAtLeast(0),
        secondaryProgress = progressSecondary?.coerceAtLeast(0),
        score100 = score?.coerceIn(0.0, 100.0),
        repeatCount = repeatCount.coerceAtLeast(0),
        startedAt = startedAt,
        completedAt = completedAt,
        providerUpdatedAtEpochMillis = providerUpdatedAtEpochMillis,
        fetchedAtEpochMillis = fetchedAtEpochMillis,
    )
}

private fun MalLibraryEditOutcome.Accepted.matchesRequestedState(
    snapshot: ProviderTrackingSnapshotEntity,
): Boolean {
    if (deleteIntent) return snapshot.isDeleted
    if (snapshot.isDeleted) return false
    val confirmedStatus = runCatching {
        TrackingStatus.valueOf(snapshot.status).toProviderLibraryStatus()
    }.getOrNull()
    return requestedFields.all { field ->
        when (field) {
            TrackingField.STATUS -> confirmedStatus == retryDraft.status
            TrackingField.PROGRESS -> snapshot.progress == retryDraft.progress
            TrackingField.PROGRESS_SECONDARY ->
                snapshot.progressSecondary == retryDraft.secondaryProgress
            TrackingField.SCORE -> snapshot.score == retryDraft.score100
                ?.toMalIntegerScore()
                ?.times(10.0)
            TrackingField.REPEAT_COUNT -> snapshot.repeatCount == retryDraft.repeatCount
            TrackingField.STARTED_AT -> snapshot.startedAt == retryDraft.startedAt
            TrackingField.COMPLETED_AT -> snapshot.completedAt == retryDraft.completedAt
            TrackingField.DELETE -> snapshot.isDeleted
            TrackingField.NOTES,
            TrackingField.CUSTOM_LISTS,
            TrackingField.PRIVATE,
            TrackingField.HIDDEN_FROM_STATUS_LISTS -> false
        }
    }
}

internal fun String?.isValidMalLibraryDate(): Boolean {
    if (this == null) return true
    if (length != 10) return false
    return runCatching { LocalDate.parse(this) }.isSuccess
}

private fun String?.toTrackingFailureOr(fallback: TrackingFailureKind): TrackingFailureKind =
    this?.let { value -> runCatching { TrackingFailureKind.valueOf(value) }.getOrNull() } ?: fallback

private fun TrackingFailureKind.isRetryableLibraryFailure(): Boolean = this in setOf(
    TrackingFailureKind.NETWORK_BLOCKED,
    TrackingFailureKind.RATE_LIMITED,
    TrackingFailureKind.OFFLINE,
    TrackingFailureKind.TIMEOUT,
    TrackingFailureKind.TRANSPORT,
    TrackingFailureKind.TRANSIENT_SERVER,
    TrackingFailureKind.LEASE_EXPIRED,
)
