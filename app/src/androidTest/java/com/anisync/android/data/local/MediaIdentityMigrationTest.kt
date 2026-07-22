package com.anisync.android.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaIdentityMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate24To25_preservesAnimeAndMangaAndCreatesProviderNeutralIds() {
        helper.createDatabase(TEST_DB, 24).apply {
            insertLibrary(id = 1, mediaId = 100, mediaType = "ANIME", malId = 200)
            insertDetails(id = 300, mediaType = "MANGA", malId = null)
            execSQL(
                """
                INSERT INTO community_scores (
                    aniListMediaId, malId, score, scoredBy, rank, title,
                    fetchedAtEpochMillis, expiresAtEpochMillis, unavailable,
                    isManualLink, etag, lastModified
                ) VALUES (100, 200, 8.5, 100, 1, 'Anime', 1, 2, 0, 1, NULL, NULL)
                """.trimIndent()
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            25,
            true,
            Migrations.MIGRATION_24_25,
        )

        assertEquals(2, database.intValue("SELECT COUNT(*) FROM local_media_identities"))
        assertEquals(2, database.intValue("SELECT COUNT(*) FROM provider_media_identities WHERE provider = 'ANILIST'"))
        assertEquals(1, database.intValue("SELECT COUNT(*) FROM provider_media_identities WHERE provider = 'MYANIMELIST' AND verificationStatus = 'CONFIRMED'"))
        assertEquals(1, database.intValue("SELECT COUNT(*) FROM provider_media_identity_issues WHERE verificationStatus = 'UNRESOLVED' AND mediaType = 'MANGA'"))
        val animeLocal = database.stringValue(
            "SELECT localMediaId FROM provider_media_identities WHERE provider = 'ANILIST' AND providerMediaId = 100 AND mediaType = 'ANIME'"
        )
        assertNotEquals("100", animeLocal)
        assertTrue(animeLocal.length >= 32)
        assertEquals(1, database.intValue("SELECT COUNT(*) FROM library_entries WHERE mediaId = 100 AND progress = 4"))
        assertEquals(1, database.intValue("SELECT COUNT(*) FROM media_details WHERE id = 300 AND titleUserPreferred = 'Fixture'"))
        assertFalse(database.tableExists("phase4_identity_seed"))
        assertFalse(database.tableExists("phase4_mal_candidates"))
        database.close()
    }

    @Test
    fun migrate24To25_keepsDuplicateAndContradictoryMalCandidatesAsConflicts() {
        helper.createDatabase(TEST_DB, 24).apply {
            insertLibrary(id = 1, mediaId = 101, mediaType = "ANIME", malId = 999)
            insertLibrary(id = 2, mediaId = 102, mediaType = "ANIME", malId = 999)
            insertLibrary(id = 3, mediaId = 103, mediaType = "ANIME", malId = 555)
            execSQL(
                """
                INSERT INTO community_scores (
                    aniListMediaId, malId, score, scoredBy, rank, title,
                    fetchedAtEpochMillis, expiresAtEpochMillis, unavailable,
                    isManualLink, etag, lastModified
                ) VALUES (103, 556, NULL, NULL, NULL, NULL, 1, 2, 0, 1, NULL, NULL)
                """.trimIndent()
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            25,
            true,
            Migrations.MIGRATION_24_25,
        )

        assertEquals(0, database.intValue("SELECT COUNT(*) FROM provider_media_identities WHERE provider = 'MYANIMELIST' AND providerMediaId IN (999, 555, 556)"))
        assertTrue(database.intValue("SELECT COUNT(*) FROM provider_media_identity_issues WHERE verificationStatus = 'CONFLICTING'") >= 4)
        assertEquals(3, database.intValue("SELECT COUNT(*) FROM provider_media_identities WHERE provider = 'ANILIST'"))
        assertEquals(3, database.intValue("SELECT COUNT(*) FROM library_entries"))
        database.close()
    }

    @Test
    fun migrate24To25_recordsInvalidIdsAndUntypedOrphansWithoutDeletingRows() {
        helper.createDatabase(TEST_DB, 24).apply {
            insertLibrary(id = 1, mediaId = 104, mediaType = "ANIME", malId = 0)
            execSQL("INSERT INTO trending_media (id, titleUserPreferred, coverUrl, averageScore, rank) VALUES (900, 'Orphan', NULL, NULL, 1)")
            execSQL("INSERT INTO franchise_graphs (rootMediaId, payloadJson, fetchedAtEpochMillis) VALUES (901, '{}', 1)")
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            25,
            true,
            Migrations.MIGRATION_24_25,
        )

        assertEquals(1, database.intValue("SELECT COUNT(*) FROM provider_media_identity_issues WHERE provider = 'MYANIMELIST' AND providerMediaId = 0 AND verificationStatus = 'REJECTED'"))
        assertEquals(2, database.intValue("SELECT COUNT(*) FROM provider_media_identity_issues WHERE provider = 'ANILIST' AND verificationStatus = 'UNRESOLVED' AND reason = 'UNKNOWN_MEDIA_TYPE'"))
        assertEquals(1, database.intValue("SELECT COUNT(*) FROM trending_media WHERE id = 900"))
        assertEquals(1, database.intValue("SELECT COUNT(*) FROM franchise_graphs WHERE rootMediaId = 901"))
        database.close()
    }

    @Test
    fun migrate24To25_handlesEmptyDatabase() {
        helper.createDatabase(TEST_DB, 24).close()
        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            25,
            true,
            Migrations.MIGRATION_24_25,
        )
        assertEquals(0, database.intValue("SELECT COUNT(*) FROM local_media_identities"))
        assertEquals(0, database.intValue("SELECT COUNT(*) FROM provider_media_identities"))
        assertEquals(0, database.intValue("SELECT COUNT(*) FROM provider_media_identity_issues"))
        database.close()
    }

    @Test
    fun migrate24To25_handlesLargeLibraryWithoutDuplicateLocalIdentities() {
        helper.createDatabase(TEST_DB, 24).apply {
            repeat(500) { offset ->
                insertLibrary(
                    id = offset + 1,
                    mediaId = 10_000 + offset,
                    mediaType = if (offset % 2 == 0) "ANIME" else "MANGA",
                    malId = null,
                )
            }
            close()
        }
        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            25,
            true,
            Migrations.MIGRATION_24_25,
        )
        assertEquals(500, database.intValue("SELECT COUNT(*) FROM local_media_identities"))
        assertEquals(500, database.intValue("SELECT COUNT(*) FROM provider_media_identities WHERE provider = 'ANILIST'"))
        assertEquals(500, database.intValue("SELECT COUNT(*) FROM provider_media_identity_issues WHERE provider = 'MYANIMELIST' AND verificationStatus = 'UNRESOLVED'"))
        assertEquals(500, database.intValue("SELECT COUNT(*) FROM library_entries"))
        database.close()
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertLibrary(
        id: Int,
        mediaId: Int,
        mediaType: String?,
        malId: Int?,
    ) {
        execSQL(
            """
            INSERT INTO library_entries (
                id, mediaId, malId, titleUserPreferred, progress, mediaType, status,
                rewatches, lastUpdated
            ) VALUES (?, ?, ?, 'Fixture', 4, ?, 'CURRENT', 0, 1)
            """.trimIndent(),
            arrayOf(id, mediaId, malId, mediaType),
        )
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertDetails(
        id: Int,
        mediaType: String?,
        malId: Int?,
    ) {
        execSQL(
            """
            INSERT INTO media_details (
                id, malId, titleUserPreferred, description, mediaType, status,
                genres, tags, characters, relations, externalLinks,
                isFavourite, lastUpdated
            ) VALUES (?, ?, 'Fixture', '', ?, 'FINISHED',
                '[]', '[]', '[]', '[]', '[]', 0, 1)
            """.trimIndent(),
            arrayOf(id, malId, mediaType),
        )
    }

    companion object {
        private const val TEST_DB = "phase4-media-identity-migration-test"
    }
}

private fun androidx.sqlite.db.SupportSQLiteDatabase.intValue(sql: String): Int =
    query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getInt(0)
    }

private fun androidx.sqlite.db.SupportSQLiteDatabase.stringValue(sql: String): String =
    query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getString(0)
    }

private fun androidx.sqlite.db.SupportSQLiteDatabase.tableExists(name: String): Boolean =
    query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name)).use {
        it.moveToFirst()
    }
