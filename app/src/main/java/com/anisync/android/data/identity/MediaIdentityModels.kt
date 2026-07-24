package com.anisync.android.data.identity

enum class MediaIdentityProvider { ANILIST, MYANIMELIST }
enum class LocalMediaType { ANIME, MANGA }
enum class MediaIdentityMappingSource {
    EXISTING_ANILIST_MIGRATION,
    ANILIST_ID_MAL,
    MAL_NATIVE,
    ANILIST_LOOKUP_BY_MAL_ID,
    MANUAL_CONFIRMATION,
}
enum class MediaIdentityVerificationStatus {
    EXACT,
    CONFIRMED,
    PROVIDER_CONFIRMED,
    UNRESOLVED,
    CONFLICTING,
    REJECTED,
}

data class LocalMediaIdentity(
    val id: String,
    val mediaType: LocalMediaType,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

data class ProviderMediaIdentity(
    val id: Long,
    val localMediaId: String,
    val provider: MediaIdentityProvider,
    val providerMediaId: Long,
    val mediaType: LocalMediaType,
    val mappingSource: MediaIdentityMappingSource,
    val verificationStatus: MediaIdentityVerificationStatus,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

data class ProviderMediaIdentityIssue(
    val id: Long,
    val localMediaId: String?,
    val provider: MediaIdentityProvider,
    val providerMediaId: Long?,
    val mediaType: LocalMediaType?,
    val mappingSource: MediaIdentityMappingSource,
    val verificationStatus: MediaIdentityVerificationStatus,
    val reason: String,
    val sourceTable: String?,
    val sourceRowKey: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

sealed interface MediaIdentityResult<out T> {
    data class Success<T>(val value: T) : MediaIdentityResult<T>
    data class NotFound(val entity: String) : MediaIdentityResult<Nothing>
    data class Invalid(val reason: String) : MediaIdentityResult<Nothing>
    data class Conflict(val reason: String, val existingLocalMediaId: String? = null) : MediaIdentityResult<Nothing>
    data class Rejected(val reason: String) : MediaIdentityResult<Nothing>
    data class StorageFailure(val operation: String) : MediaIdentityResult<Nothing>
}

interface MediaIdentityClock { fun nowEpochMillis(): Long }
class SystemMediaIdentityClock @javax.inject.Inject constructor() : MediaIdentityClock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}

interface MediaIdentityIdGenerator { fun newLocalMediaId(): String }
class UuidMediaIdentityIdGenerator @javax.inject.Inject constructor() : MediaIdentityIdGenerator {
    override fun newLocalMediaId(): String = java.util.UUID.randomUUID().toString()
}
