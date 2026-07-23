package com.anisync.android.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate1To2_preservesMediaDetailsAndAddsNeutralDefaults() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO media_details (
                    id, titleRomaji, titleEnglish, titleNative, titleUserPreferred,
                    coverUrl, bannerUrl, description, score, episodes, nextAiringEpisode,
                    nextAiringEpisodeTime, chapters, volumes, mediaType, status, format,
                    genres, studio, year, startDate, season, seasonYear, listEntryId,
                    listStatus, listProgress, characters, relations, externalLinks,
                    isFavourite, lastUpdated
                ) VALUES (
                    42, 'Romaji', 'English', 'Native', 'Preferred',
                    'cover', 'banner', 'description', 88, 12, 13,
                    1000, NULL, NULL, 'ANIME', 'RELEASING', 'TV',
                    '["Action"]', 'Studio', 2026, 'Jul 1, 2026', 'SUMMER', 2026,
                    99, 'CURRENT', 4, '[]', '[]', '[]', 1, 1234
                )
                """.trimIndent()
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            LegacyMigrations.MIGRATION_1_2,
        )
        database.query(
            "SELECT titleUserPreferred, endDate, duration, tags, trailer, listProgress " +
                "FROM media_details WHERE id = 42"
        ).use { cursor ->
            check(cursor.moveToFirst())
            assertEquals("Preferred", cursor.getString(0))
            assertNull(cursor.getString(1))
            assertTrueNull(cursor, 2)
            assertEquals("[]", cursor.getString(3))
            assertNull(cursor.getString(4))
            assertEquals(4, cursor.getInt(5))
        }
        database.close()
    }

    @Test
    fun migrate1To2_handlesEmptyDatabase() {
        helper.createDatabase(TEST_DB, 1).close()
        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            LegacyMigrations.MIGRATION_1_2,
        )
        assertEquals(0, database.query("SELECT COUNT(*) FROM media_details").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        })
        database.close()
    }

    private fun assertTrueNull(cursor: android.database.Cursor, columnIndex: Int) {
        assertEquals(true, cursor.isNull(columnIndex))
    }

    private companion object {
        const val TEST_DB = "legacy-migration-test"
    }
}
