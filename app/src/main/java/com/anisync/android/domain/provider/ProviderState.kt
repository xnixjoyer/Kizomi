package com.anisync.android.domain.provider

import com.anisync.android.domain.tracking.TrackingProvider

enum class ActiveProvider {
    UNCONFIGURED,
    ANILIST_ONLY,
    MAL_ONLY;

    val trackingProvider: TrackingProvider?
        get() = when (this) {
            UNCONFIGURED -> null
            ANILIST_ONLY -> TrackingProvider.ANILIST
            MAL_ONLY -> TrackingProvider.MYANIMELIST
        }
}

enum class ProviderTransitionPhase {
    IDLE,
    LEGACY_SELECTION_REQUIRED,
    AUTHENTICATING,
    PURGING,
}

data class ProviderRuntimeState(
    val activeProvider: ActiveProvider = ActiveProvider.UNCONFIGURED,
    val transitionPhase: ProviderTransitionPhase = ProviderTransitionPhase.IDLE,
    val pendingProvider: ActiveProvider? = null,
) {
    init {
        require(pendingProvider != ActiveProvider.UNCONFIGURED) {
            "UNCONFIGURED cannot be a pending provider"
        }
        require(
            transitionPhase == ProviderTransitionPhase.AUTHENTICATING || pendingProvider == null
        ) { "A pending provider is valid only while authenticating" }
        require(
            transitionPhase == ProviderTransitionPhase.IDLE ||
                activeProvider == ActiveProvider.UNCONFIGURED
        ) { "Provider transitions must fail closed through UNCONFIGURED" }
    }

    val providerTrafficAllowed: Boolean
        get() = transitionPhase == ProviderTransitionPhase.IDLE &&
            activeProvider != ActiveProvider.UNCONFIGURED
}

object ProviderStateMachine {
    fun reconcile(
        current: ProviderRuntimeState,
        hasAniListAccount: Boolean,
        hasMalAccount: Boolean,
    ): ProviderRuntimeState {
        if (current.transitionPhase == ProviderTransitionPhase.PURGING) return current

        if (hasAniListAccount && hasMalAccount) {
            return ProviderRuntimeState(
                transitionPhase = ProviderTransitionPhase.LEGACY_SELECTION_REQUIRED,
            )
        }

        if (current.transitionPhase == ProviderTransitionPhase.AUTHENTICATING) {
            return current
        }

        return when {
            current.activeProvider == ActiveProvider.ANILIST_ONLY && hasAniListAccount -> current
            current.activeProvider == ActiveProvider.MAL_ONLY && hasMalAccount -> current
            hasAniListAccount -> ProviderRuntimeState(ActiveProvider.ANILIST_ONLY)
            hasMalAccount -> ProviderRuntimeState(ActiveProvider.MAL_ONLY)
            else -> ProviderRuntimeState()
        }
    }

    fun beginLogin(current: ProviderRuntimeState, provider: ActiveProvider): ProviderRuntimeState {
        require(provider != ActiveProvider.UNCONFIGURED)
        require(current.transitionPhase != ProviderTransitionPhase.PURGING)
        return ProviderRuntimeState(
            transitionPhase = ProviderTransitionPhase.AUTHENTICATING,
            pendingProvider = provider,
        )
    }

    fun completeLogin(current: ProviderRuntimeState, provider: ActiveProvider): ProviderRuntimeState {
        require(provider != ActiveProvider.UNCONFIGURED)
        require(
            current.transitionPhase == ProviderTransitionPhase.AUTHENTICATING &&
                current.pendingProvider == provider
        ) { "Login completion must match the pending provider" }
        return ProviderRuntimeState(activeProvider = provider)
    }

    fun cancelLogin(current: ProviderRuntimeState): ProviderRuntimeState {
        require(current.transitionPhase == ProviderTransitionPhase.AUTHENTICATING)
        return ProviderRuntimeState()
    }

    fun beginPurge(): ProviderRuntimeState = ProviderRuntimeState(
        transitionPhase = ProviderTransitionPhase.PURGING,
    )

    fun finishPurge(): ProviderRuntimeState = ProviderRuntimeState()
}
