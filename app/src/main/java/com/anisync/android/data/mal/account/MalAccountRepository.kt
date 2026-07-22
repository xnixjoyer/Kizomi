package com.anisync.android.data.mal.account

import androidx.room.withTransaction
import com.anisync.android.data.local.AppDatabase
import com.anisync.android.data.local.dao.MalAccountDao
import com.anisync.android.data.local.entity.MalAccountEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

fun interface MalAccountClock {
    fun nowEpochMillis(): Long
}

fun interface MalAccountIdGenerator {
    fun newLocalAccountId(): String
}

@Singleton
class SystemMalAccountClock @Inject constructor() : MalAccountClock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}

@Singleton
class UuidMalAccountIdGenerator @Inject constructor() : MalAccountIdGenerator {
    override fun newLocalAccountId(): String = UUID.randomUUID().toString()
}

@Singleton
interface MalAccountCredentialStore {
    suspend fun createAccount(profile: MalAccountProfile, tokens: MalTokenSet, makeActive: Boolean = false): MalAccountResult<MalAccount>
    suspend fun getAccount(localAccountId: String): MalAccountResult<MalAccount>
    suspend fun listAccounts(): List<MalAccount>
    suspend fun activeAccount(): MalAccount?
    suspend fun selectActive(localAccountId: String?): MalAccountResult<MalAccount?>
    suspend fun replaceTokens(localAccountId: String, tokens: MalTokenSet): MalAccountResult<MalAccount>
    suspend fun replaceTokensIfGeneration(
        localAccountId: String,
        expectedGeneration: Long,
        tokens: MalTokenSet,
        requireExistingCredentials: Boolean = true,
    ): MalAccountResult<MalAccount>
    suspend fun readTokens(localAccountId: String): MalAccountResult<MalTokenSet>
    suspend fun logout(localAccountId: String): MalAccountResult<MalAccount>
}

