package com.anisync.android.data.mal.oauth

import com.anisync.android.data.mal.account.MalAccount
import com.anisync.android.data.mal.account.MalAccountProfile
import com.anisync.android.data.mal.account.MalAccountProvider
import com.anisync.android.data.mal.account.MalTokenStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MalAuthStateRedactionTest {
    @Test
    fun `OAuth state and public results never render session URL or account identity`() {
        val session = "session-private-sentinel"
        val accountId = "account-private-sentinel"
        val url = "https://example.invalid/oauth?code=private-code&state=private-state"
        val account = MalAccount(
            localAccountId = accountId,
            provider = MalAccountProvider.MYANIMELIST,
            profile = MalAccountProfile(
                malUserId = 123456789L,
                username = "username-private-sentinel",
                displayName = "display-private-sentinel",
            ),
            tokenGeneration = 1,
            tokenExpiresAtEpochMillis = null,
            scopes = emptySet(),
            tokenStatus = MalTokenStatus.ACTIVE,
            isActive = true,
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1,
        )
        val rendered = listOf(
            MalAuthState.AwaitingCallback(session),
            MalAuthState.Processing(session),
            MalAuthState.Connected(account),
            MalAuthState.ReLoginRequired(accountId),
            MalAuthState.Error(MalAuthFailureReason.INVALID_GRANT, accountId, 10),
            MalLoginStartResult.Success(url, session),
            MalCallbackResult.Success(account),
        ).joinToString("\n")

        listOf(
            session,
            accountId,
            url,
            "private-code",
            "private-state",
            "username-private-sentinel",
            "display-private-sentinel",
            "123456789",
        ).forEach { sentinel -> assertFalse(sentinel, rendered.contains(sentinel)) }
        assertTrue(rendered.contains("sessionId=<redacted>"))
        assertTrue(rendered.contains("localAccountId=<redacted>"))
        assertTrue(rendered.contains("authorizationUrl=<redacted>"))
        assertTrue(rendered.contains("account=<redacted>"))
    }
}
