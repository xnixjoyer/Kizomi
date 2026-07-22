package com.anisync.android.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Authoritative AniList -> MyAnimeList cross-id plus the titles used only for manual recovery. */
data class CommunityScoreRequest(
    val aniListMediaId: Int,
    val malId: Int?,
    val titleUserPreferred: String,
    val titleEnglish: String? = null,
    val titleRomaji: String? = null,
    val titleNative: String? = null,
    val year: Int? = null,
    val format: String? = null
)

data class CommunityScore(
    val aniListMediaId: Int,
    val malId: Int,
    val score: Double?,
    val scoredBy: Int?,
    val rank: Int?,
    val title: String?,
    val fetchedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val unavailable: Boolean = false,
    val isStale: Boolean = false
)

data class CommunityScoreRuntimeStats(
    val requests: Int = 0,
    val cacheHits: Int = 0,
    val notModified: Int = 0,
    val rateLimited: Int = 0,
    val failures: Int = 0,
    val lastRequestAtEpochMillis: Long? = null,
    val lastErrorType: CommunityScoreFailureType? = null,
    val lastHttpStatus: Int? = null
)

enum class CommunityScoreFailureType {
    OFFLINE,
    TRANSPORT,
    TIMEOUT,
    RATE_LIMITED,
    TEMPORARY_SERVER,
    INVALID_RESPONSE,
    PERMANENT,
    NO_RESULTS
}

data class CommunityScoreFailure(
    val type: CommunityScoreFailureType,
    val httpStatus: Int? = null,
    val retryAfterMillis: Long? = null
)

sealed interface CommunityScoreSearchResult {
    data class Success(val candidates: List<MalSearchCandidate>) : CommunityScoreSearchResult
    data class Failure(val error: CommunityScoreFailure) : CommunityScoreSearchResult
}

data class CommunityScoreCacheStats(
    val entries: Int = 0,
    val freshEntries: Int = 0,
    val staleEntries: Int = 0,
    val unavailableEntries: Int = 0
)

data class CommunityScorePrefetchReport(
    val requested: Int,
    val networkRequests: Int,
    val cacheHits: Int,
    val unavailableCrossIds: Int,
    val failures: Int
)

data class MalSearchCandidate(
    val malId: Int,
    val title: String,
    val titleEnglish: String?,
    val titleJapanese: String?,
    val score: Double?,
    val scoredBy: Int?,
    val year: Int?,
    val format: String?,
    val episodes: Int?,
    /** Conservative local title/context score. This never auto-links an AniList identity. */
    val confidence: Double
)

sealed interface CommunityScoreRefreshResult {
    data object CacheFresh : CommunityScoreRefreshResult
    data object Updated : CommunityScoreRefreshResult
    data object NotModified : CommunityScoreRefreshResult
    data object MissingCrossId : CommunityScoreRefreshResult
    data object NotFound : CommunityScoreRefreshResult
    data class RateLimited(val retryAfterMillis: Long?) : CommunityScoreRefreshResult
    data class Failure(val error: CommunityScoreFailure) : CommunityScoreRefreshResult
}

interface CommunityScoreRepository {
    val runtimeStats: StateFlow<CommunityScoreRuntimeStats>

    fun observeScores(aniListMediaIds: List<Int>): Flow<Map<Int, CommunityScore>>
    fun observeScore(aniListMediaId: Int): Flow<CommunityScore?>

    suspend fun refresh(
        request: CommunityScoreRequest,
        force: Boolean = false
    ): CommunityScoreRefreshResult

    suspend fun prefetch(
        requests: List<CommunityScoreRequest>,
        maxNetworkRequests: Int = 12
    ): CommunityScorePrefetchReport

    /** Explicit/manual fallback only; callers must present candidates for confirmation. */
    suspend fun searchCandidates(request: CommunityScoreRequest): CommunityScoreSearchResult

    suspend fun bindManualCandidate(request: CommunityScoreRequest, candidate: MalSearchCandidate): Result<Unit>
    suspend fun cacheStats(): CommunityScoreCacheStats
    suspend fun clearCache()
}
