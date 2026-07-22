package com.anisync.android.data.communityscore

import com.anisync.android.data.local.dao.CommunityScoreDao
import com.anisync.android.data.local.entity.CommunityScoreEntity
import com.anisync.android.domain.CommunityScore
import com.anisync.android.domain.CommunityScoreCacheStats
import com.anisync.android.domain.CommunityScoreFailure
import com.anisync.android.domain.CommunityScoreFailureType
import com.anisync.android.domain.CommunityScorePrefetchReport
import com.anisync.android.domain.CommunityScoreRefreshResult
import com.anisync.android.domain.CommunityScoreRepository
import com.anisync.android.domain.CommunityScoreRequest
import com.anisync.android.domain.CommunityScoreRuntimeStats
import com.anisync.android.domain.CommunityScoreSearchResult
import com.anisync.android.domain.MalSearchCandidate
import com.anisync.android.domain.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

private const val SCORE_TTL_MS = 24L * 60L * 60L * 1_000L
private const val UNAVAILABLE_TTL_MS = 6L * 60L * 60L * 1_000L

@Singleton
class DefaultCommunityScoreRepository @Inject internal constructor(
    private val dao: CommunityScoreDao,
    private val client: JikanCommunityScoreClient
) : CommunityScoreRepository {
    private val refreshGate = Mutex()
    private val _runtimeStats = MutableStateFlow(CommunityScoreRuntimeStats())
    override val runtimeStats: StateFlow<CommunityScoreRuntimeStats> = _runtimeStats.asStateFlow()

    override fun observeScores(aniListMediaIds: List<Int>): Flow<Map<Int, CommunityScore>> {
        val ids = aniListMediaIds.distinct()
        return dao.observeByAniListIds(ids.ifEmpty { listOf(Int.MIN_VALUE) }).map { rows ->
            val now = System.currentTimeMillis()
            rows.associate { it.aniListMediaId to it.toDomain(now) }
        }
    }

    override fun observeScore(aniListMediaId: Int): Flow<CommunityScore?> =
        dao.observeByAniListId(aniListMediaId).map { it?.toDomain(System.currentTimeMillis()) }

    override suspend fun refresh(
        request: CommunityScoreRequest,
        force: Boolean
    ): CommunityScoreRefreshResult = refreshGate.withLock {
        val now = System.currentTimeMillis()
        val cached = dao.getByAniListId(request.aniListMediaId)
        // An explicit viewer-confirmed correction overrides a stale/wrong AniList cross-id until
        // the local score cache is cleared. Automatic paths can never create this override.
        val effectiveMalId = cached?.takeIf { it.isManualLink }?.malId ?: request.malId
            ?: return@withLock CommunityScoreRefreshResult.MissingCrossId

        if (!force && cached != null && cached.malId == effectiveMalId && cached.expiresAtEpochMillis > now) {
            updateStats(cacheHit = true)
            return@withLock CommunityScoreRefreshResult.CacheFresh
        }

        updateStats(request = true)
        when (val result = client.fetchAnime(
            malId = effectiveMalId,
            etag = cached?.takeIf { it.malId == effectiveMalId }?.etag,
            lastModified = cached?.takeIf { it.malId == effectiveMalId }?.lastModified
        )) {
            is JikanFetchResult.Success -> {
                val snapshot = result.snapshot
                dao.upsert(
                    CommunityScoreEntity(
                        aniListMediaId = request.aniListMediaId,
                        malId = snapshot.malId,
                        score = snapshot.score,
                        scoredBy = snapshot.scoredBy,
                        rank = snapshot.rank,
                        title = snapshot.title,
                        fetchedAtEpochMillis = now,
                        expiresAtEpochMillis = now + SCORE_TTL_MS,
                        unavailable = false,
                        isManualLink = cached?.isManualLink == true && cached.malId == effectiveMalId,
                        etag = result.etag,
                        lastModified = result.lastModified
                    )
                )
                CommunityScoreRefreshResult.Updated
            }

            is JikanFetchResult.NotModified -> {
                if (cached == null) {
                    val error = CommunityScoreFailure(CommunityScoreFailureType.INVALID_RESPONSE, 304)
                    updateStats(failure = error)
                    CommunityScoreRefreshResult.Failure(error)
                } else {
                    dao.upsert(
                        cached.copy(
                            fetchedAtEpochMillis = now,
                            expiresAtEpochMillis = now + SCORE_TTL_MS,
                            etag = result.etag ?: cached.etag,
                            lastModified = result.lastModified ?: cached.lastModified
                        )
                    )
                    updateStats(notModified = true)
                    CommunityScoreRefreshResult.NotModified
                }
            }

            JikanFetchResult.NotFound -> {
                dao.upsert(
                    CommunityScoreEntity(
                        aniListMediaId = request.aniListMediaId,
                        malId = effectiveMalId,
                        score = null,
                        scoredBy = null,
                        rank = null,
                        title = cached?.title,
                        fetchedAtEpochMillis = now,
                        expiresAtEpochMillis = now + UNAVAILABLE_TTL_MS,
                        unavailable = true,
                        isManualLink = cached?.isManualLink == true
                    )
                )
                CommunityScoreRefreshResult.NotFound
            }

            is JikanFetchResult.Failure -> {
                val error = result.error.toDomainFailure()
                updateStats(failure = error)
                if (error.type == CommunityScoreFailureType.RATE_LIMITED) {
                    CommunityScoreRefreshResult.RateLimited(error.retryAfterMillis)
                } else {
                    CommunityScoreRefreshResult.Failure(error)
                }
            }
        }
    }

    override suspend fun prefetch(
        requests: List<CommunityScoreRequest>,
        maxNetworkRequests: Int
    ): CommunityScorePrefetchReport {
        val unique = requests.distinctBy(CommunityScoreRequest::aniListMediaId)
        var network = 0
        var hits = 0
        var missing = 0
        var failures = 0
        val cap = maxNetworkRequests.coerceIn(0, 24)

        for (request in unique) {
            if (network >= cap) break
            when (refresh(request, force = false)) {
                CommunityScoreRefreshResult.CacheFresh -> hits++
                CommunityScoreRefreshResult.MissingCrossId -> missing++
                CommunityScoreRefreshResult.Updated,
                CommunityScoreRefreshResult.NotModified,
                CommunityScoreRefreshResult.NotFound -> network++
                is CommunityScoreRefreshResult.RateLimited -> {
                    network++
                    failures++
                    break
                }
                is CommunityScoreRefreshResult.Failure -> {
                    network++
                    failures++
                }
            }
        }
        return CommunityScorePrefetchReport(unique.size, network, hits, missing, failures)
    }

    override suspend fun searchCandidates(request: CommunityScoreRequest): CommunityScoreSearchResult {
        val query = request.titleEnglish?.takeIf(String::isNotBlank)
            ?: request.titleRomaji?.takeIf(String::isNotBlank)
            ?: request.titleUserPreferred
        updateStats(request = true)
        return when (val result = client.searchAnime(query)) {
            is JikanSearchResult.Success -> CommunityScoreSearchResult.Success(
                result.candidates.map { candidate ->
                    candidate.toSearchCandidate(request)
                }.filter { it.confidence >= 0.55 }.sortedByDescending(MalSearchCandidate::confidence)
            )
            is JikanSearchResult.Failure -> {
                val error = result.error.toDomainFailure()
                updateStats(failure = error)
                CommunityScoreSearchResult.Failure(error)
            }
        }
    }

    override suspend fun bindManualCandidate(
        request: CommunityScoreRequest,
        candidate: MalSearchCandidate
    ): Result<Unit> {
        if (candidate.confidence < 0.55) return Result.Error("Candidate is not plausible enough")
        val now = System.currentTimeMillis()
        dao.upsert(
            CommunityScoreEntity(
                aniListMediaId = request.aniListMediaId,
                malId = candidate.malId,
                score = candidate.score,
                scoredBy = candidate.scoredBy,
                rank = null,
                title = candidate.title,
                fetchedAtEpochMillis = now,
                expiresAtEpochMillis = now + SCORE_TTL_MS,
                unavailable = false,
                isManualLink = true
            )
        )
        return Result.Success(Unit)
    }

    override suspend fun cacheStats(): CommunityScoreCacheStats {
        val now = System.currentTimeMillis()
        return CommunityScoreCacheStats(
            entries = dao.count(),
            freshEntries = dao.countFresh(now),
            staleEntries = dao.countStale(now),
            unavailableEntries = dao.countUnavailable()
        )
    }

    override suspend fun clearCache() = dao.clear()

    private fun updateStats(
        request: Boolean = false,
        cacheHit: Boolean = false,
        notModified: Boolean = false,
        rateLimited: Boolean = false,
        failure: CommunityScoreFailure? = null
    ) {
        _runtimeStats.value = _runtimeStats.value.let { current ->
            current.copy(
                requests = current.requests + if (request) 1 else 0,
                cacheHits = current.cacheHits + if (cacheHit) 1 else 0,
                notModified = current.notModified + if (notModified) 1 else 0,
                rateLimited = current.rateLimited + if (
                    rateLimited || failure?.type == CommunityScoreFailureType.RATE_LIMITED
                ) 1 else 0,
                failures = current.failures + if (failure != null) 1 else 0,
                lastRequestAtEpochMillis = if (request) System.currentTimeMillis() else current.lastRequestAtEpochMillis,
                lastErrorType = failure?.type ?: current.lastErrorType,
                lastHttpStatus = failure?.httpStatus ?: current.lastHttpStatus
            )
        }
    }
}

