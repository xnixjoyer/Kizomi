package com.anisync.android.data.tracking

import com.anisync.android.data.mal.oauth.AuthenticatedMalClient
import com.anisync.android.data.mal.oauth.MalAuthenticatedFailureReason
import com.anisync.android.data.mal.oauth.MalAuthenticatedResponse
import com.anisync.android.data.mal.oauth.MalAuthenticatedResult
import com.anisync.android.data.mal.oauth.MalOAuthCapability
import com.anisync.android.data.mal.oauth.MalOAuthConfigurationSource
import com.anisync.android.data.mal.oauth.MalRefreshFailureReason
import com.anisync.android.domain.tracking.TrackingConfirmedSnapshot
import com.anisync.android.domain.tracking.TrackingDeliveryResult
import com.anisync.android.domain.tracking.TrackingDesiredState
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingField
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingProvider
import com.anisync.android.domain.tracking.TrackingProviderAdapter
import com.anisync.android.domain.tracking.TrackingProviderRequest
import com.anisync.android.domain.tracking.TrackingStatus
import com.anisync.android.domain.tracking.toMalIntegerScore
import com.anisync.android.domain.tracking.toMalStatus
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val MAL_API_BASE_URL = "https://api.myanimelist.net/v2/"

data class MalTrackingCapability(
    val mediaType: TrackingMediaType,
    val supportedFields: Set<TrackingField>,
) {
    fun unsupported(requested: Set<TrackingField>): Set<TrackingField> =
        requested - supportedFields
}

/** Explicit fail-closed field matrix for MAL v2 list writes. */
object MalTrackingCapabilities {
    private val common = setOf(
        TrackingField.STATUS,
        TrackingField.PROGRESS,
        TrackingField.SCORE,
        TrackingField.REPEAT_COUNT,
        TrackingField.STARTED_AT,
        TrackingField.COMPLETED_AT,
        TrackingField.DELETE,
    )

    fun forMediaType(mediaType: TrackingMediaType): MalTrackingCapability =
        MalTrackingCapability(
            mediaType = mediaType,
            supportedFields = if (mediaType == TrackingMediaType.MANGA) {
                common + TrackingField.PROGRESS_SECONDARY
            } else {
                common
            },
        )
}

internal class MalTrackingRequestFactory(
    private val baseUrl: HttpUrl = MAL_API_BASE_URL.toHttpUrl(),
) {
    fun write(
        request: TrackingProviderRequest,
        clientId: String,
    ): Request {
        val draft = request.command.draft
        val path = statusPath(draft.mediaType, request.providerMediaId)
        val builder = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegments(path).build())
            .header("Accept", "application/json")
            .header("X-MAL-CLIENT-ID", clientId)
        if (draft.deleteIntent) return builder.delete().build()

        val desired = draft.desired
        val fields = draft.fields
        val body = FormBody.Builder().apply {
            if (TrackingField.STATUS in fields) {
                add("status", requireNotNull(desired.status).toMalStatus(draft.mediaType))
            }
            if (TrackingField.PROGRESS in fields) {
                add(
                    if (draft.mediaType == TrackingMediaType.ANIME) {
                        "num_watched_episodes"
                    } else {
                        "num_chapters_read"
                    },
                    desired.progress.toString(),
                )
            }
            if (TrackingField.PROGRESS_SECONDARY in fields) {
                add("num_volumes_read", (desired.progressSecondary ?: 0).toString())
            }
            if (TrackingField.STATUS in fields || TrackingField.REPEAT_COUNT in fields) {
                add(
                    if (draft.mediaType == TrackingMediaType.ANIME) {
                        "is_rewatching"
                    } else {
                        "is_rereading"
                    },
                    (desired.status == TrackingStatus.REPEATING).toString(),
                )
            }
            if (TrackingField.REPEAT_COUNT in fields) {
                add(
                    if (draft.mediaType == TrackingMediaType.ANIME) {
                        "num_times_rewatched"
                    } else {
                        "num_times_reread"
                    },
                    desired.repeatCount.toString(),
                )
            }
            if (TrackingField.SCORE in fields) {
                add("score", (desired.score100?.toMalIntegerScore() ?: 0).toString())
            }
            if (TrackingField.STARTED_AT in fields) {
                add("start_date", desired.startedAt.orEmpty())
            }
            if (TrackingField.COMPLETED_AT in fields) {
                add("finish_date", desired.completedAt.orEmpty())
            }
        }.build()
        return builder.patch(body).build()
    }

    fun readBack(
        mediaType: TrackingMediaType,
        providerMediaId: Long,
        clientId: String,
    ): Request = Request.Builder()
        .url(
            baseUrl.newBuilder()
                .addPathSegments(mediaPath(mediaType, providerMediaId))
                .addQueryParameter("fields", "id,title,main_picture,my_list_status")
                .build()
        )
        .header("Accept", "application/json")
        .header("X-MAL-CLIENT-ID", clientId)
        .get()
        .build()

    private fun statusPath(mediaType: TrackingMediaType, id: Long): String =
        "${mediaPath(mediaType, id)}/my_list_status"

    private fun mediaPath(mediaType: TrackingMediaType, id: Long): String =
        "${if (mediaType == TrackingMediaType.ANIME) "anime" else "manga"}/$id"
}

