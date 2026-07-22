package com.anisync.android.data.mal.oauth

import com.anisync.android.data.mal.account.MalAccountResult
import com.anisync.android.data.mal.account.MalTokenSet
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MalRefreshCoordinatorTest {
    @Test
    fun `parallel refresh callers share one network flight and one persisted generation`() = runTest {
        val accounts = FakeMalAccountCredentialStore()
        accounts.seed(generation = 4L)
        val service = FakeMalOAuthTokenService()
        val gate = CompletableDeferred<Unit>()
        service.beforeRefresh = { gate.await() }
        val coordinator = coordinator(accounts, service)

        val requests = List(12) {
            async { coordinator.refresh("local-1", observedGeneration = 4L) }
        }
        testScheduler.runCurrent()
        assertEquals(1, service.refreshCalls)
        gate.complete(Unit)
        val results = requests.awaitAll()

        assertTrue(results.all { it is MalRefreshResult.Success })
        assertEquals(setOf(5L), results.map { (it as MalRefreshResult.Success).tokenGeneration }.toSet())
        assertEquals(1, service.refreshCalls)
        assertEquals(1, accounts.replaceCalls)
    }

    @Test
    fun `omitted rotated refresh token retains prior refresh token`() = runTest {
        val accounts = FakeMalAccountCredentialStore()
        accounts.seed()
        val service = FakeMalOAuthTokenService().apply {
            refreshResult = FakeMalOAuthTokenService.successPayload(
                access = "new-access",
                refresh = null,
            )
        }

        val result = coordinator(accounts, service).refresh("local-1", 1L)

        assertTrue(result is MalRefreshResult.Success)
        val persisted = accounts.token("local-1")
        assertEquals("new-access", persisted?.accessToken)
        assertEquals("old-refresh", persisted?.refreshToken)
    }

    @Test
    fun `invalid grant logs out only the affected MAL account and requires relogin`() = runTest {
        val accounts = FakeMalAccountCredentialStore()
        accounts.seed("local-1")
        accounts.seed("local-2", active = false)
        val service = FakeMalOAuthTokenService().apply {
            refreshResult = MalOAuthTransportResult.Failure(
                MalOAuthTransportFailureReason.INVALID_GRANT,
                httpStatus = 400,
            )
        }

        val result = coordinator(accounts, service).refresh("local-1", 1L)

        assertEquals(
            MalRefreshFailureReason.RELOGIN_REQUIRED,
            (result as MalRefreshResult.Failure).reason,
        )
        assertNull(accounts.token("local-1"))
        assertTrue(accounts.token("local-2") != null)
        assertEquals(1, accounts.logoutCalls)
    }

    @Test
    fun `logout during refresh prevents late token restoration`() = runTest {
        val accounts = FakeMalAccountCredentialStore()
        accounts.seed(generation = 3L)
        val service = FakeMalOAuthTokenService()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        service.beforeRefresh = {
            started.complete(Unit)
            release.await()
        }
        val coordinator = coordinator(accounts, service)
        val refresh = async { coordinator.refresh("local-1", 3L) }
        started.await()

        val logout = accounts.logout("local-1")
        assertTrue(logout is MalAccountResult.Success)
        release.complete(Unit)
        val result = refresh.await()

        assertEquals(
            MalRefreshFailureReason.ACCOUNT_SESSION_CHANGED,
            (result as MalRefreshResult.Failure).reason,
        )
        assertNull(accounts.token("local-1"))
    }

    @Test
    fun `transient network failure preserves valid token bundle`() = runTest {
        val accounts = FakeMalAccountCredentialStore()
        val original = MalTokenSet(
            accessToken = "valid-access",
            refreshToken = "valid-refresh",
            expiresAtEpochMillis = 20_000L,
        )
        accounts.seed(tokenSet = original)
        val service = FakeMalOAuthTokenService().apply {
            refreshResult = MalOAuthTransportResult.Failure(
                MalOAuthTransportFailureReason.TIMEOUT
            )
        }

        val result = coordinator(accounts, service).refresh("local-1", 1L)

        assertEquals(
            MalRefreshFailureReason.TIMEOUT,
            (result as MalRefreshResult.Failure).reason,
        )
        assertEquals(original, accounts.token("local-1"))
        assertEquals(0, accounts.logoutCalls)
    }

    @Test
    fun `newer generation is reused without second refresh request`() = runTest {
        val accounts = FakeMalAccountCredentialStore()
        accounts.seed(generation = 2L)
        accounts.replaceTokensIfGeneration(
            "local-1",
            expectedGeneration = 2L,
            tokens = MalTokenSet("already-refreshed", "rotated", 30_000L),
            requireExistingCredentials = true,
        )
        val service = FakeMalOAuthTokenService()

        val result = coordinator(accounts, service).refresh("local-1", observedGeneration = 2L)

        assertTrue(result is MalRefreshResult.Success)
        assertEquals(3L, (result as MalRefreshResult.Success).tokenGeneration)
        assertEquals("already-refreshed", result.tokens.accessToken)
        assertEquals(0, service.refreshCalls)
    }

    private fun kotlinx.coroutines.test.TestScope.coordinator(
        accounts: FakeMalAccountCredentialStore,
        service: FakeMalOAuthTokenService,
    ): MalRefreshCoordinator = MalRefreshCoordinator(
        configurationProvider = FakeMalOAuthConfigurationSource(),
        accountStore = accounts,
        tokenService = service,
        scope = this,
    )
}
