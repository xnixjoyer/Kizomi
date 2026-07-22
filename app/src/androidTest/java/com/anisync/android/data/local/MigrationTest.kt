package com.anisync.android.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented tests for database migrations. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun createDatabase_version1() {
        helper.createDatabase(TEST_DB, 1).close()
    }

    /**
     * Verifies the additive v22 -> v23 feature migration used by the current production schema.
     */
    @Test
    fun migrate22To23_addsCommunityScoresAndFranchiseGraphCaches() {
        helper.createDatabase(TEST_DB, 22).close()

        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            23,
            true,
        )

        assertTrue(database.tableExists("community_scores"))
        assertTrue(database.tableExists("franchise_graphs"))
        assertTrue(database.columns("library_entries").containsAll(listOf("malId", "averageScore")))
        assertTrue(database.columns("media_details").contains("malId"))

        database.execSQL(
            """
            INSERT INTO community_scores (
                aniListMediaId, malId, score, scoredBy, rank, title,
                fetchedAtEpochMillis, expiresAtEpochMillis, unavailable,
                isManualLink, etag, lastModified
            ) VALUES (1, 1, 8.5, 1000, 50, 'Test', 1, 2, 0, 0, NULL, NULL)
            """.trimIndent()
        )
        database.query("SELECT score FROM community_scores WHERE aniListMediaId = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(8.5, cursor.getDouble(0), 0.0001)
        }
        database.close()
    }

    @Test
    fun migrate23To24_preservesExistingDataAndAddsMetadataOnlyMalAccounts() {
        helper.createDatabase(TEST_DB, 23).apply {
            execSQL(
                """
                INSERT INTO community_scores (
                    aniListMediaId, malId, score, scoredBy, rank, title,
                    fetchedAtEpochMillis, expiresAtEpochMillis, unavailable,
                    isManualLink, etag, lastModified
                ) VALUES (4242, 5252, 9.1, 100, 10, 'Existing AniList-linked row',
                    1000, 2000, 0, 1, NULL, NULL)
                """.trimIndent()
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            24,
            true,
            Migrations.MIGRATION_23_24,
        )

        database.query(
            "SELECT malId, score, title FROM community_scores WHERE aniListMediaId = 4242"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(5252, cursor.getInt(0))
            assertEquals(9.1, cursor.getDouble(1), 0.0001)
            assertEquals("Existing AniList-linked row", cursor.getString(2))
        }

        assertTrue(database.tableExists("mal_accounts"))
        val columns = database.columns("mal_accounts")
        assertTrue(
            columns.containsAll(
                listOf(
                    "localAccountId",
                    "provider",
                    "malUserId",
                    "accessTokenRef",
                    "refreshTokenRef",
                    "tokenGeneration",
                    "tokenStatus",
                    "isActive",
                )
            )
        )
        assertFalse(columns.contains("accessToken"))
        assertFalse(columns.contains("refreshToken"))
        assertFalse(columns.contains("clientSecret"))
        assertFalse(columns.contains("authorizationCode"))

        database.execSQL(
            """
            INSERT INTO mal_accounts (
                localAccountId, provider, malUserId, username, displayName, avatarUrl,
                accessTokenRef, refreshTokenRef, tokenGeneration,
                tokenExpiresAtEpochMillis, scopes, tokenStatus, isActive,
                createdAtEpochMillis, updatedAtEpochMillis
            ) VALUES (
                'local-fixture', 'MYANIMELIST', 123, 'fixture', 'Fixture', NULL,
                'bundle:local-fixture:1', NULL, 1, 5000, 'read', 'ACTIVE', 1, 1000, 1000
            )
            """.trimIndent()
        )
        database.query(
            "SELECT localAccountId, tokenGeneration, isActive FROM mal_accounts"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("local-fixture", cursor.getString(0))
            assertEquals(1L, cursor.getLong(1))
            assertEquals(1, cursor.getInt(2))
        }
        database.close()
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}

private fun androidx.sqlite.db.SupportSQLiteDatabase.tableExists(name: String): Boolean =
    query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name)).use {
        it.moveToFirst()
    }

private fun androidx.sqlite.db.SupportSQLiteDatabase.columns(table: String): Set<String> =
    query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndexOrThrow("name")
        buildSet {
            while (cursor.moveToNext()) add(cursor.getString(nameIndex))
        }
    }
