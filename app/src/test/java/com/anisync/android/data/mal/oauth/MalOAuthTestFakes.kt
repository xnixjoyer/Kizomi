package com.anisync.android.data.mal.oauth

import com.anisync.android.data.mal.account.MalAccount
import com.anisync.android.data.mal.account.MalAccountCredentialStore
import com.anisync.android.data.mal.account.MalAccountFailureReason
import com.anisync.android.data.mal.account.MalAccountProfile
import com.anisync.android.data.mal.account.MalAccountProvider
import com.anisync.android.data.mal.account.MalAccountResult
import com.anisync.android.data.mal.account.MalTokenSet
import com.anisync.android.data.mal.account.MalTokenStatus
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger

internal class MutableMalOAuthClock(var now: Long = 1_000L) : MalOAuthClock {
    override fun nowEpochMillis(): Long = now
}

internal class FakeMalOAuthConfigurationSource(
    configured: Boolean = true,
    environment: MalOAuthEnvironment = MalOAuthEnvironment.DEBUG,
) : MalOAuthConfigurationSource {
    override val capability: MalOAuthCapability = if (configured) {
        MalOAuthCapability.Configured(
            MalOAuthConfiguration(
                environment = environment,
                clientId = "public-client-id-fixture",
                redirectUri = URI(environment.redirectUri),
                pkceMethod = MalPkceMethod.PLAIN,
            )
        )
    } else {
        MalOAuthCapability.Unavailable(
            environment = environment,
            reason = MalOAuthUnavailableReason.MISSING_CLIENT_ID,
        )
    }

    override val isLoginConfigured: Boolean
        get() = capability is MalOAuthCapability.Configured

    override fun diagnostic(): MalOAuthDiagnostic =
        MalOAuthConfigurationValidator.diagnostic(capability)
}

internal class FakeMalOAuthSessionStore : MalOAuthSessionStore {
    var pending: MalOAuthSession? = null
    var consumedStateHash: String? = null
    var initializationReset = false
    var failWrites = false

    override fun save(session: MalOAuthSession): MalOAuthSessionStoreResult<Unit> {
        if (failWrites) {
            return MalOAuthSessionStoreResult.Failure(
                MalOAuthSessionStoreFailureReason.WRITE_FAILED,
                session.sessionId,
            )
        }
        pending = session
        return MalOAuthSessionStoreResult.Success(Unit)
    }

    override fun read(): MalOAuthSessionStoreResult<MalOAuthSession?> =
        MalOAuthSessionStoreResult.Success(pending)

    override fun consume(
        sessionId: String,
        stateHash: String,
    ): MalOAuthSessionStoreResult<Unit> {
        if (pending?.sessionId != sessionId) {
            return MalOAuthSessionStoreResult.Failure(
                MalOAuthSessionStoreFailureReason.SESSION_CHANGED,
                sessionId,
            )
        }
        pending = null
        consumedStateHash = stateHash
        return MalOAuthSessionStoreResult.Success(Unit)
    }

    override fun clearPending(sessionId: String?): MalOAuthSessionStoreResult<Unit> {
        if (sessionId != null && pending != null && pending?.sessionId != sessionId) {
            return MalOAuthSessionStoreResult.Failure(
                MalOAuthSessionStoreFailureReason.SESSION_CHANGED,
                sessionId,
            )
        }
        pending = null
        return MalOAuthSessionStoreResult.Success(Unit)
    }

    override fun lastConsumedStateHash(): MalOAuthSessionStoreResult<String?> =
        MalOAuthSessionStoreResult.Success(consumedStateHash)

    override fun consumeInitializationReset(): Boolean = initializationReset.also {
        initializationReset = false
    }
}

internal class FakeMalOAuthTokenService : MalOAuthTokenService {
    var exchangeResult: MalOAuthTransportResult<MalOAuthTokenPayload> = successPayload()
    var refreshResult: MalOAuthTransportResult<MalOAuthTokenPayload> = successPayload(
        access = "refreshed-access",
        refresh = "rotated-refresh",
    )
    var exchangeCalls = 0
    var refreshCalls = 0
    var beforeRefresh: (suspend () -> Unit)? = null

    override suspend fun exchangeAuthorizationCode(
        configuration: MalOAuthConfiguration,
        code: String,
        verifier: String,
    ): MalOAuthTransportResult<MalOAuthTokenPayload> {
        exchangeCalls++
        return exchangeResult
    }

    override suspend fun refresh(
        configuration: MalOAuthConfiguration,
        refreshToken: String,
    ): MalOAuthTransportResult<MalOAuthTokenPayload> {
        refreshCalls++
        beforeRefresh?.invoke()
        return refreshResult
    }

    companion object {
        fun successPayload(
            access: String = "access-fixture",
            refresh: String? = "refresh-fixture",
            expiresAt: Long = 50_000L,
        ): MalOAuthTransportResult<MalOAuthTokenPayload> = MalOAuthTransportResult.Success(
            MalOAuthTokenPayload(
                accessToken = access,
                refreshToken = refresh,
                expiresAtEpochMillis = expiresAt,
                scopes = setOf("read", "write"),
                tokenType = "Bearer",
            )
        )
    }
}

internal class FakeMalAccountCredentialStore : MalAccountCredentialStore {
    private val nextId = AtomicInteger(1)
    private val accounts = linkedMapOf<String, MalAccount>()
    private val tokens = linkedMapOf<String, MalTokenSet>()

    var logoutCalls = 0
    var replaceCalls = 0

