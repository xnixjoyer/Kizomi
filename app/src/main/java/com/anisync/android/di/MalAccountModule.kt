package com.anisync.android.di

import com.anisync.android.data.mal.account.AndroidMalTokenVault
import com.anisync.android.data.mal.account.MalAccountClock
import com.anisync.android.data.mal.account.MalAccountCredentialStore
import com.anisync.android.data.mal.account.MalAccountRepository
import com.anisync.android.data.mal.account.MalAccountIdGenerator
import com.anisync.android.data.mal.account.MalTokenVault
import com.anisync.android.data.mal.account.SystemMalAccountClock
import com.anisync.android.data.mal.account.UuidMalAccountIdGenerator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MalAccountModule {
    @Binds
    @Singleton
    abstract fun bindMalAccountCredentialStore(
        implementation: MalAccountRepository,
    ): MalAccountCredentialStore

    @Binds
    @Singleton
    abstract fun bindMalTokenVault(implementation: AndroidMalTokenVault): MalTokenVault

    @Binds
    @Singleton
    abstract fun bindMalAccountClock(implementation: SystemMalAccountClock): MalAccountClock

    @Binds
    @Singleton
    abstract fun bindMalAccountIdGenerator(
        implementation: UuidMalAccountIdGenerator,
    ): MalAccountIdGenerator
}
