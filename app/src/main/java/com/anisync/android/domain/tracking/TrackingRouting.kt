package com.anisync.android.domain.tracking

import com.anisync.android.domain.provider.ActiveProvider

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
    val activeProvider: ActiveProvider,
    val target: TrackingCommandTarget?,
) {
    val fullyExecutable: Boolean get() = target != null && target.blocker == null
}

class TrackingRouteResolver {
    fun resolve(
        activeProvider: ActiveProvider,
        accounts: TrackingAccountSelection,
        identities: TrackingIdentitySelection,
        network: ProviderNetworkPolicy,
    ): TrackingRoute {
        val provider = activeProvider.trackingProvider
            ?: return TrackingRoute(activeProvider, target = null)
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
        return TrackingRoute(
            activeProvider = activeProvider,
            target = TrackingCommandTarget(
                provider = provider,
                providerAccountId = account,
                providerMediaId = mediaId,
                blocker = when {
                    !allowed -> TrackingFailureKind.NETWORK_BLOCKED
                    account == null -> TrackingFailureKind.MISSING_ACCOUNT
                    mediaId == null -> TrackingFailureKind.MISSING_IDENTITY
                    else -> null
                },
            ),
        )
    }
}
