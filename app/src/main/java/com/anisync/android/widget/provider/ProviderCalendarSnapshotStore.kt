package com.anisync.android.widget.provider

import android.content.Context
import android.util.AtomicFile
import com.anisync.android.domain.calendar.provider.ProviderCalendarEntry
import com.anisync.android.domain.calendar.provider.ProviderCalendarMediaType
import com.anisync.android.domain.calendar.provider.ProviderCalendarPrecision
import com.anisync.android.domain.provider.ActiveProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ProviderCalendarSnapshot(
    val provider: ActiveProvider,
    val generatedAtEpochMillis: Long,
    val entries: List<ProviderCalendarEntry>,
) {
    init {
        require(provider != ActiveProvider.UNCONFIGURED)
        require(generatedAtEpochMillis >= 0L)
        require(entries.all { it.provider == provider })
    }

    override fun toString(): String =
        "ProviderCalendarSnapshot(provider=${provider.name}, generatedAtEpochMillis=$generatedAtEpochMillis, " +
            "entryCount=${entries.size})"
}

interface ProviderCalendarSnapshotStore {
    suspend fun read(expectedProvider: ActiveProvider): ProviderCalendarSnapshot?
    suspend fun write(snapshot: ProviderCalendarSnapshot)
    suspend fun purge()
}

/**
 * Account-bound widget snapshot stored in no-backup storage. It contains no account identifier,
 * credential, provider payload, or alternate-provider data and is deleted on any lifecycle purge.
 */
@Singleton
class FileProviderCalendarSnapshotStore internal constructor(
    file: File,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ProviderCalendarSnapshotStore {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        File(context.noBackupFilesDir, FILE_NAME)
    )

    private val file = file
    private val atomicFile = AtomicFile(file)

    override suspend fun read(expectedProvider: ActiveProvider): ProviderCalendarSnapshot? =
        withContext(Dispatchers.IO) {
            if (expectedProvider == ActiveProvider.UNCONFIGURED || !file.exists()) return@withContext null
            val wire = runCatching {
                atomicFile.openRead().bufferedReader(Charsets.UTF_8).use { reader ->
                    json.decodeFromString<WireSnapshot>(reader.readText())
                }
            }.getOrElse {
                atomicFile.delete()
                return@withContext null
            }
            val provider = runCatching { ActiveProvider.valueOf(wire.provider) }.getOrNull()
            if (provider == null || provider != expectedProvider || provider == ActiveProvider.UNCONFIGURED) {
                atomicFile.delete()
                return@withContext null
            }
            val entries = wire.entries.mapNotNull { it.toDomain() }
            if (entries.size != wire.entries.size || entries.any { it.provider != provider }) {
                atomicFile.delete()
                return@withContext null
            }
            runCatching {
                ProviderCalendarSnapshot(provider, wire.generatedAtEpochMillis, entries)
            }.getOrElse {
                atomicFile.delete()
                null
            }
        }

    override suspend fun write(snapshot: ProviderCalendarSnapshot) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val wire = WireSnapshot(
            provider = snapshot.provider.name,
            generatedAtEpochMillis = snapshot.generatedAtEpochMillis,
            entries = snapshot.entries.map { WireEntry.fromDomain(it) },
        )
        val bytes = json.encodeToString(wire).toByteArray(Charsets.UTF_8)
        val output = atomicFile.startWrite()
        try {
            output.write(bytes)
            atomicFile.finishWrite(output)
        } catch (failure: Throwable) {
            atomicFile.failWrite(output)
            throw failure
        }
    }

    override suspend fun purge() = withContext(Dispatchers.IO) {
        atomicFile.delete()
    }

    @Serializable
    private data class WireSnapshot(
        val provider: String,
        val generatedAtEpochMillis: Long,
        val entries: List<WireEntry>,
    )

    @Serializable
    private data class WireEntry(
        val provider: String,
        val providerMediaId: Long,
        val mediaType: String,
        val title: String,
        val coverUrl: String? = null,
        val scheduledAtEpochSeconds: Long,
        val episodeNumber: Int? = null,
        val isOnList: Boolean,
        val precision: String,
    ) {
        fun toDomain(): ProviderCalendarEntry? {
            val providerValue = runCatching { ActiveProvider.valueOf(provider) }.getOrNull() ?: return null
            val mediaTypeValue = runCatching { ProviderCalendarMediaType.valueOf(mediaType) }.getOrNull()
                ?: return null
            val precisionValue = runCatching { ProviderCalendarPrecision.valueOf(precision) }.getOrNull()
                ?: return null
            return runCatching {
                ProviderCalendarEntry(
                    provider = providerValue,
                    providerMediaId = providerMediaId,
                    mediaType = mediaTypeValue,
                    title = title,
                    coverUrl = coverUrl,
                    scheduledAtEpochSeconds = scheduledAtEpochSeconds,
                    episodeNumber = episodeNumber,
                    isOnList = isOnList,
                    precision = precisionValue,
                )
            }.getOrNull()
        }

        companion object {
            fun fromDomain(entry: ProviderCalendarEntry): WireEntry = WireEntry(
                provider = entry.provider.name,
                providerMediaId = entry.providerMediaId,
                mediaType = entry.mediaType.name,
                title = entry.title,
                coverUrl = entry.coverUrl,
                scheduledAtEpochSeconds = entry.scheduledAtEpochSeconds,
                episodeNumber = entry.episodeNumber,
                isOnList = entry.isOnList,
                precision = entry.precision.name,
            )
        }
    }

    private companion object {
        const val FILE_NAME = "provider_calendar_widget_snapshot_v1.json"
    }
}
