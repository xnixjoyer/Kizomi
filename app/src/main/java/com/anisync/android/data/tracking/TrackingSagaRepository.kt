package com.anisync.android.data.tracking

import com.anisync.android.data.local.dao.TrackingDao
import com.anisync.android.data.local.entity.ProviderTrackingSnapshotEntity
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingOperationState
import com.anisync.android.domain.tracking.TrackingProvider
import com.anisync.android.domain.tracking.TrackingTargetState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class TrackingSagaTarget(
    val provider: TrackingProvider,
    val state: TrackingTargetState,
    val attemptCount: Int,
    val failureKind: TrackingFailureKind?,
    val httpStatus: Int?,
) {
    val retryableByUser: Boolean get() = state == TrackingTargetState.FAILED
}

data class TrackingSagaOperation(
    val operationId: String,
    val mediaType: TrackingMediaType,
    val state: TrackingOperationState,
    val isDelete: Boolean,
    val fieldNames: List<String>,
    val targets: List<TrackingSagaTarget>,
    val updatedAtEpochMillis: Long,
) {
    override fun toString(): String =
        "TrackingSagaOperation(operationId=<redacted>, mediaType=${mediaType.name}, " +
            "state=${state.name}, isDelete=$isDelete, fieldNames=$fieldNames, " +
            "targetCount=${targets.size}, updatedAtEpochMillis=$updatedAtEpochMillis)"
}

enum class TrackingConflictField {
    STATUS,
    PROGRESS,
    PROGRESS_SECONDARY,
    SCORE,
    REPEAT_COUNT,
    NOTES,
    STARTED_AT,
    COMPLETED_AT,
}

data class TrackingProviderConflict(
    val localMediaId: String,
    val mediaType: TrackingMediaType,
    val title: String,
    val differingFields: Set<TrackingConflictField>,
    val aniListUpdatedAtEpochMillis: Long?,
    val malUpdatedAtEpochMillis: Long?,
) {
    override fun toString(): String =
        "TrackingProviderConflict(localMediaId=<redacted>, mediaType=${mediaType.name}, " +
            "title=<redacted>, differingFields=$differingFields)"
}

/** Durable dual-target result and divergence read model used by the conflict center. */
@Singleton
class TrackingSagaRepository @Inject constructor(
    private val dao: TrackingDao,
    private val scheduler: TrackingOutboxScheduler,
) {
    fun observeOperations(): Flow<List<TrackingSagaOperation>> = combine(
        dao.observeRecentOperations(),
        dao.observeAllTargets(),
    ) { operations, targets ->
        val targetsByOperation = targets.groupBy { it.operationId }
        operations.mapNotNull { operation ->
            runCatching {
                TrackingSagaOperation(
                    operationId = operation.operationId,
                    mediaType = TrackingMediaType.valueOf(operation.mediaType),
                    state = TrackingOperationState.valueOf(operation.state),
                    isDelete = operation.isTombstone,
                    fieldNames = operation.fieldMask.split(',').filter(String::isNotBlank),
                    targets = targetsByOperation[operation.operationId].orEmpty().map { target ->
                        TrackingSagaTarget(
                            provider = TrackingProvider.valueOf(target.provider),
                            state = TrackingTargetState.valueOf(target.state),
                            attemptCount = target.attemptCount,
                            failureKind = target.lastErrorKind?.let { value ->
                                runCatching { TrackingFailureKind.valueOf(value) }.getOrNull()
                            },
                            httpStatus = target.lastHttpStatus,
                        )
                    },
                    updatedAtEpochMillis = operation.updatedAtEpochMillis,
                )
            }.getOrNull()
        }
    }

    fun observeConflicts(): Flow<List<TrackingProviderConflict>> =
        dao.observeAllActiveSnapshots().map(::buildConflicts)

    /** Reopens only explicitly failed targets; successful providers can never be selected here. */
    suspend fun retryFailed(
        operationId: String,
        providers: Set<TrackingProvider>,
    ): Int {
        if (operationId.isBlank() || providers.isEmpty()) return 0
        val now = System.currentTimeMillis()
        var retried = 0
        for (provider in providers) {
            retried += dao.retryFailedTarget(operationId, provider.name, now)
        }
        if (retried > 0) {
            dao.updateOperationState(operationId, TrackingOperationState.PENDING.name, now)
            scheduler.enqueue()
        }
        return retried
    }
}

internal fun buildConflicts(
    snapshots: List<ProviderTrackingSnapshotEntity>,
): List<TrackingProviderConflict> = snapshots.groupBy { it.localMediaId }.mapNotNull { (localId, rows) ->
    val aniList = rows.singleOrNull { it.provider == TrackingProvider.ANILIST.name }
        ?: return@mapNotNull null
    val mal = rows.singleOrNull { it.provider == TrackingProvider.MYANIMELIST.name }
        ?: return@mapNotNull null
    if (aniList.mediaType != mal.mediaType) return@mapNotNull null
    val differences = buildSet {
        if (aniList.status != mal.status) add(TrackingConflictField.STATUS)
        if (aniList.progress != mal.progress) add(TrackingConflictField.PROGRESS)
        if (aniList.progressSecondary != mal.progressSecondary) {
            add(TrackingConflictField.PROGRESS_SECONDARY)
        }
        if (aniList.score != mal.score) add(TrackingConflictField.SCORE)
        if (aniList.repeatCount != mal.repeatCount) add(TrackingConflictField.REPEAT_COUNT)
        if (aniList.notes != mal.notes) add(TrackingConflictField.NOTES)
        if (aniList.startedAt != mal.startedAt) add(TrackingConflictField.STARTED_AT)
        if (aniList.completedAt != mal.completedAt) add(TrackingConflictField.COMPLETED_AT)
    }
    if (differences.isEmpty()) return@mapNotNull null
    TrackingProviderConflict(
        localMediaId = localId,
        mediaType = TrackingMediaType.valueOf(aniList.mediaType),
        title = aniList.title.ifBlank { mal.title },
        differingFields = differences,
        aniListUpdatedAtEpochMillis = aniList.providerUpdatedAtEpochMillis,
        malUpdatedAtEpochMillis = mal.providerUpdatedAtEpochMillis,
    )
}.sortedWith(compareBy(TrackingProviderConflict::mediaType, TrackingProviderConflict::title))
