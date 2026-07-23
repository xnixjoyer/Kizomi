package com.anisync.android.data.tracking

import com.anisync.android.data.local.dao.TrackingDao
import com.anisync.android.data.local.entity.ProviderTrackingSnapshotEntity
import com.anisync.android.data.local.entity.TrackingOperationTargetEntity
import com.anisync.android.domain.tracking.TrackingCommand
import com.anisync.android.domain.tracking.TrackingDeliveryResult
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingOperationState
import com.anisync.android.domain.tracking.TrackingProvider
import com.anisync.android.domain.tracking.TrackingProviderAdapter
import com.anisync.android.domain.tracking.TrackingProviderRequest
import com.anisync.android.domain.tracking.TrackingTargetState
import kotlinx.coroutines.CancellationException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

data class TrackingDrainResult(
    val attemptedDeliveries: Int,
    val hasUnsettledDeliveries: Boolean,
)

@Singleton
class TrackingOutboxExecutor internal constructor(
    private val dao: TrackingDao,
    private val codec: TrackingCommandCodec,
    adapters: Set<@JvmSuppressWildcards TrackingProviderAdapter>,
    private val writeGate: suspend (TrackingProvider, String) -> TrackingFailureKind?,
) {
    @Inject
    constructor(
        dao: TrackingDao,
        codec: TrackingCommandCodec,
        adapters: Set<@JvmSuppressWildcards TrackingProviderAdapter>,
        writeGate: TrackingWriteGate,
    ) : this(
        dao = dao,
        codec = codec,
        adapters = adapters,
        writeGate = writeGate::blocker,
    )

    /** Test-only compatibility constructor; production always receives [TrackingWriteGate]. */
    internal constructor(
        dao: TrackingDao,
        codec: TrackingCommandCodec,
        adapters: Set<@JvmSuppressWildcards TrackingProviderAdapter>,
    ) : this(
        dao = dao,
        codec = codec,
        adapters = adapters,
        writeGate = { _, _ -> null },
    )

    private val adaptersByProvider = adapters.associateBy(TrackingProviderAdapter::provider)

    suspend fun drain(maxDeliveries: Int = DEFAULT_BATCH_SIZE): TrackingDrainResult {
        require(maxDeliveries > 0)
        dao.recoverExpiredLeases(System.currentTimeMillis())
        var attempts = 0
        while (attempts < maxDeliveries) {
            val now = System.currentTimeMillis()
            val target = dao.findDeliverableTargets(now, limit = 1).firstOrNull() ?: break
            deliver(target, now)
            attempts++
        }
        return TrackingDrainResult(
            attemptedDeliveries = attempts,
            hasUnsettledDeliveries = dao.countUnsettledDeliveries() > 0,
        )
    }

    private suspend fun deliver(target: TrackingOperationTargetEntity, now: Long) {
        val leaseToken = UUID.randomUUID().toString()
        val claimed = dao.claimTarget(
            operationId = target.operationId,
            provider = target.provider,
            leaseToken = leaseToken,
            leaseExpiresAtEpochMillis = now + LEASE_DURATION_MILLIS,
            nowEpochMillis = now,
        )
        if (claimed != 1) return

        val operation = dao.getOperation(target.operationId)
        val command = try {
            operation?.let { codec.decode(it.commandJson) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        val provider = runCatching { TrackingProvider.valueOf(target.provider) }.getOrNull()
        val attempt = target.attemptCount + 1
        val accountId = target.providerAccountId
        val result = when {
            operation == null || command == null || provider == null ->
                TrackingDeliveryResult.TerminalFailure(TrackingFailureKind.INVALID_RESPONSE)
            accountId == null ->
                TrackingDeliveryResult.TerminalFailure(TrackingFailureKind.MISSING_ACCOUNT)
            target.providerMediaId == null ->
                TrackingDeliveryResult.TerminalFailure(TrackingFailureKind.MISSING_IDENTITY)
            adaptersByProvider[provider] == null ->
                TrackingDeliveryResult.TerminalFailure(TrackingFailureKind.PROVIDER_NOT_CONFIGURED)
            else -> {
                val gateBlocker = writeGate(provider, accountId)
                if (gateBlocker != null) {
                    TrackingDeliveryResult.TerminalFailure(gateBlocker)
                } else {
                    applySafely(
                        adapter = requireNotNull(adaptersByProvider[provider]),
                        command = command,
                        provider = provider,
                        target = target,
                        attempt = attempt,
                    )
                }
            }
        }

        finish(target, leaseToken, command, result, attempt)
        refreshAggregateState(target.operationId)
    }

    private suspend fun applySafely(
        adapter: TrackingProviderAdapter,
        command: TrackingCommand,
        provider: TrackingProvider,
        target: TrackingOperationTargetEntity,
        attempt: Int,
    ): TrackingDeliveryResult = try {
        adapter.apply(
            TrackingProviderRequest(
                command = command,
                provider = provider,
                providerAccountId = requireNotNull(target.providerAccountId),
                providerMediaId = requireNotNull(target.providerMediaId),
                deliveryAttempt = attempt,
            )
        )
    } catch (cancelled: CancellationException) {
        // The lease deliberately remains RUNNING and is recovered after expiry. Cancelling a worker
        // must never make the same provider write run concurrently in another worker.
        throw cancelled
    } catch (_: Throwable) {
        TrackingDeliveryResult.RetryableFailure(TrackingFailureKind.TRANSPORT)
    }

    private suspend fun finish(
        target: TrackingOperationTargetEntity,
        leaseToken: String,
        command: TrackingCommand?,
        result: TrackingDeliveryResult,
        attempt: Int,
    ) {
        val now = System.currentTimeMillis()
        val terminalRetry = result is TrackingDeliveryResult.RetryableFailure &&
            attempt >= MAX_DELIVERY_ATTEMPTS
        val terminalKind = (result as? TrackingDeliveryResult.TerminalFailure)?.kind
        val state = when {
            result is TrackingDeliveryResult.Success -> TrackingTargetState.SUCCEEDED
            terminalKind in BLOCKING_FAILURES -> TrackingTargetState.BLOCKED
            terminalRetry -> TrackingTargetState.FAILED
            result is TrackingDeliveryResult.RetryableFailure -> TrackingTargetState.RETRYING
            else -> TrackingTargetState.FAILED
        }
        val retryAfter = (result as? TrackingDeliveryResult.RetryableFailure)?.retryAfterMillis
        val nextAttempt = if (state == TrackingTargetState.RETRYING) {
            now + retryDelayMillis(target.operationId, target.provider, attempt, retryAfter)
        } else {
            now
        }
        val failureKind = when {
            terminalRetry -> TrackingFailureKind.RETRY_BUDGET_EXHAUSTED
            result is TrackingDeliveryResult.RetryableFailure -> result.kind
            result is TrackingDeliveryResult.TerminalFailure -> result.kind
            else -> null
        }
        val httpStatus = when (result) {
            is TrackingDeliveryResult.RetryableFailure -> result.httpStatus
            is TrackingDeliveryResult.TerminalFailure -> result.httpStatus
            is TrackingDeliveryResult.Success -> null
        }
        val remoteRevision = (result as? TrackingDeliveryResult.Success)
            ?.snapshot
            ?.remoteRevision

        val updated = dao.finishClaimedTarget(
            operationId = target.operationId,
            provider = target.provider,
            leaseToken = leaseToken,
            state = state.name,
            nextAttemptAtEpochMillis = nextAttempt,
            lastErrorKind = failureKind?.name,
            lastHttpStatus = httpStatus,
            retryAfterMillis = retryAfter,
            remoteRevision = remoteRevision,
            nowEpochMillis = now,
        )
        if (updated == 1 && result is TrackingDeliveryResult.Success && command != null) {
            val snapshot = result.snapshot
            dao.upsertSnapshot(
                ProviderTrackingSnapshotEntity(
                    provider = target.provider,
                    providerAccountId = requireNotNull(target.providerAccountId),
                    localMediaId = command.draft.localMediaId,
                    providerMediaId = requireNotNull(target.providerMediaId),
                    providerListEntryId = snapshot.providerListEntryId,
                    mediaType = command.draft.mediaType.name,
                    title = snapshot.title,
                    coverUrl = snapshot.coverUrl,
                    status = snapshot.state.status?.name ?: "DELETED",
                    progress = snapshot.state.progress,
                    progressSecondary = snapshot.state.progressSecondary,
                    score = snapshot.state.score100,
                    repeatCount = snapshot.state.repeatCount,
                    notes = snapshot.state.notes,
                    startedAt = snapshot.state.startedAt,
                    completedAt = snapshot.state.completedAt,
                    providerUpdatedAtEpochMillis = snapshot.providerUpdatedAtEpochMillis,
                    fetchedAtEpochMillis = now,
                    isDeleted = snapshot.deleted,
                )
            )
        }
    }

    private suspend fun refreshAggregateState(operationId: String) {
        val target = dao.getTarget(operationId) ?: return
        val state = aggregateTrackingOperationState(TrackingTargetState.valueOf(target.state))
        dao.updateOperationState(operationId, state.name, System.currentTimeMillis())
    }

    private fun retryDelayMillis(
        operationId: String,
        provider: String,
        attempt: Int,
        retryAfterMillis: Long?,
    ): Long {
        val exponent = (attempt - 1).coerceIn(0, 12)
        val exponential = min(MAX_BACKOFF_MILLIS, BASE_BACKOFF_MILLIS * (1L shl exponent))
        val floor = maxOf(exponential, retryAfterMillis ?: 0L)
        val unsignedHash = Integer.toUnsignedLong("$operationId:$provider:$attempt".hashCode())
        val jitterRange = maxOf(1L, floor / 5L)
        return min(MAX_BACKOFF_MILLIS, floor + unsignedHash % jitterRange)
    }

    companion object {
        const val MAX_DELIVERY_ATTEMPTS = 8
        private const val DEFAULT_BATCH_SIZE = 20
        private const val LEASE_DURATION_MILLIS = 10 * 60_000L
        private const val BASE_BACKOFF_MILLIS = 15_000L
        private const val MAX_BACKOFF_MILLIS = 6 * 60 * 60_000L
        private val BLOCKING_FAILURES = setOf(
            TrackingFailureKind.NETWORK_BLOCKED,
            TrackingFailureKind.MISSING_ACCOUNT,
            TrackingFailureKind.MISSING_IDENTITY,
            TrackingFailureKind.PROVIDER_NOT_CONFIGURED,
        )
    }
}

internal fun aggregateTrackingOperationState(
    state: TrackingTargetState,
): TrackingOperationState = when (state) {
    TrackingTargetState.PENDING,
    TrackingTargetState.RETRYING -> TrackingOperationState.PENDING
    TrackingTargetState.RUNNING -> TrackingOperationState.RUNNING
    TrackingTargetState.SUCCEEDED -> TrackingOperationState.SUCCEEDED
    TrackingTargetState.BLOCKED -> TrackingOperationState.BLOCKED
    TrackingTargetState.FAILED -> TrackingOperationState.FAILED
    TrackingTargetState.SUPERSEDED -> TrackingOperationState.SUPERSEDED
}
