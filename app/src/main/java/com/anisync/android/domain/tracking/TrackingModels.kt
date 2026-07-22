package com.anisync.android.domain.tracking

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
enum class TrackingMode {
    ANILIST_ONLY,
    MYANIMELIST_ONLY,
    DUAL,
}

@Serializable
enum class TrackingProvider {
    ANILIST,
    MYANIMELIST,
}

@Serializable
enum class TrackingMediaType {
    ANIME,
    MANGA,
}

@Serializable
enum class TrackingStatus {
    CURRENT,
    PLANNING,
    COMPLETED,
    DROPPED,
    PAUSED,
    REPEATING,
}

@Serializable
enum class TrackingField {
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

enum class TrackingOperationState {
    PENDING,
    RUNNING,
    PARTIAL,
    PARTIAL_FAILURE,
    SUCCEEDED,
    BLOCKED,
    FAILED,
    SUPERSEDED,
}

enum class TrackingTargetState {
    PENDING,
    RUNNING,
    RETRYING,
    SUCCEEDED,
    BLOCKED,
    FAILED,
    SUPERSEDED,
}

@Serializable
enum class TrackingFailureKind {
    STORAGE,
    MISSING_ACCOUNT,
    MISSING_IDENTITY,
    NETWORK_BLOCKED,
    NOT_AUTHENTICATED,
    UNAUTHORIZED,
    RATE_LIMITED,
    OFFLINE,
    TIMEOUT,
    TRANSPORT,
    TRANSIENT_SERVER,
    INVALID_RESPONSE,
    UNSUPPORTED_FIELD,
    VALIDATION,
    PROVIDER_NOT_CONFIGURED,
    RETRY_BUDGET_EXHAUSTED,
    LEASE_EXPIRED,
    PERMANENT,
}

/** Provider-neutral, absolute desired list state. Null means absent, never "leave unchanged". */
@Serializable
data class TrackingDesiredState(
    val status: TrackingStatus?,
    val progress: Int,
    val progressSecondary: Int? = null,
    /** Canonical 0–100 projection. Provider-native raw values remain in provider snapshots. */
    val score100: Double? = null,
    val repeatCount: Int = 0,
    val notes: String? = null,
    /** ISO-8601 yyyy-MM-dd. */
    val startedAt: String? = null,
    /** ISO-8601 yyyy-MM-dd. */
    val completedAt: String? = null,
) {
    init {
        require(progress >= 0) { "progress must be non-negative" }
        require(progressSecondary == null || progressSecondary >= 0) {
            "secondary progress must be non-negative"
        }
        require(score100 == null || score100 in 0.0..100.0) { "score100 must be in 0..100" }
        require(repeatCount >= 0) { "repeat count must be non-negative" }
        require(startedAt == null || ISO_DATE.matches(startedAt)) { "startedAt must be yyyy-MM-dd" }
        require(completedAt == null || ISO_DATE.matches(completedAt)) {
            "completedAt must be yyyy-MM-dd"
        }
    }

    override fun toString(): String =
        "TrackingDesiredState(status=${status?.name ?: "none"}, progress=$progress, " +
            "progressSecondary=${progressSecondary ?: "none"}, score100=${score100 ?: "none"}, " +
            "repeatCount=$repeatCount, notes=<redacted>, startedAt=${startedAt ?: "none"}, " +
            "completedAt=${completedAt ?: "none"})"

    companion object {
        private val ISO_DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
    }
}

/** Intent accepted by the one mutation entry point before an operation id is allocated. */
@Serializable
data class TrackingCommandDraft(
    val localMediaId: String,
    val mediaType: TrackingMediaType,
    val desired: TrackingDesiredState,
    val fields: Set<TrackingField>,
    val deleteIntent: Boolean = false,
) {
    init {
        require(localMediaId.isNotBlank()) { "localMediaId must not be blank" }
        require(fields.isNotEmpty()) { "field mask must not be empty" }
        require(deleteIntent == (TrackingField.DELETE in fields)) {
            "delete intent and DELETE field must agree"
        }
        require(deleteIntent || desired.status != null) { "non-delete state requires a status" }
    }
}

/** Immutable payload stored before any remote call. */
@Serializable
data class TrackingCommand(
    val operationId: String,
    val generation: Long,
    val draft: TrackingCommandDraft,
) {
    init {
        require(operationId.isNotBlank()) { "operationId must not be blank" }
        require(generation > 0) { "generation must be positive" }
    }
}

/** One configured saga target; blockers are persisted rather than silently dropping the target. */
@Serializable
data class TrackingCommandTarget(
    val provider: TrackingProvider,
    val providerAccountId: String?,
    val providerMediaId: Long?,
    val blocker: TrackingFailureKind? = null,
) {
    init {
        require(providerAccountId == null || providerAccountId.isNotBlank())
        require(providerMediaId == null || providerMediaId > 0L)
    }
}

data class TrackingEnqueueReceipt(
    val operationId: String,
    val generation: Long,
    val deduplicated: Boolean,
    val targetStates: Map<TrackingProvider, TrackingTargetState>,
)

sealed interface TrackingEnqueueResult {
    data class Accepted(val receipt: TrackingEnqueueReceipt) : TrackingEnqueueResult
    data class Rejected(val reason: TrackingFailureKind) : TrackingEnqueueResult
}

data class TrackingProviderRequest(
    val command: TrackingCommand,
    val provider: TrackingProvider,
    val providerAccountId: String,
    val providerMediaId: Long,
    val deliveryAttempt: Int,
)

data class TrackingConfirmedSnapshot(
    val providerListEntryId: Long? = null,
    val title: String = "",
    val coverUrl: String? = null,
    val state: TrackingDesiredState,
    val providerUpdatedAtEpochMillis: Long? = null,
    val rawProviderFieldsJson: String = "{}",
    val remoteRevision: String? = null,
    val deleted: Boolean = false,
)

sealed interface TrackingDeliveryResult {
    data class Success(val snapshot: TrackingConfirmedSnapshot) : TrackingDeliveryResult

    data class RetryableFailure(
        val kind: TrackingFailureKind,
        val httpStatus: Int? = null,
        val retryAfterMillis: Long? = null,
    ) : TrackingDeliveryResult

    data class TerminalFailure(
        val kind: TrackingFailureKind,
        val httpStatus: Int? = null,
    ) : TrackingDeliveryResult
}

interface TrackingProviderAdapter {
    val provider: TrackingProvider
    suspend fun apply(request: TrackingProviderRequest): TrackingDeliveryResult
}

fun TrackingStatus.toMalStatus(mediaType: TrackingMediaType): String = when (mediaType) {
    TrackingMediaType.ANIME -> when (this) {
        TrackingStatus.CURRENT, TrackingStatus.REPEATING -> "watching"
        TrackingStatus.PLANNING -> "plan_to_watch"
        TrackingStatus.COMPLETED -> "completed"
        TrackingStatus.DROPPED -> "dropped"
        TrackingStatus.PAUSED -> "on_hold"
    }
    TrackingMediaType.MANGA -> when (this) {
        TrackingStatus.CURRENT, TrackingStatus.REPEATING -> "reading"
        TrackingStatus.PLANNING -> "plan_to_read"
        TrackingStatus.COMPLETED -> "completed"
        TrackingStatus.DROPPED -> "dropped"
        TrackingStatus.PAUSED -> "on_hold"
    }
}

/** MAL deliberately stores only integer 0–10 scores; this conversion is visible and tested. */
fun Double.toMalIntegerScore(): Int = div(10.0).roundToInt().coerceIn(0, 10)
