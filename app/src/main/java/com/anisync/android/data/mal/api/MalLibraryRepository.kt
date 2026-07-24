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
import com.anisync.android.data.local.entity.MalLibraryRefreshEntryEntity
import com.anisync.android.data.local.entity.MalLibraryRefreshStateEntity
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
     * Refreshes a complete provider-native list into normalized staging rows. Existing last-good
     * snapshots remain readable until every page succeeds and the new generation is promoted.
     */
    suspend fun refresh(
        localAccountId: String,
        mediaType: TrackingMediaType,
        pageSize: Int = MalListRequestFactory.DEFAULT_PAGE_SIZE,
    ): MalLibraryRefreshResult {
        if (localAccountId.isBlank()) {
            return MalLibraryRefreshResult.Failure(
                MalApiFailure(MalApiFailureKind.ACCOUNT_NOT_FOUND),
                preservedEntryCount = 0,
            )
        }
        val previous = dao.getLibraryRefreshState(localAccountId, mediaType.name)
        val previousStagedIds = if (previous?.state == REFRESH_RUNNING) {
            dao.getLibraryRefreshEntryIds(localAccountId, mediaType.name, previous.generation)
        } else {
            emptyList()
        }
        val resume = previous?.state == REFRESH_RUNNING &&
            (previous.nextPageUrl != null || previousStagedIds.isNotEmpty())
        val generation = if (resume) requireNotNull(previous).generation else (previous?.generation ?: 0L) + 1L
        val generationStartedAt = if (resume) {
            requireNotNull(previous).lastAttemptAtEpochMillis
        } else {
            maxOf(System.currentTimeMillis(), (previous?.lastAttemptAtEpochMillis ?: 0L) + 1L)
        }
        var nextPageUrl = if (resume) previous?.nextPageUrl else null
        var itemCount = if (resume) previousStagedIds.size else 0
        database.withTransaction {
            if (!resume) dao.deleteLibraryRefreshEntries(localAccountId, mediaType.name)
            dao.upsertLibraryRefreshState(
                MalLibraryRefreshStateEntity(
                    localAccountId = localAccountId,
                    mediaType = mediaType.name,
                    state = REFRESH_RUNNING,
                    generation = generation,
                    nextPageUrl = nextPageUrl,
                    itemCount = itemCount,
                    lastAttemptAtEpochMillis = generationStartedAt,
                    lastSuccessAtEpochMillis = previous?.lastSuccessAtEpochMillis,
                    lastErrorKind = null,
                )
            )
        }

        val seenPageUrls = mutableSetOf<String>().apply { nextPageUrl?.let(::add) }
        val seenMediaIds = previousStagedIds.toMutableSet()
        var pageCount = 0
        try {
            var fetchPage = !resume || nextPageUrl != null
            while (fetchPage) {
                val result = if (nextPageUrl == null) {
                    api.firstPage(localAccountId, mediaType, limit = pageSize)
                } else {
                    api.nextPage(localAccountId, mediaType, requireNotNull(nextPageUrl))
                }
                val page = when (result) {
                    is MalApiResult.Failure -> return failRefresh(
                        localAccountId, mediaType, generation, generationStartedAt, itemCount,
                        previous?.lastSuccessAtEpochMillis, result.error,
                    )
                    is MalApiResult.Success -> result.value
                }
                pageCount++
                if (pageCount > MAX_PAGE_COUNT || itemCount + page.entries.size > MAX_ENTRY_COUNT) {
                    return failRefresh(
                        localAccountId, mediaType, generation, generationStartedAt, itemCount,
                        previous?.lastSuccessAtEpochMillis,
                        MalApiFailure(MalApiFailureKind.LIMIT_EXCEEDED),
                    )
                }

                val staged = mutableListOf<MalLibraryRefreshEntryEntity>()
                for (entry in page.entries) {
                    if (!seenMediaIds.add(entry.malId)) continue
                    val localIdentity = when (val resolution = resolveOrCreateIdentity(entry)) {
                        is IdentityResolution.Success -> resolution.identity
                        is IdentityResolution.Failure -> return failRefresh(
                            localAccountId, mediaType, generation, generationStartedAt, itemCount,
                            previous?.lastSuccessAtEpochMillis, resolution.error,
                        )
                    }
                    staged += entry.toRefreshEntry(localAccountId, generation, localIdentity.id)
                    itemCount++
                }

                nextPageUrl = page.nextPageUrl
                if (nextPageUrl != null && !seenPageUrls.add(requireNotNull(nextPageUrl))) {
                    return failRefresh(
                        localAccountId, mediaType, generation, generationStartedAt, itemCount,
                        previous?.lastSuccessAtEpochMillis,
                        MalApiFailure(MalApiFailureKind.PAGING_LOOP),
                    )
                }
                database.withTransaction {
                    if (staged.isNotEmpty()) dao.upsertLibraryRefreshEntries(staged)
                    dao.upsertLibraryRefreshState(
                        MalLibraryRefreshStateEntity(
                            localAccountId = localAccountId,
                            mediaType = mediaType.name,
                            state = REFRESH_RUNNING,
                            generation = generation,
                            nextPageUrl = nextPageUrl,
                            itemCount = itemCount,
                            lastAttemptAtEpochMillis = generationStartedAt,
                            lastSuccessAtEpochMillis = previous?.lastSuccessAtEpochMillis,
                            lastErrorKind = null,
                        )
                    )
                }
                fetchPage = nextPageUrl != null
            }

            val staged = dao.getLibraryRefreshEntries(localAccountId, mediaType.name, generation)
            if (staged.size != itemCount) {
                return failRefresh(
                    localAccountId, mediaType, generation, generationStartedAt, itemCount,
                    previous?.lastSuccessAtEpochMillis,
                    MalApiFailure(MalApiFailureKind.STORAGE),
                )
            }
            val snapshots = staged.map { it.toSnapshot(generationStartedAt) }
            val mediaCache = staged.map { it.toCache(generationStartedAt) }
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
                dao.upsertLibraryRefreshState(
                    MalLibraryRefreshStateEntity(
                        localAccountId = localAccountId,
                        mediaType = mediaType.name,
                        state = REFRESH_SUCCEEDED,
                        generation = generation,
                        nextPageUrl = null,
                        itemCount = itemCount,
                        lastAttemptAtEpochMillis = generationStartedAt,
                        lastSuccessAtEpochMillis = completedAt,
                        lastErrorKind = null,
                    )
                )
                dao.deleteLibraryRefreshEntries(localAccountId, mediaType.name)
            }
            return MalLibraryRefreshResult.Success(itemCount, removed, pageCount)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                database.withTransaction {
                    dao.deleteLibraryRefreshEntries(localAccountId, mediaType.name)
                    dao.upsertLibraryRefreshState(
                        MalLibraryRefreshStateEntity(
                            localAccountId = localAccountId,
                            mediaType = mediaType.name,
                            state = REFRESH_CANCELLED,
                            generation = generation,
                            nextPageUrl = null,
                            itemCount = itemCount,
                            lastAttemptAtEpochMillis = generationStartedAt,
                            lastSuccessAtEpochMillis = previous?.lastSuccessAtEpochMillis,
                            lastErrorKind = MalApiFailureKind.CANCELLED.name,
                        )
                    )
                }
            }
            throw cancelled
        } catch (_: Throwable) {
            return failRefresh(
                localAccountId, mediaType, generation, generationStartedAt, itemCount,
                previous?.lastSuccessAtEpochMillis,
                MalApiFailure(MalApiFailureKind.STORAGE),
            )
        }
    }

    suspend fun deleteLocalAccountData(localAccountId: String) {
        require(localAccountId.isNotBlank())
        database.withTransaction {
            dao.deleteLibraryRefreshEntriesForAccount(localAccountId)
            dao.deleteSnapshotsForAccount(MediaIdentityProvider.MYANIMELIST.name, localAccountId)
            dao.deleteLibraryRefreshStates(localAccountId)
        }
    }

    private suspend fun failRefresh(
        localAccountId: String,
        mediaType: TrackingMediaType,
        generation: Long,
        generationStartedAtEpochMillis: Long,
        itemCount: Int,
        lastSuccessAtEpochMillis: Long?,
        error: MalApiFailure,
    ): MalLibraryRefreshResult.Failure {
        database.withTransaction {
            dao.deleteLibraryRefreshEntries(localAccountId, mediaType.name)
            dao.upsertLibraryRefreshState(
                MalLibraryRefreshStateEntity(
                    localAccountId = localAccountId,
                    mediaType = mediaType.name,
                    state = REFRESH_FAILED,
                    generation = generation,
                    nextPageUrl = null,
                    itemCount = itemCount,
                    lastAttemptAtEpochMillis = generationStartedAtEpochMillis,
                    lastSuccessAtEpochMillis = lastSuccessAtEpochMillis,
                    lastErrorKind = error.kind.name,
                )
            )
        }
        return MalLibraryRefreshResult.Failure(
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
            is MediaIdentityResult.StorageFailure -> IdentityResolution.Failure(
                MalApiFailure(MalApiFailureKind.STORAGE)
            )
            is MediaIdentityResult.Invalid,
            is MediaIdentityResult.NotFound,
            is MediaIdentityResult.Conflict,
            is MediaIdentityResult.Rejected -> createIdentity(entry, localType)
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
            mappingSource = MediaIdentityMappingSource.MAL_NATIVE,
            verificationStatus = MediaIdentityVerificationStatus.PROVIDER_CONFIRMED,
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

    private fun MalListEntry.toRefreshEntry(
        localAccountId: String,
        generation: Long,
        localMediaId: String,
    ) = MalLibraryRefreshEntryEntity(
        localAccountId = localAccountId,
        mediaType = mediaType.name,
        generation = generation,
        malId = malId,
        localMediaId = localMediaId,
        title = title,
        alternativeTitlesJson = json.encodeToString(alternativeTitles),
        synopsis = synopsis,
        pictureMedium = pictureMedium,
        pictureLarge = pictureLarge,
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
        status = desiredState.status?.name ?: TrackingStatus.PLANNING.name,
        progress = desiredState.progress,
        progressSecondary = desiredState.progressSecondary,
        score100 = desiredState.score100,
        repeatCount = desiredState.repeatCount,
        notes = desiredState.notes,
        startedAt = desiredState.startedAt,
        completedAt = desiredState.completedAt,
        providerUpdatedAtEpochMillis = providerUpdatedAtEpochMillis,
    )

    private fun MalLibraryRefreshEntryEntity.toSnapshot(fetchedAtEpochMillis: Long) =
        ProviderTrackingSnapshotEntity(
            provider = MediaIdentityProvider.MYANIMELIST.name,
            providerAccountId = localAccountId,
            localMediaId = localMediaId,
            providerMediaId = malId,
            providerListEntryId = null,
            mediaType = mediaType,
            title = title,
            coverUrl = pictureLarge ?: pictureMedium,
            status = status,
            progress = progress,
            progressSecondary = progressSecondary,
            score = score100,
            repeatCount = repeatCount,
            notes = notes,
            startedAt = startedAt,
            completedAt = completedAt,
            providerUpdatedAtEpochMillis = providerUpdatedAtEpochMillis,
            fetchedAtEpochMillis = fetchedAtEpochMillis,
            isDeleted = false,
        )

    private fun MalLibraryRefreshEntryEntity.toCache(fetchedAtEpochMillis: Long) =
        MalMediaCacheEntity(
            malId = malId,
            mediaType = mediaType,
            title = title,
            alternativeTitlesJson = alternativeTitlesJson,
            synopsis = synopsis,
            mainPictureMedium = pictureMedium,
            mainPictureLarge = pictureLarge,
            pictureGalleryJson = "[]",
            meanScore = meanScore,
            rank = rank,
            popularity = popularity,
            mediaStatus = mediaStatus,
            mediaFormat = null,
            startDate = startDate,
            endDate = endDate,
            episodeCount = episodeCount,
            chapterCount = chapterCount,
            volumeCount = volumeCount,
            genresJson = genresJson,
            background = null,
            relatedJson = "[]",
            recommendationsJson = "[]",
            rankingPosition = null,
            isDetailed = false,
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
        private const val REFRESH_RUNNING = "RUNNING"
        private const val REFRESH_SUCCEEDED = "SUCCEEDED"
        private const val REFRESH_FAILED = "FAILED"
        private const val REFRESH_CANCELLED = "CANCELLED"
        private const val MAX_PAGE_COUNT = 10_000
        private const val MAX_ENTRY_COUNT = 50_000
        private const val MEDIA_CACHE_TTL_MILLIS = 7 * 24 * 60 * 60_000L
    }
}
