package com.anisync.android.worker.mal

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import com.anisync.android.domain.calendar.CalendarCapability
import com.anisync.android.domain.calendar.CalendarExtension
import com.anisync.android.domain.calendar.CalendarExtensionAvailability
import com.anisync.android.domain.calendar.CalendarExtensionContext
import com.anisync.android.domain.calendar.CalendarExtensionEnablementStore
import com.anisync.android.domain.calendar.CalendarExtensionMetadata
import com.anisync.android.domain.calendar.CalendarExtensionRegistry
import com.anisync.android.domain.calendar.CalendarExtensionSettings
import com.anisync.android.domain.calendar.CalendarExtensionSettingsStore
import com.anisync.android.domain.provider.ActiveProvider
import com.anisync.android.widget.provider.ProviderCalendarSnapshot
import com.anisync.android.widget.provider.ProviderCalendarSnapshotStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MalCalendarLifecycleTest {
    @Test
    fun `provider change cancels and purges MAL work when MAL becomes inactive`() = runTest {
        val scheduler = RecordingScheduler()
        val store = MemorySnapshotStore(snapshot())
        var memoryPurges = 0
        val controller = MalCalendarLifecycleController(
            scheduler = scheduler,
            purgeMemory = { memoryPurges++ },
            snapshotStore = store,
        )

        controller.onProviderChanged(ActiveProvider.ANILIST_ONLY)

        assertEquals(1, scheduler.cancelCalls)
        assertEquals(0, scheduler.periodicCalls)
        assertEquals(0, scheduler.immediateCalls)
        assertEquals(1, memoryPurges)
        assertNull(store.snapshot)
    }

    @Test
    fun `MAL account change purges old snapshot before scheduling coalesced refresh`() = runTest {
        val scheduler = RecordingScheduler()
        val store = MemorySnapshotStore(snapshot())
        var memoryPurges = 0
        val controller = MalCalendarLifecycleController(
            scheduler = scheduler,
            purgeMemory = { memoryPurges++ },
            snapshotStore = store,
        )

        controller.onAccountChanged(ActiveProvider.MAL_ONLY)

        assertEquals(1, memoryPurges)
        assertNull(store.snapshot)
        assertEquals(1, scheduler.periodicCalls)
        assertEquals(1, scheduler.immediateCalls)
        assertEquals(0, scheduler.cancelCalls)
    }

    @Test
    fun `process restart registers MAL work only for MAL and refreshes only when snapshot is absent`() = runTest {
        val scheduler = RecordingScheduler()
        val store = MemorySnapshotStore(snapshot())
        val controller = MalCalendarLifecycleController(
            scheduler = scheduler,
            purgeMemory = {},
            snapshotStore = store,
        )

        controller.onProcessRestart(ActiveProvider.MAL_ONLY)
        assertEquals(1, scheduler.periodicCalls)
        assertEquals(0, scheduler.immediateCalls)

        store.snapshot = null
        controller.onProcessRestart(ActiveProvider.MAL_ONLY)
        assertEquals(2, scheduler.periodicCalls)
        assertEquals(1, scheduler.immediateCalls)

        controller.onProcessRestart(ActiveProvider.UNCONFIGURED)
        assertEquals(1, scheduler.cancelCalls)
    }

    @Test
    fun `duplicate scheduling uses stable unique names and KEEP policies`() {
        assertTrue(MalCalendarRefreshWorker.PERIODIC_WORK_NAME.isNotBlank())
        assertTrue(MalCalendarRefreshWorker.IMMEDIATE_WORK_NAME.isNotBlank())
        assertTrue(MalCalendarRefreshWorker.PERIODIC_WORK_NAME != MalCalendarRefreshWorker.IMMEDIATE_WORK_NAME)
        assertEquals(ExistingPeriodicWorkPolicy.KEEP, MAL_PERIODIC_DUPLICATE_POLICY)
        assertEquals(ExistingWorkPolicy.KEEP, MAL_IMMEDIATE_DUPLICATE_POLICY)
    }

    @Test
    fun `logout cancels work and purges memory and snapshot through extension registry`() = runTest {
        val scheduler = RecordingScheduler()
        val store = MemorySnapshotStore(snapshot())
        var memoryPurges = 0
        val extension = MalCalendarExtension(
            MalCalendarLifecycleController(scheduler, { memoryPurges++ }, store)
        )
        val registry = registry(extension, enabled = true)

        val report = registry.onLogout(CalendarExtensionContext(ActiveProvider.MAL_ONLY, "account"))

        assertTrue(extension.extensionId in report.successfulExtensionIds)
        assertEquals(1, scheduler.cancelCalls)
        assertEquals(1, memoryPurges)
        assertNull(store.snapshot)
    }

    @Test
    fun `purge cancels work and removes all account bound calendar state`() = runTest {
        val scheduler = RecordingScheduler()
        val store = MemorySnapshotStore(snapshot())
        var memoryPurges = 0
        val extension = MalCalendarExtension(
            MalCalendarLifecycleController(scheduler, { memoryPurges++ }, store)
        )
        val registry = registry(extension, enabled = false)

        val report = registry.onPurge(CalendarExtensionContext(ActiveProvider.MAL_ONLY, "account"))

        assertTrue(extension.extensionId in report.successfulExtensionIds)
        assertEquals(1, scheduler.cancelCalls)
        assertEquals(1, memoryPurges)
        assertNull(store.snapshot)
    }

    @Test
    fun `extension failure is isolated by neutral registry`() = runTest {
        val failingScheduler = RecordingScheduler(failOnCancel = true)
        val controller = MalCalendarLifecycleController(
            scheduler = failingScheduler,
            purgeMemory = {},
            snapshotStore = MemorySnapshotStore(snapshot()),
        )
        val malExtension = MalCalendarExtension(controller)
        val healthy = HealthyExtension()
        val enablement = MemoryEnablementStore(setOf(malExtension.extensionId, healthy.extensionId))
        val registry = CalendarExtensionRegistry(
            setOf(malExtension, healthy),
            enablement,
            MemorySettingsStore(),
        )

        val report = registry.onPurge(CalendarExtensionContext(ActiveProvider.MAL_ONLY))

        assertEquals(listOf(malExtension.extensionId), report.failures.map { it.extensionId })
        assertTrue(healthy.extensionId in report.successfulExtensionIds)
        assertEquals(1, healthy.purgeCalls)
    }

    private fun registry(
        extension: MalCalendarExtension,
        enabled: Boolean,
    ) = CalendarExtensionRegistry(
        setOf(extension),
        MemoryEnablementStore(if (enabled) setOf(extension.extensionId) else emptySet()),
        MemorySettingsStore(),
    )

    private fun snapshot() = ProviderCalendarSnapshot(
        provider = ActiveProvider.MAL_ONLY,
        generatedAtEpochMillis = 1L,
        entries = emptyList(),
    )

    private class RecordingScheduler(
        private val failOnCancel: Boolean = false,
    ) : MalCalendarScheduler {
        var periodicCalls = 0
        var immediateCalls = 0
        var cancelCalls = 0

        override fun schedulePeriodic() {
            periodicCalls++
        }

        override fun enqueueImmediate() {
            immediateCalls++
        }

        override fun cancelAll() {
            cancelCalls++
            if (failOnCancel) error("isolated scheduler failure")
        }
    }

    private class MemorySnapshotStore(
        var snapshot: ProviderCalendarSnapshot?,
    ) : ProviderCalendarSnapshotStore {
        override suspend fun read(expectedProvider: ActiveProvider): ProviderCalendarSnapshot? =
            snapshot?.takeIf { it.provider == expectedProvider }

        override suspend fun write(snapshot: ProviderCalendarSnapshot) {
            this.snapshot = snapshot
        }

        override suspend fun purge() {
            snapshot = null
        }
    }

    private class HealthyExtension : CalendarExtension {
        override val extensionId = "neutral.healthy.lifecycle"
        override val supportedProviders = setOf(ActiveProvider.MAL_ONLY)
        override val capabilities = setOf(CalendarCapability.BACKGROUND_REFRESH)
        override val availability = CalendarExtensionAvailability.AVAILABLE
        override val metadata = CalendarExtensionMetadata("Healthy", "Healthy test extension")
        override val settingsNamespace = extensionId
        var purgeCalls = 0

        override suspend fun onPurge(
            context: CalendarExtensionContext,
            settings: CalendarExtensionSettings,
        ) {
            purgeCalls++
        }
    }

    private class MemoryEnablementStore(
        private var ids: Set<String>,
    ) : CalendarExtensionEnablementStore {
        override suspend fun enabledExtensionIds(): Set<String> = ids
        override suspend fun replaceEnabledExtensionIds(ids: Set<String>) {
            this.ids = ids
        }
    }

    private class MemorySettingsStore : CalendarExtensionSettingsStore {
        override suspend fun get(namespace: String, key: String): String? = null
        override suspend fun put(namespace: String, key: String, value: String) = Unit
        override suspend fun remove(namespace: String, key: String) = Unit
        override suspend fun clear(namespace: String) = Unit
    }
}
