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
import com.anisync.android.domain.tracking.PerMediaTrackingPolicy
import com.anisync.android.domain.tracking.ProviderNetworkPolicy
import com.anisync.android.domain.tracking.TrackingAccountSelection
import com.anisync.android.domain.tracking.TrackingCommandDraft
import com.anisync.android.domain.tracking.TrackingCommandTarget
import com.anisync.android.domain.tracking.TrackingDesiredState
import com.anisync.android.domain.tracking.TrackingEnqueueResult
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingField
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
 * Internal exact-target command used by conflict resolution and reconciliation execution.
 *
 * The target account and provider identity are captured from a persisted snapshot/plan. They are
 * never inferred or substituted at execution time. A switched or missing active account becomes an
 * explicit blocked target instead of silently redirecting the write.
 */
internal data class ExactTrackingCommandInput(
    val localMediaId: String,
    val mediaType: TrackingMediaType,
    val desired: TrackingDesiredState,
    val fields: Set<TrackingField>,
    val provider: TrackingProvider,
    val providerAccountId: String,
    val providerMediaId: Long,
    val providerListEntryId: Long? = null,
    val deleteIntent: Boolean = false,
) {
    init {
        require(localMediaId.isNotBlank())
        require(providerAccountId.isNotBlank())
        require(providerMediaId > 0L)
        require(providerListEntryId == null || providerListEntryId > 0L)
        require(fields.isNotEmpty())
        require(deleteIntent == (TrackingField.DELETE in fields))
        require(deleteIntent || desired.status != null)
    }
}

/** Explicit fail-closed field matrix for AniList list mutations. */
internal object AniListTrackingCapabilities {
    private val supported = TrackingField.entries.toSet() - TrackingField.PROGRESS_SECONDARY

    fun unsupported(requested: Set<TrackingField>): Set<TrackingField> = requested - supported
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
        return enqueueCommand(draft, targets)
    }

    /**
     * Enqueues one exact provider target without consulting the user's routing mode.
     *
     * This is intentionally internal: callers must originate from a persisted account- and
     * identity-bound conflict or reconciliation item. Capability and active-account checks fail
     * closed before transport; the durable outbox remains the only delivery path.
     */
    internal suspend fun enqueueExact(input: ExactTrackingCommandInput): TrackingEnqueueResult {
        val unsupported = when (input.provider) {
            TrackingProvider.ANILIST -> AniListTrackingCapabilities.unsupported(input.fields)
            TrackingProvider.MYANIMELIST ->
                MalTrackingCapabilities.forMediaType(input.mediaType).unsupported(input.fields)
        }
        if (unsupported.isNotEmpty()) {
            return TrackingEnqueueResult.Rejected(TrackingFailureKind.UNSUPPORTED_FIELD)
        }
        if (input.deleteIntent && input.providerListEntryId == null) {
            return TrackingEnqueueResult.Rejected(TrackingFailureKind.MISSING_IDENTITY)
        }

        val network = providerNetworkPolicy()
        val activeAccountId = when (input.provider) {
            TrackingProvider.ANILIST -> activeAniListAccountId()
            TrackingProvider.MYANIMELIST -> activeMalAccountId()
        }
        val blocker = when {
            input.provider == TrackingProvider.ANILIST && !network.allowAniList ->
                TrackingFailureKind.NETWORK_BLOCKED
            input.provider == TrackingProvider.MYANIMELIST && !network.allowMyAnimeList ->
                TrackingFailureKind.NETWORK_BLOCKED
            input.provider == TrackingProvider.MYANIMELIST && !isMalConfigured() ->
                TrackingFailureKind.PROVIDER_NOT_CONFIGURED
            activeAccountId != input.providerAccountId -> TrackingFailureKind.MISSING_ACCOUNT
            else -> null
        }
        val listEntryIds = input.providerListEntryId?.let {
            mapOf(input.provider to it)
        }.orEmpty()
        val draft = TrackingCommandDraft(
            localMediaId = input.localMediaId,
            mediaType = input.mediaType,
            desired = input.desired,
            fields = input.fields,
            deleteIntent = input.deleteIntent,
            providerListEntryIds = listEntryIds,
        )
        return enqueueCommand(
            draft,
            listOf(
                TrackingCommandTarget(
                    provider = input.provider,
                    providerAccountId = input.providerAccountId,
                    providerMediaId = input.providerMediaId,
                    blocker = blocker,
                )
            ),
        )
    }
}
