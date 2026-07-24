package com.anisync.android.domain.calendar

import com.anisync.android.domain.provider.ActiveProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Capabilities advertised by a modular calendar extension. */
enum class CalendarCapability {
    NATIVE_SCHEDULE,
    EXTERNAL_SCHEDULE,
    WIDGET,
    BACKGROUND_REFRESH,
}

enum class CalendarExtensionAvailability {
    AVAILABLE,
    DEGRADED,
    UNAVAILABLE,
}

data class CalendarExtensionMetadata(
    val displayName: String,
    val description: String,
)

data class CalendarExtensionContext(
    val activeProvider: ActiveProvider,
    val accountKey: String? = null,
)

/**
 * Public, provider-neutral extension contract. Implementations own no global settings keys and must
 * tolerate lifecycle callbacks after process recreation or a partial provider transition.
 */
interface CalendarExtension {
    val extensionId: String
    val supportedProviders: Set<ActiveProvider>
    val capabilities: Set<CalendarCapability>
    val availability: CalendarExtensionAvailability
    val metadata: CalendarExtensionMetadata
    val settingsNamespace: String

    suspend fun onEnable(context: CalendarExtensionContext, settings: CalendarExtensionSettings) = Unit
    suspend fun onDisable(context: CalendarExtensionContext, settings: CalendarExtensionSettings) = Unit
    suspend fun onAccountChanged(context: CalendarExtensionContext, settings: CalendarExtensionSettings) = Unit
    suspend fun onLogout(context: CalendarExtensionContext, settings: CalendarExtensionSettings) = Unit
    suspend fun onPurge(context: CalendarExtensionContext, settings: CalendarExtensionSettings) = Unit
    suspend fun onProcessRestart(context: CalendarExtensionContext, settings: CalendarExtensionSettings) = Unit
}

interface CalendarExtensionEnablementStore {
    suspend fun enabledExtensionIds(): Set<String>
    suspend fun replaceEnabledExtensionIds(ids: Set<String>)
}

interface CalendarExtensionSettingsStore {
    suspend fun get(namespace: String, key: String): String?
    suspend fun put(namespace: String, key: String, value: String)
    suspend fun remove(namespace: String, key: String)
    suspend fun clear(namespace: String)
}

class CalendarExtensionSettings internal constructor(
    private val namespace: String,
    private val store: CalendarExtensionSettingsStore,
) {
    suspend fun get(key: String): String? = store.get(namespace, checkedKey(key))

    suspend fun put(key: String, value: String) {
        store.put(namespace, checkedKey(key), value)
    }

    suspend fun remove(key: String) {
        store.remove(namespace, checkedKey(key))
    }

    suspend fun clear() = store.clear(namespace)

    private fun checkedKey(key: String): String {
        require(key.isNotBlank()) { "Calendar extension settings keys must not be blank" }
        return key
    }
}

data class CalendarExtensionFailure(
    val extensionId: String,
    val lifecycle: String,
)

data class CalendarExtensionDispatchReport(
    val successfulExtensionIds: Set<String>,
    val failures: List<CalendarExtensionFailure>,
)

/**
 * Isolated registry for independently enabled extensions. Every callback is caught per extension;
 * one failing implementation cannot block another or cause a provider fallback.
 */