class MalAccountRepository @Inject constructor(
    private val database: AppDatabase,
    private val dao: MalAccountDao,
    private val vault: MalTokenVault,
    private val clock: MalAccountClock,
    private val idGenerator: MalAccountIdGenerator,
) : MalAccountCredentialStore {
    override suspend fun createAccount(
        profile: MalAccountProfile,
        tokens: MalTokenSet,
        makeActive: Boolean,
    ): MalAccountResult<MalAccount> {
        val localAccountId = idGenerator.newLocalAccountId()
        val now = clock.nowEpochMillis()
        val generation = 1L
        val write = vault.write(localAccountId, generation, tokens)
        val reference = (write as? MalTokenVaultResult.Success)?.value
            ?: return vaultFailure(localAccountId, generation, write)

        val entity = MalAccountEntity(
            localAccountId = localAccountId,
            provider = MalAccountProvider.MYANIMELIST.name,
            malUserId = profile.malUserId,
            username = profile.username.normalizedNullable(),
            displayName = profile.displayName.normalizedNullable(),
            avatarUrl = profile.avatarUrl.normalizedNullable(),
            accessTokenRef = reference,
            refreshTokenRef = reference.takeIf { tokens.refreshToken != null },
            tokenGeneration = generation,
            tokenExpiresAtEpochMillis = tokens.expiresAtEpochMillis,
            scopes = tokens.normalizedScopes().joinToString(" "),
            tokenStatus = statusFor(tokens, now).name,
            isActive = makeActive,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )

        return try {
            database.withTransaction {
                if (makeActive) dao.clearActive(now)
                dao.insert(entity)
            }
            MalAccountResult.Success(entity.toDomain())
        } catch (_: Throwable) {
            vault.delete(reference)
            MalAccountResult.Failure(
                reason = MalAccountFailureReason.DATABASE_WRITE_FAILED,
                localAccountId = localAccountId,
                generation = generation,
            )
        }
    }

    suspend fun updateProfile(
        localAccountId: String,
        profile: MalAccountProfile,
    ): MalAccountResult<MalAccount> {
        val current = dao.get(localAccountId)
            ?: return notFound(localAccountId)
        val updated = current.copy(
            malUserId = profile.malUserId,
            username = profile.username.normalizedNullable(),
            displayName = profile.displayName.normalizedNullable(),
            avatarUrl = profile.avatarUrl.normalizedNullable(),
            updatedAtEpochMillis = clock.nowEpochMillis(),
        )
        return updateEntity(updated)
    }

    override suspend fun getAccount(localAccountId: String): MalAccountResult<MalAccount> =
        dao.get(localAccountId)
            ?.let { MalAccountResult.Success(it.toDomain()) }
            ?: notFound(localAccountId)

    override suspend fun listAccounts(): List<MalAccount> =
        dao.list().map { it.toDomain() }

    override suspend fun activeAccount(): MalAccount? =
        dao.active()?.toDomain()

    override suspend fun selectActive(localAccountId: String?): MalAccountResult<MalAccount?> {
        if (localAccountId == null) {
            val selected = dao.selectActive(null, clock.nowEpochMillis())
            return if (selected) {
                MalAccountResult.Success(null)
            } else {
                MalAccountResult.Failure(MalAccountFailureReason.DATABASE_WRITE_FAILED)
            }
        }

        val current = dao.get(localAccountId)
            ?: return notFound(localAccountId)
        val status = current.statusOrCorrupt()
        if (current.accessTokenRef == null || status in NON_ACTIVATABLE_STATUSES) {
            return MalAccountResult.Failure(
                reason = MalAccountFailureReason.ACCOUNT_NOT_ACTIVATABLE,
                localAccountId = localAccountId,
                generation = current.tokenGeneration,
            )
        }

        return try {
            val selected = dao.selectActive(localAccountId, clock.nowEpochMillis())
            if (!selected) {
                MalAccountResult.Failure(
                    reason = MalAccountFailureReason.DATABASE_WRITE_FAILED,
                    localAccountId = localAccountId,
                )
            } else {
                MalAccountResult.Success(dao.get(localAccountId)?.toDomain())
            }
        } catch (_: Throwable) {
            MalAccountResult.Failure(
                reason = MalAccountFailureReason.DATABASE_WRITE_FAILED,
                localAccountId = localAccountId,
            )
        }
    }

    suspend fun deactivateAccount(localAccountId: String): MalAccountResult<MalAccount> {
        val current = dao.get(localAccountId)
            ?: return notFound(localAccountId)
        return updateEntity(
            current.copy(
                isActive = false,
                updatedAtEpochMillis = clock.nowEpochMillis(),
            )
        )
    }

    override suspend fun replaceTokens(
        localAccountId: String,
        tokens: MalTokenSet,
    ): MalAccountResult<MalAccount> {
        val current = dao.get(localAccountId)
            ?: return notFound(localAccountId)
        return replaceTokensIfGeneration(
            localAccountId = localAccountId,
            expectedGeneration = current.tokenGeneration,
            tokens = tokens,
            requireExistingCredentials = false,
        )
    }

    override suspend fun replaceTokensIfGeneration(
        localAccountId: String,
        expectedGeneration: Long,
        tokens: MalTokenSet,
        requireExistingCredentials: Boolean,
    ): MalAccountResult<MalAccount> {
        val before = dao.get(localAccountId)
            ?: return notFound(localAccountId)
        if (before.tokenGeneration != expectedGeneration ||
            (requireExistingCredentials && before.accessTokenRef == null)
        ) {
            return sessionChanged(localAccountId, expectedGeneration)
        }

        val nextGeneration = expectedGeneration + 1L
        val write = vault.write(localAccountId, nextGeneration, tokens)
        val nextReference = (write as? MalTokenVaultResult.Success)?.value
            ?: return vaultFailure(localAccountId, nextGeneration, write)

        val now = clock.nowEpochMillis()
        var committed: MalAccountEntity? = null
        var previousReferences: Set<String> = emptySet()
        return try {
            database.withTransaction {
                val current = dao.get(localAccountId)
                    ?: error("MAL account disappeared during token replacement")
                check(current.tokenGeneration == expectedGeneration) {
                    "MAL account token generation changed"
                }
                check(!requireExistingCredentials || current.accessTokenRef != null) {
                    "MAL account credentials were removed"
                }
                previousReferences = current.references()
                val updated = current.copy(
                    accessTokenRef = nextReference,
                    refreshTokenRef = nextReference.takeIf { tokens.refreshToken != null },
                    tokenGeneration = nextGeneration,
                    tokenExpiresAtEpochMillis = tokens.expiresAtEpochMillis,
                    scopes = tokens.normalizedScopes().joinToString(" "),
                    tokenStatus = statusFor(tokens, now).name,
                    updatedAtEpochMillis = now,
                )
                check(dao.update(updated) == 1) {
                    "MAL account disappeared during token replacement"
                }
                committed = updated
            }
            previousReferences.forEach { oldReference ->
                if (oldReference != nextReference) vault.delete(oldReference)
            }
            MalAccountResult.Success(checkNotNull(committed).toDomain())
        } catch (_: Throwable) {
            vault.delete(nextReference)
            val latest = runCatching { dao.get(localAccountId) }.getOrNull()
            if (latest == null) {
                notFound(localAccountId)
            } else if (latest.tokenGeneration != expectedGeneration ||
                (requireExistingCredentials && latest.accessTokenRef == null)
            ) {
                sessionChanged(localAccountId, expectedGeneration)
            } else {
                MalAccountResult.Failure(
                    reason = MalAccountFailureReason.DATABASE_WRITE_FAILED,
                    localAccountId = localAccountId,
                    generation = nextGeneration,
                )
            }
        }
    }

    override suspend fun readTokens(localAccountId: String): MalAccountResult<MalTokenSet> {
        val current = dao.get(localAccountId)
            ?: return notFound(localAccountId)
        val reference = current.accessTokenRef
            ?: return MalAccountResult.Failure(
                reason = MalAccountFailureReason.TOKEN_MISSING,
                localAccountId = localAccountId,
                generation = current.tokenGeneration,
            )

        return when (val read = vault.read(reference)) {
            is MalTokenVaultResult.Success -> MalAccountResult.Success(read.value)
            is MalTokenVaultResult.Failure -> {
                val status = when (read.reason) {
                    MalTokenVaultFailureReason.MISSING_ENTRY -> MalTokenStatus.MISSING
                    MalTokenVaultFailureReason.CORRUPT_ENTRY -> MalTokenStatus.CORRUPT
                    MalTokenVaultFailureReason.KEYSTORE_RESET -> MalTokenStatus.KEYSTORE_RESET
                    else -> null
                }
                if (status != null) markUnavailable(current, status)
                vaultFailure(localAccountId, current.tokenGeneration, read)
            }
        }
    }

    /**
     * Local logout: remove credentials and active selection, retain metadata for an explicit re-auth.
     * This does not claim or attempt a server-side revoke.
     */
    override suspend fun logout(localAccountId: String): MalAccountResult<MalAccount> =
        clearTokens(localAccountId, MalTokenStatus.MISSING)

    /** Removes credentials while preserving account metadata. */
    suspend fun deleteTokens(localAccountId: String): MalAccountResult<MalAccount> =
        clearTokens(localAccountId, MalTokenStatus.MISSING)

    /** Removes local metadata and credentials. Server-side revoke is deliberately a later concern. */
    suspend fun removeLocal(localAccountId: String): MalAccountResult<Unit> {
        val current = dao.get(localAccountId)
            ?: return notFound(localAccountId)
        val deletion = vault.deleteAccount(localAccountId)
        if (deletion is MalTokenVaultResult.Failure &&
            deletion.reason != MalTokenVaultFailureReason.KEYSTORE_RESET
        ) {
            return vaultFailure(localAccountId, current.tokenGeneration, deletion)
        }
        return try {
            if (dao.delete(localAccountId) == 1) {
                MalAccountResult.Success(Unit)
            } else {
                notFound(localAccountId)
            }
        } catch (_: Throwable) {
            MalAccountResult.Failure(
                reason = MalAccountFailureReason.DATABASE_WRITE_FAILED,
                localAccountId = localAccountId,
            )
        }
    }

    suspend fun markExpired(localAccountId: String): MalAccountResult<MalAccount> {
        val current = dao.get(localAccountId)
            ?: return notFound(localAccountId)
        return updateEntity(
            current.copy(
                tokenStatus = MalTokenStatus.EXPIRED.name,
                updatedAtEpochMillis = clock.nowEpochMillis(),
            )
        )
    }

    /**
     * Reconciles process-death leftovers and an invalidated MAL-only keystore namespace.
     * AniList account preferences and keys are never touched.
     */
    suspend fun reconcileVaultState(): MalAccountResult<Unit> {
        if (vault.consumeInitializationReset()) {
            return markAllAfterKeystoreReset()
        }

        val accounts = dao.list()
        val referenced = accounts.flatMap { it.references() }.toSet()
        when (val references = vault.references()) {
            is MalTokenVaultResult.Failure -> {
                return if (references.reason == MalTokenVaultFailureReason.KEYSTORE_RESET) {
                    markAllAfterKeystoreReset()
                } else {
                    MalAccountResult.Failure(MalAccountFailureReason.VAULT_DELETE_FAILED)
                }
            }
            is MalTokenVaultResult.Success -> {
                references.value.minus(referenced).forEach(vault::delete)
            }
        }

        accounts.forEach { account ->
            val reference = account.accessTokenRef ?: return@forEach
            when (val read = vault.read(reference)) {
                is MalTokenVaultResult.Success -> Unit
                is MalTokenVaultResult.Failure -> {
                    val status = when (read.reason) {
                        MalTokenVaultFailureReason.MISSING_ENTRY -> MalTokenStatus.MISSING
                        MalTokenVaultFailureReason.CORRUPT_ENTRY -> MalTokenStatus.CORRUPT
                        MalTokenVaultFailureReason.KEYSTORE_RESET -> MalTokenStatus.KEYSTORE_RESET
                        else -> return@forEach
                    }
                    markUnavailable(account, status)
                }
            }
        }
        return MalAccountResult.Success(Unit)
    }

    private suspend fun clearTokens(
        localAccountId: String,
        status: MalTokenStatus,
    ): MalAccountResult<MalAccount> {
        val current = dao.get(localAccountId)
            ?: return notFound(localAccountId)
        val deletion = vault.deleteAccount(localAccountId)
        if (deletion is MalTokenVaultResult.Failure &&
            deletion.reason != MalTokenVaultFailureReason.KEYSTORE_RESET
        ) {
            return vaultFailure(localAccountId, current.tokenGeneration, deletion)
        }
        return updateEntity(
            current.copy(
                accessTokenRef = null,
                refreshTokenRef = null,
                tokenExpiresAtEpochMillis = null,
                scopes = "",
                tokenStatus = if (deletion is MalTokenVaultResult.Failure) {
                    MalTokenStatus.KEYSTORE_RESET.name
                } else {
                    status.name
                },
                isActive = false,
                updatedAtEpochMillis = clock.nowEpochMillis(),
            )
        )
    }

    private suspend fun markAllAfterKeystoreReset(): MalAccountResult<Unit> {
        return try {
            database.withTransaction {
                dao.list().forEach { account ->
                    dao.update(
                        account.copy(
                            accessTokenRef = null,
                            refreshTokenRef = null,
                            tokenExpiresAtEpochMillis = null,
                            scopes = "",
                            tokenStatus = MalTokenStatus.KEYSTORE_RESET.name,
                            isActive = false,
                            updatedAtEpochMillis = clock.nowEpochMillis(),
                        )
                    )
                }
            }
            MalAccountResult.Success(Unit)
        } catch (_: Throwable) {
            MalAccountResult.Failure(MalAccountFailureReason.DATABASE_WRITE_FAILED)
        }
    }

    private suspend fun markUnavailable(
        current: MalAccountEntity,
        status: MalTokenStatus,
    ) {
        runCatching {
            dao.update(
                current.copy(
                    accessTokenRef = null,
                    refreshTokenRef = null,
                    tokenStatus = status.name,
                    isActive = false,
                    updatedAtEpochMillis = clock.nowEpochMillis(),
                )
            )
        }
    }

    private suspend fun updateEntity(updated: MalAccountEntity): MalAccountResult<MalAccount> =
        try {
            if (dao.update(updated) == 1) {
                MalAccountResult.Success(updated.toDomain())
            } else {
                notFound(updated.localAccountId)
            }
        } catch (_: Throwable) {
            MalAccountResult.Failure(
                reason = MalAccountFailureReason.DATABASE_WRITE_FAILED,
                localAccountId = updated.localAccountId,
                generation = updated.tokenGeneration,
            )
        }

    private fun statusFor(tokens: MalTokenSet, now: Long): MalTokenStatus =
        if (tokens.expiresAtEpochMillis != null && tokens.expiresAtEpochMillis <= now) {
            MalTokenStatus.EXPIRED
        } else {
            MalTokenStatus.ACTIVE
        }

    private fun vaultFailure(
        localAccountId: String,
        generation: Long,
        result: MalTokenVaultResult<*>,
    ): MalAccountResult.Failure {
        val reason = when ((result as? MalTokenVaultResult.Failure)?.reason) {
            MalTokenVaultFailureReason.INVALID_INPUT -> MalAccountFailureReason.INVALID_TOKEN_BUNDLE
            MalTokenVaultFailureReason.WRITE_FAILED -> MalAccountFailureReason.VAULT_WRITE_FAILED
            MalTokenVaultFailureReason.MISSING_ENTRY -> MalAccountFailureReason.TOKEN_MISSING
            MalTokenVaultFailureReason.CORRUPT_ENTRY -> MalAccountFailureReason.TOKEN_CORRUPT
            MalTokenVaultFailureReason.DELETE_FAILED -> MalAccountFailureReason.VAULT_DELETE_FAILED
            MalTokenVaultFailureReason.KEYSTORE_RESET -> MalAccountFailureReason.KEYSTORE_RESET
            MalTokenVaultFailureReason.READ_FAILED -> MalAccountFailureReason.TOKEN_CORRUPT
            null -> MalAccountFailureReason.VAULT_WRITE_FAILED
        }
        return MalAccountResult.Failure(
            reason = reason,
            localAccountId = localAccountId,
            generation = generation,
        )
    }

    private fun sessionChanged(
        localAccountId: String,
        expectedGeneration: Long,
    ): MalAccountResult.Failure = MalAccountResult.Failure(
        reason = MalAccountFailureReason.ACCOUNT_SESSION_CHANGED,
        localAccountId = localAccountId,
        generation = expectedGeneration,
    )

    private fun notFound(localAccountId: String): MalAccountResult.Failure =
        MalAccountResult.Failure(
            reason = MalAccountFailureReason.ACCOUNT_NOT_FOUND,
            localAccountId = localAccountId,
        )

    private fun MalAccountEntity.toDomain(): MalAccount =
        MalAccount(
            localAccountId = localAccountId,
            provider = runCatching { MalAccountProvider.valueOf(provider) }
                .getOrDefault(MalAccountProvider.MYANIMELIST),
            profile = MalAccountProfile(
                malUserId = malUserId,
                username = username,
                displayName = displayName,
                avatarUrl = avatarUrl,
            ),
            tokenGeneration = tokenGeneration,
            tokenExpiresAtEpochMillis = tokenExpiresAtEpochMillis,
            scopes = scopes.split(' ').map(String::trim).filter(String::isNotEmpty).toSet(),
            tokenStatus = statusOrCorrupt(),
            isActive = isActive,
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )

    private fun MalAccountEntity.statusOrCorrupt(): MalTokenStatus =
        runCatching { MalTokenStatus.valueOf(tokenStatus) }.getOrDefault(MalTokenStatus.CORRUPT)

    private fun MalAccountEntity.references(): Set<String> =
        setOfNotNull(accessTokenRef, refreshTokenRef)

    private fun String?.normalizedNullable(): String? =
        this?.trim()?.takeIf(String::isNotEmpty)

    private companion object {
        val NON_ACTIVATABLE_STATUSES = setOf(
            MalTokenStatus.MISSING,
            MalTokenStatus.CORRUPT,
            MalTokenStatus.KEYSTORE_RESET,
        )
    }
}
