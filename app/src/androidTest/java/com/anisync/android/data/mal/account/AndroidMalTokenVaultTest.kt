package com.anisync.android.data.mal.account

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class AndroidMalTokenVaultTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        resetTestVault()
    }

    @After
    fun tearDown() {
        resetTestVault()
    }

    @Test
    fun encryptedVaultRoundTripDoesNotWriteTokenPlaintext() {
        val vault = AndroidMalTokenVault(context)
        val tokens = MalTokenSet(
            accessToken = ACCESS_TOKEN,
            refreshToken = REFRESH_TOKEN,
            expiresAtEpochMillis = 12_345L,
            scopes = setOf("write", "read"),
        )

        val write = vault.write(ACCOUNT_ID, 1L, tokens)
        assertTrue(write is MalTokenVaultResult.Success)
        val reference = (write as MalTokenVaultResult.Success).value

        val read = vault.read(reference)
        assertTrue(read is MalTokenVaultResult.Success)
        read as MalTokenVaultResult.Success
        assertEquals(ACCESS_TOKEN, read.value.accessToken)
        assertEquals(REFRESH_TOKEN, read.value.refreshToken)
        assertEquals(setOf("read", "write"), read.value.normalizedScopes())

        val backingFile = File(
            context.applicationInfo.dataDir,
            "shared_prefs/${AndroidMalTokenVault.SECURE_PREFS}.xml",
        )
        assertTrue(backingFile.exists())
        val raw = backingFile.readText()
        assertFalse(raw.contains(ACCESS_TOKEN))
        assertFalse(raw.contains(REFRESH_TOKEN))
        assertFalse(raw.contains(ACCOUNT_ID))

        assertTrue(vault.delete(reference) is MalTokenVaultResult.Success)
        assertTrue(
            (vault.references() as MalTokenVaultResult.Success).value.isEmpty()
        )
    }

    private fun resetTestVault() {
        context.deleteSharedPreferences(AndroidMalTokenVault.SECURE_PREFS)
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            if (keyStore.containsAlias(AndroidMalTokenVault.MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(AndroidMalTokenVault.MASTER_KEY_ALIAS)
            }
        }
    }

    private companion object {
        const val ACCOUNT_ID = "instrumented-account"
        const val ACCESS_TOKEN = "instrumented-access-token-value"
        const val REFRESH_TOKEN = "instrumented-refresh-token-value"
    }
}
