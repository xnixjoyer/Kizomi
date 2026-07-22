package com.anisync.android.data.tracking

import com.anisync.android.data.local.entity.ProviderTrackingSnapshotEntity
import com.anisync.android.data.local.entity.TrackingReconciliationItemEntity
import com.anisync.android.data.local.entity.TrackingReconciliationPlanEntity
import com.anisync.android.domain.tracking.TrackingDesiredState
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingField
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingProvider
import com.anisync.android.domain.tracking.TrackingStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

enum class ReconciliationAction {
    EQUAL,
    DIFFERENT,
    ONLY_SOURCE,
    ONLY_TARGET,
    UNMAPPED,
    BLOCKED_CONFLICT,
}

enum class ReconciliationItemState {
    READY,
    CLAIMED,
    ENQUEUED,
    SUCCEEDED,
    SKIPPED,
    SKIPPED_PRESENT,
    BLOCKED,
    FAILED,
}

enum class ReconciliationPlanState {
    PREVIEW,
    RUNNING,
    PAUSED,
    SUCCEEDED,
    PARTIAL_FAILURE,
    FAILED,
}

data class ReconciliationDirection(
    val source: TrackingProvider,
    val target: TrackingProvider,
) {
    init {
        require(source != target)
    }
}

data class ReconciliationCounts(
    val equal: Int = 0,
    val different: Int = 0,
    val onlySource: Int = 0,
    val onlyTarget: Int = 0,
    val unmapped: Int = 0,
    val blocked: Int = 0,
    val ready: Int = 0,
    val enqueued: Int = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
)

data class ReconciliationItemView(
    val itemKey: String,
    val action: ReconciliationAction,
    val state: ReconciliationItemState,
    val lastError: TrackingFailureKind?,
)

data class ReconciliationPlanView(
    val planId: String,
    val mediaType: TrackingMediaType,
    val direction: ReconciliationDirection,
    val state: ReconciliationPlanState,
    val counts: ReconciliationCounts,
    val items: List<ReconciliationItemView>,
) {
    override fun toString(): String =
        "ReconciliationPlanView(planId=<redacted>, mediaType=${mediaType.name}, " +
            "direction=${direction.source.name}->${direction.target.name}, state=${state.name}, " +
            "counts=$counts, itemCount=${items.size})"
}

sealed interface ReconciliationCreateResult {
    data class Created(val planId: String) : ReconciliationCreateResult
    data class Rejected(val reason: TrackingFailureKind) : ReconciliationCreateResult
}

@Serializable
internal data class ReconciliationSnapshotPayload(
    val localMediaId: String,
    val provider: TrackingProvider,
    val providerAccountId: String,
    val providerMediaId: Long,
    val mediaType: TrackingMediaType,
    val state: TrackingDesiredState,
)

@Serializable
internal data class MissingOnlyCommandEnvelope(
    val localMediaId: String,
    val mediaType: TrackingMediaType,
    val targetProvider: TrackingProvider,
    val targetAccountId: String,
    val targetMediaId: Long,
    val desired: TrackingDesiredState,
    val fields: Set<TrackingField>,
) {
    override fun toString(): String =
        "MissingOnlyCommandEnvelope(localMediaId=<redacted>, mediaType=${mediaType.name}, " +
            "targetProvider=${targetProvider.name}, targetAccountId=<redacted>, " +
            "targetMediaId=<redacted>, desired=$desired, fields=$fields)"
}

internal val reconciliationJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = false
    explicitNulls = true
}

internal fun ProviderTrackingSnapshotEntity.toReconciliationPayload(): ReconciliationSnapshotPayload? {
    val parsedMediaType = runCatching { TrackingMediaType.valueOf(mediaType) }.getOrNull()
        ?: return null
    val parsedStatus = runCatching { TrackingStatus.valueOf(status) }.getOrNull()
        ?: return null
    val desired = runCatching {
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
    }.getOrNull() ?: return null
    return ReconciliationSnapshotPayload(
        localMediaId = localMediaId,
        provider = TrackingProvider.valueOf(provider),
        providerAccountId = providerAccountId,
        providerMediaId = providerMediaId,
        mediaType = parsedMediaType,
        state = desired,
    )
}

