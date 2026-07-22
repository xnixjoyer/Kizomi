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

    val MIGRATION_25_26 = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `provider_tracking_snapshots` (
                    `provider` TEXT NOT NULL,
                    `providerAccountId` TEXT NOT NULL,
                    `localMediaId` TEXT NOT NULL,
                    `providerMediaId` INTEGER NOT NULL,
                    `providerListEntryId` INTEGER,
                    `mediaType` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `coverUrl` TEXT,
                    `status` TEXT NOT NULL,
                    `progress` INTEGER NOT NULL,
                    `progressSecondary` INTEGER,
                    `score` REAL,
                    `repeatCount` INTEGER NOT NULL,
                    `notes` TEXT,
                    `startedAt` TEXT,
                    `completedAt` TEXT,
                    `providerUpdatedAtEpochMillis` INTEGER,
                    `fetchedAtEpochMillis` INTEGER NOT NULL,
                    `rawProviderFieldsJson` TEXT NOT NULL,
                    `isDeleted` INTEGER NOT NULL,
                    PRIMARY KEY(`provider`, `providerAccountId`, `localMediaId`),
                    FOREIGN KEY(`localMediaId`) REFERENCES `local_media_identities`(`id`)
                        ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_provider_tracking_snapshots_localMediaId` ON `provider_tracking_snapshots` (`localMediaId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_provider_tracking_snapshots_provider_providerAccountId_mediaType` ON `provider_tracking_snapshots` (`provider`, `providerAccountId`, `mediaType`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_provider_tracking_snapshots_provider_providerAccountId_providerMediaId_mediaType` ON `provider_tracking_snapshots` (`provider`, `providerAccountId`, `providerMediaId`, `mediaType`)")

            // Seed the last locally cached AniList acknowledgement without rewriting or deleting the
            // production library table. The static raw marker is deliberately non-sensitive; the
            // first successful provider refresh replaces it with current provider fields.
            db.execSQL(
                """
                INSERT OR IGNORE INTO `provider_tracking_snapshots` (
                    `provider`, `providerAccountId`, `localMediaId`, `providerMediaId`,
                    `providerListEntryId`, `mediaType`, `title`, `coverUrl`, `status`,
                    `progress`, `progressSecondary`, `score`, `repeatCount`, `notes`,
                    `startedAt`, `completedAt`, `providerUpdatedAtEpochMillis`,
                    `fetchedAtEpochMillis`, `rawProviderFieldsJson`, `isDeleted`
                )
                SELECT
                    'ANILIST', CAST(library.`ownerId` AS TEXT), identity.`localMediaId`,
                    library.`mediaId`, library.`id`, identity.`mediaType`,
                    library.`titleUserPreferred`,
                    COALESCE(library.`coverExtraLarge`, library.`coverLarge`,
                        library.`coverMedium`, library.`coverUrl`),
                    library.`status`,
                    CASE WHEN library.`progress` < 0 THEN 0 ELSE library.`progress` END,
                    NULL, library.`score`,
                    CASE WHEN library.`rewatches` < 0 THEN 0 ELSE library.`rewatches` END,
                    library.`notes`,
                    CASE WHEN library.`startedAt` IS NULL THEN NULL
                        ELSE strftime('%Y-%m-%d', library.`startedAt` / 1000, 'unixepoch') END,
                    CASE WHEN library.`completedAt` IS NULL THEN NULL
                        ELSE strftime('%Y-%m-%d', library.`completedAt` / 1000, 'unixepoch') END,
                    library.`updatedAt`, library.`lastUpdated`,
                    '{"baselineSource":"MIGRATED_LOCAL"}', 0
                FROM `library_entries` library
                JOIN `provider_media_identities` identity
                  ON identity.`provider` = 'ANILIST'
                 AND identity.`providerMediaId` = library.`mediaId`
                 AND identity.`mediaType` = library.`mediaType`
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `tracking_operations` (
                    `operationId` TEXT NOT NULL,
                    `logicalKey` TEXT NOT NULL,
                    `localMediaId` TEXT NOT NULL,
                    `mediaType` TEXT NOT NULL,
                    `generation` INTEGER NOT NULL,
                    `deduplicationKey` TEXT NOT NULL,
                    `commandJson` TEXT NOT NULL,
                    `fieldMask` TEXT NOT NULL,
                    `isTombstone` INTEGER NOT NULL,
                    `state` TEXT NOT NULL,
                    `createdAtEpochMillis` INTEGER NOT NULL,
                    `updatedAtEpochMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`operationId`),
                    FOREIGN KEY(`localMediaId`) REFERENCES `local_media_identities`(`id`)
                        ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracking_operations_localMediaId` ON `tracking_operations` (`localMediaId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tracking_operations_logicalKey_generation` ON `tracking_operations` (`logicalKey`, `generation`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracking_operations_deduplicationKey` ON `tracking_operations` (`deduplicationKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracking_operations_state_updatedAtEpochMillis` ON `tracking_operations` (`state`, `updatedAtEpochMillis`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `tracking_operation_targets` (
                    `operationId` TEXT NOT NULL,
                    `provider` TEXT NOT NULL,
                    `providerAccountId` TEXT,
                    `providerMediaId` INTEGER,
                    `state` TEXT NOT NULL,
                    `attemptCount` INTEGER NOT NULL,
                    `nextAttemptAtEpochMillis` INTEGER NOT NULL,
                    `leaseToken` TEXT,
                    `leaseExpiresAtEpochMillis` INTEGER,
                    `lastErrorKind` TEXT,
                    `lastHttpStatus` INTEGER,
                    `retryAfterMillis` INTEGER,
                    `remoteRevision` TEXT,
                    `updatedAtEpochMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`operationId`, `provider`),
                    FOREIGN KEY(`operationId`) REFERENCES `tracking_operations`(`operationId`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracking_operation_targets_operationId` ON `tracking_operation_targets` (`operationId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracking_operation_targets_state_nextAttemptAtEpochMillis_updatedAtEpochMillis` ON `tracking_operation_targets` (`state`, `nextAttemptAtEpochMillis`, `updatedAtEpochMillis`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracking_operation_targets_provider_providerAccountId_providerMediaId` ON `tracking_operation_targets` (`provider`, `providerAccountId`, `providerMediaId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `mal_media_cache` (
                    `malId` INTEGER NOT NULL,
                    `mediaType` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `alternativeTitlesJson` TEXT NOT NULL,
                    `synopsis` TEXT,
                    `mainPictureMedium` TEXT,
                    `mainPictureLarge` TEXT,
                    `meanScore` REAL,
                    `rank` INTEGER,
                    `popularity` INTEGER,
                    `mediaStatus` TEXT,
                    `startDate` TEXT,
                    `endDate` TEXT,
                    `episodeCount` INTEGER,
                    `chapterCount` INTEGER,
                    `volumeCount` INTEGER,
                    `genresJson` TEXT NOT NULL,
                    `relatedJson` TEXT NOT NULL,
                    `recommendationsJson` TEXT NOT NULL,
                    `rawJson` TEXT NOT NULL,
                    `fetchedAtEpochMillis` INTEGER NOT NULL,
                    `expiresAtEpochMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`malId`, `mediaType`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_mal_media_cache_mediaType_title` ON `mal_media_cache` (`mediaType`, `title`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_mal_media_cache_mediaType_fetchedAtEpochMillis` ON `mal_media_cache` (`mediaType`, `fetchedAtEpochMillis`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `mal_import_states` (
                    `localAccountId` TEXT NOT NULL,
                    `mediaType` TEXT NOT NULL,
                    `state` TEXT NOT NULL,
                    `generation` INTEGER NOT NULL,
                    `nextPageUrl` TEXT,
                    `importedCount` INTEGER NOT NULL,
                    `lastAttemptAtEpochMillis` INTEGER NOT NULL,
                    `lastSuccessAtEpochMillis` INTEGER,
                    `lastErrorKind` TEXT,
                    PRIMARY KEY(`localAccountId`, `mediaType`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_mal_import_states_state_lastAttemptAtEpochMillis` ON `mal_import_states` (`state`, `lastAttemptAtEpochMillis`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `tracking_reconciliation_plans` (
                    `planId` TEXT NOT NULL,
                    `mode` TEXT NOT NULL,
                    `mediaType` TEXT NOT NULL,
                    `sourceAccountId` TEXT,
                    `targetAccountId` TEXT,
                    `state` TEXT NOT NULL,
                    `baselineFingerprint` TEXT NOT NULL,
                    `createdAtEpochMillis` INTEGER NOT NULL,
                    `updatedAtEpochMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`planId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracking_reconciliation_plans_state_updatedAtEpochMillis` ON `tracking_reconciliation_plans` (`state`, `updatedAtEpochMillis`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `tracking_reconciliation_items` (
                    `planId` TEXT NOT NULL,
                    `itemKey` TEXT NOT NULL,
                    `localMediaId` TEXT,
                    `mediaType` TEXT NOT NULL,
                    `aniListId` INTEGER,
                    `malId` INTEGER,
                    `action` TEXT NOT NULL,
                    `state` TEXT NOT NULL,
                    `sourceSnapshotJson` TEXT,
                    `targetSnapshotJson` TEXT,
                    `commandJson` TEXT,
                    `operationId` TEXT,
                    `lastErrorKind` TEXT,
                    `updatedAtEpochMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`planId`, `itemKey`),
                    FOREIGN KEY(`planId`) REFERENCES `tracking_reconciliation_plans`(`planId`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracking_reconciliation_items_planId` ON `tracking_reconciliation_items` (`planId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracking_reconciliation_items_planId_action_state` ON `tracking_reconciliation_items` (`planId`, `action`, `state`)")
        }
    }

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        MIGRATION_23_24,
        MIGRATION_24_25,
        MIGRATION_25_26,
    )
}
