package com.anisync.android.di

import com.anisync.android.data.mal.oauth.AndroidMalOAuthSessionStore
import com.anisync.android.data.mal.oauth.BuildConfigMalOAuthConfigurationProvider
import com.anisync.android.data.mal.oauth.MalOAuthConfigurationSource
import com.anisync.android.data.mal.oauth.MalOAuthClock
import com.anisync.android.data.mal.oauth.MalOAuthSessionIdGenerator
import com.anisync.android.data.mal.oauth.MalOAuthSessionStore
import com.anisync.android.data.mal.oauth.MalOAuthTokenService
import com.anisync.android.data.mal.oauth.OkHttpMalOAuthTokenService
import com.anisync.android.data.mal.oauth.SystemMalOAuthClock
import com.anisync.android.data.mal.oauth.UuidMalOAuthSessionIdGenerator
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
annotation class MalOAuthHttpClient

@Module
@InstallIn(SingletonComponent::class)
object MalOAuthNetworkModule {
    @Provides
    @Singleton
    @MalOAuthHttpClient
    fun provideMalOAuthHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MalOAuthBindingsModule {
    @Binds
    @Singleton
    abstract fun bindMalOAuthConfigurationSource(
        implementation: BuildConfigMalOAuthConfigurationProvider,
    ): MalOAuthConfigurationSource

    @Binds
    @Singleton
    abstract fun bindMalOAuthSessionStore(
        implementation: AndroidMalOAuthSessionStore,
    ): MalOAuthSessionStore

    @Binds
    @Singleton
    abstract fun bindMalOAuthClock(
        implementation: SystemMalOAuthClock,
    ): MalOAuthClock

    @Binds
    @Singleton
    abstract fun bindMalOAuthSessionIdGenerator(
        implementation: UuidMalOAuthSessionIdGenerator,
    ): MalOAuthSessionIdGenerator

    @Binds
    @Singleton
    abstract fun bindMalOAuthTokenService(
        implementation: OkHttpMalOAuthTokenService,
    ): MalOAuthTokenService
}
