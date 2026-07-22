package com.anisync.android.data.communityscore

import com.anisync.android.data.local.dao.CommunityScoreDao
import com.anisync.android.data.local.entity.CommunityScoreEntity
import com.anisync.android.domain.CommunityScoreRefreshResult
import com.anisync.android.domain.CommunityScoreFailureType
import com.anisync.android.domain.CommunityScoreRequest
import com.anisync.android.domain.MalSearchCandidate
import com.anisync.android.domain.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DefaultCommunityScoreRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var dao: FakeCommunityScoreDao
    private lateinit var repository: DefaultCommunityScoreRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        dao = FakeCommunityScoreDao()
        repository = DefaultCommunityScoreRepository(
            dao = dao,
            client = JikanCommunityScoreClient(
                client = OkHttpClient(),
                userAgent = "AniSyncPlus-Test/1",
                baseUrl = server.url("/v4/"),
                minimumRequestIntervalMillis = 0
            )
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun missingAniListCrossIdNeverStartsAutomaticFuzzySearch() = runTest {
        val result = repository.refresh(request(malId = null), force = false)

        assertEquals(CommunityScoreRefreshResult.MissingCrossId, result)
        assertEquals(0, server.requestCount)
        assertEquals(0, repository.runtimeStats.value.requests)
    }

    @Test
    fun implausibleManualCandidateIsRejectedWithoutChangingCache() = runTest {
        val result = repository.bindManualCandidate(
            request(malId = null),
            candidate(malId = 99, confidence = 0.54)
        )

        assertTrue(result is Result.Error)
        assertEquals(0, dao.count())
    }

    @Test
    fun viewerConfirmedLinkOverridesWrongCrossIdAndRemainsMarkedManual() = runTest {
        val bind = repository.bindManualCandidate(
            request(malId = 1),
            candidate(malId = 52991, confidence = 0.93)
        )
        assertTrue(bind is Result.Success)
        server.enqueue(
            MockResponse().setBody(
                """
                {"data":{"mal_id":52991,"score":8.73,"scored_by":424242,"rank":42,"title":"Sousou no Frieren"}}
                """.trimIndent()
            )
        )

        val refreshed = repository.refresh(request(malId = 1), force = true)

        assertEquals(CommunityScoreRefreshResult.Updated, refreshed)
        assertEquals("/v4/anime/52991", server.takeRequest().path)
        val cached = dao.getByAniListId(100)!!
        assertEquals(52991, cached.malId)
        assertEquals(8.73, cached.score ?: 0.0, 0.0001)
        assertTrue(cached.isManualLink)
        assertFalse(cached.unavailable)
    }

    @Test
    fun temporaryFailurePreservesExistingManualMatchAndManualRetryCanRecover() = runTest {
        repository.bindManualCandidate(
            request(malId = 1),
            candidate(malId = 52991, confidence = 0.93)
        )
        val before = dao.getByAniListId(100)!!
        server.enqueue(MockResponse().setResponseCode(504).setBody("gateway secret"))

        val failed = repository.refresh(request(malId = 1), force = true)

        assertTrue(failed is CommunityScoreRefreshResult.Failure)
        assertEquals(
            CommunityScoreFailureType.TEMPORARY_SERVER,
            (failed as CommunityScoreRefreshResult.Failure).error.type
        )
        assertEquals(before, dao.getByAniListId(100))

        server.enqueue(
            MockResponse().setBody(
                """{"data":{"mal_id":52991,"score":8.9,"title":"Sousou no Frieren"}}"""
            )
        )
        assertEquals(
            CommunityScoreRefreshResult.Updated,
            repository.refresh(request(malId = 1), force = true)
        )
        assertEquals(8.9, dao.getByAniListId(100)?.score ?: 0.0, 0.0001)
        assertTrue(dao.getByAniListId(100)?.isManualLink == true)
    }

    private fun request(malId: Int?) = CommunityScoreRequest(
        aniListMediaId = 100,
        malId = malId,
        titleUserPreferred = "Frieren: Beyond Journey's End",
        titleEnglish = "Frieren: Beyond Journey's End",
        titleRomaji = "Sousou no Frieren",
        year = 2023,
        format = "TV"
    )

    private fun candidate(malId: Int, confidence: Double) = MalSearchCandidate(
        malId = malId,
        title = "Sousou no Frieren",
        titleEnglish = "Frieren: Beyond Journey's End",
        titleJapanese = null,
        score = 8.73,
        scoredBy = 424242,
        year = 2023,
        format = "TV",
        episodes = 28,
        confidence = confidence
    )
}

private class FakeCommunityScoreDao : CommunityScoreDao {
    private val rows = MutableStateFlow<Map<Int, CommunityScoreEntity>>(emptyMap())

    override fun observeByAniListIds(aniListMediaIds: List<Int>): Flow<List<CommunityScoreEntity>> =
        rows.map { current -> aniListMediaIds.mapNotNull(current::get) }

    override fun observeByAniListId(aniListMediaId: Int): Flow<CommunityScoreEntity?> =
        rows.map { it[aniListMediaId] }

    override suspend fun getByAniListId(aniListMediaId: Int): CommunityScoreEntity? =
        rows.value[aniListMediaId]

    override suspend fun upsert(entity: CommunityScoreEntity) {
        rows.value = rows.value + (entity.aniListMediaId to entity)
    }

    override suspend fun count(): Int = rows.value.size

    override suspend fun countFresh(now: Long): Int =
        rows.value.values.count { it.expiresAtEpochMillis > now }

    override suspend fun countStale(now: Long): Int =
        rows.value.values.count { it.expiresAtEpochMillis <= now }

    override suspend fun countUnavailable(): Int = rows.value.values.count(CommunityScoreEntity::unavailable)

    override suspend fun clear() {
        rows.value = emptyMap()
    }
}