class CalendarExtensionRegistry(
    extensions: Set<CalendarExtension>,
    private val enablementStore: CalendarExtensionEnablementStore,
    private val settingsStore: CalendarExtensionSettingsStore,
) {
    private val mutex = Mutex()
    val registeredExtensions: List<CalendarExtension> = extensions.sortedBy { it.extensionId }
    private val byId = registeredExtensions.associateBy { it.extensionId }

    init {
        require(byId.size == registeredExtensions.size) { "Calendar extension IDs must be unique" }
        require(registeredExtensions.none { it.extensionId.isBlank() }) { "Calendar extension IDs must not be blank" }
        require(registeredExtensions.none { it.settingsNamespace.isBlank() }) {
            "Calendar extension settings namespaces must not be blank"
        }
        require(registeredExtensions.map { it.settingsNamespace }.toSet().size == registeredExtensions.size) {
            "Calendar extension settings namespaces must be unique"
        }
        require(registeredExtensions.none { ActiveProvider.UNCONFIGURED in it.supportedProviders }) {
            "Calendar extensions cannot support UNCONFIGURED"
        }
    }

    suspend fun availableExtensions(
        activeProvider: ActiveProvider,
        capability: CalendarCapability? = null,
    ): List<CalendarExtension> {
        if (activeProvider == ActiveProvider.UNCONFIGURED) return emptyList()
        return registeredExtensions.filter { extension ->
            activeProvider in extension.supportedProviders &&
                extension.availability != CalendarExtensionAvailability.UNAVAILABLE &&
                (capability == null || capability in extension.capabilities)
        }
    }

    suspend fun enabledExtensions(
        activeProvider: ActiveProvider,
        capability: CalendarCapability? = null,
    ): List<CalendarExtension> {
        val enabled = enablementStore.enabledExtensionIds()
        return availableExtensions(activeProvider, capability).filter { it.extensionId in enabled }
    }

    suspend fun enable(extensionId: String, context: CalendarExtensionContext): CalendarExtensionDispatchReport =
        mutex.withLock {
            val extension = requireNotNull(byId[extensionId]) { "Unknown calendar extension" }
            require(context.activeProvider in extension.supportedProviders) {
                "Calendar extension is unavailable for the active provider"
            }
            require(extension.availability != CalendarExtensionAvailability.UNAVAILABLE) {
                "Calendar extension is unavailable"
            }
            val settings = settings(extension)
            val failure = runCatching { extension.onEnable(context, settings) }.exceptionOrNull()
            if (failure == null) {
                enablementStore.replaceEnabledExtensionIds(
                    enablementStore.enabledExtensionIds() + extensionId
                )
                CalendarExtensionDispatchReport(setOf(extensionId), emptyList())
            } else {
                CalendarExtensionDispatchReport(
                    emptySet(),
                    listOf(CalendarExtensionFailure(extensionId, "enable")),
                )
            }
        }

    suspend fun disable(extensionId: String, context: CalendarExtensionContext): CalendarExtensionDispatchReport =
        mutex.withLock {
            val extension = requireNotNull(byId[extensionId]) { "Unknown calendar extension" }
            enablementStore.replaceEnabledExtensionIds(
                enablementStore.enabledExtensionIds() - extensionId
            )
            dispatch(listOf(extension), "disable", context) { candidate, settings ->
                candidate.onDisable(context, settings)
            }
        }

    suspend fun disableAll(context: CalendarExtensionContext): CalendarExtensionDispatchReport = mutex.withLock {
        val enabled = enablementStore.enabledExtensionIds()
        enablementStore.replaceEnabledExtensionIds(emptySet())
        dispatch(registeredExtensions.filter { it.extensionId in enabled }, "disable", context) { extension, settings ->
            extension.onDisable(context, settings)
        }
    }

    suspend fun onAccountChanged(context: CalendarExtensionContext): CalendarExtensionDispatchReport =
        dispatchEnabled("account_changed", context) { extension, settings ->
            extension.onAccountChanged(context, settings)
        }

    suspend fun onLogout(context: CalendarExtensionContext): CalendarExtensionDispatchReport =
        dispatchEnabled("logout", context) { extension, settings ->
            extension.onLogout(context, settings)
        }

    suspend fun onPurge(context: CalendarExtensionContext): CalendarExtensionDispatchReport {
        val report = dispatch(registeredExtensions, "purge", context) { extension, settings ->
            extension.onPurge(context, settings)
        }
        registeredExtensions.forEach { settingsStore.clear(it.settingsNamespace) }
        enablementStore.replaceEnabledExtensionIds(emptySet())
        return report
    }

    suspend fun onProcessRestart(context: CalendarExtensionContext): CalendarExtensionDispatchReport =
        dispatchEnabled("process_restart", context) { extension, settings ->
            extension.onProcessRestart(context, settings)
        }

    private suspend fun dispatchEnabled(
        lifecycle: String,
        context: CalendarExtensionContext,
        callback: suspend (CalendarExtension, CalendarExtensionSettings) -> Unit,
    ): CalendarExtensionDispatchReport {
        if (context.activeProvider == ActiveProvider.UNCONFIGURED) {
            return CalendarExtensionDispatchReport(emptySet(), emptyList())
        }
        val enabled = enablementStore.enabledExtensionIds()
        val eligible = registeredExtensions.filter { extension ->
            extension.extensionId in enabled &&
                context.activeProvider in extension.supportedProviders &&
                extension.availability != CalendarExtensionAvailability.UNAVAILABLE
        }
        return dispatch(eligible, lifecycle, context, callback)
    }

    private suspend fun dispatch(
        extensions: List<CalendarExtension>,
        lifecycle: String,
        context: CalendarExtensionContext,
        callback: suspend (CalendarExtension, CalendarExtensionSettings) -> Unit,
    ): CalendarExtensionDispatchReport {
        val successes = linkedSetOf<String>()
        val failures = mutableListOf<CalendarExtensionFailure>()
        extensions.forEach { extension ->
            runCatching { callback(extension, settings(extension)) }
                .onSuccess { successes += extension.extensionId }
                .onFailure { failures += CalendarExtensionFailure(extension.extensionId, lifecycle) }
        }
        return CalendarExtensionDispatchReport(successes, failures)
    }

    private fun settings(extension: CalendarExtension) =
        CalendarExtensionSettings(extension.settingsNamespace, settingsStore)
}
