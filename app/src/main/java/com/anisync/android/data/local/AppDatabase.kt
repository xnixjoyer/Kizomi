package com.anisync.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.anisync.android.data.local.dao.CommunityScoreDao
import com.anisync.android.data.local.dao.FranchiseGraphDao
import com.anisync.android.data.local.dao.LibraryDao
import com.anisync.android.data.local.dao.MalAccountDao
import com.anisync.android.data.local.dao.MediaDetailsDao
import com.anisync.android.data.local.dao.MediaIdentityDao
import com.anisync.android.data.local.dao.SavedForumThreadDao
import com.anisync.android.data.local.dao.TrackingDao
import com.anisync.android.data.local.dao.UserProfileDao
import com.anisync.android.data.local.entity.AiringScheduleEntity
import com.anisync.android.data.local.entity.CommunityScoreEntity
import com.anisync.android.data.local.entity.FranchiseGraphEntity
import com.anisync.android.data.local.entity.LibraryEntryEntity
import com.anisync.android.data.local.entity.LocalMediaIdentityEntity
import com.anisync.android.data.local.entity.MalAccountEntity
import com.anisync.android.data.local.entity.MediaDetailsEntity
import com.anisync.android.data.local.entity.ProviderMediaIdentityEntity
import com.anisync.android.data.local.entity.ProviderMediaIdentityIssueEntity
import com.anisync.android.data.local.entity.ProviderTrackingSnapshotEntity
import com.anisync.android.data.local.entity.SavedForumThreadEntity
import com.anisync.android.data.local.entity.TrackingOperationEntity
import com.anisync.android.data.local.entity.TrackingOperationTargetEntity
import com.anisync.android.data.local.entity.TrackingReconciliationItemEntity
import com.anisync.android.data.local.entity.TrackingReconciliationPlanEntity
import com.anisync.android.data.local.entity.TrendingEntity
import com.anisync.android.data.local.entity.UserProfileEntity
import com.anisync.android.data.local.entity.MalImportStateEntity
import com.anisync.android.data.local.entity.MalMediaCacheEntity

