package com.anisync.android.di

import com.anisync.android.data.calendar.AniListCalendarProvider
import com.anisync.android.data.calendar.DefaultCalendarProviderRegistry
import com.anisync.android.domain.calendar.CalendarProvider
import com.anisync.android.domain.calendar.CalendarProviderRegistry
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarProviderModule {
    @Binds
    @IntoSet
    abstract fun bindAniListCalendarProvider(
        implementation: AniListCalendarProvider
    ): CalendarProvider

    @Binds
    @Singleton
    abstract fun bindCalendarProviderRegistry(
        implementation: DefaultCalendarProviderRegistry
    ): CalendarProviderRegistry
}
