package com.anisync.android.di

import com.anisync.android.data.tracking.TrackingOutboxScheduler
import com.anisync.android.domain.tracking.TrackingProviderAdapter
import com.anisync.android.worker.AndroidTrackingOutboxScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TrackingModule {
    @Binds
    @Singleton
    abstract fun bindTrackingOutboxScheduler(
        implementation: AndroidTrackingOutboxScheduler,
    ): TrackingOutboxScheduler

    @Multibinds
    abstract fun trackingProviderAdapters(): Set<TrackingProviderAdapter>
}
