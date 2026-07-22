package com.anisync.android.data

import com.anisync.android.GetMediaRelationBatchQuery
import com.anisync.android.data.local.dao.FranchiseGraphDao
import com.anisync.android.data.local.entity.FranchiseGraphEntity
import com.anisync.android.data.util.safeApiCall
import com.anisync.android.domain.FranchiseGraph
import com.anisync.android.domain.FranchiseGraphEdge
import com.anisync.android.domain.FranchiseGraphNode
import com.anisync.android.domain.FranchiseGraphRepository
import com.anisync.android.domain.FranchiseGraphTruncationReason
import com.anisync.android.domain.Result
import com.anisync.android.fragment.FranchiseGraphNodeFields
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.cache.normalized.FetchPolicy
import com.apollographql.apollo.cache.normalized.fetchPolicy
import com.apollographql.apollo.api.Optional
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private const val GRAPH_TTL_MS = 48L * 60L * 60L * 1_000L
internal const val FRANCHISE_GRAPH_SCHEMA_VERSION = 3
internal const val DEFAULT_GRAPH_MAX_DEPTH = 8
internal const val DEFAULT_GRAPH_MAX_NODES = 80
internal const val DEFAULT_GRAPH_MAX_REQUESTS = 16
internal const val DEFAULT_GRAPH_BATCH_SIZE = 10
internal const val DEFAULT_RELATION_PAGE_SIZE = 25
internal const val DEFAULT_RELATION_PAGE_LIMIT = 4


/**
 * Relations that express membership in the same narrative/adaptation franchise. AniList also
 * exposes weak links such as CHARACTER and OTHER; traversing those creates crossover hubs and can
 * connect otherwise unrelated franchises transitively.
 */
internal val FRANCHISE_MEMBERSHIP_RELATION_TYPES = setOf(
    "ADAPTATION",
    "ALTERNATIVE",
    "COMPILATION",
    "CONTAINS",
    "PARENT",
    "PREQUEL",
    "SEQUEL",
    "SIDE_STORY",
    "SOURCE",
    "SPIN_OFF",
    "SUMMARY",
)

internal fun isFranchiseMembershipRelation(relationType: String): Boolean =
    relationType.uppercase() in FRANCHISE_MEMBERSHIP_RELATION_TYPES

/** Prunes contaminated legacy caches to the root's strong-relation connected component. */
internal fun sanitizeFranchiseGraphMembership(graph: FranchiseGraph): FranchiseGraph {
    val safeEdges = graph.edges.filter { isFranchiseMembershipRelation(it.relationType) }
    val nodeIds = graph.nodes.mapTo(hashSetOf(), FranchiseGraphNode::mediaId)
    if (graph.rootMediaId !in nodeIds) return graph.copy(nodes = emptyList(), edges = emptyList())

    val adjacency = mutableMapOf<Int, MutableSet<Int>>()
    safeEdges.forEach { edge ->
        adjacency.getOrPut(edge.sourceMediaId) { linkedSetOf() }.add(edge.targetMediaId)
        adjacency.getOrPut(edge.targetMediaId) { linkedSetOf() }.add(edge.sourceMediaId)
    }
    val reachable = linkedSetOf(graph.rootMediaId)
    val queue = ArrayDeque<Int>().apply { add(graph.rootMediaId) }
    while (queue.isNotEmpty()) {
        adjacency[queue.removeFirst()].orEmpty().sorted().forEach { target ->
            if (target in nodeIds && reachable.add(target)) queue.add(target)
        }
    }
    return graph.copy(
        nodes = graph.nodes.filter { it.mediaId in reachable }.sortedBy(FranchiseGraphNode::mediaId),
        edges = safeEdges.filter { it.sourceMediaId in reachable && it.targetMediaId in reachable }
            .sortedWith(
                compareBy(FranchiseGraphEdge::sourceMediaId)
                    .thenBy(FranchiseGraphEdge::targetMediaId)
                    .thenBy(FranchiseGraphEdge::relationType)
            ),
    )
}

internal data class RelationSnapshot(
    val target: FranchiseGraphNode,
    val relationType: String
)

internal data class RelationPageNode(
    val node: FranchiseGraphNode,
    val relations: List<RelationSnapshot>,
    val hasNextPage: Boolean
)

