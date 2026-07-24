package com.anisync.android.data.mal.oauth

import com.anisync.android.data.mal.account.MalTokenStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MalAuthRepositoryTest {
    @Test
    fun `login start fails closed without public client configuration`() = runTest {
        val fixture = fixture(configured = false)

        val result = fixture.repository.startLogin()

        assertEquals(
            MalAuthFailureReason.CONFIGURATION_UNAVAILABLE,
            (result as MalLoginStartResult.Failure).reason,
        )
        assertNull(fixture.sessions.pending)
    }

    @Test
    fun `successful callback validates state persists account and rejects replay`() = runTest {
        val fixture = fixture()
        val start = fixture.repository.startLogin() as MalLoginStartResult.Success
        val pending = requireNotNull(fixture.sessions.pending)
        assertTrue(start.authorizationUrl.contains("state="))

        val callback = fixture.repository.handleCallback(
            "${pending.redirectUri}?code=code-fixture&state=${pending.state}"
        )

        assertTrue(callback is MalCallbackResult.Success)
        callback as MalCallbackResult.Success
        assertTrue(callback.account.isActive)
        assertEquals(1, fixture.tokens.exchangeCalls)
        assertNull(fixture.sessions.pending)
        assertTrue(fixture.repository.state.value is MalAuthState.Connected)
        assertFalse(callback.toString().contains("code-fixture"))
        assertFalse(callback.toString().contains("access-fixture"))

        val replay = fixture.repository.handleCallback(
            "${pending.redirectUri}?code=code-fixture&state=${pending.state}"
        )
        assertEquals(
            MalAuthFailureReason.CALLBACK_REPLAY,
            (replay as MalCallbackResult.Failure).reason,
        )
    }

    @Test
    fun `wrong redirect state expiry missing code and provider error are terminal`() = runTest {
        val cases = listOf<(Fixture, MalOAuthSession) -> Pair<String, MalAuthFailureReason>>(
            { _, session ->
                "anisyncplus-debug://wrong/mal/callback?code=x&state=${session.state}" to
                    MalAuthFailureReason.CALLBACK_REDIRECT_MISMATCH
            },
            { _, session ->
                "${session.redirectUri}?code=x&state=wrong-state" to
                    MalAuthFailureReason.STATE_MISMATCH
            },
            { fixture, session ->
                fixture.clock.now = session.expiresAtEpochMillis
                "${session.redirectUri}?code=x&state=${session.state}" to
                    MalAuthFailureReason.SESSION_EXPIRED
            },
            { _, session ->
                "${session.redirectUri}?state=${session.state}" to
                    MalAuthFailureReason.MISSING_AUTHORIZATION_CODE
            },
            { _, session ->
                "${session.redirectUri}?error=access_denied&error_description=sensitive&state=${session.state}" to
                    MalAuthFailureReason.PROVIDER_ACCESS_DENIED
            },
        )

        cases.forEach { build ->
            val fixture = fixture()
            fixture.repository.startLogin()
            val session = requireNotNull(fixture.sessions.pending)
            val (callback, expected) = build(fixture, session)

            val result = fixture.repository.handleCallback(callback)

            assertEquals(expected, (result as MalCallbackResult.Failure).reason)
            assertNull(fixture.sessions.pending)
            assertFalse(result.toString().contains("sensitive"))
        }
    }

    @Test
    fun `transient exchange failure keeps encrypted staged continuation for process recreation`() = runTest {
        val fixture = fixture()
        fixture.tokens.exchangeResult = MalOAuthTransportResult.Failure(
            MalOAuthTransportFailureReason.TRANSPORT
        )
        fixture.repository.startLogin()
        val session = requireNotNull(fixture.sessions.pending)

        val first = fixture.repository.handleCallback(
            "${session.redirectUri}?code=process-death-code&state=${session.state}"
        )

        assertEquals(
            MalAuthFailureReason.TRANSPORT,
            (first as MalCallbackResult.Failure).reason,
        )
        assertTrue(requireNotNull(fixture.sessions.pending).isCallbackStaged)

        fixture.tokens.exchangeResult = FakeMalOAuthTokenService.successPayload()
        val recreated = fixture.repository()
        val resumed = recreated.resumePendingLogin()

        assertTrue(resumed is MalCallbackResult.Success)
        assertNull(fixture.sessions.pending)
        assertEquals(2, fixture.tokens.exchangeCalls)
    }

    @Test
    fun `relogin targets existing account and uses expected generation`() = runTest {
        val fixture = fixture()
        val account = fixture.accounts.seed(generation = 7L, status = MalTokenStatus.MISSING)
        fixture.accounts.logout(account.localAccountId)
        fixture.repository.startLogin(account.localAccountId)
        val session = requireNotNull(fixture.sessions.pending)

        val result = fixture.repository.handleCallback(
            "${session.redirectUri}?code=relogin-code&state=${session.state}"
        )

        assertTrue(result is MalCallbackResult.Success)
        result as MalCallbackResult.Success
        assertEquals(account.localAccountId, result.account.localAccountId)
        assertEquals(8L, result.account.tokenGeneration)
        assertEquals(1, fixture.accounts.replaceCalls)
    }

    @Test
    fun `cancelling exchange propagates cancellation and leaves staged callback recoverable`() = runTest {
        val fixture = fixture()
        val blocking = object : MalOAuthTokenService {
            override suspend fun exchangeAuthorizationCode(
                configuration: MalOAuthConfiguration,
                code: String,
                verifier: String,
            ): MalOAuthTransportResult<MalOAuthTokenPayload> {
                delay(Long.MAX_VALUE)
                error("unreachable")
            }

            override suspend fun refresh(
                configuration: MalOAuthConfiguration,
                refreshToken: String,
            ): MalOAuthTransportResult<MalOAuthTokenPayload> = error("unused")
        }
        val repository = fixture.repository(blocking)
        repository.startLogin()
        val session = requireNotNull(fixture.sessions.pending)
        var cancelled = false
        val job = launch {
            try {
                repository.handleCallback(
                    "${session.redirectUri}?code=cancel-code&state=${session.state}"
                )
            } catch (_: CancellationException) {
                cancelled = true
                throw CancellationException()
            }
        }
        testScheduler.runCurrent()

        job.cancelAndJoin()

        assertTrue(cancelled)
        assertTrue(requireNotNull(fixture.sessions.pending).isCallbackStaged)
    }

    @Test
    fun `normal restart restores an active persisted MAL account`() = runTest {
        val fixture = fixture()
        val account = fixture.accounts.seed(status = MalTokenStatus.ACTIVE)
        val recreated = fixture.repository()

        assertTrue(recreated.state.value is MalAuthState.Disconnected)
        assertNull(recreated.resumePendingLogin())

        val connected = recreated.state.value as MalAuthState.Connected
        assertEquals(account.localAccountId, connected.account.localAccountId)
    }

    @Test
    fun `normal restart keeps an expired refreshable MAL account connected`() = runTest {
        val fixture = fixture()
        val account = fixture.accounts.seed(status = MalTokenStatus.EXPIRED)
        val recreated = fixture.repository()

        assertNull(recreated.resumePendingLogin())

        val connected = recreated.state.value as MalAuthState.Connected
        assertEquals(account.localAccountId, connected.account.localAccountId)
        assertEquals(MalTokenStatus.EXPIRED, connected.account.tokenStatus)
    }

    @Test
    fun `normal restart exposes missing corrupt and keystore-reset credentials as relogin required`() = runTest {
        listOf(
            MalTokenStatus.MISSING,
            MalTokenStatus.CORRUPT,
            MalTokenStatus.KEYSTORE_RESET,
        ).forEach { status ->
            val fixture = fixture()
            val account = fixture.accounts.seed(status = status)
            val recreated = fixture.repository()

            assertNull(recreated.resumePendingLogin())

            val relogin = recreated.state.value as MalAuthState.ReLoginRequired
            assertEquals(account.localAccountId, relogin.localAccountId)
        }
    }

    @Test
    fun `startup restoration remains fail closed when the OAuth session store was reset`() = runTest {
        val fixture = fixture()
        fixture.accounts.seed(status = MalTokenStatus.ACTIVE)
        fixture.sessions.initializationReset = true
        val recreated = fixture.repository()

        assertNull(recreated.resumePendingLogin())

        val error = recreated.state.value as MalAuthState.Error
        assertEquals(MalAuthFailureReason.SESSION_STORE_FAILED, error.reason)
    }

    private fun fixture(configured: Boolean = true): Fixture {
        val clock = MutableMalOAuthClock()
        val sessions = FakeMalOAuthSessionStore()
        val tokens = FakeMalOAuthTokenService()
        val accounts = FakeMalAccountCredentialStore()
        val source = FakeMalOAuthConfigurationSource(configured)
        return Fixture(clock, sessions, tokens, accounts, source)
    }

    private data class Fixture(
        val clock: MutableMalOAuthClock,
        val sessions: FakeMalOAuthSessionStore,
        val tokens: FakeMalOAuthTokenService,
        val accounts: FakeMalAccountCredentialStore,
        val source: FakeMalOAuthConfigurationSource,
    ) {
        val repository: MalAuthRepository = repository()

        fun repository(
            tokenService: MalOAuthTokenService = tokens,
        ): MalAuthRepository = MalAuthRepository(
            configurationProvider = source,
            sessionStore = sessions,
            pkceGenerator = MalPkceGenerator(),
            sessionIdGenerator = MalOAuthSessionIdGenerator { "session-fixture" },
            clock = clock,
            tokenService = tokenService,
            accountStore = accounts,
        )
    }
}
