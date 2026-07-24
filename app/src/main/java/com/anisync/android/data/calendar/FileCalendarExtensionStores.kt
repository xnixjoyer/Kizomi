package com.anisync.android.data.calendar

import android.content.Context
import com.anisync.android.domain.calendar.CalendarExtensionEnablementStore
import com.anisync.android.domain.calendar.CalendarExtensionSettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileCalendarExtensionStores @Inject constructor(
    @ApplicationContext context: Context,
) : CalendarExtensionEnablementStore, CalendarExtensionSettingsStore {
    private val mutex = Mutex()
    private val directory = File(context.noBackupFilesDir, "calendar_extensions").apply { mkdirs() }
    private val enabledFile = File(directory, "enabled.properties")

    override suspend fun enabledExtensionIds(): Set<String> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val properties = read(enabledFile)
            properties.stringPropertyNames().filterTo(linkedSetOf()) { id ->
                properties.getProperty(id) == "enabled"
            }
        }
    }

    override suspend fun replaceEnabledExtensionIds(ids: Set<String>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val properties = Properties().apply {
                ids.sorted().forEach { setProperty(it, "enabled") }
            }
            writeAtomically(enabledFile, properties)
        }
    }

    override suspend fun get(namespace: String, key: String): String? = mutex.withLock {
        withContext(Dispatchers.IO) { read(settingsFile(namespace)).getProperty(key) }
    }

    override suspend fun put(namespace: String, key: String, value: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val file = settingsFile(namespace)
            val properties = read(file).apply { setProperty(key, value) }
            writeAtomically(file, properties)
        }
    }

    override suspend fun remove(namespace: String, key: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val file = settingsFile(namespace)
            val properties = read(file).apply { remove(key) }
            writeAtomically(file, properties)
        }
    }

    override suspend fun clear(namespace: String) = mutex.withLock {
        withContext(Dispatchers.IO) { settingsFile(namespace).delete() }
    }

    private fun settingsFile(namespace: String): File {
        require(SAFE_NAMESPACE.matches(namespace)) { "Invalid calendar extension settings namespace" }
        return File(directory, "$namespace.properties")
    }

    private fun read(file: File): Properties = Properties().apply {
        if (file.isFile) file.inputStream().buffered().use(::load)
    }

    private fun writeAtomically(file: File, properties: Properties) {
        directory.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.outputStream().buffered().use { properties.store(it, null) }
        check(temporary.renameTo(file) || run {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }) { "Unable to persist calendar extension state" }
    }

    companion object {
        private val SAFE_NAMESPACE = Regex("[a-z0-9][a-z0-9._-]{2,63}")
    }
}