internal fun interface FranchiseRelationBatchSource {
    suspend fun load(ids: List<Int>, relationPage: Int): List<RelationPageNode>
}

@Singleton
class FranchiseGraphRepositoryImpl @Inject constructor(
    private val apolloClient: ApolloClient,
    private val dao: FranchiseGraphDao
) : FranchiseGraphRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override fun observe(mediaId: Int): Flow<FranchiseGraph?> = dao.observe(mediaId).map { entity ->
        entity?.let {
            runCatching { json.decodeFromString<FranchiseGraph>(it.payloadJson) }.getOrNull()
                ?.let(::sanitizeFranchiseGraphMembership)
                ?.let(::markLegacyCacheIncomplete)
        }
    }

    override suspend fun refresh(mediaId: Int, force: Boolean): Result<Unit> {
        val cached = dao.get(mediaId)
        val cachedGraph = cached?.let {
            runCatching { json.decodeFromString<FranchiseGraph>(it.payloadJson) }.getOrNull()
        }
        if (
            !force && cached != null && cachedGraph?.schemaVersion == FRANCHISE_GRAPH_SCHEMA_VERSION &&
            System.currentTimeMillis() - cached.fetchedAtEpochMillis < GRAPH_TTL_MS
        ) {
            return Result.Success(Unit)
        }

        return safeApiCall {
            val graph = traverseFranchiseGraph(
                rootMediaId = mediaId,
                source = FranchiseRelationBatchSource(::loadRelationBatch)
            )
            dao.upsert(
                FranchiseGraphEntity(
                    rootMediaId = mediaId,
                    payloadJson = json.encodeToString(graph),
                    fetchedAtEpochMillis = graph.fetchedAtEpochMillis
                )
            )
        }
    }

    private suspend fun loadRelationBatch(ids: List<Int>, relationPage: Int): List<RelationPageNode> {
        val response = apolloClient.query(
            GetMediaRelationBatchQuery(
                ids = Optional.present(ids)
            )
        ).fetchPolicy(FetchPolicy.NetworkOnly).execute()
        if (response.hasErrors()) {
            throw IllegalStateException(response.errors?.firstOrNull()?.message ?: "Relation graph failed")
        }
        return response.data?.Page?.media?.filterNotNull().orEmpty().map { media ->
            RelationPageNode(
                node = media.franchiseGraphNodeFields.toDomainNode(),
                relations = media.relations?.edges?.filterNotNull().orEmpty().mapNotNull { edge ->
                    val target = edge.node ?: return@mapNotNull null
                    RelationSnapshot(
                        target = target.franchiseGraphNodeFields.toDomainNode(),
                        relationType = edge.relationType?.name ?: "OTHER"
                    )
                },
                // AniList's Media.relations connection is non-pageable in the current schema.
                hasNextPage = false
            )
        }
    }
}

