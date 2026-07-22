package com.anisync.android.di

import com.anisync.android.data.identity.MediaIdentityClock
import com.anisync.android.data.identity.MediaIdentityIdGenerator
import com.anisync.android.data.identity.MediaIdentityRepository
import com.anisync.android.data.identity.MediaIdentityStore
import com.anisync.android.data.identity.SystemMediaIdentityClock
import com.anisync.android.data.identity.UuidMediaIdentityIdGenerator
import com.anisync.android.data.local.AppDatabase
import com.anisync.android.data.local.dao.MediaIdentityDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaIdentityModule {
    @Provides
    fun provideMediaIdentityDao(database: AppDatabase): MediaIdentityDao =
        database.mediaIdentityDao()

    @Provides
    @Singleton
    fun provideMediaIdentityStore(
        repository: MediaIdentityRepository,
    ): MediaIdentityStore = repository

    @Provides
    @Singleton
    fun provideMediaIdentityClock(
        clock: SystemMediaIdentityClock,
    ): MediaIdentityClock = clock

    @Provides
    @Singleton
    fun provideMediaIdentityIdGenerator(
        generator: UuidMediaIdentityIdGenerator,
    ): MediaIdentityIdGenerator = generator
}
