package com.anisync.android.data.tracking

import com.anisync.android.data.local.dao.TrackingAccountPairBinding
import com.anisync.android.data.local.dao.TrackingConflictDao
import com.anisync.android.data.local.dao.TrackingDao
import com.anisync.android.data.local.entity.ProviderTrackingSnapshotEntity
import com.anisync.android.domain.tracking.TrackingDesiredState
import com.anisync.android.domain.tracking.TrackingEnqueueResult
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingField
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingOperationState
import com.anisync.android.domain.tracking.TrackingProvider
import com.anisync.android.domain.tracking.TrackingStatus
import com.anisync.android.domain.tracking.TrackingTargetState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    DELETE,
}

/**
 * One persisted, exact account pair. Account and identity values are intentionally available only to
 * the resolution path; [toString] and the UI never reveal them.
 */
data class TrackingProviderConflict(
    val localMediaId: String,
    val mediaType: TrackingMediaType,
    val title: String,
    val aniListAccountId: String,
    val malAccountId: String,
    val aniListMediaId: Long,
    val malMediaId: Long,
    val aniListListEntryId: Long?,
    val malListEntryId: Long?,
    val aniListState: TrackingDesiredState?,
    val malState: TrackingDesiredState?,
    val aniListDeleted: Boolean,
    val malDeleted: Boolean,
    val differingFields: Set<TrackingConflictField>,
    val aniListUpdatedAtEpochMillis: Long?,
    val malUpdatedAtEpochMillis: Long?,
) {
    fun blockedFieldsWhenUsing(source: TrackingProvider): Set<TrackingConflictField> {
        val target = source.other()
        val requested = requestedTrackingFieldsWhenUsing(source)
        val unsupported = when (target) {
            TrackingProvider.ANILIST -> AniListTrackingCapabilities.unsupported(requested)
            TrackingProvider.MYANIMELIST ->
                MalTrackingCapabilities.forMediaType(mediaType).unsupported(requested)
        }
        return unsupported.mapNotNullTo(linkedSetOf()) { it.conflictFieldOrNull() }
    }

    fun resolutionBlockerWhenUsing(source: TrackingProvider): TrackingFailureKind? {
        if (blockedFieldsWhenUsing(source).isNotEmpty()) {
            return TrackingFailureKind.UNSUPPORTED_FIELD
        }
        val sourceDeleted = when (source) {
            TrackingProvider.ANILIST -> aniListDeleted
            TrackingProvider.MYANIMELIST -> malDeleted
        }
        val targetListEntryId = when (source.other()) {
            TrackingProvider.ANILIST -> aniListListEntryId
            TrackingProvider.MYANIMELIST -> malListEntryId
        }
        val sourceState = when (source) {
            TrackingProvider.ANILIST -> aniListState
            TrackingProvider.MYANIMELIST -> malState
        }
        return when {
            sourceDeleted && targetListEntryId == null -> TrackingFailureKind.MISSING_IDENTITY
            !sourceDeleted && sourceState == null -> TrackingFailureKind.INVALID_RESPONSE
            else -> null
        }
    }

    fun canResolveFrom(source: TrackingProvider): Boolean =
        resolutionBlockerWhenUsing(source) == null

    internal fun requestedTrackingFieldsWhenUsing(source: TrackingProvider): Set<TrackingField> {
        val sourceDeleted = when (source) {
            TrackingProvider.ANILIST -> aniListDeleted
            TrackingProvider.MYANIMELIST -> malDeleted
        }
        if (sourceDeleted) return setOf(TrackingField.DELETE)
        if (TrackingConflictField.DELETE in differingFields) {
            val sourceState = when (source) {
                TrackingProvider.ANILIST -> aniListState
                TrackingProvider.MYANIMELIST -> malState
            }
            return sourceState?.resolutionFields().orEmpty()
        }
        return differingFields.mapTo(linkedSetOf()) { it.trackingField() }
    }

    override fun toString(): String =
        "TrackingProviderConflict(localMediaId=<redacted>, mediaType=${mediaType.name}, " +
            "title=<redacted>, aniListAccountId=<redacted>, malAccountId=<redacted>, " +
            "aniListMediaId=<redacted>, malMediaId=<redacted>, differingFields=$differingFields)"
}

