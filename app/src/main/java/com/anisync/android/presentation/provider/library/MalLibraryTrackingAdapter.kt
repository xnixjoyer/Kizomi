package com.anisync.android.presentation.provider.library

import com.anisync.android.data.tracking.MalTrackingCommandInput
import com.anisync.android.data.tracking.TrackingCommandService
import com.anisync.android.domain.tracking.TrackingDesiredState
import com.anisync.android.domain.tracking.TrackingEnqueueReceipt
import com.anisync.android.domain.tracking.TrackingEnqueueResult
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingField
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.presentation.model.ProviderMediaIdentity
import javax.inject.Inject
import javax.inject.Singleton

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
        val receipt: TrackingEnqueueReceipt,
    ) : MalLibraryEditOutcome

    data class Rejected(
        override val displayedItem: ProviderLibraryItem,
        val retryDraft: MalLibraryEditDraft,
        val reason: TrackingFailureKind,
        val retryable: Boolean,
    ) : MalLibraryEditOutcome
}

/**
 * Provider-facing edit ingress for the shared Library UI. It only invokes enqueueMal, so one user
 * action can create exactly one MAL target and can never leak MAL state into an AniList request.
 */
@Singleton
class MalLibraryTrackingAdapter internal constructor(
    private val enqueueMal: suspend (MalTrackingCommandInput) -> TrackingEnqueueResult,
) {
    @Inject
    constructor(service: TrackingCommandService) : this(service::enqueueMal)

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
                receipt = result.receipt,
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
        val result = enqueueMal(
            MalTrackingCommandInput(
                malMediaId = identity.malId,
                mediaType = mediaType,
                desired = MalLibraryEditDraft.from(original).toDesiredState(mediaType),
                fields = setOf(TrackingField.DELETE),
                deleteIntent = true,
            )
        )
        return when (result) {
            is TrackingEnqueueResult.Accepted -> MalLibraryEditOutcome.Accepted(
                displayedItem = original,
                rollbackItem = original,
                receipt = result.receipt,
            )
            is TrackingEnqueueResult.Rejected -> MalLibraryEditOutcome.Rejected(
                displayedItem = original,
                retryDraft = MalLibraryEditDraft.from(original),
                reason = result.reason,
                retryable = result.reason.isRetryableLibraryFailure(),
            )
        }
    }
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

private fun TrackingFailureKind.isRetryableLibraryFailure(): Boolean = this in setOf(
    TrackingFailureKind.NETWORK_BLOCKED,
    TrackingFailureKind.RATE_LIMITED,
    TrackingFailureKind.OFFLINE,
    TrackingFailureKind.TIMEOUT,
    TrackingFailureKind.TRANSPORT,
    TrackingFailureKind.TRANSIENT_SERVER,
    TrackingFailureKind.LEASE_EXPIRED,
)
