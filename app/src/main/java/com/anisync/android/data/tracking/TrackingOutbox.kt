package com.anisync.android.data.tracking

import androidx.room.withTransaction
import com.anisync.android.data.local.AppDatabase
import com.anisync.android.data.local.dao.TrackingDao
import com.anisync.android.data.local.entity.TrackingOperationEntity
import com.anisync.android.data.local.entity.TrackingOperationTargetEntity
import com.anisync.android.domain.tracking.TrackingCommand
import com.anisync.android.domain.tracking.TrackingCommandDraft
import com.anisync.android.domain.tracking.TrackingCommandTarget
import com.anisync.android.domain.tracking.TrackingEnqueueReceipt
import com.anisync.android.domain.tracking.TrackingEnqueueResult
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingOperationState
import com.anisync.android.domain.tracking.TrackingProvider
import com.anisync.android.domain.tracking.TrackingTargetState
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface TrackingOutboxScheduler {
    fun enqueue()
}

@Singleton
class TrackingCommandCodec @Inject constructor() {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = true
    }

    fun encode(command: TrackingCommand): String = json.encodeToString(command)
    fun decode(value: String): TrackingCommand = json.decodeFromString(value)

    fun deduplicationKey(
        draft: TrackingCommandDraft,
        target: TrackingCommandTarget,
    ): String {
        val normalizedDraft = draft.copy(
            fields = draft.fields.sortedBy { it.name }.toCollection(linkedSetOf()),
        )
        val envelope = TrackingDedupeEnvelope(normalizedDraft, target)
        return MessageDigest.getInstance("SHA-256")
            .digest(json.encodeToString(envelope).toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

@Serializable
private data class TrackingDedupeEnvelope(
    val draft: TrackingCommandDraft,
    val target: TrackingCommandTarget,
)

@Singleton
class TrackingOutboxRepository @Inject constructor(
    private val database: AppDatabase,
    private val dao: TrackingDao,
    private val codec: TrackingCommandCodec,
    private val scheduler: TrackingOutboxScheduler,
) {
    suspend fun enqueue(
        draft: TrackingCommandDraft,
        target: TrackingCommandTarget,
    ): TrackingEnqueueResult {
        val deduplicationKey = codec.deduplicationKey(draft, target)
        return try {
            val receipt = database.withTransaction {
                val existing = dao.findUnsettledByDeduplicationKey(deduplicationKey)
                if (existing != null) {
                    val existingTarget = dao.getTarget(existing.operationId)
                        ?: return@withTransaction null
                    return@withTransaction existing.toReceipt(existingTarget, deduplicated = true)
                }

                val logicalKey = logicalKey(draft, target)
                val generation = dao.latestGeneration(logicalKey) + 1L
                val operationId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val command = TrackingCommand(operationId, generation, draft)
                val runnable = target.blocker == null
                val operation = TrackingOperationEntity(
                    operationId = operationId,
                    logicalKey = logicalKey,
                    localMediaId = draft.localMediaId,
                    mediaType = draft.mediaType.name,
                    generation = generation,
                    deduplicationKey = deduplicationKey,
                    commandJson = codec.encode(command),
                    fieldMask = draft.fields.map { it.name }.sorted().joinToString(","),
                    isTombstone = draft.deleteIntent,
                    state = if (runnable) TrackingOperationState.PENDING.name
                    else TrackingOperationState.BLOCKED.name,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                )
                val targetEntity = TrackingOperationTargetEntity(
                    operationId = operationId,
                    provider = target.provider.name,
                    providerAccountId = target.providerAccountId,
                    providerMediaId = target.providerMediaId,
                    state = if (runnable) TrackingTargetState.PENDING.name
                    else TrackingTargetState.BLOCKED.name,
                    attemptCount = 0,
                    nextAttemptAtEpochMillis = now,
                    leaseToken = null,
                    leaseExpiresAtEpochMillis = null,
                    lastErrorKind = target.blocker?.name,
                    lastHttpStatus = null,
                    retryAfterMillis = null,
                    remoteRevision = null,
                    updatedAtEpochMillis = now,
                )

                dao.supersedeWaitingTargets(logicalKey, generation, now)
                dao.insertOperationWithTarget(operation, targetEntity)
                operation.toReceipt(targetEntity, deduplicated = false)
            } ?: return TrackingEnqueueResult.Rejected(TrackingFailureKind.STORAGE)
            if (!receipt.deduplicated && receipt.targetState == TrackingTargetState.PENDING) {
                scheduler.enqueue()
            }
            TrackingEnqueueResult.Accepted(receipt)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            TrackingEnqueueResult.Rejected(TrackingFailureKind.STORAGE)
        }
    }

    private fun logicalKey(draft: TrackingCommandDraft, target: TrackingCommandTarget): String =
        "${draft.mediaType.name}:${draft.localMediaId}:${target.provider.name}=" +
            (target.providerAccountId ?: "none")

    private fun TrackingOperationEntity.toReceipt(
        target: TrackingOperationTargetEntity,
        deduplicated: Boolean,
    ) = TrackingEnqueueReceipt(
        operationId = operationId,
        generation = generation,
        deduplicated = deduplicated,
        provider = TrackingProvider.valueOf(target.provider),
        targetState = TrackingTargetState.valueOf(target.state),
    )
}
