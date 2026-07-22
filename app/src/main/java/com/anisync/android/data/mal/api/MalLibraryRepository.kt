package com.anisync.android.data.mal.api

import androidx.room.withTransaction
import com.anisync.android.data.identity.LocalMediaIdentity
import com.anisync.android.data.identity.LocalMediaType
import com.anisync.android.data.identity.MediaIdentityMappingSource
import com.anisync.android.data.identity.MediaIdentityProvider
import com.anisync.android.data.identity.MediaIdentityResult
import com.anisync.android.data.identity.MediaIdentityStore
import com.anisync.android.data.identity.MediaIdentityVerificationStatus
import com.anisync.android.data.local.AppDatabase
import com.anisync.android.data.local.dao.TrackingDao
import com.anisync.android.data.local.entity.MalImportStateEntity
import com.anisync.android.data.local.entity.MalImportEntryEntity
import com.anisync.android.data.local.entity.MalMediaCacheEntity
import com.anisync.android.data.local.entity.ProviderTrackingSnapshotEntity
import com.anisync.android.domain.tracking.TrackingDesiredState
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.domain.tracking.TrackingStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MalLibraryRepository @Inject constructor(
    private val database: AppDatabase,
    private val dao: TrackingDao,
    private val api: MalListApi,
    private val identities: MediaIdentityStore,
) {
    private val json = Json { encodeDefaults = true }

    fun observeLibrary(
        localAccountId: String,
        mediaType: TrackingMediaType,
    ): Flow<List<MalLibraryItem>> = dao.observeActiveSnapshots(
        provider = MediaIdentityProvider.MYANIMELIST.name,
        providerAccountId = localAccountId,
        mediaType = mediaType.name,
    ).map { snapshots -> snapshots.mapNotNull { snapshot -> snapshot.toLibraryItem() } }

    /**
     * Refreshes one complete MAL list page-by-page while the existing Room flow remains readable.
     * Rows absent remotely are removed only after every page succeeds; any failure keeps last-good
     * rows and records a typed account/type import state.
     */
    suspend fun refresh(
        localAccountId: String,
        mediaType: TrackingMediaType,
        pageSize: Int = MalListRequestFactory.DEFAULT_PAGE_SIZE,
    ): MalImportResult {
        if (localAccountId.isBlank()) {
            return MalImportResult.Failure(
                MalApiFailure(MalApiFailureKind.ACCOUNT_NOT_FOUND),
                preservedEntryCount = 0,
            )
        }
        val previous = dao.getImportState(localAccountId, mediaType.name)
        val previousStagedIds = if (previous?.state == IMPORT_RUNNING) {
            dao.getImportEntryIds(localAccountId, mediaType.name, previous.generation)
        } else {
            emptyList()
        }
        val resumeAfterProcessDeath = previous?.state == IMPORT_RUNNING &&
            (previous.nextPageUrl != null || previousStagedIds.isNotEmpty())
        val generation = if (resumeAfterProcessDeath) {
            requireNotNull(previous).generation
        } else {
            (previous?.generation ?: 0L) + 1L
        }
        val generationStartedAt = if (resumeAfterProcessDeath) {
            requireNotNull(previous).lastAttemptAtEpochMillis
        } else {
            maxOf(
                System.currentTimeMillis(),
                (previous?.lastAttemptAtEpochMillis ?: 0L) + 1L,
            )
        }
        var nextPageUrl: String? = if (resumeAfterProcessDeath) previous?.nextPageUrl else null
        var importedCount = if (resumeAfterProcessDeath) previousStagedIds.size else 0
        database.withTransaction {
            if (!resumeAfterProcessDeath) {
                dao.deleteImportEntries(localAccountId, mediaType.name)
            }
            dao.upsertImportState(
                MalImportStateEntity(
                    localAccountId = localAccountId,
                    mediaType = mediaType.name,
                    state = IMPORT_RUNNING,
                    generation = generation,
                    nextPageUrl = nextPageUrl,
                    importedCount = importedCount,
                    lastAttemptAtEpochMillis = generationStartedAt,
                    lastSuccessAtEpochMillis = previous?.lastSuccessAtEpochMillis,
                    lastErrorKind = null,
                )
            )
        }

        val seenPageUrls = mutableSetOf<String>().apply {
            nextPageUrl?.let { add(it) }
        }
        val seenMediaIds = previousStagedIds.toMutableSet()
        var pageCount = 0
        try {
            var shouldFetchPage = !resumeAfterProcessDeath || nextPageUrl != null
            while (shouldFetchPage) {
                val pageResult = if (nextPageUrl == null) {
                    api.firstPage(localAccountId, mediaType, limit = pageSize)
                } else {
                    api.nextPage(localAccountId, mediaType, requireNotNull(nextPageUrl))
                }
                val page = when (pageResult) {
                    is MalApiResult.Failure -> return failImport(
                        localAccountId,
                        mediaType,
                        generation,
                        generationStartedAt,
                        importedCount,
                        previous?.lastSuccessAtEpochMillis,
                        pageResult.error,
                    )
                    is MalApiResult.Success -> pageResult.value
                }
                pageCount++
                if (pageCount > MAX_PAGE_COUNT || importedCount + page.entries.size > MAX_ENTRY_COUNT) {
                    return failImport(
                        localAccountId,
                        mediaType,
                        generation,
                        generationStartedAt,
                        importedCount,
                        previous?.lastSuccessAtEpochMillis,
                        MalApiFailure(MalApiFailureKind.LIMIT_EXCEEDED),
                    )
                }

                val stagedEntries = mutableListOf<MalImportEntryEntity>()
                for (entry in page.entries) {
                    if (!seenMediaIds.add(entry.malId)) continue
                    val localIdentity = when (val identity = resolveOrCreateIdentity(entry)) {
                        is IdentityResolution.Success -> identity.identity
                        is IdentityResolution.Failure -> return failImport(
                            localAccountId,
                            mediaType,
                            generation,
                            generationStartedAt,
                            importedCount,
                            previous?.lastSuccessAtEpochMillis,
                            identity.error,
                        )
                    }
                    stagedEntries += MalImportEntryEntity(
                        localAccountId = localAccountId,
                        mediaType = mediaType.name,
                        generation = generation,
                        malId = entry.malId,
                        localMediaId = localIdentity.id,
                        payloadJson = json.encodeToString(entry),
                    )
                    importedCount++
                }

                nextPageUrl = page.nextPageUrl
                if (nextPageUrl != null && !seenPageUrls.add(requireNotNull(nextPageUrl))) {
                    return failImport(
                        localAccountId,
                        mediaType,
                        generation,
                        generationStartedAt,
                        importedCount,
                        previous?.lastSuccessAtEpochMillis,
                        MalApiFailure(MalApiFailureKind.PAGING_LOOP),
                    )
                }
                database.withTransaction {
                    if (stagedEntries.isNotEmpty()) dao.upsertImportEntries(stagedEntries)
                    dao.upsertImportState(
                        MalImportStateEntity(
                            localAccountId = localAccountId,
                            mediaType = mediaType.name,
                            state = IMPORT_RUNNING,
                            generation = generation,
                            nextPageUrl = nextPageUrl,
                            importedCount = importedCount,
                            lastAttemptAtEpochMillis = generationStartedAt,
                            lastSuccessAtEpochMillis = previous?.lastSuccessAtEpochMillis,
                            lastErrorKind = null,
                        )
                    )
                }
                shouldFetchPage = nextPageUrl != null
            }

            val stagedEntries = dao.getImportEntries(localAccountId, mediaType.name, generation)
            if (stagedEntries.size != importedCount) {
                return failImport(
                    localAccountId,
                    mediaType,
                    generation,
                    generationStartedAt,
                    importedCount,
                    previous?.lastSuccessAtEpochMillis,
                    MalApiFailure(MalApiFailureKind.STORAGE),
                )
            }
            val decodedEntries = stagedEntries.map { staged ->
                val entry = json.decodeFromString<MalListEntry>(staged.payloadJson)
                require(entry.malId == staged.malId)
                require(entry.mediaType == mediaType)
                staged to entry
            }
            val snapshots = decodedEntries.map { (staged, entry) ->
                entry.toSnapshot(
                    localAccountId = localAccountId,
                    localMediaId = staged.localMediaId,
                    fetchedAtEpochMillis = generationStartedAt,
                )
            }
            val mediaCache = decodedEntries.map { (_, entry) ->
                entry.toCache(generationStartedAt)
            }

            val completedAt = System.currentTimeMillis()
            var removed = 0
            database.withTransaction {
                if (snapshots.isNotEmpty()) dao.upsertSnapshots(snapshots)
                if (mediaCache.isNotEmpty()) dao.upsertMalMedia(mediaCache)
                removed = dao.deleteSnapshotsMissingFromGeneration(
                    provider = MediaIdentityProvider.MYANIMELIST.name,
                    providerAccountId = localAccountId,
                    mediaType = mediaType.name,
                    generationStartedAtEpochMillis = generationStartedAt,
                )
                dao.upsertImportState(
                    MalImportStateEntity(
                        localAccountId = localAccountId,
                        mediaType = mediaType.name,
                        state = IMPORT_SUCCEEDED,
                        generation = generation,
                        nextPageUrl = null,
                        importedCount = importedCount,
                        lastAttemptAtEpochMillis = generationStartedAt,
                        lastSuccessAtEpochMillis = completedAt,
                        lastErrorKind = null,
                    )
                )
                dao.deleteImportEntries(localAccountId, mediaType.name)
            }
            return MalImportResult.Success(importedCount, removed, pageCount)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                database.withTransaction {
                    dao.deleteImportEntries(localAccountId, mediaType.name)
                    dao.upsertImportState(
                        MalImportStateEntity(
                            localAccountId = localAccountId,
                            mediaType = mediaType.name,
                            state = IMPORT_CANCELLED,
                            generation = generation,
                            nextPageUrl = null,
                            importedCount = importedCount,
                            lastAttemptAtEpochMillis = generationStartedAt,
                            lastSuccessAtEpochMillis = previous?.lastSuccessAtEpochMillis,
                            lastErrorKind = MalApiFailureKind.CANCELLED.name,
                        )
                    )
                }
            }
            throw cancelled
        } catch (_: Throwable) {
            return failImport(
                localAccountId,
                mediaType,
                generation,
                generationStartedAt,
                importedCount,
                previous?.lastSuccessAtEpochMillis,
                MalApiFailure(MalApiFailureKind.STORAGE),
            )
        }
    }

    suspend fun deleteLocalAccountData(localAccountId: String) {
        require(localAccountId.isNotBlank())
        database.withTransaction {
            dao.deleteImportEntriesForAccount(localAccountId)
            dao.deleteSnapshotsForAccount(MediaIdentityProvider.MYANIMELIST.name, localAccountId)
            dao.deleteImportStates(localAccountId)
        }
    }

    private suspend fun failImport(
        localAccountId: String,
        mediaType: TrackingMediaType,
        generation: Long,
        generationStartedAtEpochMillis: Long,
        importedCount: Int,
        lastSuccessAtEpochMillis: Long?,
        error: MalApiFailure,
    ): MalImportResult.Failure {
        database.withTransaction {
            dao.deleteImportEntries(localAccountId, mediaType.name)
            dao.upsertImportState(
                MalImportStateEntity(
                    localAccountId = localAccountId,
                    mediaType = mediaType.name,
                    state = IMPORT_FAILED,
                    generation = generation,
                    nextPageUrl = null,
                    importedCount = importedCount,
                    // This timestamp is the generation marker used by the snapshot cleanup query.
                    // Keeping it stable makes an abrupt-process-death checkpoint resumable.
                    lastAttemptAtEpochMillis = generationStartedAtEpochMillis,
                    lastSuccessAtEpochMillis = lastSuccessAtEpochMillis,
                    lastErrorKind = error.kind.name,
                )
            )
        }
        return MalImportResult.Failure(
            error = error,
            preservedEntryCount = dao.countActiveSnapshots(
                MediaIdentityProvider.MYANIMELIST.name,
                localAccountId,
                mediaType.name,
            ),
        )
    }

    private suspend fun resolveOrCreateIdentity(entry: MalListEntry): IdentityResolution {
        val localType = entry.mediaType.toLocalType()
        return when (val existing = identities.resolveByMalId(localType, entry.malId)) {
            is MediaIdentityResult.Success -> existing.value?.let(IdentityResolution::Success)
                ?: createIdentity(entry, localType)
            else -> IdentityResolution.Failure(MalApiFailure(MalApiFailureKind.STORAGE))
        }
    }

    private suspend fun createIdentity(
        entry: MalListEntry,
        localType: LocalMediaType,
    ): IdentityResolution {
        val created = when (val result = identities.createLocalIdentity(localType)) {
            is MediaIdentityResult.Success -> result.value
            else -> return IdentityResolution.Failure(MalApiFailure(MalApiFailureKind.STORAGE))
        }
        return when (identities.attachProviderIdentity(
            localMediaId = created.id,
            provider = MediaIdentityProvider.MYANIMELIST,
            providerMediaId = entry.malId,
            mediaType = localType,
            mappingSource = MediaIdentityMappingSource.MAL_IMPORT,
            verificationStatus = MediaIdentityVerificationStatus.IMPORTED,
        )) {
            is MediaIdentityResult.Success -> IdentityResolution.Success(created)
            is MediaIdentityResult.Conflict -> when (
                val winner = identities.resolveByMalId(localType, entry.malId)
            ) {
                is MediaIdentityResult.Success -> winner.value?.let(IdentityResolution::Success)
                    ?: IdentityResolution.Failure(MalApiFailure(MalApiFailureKind.STORAGE))
                else -> IdentityResolution.Failure(MalApiFailure(MalApiFailureKind.STORAGE))
            }
            else -> IdentityResolution.Failure(MalApiFailure(MalApiFailureKind.STORAGE))
        }
    }

    private fun MalListEntry.toSnapshot(
        localAccountId: String,
        localMediaId: String,
        fetchedAtEpochMillis: Long,
    ) = ProviderTrackingSnapshotEntity(
        provider = MediaIdentityProvider.MYANIMELIST.name,
        providerAccountId = localAccountId,
        localMediaId = localMediaId,
        providerMediaId = malId,
        providerListEntryId = null,
        mediaType = mediaType.name,
        title = title,
        coverUrl = pictureLarge ?: pictureMedium,
        status = desiredState.status?.name ?: TrackingStatus.PLANNING.name,
        progress = desiredState.progress,
        progressSecondary = desiredState.progressSecondary,
        score = desiredState.score100,
        repeatCount = desiredState.repeatCount,
        notes = desiredState.notes,
        startedAt = desiredState.startedAt,
        completedAt = desiredState.completedAt,
        providerUpdatedAtEpochMillis = providerUpdatedAtEpochMillis,
        fetchedAtEpochMillis = fetchedAtEpochMillis,
        rawProviderFieldsJson = rawListStatusJson,
        isDeleted = false,
    )

    private fun MalListEntry.toCache(fetchedAtEpochMillis: Long) = MalMediaCacheEntity(
        malId = malId,
        mediaType = mediaType.name,
        title = title,
        alternativeTitlesJson = json.encodeToString(alternativeTitles),
        synopsis = synopsis,
        mainPictureMedium = pictureMedium,
        mainPictureLarge = pictureLarge,
        meanScore = meanScore,
        rank = rank,
        popularity = popularity,
        mediaStatus = mediaStatus,
        startDate = startDate,
        endDate = endDate,
        episodeCount = episodeCount,
        chapterCount = chapterCount,
        volumeCount = volumeCount,
        genresJson = json.encodeToString(genres),
        relatedJson = "[]",
        recommendationsJson = "[]",
        rawJson = rawMediaJson,
        fetchedAtEpochMillis = fetchedAtEpochMillis,
        expiresAtEpochMillis = fetchedAtEpochMillis + MEDIA_CACHE_TTL_MILLIS,
    )

    private fun ProviderTrackingSnapshotEntity.toLibraryItem(): MalLibraryItem? {
        val type = runCatching { TrackingMediaType.valueOf(mediaType) }.getOrNull() ?: return null
        val normalizedStatus = runCatching { TrackingStatus.valueOf(status) }.getOrNull() ?: return null
        return MalLibraryItem(
            localMediaId = localMediaId,
            malId = providerMediaId,
            mediaType = type,
            title = title,
            coverUrl = coverUrl,
            state = TrackingDesiredState(
                status = normalizedStatus,
                progress = progress,
                progressSecondary = progressSecondary,
                score100 = score,
                repeatCount = repeatCount,
                notes = notes,
                startedAt = startedAt,
                completedAt = completedAt,
            ),
            fetchedAtEpochMillis = fetchedAtEpochMillis,
        )
    }

    private fun TrackingMediaType.toLocalType(): LocalMediaType = when (this) {
        TrackingMediaType.ANIME -> LocalMediaType.ANIME
        TrackingMediaType.MANGA -> LocalMediaType.MANGA
    }

    private sealed interface IdentityResolution {
        data class Success(val identity: LocalMediaIdentity) : IdentityResolution
        data class Failure(val error: MalApiFailure) : IdentityResolution
    }

    companion object {
        private const val IMPORT_RUNNING = "RUNNING"
        private const val IMPORT_SUCCEEDED = "SUCCEEDED"
        private const val IMPORT_FAILED = "FAILED"
        private const val IMPORT_CANCELLED = "CANCELLED"
        private const val MAX_PAGE_COUNT = 10_000
        private const val MAX_ENTRY_COUNT = 50_000
        private const val MEDIA_CACHE_TTL_MILLIS = 7 * 24 * 60 * 60_000L
    }
}
