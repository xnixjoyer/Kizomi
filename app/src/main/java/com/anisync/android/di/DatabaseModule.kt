package com.anisync.android.di

import android.content.Context
import androidx.room.Room
import com.anisync.android.data.local.AppDatabase
import com.anisync.android.data.local.Migrations
import com.anisync.android.data.local.dao.CommunityScoreDao
import com.anisync.android.data.local.dao.FranchiseGraphDao
import com.anisync.android.data.local.dao.LibraryDao
import com.anisync.android.data.local.dao.MalAccountDao
import com.anisync.android.data.local.dao.MediaDetailsDao
import com.anisync.android.data.local.dao.SavedForumThreadDao
import com.anisync.android.data.local.dao.TrackingConflictDao
import com.anisync.android.data.local.dao.TrackingDao
import com.anisync.android.data.local.dao.TrackingReconciliationDao
import com.anisync.android.data.local.dao.UserProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt module providing Room database and DAOs. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "anisync.db"
        )
            .addMigrations(*Migrations.ALL_MIGRATIONS)
            // Missing migration paths must fail closed. Production data is never dropped to make an
            // upgrade appear successful; supported upgrades are covered by committed Room schemas
            // and instrumentation migration tests.
            .build()
    }

    @Provides
    fun provideLibraryDao(database: AppDatabase): LibraryDao = database.libraryDao()

    @Provides
    fun provideMediaDetailsDao(database: AppDatabase): MediaDetailsDao = database.mediaDetailsDao()

    @Provides
    fun provideUserProfileDao(database: AppDatabase): UserProfileDao = database.userProfileDao()

    @Provides
    fun provideAiringScheduleDao(
        database: AppDatabase,
    ): com.anisync.android.data.local.dao.AiringScheduleDao = database.airingScheduleDao()

    @Provides
    fun provideTrendingDao(
        database: AppDatabase,
    ): com.anisync.android.data.local.dao.TrendingDao = database.trendingDao()

    @Provides
    fun provideSavedForumThreadDao(database: AppDatabase): SavedForumThreadDao =
        database.savedForumThreadDao()

    @Provides
    fun provideCommunityScoreDao(database: AppDatabase): CommunityScoreDao =
        database.communityScoreDao()

    @Provides
    fun provideFranchiseGraphDao(database: AppDatabase): FranchiseGraphDao =
        database.franchiseGraphDao()

    @Provides
    fun provideMalAccountDao(database: AppDatabase): MalAccountDao = database.malAccountDao()

    @Provides
    fun provideTrackingDao(database: AppDatabase): TrackingDao = database.trackingDao()

    @Provides
    fun provideTrackingConflictDao(database: AppDatabase): TrackingConflictDao =
        database.trackingConflictDao()

    @Provides
    fun provideTrackingReconciliationDao(database: AppDatabase): TrackingReconciliationDao =
        database.trackingReconciliationDao()
}
