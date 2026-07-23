package com.anisync.android.data.mal.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MalAccountModelRedactionTest {
    @Test
    fun `account profile and failures never render account identity`() {
        val sentinels = listOf(
            "local-account-private-sentinel",
            "username-private-sentinel",
            "display-name-private-sentinel",
            "avatar-private-sentinel",
            "access-token-private-sentinel",
            "refresh-token-private-sentinel",
        )
        val profile = MalAccountProfile(
            malUserId = 987654321L,
            username = sentinels[1],
            displayName = sentinels[2],
            avatarUrl = sentinels[3],
        )
        val tokens = MalTokenSet(
            accessToken = sentinels[4],
            refreshToken = sentinels[5],
            scopes = setOf("read", "write"),
        )
        val account = MalAccount(
            localAccountId = sentinels[0],
            provider = MalAccountProvider.MYANIMELIST,
            profile = profile,
            tokenGeneration = 3,
            tokenExpiresAtEpochMillis = 10_000,
            scopes = setOf("read", "write"),
            tokenStatus = MalTokenStatus.ACTIVE,
            isActive = true,
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 2,
        )
        val failure = MalAccountResult.Failure(
            reason = MalAccountFailureReason.ACCOUNT_SESSION_CHANGED,
            localAccountId = sentinels[0],
            generation = 3,
        )
        val rendered = listOf(profile, tokens, account, failure).joinToString("\n")

        sentinels.forEach { sentinel -> assertFalse(sentinel, rendered.contains(sentinel)) }
        assertFalse(rendered.contains("987654321"))
        assertTrue(rendered.contains("<redacted>"))
        assertTrue(rendered.contains("scopeCount=2"))
    }
}