internal fun TrackingDesiredState.creationFields(): Set<TrackingField> = buildSet {
    add(TrackingField.STATUS)
    add(TrackingField.PROGRESS)
    add(TrackingField.SCORE)
    add(TrackingField.REPEAT_COUNT)
    add(TrackingField.STARTED_AT)
    add(TrackingField.COMPLETED_AT)
    if (progressSecondary != null) add(TrackingField.PROGRESS_SECONDARY)
    if (notes != null) add(TrackingField.NOTES)
    if (customLists.isNotEmpty()) add(TrackingField.CUSTOM_LISTS)
    if (isPrivate) add(TrackingField.PRIVATE)
    if (hiddenFromStatusLists) add(TrackingField.HIDDEN_FROM_STATUS_LISTS)
}

internal fun MissingOnlyCommandEnvelope.toExactInput() = ExactTrackingCommandInput(
    localMediaId = localMediaId,
    mediaType = mediaType,
    desired = desired,
    fields = fields,
    provider = targetProvider,
    providerAccountId = targetAccountId,
    providerMediaId = targetMediaId,
)

internal fun reconciliationMode(direction: ReconciliationDirection): String =
    "MISSING_ONLY:${direction.source.name}:${direction.target.name}"

internal fun parseReconciliationDirection(mode: String): ReconciliationDirection? {
    val parts = mode.split(':')
    if (parts.size != 3 || parts[0] != "MISSING_ONLY") return null
    return runCatching {
        ReconciliationDirection(
            source = TrackingProvider.valueOf(parts[1]),
            target = TrackingProvider.valueOf(parts[2]),
        )
    }.getOrNull()
}

internal fun TrackingReconciliationPlanEntity.toView(
    items: List<TrackingReconciliationItemEntity>,
): ReconciliationPlanView? {
    val direction = parseReconciliationDirection(mode) ?: return null
    val itemViews = items.mapNotNull { item ->
        val action = runCatching { ReconciliationAction.valueOf(item.action) }.getOrNull()
            ?: return@mapNotNull null
        val itemState = runCatching { ReconciliationItemState.valueOf(item.state) }.getOrNull()
            ?: return@mapNotNull null
        ReconciliationItemView(
            itemKey = item.itemKey,
            action = action,
            state = itemState,
            lastError = item.lastErrorKind?.let { value ->
                runCatching { TrackingFailureKind.valueOf(value) }.getOrNull()
            },
        )
    }
    return ReconciliationPlanView(
        planId = planId,
        mediaType = TrackingMediaType.valueOf(mediaType),
        direction = direction,
        state = ReconciliationPlanState.valueOf(state),
        counts = itemViews.toReconciliationCounts(),
        items = itemViews,
    )
}

private fun List<ReconciliationItemView>.toReconciliationCounts() = ReconciliationCounts(
    equal = count { it.action == ReconciliationAction.EQUAL },
    different = count { it.action == ReconciliationAction.DIFFERENT },
    onlySource = count { it.action == ReconciliationAction.ONLY_SOURCE },
    onlyTarget = count { it.action == ReconciliationAction.ONLY_TARGET },
    unmapped = count { it.action == ReconciliationAction.UNMAPPED },
    blocked = count {
        it.action == ReconciliationAction.BLOCKED_CONFLICT ||
            (it.state == ReconciliationItemState.BLOCKED &&
                it.action != ReconciliationAction.UNMAPPED)
    },
    ready = count {
        it.state == ReconciliationItemState.READY ||
            it.state == ReconciliationItemState.CLAIMED
    },
    enqueued = count { it.state == ReconciliationItemState.ENQUEUED },
    succeeded = count { it.state == ReconciliationItemState.SUCCEEDED },
    failed = count { it.state == ReconciliationItemState.FAILED },
)

internal fun reconciliationSha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString(separator = "") { byte -> "%02x".format(byte) }

internal val trustedIdentityStates = setOf("EXACT", "CONFIRMED", "IMPORTED")
