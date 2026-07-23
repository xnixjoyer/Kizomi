package com.anisync.android.data.identity

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.anisync.android.data.local.AppDatabase
import com.anisync.android.data.local.dao.MediaIdentityDao
import com.anisync.android.data.local.entity.LocalMediaIdentityEntity
import com.anisync.android.data.local.entity.ProviderMediaIdentityEntity
import com.anisync.android.data.local.entity.ProviderMediaIdentityIssueEntity
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

interface MediaIdentityStore {
    suspend fun createLocalIdentity(mediaType: LocalMediaType): MediaIdentityResult<LocalMediaIdentity>
    suspend fun resolveByAniListId(mediaType: LocalMediaType, aniListId: Long): MediaIdentityResult<LocalMediaIdentity?>
    suspend fun resolveByMalId(mediaType: LocalMediaType, malId: Long): MediaIdentityResult<LocalMediaIdentity?>
    suspend fun getProviderIdentities(localMediaId: String): MediaIdentityResult<List<ProviderMediaIdentity>>
    suspend fun attachProviderIdentity(
        localMediaId: String,
        provider: MediaIdentityProvider,
        providerMediaId: Long,
        mediaType: LocalMediaType,
        mappingSource: MediaIdentityMappingSource,
        verificationStatus: MediaIdentityVerificationStatus,
    ): MediaIdentityResult<ProviderMediaIdentity>
    suspend fun confirmProviderIdentity(
        localMediaId: String,
        provider: MediaIdentityProvider,
        providerMediaId: Long,
        mediaType: LocalMediaType,
    ): MediaIdentityResult<ProviderMediaIdentity>
    suspend fun rejectProviderIdentity(
        localMediaId: String,
        provider: MediaIdentityProvider,
        providerMediaId: Long,
        mediaType: LocalMediaType,
        reason: String,
    ): MediaIdentityResult<Unit>
    suspend fun markConflict(
        localMediaId: String?,
        provider: MediaIdentityProvider,
        providerMediaId: Long?,
        mediaType: LocalMediaType?,
        mappingSource: MediaIdentityMappingSource,
        reason: String,
    ): MediaIdentityResult<ProviderMediaIdentityIssue>
    suspend fun listUnresolved(mediaType: LocalMediaType? = null): MediaIdentityResult<List<ProviderMediaIdentityIssue>>
    suspend fun listConflicting(mediaType: LocalMediaType? = null): MediaIdentityResult<List<ProviderMediaIdentityIssue>>
}

