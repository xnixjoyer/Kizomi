package com.anisync.android.data.tracking

import androidx.room.withTransaction
import com.anisync.android.data.local.AppDatabase
import com.anisync.android.data.local.dao.TrackingDao
import com.anisync.android.data.local.dao.TrackingReconciliationDao
import com.anisync.android.data.local.entity.TrackingReconciliationItemEntity
import com.anisync.android.data.local.entity.TrackingReconciliationPlanEntity
import com.anisync.android.domain.tracking.TrackingEnqueueResult
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingOperationState
import com.anisync.android.domain.tracking.TrackingProvider
import com.anisync.android.domain.tracking.TrackingTargetState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Immutable compare planner and resumable missing-only executor.
 *
 * It never creates update or delete commands. Existing target rows, ambiguous identities and
 * provider-exclusive fields are persisted as non-executable preview items.
 */
@Singleton
class TrackingReconciliationService @Inject constructor(
    private val database: AppDatabase,
    private val dao: TrackingReconciliationDao,
    private val trackingDao: TrackingDao,
    private val commands: TrackingCommandService,
) {
    suspend fun createMissingOnlyPreview(
        mediaType: TrackingMediaType,
        direction: ReconciliationDirection,
        sourceAccountId: String,
        targetAccountId: String,
    ): ReconciliationCreateResult {
        if (sourceAccountId.isBlank() || targetAccountId.isBlank()) {
            return ReconciliationCreateResult.Rejected(TrackingFailureKind.MISSING_ACCOUNT)
        }
        val sourceRows = dao.getActiveSnapshots(
            direction.source.name,
            sourceAccountId,
            mediaType.name,
        )
        val targetRows = dao.getActiveSnapshots(
            direction.target.name,
            targetAccountId,
            mediaType.name,
        )
        val sourceByLocal = sourceRows.associateBy { it.localMediaId }
        val targetByLocal = targetRows.associateBy { it.localMediaId }
        val allLocalIds = (sourceByLocal.keys + targetByLocal.keys).sorted()
        val now = System.currentTimeMillis()
        val planId = UUID.randomUUID().toString()
        val fingerprintParts = mutableListOf<String>()
        val items = mutableListOf<TrackingReconciliationItemEntity>()

        for (localMediaId in allLocalIds) {
            val source = sourceByLocal[localMediaId]
            val target = targetByLocal[localMediaId]
            val sourcePayload = source?.toReconciliationPayload()
            val targetPayload = target?.toReconciliationPayload()
            fingerprintParts += sourcePayload?.let { reconciliationJson.encodeToString(it) }
                ?: "source:none:$localMediaId"
            fingerprintParts += targetPayload?.let { reconciliationJson.encodeToString(it) }
                ?: "target:none:$localMediaId"

            var action = ReconciliationAction.BLOCKED_CONFLICT
            var itemState = ReconciliationItemState.BLOCKED
            var commandJson: String? = null
            var lastError: TrackingFailureKind? = null

            when {
                source != null && sourcePayload == null -> {
                    lastError = TrackingFailureKind.INVALID_RESPONSE
                }
                target != null && targetPayload == null -> {
                    lastError = TrackingFailureKind.INVALID_RESPONSE
                }
                source != null && target != null -> {
                    val issueCount = dao.countBlockingIdentityIssues(
                        localMediaId,
                        direction.target.name,
                        mediaType.name,
                    )
                    when {
                        issueCount > 0 -> lastError = TrackingFailureKind.MISSING_IDENTITY
                        sourcePayload!!.state == targetPayload!!.state -> {
                            action = ReconciliationAction.EQUAL
                            itemState = ReconciliationItemState.SKIPPED
                        }
                        else -> {
                            action = ReconciliationAction.DIFFERENT
                            itemState = ReconciliationItemState.SKIPPED
                        }
                    }
                }
                source != null -> {
                    val issueCount = dao.countBlockingIdentityIssues(
                        localMediaId,
                        direction.target.name,
                        mediaType.name,
                    )
                    val targetIdentity = dao.findProviderIdentity(
                        localMediaId,
                        direction.target.name,
                        mediaType.name,
                    )?.takeIf {
                        it.providerMediaId > 0L && it.verificationStatus in trustedIdentityStates
                    }
                    when {
                        issueCount > 0 -> lastError = TrackingFailureKind.MISSING_IDENTITY
                        targetIdentity == null -> {
                            action = ReconciliationAction.UNMAPPED
                            lastError = TrackingFailureKind.MISSING_IDENTITY
                        }
                        else -> {
                            val desired = sourcePayload!!.state
                            val fields = desired.creationFields()
                            val unsupported = when (direction.target) {
                                TrackingProvider.ANILIST ->
                                    AniListTrackingCapabilities.unsupported(fields)
                                TrackingProvider.MYANIMELIST ->
                                    MalTrackingCapabilities.forMediaType(mediaType).unsupported(fields)
                            }
                            if (unsupported.isNotEmpty()) {
                                lastError = TrackingFailureKind.UNSUPPORTED_FIELD
                            } else {
                                action = ReconciliationAction.ONLY_SOURCE
                                itemState = ReconciliationItemState.READY
                                commandJson = reconciliationJson.encodeToString(
                                    MissingOnlyCommandEnvelope(
                                        localMediaId = localMediaId,
                                        mediaType = mediaType,
                                        targetProvider = direction.target,
                                        targetAccountId = targetAccountId,
                                        targetMediaId = targetIdentity.providerMediaId,
                                        desired = desired,
                                        fields = fields,
                                    )
                                )
                                fingerprintParts += listOf(
                                    targetIdentity.provider,
                                    targetIdentity.providerMediaId.toString(),
                                    targetIdentity.verificationStatus,
                                ).joinToString(":")
                            }
                        }
                    }
                }
                target != null -> {
                    action = ReconciliationAction.ONLY_TARGET
                    itemState = ReconciliationItemState.SKIPPED
                }
            }

            items += TrackingReconciliationItemEntity(
                planId = planId,
                itemKey = reconciliationSha256(
                    listOf(
                        mediaType.name,
                        direction.source.name,
                        sourceAccountId,
                        direction.target.name,
                        targetAccountId,
                        localMediaId,
                    ).joinToString(":"),
                ),
                localMediaId = localMediaId,
                mediaType = mediaType.name,
                aniListId = if (direction.source == TrackingProvider.ANILIST) {
                    source?.providerMediaId
                } else {
                    target?.providerMediaId
                },
                malId = if (direction.source == TrackingProvider.MYANIMELIST) {
                    source?.providerMediaId
                } else {
                    target?.providerMediaId
                },
                action = action.name,
                state = itemState.name,
                sourceSnapshotJson = sourcePayload?.let { reconciliationJson.encodeToString(it) },
                targetSnapshotJson = targetPayload?.let { reconciliationJson.encodeToString(it) },
                commandJson = commandJson,
                operationId = null,
                lastErrorKind = lastError?.name,
                updatedAtEpochMillis = now,
            )
        }

        val plan = TrackingReconciliationPlanEntity(
            planId = planId,
            mode = reconciliationMode(direction),
            mediaType = mediaType.name,
            sourceAccountId = sourceAccountId,
            targetAccountId = targetAccountId,
            state = ReconciliationPlanState.PREVIEW.name,
            baselineFingerprint = reconciliationSha256(
                fingerprintParts.sorted().joinToString("\n"),
            ),
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        return try {
            database.withTransaction { dao.insertPlanWithItems(plan, items) }
            ReconciliationCreateResult.Created(planId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            ReconciliationCreateResult.Rejected(TrackingFailureKind.STORAGE)
        }
    }

    fun observePlan(planId: String): Flow<ReconciliationPlanView?> = combine(
        dao.observePlan(planId),
        dao.observeItems(planId),
    ) { plan, items -> plan?.toView(items) }

    suspend fun executeMissingOnly(planId: String): ReconciliationPlanView? {
        val plan = dao.getPlan(planId) ?: return null
        val direction = parseReconciliationDirection(plan.mode) ?: return null
        if (plan.state == ReconciliationPlanState.SUCCEEDED.name) return refresh(planId)
        dao.updatePlan(planId, ReconciliationPlanState.RUNNING.name, System.currentTimeMillis())
        try {
            refreshEnqueuedItems(planId)
            for (item in dao.getItems(planId)) {
                val state = runCatching { ReconciliationItemState.valueOf(item.state) }.getOrNull()
                if (state !in setOf(ReconciliationItemState.READY, ReconciliationItemState.CLAIMED)) {
                    continue
                }
                val localMediaId = item.localMediaId
                val command = item.commandJson?.let { encoded ->
                    runCatching {
                        reconciliationJson.decodeFromString<MissingOnlyCommandEnvelope>(encoded)
                    }.getOrNull()
                }
                if (localMediaId == null || command == null || command.targetProvider != direction.target) {
                    updateItem(item, ReconciliationItemState.BLOCKED, null, TrackingFailureKind.VALIDATION)
                    continue
                }

                // Missing-only invariant and crash recovery: if the target appeared since preview or
                // after an earlier delivery, never enqueue another mutation.
                val existingTarget = dao.findActiveSnapshot(
                    provider = direction.target.name,
                    accountId = requireNotNull(plan.targetAccountId),
                    mediaType = plan.mediaType,
                    localMediaId = localMediaId,
                )
                if (existingTarget != null) {
                    updateItem(item, ReconciliationItemState.SKIPPED_PRESENT, item.operationId, null)
                    continue
                }

                updateItem(item, ReconciliationItemState.CLAIMED, item.operationId, null)
                when (val result = commands.enqueueExact(command.toExactInput())) {
                    is TrackingEnqueueResult.Rejected -> updateItem(
                        item,
                        ReconciliationItemState.BLOCKED,
                        null,
                        result.reason,
                    )
                    is TrackingEnqueueResult.Accepted -> recordAccepted(item, direction.target, result)
                }
            }
            refreshEnqueuedItems(planId)
            updateAggregatePlanState(planId)
            return refresh(planId)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                dao.updatePlan(planId, ReconciliationPlanState.PAUSED.name, System.currentTimeMillis())
            }
            throw cancelled
        } catch (_: Throwable) {
            dao.updatePlan(planId, ReconciliationPlanState.FAILED.name, System.currentTimeMillis())
            return refresh(planId)
        }
    }

    suspend fun pause(planId: String): Boolean =
        dao.updatePlan(planId, ReconciliationPlanState.PAUSED.name, System.currentTimeMillis()) == 1

    suspend fun refresh(planId: String): ReconciliationPlanView? {
        refreshEnqueuedItems(planId)
        updateAggregatePlanState(planId)
        val plan = dao.getPlan(planId) ?: return null
        return plan.toView(dao.getItems(planId))
    }

    private suspend fun recordAccepted(
        item: TrackingReconciliationItemEntity,
        targetProvider: TrackingProvider,
        result: TrackingEnqueueResult.Accepted,
    ) {
        if (result.receipt.targetStates[targetProvider] == TrackingTargetState.BLOCKED) {
            val target = trackingDao.getTargets(result.receipt.operationId)
                .firstOrNull { it.provider == targetProvider.name }
            val reason = target?.lastErrorKind?.let { value ->
                runCatching { TrackingFailureKind.valueOf(value) }.getOrNull()
            } ?: TrackingFailureKind.PERMANENT
            updateItem(
                item,
                ReconciliationItemState.BLOCKED,
                result.receipt.operationId,
                reason,
            )
        } else {
            updateItem(
                item,
                ReconciliationItemState.ENQUEUED,
                result.receipt.operationId,
                null,
            )
        }
    }

    private suspend fun refreshEnqueuedItems(planId: String) {
        for (item in dao.getItems(planId)) {
            if (item.state != ReconciliationItemState.ENQUEUED.name) continue
            val operationId = item.operationId ?: continue
            val operation = trackingDao.getOperation(operationId) ?: continue
            when (runCatching { TrackingOperationState.valueOf(operation.state) }.getOrNull()) {
                TrackingOperationState.SUCCEEDED -> updateItem(
                    item,
                    ReconciliationItemState.SUCCEEDED,
                    operationId,
                    null,
                )
                TrackingOperationState.FAILED,
                TrackingOperationState.PARTIAL_FAILURE -> updateItem(
                    item,
                    ReconciliationItemState.FAILED,
                    operationId,
                    firstOperationFailure(operationId) ?: TrackingFailureKind.PERMANENT,
                )
                TrackingOperationState.BLOCKED -> updateItem(
                    item,
                    ReconciliationItemState.BLOCKED,
                    operationId,
                    firstOperationFailure(operationId) ?: TrackingFailureKind.PERMANENT,
                )
                TrackingOperationState.SUPERSEDED -> updateItem(
                    item,
                    ReconciliationItemState.FAILED,
                    operationId,
                    TrackingFailureKind.PERMANENT,
                )
                else -> Unit
            }
        }
    }

    private suspend fun firstOperationFailure(operationId: String): TrackingFailureKind? =
        trackingDao.getTargets(operationId).firstNotNullOfOrNull { target ->
            target.lastErrorKind?.let { value ->
                runCatching { TrackingFailureKind.valueOf(value) }.getOrNull()
            }
        }

    private suspend fun updateAggregatePlanState(planId: String) {
        val current = dao.getPlan(planId) ?: return
        if (current.state in setOf(
                ReconciliationPlanState.PREVIEW.name,
                ReconciliationPlanState.PAUSED.name,
            )
        ) {
            return
        }
        val states = dao.getItems(planId).mapNotNull { item ->
            runCatching { ReconciliationItemState.valueOf(item.state) }.getOrNull()
        }
        val next = when {
            states.any { it in setOf(ReconciliationItemState.READY, ReconciliationItemState.CLAIMED) } ->
                ReconciliationPlanState.PAUSED
            states.any { it == ReconciliationItemState.ENQUEUED } -> ReconciliationPlanState.RUNNING
            states.any { it in setOf(ReconciliationItemState.FAILED, ReconciliationItemState.BLOCKED) } ->
                ReconciliationPlanState.PARTIAL_FAILURE
            else -> ReconciliationPlanState.SUCCEEDED
        }
        dao.updatePlan(planId, next.name, System.currentTimeMillis())
    }

    private suspend fun updateItem(
        item: TrackingReconciliationItemEntity,
        state: ReconciliationItemState,
        operationId: String?,
        failure: TrackingFailureKind?,
    ) {
        dao.updateItem(
            planId = item.planId,
            itemKey = item.itemKey,
            state = state.name,
            operationId = operationId,
            lastErrorKind = failure?.name,
            nowEpochMillis = System.currentTimeMillis(),
        )
    }
}
