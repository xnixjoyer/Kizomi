package com.anisync.android.presentation.settings

import com.anisync.android.data.mal.account.MalAccount
import com.anisync.android.data.mal.account.MalAccountProfile
import com.anisync.android.data.mal.account.MalAccountProvider
import com.anisync.android.data.mal.account.MalTokenStatus
import com.anisync.android.data.mal.oauth.MalAuthFailureReason
import com.anisync.android.data.mal.oauth.MalAuthState
import com.anisync.android.domain.tracking.TrackingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MalAccountSettingsUiStateTest {
    @Test
    fun `configuration connected and relogin states map explicitly`() {
        val unavailable = MalAuthState.Disconnected(configured = false)
            .toMalAccountSettingsUiState()
        assertEquals(MalAccountConnectionState.NOT_CONFIGURED, unavailable.connectionState)
        assertFalse(unavailable.configured)

        val connected = MalAuthState.Connected(account())
            .toMalAccountSettingsUiState()
        assertEquals(MalAccountConnectionState.CONNECTED, connected.connectionState)
        assertEquals("MAL User", connected.displayName)
        assertEquals("local-1", connected.localAccountId)

        val relogin = MalAuthState.ReLoginRequired("local-1")
            .toMalAccountSettingsUiState(connected)
        assertEquals(MalAccountConnectionState.RELOGIN_REQUIRED, relogin.connectionState)
        assertTrue(relogin.configured)
    }

    @Test
    fun `sanitized error retains account context without rendering account data`() {
        val accountSentinel = "local-account-private-sentinel"
        val displaySentinel = "display-name-private-sentinel"
        val previous = MalAccountSettingsUiState(
            connectionState = MalAccountConnectionState.CONNECTED,
            configured = true,
            localAccountId = accountSentinel,
            displayName = displaySentinel,
        )

        val error = MalAuthState.Error(
            reason = MalAuthFailureReason.INVALID_GRANT,
            retryAfterSeconds = 10,
        ).toMalAccountSettingsUiState(previous)

        assertEquals(MalAccountConnectionState.ERROR, error.connectionState)
        assertEquals(accountSentinel, error.localAccountId)
        assertEquals(displaySentinel, error.displayName)
        assertEquals(MalAuthFailureReason.INVALID_GRANT, error.failureReason)
        assertEquals(10L, error.retryAfterSeconds)
        assertFalse(error.toString().contains(accountSentinel))
        assertFalse(error.toString().contains(displaySentinel))
        assertFalse(error.toString().contains("access-token"))
        assertFalse(error.toString().contains("refresh-token"))
        assertTrue(error.toString().contains("localAccountId=<redacted>"))
        assertTrue(error.toString().contains("displayName=<redacted>"))
    }

    @Test
    fun `authentication state changes preserve independent visible routing choices`() {
        val routing = MalAccountSettingsUiState(
            animeTrackingMode = TrackingMode.DUAL,
            mangaTrackingMode = TrackingMode.MYANIMELIST_ONLY,
        )

        val connected = MalAuthState.Connected(account())
            .toMalAccountSettingsUiState(routing)
        val disconnected = MalAuthState.Disconnected(configured = true)
            .toMalAccountSettingsUiState(connected)

        assertEquals(TrackingMode.DUAL, connected.animeTrackingMode)
        assertEquals(TrackingMode.MYANIMELIST_ONLY, connected.mangaTrackingMode)
        assertEquals(TrackingMode.DUAL, disconnected.animeTrackingMode)
        assertEquals(TrackingMode.MYANIMELIST_ONLY, disconnected.mangaTrackingMode)
    }

    private fun account() = MalAccount(
        localAccountId = "local-1",
        provider = MalAccountProvider.MYANIMELIST,
        profile = MalAccountProfile(displayName = "MAL User"),
        tokenGeneration = 1L,
        tokenExpiresAtEpochMillis = 20_000L,
        scopes = setOf("read"),
        tokenStatus = MalTokenStatus.ACTIVE,
        isActive = true,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )
}