/**
 * Room database for offline caching and non-secret provider account metadata.
 *
 * Version History:
 * ─────────────────────────────────────────────────────────────────────────────
 * v26 (Jul 2026):
 *   - Added account-scoped provider snapshots and a durable multi-provider command journal.
 *   - Added MAL-native metadata/import caches and resumable reconciliation plans.
 *   - Manual additive migration preserves all Phase 1–4 data.
 *
 * v25 (Jul 2026):
 *   - Added provider-neutral local media identities, active provider mappings, and review issues.
 *   - Manual migration seeds existing typed AniList identities without rewriting production caches.
 *
 * v24 (Jul 2026):
 *   - Added `mal_accounts` for independent MyAnimeList account metadata.
 *   - Stores only opaque encrypted-vault references; OAuth credentials never enter Room.
 *   - Manual migration preserves every existing AniList/library/profile/cache table.
 *
 * v23 (Jul 2026):
 *   - Added nullable AniList/MAL score metadata to library/details caches.
 *   - Added community_scores for opt-in, read-only Jikan/MAL score caching.
 *   - Added franchise_graphs for 48-hour offline Franchise Universe snapshots.
 *
 * v19 (Jun 2026):
 *   - Added field to user_profile:
 *     • aboutMarkdown - raw markdown bio (about asHtml:false), cached next to the
 *       rendered HTML so the bio editor loads clean source instead of falling back
 *       to the asHtml-wrapped HTML (which saved corrupted markup back to AniList).
 *
 * v18 (Jun 2026):
 *   - Added field to library_entries:
 *     • ownerId - AniList user id the entry belongs to, so multiple accounts'
 *       libraries persist side by side (instant switch from cache, no bleed).
 *       Existing rows default to 0 and are re-tagged to the real id on first
 *       account reconcile.
 *
 * v17 (Jun 2026):
 *   - Added fields to media_details:
 *     • isRecommendationBlocked - hides the "add recommendation" action when true
 *     • isReviewBlocked - hides the "write review" action when true
 *
 * v16 (May 2026):
 *   - Added field to media_details:
 *     • coverColor - average cover hex color, used to tint rich-text link cards
 *
 * v15 (May 2026):
 *   - Added fields to media_details:
 *     • popularity, favourites - AniList community stats
 *     • nextAiringTimeUntil - seconds-snapshot fallback for next episode airing
 *     • staff - lightweight staff list for media details page
 *
 * v4 (Mar 2026):
 *   - Added saved_forum_threads table for local thread bookmarks.
 *
 * v3 (Feb 2026):
 *   - Added source and tag-description fields to media details.
 *
 * v2 (Feb 2026):
 *   - Added end date, duration, tags and trailer fields to media details.
 *
 * v1 (Fresh Start - June 2025):
 *   - Initial production schema.
 *
 * Migration Guidelines:
 *   - Auto-migrations: Use for simple changes.
 *   - Manual migrations: Use for complex changes (see Migrations.kt).
 *   - Always test migrations before release (see MigrationTest.kt).
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Database(
    entities = [
        LibraryEntryEntity::class,
        MediaDetailsEntity::class,
        UserProfileEntity::class,
        AiringScheduleEntity::class,
        TrendingEntity::class,
        SavedForumThreadEntity::class,
        CommunityScoreEntity::class,
        FranchiseGraphEntity::class,
        MalAccountEntity::class,
        LocalMediaIdentityEntity::class,
        ProviderMediaIdentityEntity::class,
        ProviderMediaIdentityIssueEntity::class,
        ProviderTrackingSnapshotEntity::class,
        TrackingOperationEntity::class,
        TrackingOperationTargetEntity::class,
        MalMediaCacheEntity::class,
        MalImportStateEntity::class,
        TrackingReconciliationPlanEntity::class,
        TrackingReconciliationItemEntity::class,
    ],
    version = 26,
    exportSchema = true,
    autoMigrations = [
        androidx.room.AutoMigration(from = 2, to = 3),
        androidx.room.AutoMigration(from = 3, to = 4),
        androidx.room.AutoMigration(from = 4, to = 5),
        androidx.room.AutoMigration(from = 5, to = 7),
        androidx.room.AutoMigration(from = 7, to = 8),
        androidx.room.AutoMigration(from = 8, to = 9),
        androidx.room.AutoMigration(from = 9, to = 10),
        androidx.room.AutoMigration(from = 10, to = 11),
        androidx.room.AutoMigration(from = 11, to = 12),
        androidx.room.AutoMigration(from = 12, to = 13),
        androidx.room.AutoMigration(from = 13, to = 14),
        androidx.room.AutoMigration(from = 14, to = 15),
        androidx.room.AutoMigration(from = 15, to = 16),
        androidx.room.AutoMigration(from = 16, to = 17),
        androidx.room.AutoMigration(from = 17, to = 18),
        androidx.room.AutoMigration(from = 18, to = 19),
        androidx.room.AutoMigration(from = 19, to = 20),
        androidx.room.AutoMigration(from = 20, to = 21),
        androidx.room.AutoMigration(from = 21, to = 22),
        androidx.room.AutoMigration(from = 22, to = 23),
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun mediaDetailsDao(): MediaDetailsDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun airingScheduleDao(): com.anisync.android.data.local.dao.AiringScheduleDao
    abstract fun trendingDao(): com.anisync.android.data.local.dao.TrendingDao
    abstract fun savedForumThreadDao(): SavedForumThreadDao
    abstract fun communityScoreDao(): CommunityScoreDao
    abstract fun franchiseGraphDao(): FranchiseGraphDao
    abstract fun malAccountDao(): MalAccountDao
    abstract fun mediaIdentityDao(): MediaIdentityDao
    abstract fun trackingDao(): TrackingDao
}
