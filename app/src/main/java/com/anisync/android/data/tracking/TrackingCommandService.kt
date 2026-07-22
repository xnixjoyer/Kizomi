package com.anisync.android.data.tracking

import com.anisync.android.data.AppSettings
import com.anisync.android.data.account.AccountStore
import com.anisync.android.data.identity.AniListMediaIdentityAdapter
import com.anisync.android.data.identity.LocalMediaIdentity
import com.anisync.android.data.identity.LocalMediaType
import com.anisync.android.data.identity.MediaIdentityProvider
import com.anisync.android.data.identity.MediaIdentityResult
import com.anisync.android.data.identity.MediaIdentityStore
import com.anisync.android.data.identity.ProviderMediaIdentity
import com.anisync.android.data.mal.account.MalAccountCredentialStore
import com.anisync.android.data.mal.oauth.MalOAuthConfigurationSource
import com.anisync.android.domain.tracking.TrackingCommandDraft
import com.anisync.android.domain.tracking.TrackingCommandTarget
import com.anisync.android.domain.tracking.TrackingDesiredState
import com.anisync.android.domain.tracking.TrackingEnqueueResult
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingField
import com.anisync.android.domain.tracking.PerMediaTrackingPolicy
import com.anisync.android.domain.tracking.ProviderNetworkPolicy
import com.anisync.android.domain.tracking.TrackingAccountSelection
import com.anisync.android.domain.tracking.TrackingIdentitySelection
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingMode
import com.anisync.android.domain.tracking.TrackingProvider
import com.anisync.android.domain.tracking.TrackingRouteResolver
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
 * always represented by one durable absolute command before a provider adapter can run. The
 * source UI may still be backed by an AniList model, but provider targets are resolved from the
 * stable local identity and the independently persisted anime/manga policy. A missing provider is
 * retained as a blocked target rather than silently falling back to another provider.
 */
@Singleton
class TrackingCommandService internal constructor(
    private val ensureLocalIdentity: suspend (LocalMediaType, Int) -> MediaIdentityResult<LocalMediaIdentity>,
    private val activeAniListAccountId: () -> String?,
    private val getProviderIdentities: suspend (String) -> MediaIdentityResult<List<ProviderMediaIdentity>> = {
        MediaIdentityResult.Success(emptyList())
    },
    private val activeMalAccountId: suspend () -> String? = { null },
    private val routingPolicy: () -> PerMediaTrackingPolicy = { PerMediaTrackingPolicy() },
    private val providerNetworkPolicy: () -> ProviderNetworkPolicy = { ProviderNetworkPolicy() },
    private val isMalConfigured: () -> Boolean = { false },
    private val enqueueCommand: suspend (
        TrackingCommandDraft,
        List<TrackingCommandTarget>,
    ) -> TrackingEnqueueResult,
) {
    @Inject
    constructor(
        identityAdapter: AniListMediaIdentityAdapter,
        identityStore: MediaIdentityStore,
        accountStore: AccountStore,
        malAccounts: MalAccountCredentialStore,
        malConfiguration: MalOAuthConfigurationSource,
        appSettings: AppSettings,
        outbox: TrackingOutboxRepository,
    ) : this(
        ensureLocalIdentity = identityAdapter::ensureLocalIdentity,
        activeAniListAccountId = { accountStore.activeAccount.value?.id?.toString() },
        getProviderIdentities = identityStore::getProviderIdentities,
        activeMalAccountId = { malAccounts.activeAccount()?.localAccountId },
        routingPolicy = appSettings::currentTrackingPolicy,
        isMalConfigured = { malConfiguration.isLoginConfigured },
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
        val policy = routingPolicy()
        val mode = policy.modeFor(input.mediaType)
        val needsMal = mode == TrackingMode.MYANIMELIST_ONLY || mode == TrackingMode.DUAL
        val malConfigured = !needsMal || isMalConfigured()
        val malIdentity = if (needsMal && malConfigured) {
            when (val result = getProviderIdentities(localIdentity.id)) {
                is MediaIdentityResult.Success -> result.value.firstOrNull {
                    it.provider == MediaIdentityProvider.MYANIMELIST
                }?.providerMediaId
                is MediaIdentityResult.StorageFailure -> {
                    return TrackingEnqueueResult.Rejected(TrackingFailureKind.STORAGE)
                }
                else -> null
            }
        } else {
            null
        }
        val aniListAccountId = activeAniListAccountId()
        val malAccountId = if (needsMal && malConfigured) activeMalAccountId() else null
        val targets = TrackingRouteResolver().resolve(
            mediaType = input.mediaType,
            policy = policy,
            accounts = TrackingAccountSelection(
                aniListAccountId = aniListAccountId,
                myAnimeListAccountId = malAccountId,
            ),
            identities = TrackingIdentitySelection(
                aniListId = input.aniListMediaId.toLong(),
                myAnimeListId = malIdentity,
            ),
            network = providerNetworkPolicy(),
        ).targets.map { target ->
            when {
                target.provider == TrackingProvider.MYANIMELIST && !malConfigured ->
                    target.copy(blocker = TrackingFailureKind.PROVIDER_NOT_CONFIGURED)
                target.provider == TrackingProvider.ANILIST &&
                    input.deleteIntent && input.aniListListEntryId == null ->
                    target.copy(blocker = TrackingFailureKind.MISSING_IDENTITY)
                else -> target
            }
        }
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
            targets,
        )
    }
}
