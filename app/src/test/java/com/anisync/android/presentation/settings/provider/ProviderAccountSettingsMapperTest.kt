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
    fun `missing expired and corrupt MAL sessions render safely`() {
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
            ProviderAccountSettingsMapper.map(
                providerState = runtime,
                accountSnapshot = ProviderAccountSnapshot(
                    malAccount = malAccount(MalTokenStatus.EXPIRED),
                ),
                malConsentStored = false,
                busy = false,
                error = null,
                nowEpochMillis = NOW,
            ).sessionState,
        )
        assertEquals(
            ProviderSessionDisplayState.CORRUPT,
            ProviderAccountSettingsMapper.map(
                providerState = runtime,
                accountSnapshot = ProviderAccountSnapshot(
                    malAccount = malAccount(MalTokenStatus.CORRUPT),
                ),
                malConsentStored = false,
                busy = false,
                error = null,
                nowEpochMillis = NOW,
            ).sessionState,
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
    fun `MAL mode exposes only generic account actions and MAL consent`() {
        val state = ProviderAccountSettingsMapper.map(
            providerState = ProviderRuntimeState(activeProvider = ActiveProvider.MAL_ONLY),
            accountSnapshot = ProviderAccountSnapshot(
                malAccount = malAccount(MalTokenStatus.ACTIVE, NOW + 100_000L),
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
        assertFalse(state.availableActions.any { it.name.contains("ANILIST") })
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
