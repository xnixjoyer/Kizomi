package com.anisync.android.data.identity

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AniListMediaIdentityAdapter @Inject constructor(
    private val store: MediaIdentityStore,
) {
    suspend fun resolveLocalIdentity(
        mediaType: LocalMediaType,
        aniListMediaId: Int,
    ): MediaIdentityResult<LocalMediaIdentity?> =
        store.resolveByAniListId(mediaType, aniListMediaId.toLong())

    suspend fun resolveAniListId(
        localMediaId: String,
    ): MediaIdentityResult<Long?> = when (val result = store.getProviderIdentities(localMediaId)) {
        is MediaIdentityResult.Success -> MediaIdentityResult.Success(
            result.value.firstOrNull { it.provider == MediaIdentityProvider.ANILIST }?.providerMediaId
        )
        is MediaIdentityResult.NotFound -> result
        is MediaIdentityResult.Invalid -> result
        is MediaIdentityResult.Conflict -> result
        is MediaIdentityResult.Rejected -> result
        is MediaIdentityResult.StorageFailure -> result
    }

    suspend fun ensureLocalIdentity(
        mediaType: LocalMediaType,
        aniListMediaId: Int,
    ): MediaIdentityResult<LocalMediaIdentity> {
        when (val existing = store.resolveByAniListId(mediaType, aniListMediaId.toLong())) {
            is MediaIdentityResult.Success -> existing.value?.let {
                return MediaIdentityResult.Success(it)
            }
            is MediaIdentityResult.NotFound -> return existing
            is MediaIdentityResult.Invalid -> return existing
            is MediaIdentityResult.Conflict -> return existing
            is MediaIdentityResult.Rejected -> return existing
            is MediaIdentityResult.StorageFailure -> return existing
        }
        val created = store.createLocalIdentity(mediaType)
        if (created !is MediaIdentityResult.Success) return created
        val attached = store.attachProviderIdentity(
            localMediaId = created.value.id,
            provider = MediaIdentityProvider.ANILIST,
            providerMediaId = aniListMediaId.toLong(),
            mediaType = mediaType,
            mappingSource = MediaIdentityMappingSource.EXISTING_ANILIST_MIGRATION,
            verificationStatus = MediaIdentityVerificationStatus.EXACT,
        )
        return when (attached) {
            is MediaIdentityResult.Success -> MediaIdentityResult.Success(created.value)
            is MediaIdentityResult.Conflict -> when (
                val winner = store.resolveByAniListId(mediaType, aniListMediaId.toLong())
            ) {
                is MediaIdentityResult.Success -> winner.value?.let {
                    MediaIdentityResult.Success(it)
                } ?: attached
                else -> attached
            }
            is MediaIdentityResult.NotFound -> attached
            is MediaIdentityResult.Invalid -> attached
            is MediaIdentityResult.Rejected -> attached
            is MediaIdentityResult.StorageFailure -> attached
        }
    }
}
