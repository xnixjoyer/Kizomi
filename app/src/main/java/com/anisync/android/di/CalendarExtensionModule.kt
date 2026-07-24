package com.anisync.android.di

import com.anisync.android.data.calendar.FileCalendarExtensionStores
import com.anisync.android.domain.calendar.CalendarExtension
import com.anisync.android.domain.calendar.CalendarExtensionEnablementStore
import com.anisync.android.domain.calendar.CalendarExtensionRegistry
import com.anisync.android.domain.calendar.CalendarExtensionSettingsStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarExtensionBindings {
    @Multibinds
    abstract fun calendarExtensions(): Set<CalendarExtension>

    @Binds
    abstract fun bindCalendarExtensionEnablementStore(
        implementation: FileCalendarExtensionStores,
    ): CalendarExtensionEnablementStore

    @Binds
    abstract fun bindCalendarExtensionSettingsStore(
        implementation: FileCalendarExtensionStores,
    ): CalendarExtensionSettingsStore
}

@Module
@InstallIn(SingletonComponent::class)
object CalendarExtensionModule {
    @Provides
    @Singleton
    fun provideCalendarExtensionRegistry(
        extensions: Set<@JvmSuppressWildcards CalendarExtension>,
        enablementStore: CalendarExtensionEnablementStore,
        settingsStore: CalendarExtensionSettingsStore,
    ): CalendarExtensionRegistry = CalendarExtensionRegistry(
        extensions = extensions,
        enablementStore = enablementStore,
        settingsStore = settingsStore,
    )
}
