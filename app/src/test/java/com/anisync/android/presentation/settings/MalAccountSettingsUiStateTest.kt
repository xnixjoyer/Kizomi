package com.anisync.android.presentation.settings

import com.anisync.android.data.mal.account.MalAccount
import com.anisync.android.data.mal.account.MalAccountProfile
import com.anisync.android.data.mal.account.MalAccountProvider
import com.anisync.android.data.mal.account.MalTokenStatus
import com.anisync.android.data.mal.oauth.MalAuthFailureReason
import com.anisync.android.data.mal.oauth.MalAuthState
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
    fun `sanitized error retains account context without sensitive fields`() {
        val previous = MalAccountSettingsUiState(
            connectionState = MalAccountConnectionState.CONNECTED,
            configured = true,
            localAccountId = "local-1",
        )

        val error = MalAuthState.Error(
            reason = MalAuthFailureReason.INVALID_GRANT,
            retryAfterSeconds = 10,
        ).toMalAccountSettingsUiState(previous)

        assertEquals(MalAccountConnectionState.ERROR, error.connectionState)
        assertEquals("local-1", error.localAccountId)
        assertEquals(MalAuthFailureReason.INVALID_GRANT, error.failureReason)
        assertEquals(10L, error.retryAfterSeconds)
        assertFalse(error.toString().contains("access-token"))
        assertFalse(error.toString().contains("refresh-token"))
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
