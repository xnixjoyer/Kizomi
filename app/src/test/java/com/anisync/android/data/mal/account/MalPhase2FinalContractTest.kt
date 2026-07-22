package com.anisync.android.data.mal.account

import com.anisync.android.data.local.entity.MalAccountEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MalPhase2FinalContractTest {
    @Test
    fun `Room metadata has only opaque references and no raw credential fields`() {
        val fields = MalAccountEntity::class.java.declaredFields.map { it.name }.toSet()

        assertTrue(fields.contains("accessTokenRef"))
        assertTrue(fields.contains("refreshTokenRef"))
        assertFalse(fields.contains("accessToken"))
        assertFalse(fields.contains("refreshToken"))
        assertFalse(fields.contains("clientSecret"))
        assertFalse(fields.contains("authorizationCode"))
    }

    @Test
    fun `logout remove expiry and keystore loss remain distinct states`() {
        assertNotEquals(MalTokenStatus.MISSING, MalTokenStatus.EXPIRED)
        assertNotEquals(MalTokenStatus.MISSING, MalTokenStatus.KEYSTORE_RESET)
        assertNotEquals(MalTokenStatus.CORRUPT, MalTokenStatus.KEYSTORE_RESET)
        assertEquals(5, MalTokenStatus.entries.size)
    }

    @Test
    fun `generation references identify account and immutable generation without token material`() {
        val reference = AndroidMalTokenVault.reference("local-account", 7L)

        assertEquals("bundle:local-account:7", reference)
        assertFalse(reference.contains("access-token"))
        assertFalse(reference.contains("refresh-token"))
    }
}
