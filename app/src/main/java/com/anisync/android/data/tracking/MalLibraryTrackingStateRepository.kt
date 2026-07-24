package com.anisync.android.data.tracking

import com.anisync.android.data.local.dao.TrackingDao
import com.anisync.android.data.local.entity.ProviderTrackingSnapshotEntity
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingProvider
import com.anisync.android.domain.tracking.TrackingStatus
import com.anisync.android.domain.tracking.TrackingTargetState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

/**
 * Typed, read-only projection of the durable tracking journal for the MAL Library presentation.
 * Room entities and DAO details stay below this boundary.
 */
data class MalLibraryConfirmedSnapshot(
    val providerMediaId: Long,
    val mediaType: TrackingMediaType,
    val title: String,
    val coverUrl: String?,
    val status: TrackingStatus?,
    val progress: Int,
    val progressSecondary: Int?,
    val score100: Double?,
    val repeatCount: Int,
    val startedAt: String?,
    val completedAt: String?,
    val providerUpdatedAtEpochMillis: Long?,
    val fetchedAtEpochMillis: Long,
    val deleted: Boolean,
)

sealed interface MalLibraryTrackingState {
    data class Pending(
        val targetState: TrackingTargetState,
        val attemptCount: Int,
    ) : MalLibraryTrackingState

    /** Provider delivery completed; the matching persisted read-back snapshot is still being observed. */
    data class Delivered(
        val attemptCount: Int,
    ) : MalLibraryTrackingState

    data class RetryableFailure(
        val reason: TrackingFailureKind,
        val attemptCount: Int,
        val retryAfterMillis: Long?,
    ) : MalLibraryTrackingState

    data class Confirmed(
        val snapshot: MalLibraryConfirmedSnapshot,
    ) : MalLibraryTrackingState

    data class TerminalFailure(
        val reason: TrackingFailureKind,
        val targetState: TrackingTargetState,
    ) : MalLibraryTrackingState
}

@Singleton
class MalLibraryTrackingStateRepository @Inject constructor(
    private val dao: TrackingDao,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(
        operationId: String,
        providerMediaId: Long,
        mediaType: TrackingMediaType,
    ): Flow<MalLibraryTrackingState> = dao.observeAllTargets()
        .map { targets -> targets.firstOrNull { it.operationId == operationId } }
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { target ->
            val state = runCatching { TrackingTargetState.valueOf(target.state) }.getOrNull()
                ?: return@flatMapLatest flowOf(
                    MalLibraryTrackingState.TerminalFailure(
                        reason = TrackingFailureKind.INVALID_RESPONSE,
                        targetState = TrackingTargetState.FAILED,
                    )
                )
            when (state) {
                TrackingTargetState.PENDING,
                TrackingTargetState.RUNNING -> flowOf(
                    MalLibraryTrackingState.Pending(
                        targetState = state,
                        attemptCount = target.attemptCount,
                    )
                )

                TrackingTargetState.RETRYING -> flowOf(
                    MalLibraryTrackingState.RetryableFailure(
                        reason = target.lastErrorKind.toTrackingFailureOr(
                            TrackingFailureKind.TRANSIENT_SERVER
                        ),
                        attemptCount = target.attemptCount,
                        retryAfterMillis = target.retryAfterMillis,
                    )
                )

                TrackingTargetState.SUCCEEDED -> {
                    val accountId = target.providerAccountId
                    if (
                        target.provider != TrackingProvider.MYANIMELIST.name ||
                        accountId == null ||
                        target.providerMediaId != providerMediaId
                    ) {
                        flowOf(
                            MalLibraryTrackingState.TerminalFailure(
                                reason = TrackingFailureKind.INVALID_RESPONSE,
                                targetState = TrackingTargetState.FAILED,
                            )
                        )
                    } else {
                        flow {
                            emit(MalLibraryTrackingState.Delivered(target.attemptCount))
                            emitAll(
                                dao.observeSnapshots(
                                    provider = TrackingProvider.MYANIMELIST.name,
                                    providerAccountId = accountId,
                                    mediaType = mediaType.name,
                                ).map { snapshots ->
                                    snapshots.firstOrNull { snapshot ->
                                        snapshot.providerMediaId == providerMediaId &&
                                            snapshot.fetchedAtEpochMillis >= target.updatedAtEpochMillis
                                    }
                                }.filterNotNull()
                                    .take(1)
                                    .map { snapshot ->
                                        MalLibraryTrackingState.Confirmed(snapshot.toBoundaryModel())
                                    }
                            )
                        }
                    }
                }

                TrackingTargetState.BLOCKED,
                TrackingTargetState.FAILED,
                TrackingTargetState.SUPERSEDED -> flowOf(
                    MalLibraryTrackingState.TerminalFailure(
                        reason = target.lastErrorKind.toTrackingFailureOr(
                            if (state == TrackingTargetState.SUPERSEDED) {
                                TrackingFailureKind.PERMANENT
                            } else {
                                TrackingFailureKind.RETRY_BUDGET_EXHAUSTED
                            }
                        ),
                        targetState = state,
                    )
                )
            }
        }
        .distinctUntilChanged()
}

private fun ProviderTrackingSnapshotEntity.toBoundaryModel(): MalLibraryConfirmedSnapshot {
    val type = runCatching { TrackingMediaType.valueOf(mediaType) }.getOrElse {
        if (progressSecondary == null) TrackingMediaType.ANIME else TrackingMediaType.MANGA
    }
    val trackingStatus = if (isDeleted || status == "DELETED") {
        null
    } else {
        runCatching { TrackingStatus.valueOf(status) }.getOrNull()
    }
    return MalLibraryConfirmedSnapshot(
        providerMediaId = providerMediaId,
        mediaType = type,
        title = title,
        coverUrl = coverUrl,
        status = trackingStatus,
        progress = progress.coerceAtLeast(0),
        progressSecondary = progressSecondary?.coerceAtLeast(0),
        score100 = score?.coerceIn(0.0, 100.0),
        repeatCount = repeatCount.coerceAtLeast(0),
        startedAt = startedAt,
        completedAt = completedAt,
        providerUpdatedAtEpochMillis = providerUpdatedAtEpochMillis,
        fetchedAtEpochMillis = fetchedAtEpochMillis,
        deleted = isDeleted,
    )
}

private fun String?.toTrackingFailureOr(fallback: TrackingFailureKind): TrackingFailureKind =
    this?.let { value -> runCatching { TrackingFailureKind.valueOf(value) }.getOrNull() } ?: fallback
