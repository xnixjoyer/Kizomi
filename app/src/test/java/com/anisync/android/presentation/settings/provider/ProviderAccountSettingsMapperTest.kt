package com.anisync.android.presentation.settings.provider

import com.anisync.android.data.mal.account.MalAccount
import com.anisync.android.data.mal.account.MalAccountProfile
import com.anisync.android.data.mal.account.MalAccountProvider
import com.anisync.android.data.mal.account.MalTokenStatus
import com.anisync.android.domain.provider.ActiveProvider
import com.anisync.android.domain.provider.ProviderRuntimeState
import com.anisync.android.domain.provider.ProviderTransitionPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderAccountSettingsMapperTest {
    @Test
    fun `missing expired corrupt and keystore reset MAL sessions render safely`() {
        val runtime = ProviderRuntimeState(activeProvider = ActiveProvider.MAL_ONLY)

        assertEquals(
            ProviderSessionDisplayState.MISSING,
            ProviderAccountSettingsMapper.map(
                providerState = runtime,
                accountSnapshot = ProviderAccountSnapshot(),
                malConsentStored = false,
                busy = false,
                error = null,
                nowEpochMillis = NOW,
            ).sessionState,
        )
        assertEquals(
            ProviderSessionDisplayState.EXPIRED,
            mappedMalState(runtime, MalTokenStatus.EXPIRED),
        )
        assertEquals(
            ProviderSessionDisplayState.CORRUPT,
            mappedMalState(runtime, MalTokenStatus.CORRUPT),
        )
        assertEquals(
            ProviderSessionDisplayState.KEYSTORE_RESET,
            mappedMalState(runtime, MalTokenStatus.KEYSTORE_RESET),
        )
    }

    @Test
    fun `transition state is fail closed in settings`() {
        val state = ProviderAccountSettingsMapper.map(
            providerState = ProviderRuntimeState(
                transitionPhase = ProviderTransitionPhase.PURGING,
            ),
            accountSnapshot = ProviderAccountSnapshot(),
            malConsentStored = true,
            busy = true,
            error = null,
            nowEpochMillis = NOW,
        )

        assertEquals(ProviderSessionDisplayState.TRANSITIONING, state.sessionState)
        assertTrue(state.availableActions.isEmpty())
    }

    @Test
    fun `MAL mode exposes only generic account actions and active MAL consent`() {
        val state = ProviderAccountSettingsMapper.map(
            providerState = ProviderRuntimeState(activeProvider = ActiveProvider.MAL_ONLY),
            accountSnapshot = ProviderAccountSnapshot(
                malAccount = malAccount(MalTokenStatus.ACTIVE, NOW + 100_000L),
                aniListAccountPresent = true,
            ),
            malConsentStored = true,
            busy = false,
            error = null,
            nowEpochMillis = NOW,
        )

        assertEquals(
            setOf(
                ProviderAccountAction.DISCONNECT_AND_DELETE_LOCAL_DATA,
                ProviderAccountAction.CHANGE_PROVIDER,
                ProviderAccountAction.REVOKE_MAL_CONSENT,
            ),
            state.availableActions,
        )
        assertEquals(ActiveProvider.MAL_ONLY, state.activeProvider)
        assertFalse(state.availableActions.any { it.name.contains("ANILIST") })
    }

    @Test
    fun `AniList mode ignores stale MAL account and never exposes MAL consent`() {
        val state = ProviderAccountSettingsMapper.map(
            providerState = ProviderRuntimeState(activeProvider = ActiveProvider.ANILIST_ONLY),
            accountSnapshot = ProviderAccountSnapshot(
                malAccount = malAccount(MalTokenStatus.ACTIVE, NOW + 100_000L),
                aniListAccountPresent = true,
                aniListAccountExpired = false,
            ),
            malConsentStored = true,
            busy = false,
            error = null,
            nowEpochMillis = NOW,
        )

        assertEquals(ProviderSessionDisplayState.CONNECTED, state.sessionState)
        assertTrue(state.accountRecordPresent)
        assertEquals(null, state.expiryEpochMillis)
        assertEquals(
            setOf(
                ProviderAccountAction.DISCONNECT_AND_DELETE_LOCAL_DATA,
                ProviderAccountAction.CHANGE_PROVIDER,
            ),
            state.availableActions,
        )
        assertFalse(ProviderAccountAction.REVOKE_MAL_CONSENT in state.availableActions)
    }

    @Test
    fun `unconfigured state ignores stale provider records and exposes no destructive action`() {
        val state = ProviderAccountSettingsMapper.map(
            providerState = ProviderRuntimeState(activeProvider = ActiveProvider.UNCONFIGURED),
            accountSnapshot = ProviderAccountSnapshot(
                malAccount = malAccount(MalTokenStatus.CORRUPT),
                aniListAccountPresent = true,
                aniListAccountExpired = true,
            ),
            malConsentStored = true,
            busy = false,
            error = ProviderAccountSettingsError.LOCAL_STATE_UNAVAILABLE,
            nowEpochMillis = NOW,
        )

        assertEquals(ProviderSessionDisplayState.NOT_CONFIGURED, state.sessionState)
        assertFalse(state.accountRecordPresent)
        assertTrue(state.availableActions.isEmpty())
        assertEquals(ProviderAccountSettingsError.LOCAL_STATE_UNAVAILABLE, state.error)
    }

    @Test
    fun `shared neutral settings catalog remains provider independent`() {
        assertEquals(
            listOf(
                SharedSettingsDestination.APPEARANCE,
                SharedSettingsDestination.LANGUAGE,
                SharedSettingsDestination.ACCESSIBILITY,
                SharedSettingsDestination.STORAGE,
                SharedSettingsDestination.UPDATES,
            ),
            SharedSettingsCatalog.destinations,
        )
    }

    private fun mappedMalState(
        runtime: ProviderRuntimeState,
        tokenStatus: MalTokenStatus,
    ): ProviderSessionDisplayState = ProviderAccountSettingsMapper.map(
        providerState = runtime,
        accountSnapshot = ProviderAccountSnapshot(malAccount = malAccount(tokenStatus)),
        malConsentStored = false,
        busy = false,
        error = null,
        nowEpochMillis = NOW,
    ).sessionState

    private fun malAccount(
        status: MalTokenStatus,
        expiry: Long? = null,
    ): MalAccount = MalAccount(
        localAccountId = "local-test-account",
        provider = MalAccountProvider.MYANIMELIST,
        profile = MalAccountProfile(),
        tokenGeneration = 1L,
        tokenExpiresAtEpochMillis = expiry,
        scopes = emptySet(),
        tokenStatus = status,
        isActive = true,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )

    private companion object {
        const val NOW = 1_000_000L
    }
}