@Singleton
class MediaIdentityRepository @Inject constructor(
    private val database: AppDatabase,
    private val dao: MediaIdentityDao,
    private val clock: MediaIdentityClock,
    private val idGenerator: MediaIdentityIdGenerator,
) : MediaIdentityStore {
    override suspend fun createLocalIdentity(
        mediaType: LocalMediaType,
    ): MediaIdentityResult<LocalMediaIdentity> = try {
        val now = clock.nowEpochMillis()
        val entity = LocalMediaIdentityEntity(
            id = idGenerator.newLocalMediaId(),
            mediaType = mediaType.name,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        database.withTransaction { dao.insertLocalIdentity(entity) }
        MediaIdentityResult.Success(entity.toModel())
    } catch (_: SQLiteConstraintException) {
        MediaIdentityResult.Conflict("local identity collision")
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        MediaIdentityResult.StorageFailure("create local identity")
    }

    override suspend fun resolveByAniListId(
        mediaType: LocalMediaType,
        aniListId: Long,
    ): MediaIdentityResult<LocalMediaIdentity?> =
        resolveByProviderId(MediaIdentityProvider.ANILIST, mediaType, aniListId)

    override suspend fun resolveByMalId(
        mediaType: LocalMediaType,
        malId: Long,
    ): MediaIdentityResult<LocalMediaIdentity?> =
        resolveByProviderId(MediaIdentityProvider.MYANIMELIST, mediaType, malId)

    override suspend fun getProviderIdentities(
        localMediaId: String,
    ): MediaIdentityResult<List<ProviderMediaIdentity>> = try {
        if (dao.getLocalIdentity(localMediaId) == null) {
            MediaIdentityResult.NotFound("local media identity")
        } else {
            MediaIdentityResult.Success(
                dao.getProviderIdentities(localMediaId).map(ProviderMediaIdentityEntity::toModel)
            )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        MediaIdentityResult.StorageFailure("read provider identities")
    }

    override suspend fun attachProviderIdentity(
        localMediaId: String,
        provider: MediaIdentityProvider,
        providerMediaId: Long,
        mediaType: LocalMediaType,
        mappingSource: MediaIdentityMappingSource,
        verificationStatus: MediaIdentityVerificationStatus,
    ): MediaIdentityResult<ProviderMediaIdentity> = attachInternal(
        localMediaId,
        provider,
        providerMediaId,
        mediaType,
        mappingSource,
        verificationStatus,
        allowRejected = false,
    )

    override suspend fun confirmProviderIdentity(
        localMediaId: String,
        provider: MediaIdentityProvider,
        providerMediaId: Long,
        mediaType: LocalMediaType,
    ): MediaIdentityResult<ProviderMediaIdentity> = attachInternal(
        localMediaId,
        provider,
        providerMediaId,
        mediaType,
        MediaIdentityMappingSource.MANUAL_CONFIRMATION,
        MediaIdentityVerificationStatus.CONFIRMED,
        allowRejected = true,
    )

    override suspend fun rejectProviderIdentity(
        localMediaId: String,
        provider: MediaIdentityProvider,
        providerMediaId: Long,
        mediaType: LocalMediaType,
        reason: String,
    ): MediaIdentityResult<Unit> {
        if (providerMediaId <= 0L) return MediaIdentityResult.Invalid("provider media id must be positive")
        return try {
            database.withTransaction {
                val local = dao.getLocalIdentity(localMediaId)
                    ?: return@withTransaction MediaIdentityResult.NotFound("local media identity")
                if (local.mediaType != mediaType.name) {
                    return@withTransaction MediaIdentityResult.Invalid("media type does not match local identity")
                }
                val active = dao.findProviderForLocal(localMediaId, provider.name, mediaType.name)
                if (active != null && active.providerMediaId == providerMediaId) {
                    dao.removeProviderIdentity(active)
                }
                val now = clock.nowEpochMillis()
                dao.insertIssue(
                    ProviderMediaIdentityIssueEntity(
                        localMediaId = localMediaId,
                        provider = provider.name,
                        providerMediaId = providerMediaId,
                        mediaType = mediaType.name,
                        mappingSource = MediaIdentityMappingSource.MANUAL_CONFIRMATION.name,
                        verificationStatus = MediaIdentityVerificationStatus.REJECTED.name,
                        reason = reason.trim().ifEmpty { "manual rejection" },
                        sourceTable = null,
                        sourceRowKey = null,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                    )
                )
                MediaIdentityResult.Success(Unit)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            MediaIdentityResult.StorageFailure("reject provider identity")
        }
    }

    override suspend fun markConflict(
        localMediaId: String?,
        provider: MediaIdentityProvider,
        providerMediaId: Long?,
        mediaType: LocalMediaType?,
        mappingSource: MediaIdentityMappingSource,
        reason: String,
    ): MediaIdentityResult<ProviderMediaIdentityIssue> = try {
        database.withTransaction {
            if (localMediaId != null) {
                val local = dao.getLocalIdentity(localMediaId)
                    ?: return@withTransaction MediaIdentityResult.NotFound("local media identity")
                if (mediaType != null && local.mediaType != mediaType.name) {
                    return@withTransaction MediaIdentityResult.Invalid("media type does not match local identity")
                }
            }
            val now = clock.nowEpochMillis()
            val entity = ProviderMediaIdentityIssueEntity(
                localMediaId = localMediaId,
                provider = provider.name,
                providerMediaId = providerMediaId,
                mediaType = mediaType?.name,
                mappingSource = mappingSource.name,
                verificationStatus = MediaIdentityVerificationStatus.CONFLICTING.name,
                reason = reason.trim().ifEmpty { "provider identity conflict" },
                sourceTable = null,
                sourceRowKey = null,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )
            val id = dao.insertIssue(entity)
            MediaIdentityResult.Success(entity.copy(id = id).toModel())
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        MediaIdentityResult.StorageFailure("mark provider identity conflict")
    }

    override suspend fun listUnresolved(
        mediaType: LocalMediaType?,
    ): MediaIdentityResult<List<ProviderMediaIdentityIssue>> =
        listIssues(MediaIdentityVerificationStatus.UNRESOLVED, mediaType)

    override suspend fun listConflicting(
        mediaType: LocalMediaType?,
    ): MediaIdentityResult<List<ProviderMediaIdentityIssue>> =
        listIssues(MediaIdentityVerificationStatus.CONFLICTING, mediaType)

    private suspend fun resolveByProviderId(
        provider: MediaIdentityProvider,
        mediaType: LocalMediaType,
        providerMediaId: Long,
    ): MediaIdentityResult<LocalMediaIdentity?> {
        if (providerMediaId <= 0L) return MediaIdentityResult.Invalid("provider media id must be positive")
        return try {
            val mapping = dao.findByProviderId(provider.name, providerMediaId, mediaType.name)
                ?: return MediaIdentityResult.Success(null)
            MediaIdentityResult.Success(dao.getLocalIdentity(mapping.localMediaId)?.toModel())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            MediaIdentityResult.StorageFailure("resolve provider identity")
        }
    }

    private suspend fun attachInternal(
        localMediaId: String,
        provider: MediaIdentityProvider,
        providerMediaId: Long,
        mediaType: LocalMediaType,
        mappingSource: MediaIdentityMappingSource,
        verificationStatus: MediaIdentityVerificationStatus,
        allowRejected: Boolean,
    ): MediaIdentityResult<ProviderMediaIdentity> {
        if (providerMediaId <= 0L) return MediaIdentityResult.Invalid("provider media id must be positive")
        if (verificationStatus !in ACTIVE_STATUSES) {
            return MediaIdentityResult.Invalid("active identity requires exact, confirmed, or imported status")
        }
        return try {
            database.withTransaction {
                val local = dao.getLocalIdentity(localMediaId)
                    ?: return@withTransaction MediaIdentityResult.NotFound("local media identity")
                if (local.mediaType != mediaType.name) {
                    return@withTransaction MediaIdentityResult.Invalid("media type does not match local identity")
                }
                if (!allowRejected && dao.findIssue(
                        localMediaId,
                        provider.name,
                        providerMediaId,
                        mediaType.name,
                        MediaIdentityVerificationStatus.REJECTED.name,
                    ) != null
                ) {
                    return@withTransaction MediaIdentityResult.Rejected("provider identity was rejected")
                }
                val localSlot = dao.findProviderForLocal(localMediaId, provider.name, mediaType.name)
                if (localSlot != null) {
                    return@withTransaction if (localSlot.providerMediaId == providerMediaId) {
                        MediaIdentityResult.Success(localSlot.toModel())
                    } else {
                        recordConflict(localMediaId, provider, providerMediaId, mediaType, mappingSource, "local provider slot already contains another provider id")
                        MediaIdentityResult.Conflict("local provider slot already occupied", localMediaId)
                    }
                }
                val global = dao.findByProviderId(provider.name, providerMediaId, mediaType.name)
                if (global != null) {
                    recordConflict(localMediaId, provider, providerMediaId, mediaType, mappingSource, "provider id already belongs to another local identity")
                    return@withTransaction MediaIdentityResult.Conflict("provider identity already attached", global.localMediaId)
                }
                val now = clock.nowEpochMillis()
                val entity = ProviderMediaIdentityEntity(
                    localMediaId = localMediaId,
                    provider = provider.name,
                    providerMediaId = providerMediaId,
                    mediaType = mediaType.name,
                    mappingSource = mappingSource.name,
                    verificationStatus = verificationStatus.name,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                )
                val id = dao.insertProviderIdentity(entity)
                MediaIdentityResult.Success(entity.copy(id = id).toModel())
            }
        } catch (_: SQLiteConstraintException) {
            runCatching {
                database.withTransaction {
                    recordConflict(localMediaId, provider, providerMediaId, mediaType, mappingSource, "concurrent provider identity constraint conflict")
                }
            }
            MediaIdentityResult.Conflict("provider identity constraint conflict")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            MediaIdentityResult.StorageFailure("attach provider identity")
        }
    }

    private suspend fun recordConflict(
        localMediaId: String?,
        provider: MediaIdentityProvider,
        providerMediaId: Long?,
        mediaType: LocalMediaType?,
        mappingSource: MediaIdentityMappingSource,
        reason: String,
    ) {
        val now = clock.nowEpochMillis()
        dao.insertIssue(
            ProviderMediaIdentityIssueEntity(
                localMediaId = localMediaId,
                provider = provider.name,
                providerMediaId = providerMediaId,
                mediaType = mediaType?.name,
                mappingSource = mappingSource.name,
                verificationStatus = MediaIdentityVerificationStatus.CONFLICTING.name,
                reason = reason,
                sourceTable = null,
                sourceRowKey = null,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )
        )
    }

    private suspend fun listIssues(
        status: MediaIdentityVerificationStatus,
        mediaType: LocalMediaType?,
    ): MediaIdentityResult<List<ProviderMediaIdentityIssue>> = try {
        MediaIdentityResult.Success(
            dao.listIssues(status.name, mediaType?.name).map(ProviderMediaIdentityIssueEntity::toModel)
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        MediaIdentityResult.StorageFailure("list provider identity issues")
    }

    private companion object {
        val ACTIVE_STATUSES = setOf(
            MediaIdentityVerificationStatus.EXACT,
            MediaIdentityVerificationStatus.CONFIRMED,
            MediaIdentityVerificationStatus.PROVIDER_CONFIRMED,
        )
    }
}

private fun LocalMediaIdentityEntity.toModel(): LocalMediaIdentity = LocalMediaIdentity(
    id = id,
    mediaType = LocalMediaType.valueOf(mediaType),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun ProviderMediaIdentityEntity.toModel(): ProviderMediaIdentity = ProviderMediaIdentity(
    id = id,
    localMediaId = localMediaId,
    provider = MediaIdentityProvider.valueOf(provider),
    providerMediaId = providerMediaId,
    mediaType = LocalMediaType.valueOf(mediaType),
    mappingSource = MediaIdentityMappingSource.valueOf(mappingSource),
    verificationStatus = MediaIdentityVerificationStatus.valueOf(verificationStatus),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun ProviderMediaIdentityIssueEntity.toModel(): ProviderMediaIdentityIssue =
    ProviderMediaIdentityIssue(
        id = id,
        localMediaId = localMediaId,
        provider = MediaIdentityProvider.valueOf(provider),
        providerMediaId = providerMediaId,
        mediaType = mediaType?.let(LocalMediaType::valueOf),
        mappingSource = MediaIdentityMappingSource.valueOf(mappingSource),
        verificationStatus = MediaIdentityVerificationStatus.valueOf(verificationStatus),
        reason = reason,
        sourceTable = sourceTable,
        sourceRowKey = sourceRowKey,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
