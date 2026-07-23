package com.anisync.android.data.tracking

import android.content.Context
import com.anisync.android.data.account.AccountStore
import com.anisync.android.data.mal.account.MalAccountCredentialStore
import com.anisync.android.data.provider.ActiveProviderStore
import com.anisync.android.domain.provider.ActiveProvider
import com.anisync.android.domain.tracking.ProviderNetworkPolicy
import com.anisync.android.domain.tracking.TrackingFailureKind
import com.anisync.android.domain.tracking.TrackingProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Last fail-closed boundary before a durable tracking command may reach a provider adapter.
 *
 * The switches are intentionally provider-specific and contain no credentials. They are an
 * emergency/worker kill switch, not a routing preference: routing still determines which durable
 * targets exist, while this gate can stop a previously queued target immediately. Account identity
 * is checked again at delivery time so a queued command can never follow a later account switch.
 */
@Singleton
class TrackingWriteGate @Inject constructor(
    @ApplicationContext context: Context,
    private val accountStore: AccountStore,
    private val malAccounts: MalAccountCredentialStore,
    private val activeProviderStore: ActiveProviderStore,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun currentPolicy(): ProviderNetworkPolicy = ProviderNetworkPolicy(
        allowAniList = readEnabled(KEY_ANILIST_WRITES_ENABLED),
        allowMyAnimeList = readEnabled(KEY_MYANIMELIST_WRITES_ENABLED),
    )

    suspend fun blocker(
        provider: TrackingProvider,
        expectedAccountId: String,
    ): TrackingFailureKind? {
        val activeAccountId = when (provider) {
            TrackingProvider.ANILIST -> accountStore.activeAccount.value?.id?.toString()
            TrackingProvider.MYANIMELIST -> malAccounts.activeAccount()?.localAccountId
        }
        val providerState = activeProviderStore.snapshot()
        return evaluateTrackingWriteGate(
            provider = provider,
            policy = currentPolicy(),
            expectedAccountId = expectedAccountId,
            activeAccountId = activeAccountId,
            activeProvider = providerState.activeProvider,
            providerTrafficAllowed = providerState.providerTrafficAllowed,
        )
    }

    /** Provider-specific emergency switch used by trusted application control paths. */
    fun setProviderEnabled(provider: TrackingProvider, enabled: Boolean) {
        preferences.edit()
            .putBoolean(
                when (provider) {
                    TrackingProvider.ANILIST -> KEY_ANILIST_WRITES_ENABLED
                    TrackingProvider.MYANIMELIST -> KEY_MYANIMELIST_WRITES_ENABLED
                },
                enabled,
            )
            .apply()
    }

    private fun readEnabled(key: String): Boolean = runCatching {
        preferences.getBoolean(key, true)
    }.getOrDefault(false)

    private companion object {
        const val PREFERENCES_NAME = "tracking_write_gate"
        const val KEY_ANILIST_WRITES_ENABLED = "anilist_tracking_writes_enabled"
        const val KEY_MYANIMELIST_WRITES_ENABLED = "myanimelist_tracking_writes_enabled"
    }
}

internal fun evaluateTrackingWriteGate(
    provider: TrackingProvider,
    policy: ProviderNetworkPolicy,
    expectedAccountId: String,
    activeAccountId: String?,
    activeProvider: ActiveProvider = when (provider) {
        TrackingProvider.ANILIST -> ActiveProvider.ANILIST_ONLY
        TrackingProvider.MYANIMELIST -> ActiveProvider.MAL_ONLY
    },
    providerTrafficAllowed: Boolean = true,
): TrackingFailureKind? = when {
    !providerTrafficAllowed -> TrackingFailureKind.PROVIDER_NOT_CONFIGURED
    activeProvider.trackingProvider != provider -> TrackingFailureKind.PROVIDER_NOT_CONFIGURED
    expectedAccountId.isBlank() -> TrackingFailureKind.MISSING_ACCOUNT
    provider == TrackingProvider.ANILIST && !policy.allowAniList ->
        TrackingFailureKind.NETWORK_BLOCKED
    provider == TrackingProvider.MYANIMELIST && !policy.allowMyAnimeList ->
        TrackingFailureKind.NETWORK_BLOCKED
    activeAccountId != expectedAccountId -> TrackingFailureKind.MISSING_ACCOUNT
    else -> null
}