/** Durable dual-target result and account-bound divergence read model used by the conflict center. */
@Singleton
class TrackingSagaRepository @Inject constructor(
    private val dao: TrackingDao,
    private val conflictDao: TrackingConflictDao,
    private val scheduler: TrackingOutboxScheduler,
    private val commands: TrackingCommandService,
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

    fun observeConflicts(): Flow<List<TrackingProviderConflict>> = combine(
        conflictDao.observeAllSnapshots(),
        conflictDao.observeDualBindings(),
        ::buildConflicts,
    )

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

    /**
     * Explicitly copies one chosen persisted provider truth to the other exact provider/account.
     * The complete conflict is re-read first so stale UI cannot overwrite a newer generation.
     */
    suspend fun resolveConflict(
        requested: TrackingProviderConflict,
        source: TrackingProvider,
    ): TrackingEnqueueResult {
        val current = buildConflicts(
            conflictDao.getAllSnapshots(),
            conflictDao.getDualBindings(),
        ).firstOrNull { it == requested }
            ?: return TrackingEnqueueResult.Rejected(TrackingFailureKind.MISSING_IDENTITY)
        current.resolutionBlockerWhenUsing(source)?.let {
            return TrackingEnqueueResult.Rejected(it)
        }

        val target = source.other()
        val sourceDeleted = when (source) {
            TrackingProvider.ANILIST -> current.aniListDeleted
            TrackingProvider.MYANIMELIST -> current.malDeleted
        }
        val desired = if (sourceDeleted) {
            TrackingDesiredState(status = null, progress = 0)
        } else {
            requireNotNull(
                when (source) {
                    TrackingProvider.ANILIST -> current.aniListState
                    TrackingProvider.MYANIMELIST -> current.malState
                }
            )
        }
        val targetAccount = when (target) {
            TrackingProvider.ANILIST -> current.aniListAccountId
            TrackingProvider.MYANIMELIST -> current.malAccountId
        }
        val targetMediaId = when (target) {
            TrackingProvider.ANILIST -> current.aniListMediaId
            TrackingProvider.MYANIMELIST -> current.malMediaId
        }
        val targetListEntryId = when (target) {
            TrackingProvider.ANILIST -> current.aniListListEntryId
            TrackingProvider.MYANIMELIST -> current.malListEntryId
        }
        return commands.enqueueExact(
            ExactTrackingCommandInput(
                localMediaId = current.localMediaId,
                mediaType = current.mediaType,
                desired = desired,
                fields = current.requestedTrackingFieldsWhenUsing(source),
                provider = target,
                providerAccountId = targetAccount,
                providerMediaId = targetMediaId,
                providerListEntryId = if (sourceDeleted) targetListEntryId else null,
                deleteIntent = sourceDeleted,
            )
        )
    }
}

internal fun buildConflicts(
    snapshots: List<ProviderTrackingSnapshotEntity>,
    bindings: List<TrackingAccountPairBinding> = inferBindings(snapshots),
): List<TrackingProviderConflict> = bindings.mapNotNull { binding ->
    val aniListAccount = binding.aniListAccountId ?: return@mapNotNull null
    val malAccount = binding.malAccountId ?: return@mapNotNull null
    val aniList = snapshots.singleOrNull {
        it.localMediaId == binding.localMediaId &&
            it.mediaType == binding.mediaType &&
            it.provider == TrackingProvider.ANILIST.name &&
            it.providerAccountId == aniListAccount
    } ?: return@mapNotNull null
    val mal = snapshots.singleOrNull {
        it.localMediaId == binding.localMediaId &&
            it.mediaType == binding.mediaType &&
            it.provider == TrackingProvider.MYANIMELIST.name &&
            it.providerAccountId == malAccount
    } ?: return@mapNotNull null
    buildConflict(aniList, mal)
}.distinct().sortedWith(
    compareBy(
        TrackingProviderConflict::mediaType,
        TrackingProviderConflict::title,
        TrackingProviderConflict::aniListAccountId,
        TrackingProviderConflict::malAccountId,
    )
)

private fun inferBindings(
    snapshots: List<ProviderTrackingSnapshotEntity>,
): List<TrackingAccountPairBinding> = snapshots
    .groupBy { it.localMediaId to it.mediaType }
    .flatMap { (key, rows) ->
        val aniListAccounts = rows.filter { it.provider == TrackingProvider.ANILIST.name }
            .map(ProviderTrackingSnapshotEntity::providerAccountId)
            .distinct()
        val malAccounts = rows.filter { it.provider == TrackingProvider.MYANIMELIST.name }
            .map(ProviderTrackingSnapshotEntity::providerAccountId)
            .distinct()
        aniListAccounts.flatMap { aniListAccount ->
            malAccounts.map { malAccount ->
                TrackingAccountPairBinding(
                    localMediaId = key.first,
                    mediaType = key.second,
                    aniListAccountId = aniListAccount,
                    malAccountId = malAccount,
                )
            }
        }
    }

