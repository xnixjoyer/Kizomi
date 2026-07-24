package com.anisync.android.domain.calendar

import com.anisync.android.domain.provider.ActiveProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarExtensionRegistryTest {
    @Test
    fun `four neutral extensions register enable filter isolate settings and clean lifecycle`() = runTest {
        val enablement = MemoryEnablementStore()
        val settings = MemorySettingsStore()
        val aniNative = FakeExtension("neutral.ani.native", setOf(ActiveProvider.ANILIST_ONLY))
        val malNative = FakeExtension("neutral.mal.native", setOf(ActiveProvider.MAL_ONLY))
        val sharedWidget = FakeExtension(
            "neutral.shared.widget",
            setOf(ActiveProvider.ANILIST_ONLY, ActiveProvider.MAL_ONLY),
            setOf(CalendarCapability.WIDGET),
        )
        val failing = FakeExtension(
            "neutral.failure.isolation",
            setOf(ActiveProvider.MAL_ONLY),
            failLifecycle = "purge",
        )
        val registry = CalendarExtensionRegistry(
            linkedSetOf(malNative, aniNative, sharedWidget, failing),
            enablement,
            settings,
        )

        assertEquals(4, registry.registeredExtensions.size)
        assertEquals(
            listOf("neutral.ani.native", "neutral.shared.widget"),
            registry.availableExtensions(ActiveProvider.ANILIST_ONLY).map { it.extensionId },
        )
        assertTrue(registry.availableExtensions(ActiveProvider.UNCONFIGURED).isEmpty())
        assertEquals(
            listOf("neutral.shared.widget"),
            registry.availableExtensions(
                ActiveProvider.MAL_ONLY,
                CalendarCapability.WIDGET,
            ).map { it.extensionId },
        )

        registry.onProcessRestart(CalendarExtensionContext(ActiveProvider.UNCONFIGURED))
        assertTrue(aniNative.calls.isEmpty())
        assertTrue(malNative.calls.isEmpty())
        assertTrue(sharedWidget.calls.isEmpty())
        assertTrue(failing.calls.isEmpty())

        val malContext = CalendarExtensionContext(ActiveProvider.MAL_ONLY, "account")
        registry.enable(malNative.extensionId, malContext)
        registry.enable(sharedWidget.extensionId, malContext)
        registry.enable(failing.extensionId, malContext)
        assertEquals(3, registry.enabledExtensions(ActiveProvider.MAL_ONLY).size)

        malNative.settings(registry, settings).put("timezone", "UTC")
        sharedWidget.settings(registry, settings).put("timezone", "Europe/Amsterdam")
        assertEquals("UTC", settings.get(malNative.settingsNamespace, "timezone"))
        assertEquals("Europe/Amsterdam", settings.get(sharedWidget.settingsNamespace, "timezone"))

        registry.onAccountChanged(malContext)
        registry.onProcessRestart(malContext)
        registry.disable(sharedWidget.extensionId, malContext)
        assertFalse(sharedWidget.extensionId in enablement.enabledExtensionIds())
        assertTrue("account_changed" in malNative.calls)
        assertTrue("process_restart" in malNative.calls)
        assertTrue("disable" in sharedWidget.calls)

        val purge = registry.onPurge(malContext)
        assertTrue(malNative.extensionId in purge.successfulExtensionIds)
        assertEquals(listOf(failing.extensionId), purge.failures.map { it.extensionId })
        assertTrue(enablement.enabledExtensionIds().isEmpty())
        assertEquals(null, settings.get(malNative.settingsNamespace, "timezone"))
    }

    @Test
    fun `a failed enable is isolated and never persisted`() = runTest {
        val enablement = MemoryEnablementStore()
        val settings = MemorySettingsStore()
        val healthy = FakeExtension("neutral.healthy", setOf(ActiveProvider.MAL_ONLY))
        val failing = FakeExtension(
            "neutral.enable.failure",
            setOf(ActiveProvider.MAL_ONLY),
            failLifecycle = "enable",
        )
        val registry = CalendarExtensionRegistry(setOf(healthy, failing), enablement, settings)
        val context = CalendarExtensionContext(ActiveProvider.MAL_ONLY)

        val failed = registry.enable(failing.extensionId, context)
        val succeeded = registry.enable(healthy.extensionId, context)

        assertEquals(listOf(failing.extensionId), failed.failures.map { it.extensionId })
        assertTrue(succeeded.failures.isEmpty())
        assertEquals(setOf(healthy.extensionId), enablement.enabledExtensionIds())
    }

    private class FakeExtension(
        override val extensionId: String,
        override val supportedProviders: Set<ActiveProvider>,
        override val capabilities: Set<CalendarCapability> =
            setOf(CalendarCapability.NATIVE_SCHEDULE),
        private val failLifecycle: String? = null,
    ) : CalendarExtension {
        override val availability = CalendarExtensionAvailability.AVAILABLE
        override val metadata = CalendarExtensionMetadata(extensionId, "Neutral test extension")
        override val settingsNamespace = extensionId
        val calls = mutableListOf<String>()

        override suspend fun onEnable(
            context: CalendarExtensionContext,
            settings: CalendarExtensionSettings,
        ) = call("enable")

        override suspend fun onDisable(
            context: CalendarExtensionContext,
            settings: CalendarExtensionSettings,
        ) = call("disable")

        override suspend fun onAccountChanged(
            context: CalendarExtensionContext,
            settings: CalendarExtensionSettings,
        ) = call("account_changed")

        override suspend fun onLogout(
            context: CalendarExtensionContext,
            settings: CalendarExtensionSettings,
        ) = call("logout")

        override suspend fun onPurge(
            context: CalendarExtensionContext,
            settings: CalendarExtensionSettings,
        ) = call("purge")

        override suspend fun onProcessRestart(
            context: CalendarExtensionContext,
            settings: CalendarExtensionSettings,
        ) = call("process_restart")

        fun settings(
            registry: CalendarExtensionRegistry,
            store: CalendarExtensionSettingsStore,
        ): CalendarExtensionSettings {
            check(registry.registeredExtensions.any { it.extensionId == extensionId })
            return CalendarExtensionSettings(settingsNamespace, store)
        }

        private fun call(lifecycle: String) {
            calls += lifecycle
            if (failLifecycle == lifecycle) error("isolated failure")
        }
    }

    private class MemoryEnablementStore : CalendarExtensionEnablementStore {
        private var ids = emptySet<String>()
        override suspend fun enabledExtensionIds(): Set<String> = ids
        override suspend fun replaceEnabledExtensionIds(ids: Set<String>) {
            this.ids = ids.toSet()
        }
    }

    private class MemorySettingsStore : CalendarExtensionSettingsStore {
        private val values = mutableMapOf<Pair<String, String>, String>()
        override suspend fun get(namespace: String, key: String): String? = values[namespace to key]
        override suspend fun put(namespace: String, key: String, value: String) {
            values[namespace to key] = value
        }
        override suspend fun remove(namespace: String, key: String) {
            values.remove(namespace to key)
        }
        override suspend fun clear(namespace: String) {
            values.keys.removeAll { it.first == namespace }
        }
    }
}
