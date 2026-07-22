package com.anisync.android.data.mal.oauth

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AndroidMalOAuthSessionStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        context.deleteSharedPreferences(AndroidMalOAuthSessionStore.SECURE_PREFS)
    }

    @After
    fun tearDown() {
        context.deleteSharedPreferences(AndroidMalOAuthSessionStore.SECURE_PREFS)
    }

    @Test
    fun encryptedSessionSurvivesStoreRecreationWithoutPlaintextInBackingFile() {
        val session = MalOAuthSession(
            sessionId = "session-fixture",
            environment = MalOAuthEnvironment.DEBUG,
            redirectUri = MalOAuthEnvironment.DEBUG.redirectUri,
            pkceMethod = MalPkceMethod.PLAIN,
            verifier = "pkce-verifier-fixture",
            challenge = "pkce-challenge-fixture",
            state = "oauth-state-fixture",
            createdAtEpochMillis = 1_000L,
            expiresAtEpochMillis = 5_000L,
            phase = MalOAuthSessionPhase.CALLBACK_STAGED,
            authorizationCode = "authorization-code-fixture",
        )
        val first = AndroidMalOAuthSessionStore(context)
        assertTrue(first.save(session) is MalOAuthSessionStoreResult.Success)

        val recreated = AndroidMalOAuthSessionStore(context)
        val restored = recreated.read() as MalOAuthSessionStoreResult.Success
        assertEquals(session, restored.value)

        val backing = File(context.applicationInfo.dataDir, "shared_prefs/${AndroidMalOAuthSessionStore.SECURE_PREFS}.xml")
        val raw = backing.readText()
        listOf(
            "pkce-verifier-fixture",
            "pkce-challenge-fixture",
            "oauth-state-fixture",
            "authorization-code-fixture",
        ).forEach { sensitive ->
            assertFalse("encrypted preferences leaked $sensitive", raw.contains(sensitive))
        }

        val consumed = recreated.consume(
            session.sessionId,
            MalPkceGenerator.stateHash(session.state),
        )
        assertTrue(consumed is MalOAuthSessionStoreResult.Success)
        assertNull((recreated.read() as MalOAuthSessionStoreResult.Success).value)
        assertEquals(
            MalPkceGenerator.stateHash(session.state),
            (recreated.lastConsumedStateHash() as MalOAuthSessionStoreResult.Success).value,
        )
    }
}
