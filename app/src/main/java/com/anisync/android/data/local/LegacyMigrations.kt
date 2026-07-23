package com.anisync.android.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive migrations for the earliest committed schemas. */
object LegacyMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `media_details_v2` (
                    `id` INTEGER NOT NULL,
                    `titleRomaji` TEXT,
                    `titleEnglish` TEXT,
                    `titleNative` TEXT,
                    `titleUserPreferred` TEXT NOT NULL,
                    `coverUrl` TEXT,
                    `bannerUrl` TEXT,
                    `description` TEXT NOT NULL,
                    `score` INTEGER,
                    `episodes` INTEGER,
                    `nextAiringEpisode` INTEGER,
                    `nextAiringEpisodeTime` INTEGER,
                    `chapters` INTEGER,
                    `volumes` INTEGER,
                    `mediaType` TEXT,
                    `status` TEXT NOT NULL,
                    `format` TEXT,
                    `genres` TEXT NOT NULL,
                    `studio` TEXT,
                    `year` INTEGER,
                    `startDate` TEXT,
                    `endDate` TEXT,
                    `season` TEXT,
                    `seasonYear` INTEGER,
                    `duration` INTEGER,
                    `tags` TEXT NOT NULL,
                    `trailer` TEXT,
                    `listEntryId` INTEGER,
                    `listStatus` TEXT,
                    `listProgress` INTEGER,
                    `characters` TEXT NOT NULL,
                    `relations` TEXT NOT NULL,
                    `externalLinks` TEXT NOT NULL,
                    `isFavourite` INTEGER NOT NULL,
                    `lastUpdated` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `media_details_v2` (
                    `id`, `titleRomaji`, `titleEnglish`, `titleNative`,
                    `titleUserPreferred`, `coverUrl`, `bannerUrl`, `description`, `score`,
                    `episodes`, `nextAiringEpisode`, `nextAiringEpisodeTime`, `chapters`,
                    `volumes`, `mediaType`, `status`, `format`, `genres`, `studio`, `year`,
                    `startDate`, `endDate`, `season`, `seasonYear`, `duration`, `tags`,
                    `trailer`, `listEntryId`, `listStatus`, `listProgress`, `characters`,
                    `relations`, `externalLinks`, `isFavourite`, `lastUpdated`
                )
                SELECT
                    `id`, `titleRomaji`, `titleEnglish`, `titleNative`,
                    `titleUserPreferred`, `coverUrl`, `bannerUrl`, `description`, `score`,
                    `episodes`, `nextAiringEpisode`, `nextAiringEpisodeTime`, `chapters`,
                    `volumes`, `mediaType`, `status`, `format`, `genres`, `studio`, `year`,
                    `startDate`, NULL, `season`, `seasonYear`, NULL, '[]', NULL,
                    `listEntryId`, `listStatus`, `listProgress`, `characters`, `relations`,
                    `externalLinks`, `isFavourite`, `lastUpdated`
                FROM `media_details`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `media_details`")
            db.execSQL("ALTER TABLE `media_details_v2` RENAME TO `media_details`")
        }
    }

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
}
