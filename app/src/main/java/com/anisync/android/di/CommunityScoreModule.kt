package com.anisync.android.di

import com.anisync.android.BuildConfig
import com.anisync.android.data.communityscore.DefaultCommunityScoreRepository
import com.anisync.android.data.communityscore.JikanCommunityScoreClient
import com.anisync.android.domain.CommunityScoreRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CommunityScoreHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class CommunityScoreBindingsModule {
    @Binds
    @Singleton
    abstract fun bindCommunityScoreRepository(
        implementation: DefaultCommunityScoreRepository
    ): CommunityScoreRepository
}

@Module
@InstallIn(SingletonComponent::class)
object CommunityScoreNetworkModule {
    @Provides
    @Singleton
    @CommunityScoreHttpClient
    fun provideCommunityScoreHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    @Provides
    @Singleton
    fun provideJikanCommunityScoreClient(
        @CommunityScoreHttpClient client: OkHttpClient
    ): JikanCommunityScoreClient = JikanCommunityScoreClient(
        client = client,
        userAgent = "AniSyncPlus/${BuildConfig.VERSION_NAME} (read-only community scores)"
    )
}
