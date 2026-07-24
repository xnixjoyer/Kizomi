package com.anisync.android.presentation.settings.provider

import com.anisync.android.data.mal.account.MalAccount
import com.anisync.android.data.mal.account.MalTokenStatus
import com.anisync.android.domain.provider.ActiveProvider
import com.anisync.android.domain.provider.ProviderRuntimeState
import com.anisync.android.domain.provider.ProviderTransitionPhase

enum class ProviderSessionDisplayState {
    NOT_CONFIGURED,
    TRANSITIONING,
    CONNECTED,
    EXPIRING,
    MISSING,
    EXPIRED,
    CORRUPT,
    KEYSTORE_RESET,
    UNKNOWN,
}

enum class ProviderAccountAction {
    DISCONNECT_AND_DELETE_LOCAL_DATA,
    CHANGE_PROVIDER,
    REVOKE_MAL_CONSENT,
}

enum class SharedSettingsDestination {
    APPEARANCE,
    LANGUAGE,
    ACCESSIBILITY,
    STORAGE,
    UPDATES,
}

enum class ProviderAccountSettingsError {
    LOCAL_ACTION_FAILED,
    LOCAL_STATE_UNAVAILABLE,
}

data class ProviderAccountSnapshot(
    val malAccount: MalAccount? = null,
    val aniListAccountPresent: Boolean = false,
    val aniListAccountExpired: Boolean = false,
)

data class ProviderAccountSettingsUiState(
    val activeProvider: ActiveProvider = ActiveProvider.UNCONFIGURED,
    val transitionPhase: ProviderTransitionPhase = ProviderTransitionPhase.IDLE,
    val sessionState: ProviderSessionDisplayState = ProviderSessionDisplayState.NOT_CONFIGURED,
    val accountRecordPresent: Boolean = false,
    val expiryEpochMillis: Long? = null,
    val availableActions: Set<ProviderAccountAction> = emptySet(),
    val sharedDestinations: List<SharedSettingsDestination> = SharedSettingsCatalog.destinations,
    val busy: Boolean = false,
    val error: ProviderAccountSettingsError? = null,
)

object SharedSettingsCatalog {
    val destinations: List<SharedSettingsDestination> = listOf(
        SharedSettingsDestination.APPEARANCE,
        SharedSettingsDestination.LANGUAGE,
        SharedSettingsDestination.ACCESSIBILITY,
        SharedSettingsDestination.STORAGE,
        SharedSettingsDestination.UPDATES,
    )
}

object ProviderAccountSettingsMapper {
    private const val EXPIRING_WINDOW_MILLIS = 24L * 60L * 60L * 1000L

    fun map(
        providerState: ProviderRuntimeState,
        accountSnapshot: ProviderAccountSnapshot,
        malConsentStored: Boolean,
        busy: Boolean,
        error: ProviderAccountSettingsError?,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): ProviderAccountSettingsUiState {
        val sessionState = when {
            providerState.transitionPhase != ProviderTransitionPhase.IDLE ->
                ProviderSessionDisplayState.TRANSITIONING
            providerState.activeProvider == ActiveProvider.UNCONFIGURED ->
                ProviderSessionDisplayState.NOT_CONFIGURED
            providerState.activeProvider == ActiveProvider.MAL_ONLY ->
                mapMalSession(accountSnapshot.malAccount, nowEpochMillis)
            providerState.activeProvider == ActiveProvider.ANILIST_ONLY -> when {
                !accountSnapshot.aniListAccountPresent -> ProviderSessionDisplayState.MISSING
                accountSnapshot.aniListAccountExpired -> ProviderSessionDisplayState.EXPIRED
                else -> ProviderSessionDisplayState.CONNECTED
            }
            else -> ProviderSessionDisplayState.UNKNOWN
        }

        val accountPresent = when (providerState.activeProvider) {
            ActiveProvider.MAL_ONLY -> accountSnapshot.malAccount != null
            ActiveProvider.ANILIST_ONLY -> accountSnapshot.aniListAccountPresent
            ActiveProvider.UNCONFIGURED -> false
        }

        val actions = buildSet {
            if (providerState.activeProvider != ActiveProvider.UNCONFIGURED) {
                add(ProviderAccountAction.DISCONNECT_AND_DELETE_LOCAL_DATA)
                add(ProviderAccountAction.CHANGE_PROVIDER)
            }
            if (providerState.activeProvider == ActiveProvider.MAL_ONLY && malConsentStored) {
                add(ProviderAccountAction.REVOKE_MAL_CONSENT)
            }
        }

        return ProviderAccountSettingsUiState(
            activeProvider = providerState.activeProvider,
            transitionPhase = providerState.transitionPhase,
            sessionState = sessionState,
            accountRecordPresent = accountPresent,
            expiryEpochMillis = accountSnapshot.malAccount
                ?.tokenExpiresAtEpochMillis
                ?.takeIf { providerState.activeProvider == ActiveProvider.MAL_ONLY },
            availableActions = actions,
            busy = busy,
            error = error,
        )
    }

    private fun mapMalSession(
        account: MalAccount?,
        nowEpochMillis: Long,
    ): ProviderSessionDisplayState {
        account ?: return ProviderSessionDisplayState.MISSING
        return when (account.tokenStatus) {
            MalTokenStatus.ACTIVE -> {
                val expiry = account.tokenExpiresAtEpochMillis
                when {
                    expiry == null -> ProviderSessionDisplayState.CONNECTED
                    expiry <= nowEpochMillis -> ProviderSessionDisplayState.EXPIRED
                    expiry - nowEpochMillis <= EXPIRING_WINDOW_MILLIS ->
                        ProviderSessionDisplayState.EXPIRING
                    else -> ProviderSessionDisplayState.CONNECTED
                }
            }
            MalTokenStatus.EXPIRED -> ProviderSessionDisplayState.EXPIRED
            MalTokenStatus.MISSING -> ProviderSessionDisplayState.MISSING
            MalTokenStatus.CORRUPT -> ProviderSessionDisplayState.CORRUPT
            MalTokenStatus.KEYSTORE_RESET -> ProviderSessionDisplayState.KEYSTORE_RESET
        }
    }
}