private fun buildConflict(
    aniList: ProviderTrackingSnapshotEntity,
    mal: ProviderTrackingSnapshotEntity,
): TrackingProviderConflict? {
    if (aniList.localMediaId != mal.localMediaId || aniList.mediaType != mal.mediaType) return null
    if (aniList.isDeleted && mal.isDeleted) return null
    val mediaType = runCatching { TrackingMediaType.valueOf(aniList.mediaType) }.getOrNull() ?: return null
    val aniListState = if (aniList.isDeleted) null else aniList.toDesiredState() ?: return null
    val malState = if (mal.isDeleted) null else mal.toDesiredState() ?: return null
    val differences = if (aniList.isDeleted != mal.isDeleted) {
        setOf(TrackingConflictField.DELETE)
    } else {
        buildSet {
            requireNotNull(aniListState)
            requireNotNull(malState)
            if (aniListState.status != malState.status) add(TrackingConflictField.STATUS)
            if (aniListState.progress != malState.progress) add(TrackingConflictField.PROGRESS)
            if (aniListState.progressSecondary != malState.progressSecondary) {
                add(TrackingConflictField.PROGRESS_SECONDARY)
            }
            if (aniListState.score100 != malState.score100) add(TrackingConflictField.SCORE)
            if (aniListState.repeatCount != malState.repeatCount) {
                add(TrackingConflictField.REPEAT_COUNT)
            }
            if (aniListState.notes != malState.notes) add(TrackingConflictField.NOTES)
            if (aniListState.startedAt != malState.startedAt) add(TrackingConflictField.STARTED_AT)
            if (aniListState.completedAt != malState.completedAt) {
                add(TrackingConflictField.COMPLETED_AT)
            }
        }
    }
    if (differences.isEmpty()) return null
    return TrackingProviderConflict(
        localMediaId = aniList.localMediaId,
        mediaType = mediaType,
        title = aniList.title.ifBlank { mal.title },
        aniListAccountId = aniList.providerAccountId,
        malAccountId = mal.providerAccountId,
        aniListMediaId = aniList.providerMediaId,
        malMediaId = mal.providerMediaId,
        aniListListEntryId = aniList.providerListEntryId,
        malListEntryId = mal.providerListEntryId,
        aniListState = aniListState,
        malState = malState,
        aniListDeleted = aniList.isDeleted,
        malDeleted = mal.isDeleted,
        differingFields = differences,
        aniListUpdatedAtEpochMillis = aniList.providerUpdatedAtEpochMillis,
        malUpdatedAtEpochMillis = mal.providerUpdatedAtEpochMillis,
    )
}

private fun ProviderTrackingSnapshotEntity.toDesiredState(): TrackingDesiredState? {
    val parsedStatus = runCatching { TrackingStatus.valueOf(status) }.getOrNull() ?: return null
    return runCatching {
        TrackingDesiredState(
            status = parsedStatus,
            progress = progress,
            progressSecondary = progressSecondary,
            score100 = score,
            repeatCount = repeatCount,
            notes = notes,
            startedAt = startedAt,
            completedAt = completedAt,
        )
    }.getOrNull()
}

private fun TrackingDesiredState.resolutionFields(): Set<TrackingField> = buildSet {
    add(TrackingField.STATUS)
    add(TrackingField.PROGRESS)
    add(TrackingField.SCORE)
    add(TrackingField.REPEAT_COUNT)
    add(TrackingField.STARTED_AT)
    add(TrackingField.COMPLETED_AT)
    if (progressSecondary != null) add(TrackingField.PROGRESS_SECONDARY)
    if (notes != null) add(TrackingField.NOTES)
}

private fun TrackingProvider.other(): TrackingProvider = when (this) {
    TrackingProvider.ANILIST -> TrackingProvider.MYANIMELIST
    TrackingProvider.MYANIMELIST -> TrackingProvider.ANILIST
}

private fun TrackingConflictField.trackingField(): TrackingField = when (this) {
    TrackingConflictField.STATUS -> TrackingField.STATUS
    TrackingConflictField.PROGRESS -> TrackingField.PROGRESS
    TrackingConflictField.PROGRESS_SECONDARY -> TrackingField.PROGRESS_SECONDARY
    TrackingConflictField.SCORE -> TrackingField.SCORE
    TrackingConflictField.REPEAT_COUNT -> TrackingField.REPEAT_COUNT
    TrackingConflictField.NOTES -> TrackingField.NOTES
    TrackingConflictField.STARTED_AT -> TrackingField.STARTED_AT
    TrackingConflictField.COMPLETED_AT -> TrackingField.COMPLETED_AT
    TrackingConflictField.DELETE -> TrackingField.DELETE
}

private fun TrackingField.conflictFieldOrNull(): TrackingConflictField? = when (this) {
    TrackingField.STATUS -> TrackingConflictField.STATUS
    TrackingField.PROGRESS -> TrackingConflictField.PROGRESS
    TrackingField.PROGRESS_SECONDARY -> TrackingConflictField.PROGRESS_SECONDARY
    TrackingField.SCORE -> TrackingConflictField.SCORE
    TrackingField.REPEAT_COUNT -> TrackingConflictField.REPEAT_COUNT
    TrackingField.NOTES -> TrackingConflictField.NOTES
    TrackingField.STARTED_AT -> TrackingConflictField.STARTED_AT
    TrackingField.COMPLETED_AT -> TrackingConflictField.COMPLETED_AT
    TrackingField.DELETE -> TrackingConflictField.DELETE
    TrackingField.CUSTOM_LISTS,
    TrackingField.PRIVATE,
    TrackingField.HIDDEN_FROM_STATUS_LISTS -> null
}
