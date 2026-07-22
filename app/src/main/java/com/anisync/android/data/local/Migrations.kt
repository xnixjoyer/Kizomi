package com.anisync.android.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Database migrations for AniSync Plus. */
object Migrations {
    val MIGRATION_23_24 = object : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `mal_accounts` (
                    `localAccountId` TEXT NOT NULL,
                    `provider` TEXT NOT NULL,
                    `malUserId` INTEGER,
                    `username` TEXT,
                    `displayName` TEXT,
                    `avatarUrl` TEXT,
                    `accessTokenRef` TEXT,
                    `refreshTokenRef` TEXT,
                    `tokenGeneration` INTEGER NOT NULL,
                    `tokenExpiresAtEpochMillis` INTEGER,
                    `scopes` TEXT NOT NULL,
                    `tokenStatus` TEXT NOT NULL,
                    `isActive` INTEGER NOT NULL,
                    `createdAtEpochMillis` INTEGER NOT NULL,
                    `updatedAtEpochMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`localAccountId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_mal_accounts_malUserId` ON `mal_accounts` (`malUserId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_mal_accounts_isActive` ON `mal_accounts` (`isActive`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_mal_accounts_updatedAtEpochMillis` ON `mal_accounts` (`updatedAtEpochMillis`)")
        }
    }

    val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `local_media_identities` (
                    `id` TEXT NOT NULL,
                    `mediaType` TEXT NOT NULL,
                    `createdAtEpochMillis` INTEGER NOT NULL,
                    `updatedAtEpochMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `provider_media_identities` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `localMediaId` TEXT NOT NULL,
                    `provider` TEXT NOT NULL,
                    `providerMediaId` INTEGER NOT NULL,
                    `mediaType` TEXT NOT NULL,
                    `mappingSource` TEXT NOT NULL,
                    `verificationStatus` TEXT NOT NULL,
                    `createdAtEpochMillis` INTEGER NOT NULL,
                    `updatedAtEpochMillis` INTEGER NOT NULL,
                    FOREIGN KEY(`localMediaId`) REFERENCES `local_media_identities`(`id`)
                        ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `provider_media_identity_issues` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `localMediaId` TEXT,
                    `provider` TEXT NOT NULL,
                    `providerMediaId` INTEGER,
                    `mediaType` TEXT,
                    `mappingSource` TEXT NOT NULL,
                    `verificationStatus` TEXT NOT NULL,
                    `reason` TEXT NOT NULL,
                    `sourceTable` TEXT,
                    `sourceRowKey` TEXT,
                    `createdAtEpochMillis` INTEGER NOT NULL,
                    `updatedAtEpochMillis` INTEGER NOT NULL,
                    FOREIGN KEY(`localMediaId`) REFERENCES `local_media_identities`(`id`)
                        ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_provider_media_identities_provider_providerMediaId_mediaType` ON `provider_media_identities` (`provider`, `providerMediaId`, `mediaType`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_provider_media_identities_localMediaId_provider_mediaType` ON `provider_media_identities` (`localMediaId`, `provider`, `mediaType`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_provider_media_identities_localMediaId` ON `provider_media_identities` (`localMediaId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_provider_media_identity_issues_localMediaId` ON `provider_media_identity_issues` (`localMediaId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_provider_media_identity_issues_verificationStatus_provider_mediaType` ON `provider_media_identity_issues` (`verificationStatus`, `provider`, `mediaType`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_provider_media_identity_issues_provider_providerMediaId_mediaType` ON `provider_media_identity_issues` (`provider`, `providerMediaId`, `mediaType`)")

            db.execSQL(
                """
                CREATE TEMP TABLE `phase4_identity_seed` (
                    `aniListId` INTEGER NOT NULL,
                    `mediaType` TEXT NOT NULL,
                    `localMediaId` TEXT NOT NULL,
                    PRIMARY KEY(`aniListId`, `mediaType`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `phase4_identity_seed` (`aniListId`, `mediaType`, `localMediaId`)
                SELECT `aniListId`, `mediaType`, lower(hex(randomblob(16)))
                FROM (
                    SELECT `mediaId` AS `aniListId`, `mediaType`
                    FROM `library_entries`
                    WHERE `mediaId` > 0 AND `mediaType` IN ('ANIME', 'MANGA')
                    UNION
                    SELECT `id`, `mediaType`
                    FROM `media_details`
                    WHERE `id` > 0 AND `mediaType` IN ('ANIME', 'MANGA')
                    UNION
                    SELECT `aniListMediaId`, 'ANIME'
                    FROM `community_scores`
                    WHERE `aniListMediaId` > 0
                    UNION
                    SELECT `mediaId`, 'ANIME'
                    FROM `airing_schedule`
                    WHERE `mediaId` > 0
                )
                GROUP BY `aniListId`, `mediaType`
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `local_media_identities`
                    (`id`, `mediaType`, `createdAtEpochMillis`, `updatedAtEpochMillis`)
                SELECT `localMediaId`, `mediaType`,
                    CAST(strftime('%s','now') AS INTEGER) * 1000,
                    CAST(strftime('%s','now') AS INTEGER) * 1000
                FROM `phase4_identity_seed`
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `provider_media_identities`
                    (`localMediaId`, `provider`, `providerMediaId`, `mediaType`,
                     `mappingSource`, `verificationStatus`,
                     `createdAtEpochMillis`, `updatedAtEpochMillis`)
                SELECT `localMediaId`, 'ANILIST', `aniListId`, `mediaType`,
                    'EXISTING_ANILIST_MIGRATION', 'EXACT',
                    CAST(strftime('%s','now') AS INTEGER) * 1000,
                    CAST(strftime('%s','now') AS INTEGER) * 1000
                FROM `phase4_identity_seed`
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TEMP TABLE `phase4_mal_candidates` (
                    `localMediaId` TEXT NOT NULL,
                    `providerMediaId` INTEGER NOT NULL,
                    `mediaType` TEXT NOT NULL,
                    `mappingSource` TEXT NOT NULL,
                    `isManual` INTEGER NOT NULL,
                    `sourceTable` TEXT NOT NULL,
                    `sourceRowKey` TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `phase4_mal_candidates`
                SELECT seed.`localMediaId`, library.`malId`, seed.`mediaType`,
                    'ANILIST_ID_MAL', 0, 'library_entries', CAST(library.`id` AS TEXT)
                FROM `library_entries` library
                JOIN `phase4_identity_seed` seed
                  ON seed.`aniListId` = library.`mediaId`
                 AND seed.`mediaType` = library.`mediaType`
                WHERE library.`malId` IS NOT NULL
                UNION ALL
                SELECT seed.`localMediaId`, details.`malId`, seed.`mediaType`,
                    'ANILIST_ID_MAL', 0, 'media_details', CAST(details.`id` AS TEXT)
                FROM `media_details` details
                JOIN `phase4_identity_seed` seed
                  ON seed.`aniListId` = details.`id`
                 AND seed.`mediaType` = details.`mediaType`
                WHERE details.`malId` IS NOT NULL
                UNION ALL
                SELECT seed.`localMediaId`, scores.`malId`, 'ANIME',
                    CASE WHEN scores.`isManualLink` = 1
                        THEN 'MANUAL_CONFIRMATION' ELSE 'ANILIST_ID_MAL' END,
                    scores.`isManualLink`, 'community_scores',
                    CAST(scores.`aniListMediaId` AS TEXT)
                FROM `community_scores` scores
                JOIN `phase4_identity_seed` seed
                  ON seed.`aniListId` = scores.`aniListMediaId`
                 AND seed.`mediaType` = 'ANIME'
                """.trimIndent()
            )

            db.execSQL(
                """
                WITH per_local AS (
                    SELECT `localMediaId`, `mediaType`,
                        MIN(`providerMediaId`) AS `providerMediaId`,
                        COUNT(DISTINCT `providerMediaId`) AS `candidateCount`,
                        MAX(`isManual`) AS `hasManual`
                    FROM `phase4_mal_candidates`
                    WHERE `providerMediaId` > 0
                    GROUP BY `localMediaId`, `mediaType`
                ),
                global_count AS (
                    SELECT `providerMediaId`, `mediaType`,
                        COUNT(DISTINCT `localMediaId`) AS `localCount`
                    FROM `phase4_mal_candidates`
                    WHERE `providerMediaId` > 0
                    GROUP BY `providerMediaId`, `mediaType`
                )
                INSERT INTO `provider_media_identities`
                    (`localMediaId`, `provider`, `providerMediaId`, `mediaType`,
                     `mappingSource`, `verificationStatus`,
                     `createdAtEpochMillis`, `updatedAtEpochMillis`)
                SELECT local.`localMediaId`, 'MYANIMELIST', local.`providerMediaId`,
                    local.`mediaType`,
                    CASE WHEN local.`hasManual` = 1
                        THEN 'MANUAL_CONFIRMATION' ELSE 'ANILIST_ID_MAL' END,
                    CASE WHEN local.`hasManual` = 1 THEN 'CONFIRMED' ELSE 'EXACT' END,
                    CAST(strftime('%s','now') AS INTEGER) * 1000,
                    CAST(strftime('%s','now') AS INTEGER) * 1000
                FROM per_local local
                JOIN global_count global
                  ON global.`providerMediaId` = local.`providerMediaId`
                 AND global.`mediaType` = local.`mediaType`
                WHERE local.`candidateCount` = 1 AND global.`localCount` = 1
                """.trimIndent()
            )

            db.execSQL(
                """
                WITH local_conflicts AS (
                    SELECT `localMediaId`, `mediaType`
                    FROM `phase4_mal_candidates`
                    WHERE `providerMediaId` > 0
                    GROUP BY `localMediaId`, `mediaType`
                    HAVING COUNT(DISTINCT `providerMediaId`) > 1
                ),
                global_conflicts AS (
                    SELECT `providerMediaId`, `mediaType`
                    FROM `phase4_mal_candidates`
                    WHERE `providerMediaId` > 0
                    GROUP BY `providerMediaId`, `mediaType`
                    HAVING COUNT(DISTINCT `localMediaId`) > 1
                )
                INSERT INTO `provider_media_identity_issues`
                    (`localMediaId`, `provider`, `providerMediaId`, `mediaType`,
                     `mappingSource`, `verificationStatus`, `reason`,
                     `sourceTable`, `sourceRowKey`,
                     `createdAtEpochMillis`, `updatedAtEpochMillis`)
                SELECT DISTINCT candidate.`localMediaId`, 'MYANIMELIST',
                    candidate.`providerMediaId`, candidate.`mediaType`,
                    candidate.`mappingSource`, 'CONFLICTING',
                    CASE WHEN local.`localMediaId` IS NOT NULL
                        THEN 'MULTIPLE_PROVIDER_IDS_FOR_LOCAL'
                        ELSE 'PROVIDER_ID_ATTACHED_TO_MULTIPLE_LOCALS' END,
                    candidate.`sourceTable`, candidate.`sourceRowKey`,
                    CAST(strftime('%s','now') AS INTEGER) * 1000,
                    CAST(strftime('%s','now') AS INTEGER) * 1000
                FROM `phase4_mal_candidates` candidate
                LEFT JOIN local_conflicts local
                  ON local.`localMediaId` = candidate.`localMediaId`
                 AND local.`mediaType` = candidate.`mediaType`
                LEFT JOIN global_conflicts global
                  ON global.`providerMediaId` = candidate.`providerMediaId`
                 AND global.`mediaType` = candidate.`mediaType`
                WHERE candidate.`providerMediaId` > 0
                  AND (local.`localMediaId` IS NOT NULL
                    OR global.`providerMediaId` IS NOT NULL)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `provider_media_identity_issues`
                    (`localMediaId`, `provider`, `providerMediaId`, `mediaType`,
                     `mappingSource`, `verificationStatus`, `reason`,
                     `sourceTable`, `sourceRowKey`,
                     `createdAtEpochMillis`, `updatedAtEpochMillis`)
                SELECT `localMediaId`, 'MYANIMELIST', `providerMediaId`, `mediaType`,
                    `mappingSource`, 'REJECTED', 'INVALID_PROVIDER_ID',
                    `sourceTable`, `sourceRowKey`,
                    CAST(strftime('%s','now') AS INTEGER) * 1000,
                    CAST(strftime('%s','now') AS INTEGER) * 1000
                FROM `phase4_mal_candidates`
                WHERE `providerMediaId` <= 0
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `provider_media_identity_issues`
                    (`localMediaId`, `provider`, `providerMediaId`, `mediaType`,
                     `mappingSource`, `verificationStatus`, `reason`,
                     `sourceTable`, `sourceRowKey`,
                     `createdAtEpochMillis`, `updatedAtEpochMillis`)
                SELECT seed.`localMediaId`, 'MYANIMELIST', NULL, seed.`mediaType`,
                    'ANILIST_ID_MAL', 'UNRESOLVED', 'MISSING_PROVIDER_ID',
                    NULL, CAST(seed.`aniListId` AS TEXT),
                    CAST(strftime('%s','now') AS INTEGER) * 1000,
                    CAST(strftime('%s','now') AS INTEGER) * 1000
                FROM `phase4_identity_seed` seed
                WHERE NOT EXISTS (
                    SELECT 1 FROM `provider_media_identities` active
                    WHERE active.`localMediaId` = seed.`localMediaId`
                      AND active.`provider` = 'MYANIMELIST'
                      AND active.`mediaType` = seed.`mediaType`
                )
                AND NOT EXISTS (
                    SELECT 1 FROM `provider_media_identity_issues` issue
                    WHERE issue.`localMediaId` = seed.`localMediaId`
                      AND issue.`provider` = 'MYANIMELIST'
                      AND issue.`mediaType` = seed.`mediaType`
                )
                """.trimIndent()
            )

            recordUntypedOrInvalidLegacyRows(db)
            db.execSQL("DROP TABLE `phase4_mal_candidates`")
            db.execSQL("DROP TABLE `phase4_identity_seed`")
        }
    }

    private fun recordUntypedOrInvalidLegacyRows(db: SupportSQLiteDatabase) {
        val now = "CAST(strftime('%s','now') AS INTEGER) * 1000"
        db.execSQL(
            """
            INSERT INTO `provider_media_identity_issues`
                (`localMediaId`, `provider`, `providerMediaId`, `mediaType`,
                 `mappingSource`, `verificationStatus`, `reason`,
                 `sourceTable`, `sourceRowKey`,
                 `createdAtEpochMillis`, `updatedAtEpochMillis`)
            SELECT NULL, 'ANILIST', `mediaId`, NULL,
                'EXISTING_ANILIST_MIGRATION', 'UNRESOLVED', 'UNKNOWN_MEDIA_TYPE',
                'library_entries', CAST(`id` AS TEXT), $now, $now
            FROM `library_entries`
            WHERE `mediaId` > 0 AND (`mediaType` IS NULL OR `mediaType` NOT IN ('ANIME','MANGA'))
            UNION ALL
            SELECT NULL, 'ANILIST', `id`, NULL,
                'EXISTING_ANILIST_MIGRATION', 'UNRESOLVED', 'UNKNOWN_MEDIA_TYPE',
                'media_details', CAST(`id` AS TEXT), $now, $now
            FROM `media_details`
            WHERE `id` > 0 AND (`mediaType` IS NULL OR `mediaType` NOT IN ('ANIME','MANGA'))
            UNION ALL
            SELECT NULL, 'ANILIST', trending.`id`, NULL,
                'EXISTING_ANILIST_MIGRATION', 'UNRESOLVED', 'UNKNOWN_MEDIA_TYPE',
                'trending_media', CAST(trending.`id` AS TEXT), $now, $now
            FROM `trending_media` trending
            WHERE trending.`id` > 0 AND NOT EXISTS (
                SELECT 1 FROM `provider_media_identities` active
                WHERE active.`provider` = 'ANILIST'
                  AND active.`providerMediaId` = trending.`id`
            )
            UNION ALL
            SELECT NULL, 'ANILIST', graph.`rootMediaId`, NULL,
                'EXISTING_ANILIST_MIGRATION', 'UNRESOLVED', 'UNKNOWN_MEDIA_TYPE',
                'franchise_graphs', CAST(graph.`rootMediaId` AS TEXT), $now, $now
            FROM `franchise_graphs` graph
            WHERE graph.`rootMediaId` > 0 AND NOT EXISTS (
                SELECT 1 FROM `provider_media_identities` active
                WHERE active.`provider` = 'ANILIST'
                  AND active.`providerMediaId` = graph.`rootMediaId`
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `provider_media_identity_issues`
                (`localMediaId`, `provider`, `providerMediaId`, `mediaType`,
                 `mappingSource`, `verificationStatus`, `reason`,
                 `sourceTable`, `sourceRowKey`,
                 `createdAtEpochMillis`, `updatedAtEpochMillis`)
            SELECT NULL, 'ANILIST', `mediaId`, `mediaType`,
                'EXISTING_ANILIST_MIGRATION', 'REJECTED', 'INVALID_PROVIDER_ID',
                'library_entries', CAST(`id` AS TEXT), $now, $now
            FROM `library_entries` WHERE `mediaId` <= 0
            UNION ALL
            SELECT NULL, 'ANILIST', `id`, `mediaType`,
                'EXISTING_ANILIST_MIGRATION', 'REJECTED', 'INVALID_PROVIDER_ID',
                'media_details', CAST(`id` AS TEXT), $now, $now
            FROM `media_details` WHERE `id` <= 0
            UNION ALL
            SELECT NULL, 'ANILIST', `aniListMediaId`, 'ANIME',
                'EXISTING_ANILIST_MIGRATION', 'REJECTED', 'INVALID_PROVIDER_ID',
                'community_scores', CAST(`aniListMediaId` AS TEXT), $now, $now
            FROM `community_scores` WHERE `aniListMediaId` <= 0
            UNION ALL
            SELECT NULL, 'ANILIST', `mediaId`, 'ANIME',
                'EXISTING_ANILIST_MIGRATION', 'REJECTED', 'INVALID_PROVIDER_ID',
                'airing_schedule', CAST(`id` AS TEXT), $now, $now
            FROM `airing_schedule` WHERE `mediaId` <= 0
            """.trimIndent()
        )
    }

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        MIGRATION_23_24,
        MIGRATION_24_25,
    )
}