private fun JikanFailure.toDomainFailure(): CommunityScoreFailure = CommunityScoreFailure(
    type = when (type) {
        JikanFailureType.OFFLINE -> CommunityScoreFailureType.OFFLINE
        JikanFailureType.TRANSPORT -> CommunityScoreFailureType.TRANSPORT
        JikanFailureType.TIMEOUT -> CommunityScoreFailureType.TIMEOUT
        JikanFailureType.RATE_LIMITED -> CommunityScoreFailureType.RATE_LIMITED
        JikanFailureType.TEMPORARY_SERVER -> CommunityScoreFailureType.TEMPORARY_SERVER
        JikanFailureType.INVALID_RESPONSE -> CommunityScoreFailureType.INVALID_RESPONSE
        JikanFailureType.PERMANENT -> CommunityScoreFailureType.PERMANENT
    },
    httpStatus = httpStatus,
    retryAfterMillis = retryAfterMillis
)

private fun CommunityScoreEntity.toDomain(now: Long): CommunityScore = CommunityScore(
    aniListMediaId = aniListMediaId,
    malId = malId,
    score = score,
    scoredBy = scoredBy,
    rank = rank,
    title = title,
    fetchedAtEpochMillis = fetchedAtEpochMillis,
    expiresAtEpochMillis = expiresAtEpochMillis,
    unavailable = unavailable,
    isStale = expiresAtEpochMillis <= now
)

