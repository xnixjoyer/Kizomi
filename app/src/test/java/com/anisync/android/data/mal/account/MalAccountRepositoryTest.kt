package com.anisync.android.data.mal.account

import android.content.Context
import androidx.room.Room
import com.anisync.android.data.local.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MalAccountRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var vault: FakeMalTokenVault
    private lateinit var repository: MalAccountRepository
    private lateinit var databaseFile: File
    private var now = 1_000L
    private var nextId = 0

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        databaseFile = context.getDatabasePath("mal-account-repository-test.db")
        context.deleteDatabase(databaseFile.name)
        vault = FakeMalTokenVault()
        database = openDatabase()
        repository = newRepository(database)
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
        context.deleteDatabase(databaseFile.name)
    }

    @Test
    fun `creates multiple independent accounts and selects exactly one active account`() = runTest {
        val first = create(username = "first", makeActive = true)
        val second = create(username = "second", makeActive = false)

        assertEquals(2, repository.listAccounts().size)
        assertEquals(first.localAccountId, repository.activeAccount()?.localAccountId)

        val selected = repository.selectActive(second.localAccountId)
        assertTrue(selected is MalAccountResult.Success)
        assertEquals(second.localAccountId, repository.activeAccount()?.localAccountId)
        assertEquals(1, repository.listAccounts().count { it.isActive })
    }

    @Test
    fun `repository recreation restores accounts active selection and encrypted pointer`() = runTest {
        val account = create(username = "persisted", makeActive = true)
        val before = repository.readTokens(account.localAccountId)
        assertTrue(before is MalAccountResult.Success)

        database.close()
        database = openDatabase()
        repository = newRepository(database)

        assertEquals(account.localAccountId, repository.activeAccount()?.localAccountId)
        assertEquals(1, repository.listAccounts().size)
        val restored = repository.readTokens(account.localAccountId)
        assertTrue(restored is MalAccountResult.Success)
        restored as MalAccountResult.Success
        assertEquals("access-persisted", restored.value.accessToken)
        assertEquals("refresh-persisted", restored.value.refreshToken)
    }

    @Test
    fun `token replacement advances generation atomically and removes old bundle`() = runTest {
        val account = create(username = "replace")
        val oldReferences = vault.referencesSnapshot()

        val replaced = repository.replaceTokens(
            account.localAccountId,
            MalTokenSet(
                accessToken = "access-new",
                refreshToken = null,
                expiresAtEpochMillis = 9_000L,
                scopes = setOf("write", "read"),
            ),
        )

        assertTrue(replaced is MalAccountResult.Success)
        replaced as MalAccountResult.Success
        assertEquals(2L, replaced.value.tokenGeneration)
        assertEquals(setOf("read", "write"), replaced.value.scopes)
        assertTrue(oldReferences.intersect(vault.referencesSnapshot()).isEmpty())
        assertEquals(1, vault.referencesSnapshot().size)

        val read = repository.readTokens(account.localAccountId) as MalAccountResult.Success
        assertEquals("access-new", read.value.accessToken)
        assertNull(read.value.refreshToken)
    }

    @Test
    fun `failed Room pointer swap deletes new generation and retains old generation`() = runTest {
        val account = create(username = "failure-window")
        val oldReferences = vault.referencesSnapshot()
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_mal_token_pointer_update
            BEFORE UPDATE OF accessTokenRef ON mal_accounts
            BEGIN
                SELECT RAISE(ABORT, 'forced token pointer failure');
            END
            """.trimIndent()
        )

        val result = repository.replaceTokens(
            account.localAccountId,
            MalTokenSet(accessToken = "access-uncommitted", refreshToken = "refresh-uncommitted"),
        )

        assertEquals(
            MalAccountFailureReason.DATABASE_WRITE_FAILED,
            (result as MalAccountResult.Failure).reason,
        )
        assertEquals(oldReferences, vault.referencesSnapshot())
        assertFalse(vault.containsPlaintext("access-uncommitted"))
        assertFalse(vault.containsPlaintext("refresh-uncommitted"))
    }

    @Test
    fun `deactivate retains tokens logout removes tokens and local remove deletes metadata`() = runTest {
        val account = create(username = "semantics", makeActive = true)

        val deactivated = repository.deactivateAccount(account.localAccountId)
        assertTrue(deactivated is MalAccountResult.Success)
        assertTrue(repository.readTokens(account.localAccountId) is MalAccountResult.Success)
        assertFalse((deactivated as MalAccountResult.Success).value.isActive)

        repository.selectActive(account.localAccountId)
        val loggedOut = repository.logout(account.localAccountId)
        assertTrue(loggedOut is MalAccountResult.Success)
        loggedOut as MalAccountResult.Success
        assertEquals(MalTokenStatus.MISSING, loggedOut.value.tokenStatus)
        assertFalse(loggedOut.value.isActive)
        assertEquals(
            MalAccountFailureReason.TOKEN_MISSING,
            (repository.readTokens(account.localAccountId) as MalAccountResult.Failure).reason,
        )
        assertTrue(repository.getAccount(account.localAccountId) is MalAccountResult.Success)

        val removed = repository.removeLocal(account.localAccountId)
        assertTrue(removed is MalAccountResult.Success)
        assertEquals(
            MalAccountFailureReason.ACCOUNT_NOT_FOUND,
            (repository.getAccount(account.localAccountId) as MalAccountResult.Failure).reason,
        )
    }

    @Test
    fun `missing refresh token and expired access token remain explicit metadata states`() = runTest {
        val missingRefresh = repository.createAccount(
            profile = MalAccountProfile(username = "no-refresh"),
            tokens = MalTokenSet(
                accessToken = "access-no-refresh",
                refreshToken = null,
                expiresAtEpochMillis = now + 10_000L,
            ),
        ) as MalAccountResult.Success
        val missingRefreshTokens =
            repository.readTokens(missingRefresh.value.localAccountId) as MalAccountResult.Success
        assertNull(missingRefreshTokens.value.refreshToken)

        val expired = repository.createAccount(
            profile = MalAccountProfile(username = "expired"),
            tokens = MalTokenSet(
                accessToken = "access-expired",
                refreshToken = "refresh-expired",
                expiresAtEpochMillis = now,
            ),
        ) as MalAccountResult.Success
        assertEquals(MalTokenStatus.EXPIRED, expired.value.tokenStatus)
    }

    @Test
    fun `missing corrupt and keystore reset vault states are typed and deactivate accounts`() = runTest {
        val missing = create(username = "missing", makeActive = true)
        vault.dropAll()
        val missingResult = repository.readTokens(missing.localAccountId)
        assertEquals(
            MalAccountFailureReason.TOKEN_MISSING,
            (missingResult as MalAccountResult.Failure).reason,
        )
        assertEquals(MalTokenStatus.MISSING, account(missing.localAccountId).tokenStatus)
        assertFalse(account(missing.localAccountId).isActive)

        val corrupt = create(username = "corrupt")
        vault.corrupt(corrupt.localAccountId)
        val corruptResult = repository.readTokens(corrupt.localAccountId)
        assertEquals(
            MalAccountFailureReason.TOKEN_CORRUPT,
            (corruptResult as MalAccountResult.Failure).reason,
        )
        assertEquals(MalTokenStatus.CORRUPT, account(corrupt.localAccountId).tokenStatus)

        val reset = create(username = "reset", makeActive = true)
        vault.simulateInitializationReset()
        val reconciliation = repository.reconcileVaultState()
        assertTrue(reconciliation is MalAccountResult.Success)
        assertEquals(MalTokenStatus.KEYSTORE_RESET, account(reset.localAccountId).tokenStatus)
        assertFalse(account(reset.localAccountId).isActive)
    }

    @Test
    fun `orphan generations are cleaned during reconciliation`() = runTest {
        val account = create(username = "cleanup")
        val orphan = vault.write(
            localAccountId = account.localAccountId,
            generation = 99L,
            tokens = MalTokenSet(accessToken = "orphan-access"),
        ) as MalTokenVaultResult.Success
        assertTrue(vault.referencesSnapshot().contains(orphan.value))

        val result = repository.reconcileVaultState()

        assertTrue(result is MalAccountResult.Success)
        assertFalse(vault.referencesSnapshot().contains(orphan.value))
        assertEquals(1, vault.referencesSnapshot().size)
    }

    @Test
    fun `Room entity and result strings do not expose credential fields or values`() = runTest {
        val entityFieldNames = com.anisync.android.data.local.entity.MalAccountEntity::class.java
            .declaredFields
            .map { it.name }
            .toSet()
        assertFalse(entityFieldNames.contains("accessToken"))
        assertFalse(entityFieldNames.contains("refreshToken"))
        assertFalse(entityFieldNames.contains("clientSecret"))
        assertFalse(entityFieldNames.contains("authorizationCode"))

        val token = MalTokenSet(
            accessToken = "access-sensitive",
            refreshToken = "refresh-sensitive",
            scopes = setOf("read"),
        )
        val account = create(username = "redaction")
        val rendered = listOf(
            token.toString(),
            account.toString(),
            MalAccountResult.Success(token).toString(),
            MalAccountResult.Failure(
                MalAccountFailureReason.TOKEN_CORRUPT,
                account.localAccountId,
                account.tokenGeneration,
            ).toString(),
        ).joinToString("\n")
        assertFalse(rendered.contains("access-sensitive"))
        assertFalse(rendered.contains("refresh-sensitive"))
        assertTrue(rendered.contains("<redacted>"))
    }

    @Test
    fun `generation checked replacement rejects stale or logged out account without restoring tokens`() = runTest {
        val account = create(username = "conditional", makeActive = true)
        val stale = repository.replaceTokensIfGeneration(
            localAccountId = account.localAccountId,
            expectedGeneration = account.tokenGeneration + 1L,
            tokens = MalTokenSet(accessToken = "stale-access", refreshToken = "stale-refresh"),
            requireExistingCredentials = true,
        )
        assertEquals(
            MalAccountFailureReason.ACCOUNT_SESSION_CHANGED,
            (stale as MalAccountResult.Failure).reason,
        )
        assertFalse(vault.containsPlaintext("stale-access"))

        repository.logout(account.localAccountId)
        val afterLogout = repository.replaceTokensIfGeneration(
            localAccountId = account.localAccountId,
            expectedGeneration = account.tokenGeneration,
            tokens = MalTokenSet(accessToken = "late-access", refreshToken = "late-refresh"),
            requireExistingCredentials = true,
        )
        assertEquals(
            MalAccountFailureReason.ACCOUNT_SESSION_CHANGED,
            (afterLogout as MalAccountResult.Failure).reason,
        )
        assertFalse(vault.containsPlaintext("late-access"))
    }

    private suspend fun create(username: String, makeActive: Boolean = false): MalAccount {
        val result = repository.createAccount(
            profile = MalAccountProfile(
                malUserId = (++nextId).toLong(),
                username = username,
                displayName = username.replaceFirstChar { it.uppercase() },
            ),
            tokens = MalTokenSet(
                accessToken = "access-$username",
                refreshToken = "refresh-$username",
                expiresAtEpochMillis = now + 5_000L,
                scopes = setOf("read", "write"),
            ),
            makeActive = makeActive,
        )
        assertTrue(result is MalAccountResult.Success)
        return (result as MalAccountResult.Success).value
    }

    private suspend fun account(localAccountId: String): MalAccount =
        (repository.getAccount(localAccountId) as MalAccountResult.Success).value

    private fun newRepository(db: AppDatabase): MalAccountRepository =
        MalAccountRepository(
            database = db,
            dao = db.malAccountDao(),
            vault = vault,
            clock = MalAccountClock { now++ },
            idGenerator = MalAccountIdGenerator { "local-${nextId + 1}-${now}" },
        )

    private fun openDatabase(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, databaseFile.name)
            .allowMainThreadQueries()
            .addMigrations(com.anisync.android.data.local.Migrations.MIGRATION_23_24)
            .build()

    private class FakeMalTokenVault : MalTokenVault {
        private val bundles = linkedMapOf<String, MalTokenSet>()
        private val corrupt = mutableSetOf<String>()
        private var initializationReset = false

        override fun write(
            localAccountId: String,
            generation: Long,
            tokens: MalTokenSet,
        ): MalTokenVaultResult<String> {
            val reference = AndroidMalTokenVault.reference(localAccountId, generation)
            bundles[reference] = tokens
            return MalTokenVaultResult.Success(reference)
        }

        override fun read(reference: String): MalTokenVaultResult<MalTokenSet> = when {
            reference in corrupt -> MalTokenVaultResult.Failure(
                MalTokenVaultFailureReason.CORRUPT_ENTRY,
                reference,
            )
            reference !in bundles -> MalTokenVaultResult.Failure(
                MalTokenVaultFailureReason.MISSING_ENTRY,
                reference,
            )
            else -> MalTokenVaultResult.Success(checkNotNull(bundles[reference]))
        }

        override fun delete(reference: String): MalTokenVaultResult<Unit> {
            bundles.remove(reference)
            corrupt.remove(reference)
            return MalTokenVaultResult.Success(Unit)
        }

        override fun deleteAccount(localAccountId: String): MalTokenVaultResult<Unit> {
            bundles.keys.filter { it.startsWith("bundle:$localAccountId:") }.toList().forEach {
                bundles.remove(it)
                corrupt.remove(it)
            }
            return MalTokenVaultResult.Success(Unit)
        }

        override fun references(): MalTokenVaultResult<Set<String>> =
            MalTokenVaultResult.Success(bundles.keys.toSet())

        override fun consumeInitializationReset(): Boolean = initializationReset.also {
            initializationReset = false
        }

        fun referencesSnapshot(): Set<String> = bundles.keys.toSet()
        fun dropAll() = bundles.clear()
        fun simulateInitializationReset() {
            bundles.clear()
            initializationReset = true
        }

        fun corrupt(localAccountId: String) {
            bundles.keys.firstOrNull { it.startsWith("bundle:$localAccountId:") }?.let(corrupt::add)
        }

        fun containsPlaintext(value: String): Boolean =
            bundles.values.any { it.accessToken == value || it.refreshToken == value }
    }
}
