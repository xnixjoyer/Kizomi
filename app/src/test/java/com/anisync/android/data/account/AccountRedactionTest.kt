package com.anisync.android.data.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRedactionTest {
    @Test
    fun `account string never contains OAuth token`() {
        val secret = "oauth-secret-that-must-never-be-rendered"
        val rendered = Account(
            id = 7,
            name = "viewer",
            avatarUrl = null,
            expiresAt = 123,
            token = secret,
        ).toString()

        assertFalse(rendered.contains(secret))
        assertTrue(rendered.contains("token=<redacted>"))
    }
}
