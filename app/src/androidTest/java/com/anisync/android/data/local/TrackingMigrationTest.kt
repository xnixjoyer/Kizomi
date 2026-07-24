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
    fun migrate25To26_isAdditiveAndPreservesHistoricalTrackingRows() {
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
                INSERT INTO provider_media_identities (
                    localMediaId, provider, providerMediaId, mediaType, mappingSource,
                    verificationStatus, createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (
                    'local-anime', 'ANILIST', 42, 'ANIME', 'EXISTING_ANILIST_MIGRATION',
                    'EXACT', 1, 1
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO library_entries (
                    id, ownerId, mediaId, titleUserPreferred, progress, mediaType, status,
                    score, rewatches, startedAt, updatedAt, lastUpdated
                ) VALUES (
                    99, 123, 42, 'Migrated fixture', 4, 'ANIME', 'CURRENT',
                    80.0, 1, 1719792000000, 1719792000000, 1719792000000
                )
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
        assertEquals(
            1,
            database.intValue(
                "SELECT COUNT(*) FROM provider_tracking_snapshots " +
                    "WHERE provider = 'ANILIST' AND providerAccountId = '123' " +
                    "AND localMediaId = 'local-anime' AND providerMediaId = 42 " +
                    "AND providerListEntryId = 99 AND progress = 4 " +
                    "AND startedAt = '2024-07-01'"
            ),
        )

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

    @Test
    fun migrate26To27_addsRestartSafeHistoricalListStagingWithoutDataLoss() {
        helper.createDatabase(TEST_DB, 26).apply {
            execSQL(
                """
                INSERT INTO mal_import_states (
                    localAccountId, mediaType, state, generation, nextPageUrl,
                    importedCount, lastAttemptAtEpochMillis, lastSuccessAtEpochMillis,
                    lastErrorKind
                ) VALUES ('account', 'ANIME', 'SUCCEEDED', 4, NULL, 12, 100, 110, NULL)
                """.trimIndent()
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            27,
            true,
            Migrations.MIGRATION_26_27,
        )

        assertTrue(database.hasTable("mal_import_entries"))
        assertEquals(12, database.intValue("SELECT importedCount FROM mal_import_states"))
        database.execSQL(
            """
            INSERT INTO mal_import_entries (
                localAccountId, mediaType, generation, malId, localMediaId, payloadJson
            ) VALUES ('account', 'ANIME', 5, 42, 'local-id', '{}')
            """.trimIndent()
        )
        assertEquals(1, database.intValue("SELECT COUNT(*) FROM mal_import_entries"))
        database.close()
    }


    @Test
    fun migrate27To28_purgesMixedQueueDropsRawPayloadsAndEnforcesOneTarget() {
        helper.createDatabase(TEST_DB, 27).apply {
            execSQL(
                """
                INSERT INTO local_media_identities
                    (id, mediaType, createdAtEpochMillis, updatedAtEpochMillis)
                VALUES ('local-anime', 'ANIME', 1, 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO provider_tracking_snapshots (
                    provider, providerAccountId, localMediaId, providerMediaId,
                    providerListEntryId, mediaType, title, coverUrl, status, progress,
                    progressSecondary, score, repeatCount, notes, startedAt, completedAt,
                    providerUpdatedAtEpochMillis, fetchedAtEpochMillis,
                    rawProviderFieldsJson, isDeleted
                ) VALUES (
                    'MYANIMELIST', 'mal-account', 'local-anime', 42,
                    NULL, 'ANIME', 'Fixture', NULL, 'CURRENT', 4,
                    NULL, 80.0, 0, 'private note', NULL, NULL,
                    10, 10, '{"private":"wire"}', 0
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO tracking_operations (
                    operationId, logicalKey, localMediaId, mediaType, generation,
                    deduplicationKey, commandJson, fieldMask, isTombstone, state,
                    createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (
                    'op', 'legacy', 'local-anime', 'ANIME', 1,
                    'dedupe', '{}', 'PROGRESS', 0, 'PENDING', 1, 1
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO tracking_operation_targets (
                    operationId, provider, providerAccountId, providerMediaId, state,
                    attemptCount, nextAttemptAtEpochMillis, leaseToken,
                    leaseExpiresAtEpochMillis, lastErrorKind, lastHttpStatus,
                    retryAfterMillis, remoteRevision, updatedAtEpochMillis
                ) VALUES
                    ('op', 'ANILIST', 'ani', 10, 'PENDING', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 1),
                    ('op', 'MYANIMELIST', 'mal', 42, 'PENDING', 0, 1, NULL, NULL, NULL, NULL, NULL, NULL, 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO mal_import_states (
                    localAccountId, mediaType, state, generation, nextPageUrl,
                    importedCount, lastAttemptAtEpochMillis, lastSuccessAtEpochMillis,
                    lastErrorKind
                ) VALUES ('mal-account', 'ANIME', 'RUNNING', 3, 'https://api.myanimelist.net/v2/page', 5, 10, 9, NULL)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO mal_import_entries (
                    localAccountId, mediaType, generation, malId, localMediaId, payloadJson
                ) VALUES ('mal-account', 'ANIME', 3, 42, 'local-anime', '{"raw":"payload"}')
                """.trimIndent()
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            28,
            true,
            Migrations.MIGRATION_27_28,
        )

        assertEquals(0, database.intValue("SELECT COUNT(*) FROM tracking_operations"))
        assertEquals(0, database.intValue("SELECT COUNT(*) FROM tracking_operation_targets"))
        assertEquals(1, database.intValue("SELECT COUNT(*) FROM provider_tracking_snapshots"))
        assertTrue(!database.hasColumn("provider_tracking_snapshots", "rawProviderFieldsJson"))
        assertTrue(!database.hasColumn("mal_media_cache", "rawJson"))
        assertTrue(!database.hasTable("tracking_reconciliation_plans"))
        assertTrue(!database.hasTable("tracking_reconciliation_items"))
        assertTrue(!database.hasTable("mal_import_states"))
        assertTrue(!database.hasTable("mal_import_entries"))
        assertTrue(database.hasTable("mal_library_refresh_states"))
        assertTrue(database.hasTable("mal_library_refresh_entries"))
        assertEquals(
            "FAILED",
            database.stringValue(
                "SELECT state FROM mal_library_refresh_states " +
                    "WHERE localAccountId = 'mal-account' AND mediaType = 'ANIME'"
            ),
        )
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
private fun androidx.sqlite.db.SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean =
    query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex) == column) return@use true
        }
        false
    }

private fun androidx.sqlite.db.SupportSQLiteDatabase.stringValue(sql: String): String =
    query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getString(0)
    }