@Singleton
class MalTrackingProviderAdapter internal constructor(
    private val executeAuthenticated: suspend (
        localAccountId: String,
        requestFactory: () -> Request,
    ) -> MalAuthenticatedResult,
    private val clientId: () -> String?,
    private val requests: MalTrackingRequestFactory,
) : TrackingProviderAdapter {
    @Inject
    constructor(
        client: AuthenticatedMalClient,
        configuration: MalOAuthConfigurationSource,
    ) : this(
        executeAuthenticated = client::execute,
        clientId = {
            (configuration.capability as? MalOAuthCapability.Configured)
                ?.configuration
                ?.clientId
        },
        requests = MalTrackingRequestFactory(),
    )

    override val provider: TrackingProvider = TrackingProvider.MYANIMELIST

    override suspend fun apply(request: TrackingProviderRequest): TrackingDeliveryResult {
        if (request.provider != provider) {
            return TrackingDeliveryResult.TerminalFailure(TrackingFailureKind.VALIDATION)
        }
        val publicClientId = clientId()?.trim()?.takeIf(String::isNotEmpty)
            ?: return TrackingDeliveryResult.TerminalFailure(
                TrackingFailureKind.PROVIDER_NOT_CONFIGURED
            )
        val unsupported = MalTrackingCapabilities.forMediaType(request.command.draft.mediaType)
            .unsupported(request.command.draft.fields)
        if (unsupported.isNotEmpty()) {
            return TrackingDeliveryResult.TerminalFailure(TrackingFailureKind.UNSUPPORTED_FIELD)
        }
        return try {
            val write = executeAuthenticated(request.providerAccountId) {
                requests.write(request, publicClientId)
            }
            when (write) {
                is MalAuthenticatedResult.Failure -> write.toTrackingFailure()
                is MalAuthenticatedResult.Success -> {
                    write.response.httpFailureOrNull()?.let { return it }
                    reconcile(request, publicClientId)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            TrackingDeliveryResult.RetryableFailure(TrackingFailureKind.TRANSPORT)
        }
    }

    private suspend fun reconcile(
        request: TrackingProviderRequest,
        publicClientId: String,
    ): TrackingDeliveryResult {
        val response = when (
            val readBack = executeAuthenticated(request.providerAccountId) {
                requests.readBack(
                    request.command.draft.mediaType,
                    request.providerMediaId,
                    publicClientId,
                )
            }
        ) {
            is MalAuthenticatedResult.Failure -> return readBack.toTrackingFailure()
            is MalAuthenticatedResult.Success -> readBack.response
        }
        response.httpFailureOrNull()?.let { return it }
        val wire = runCatching { json.decodeFromString<WireMedia>(response.body) }.getOrNull()
            ?: return TrackingDeliveryResult.TerminalFailure(
                TrackingFailureKind.INVALID_RESPONSE,
                response.statusCode,
            )
        if (wire.id != request.providerMediaId) {
            return TrackingDeliveryResult.TerminalFailure(TrackingFailureKind.INVALID_RESPONSE)
        }
        if (request.command.draft.deleteIntent) {
            if (wire.listStatus != null) {
                return TrackingDeliveryResult.TerminalFailure(TrackingFailureKind.INVALID_RESPONSE)
            }
            return TrackingDeliveryResult.Success(
                TrackingConfirmedSnapshot(
                    title = wire.title,
                    coverUrl = wire.mainPicture?.large ?: wire.mainPicture?.medium,
                    state = request.command.draft.desired,
                    rawProviderFieldsJson = "{}",
                    deleted = true,
                )
            )
        }
        val state = wire.listStatus?.toDesiredState(request.command.draft.mediaType)
            ?: return TrackingDeliveryResult.TerminalFailure(TrackingFailureKind.INVALID_RESPONSE)
        return TrackingDeliveryResult.Success(
            TrackingConfirmedSnapshot(
                title = wire.title,
                coverUrl = wire.mainPicture?.large ?: wire.mainPicture?.medium,
                state = state,
                providerUpdatedAtEpochMillis = wire.listStatus.updatedAt?.let { value ->
                    runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
                },
                rawProviderFieldsJson = json.encodeToString(wire.listStatus),
            )
        )
    }

    @Serializable
    private data class WireMedia(
        val id: Long,
        val title: String = "",
        @SerialName("main_picture") val mainPicture: WirePicture? = null,
        @SerialName("my_list_status") val listStatus: WireListStatus? = null,
    )

    @Serializable
    private data class WirePicture(val medium: String? = null, val large: String? = null)

    @Serializable
    private data class WireListStatus(
        val status: String,
        val score: Int? = null,
        @SerialName("num_episodes_watched") val episodes: Int? = null,
        @SerialName("num_chapters_read") val chapters: Int? = null,
        @SerialName("num_volumes_read") val volumes: Int? = null,
        @SerialName("is_rewatching") val rewatching: Boolean? = null,
        @SerialName("is_rereading") val rereading: Boolean? = null,
        @SerialName("num_times_rewatched") val rewatchCount: Int? = null,
        @SerialName("num_times_reread") val rereadCount: Int? = null,
        val comments: String? = null,
        @SerialName("start_date") val startDate: String? = null,
        @SerialName("finish_date") val finishDate: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null,
    ) {
        fun toDesiredState(mediaType: TrackingMediaType): TrackingDesiredState? = runCatching {
            val repeating = if (mediaType == TrackingMediaType.ANIME) rewatching else rereading
            TrackingDesiredState(
                status = if (repeating == true) {
                    TrackingStatus.REPEATING
                } else {
                    status.toTrackingStatus(mediaType)
                },
                progress = if (mediaType == TrackingMediaType.ANIME) episodes ?: 0 else chapters ?: 0,
                progressSecondary = volumes.takeIf { mediaType == TrackingMediaType.MANGA },
                score100 = score?.also { require(it in 0..10) }?.takeIf { it > 0 }?.times(10.0),
                repeatCount = if (mediaType == TrackingMediaType.ANIME) {
                    rewatchCount ?: 0
                } else {
                    rereadCount ?: 0
                },
                notes = comments,
                startedAt = startDate,
                completedAt = finishDate,
            )
        }.getOrNull()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}

private fun String.toTrackingStatus(mediaType: TrackingMediaType): TrackingStatus = when (this) {
    "watching" -> TrackingStatus.CURRENT.also { require(mediaType == TrackingMediaType.ANIME) }
    "reading" -> TrackingStatus.CURRENT.also { require(mediaType == TrackingMediaType.MANGA) }
    "plan_to_watch" -> TrackingStatus.PLANNING.also {
        require(mediaType == TrackingMediaType.ANIME)
    }
    "plan_to_read" -> TrackingStatus.PLANNING.also {
        require(mediaType == TrackingMediaType.MANGA)
    }
    "completed" -> TrackingStatus.COMPLETED
    "dropped" -> TrackingStatus.DROPPED
    "on_hold" -> TrackingStatus.PAUSED
    else -> error("Unsupported MAL tracking status")
}

private fun MalAuthenticatedResponse.httpFailureOrNull(): TrackingDeliveryResult? = when {
    statusCode in 200..299 -> null
    statusCode == 401 -> TrackingDeliveryResult.TerminalFailure(
        TrackingFailureKind.UNAUTHORIZED,
        statusCode,
    )
    statusCode == 429 -> TrackingDeliveryResult.RetryableFailure(
        TrackingFailureKind.RATE_LIMITED,
        statusCode,
        headers["Retry-After"]?.trim()?.toLongOrNull()?.coerceAtLeast(0L)?.times(1_000L),
    )
    statusCode in 500..599 -> TrackingDeliveryResult.RetryableFailure(
        TrackingFailureKind.TRANSIENT_SERVER,
        statusCode,
    )
    statusCode == 404 -> TrackingDeliveryResult.TerminalFailure(
        TrackingFailureKind.MISSING_IDENTITY,
        statusCode,
    )
    statusCode == 400 || statusCode == 409 || statusCode == 422 ->
        TrackingDeliveryResult.TerminalFailure(TrackingFailureKind.VALIDATION, statusCode)
    else -> TrackingDeliveryResult.TerminalFailure(TrackingFailureKind.PERMANENT, statusCode)
}

private fun MalAuthenticatedResult.Failure.toTrackingFailure(): TrackingDeliveryResult =
    when (refreshFailure) {
        MalRefreshFailureReason.RATE_LIMITED -> TrackingDeliveryResult.RetryableFailure(
            TrackingFailureKind.RATE_LIMITED
        )
        MalRefreshFailureReason.SERVER_ERROR -> TrackingDeliveryResult.RetryableFailure(
            TrackingFailureKind.TRANSIENT_SERVER
        )
        MalRefreshFailureReason.TIMEOUT -> TrackingDeliveryResult.RetryableFailure(
            TrackingFailureKind.TIMEOUT
        )
        MalRefreshFailureReason.TRANSPORT,
        MalRefreshFailureReason.CANCELLED -> TrackingDeliveryResult.RetryableFailure(
            TrackingFailureKind.TRANSPORT
        )
        MalRefreshFailureReason.REFRESH_TOKEN_MISSING,
        MalRefreshFailureReason.TOKEN_MISSING,
        MalRefreshFailureReason.RELOGIN_REQUIRED,
        MalRefreshFailureReason.INVALID_CLIENT,
        MalRefreshFailureReason.CONFIGURATION_UNAVAILABLE -> TrackingDeliveryResult.TerminalFailure(
            TrackingFailureKind.NOT_AUTHENTICATED
        )
        MalRefreshFailureReason.ACCOUNT_NOT_FOUND,
        MalRefreshFailureReason.ACCOUNT_SESSION_CHANGED -> TrackingDeliveryResult.TerminalFailure(
            TrackingFailureKind.MISSING_ACCOUNT
        )
        MalRefreshFailureReason.MALFORMED_RESPONSE -> TrackingDeliveryResult.TerminalFailure(
            TrackingFailureKind.INVALID_RESPONSE
        )
        MalRefreshFailureReason.PERSISTENCE_FAILED,
        MalRefreshFailureReason.PERMANENT_HTTP_ERROR -> TrackingDeliveryResult.TerminalFailure(
            TrackingFailureKind.PERMANENT
        )
        null -> when (reason) {
            MalAuthenticatedFailureReason.ACCOUNT_NOT_FOUND ->
                TrackingDeliveryResult.TerminalFailure(TrackingFailureKind.MISSING_ACCOUNT)
            MalAuthenticatedFailureReason.TOKEN_UNAVAILABLE,
            MalAuthenticatedFailureReason.RELOGIN_REQUIRED ->
                TrackingDeliveryResult.TerminalFailure(TrackingFailureKind.NOT_AUTHENTICATED)
            MalAuthenticatedFailureReason.REFRESH_FAILED,
            MalAuthenticatedFailureReason.TRANSPORT,
            MalAuthenticatedFailureReason.CANCELLED ->
                TrackingDeliveryResult.RetryableFailure(TrackingFailureKind.TRANSPORT)
            MalAuthenticatedFailureReason.OFFLINE ->
                TrackingDeliveryResult.RetryableFailure(TrackingFailureKind.OFFLINE)
            MalAuthenticatedFailureReason.TIMEOUT ->
                TrackingDeliveryResult.RetryableFailure(TrackingFailureKind.TIMEOUT)
        }
    }
