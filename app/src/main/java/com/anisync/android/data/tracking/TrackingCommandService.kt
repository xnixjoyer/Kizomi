package com.anisync.android.data.tracking

import com.anisync.android.data.account.AccountStore
import com.anisync.android.data.identity.AniListMediaIdentityAdapter
import com.anisync.android.data.identity.LocalMediaIdentity
import com.anisync.android.data.identity.LocalMediaType
import com.anisync.android.data.identity.MediaIdentityMappingSource
import com.anisync.android.data.identity.MediaIdentityProvider
import com.anisync.android.data.identity.MediaIdentityResult
import com.anisync.android.data.identity.MediaIdentityStore
import com.anisync.android.data.identity.MediaIdentityVerificationStatus
import com.anisync.android.data.mal.account.MalAccountCredentialStore
import com.anisync.android.data.mal.oauth.MalOAuthConfigurationSource
import com.anisync.android.data.provider.ActiveProviderStore
import com.anisync.android.domain.provider.ActiveProvider
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

data class MalTrackingCommandInput(
    val malMediaId: Long,
    val mediaType: TrackingMediaType,
    val desired: TrackingDesiredState,
    val fields: Set<TrackingField>,
    val deleteIntent: Boolean = false,
) {
    init {
        require(malMediaId > 0L)
        require(fields.isNotEmpty())
        require(deleteIntent == (TrackingField.DELETE in fields))
        require(deleteIntent || desired.status != null)
    }
}

internal object AniListTrackingCapabilities {
    private val supported = TrackingField.entries.toSet() - TrackingField.PROGRESS_SECONDARY
    fun unsupported(requested: Set<TrackingField>): Set<TrackingField> = requested - supported
}

/** The only production ingress for provider list mutations. */
@Singleton
class TrackingCommandService internal constructor(
    private val activeProvider: () -> ActiveProvider,
    private val ensureAniListIdentity: suspend (LocalMediaType, Int) -> MediaIdentityResult<LocalMediaIdentity>,
    private val ensureMalIdentity: suspend (LocalMediaType, Long) -> MediaIdentityResult<LocalMediaIdentity>,
    private val activeAniListAccountId: () -> String?,
    private val activeMalAccountId: suspend () -> String?,
    private val providerNetworkPolicy: () -> ProviderNetworkPolicy,
    private val isMalConfigured: () -> Boolean,
    private val enqueueCommand: suspend (
        TrackingCommandDraft,
        TrackingCommandTarget,
    ) -> TrackingEnqueueResult,
) {
    @Inject
    constructor(
        identityAdapter: AniListMediaIdentityAdapter,
        identityStore: MediaIdentityStore,
        accountStore: AccountStore,
        malAccounts: MalAccountCredentialStore,
        malConfiguration: MalOAuthConfigurationSource,
        providerStore: ActiveProviderStore,
        writeGate: TrackingWriteGate,
        outbox: TrackingOutboxRepository,
    ) : this(
        activeProvider = { providerStore.snapshot().activeProvider },
        ensureAniListIdentity = identityAdapter::ensureLocalIdentity,
        ensureMalIdentity = { type, malId -> identityStore.ensureMalIdentity(type, malId) },
        activeAniListAccountId = { accountStore.activeAccount.value?.id?.toString() },
        activeMalAccountId = { malAccounts.activeAccount()?.localAccountId },
        providerNetworkPolicy = writeGate::currentPolicy,
        isMalConfigured = { malConfiguration.isLoginConfigured },
        enqueueCommand = outbox::enqueue,
    )

    suspend fun enqueueAniList(input: AniListTrackingCommandInput): TrackingEnqueueResult {
        if (activeProvider() != ActiveProvider.ANILIST_ONLY) {
            return TrackingEnqueueResult.Rejected(TrackingFailureKind.PROVIDER_NOT_CONFIGURED)
        }
        val unsupported = AniListTrackingCapabilities.unsupported(input.fields)
        if (unsupported.isNotEmpty()) {
            return TrackingEnqueueResult.Rejected(TrackingFailureKind.UNSUPPORTED_FIELD)
        }
        if (input.deleteIntent && input.aniListListEntryId == null) {
            return TrackingEnqueueResult.Rejected(TrackingFailureKind.MISSING_IDENTITY)
        }
        val localIdentity = when (
            val resolved = resolveIdentity(
                ensureAniListIdentity(input.mediaType.toLocalType(), input.aniListMediaId)
            )
        ) {
            is IdentityResolution.Success -> resolved.identity
            is IdentityResolution.Failure -> return TrackingEnqueueResult.Rejected(resolved.reason)
        }
        val target = route(
            provider = ActiveProvider.ANILIST_ONLY,
            aniListId = input.aniListMediaId.toLong(),
            malId = null,
        ) ?: return TrackingEnqueueResult.Rejected(TrackingFailureKind.PROVIDER_NOT_CONFIGURED)
        return enqueueCommand(
            TrackingCommandDraft(
                localMediaId = localIdentity.id,
                mediaType = input.mediaType,
                desired = input.desired,
                fields = input.fields,
                deleteIntent = input.deleteIntent,
                providerListEntryId = input.aniListListEntryId?.toLong(),
            ),
            target,
        )
    }

    suspend fun enqueueMal(input: MalTrackingCommandInput): TrackingEnqueueResult {
        if (activeProvider() != ActiveProvider.MAL_ONLY || !isMalConfigured()) {
            return TrackingEnqueueResult.Rejected(TrackingFailureKind.PROVIDER_NOT_CONFIGURED)
        }
        val unsupported = MalTrackingCapabilities.forMediaType(input.mediaType)
            .unsupported(input.fields)
        if (unsupported.isNotEmpty()) {
            return TrackingEnqueueResult.Rejected(TrackingFailureKind.UNSUPPORTED_FIELD)
        }
        val localIdentity = when (
            val resolved = resolveIdentity(
                ensureMalIdentity(input.mediaType.toLocalType(), input.malMediaId)
            )
        ) {
            is IdentityResolution.Success -> resolved.identity
            is IdentityResolution.Failure -> return TrackingEnqueueResult.Rejected(resolved.reason)
        }
        val target = route(
            provider = ActiveProvider.MAL_ONLY,
            aniListId = null,
            malId = input.malMediaId,
        ) ?: return TrackingEnqueueResult.Rejected(TrackingFailureKind.PROVIDER_NOT_CONFIGURED)
        return enqueueCommand(
            TrackingCommandDraft(
                localMediaId = localIdentity.id,
                mediaType = input.mediaType,
                desired = input.desired,
                fields = input.fields,
                deleteIntent = input.deleteIntent,
            ),
            target,
        )
    }

    private fun resolveIdentity(
        result: MediaIdentityResult<LocalMediaIdentity>,
    ): IdentityResolution = when (result) {
        is MediaIdentityResult.Success -> IdentityResolution.Success(result.value)
        is MediaIdentityResult.StorageFailure ->
            IdentityResolution.Failure(TrackingFailureKind.STORAGE)
        is MediaIdentityResult.Invalid ->
            IdentityResolution.Failure(TrackingFailureKind.VALIDATION)
        is MediaIdentityResult.NotFound,
        is MediaIdentityResult.Conflict,
        is MediaIdentityResult.Rejected ->
            IdentityResolution.Failure(TrackingFailureKind.MISSING_IDENTITY)
    }

    private sealed interface IdentityResolution {
        data class Success(val identity: LocalMediaIdentity) : IdentityResolution
        data class Failure(val reason: TrackingFailureKind) : IdentityResolution
    }

    private suspend fun route(
        provider: ActiveProvider,
        aniListId: Long?,
        malId: Long?,
    ): TrackingCommandTarget? = TrackingRouteResolver().resolve(
        activeProvider = provider,
        accounts = TrackingAccountSelection(
            aniListAccountId = if (provider == ActiveProvider.ANILIST_ONLY) {
                activeAniListAccountId()
            } else null,
            myAnimeListAccountId = if (provider == ActiveProvider.MAL_ONLY) {
                activeMalAccountId()
            } else null,
        ),
        identities = TrackingIdentitySelection(
            aniListId = aniListId,
            myAnimeListId = malId,
        ),
        network = providerNetworkPolicy(),
    ).target

    private fun TrackingMediaType.toLocalType(): LocalMediaType = when (this) {
        TrackingMediaType.ANIME -> LocalMediaType.ANIME
        TrackingMediaType.MANGA -> LocalMediaType.MANGA
    }
}

