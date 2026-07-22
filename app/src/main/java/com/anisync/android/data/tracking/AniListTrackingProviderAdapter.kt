package com.anisync.android.data.tracking

import com.anisync.android.DeleteMediaListEntryMutation
import com.anisync.android.SaveMediaListEntryMutation
import com.anisync.android.data.account.AccountStore
import com.anisync.android.data.account.TokenedApolloClientFactory
import com.anisync.android.data.util.ApiError
import com.anisync.android.domain.tracking.TrackingConfirmedSnapshot
import com.anisync.android.domain.tracking.TrackingDeliveryResult
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingField
import com.anisync.android.domain.tracking.TrackingProvider
import com.anisync.android.domain.tracking.TrackingProviderAdapter
import com.anisync.android.domain.tracking.TrackingProviderRequest
import com.anisync.android.domain.tracking.TrackingStatus
import com.anisync.android.type.FuzzyDateInput
import com.anisync.android.type.MediaListStatus
import com.anisync.android.util.AniListTextEncoder.encodeForAniList
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.apollo.exception.ApolloNetworkException
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AniListTrackingProviderAdapter @Inject constructor(
    private val accountStore: AccountStore,
    private val clients: TokenedApolloClientFactory,
) : TrackingProviderAdapter {
    override val provider: TrackingProvider = TrackingProvider.ANILIST

    override suspend fun apply(request: TrackingProviderRequest): TrackingDeliveryResult {
        val accountId = request.providerAccountId.toIntOrNull()
            ?: return TrackingDeliveryResult.TerminalFailure(TrackingFailureKind.MISSING_ACCOUNT)
        val account = accountStore.accounts.value.firstOrNull { it.id == accountId }
            ?: return TrackingDeliveryResult.TerminalFailure(TrackingFailureKind.MISSING_ACCOUNT)
        if (account.isExpired || account.token.isBlank()) {
            return TrackingDeliveryResult.TerminalFailure(TrackingFailureKind.NOT_AUTHENTICATED)
        }
        val mediaId = request.providerMediaId.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
            ?: return TrackingDeliveryResult.TerminalFailure(TrackingFailureKind.VALIDATION)
        return try {
            val result = if (request.command.draft.deleteIntent) {
                delete(request, account.token)
            } else {
                save(request, mediaId, account.token)
            }
            if (result is TrackingDeliveryResult.TerminalFailure &&
                result.kind == TrackingFailureKind.UNAUTHORIZED
            ) {
                accountStore.markExpired(accountId)
            }
            result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val result = error.toDeliveryFailure()
            if (result is TrackingDeliveryResult.TerminalFailure &&
                result.kind == TrackingFailureKind.UNAUTHORIZED
            ) {
                accountStore.markExpired(accountId)
            }
            result
        }
    }

    private suspend fun save(
        request: TrackingProviderRequest,
        mediaId: Int,
        token: String,
    ): TrackingDeliveryResult {
        val draft = request.command.draft
        val fields = draft.fields
        if (TrackingField.PROGRESS_SECONDARY in fields || TrackingField.DELETE in fields) {
            return TrackingDeliveryResult.TerminalFailure(TrackingFailureKind.UNSUPPORTED_FIELD)
        }
        val desired = draft.desired
        val startedAt = desired.startedAt?.toFuzzyDateInputOrNull()
        val completedAt = desired.completedAt?.toFuzzyDateInputOrNull()
        if ((desired.startedAt != null && startedAt == null) ||
            (desired.completedAt != null && completedAt == null)
        ) {
            return TrackingDeliveryResult.TerminalFailure(TrackingFailureKind.VALIDATION)
        }
        val response = clients.create(token).mutation(
            SaveMediaListEntryMutation(
                mediaId = Optional.present(mediaId),
                status = fields.optional(TrackingField.STATUS) {
                    desired.status?.toAniListStatus()
                },
                progress = fields.optional(TrackingField.PROGRESS) { desired.progress },
                score = fields.optional(TrackingField.SCORE) { desired.score100 },
                repeat = fields.optional(TrackingField.REPEAT_COUNT) { desired.repeatCount },
                notes = fields.optional(TrackingField.NOTES) {
                    desired.notes?.let(::encodeForAniList)
                },
                startedAt = fields.optional(TrackingField.STARTED_AT) { startedAt },
                completedAt = fields.optional(TrackingField.COMPLETED_AT) { completedAt },
                customLists = fields.optional(TrackingField.CUSTOM_LISTS) { desired.customLists },
                `private` = fields.optional(TrackingField.PRIVATE) { desired.isPrivate },
                hiddenFromStatusLists = fields.optional(
                    TrackingField.HIDDEN_FROM_STATUS_LISTS
                ) { desired.hiddenFromStatusLists },
            )
        ).execute()
        response.graphQlFailureOrNull()?.let { return it }
        val saved = response.data?.SaveMediaListEntry
            ?: return TrackingDeliveryResult.TerminalFailure(TrackingFailureKind.INVALID_RESPONSE)
        return TrackingDeliveryResult.Success(
            TrackingConfirmedSnapshot(
                providerListEntryId = saved.id.toLong(),
                state = desired,
                rawProviderFieldsJson = "{}",
            )
        )
    }

    private suspend fun delete(
        request: TrackingProviderRequest,
        token: String,
    ): TrackingDeliveryResult {
        val listEntryId = request.command.draft
            .providerListEntryIds[TrackingProvider.ANILIST]
            ?.takeIf { it in 1..Int.MAX_VALUE.toLong() }
            ?.toInt()
            ?: return TrackingDeliveryResult.TerminalFailure(TrackingFailureKind.MISSING_IDENTITY)
        val response = clients.create(token).mutation(
            DeleteMediaListEntryMutation(id = Optional.present(listEntryId))
        ).execute()
        response.graphQlFailureOrNull()?.let { return it }
        if (response.data?.DeleteMediaListEntry?.deleted != true) {
            return TrackingDeliveryResult.TerminalFailure(TrackingFailureKind.INVALID_RESPONSE)
        }
        return TrackingDeliveryResult.Success(
            TrackingConfirmedSnapshot(
                providerListEntryId = listEntryId.toLong(),
                state = request.command.draft.desired,
                rawProviderFieldsJson = "{}",
                deleted = true,
            )
        )
    }
}

