package com.anisync.android.domain.tracking

data class PerMediaTrackingPolicy(
    val animeMode: TrackingMode = TrackingMode.ANILIST_ONLY,
    val mangaMode: TrackingMode = TrackingMode.ANILIST_ONLY,
) {
    fun modeFor(mediaType: TrackingMediaType): TrackingMode = when (mediaType) {
        TrackingMediaType.ANIME -> animeMode
        TrackingMediaType.MANGA -> mangaMode
    }
}

data class ProviderNetworkPolicy(
    val allowAniList: Boolean = true,
    val allowMyAnimeList: Boolean = true,
)

data class TrackingAccountSelection(
    val aniListAccountId: String? = null,
    val myAnimeListAccountId: String? = null,
)

data class TrackingIdentitySelection(
    val aniListId: Long? = null,
    val myAnimeListId: Long? = null,
)

data class TrackingRoute(
    val configuredMode: TrackingMode,
    val targets: List<TrackingCommandTarget>,
) {
    val fullyExecutable: Boolean get() = targets.isNotEmpty() && targets.all { it.blocker == null }
}

class TrackingRouteResolver {
    fun resolve(
        mediaType: TrackingMediaType,
        policy: PerMediaTrackingPolicy,
        accounts: TrackingAccountSelection,
        identities: TrackingIdentitySelection,
        network: ProviderNetworkPolicy,
    ): TrackingRoute {
        val mode = policy.modeFor(mediaType)
        val providers = when (mode) {
            TrackingMode.ANILIST_ONLY -> listOf(TrackingProvider.ANILIST)
            TrackingMode.MYANIMELIST_ONLY -> listOf(TrackingProvider.MYANIMELIST)
            TrackingMode.DUAL -> listOf(TrackingProvider.ANILIST, TrackingProvider.MYANIMELIST)
        }
        return TrackingRoute(
            configuredMode = mode,
            targets = providers.map { provider ->
                val account = when (provider) {
                    TrackingProvider.ANILIST -> accounts.aniListAccountId
                    TrackingProvider.MYANIMELIST -> accounts.myAnimeListAccountId
                }
                val mediaId = when (provider) {
                    TrackingProvider.ANILIST -> identities.aniListId
                    TrackingProvider.MYANIMELIST -> identities.myAnimeListId
                }
                val allowed = when (provider) {
                    TrackingProvider.ANILIST -> network.allowAniList
                    TrackingProvider.MYANIMELIST -> network.allowMyAnimeList
                }
                TrackingCommandTarget(
                    provider = provider,
                    providerAccountId = account,
                    providerMediaId = mediaId,
                    blocker = when {
                        !allowed -> TrackingFailureKind.NETWORK_BLOCKED
                        account == null -> TrackingFailureKind.MISSING_ACCOUNT
                        mediaId == null -> TrackingFailureKind.MISSING_IDENTITY
                        else -> null
                    },
                )
            },
        )
    }
}
