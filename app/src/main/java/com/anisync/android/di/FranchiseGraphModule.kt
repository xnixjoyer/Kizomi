package com.anisync.android.di

import com.anisync.android.data.FranchiseGraphRepositoryImpl
import com.anisync.android.domain.FranchiseGraphRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FranchiseGraphModule {
    @Binds
    @Singleton
    abstract fun bindFranchiseGraphRepository(
        implementation: FranchiseGraphRepositoryImpl
    ): FranchiseGraphRepository
}
