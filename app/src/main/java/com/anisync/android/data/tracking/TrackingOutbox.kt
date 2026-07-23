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
        targets: List<TrackingCommandTarget>,
    ): String {
        val normalizedDraft = draft.copy(
            fields = draft.fields.sortedBy { it.name }.toCollection(linkedSetOf()),
        )
        val envelope = TrackingDedupeEnvelope(
            draft = normalizedDraft,
            targets = targets.sortedBy { it.provider.name },
        )
        return MessageDigest.getInstance("SHA-256")
            .digest(json.encodeToString(envelope).toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

@Serializable
private data class TrackingDedupeEnvelope(
    val draft: TrackingCommandDraft,
    val targets: List<TrackingCommandTarget>,
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
        targets: List<TrackingCommandTarget>,
    ): TrackingEnqueueResult {
        if (targets.isEmpty() || targets.map { it.provider }.distinct().size != targets.size) {
            return TrackingEnqueueResult.Rejected(TrackingFailureKind.VALIDATION)
        }
        val normalizedTargets = targets.sortedBy { it.provider.name }
        val deduplicationKey = codec.deduplicationKey(draft, normalizedTargets)
        return try {
            val receipt = database.withTransaction {
                val existing = dao.findUnsettledByDeduplicationKey(deduplicationKey)
                if (existing != null) {
                    return@withTransaction existing.toReceipt(
                        targets = dao.getTargets(existing.operationId),
                        deduplicated = true,
                    )
                }

                val logicalKey = logicalKey(draft, normalizedTargets)
                val generation = dao.latestGeneration(logicalKey) + 1L
                val operationId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val command = TrackingCommand(operationId, generation, draft)
                val hasRunnableTarget = normalizedTargets.any { it.blocker == null }
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
                    state = if (hasRunnableTarget) {
                        TrackingOperationState.PENDING.name
                    } else {
                        TrackingOperationState.BLOCKED.name
                    },
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                )
                val targetEntities = normalizedTargets.map { target ->
                    TrackingOperationTargetEntity(
                        operationId = operationId,
                        provider = target.provider.name,
                        providerAccountId = target.providerAccountId,
                        providerMediaId = target.providerMediaId,
                        state = if (target.blocker == null) {
                            TrackingTargetState.PENDING.name
                        } else {
                            TrackingTargetState.BLOCKED.name
                        },
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
                }

                // A normal newer desired state supersedes only waiting work. RUNNING writes retain
                // their lease and complete; the new absolute state is delivered immediately after.
                dao.supersedeWaitingTargets(logicalKey, generation, now)
                dao.insertOperationWithTargets(operation, targetEntities)
                operation.toReceipt(targetEntities, deduplicated = false)
            }
            if (!receipt.deduplicated &&
                receipt.targetStates.values.any { it == TrackingTargetState.PENDING }
            ) {
                scheduler.enqueue()
            }
            TrackingEnqueueResult.Accepted(receipt)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            TrackingEnqueueResult.Rejected(TrackingFailureKind.STORAGE)
        }
    }

    private fun logicalKey(
        draft: TrackingCommandDraft,
        targets: List<TrackingCommandTarget>,
    ): String = buildString {
        append(draft.mediaType.name)
        append(':')
        append(draft.localMediaId)
        targets.forEach { target ->
            append(':')
            append(target.provider.name)
            append('=')
            append(target.providerAccountId ?: "none")
        }
    }

    private fun TrackingOperationEntity.toReceipt(
        targets: List<TrackingOperationTargetEntity>,
        deduplicated: Boolean,
    ) = TrackingEnqueueReceipt(
        operationId = operationId,
        generation = generation,
        deduplicated = deduplicated,
        targetStates = targets.associate { target ->
            TrackingProvider.valueOf(target.provider) to TrackingTargetState.valueOf(target.state)
        },
    )
}
