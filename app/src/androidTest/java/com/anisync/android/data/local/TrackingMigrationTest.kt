package com.anisync.android.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackingMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate25To26_isAdditiveAndEnforcesDurableSagaRelationships() {
        helper.createDatabase(TEST_DB, 25).apply {
            execSQL(
                """
                INSERT INTO local_media_identities
                    (id, mediaType, createdAtEpochMillis, updatedAtEpochMillis)
                VALUES ('local-anime', 'ANIME', 1, 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO mal_accounts (
                    localAccountId, provider, malUserId, username, displayName, avatarUrl,
                    accessTokenRef, refreshTokenRef, tokenGeneration,
                    tokenExpiresAtEpochMillis, scopes, tokenStatus, isActive,
                    createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (
                    'mal-account', 'MYANIMELIST', 123, 'fixture', 'Fixture', NULL,
                    'vault-ref', NULL, 1, 5000, 'read write', 'ACTIVE', 1, 1, 1
                )
                """.trimIndent()
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            26,
            true,
            Migrations.MIGRATION_25_26,
        )

        EXPECTED_TABLES.forEach { table -> assertTrue(database.hasTable(table)) }
        assertEquals(1, database.intValue("SELECT COUNT(*) FROM local_media_identities"))
        assertEquals(1, database.intValue("SELECT COUNT(*) FROM mal_accounts"))

        database.execSQL(
            """
            INSERT INTO tracking_operations (
                operationId, logicalKey, localMediaId, mediaType, generation,
                deduplicationKey, commandJson, fieldMask, isTombstone, state,
                createdAtEpochMillis, updatedAtEpochMillis
            ) VALUES (
                'op-1', 'ANIME:local-anime:mal-account', 'local-anime', 'ANIME', 1,
                'dedupe-1', '{}', 'PROGRESS', 0, 'PENDING', 2, 2
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO tracking_operation_targets (
                operationId, provider, providerAccountId, providerMediaId, state,
                attemptCount, nextAttemptAtEpochMillis, leaseToken,
                leaseExpiresAtEpochMillis, lastErrorKind, lastHttpStatus,
                retryAfterMillis, remoteRevision, updatedAtEpochMillis
            ) VALUES (
                'op-1', 'MYANIMELIST', 'mal-account', 42, 'PENDING',
                0, 2, NULL, NULL, NULL, NULL, NULL, NULL, 2
            )
            """.trimIndent()
        )
        assertEquals(1, database.intValue("SELECT COUNT(*) FROM tracking_operation_targets"))
        database.execSQL("DELETE FROM tracking_operations WHERE operationId = 'op-1'")
        assertEquals(0, database.intValue("SELECT COUNT(*) FROM tracking_operation_targets"))
        database.close()
    }

    @Test
    fun migrate25To26_handlesEmptyDatabase() {
        helper.createDatabase(TEST_DB, 25).close()
        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            26,
            true,
            Migrations.MIGRATION_25_26,
        )
        EXPECTED_TABLES.forEach { table -> assertTrue(database.hasTable(table)) }
        assertEquals(0, database.intValue("SELECT COUNT(*) FROM tracking_operations"))
        database.close()
    }

    companion object {
        private const val TEST_DB = "phase5-tracking-migration-test"
        private val EXPECTED_TABLES = setOf(
            "provider_tracking_snapshots",
            "tracking_operations",
            "tracking_operation_targets",
            "mal_media_cache",
            "mal_import_states",
            "tracking_reconciliation_plans",
            "tracking_reconciliation_items",
        )
    }
}

private fun androidx.sqlite.db.SupportSQLiteDatabase.hasTable(name: String): Boolean =
    query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name)).use {
        it.moveToFirst()
    }

private fun androidx.sqlite.db.SupportSQLiteDatabase.intValue(sql: String): Int =
    query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getInt(0)
    }