internal suspend fun traverseFranchiseGraph(
    rootMediaId: Int,
    source: FranchiseRelationBatchSource,
    maxDepth: Int = DEFAULT_GRAPH_MAX_DEPTH,
    maxNodes: Int = DEFAULT_GRAPH_MAX_NODES,
    maxRequests: Int = DEFAULT_GRAPH_MAX_REQUESTS,
    batchSize: Int = DEFAULT_GRAPH_BATCH_SIZE,
    relationPageLimit: Int = DEFAULT_RELATION_PAGE_LIMIT,
    now: () -> Long = System::currentTimeMillis
): FranchiseGraph {
    val queue = ArrayDeque<Pair<Int, Int>>()
    val queued = linkedSetOf(rootMediaId)
    val expanded = hashSetOf<Int>()
    val depthById = mutableMapOf(rootMediaId to 0)
    val nodes = linkedMapOf<Int, FranchiseGraphNode>()
    val edges = linkedMapOf<String, FranchiseGraphEdge>()
    queue.add(rootMediaId to 0)
    var requests = 0
    var maxDepthReached = 0
    var truncation: FranchiseGraphTruncationReason? = null

    while (queue.isNotEmpty() && truncation == null) {
        val batch = buildList {
            while (queue.isNotEmpty() && size < batchSize.coerceAtLeast(1)) {
                val next = queue.removeFirst()
                if (expanded.add(next.first)) add(next)
            }
        }
        if (batch.isEmpty()) continue
        val ids = batch.map { it.first }
        var page = 1
        var hasNextPage: Boolean
        do {
            if (requests >= maxRequests) {
                truncation = FranchiseGraphTruncationReason.REQUEST_LIMIT
                break
            }
            val pageNodes = source.load(ids, page)
            requests++
            hasNextPage = false
            pageNodes.sortedBy { it.node.mediaId }.forEach { pageNode ->
                val sourceDepth = depthById[pageNode.node.mediaId] ?: 0
                maxDepthReached = maxOf(maxDepthReached, sourceDepth)
                nodes.putIfAbsent(pageNode.node.mediaId, pageNode.node)
                hasNextPage = hasNextPage || pageNode.hasNextPage
                pageNode.relations.asSequence()
                    .filter { isFranchiseMembershipRelation(it.relationType) }
                    .sortedWith(compareBy({ it.target.mediaId }, { it.relationType }))
                    .forEach { relation ->
                        if (relation.target.mediaId == pageNode.node.mediaId) return@forEach
                        if (nodes.size >= maxNodes && relation.target.mediaId !in nodes) {
                            truncation = FranchiseGraphTruncationReason.NODE_LIMIT
                            return@forEach
                        }
                        nodes.putIfAbsent(relation.target.mediaId, relation.target)
                        val edge = FranchiseGraphEdge(
                            sourceMediaId = pageNode.node.mediaId,
                            targetMediaId = relation.target.mediaId,
                            relationType = relation.relationType
                        )
                        edges.putIfAbsent(
                            "${edge.sourceMediaId}:${edge.targetMediaId}:${edge.relationType}",
                            edge
                        )
                        val nextDepth = sourceDepth + 1
                        maxDepthReached = maxOf(maxDepthReached, nextDepth)
                        val previousDepth = depthById[relation.target.mediaId]
                        if (previousDepth == null || nextDepth < previousDepth) {
                            depthById[relation.target.mediaId] = nextDepth
                        }
                        when {
                            nextDepth > maxDepth -> {
                                truncation = truncation ?: FranchiseGraphTruncationReason.DEPTH_LIMIT
                            }
                            relation.target.mediaId !in expanded && queued.add(relation.target.mediaId) -> {
                                queue.add(relation.target.mediaId to nextDepth)
                            }
                        }
                    }
            }
            page++
            if (hasNextPage && page > relationPageLimit) {
                truncation = FranchiseGraphTruncationReason.PAGINATION_LIMIT
            }
        } while (hasNextPage && truncation == null)
    }

    return FranchiseGraph(
        rootMediaId = rootMediaId,
        nodes = nodes.values.sortedBy(FranchiseGraphNode::mediaId),
        edges = edges.values.sortedWith(
            compareBy(FranchiseGraphEdge::sourceMediaId)
                .thenBy(FranchiseGraphEdge::targetMediaId)
                .thenBy(FranchiseGraphEdge::relationType)
        ),
        fetchedAtEpochMillis = now(),
        schemaVersion = FRANCHISE_GRAPH_SCHEMA_VERSION,
        isTruncated = truncation != null,
        truncationReason = truncation,
        requestCount = requests,
        maxDepthReached = maxDepthReached
    )
}

internal fun markLegacyCacheIncomplete(graph: FranchiseGraph): FranchiseGraph =
    if (graph.schemaVersion >= FRANCHISE_GRAPH_SCHEMA_VERSION) graph else graph.copy(
        isTruncated = true,
        truncationReason = FranchiseGraphTruncationReason.LEGACY_CACHE
    )

private fun FranchiseGraphNodeFields.toDomainNode(): FranchiseGraphNode = FranchiseGraphNode(
    mediaId = id,
    malId = idMal,
    mediaType = type?.name,
    titleUserPreferred = title?.userPreferred ?: "Unknown",
    titleRomaji = title?.romaji,
    titleEnglish = title?.english,
    titleNative = title?.native,
    coverMedium = coverImage?.medium,
    coverLarge = coverImage?.large,
    coverExtraLarge = coverImage?.extraLarge,
    averageScore = averageScore,
    format = format?.name,
    status = status?.name,
    startYear = startDate?.year,
    startMonth = startDate?.month,
    startDay = startDate?.day,
    episodes = episodes,
    listStatus = mediaListEntry?.status?.name,
    listProgress = mediaListEntry?.progress
)
