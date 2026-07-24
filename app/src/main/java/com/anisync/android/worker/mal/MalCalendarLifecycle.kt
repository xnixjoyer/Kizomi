package com.anisync.android.worker.mal

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.anisync.android.data.mal.calendar.MalCalendarRepository
import com.anisync.android.domain.calendar.CalendarCapability
import com.anisync.android.domain.calendar.CalendarExtension
import com.anisync.android.domain.calendar.CalendarExtensionAvailability
import com.anisync.android.domain.calendar.CalendarExtensionContext
import com.anisync.android.domain.calendar.CalendarExtensionMetadata
import com.anisync.android.domain.calendar.CalendarExtensionSettings
import com.anisync.android.domain.provider.ActiveProvider
import com.anisync.android.widget.provider.FileProviderCalendarSnapshotStore
import com.anisync.android.widget.provider.ProviderCalendarSnapshotStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface MalCalendarScheduler {
    fun schedulePeriodic()
    fun enqueueImmediate()
    fun cancelAll()
}

@Singleton
class WorkManagerMalCalendarScheduler @Inject constructor(
    @ApplicationContext context: Context,
) : MalCalendarScheduler {
    private val workManager = WorkManager.getInstance(context)

    override fun schedulePeriodic() {
        workManager.enqueueUniquePeriodicWork(
            MalCalendarRefreshWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            MalCalendarRefreshWorker.periodicRequest(),
        )
    }

    override fun enqueueImmediate() {
        workManager.enqueueUniqueWork(
            MalCalendarRefreshWorker.IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            MalCalendarRefreshWorker.immediateRequest(),
        )
    }

    override fun cancelAll() {
        workManager.cancelUniqueWork(MalCalendarRefreshWorker.PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(MalCalendarRefreshWorker.IMMEDIATE_WORK_NAME)
    }
}

@Singleton
class MalCalendarLifecycleController internal constructor(
    private val scheduler: MalCalendarScheduler,
    private val purgeMemory: suspend () -> Unit,
    private val snapshotStore: ProviderCalendarSnapshotStore,
) {
    @Inject
    constructor(
        scheduler: WorkManagerMalCalendarScheduler,
        repository: MalCalendarRepository,
        snapshotStore: FileProviderCalendarSnapshotStore,
    ) : this(
        scheduler = scheduler,
        purgeMemory = repository::purgeMemory,
        snapshotStore = snapshotStore,
    )

    suspend fun onProviderChanged(activeProvider: ActiveProvider) {
        if (activeProvider == ActiveProvider.MAL_ONLY) {
            purgeAccountBoundData()
            scheduler.schedulePeriodic()
            scheduler.enqueueImmediate()
        } else {
            deactivateAndPurge()
        }
    }

    suspend fun onProcessRestart(activeProvider: ActiveProvider) {
        if (activeProvider != ActiveProvider.MAL_ONLY) {
            deactivateAndPurge()
            return
        }
        scheduler.schedulePeriodic()
        if (snapshotStore.read(ActiveProvider.MAL_ONLY) == null) {
            scheduler.enqueueImmediate()
        }
    }

    suspend fun deactivateAndPurge() {
        scheduler.cancelAll()
        purgeAccountBoundData()
    }

    private suspend fun purgeAccountBoundData() {
        purgeMemory()
        snapshotStore.purge()
    }
}

/**
 * Provider-native calendar extension. Availability is degraded because MAL exposes recurring
 * broadcast metadata but not an exact episode-airing schedule or notification feed.
 */
@Singleton
class MalCalendarExtension @Inject constructor(
    private val lifecycleController: MalCalendarLifecycleController,
) : CalendarExtension {
    override val extensionId: String = "calendar.provider.native.broadcast"
    override val supportedProviders: Set<ActiveProvider> = setOf(ActiveProvider.MAL_ONLY)
    override val capabilities: Set<CalendarCapability> = setOf(
        CalendarCapability.NATIVE_SCHEDULE,
        CalendarCapability.WIDGET,
        CalendarCapability.BACKGROUND_REFRESH,
    )
    override val availability: CalendarExtensionAvailability = CalendarExtensionAvailability.DEGRADED
    override val metadata: CalendarExtensionMetadata = CalendarExtensionMetadata(
        displayName = "Provider broadcast calendar",
        description = "Recurring provider-native broadcast slots with explicit capability limits",
    )
    override val settingsNamespace: String = extensionId

    override suspend fun onEnable(
        context: CalendarExtensionContext,
        settings: CalendarExtensionSettings,
    ) = lifecycleController.onProviderChanged(context.activeProvider)

    override suspend fun onDisable(
        context: CalendarExtensionContext,
        settings: CalendarExtensionSettings,
    ) = lifecycleController.deactivateAndPurge()

    override suspend fun onAccountChanged(
        context: CalendarExtensionContext,
        settings: CalendarExtensionSettings,
    ) = lifecycleController.onProviderChanged(context.activeProvider)

    override suspend fun onLogout(
        context: CalendarExtensionContext,
        settings: CalendarExtensionSettings,
    ) = lifecycleController.deactivateAndPurge()

    override suspend fun onPurge(
        context: CalendarExtensionContext,
        settings: CalendarExtensionSettings,
    ) = lifecycleController.deactivateAndPurge()

    override suspend fun onProcessRestart(
        context: CalendarExtensionContext,
        settings: CalendarExtensionSettings,
    ) = lifecycleController.onProcessRestart(context.activeProvider)
}