private suspend fun MediaIdentityStore.ensureMalIdentity(
    mediaType: LocalMediaType,
    malId: Long,
): MediaIdentityResult<LocalMediaIdentity> {
    when (val existing = resolveByMalId(mediaType, malId)) {
        is MediaIdentityResult.Success -> existing.value?.let {
            return MediaIdentityResult.Success(it)
        }
        is MediaIdentityResult.NotFound -> return existing
        is MediaIdentityResult.Invalid -> return existing
        is MediaIdentityResult.Conflict -> return existing
        is MediaIdentityResult.Rejected -> return existing
        is MediaIdentityResult.StorageFailure -> return existing
    }
    val created = createLocalIdentity(mediaType)
    if (created !is MediaIdentityResult.Success) return created
    return when (
        val attached = attachProviderIdentity(
            localMediaId = created.value.id,
            provider = MediaIdentityProvider.MYANIMELIST,
            providerMediaId = malId,
            mediaType = mediaType,
            mappingSource = MediaIdentityMappingSource.MAL_NATIVE,
            verificationStatus = MediaIdentityVerificationStatus.EXACT,
        )
    ) {
        is MediaIdentityResult.Success -> MediaIdentityResult.Success(created.value)
        is MediaIdentityResult.Conflict -> when (val winner = resolveByMalId(mediaType, malId)) {
            is MediaIdentityResult.Success -> winner.value?.let { MediaIdentityResult.Success(it) }
                ?: attached
            is MediaIdentityResult.NotFound -> attached
            is MediaIdentityResult.Invalid -> attached
            is MediaIdentityResult.Conflict -> attached
            is MediaIdentityResult.Rejected -> attached
            is MediaIdentityResult.StorageFailure -> attached
        }
        is MediaIdentityResult.NotFound -> attached
        is MediaIdentityResult.Invalid -> attached
        is MediaIdentityResult.Rejected -> attached
        is MediaIdentityResult.StorageFailure -> attached
    }
}
