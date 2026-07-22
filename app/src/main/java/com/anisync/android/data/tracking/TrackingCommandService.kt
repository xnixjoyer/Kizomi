package com.anisync.android.data.tracking

import com.anisync.android.data.account.AccountStore
import com.anisync.android.data.identity.AniListMediaIdentityAdapter
import com.anisync.android.data.identity.LocalMediaIdentity
import com.anisync.android.data.identity.LocalMediaType
import com.anisync.android.data.identity.MediaIdentityResult
import com.anisync.android.domain.tracking.TrackingCommandDraft
import com.anisync.android.domain.tracking.TrackingCommandTarget
import com.anisync.android.domain.tracking.TrackingDesiredState
import com.anisync.android.domain.tracking.TrackingEnqueueResult
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingField
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingProvider
import javax.inject.Inject
import javax.inject.Singleton

data class AniListTrackingCommandInput(
    val aniListMediaId: Int,
    val aniListListEntryId: Int? = null,
    val mediaType: TrackingMediaType,
    val desired: TrackingDesiredState,
    val fields: Set<TrackingField>,
    val deleteIntent: Boolean = false,
) {
    init {
        require(aniListMediaId > 0)
        require(aniListListEntryId == null || aniListListEntryId > 0)
        require(fields.isNotEmpty())
        require(deleteIntent == (TrackingField.DELETE in fields))
        require(deleteIntent || desired.status != null)
    }
}

/**
 * The single production ingress for tracking-state writes.
 *
 * UI, receivers, and workers may update their local optimistic projection, but remote delivery is
 * always represented by one durable absolute command before a provider adapter can run. Phase 8
 * deliberately keeps the existing AniList-only default; Phase 9 supplies persisted routing modes.
 */
@Singleton
class TrackingCommandService internal constructor(
    private val ensureLocalIdentity: suspend (LocalMediaType, Int) -> MediaIdentityResult<LocalMediaIdentity>,
    private val activeAniListAccountId: () -> String?,
    private val enqueueCommand: suspend (
        TrackingCommandDraft,
        List<TrackingCommandTarget>,
    ) -> TrackingEnqueueResult,
) {
    @Inject
    constructor(
        identityAdapter: AniListMediaIdentityAdapter,
        accountStore: AccountStore,
        outbox: TrackingOutboxRepository,
    ) : this(
        ensureLocalIdentity = identityAdapter::ensureLocalIdentity,
        activeAniListAccountId = { accountStore.activeAccount.value?.id?.toString() },
        enqueueCommand = outbox::enqueue,
    )

    suspend fun enqueueAniList(input: AniListTrackingCommandInput): TrackingEnqueueResult {
        val localType = when (input.mediaType) {
            TrackingMediaType.ANIME -> LocalMediaType.ANIME
            TrackingMediaType.MANGA -> LocalMediaType.MANGA
        }
        val localIdentity = when (
            val result = ensureLocalIdentity(localType, input.aniListMediaId)
        ) {
            is MediaIdentityResult.Success -> result.value
            is MediaIdentityResult.StorageFailure -> {
                return TrackingEnqueueResult.Rejected(TrackingFailureKind.STORAGE)
            }
            is MediaIdentityResult.Invalid -> {
                return TrackingEnqueueResult.Rejected(TrackingFailureKind.VALIDATION)
            }
            is MediaIdentityResult.NotFound,
            is MediaIdentityResult.Conflict,
            is MediaIdentityResult.Rejected -> {
                return TrackingEnqueueResult.Rejected(TrackingFailureKind.MISSING_IDENTITY)
            }
        }
        val accountId = activeAniListAccountId()
        val draft = TrackingCommandDraft(
            localMediaId = localIdentity.id,
            mediaType = input.mediaType,
            desired = input.desired,
            fields = input.fields,
            deleteIntent = input.deleteIntent,
            providerListEntryIds = input.aniListListEntryId?.let {
                mapOf(TrackingProvider.ANILIST to it.toLong())
            }.orEmpty(),
        )
        return enqueueCommand(
            draft,
            listOf(
                TrackingCommandTarget(
                    provider = TrackingProvider.ANILIST,
                    providerAccountId = accountId,
                    providerMediaId = input.aniListMediaId.toLong(),
                    blocker = when {
                        accountId == null -> TrackingFailureKind.MISSING_ACCOUNT
                        input.deleteIntent && input.aniListListEntryId == null ->
                            TrackingFailureKind.MISSING_IDENTITY
                        else -> null
                    },
                )
            ),
        )
    }
}