private fun JikanAnimeSnapshot.toSearchCandidate(request: CommunityScoreRequest): MalSearchCandidate {
    val sourceTitles = listOfNotNull(
        request.titleUserPreferred,
        request.titleEnglish,
        request.titleRomaji,
        request.titleNative
    ).filter(String::isNotBlank)
    val targetTitles = listOfNotNull(title, titleEnglish, titleJapanese).filter(String::isNotBlank)
    val titleConfidence = sourceTitles.maxOfOrNull { source ->
        targetTitles.maxOfOrNull { target -> titleSimilarity(source, target) } ?: 0.0
    } ?: 0.0
    val yearConfidence = when {
        request.year == null || year == null -> 0.5
        request.year == year -> 1.0
        abs(request.year - year) == 1 -> 0.55
        else -> 0.0
    }
    val formatConfidence = formatCompatibility(request.format, format)
    val combined = titleConfidence * 0.82 + yearConfidence * 0.10 + formatConfidence * 0.08
    return MalSearchCandidate(
        malId = malId,
        title = title ?: titleEnglish ?: "MyAnimeList #$malId",
        titleEnglish = titleEnglish,
        titleJapanese = titleJapanese,
        score = score,
        scoredBy = scoredBy,
        year = year,
        format = format,
        episodes = episodes,
        confidence = combined.coerceIn(0.0, 1.0)
    )
}

internal fun titleSimilarity(left: String, right: String): Double {
    val a = normalizeTitle(left)
    val b = normalizeTitle(right)
    if (a.isBlank() || b.isBlank()) return 0.0
    if (a == b) return 1.0
    val aTokens = a.split(' ').filter(String::isNotBlank).toSet()
    val bTokens = b.split(' ').filter(String::isNotBlank).toSet()
    val tokenUnion = aTokens union bTokens
    val jaccard = if (tokenUnion.isEmpty()) 0.0 else (aTokens intersect bTokens).size.toDouble() / tokenUnion.size
    val edit = 1.0 - levenshtein(a, b).toDouble() / max(a.length, b.length).coerceAtLeast(1)
    val prefix = if ((a.startsWith(b) || b.startsWith(a)) && minOf(a.length, b.length) >= 8) 0.9 else 0.0
    return max(prefix, jaccard * 0.55 + edit.coerceAtLeast(0.0) * 0.45)
}

private fun normalizeTitle(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase(Locale.ROOT)
    .replace('&', ' ')
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

private fun levenshtein(left: String, right: String): Int {
    if (left == right) return 0
    if (left.isEmpty()) return right.length
    if (right.isEmpty()) return left.length
    var previous = IntArray(right.length + 1) { it }
    for (i in left.indices) {
        val current = IntArray(right.length + 1)
        current[0] = i + 1
        for (j in right.indices) {
            current[j + 1] = minOf(
                current[j] + 1,
                previous[j + 1] + 1,
                previous[j] + if (left[i] == right[j]) 0 else 1
            )
        }
        previous = current
    }
    return previous[right.length]
}

private fun formatCompatibility(source: String?, target: String?): Double {
    if (source.isNullOrBlank() || target.isNullOrBlank()) return 0.5
    val normalizedSource = source.uppercase(Locale.ROOT)
    val normalizedTarget = target.uppercase(Locale.ROOT).replace(' ', '_')
    if (normalizedSource == normalizedTarget) return 1.0
    val episodic = setOf("TV", "TV_SHORT", "ONA", "OVA", "SPECIAL")
    return if (normalizedSource in episodic && normalizedTarget in episodic) 0.55 else 0.0
}