private inline fun <T> Set<TrackingField>.optional(
    field: TrackingField,
    value: () -> T?,
): Optional<T?> = if (field in this) Optional.present(value()) else Optional.absent()

private fun TrackingStatus.toAniListStatus(): MediaListStatus = when (this) {
    TrackingStatus.CURRENT -> MediaListStatus.CURRENT
    TrackingStatus.PLANNING -> MediaListStatus.PLANNING
    TrackingStatus.COMPLETED -> MediaListStatus.COMPLETED
    TrackingStatus.DROPPED -> MediaListStatus.DROPPED
    TrackingStatus.PAUSED -> MediaListStatus.PAUSED
    TrackingStatus.REPEATING -> MediaListStatus.REPEATING
}

private fun String.toFuzzyDateInputOrNull(): FuzzyDateInput? = runCatching {
    val date = LocalDate.parse(this)
    FuzzyDateInput(
        year = Optional.present(date.year),
        month = Optional.present(date.monthValue),
        day = Optional.present(date.dayOfMonth),
    )
}.getOrNull()

private fun com.apollographql.apollo.api.ApolloResponse<*>.graphQlFailureOrNull(): TrackingDeliveryResult? {
    if (!hasErrors()) return null
    val status = errors?.firstNotNullOfOrNull { error ->
        (error.extensions?.get("status") as? Number)?.toInt()
    }
    return status.toDeliveryFailure()
}

private fun Int?.toDeliveryFailure(): TrackingDeliveryResult = when {
    this == 401 -> TrackingDeliveryResult.TerminalFailure(
        TrackingFailureKind.UNAUTHORIZED,
        this,
    )
    this == 429 -> TrackingDeliveryResult.RetryableFailure(
        TrackingFailureKind.RATE_LIMITED,
        this,
    )
    this != null && this in 500..599 -> TrackingDeliveryResult.RetryableFailure(
        TrackingFailureKind.TRANSIENT_SERVER,
        this,
    )
    else -> TrackingDeliveryResult.TerminalFailure(
        TrackingFailureKind.PERMANENT,
        this,
    )
}

private fun Throwable.toDeliveryFailure(): TrackingDeliveryResult = when (this) {
    is ApiError.RateLimited -> TrackingDeliveryResult.RetryableFailure(
        TrackingFailureKind.RATE_LIMITED,
        httpStatus = 429,
        retryAfterMillis = retryAfterSeconds.coerceAtLeast(0L) * 1_000L,
    )
    is ApiError.Unauthorized -> TrackingDeliveryResult.TerminalFailure(
        TrackingFailureKind.UNAUTHORIZED,
        401,
    )
    is ApiError.Forbidden -> TrackingDeliveryResult.TerminalFailure(
        TrackingFailureKind.PERMANENT,
        403,
    )
    is ApiError.ServerError -> TrackingDeliveryResult.RetryableFailure(
        TrackingFailureKind.TRANSIENT_SERVER,
        statusCode,
    )
    is ApiError.NetworkError,
    is ApolloNetworkException -> TrackingDeliveryResult.RetryableFailure(
        TrackingFailureKind.TRANSPORT,
    )
    is ApiError.GraphQLError -> statusCode.toDeliveryFailure()
    is ApolloHttpException -> statusCode.toDeliveryFailure()
    is ApolloException -> TrackingDeliveryResult.RetryableFailure(TrackingFailureKind.TRANSPORT)
    else -> TrackingDeliveryResult.RetryableFailure(TrackingFailureKind.TRANSPORT)
}