    fun seed(
        localAccountId: String = "local-1",
        generation: Long = 1L,
        tokenSet: MalTokenSet = MalTokenSet(
            accessToken = "old-access",
            refreshToken = "old-refresh",
            expiresAtEpochMillis = 20_000L,
            scopes = setOf("read"),
        ),
        active: Boolean = true,
        status: MalTokenStatus = MalTokenStatus.ACTIVE,
    ): MalAccount {
        val account = MalAccount(
            localAccountId = localAccountId,
            provider = MalAccountProvider.MYANIMELIST,
            profile = MalAccountProfile(),
            tokenGeneration = generation,
            tokenExpiresAtEpochMillis = tokenSet.expiresAtEpochMillis,
            scopes = tokenSet.scopes,
            tokenStatus = status,
            isActive = active,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )
        accounts[localAccountId] = account
        tokens[localAccountId] = tokenSet
        return account
    }

    override suspend fun createAccount(
        profile: MalAccountProfile,
        tokens: MalTokenSet,
        makeActive: Boolean,
    ): MalAccountResult<MalAccount> {
        val id = "local-${nextId.getAndIncrement()}"
        if (makeActive) deactivateAll()
        val account = MalAccount(
            localAccountId = id,
            provider = MalAccountProvider.MYANIMELIST,
            profile = profile,
            tokenGeneration = 1L,
            tokenExpiresAtEpochMillis = tokens.expiresAtEpochMillis,
            scopes = tokens.scopes,
            tokenStatus = MalTokenStatus.ACTIVE,
            isActive = makeActive,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )
        accounts[id] = account
        this.tokens[id] = tokens
        return MalAccountResult.Success(account)
    }

    override suspend fun getAccount(localAccountId: String): MalAccountResult<MalAccount> =
        accounts[localAccountId]?.let { MalAccountResult.Success(it) }
            ?: MalAccountResult.Failure(
                MalAccountFailureReason.ACCOUNT_NOT_FOUND,
                localAccountId,
            )

    override suspend fun listAccounts(): List<MalAccount> = accounts.values.toList()

    override suspend fun activeAccount(): MalAccount? = accounts.values.firstOrNull { it.isActive }

    override suspend fun selectActive(localAccountId: String?): MalAccountResult<MalAccount?> {
        if (localAccountId == null) {
            deactivateAll()
            return MalAccountResult.Success(null)
        }
        val selected = accounts[localAccountId]
            ?: return MalAccountResult.Failure(
                MalAccountFailureReason.ACCOUNT_NOT_FOUND,
                localAccountId,
            )
        deactivateAll()
        val active = selected.copy(isActive = true)
        accounts[localAccountId] = active
        return MalAccountResult.Success(active)
    }

    override suspend fun replaceTokens(
        localAccountId: String,
        tokens: MalTokenSet,
    ): MalAccountResult<MalAccount> {
        val current = accounts[localAccountId]
            ?: return MalAccountResult.Failure(
                MalAccountFailureReason.ACCOUNT_NOT_FOUND,
                localAccountId,
            )
        return replaceTokensIfGeneration(
            localAccountId,
            current.tokenGeneration,
            tokens,
            requireExistingCredentials = false,
        )
    }

    override suspend fun replaceTokensIfGeneration(
        localAccountId: String,
        expectedGeneration: Long,
        tokens: MalTokenSet,
        requireExistingCredentials: Boolean,
    ): MalAccountResult<MalAccount> {
        replaceCalls++
        val current = accounts[localAccountId]
            ?: return MalAccountResult.Failure(
                MalAccountFailureReason.ACCOUNT_NOT_FOUND,
                localAccountId,
            )
        if (current.tokenGeneration != expectedGeneration ||
            (requireExistingCredentials && this.tokens[localAccountId] == null)
        ) {
            return MalAccountResult.Failure(
                MalAccountFailureReason.ACCOUNT_SESSION_CHANGED,
                localAccountId,
                expectedGeneration,
            )
        }
        val updated = current.copy(
            tokenGeneration = expectedGeneration + 1L,
            tokenExpiresAtEpochMillis = tokens.expiresAtEpochMillis,
            scopes = tokens.scopes,
            tokenStatus = MalTokenStatus.ACTIVE,
        )
        accounts[localAccountId] = updated
        this.tokens[localAccountId] = tokens
        return MalAccountResult.Success(updated)
    }

    override suspend fun readTokens(localAccountId: String): MalAccountResult<MalTokenSet> =
        tokens[localAccountId]?.let { MalAccountResult.Success(it) }
            ?: MalAccountResult.Failure(
                MalAccountFailureReason.TOKEN_MISSING,
                localAccountId,
            )

    override suspend fun logout(localAccountId: String): MalAccountResult<MalAccount> {
        logoutCalls++
        val current = accounts[localAccountId]
            ?: return MalAccountResult.Failure(
                MalAccountFailureReason.ACCOUNT_NOT_FOUND,
                localAccountId,
            )
        tokens.remove(localAccountId)
        val updated = current.copy(
            tokenStatus = MalTokenStatus.MISSING,
            isActive = false,
            tokenExpiresAtEpochMillis = null,
            scopes = emptySet(),
        )
        accounts[localAccountId] = updated
        return MalAccountResult.Success(updated)
    }

    fun token(localAccountId: String): MalTokenSet? = tokens[localAccountId]
    fun account(localAccountId: String): MalAccount? = accounts[localAccountId]

    private fun deactivateAll() {
        accounts.replaceAll { _, account -> account.copy(isActive = false) }
    }
}
