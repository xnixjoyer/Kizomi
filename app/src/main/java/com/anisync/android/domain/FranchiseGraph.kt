package com.anisync.android.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class FranchiseGraph(
    val rootMediaId: Int,
    val nodes: List<FranchiseGraphNode>,
    val edges: List<FranchiseGraphEdge>,
    val fetchedAtEpochMillis: Long,
    /** Missing in legacy two-hop payloads, which decode as version 1 and are revalidated. */
    val schemaVersion: Int = 1,
    val isTruncated: Boolean = false,
    val truncationReason: FranchiseGraphTruncationReason? = null,
    val requestCount: Int = 0,
    val maxDepthReached: Int = 0
)

@Serializable
enum class FranchiseGraphTruncationReason {
    DEPTH_LIMIT,
    NODE_LIMIT,
    REQUEST_LIMIT,
    PAGINATION_LIMIT,
    LEGACY_CACHE
}

@Serializable
data class FranchiseGraphNode(
    val mediaId: Int,
    val malId: Int? = null,
    val mediaType: String? = null,
    val titleUserPreferred: String,
    val titleRomaji: String? = null,
    val titleEnglish: String? = null,
    val titleNative: String? = null,
    val coverMedium: String? = null,
    val coverLarge: String? = null,
    val coverExtraLarge: String? = null,
    val averageScore: Int? = null,
    val format: String? = null,
    val status: String? = null,
    val startYear: Int? = null,
    val startMonth: Int? = null,
    val startDay: Int? = null,
    val episodes: Int? = null,
    val listStatus: String? = null,
    val listProgress: Int? = null
) {
    val coverUrl: String?
        get() = coverExtraLarge ?: coverLarge ?: coverMedium
}

@Serializable
data class FranchiseGraphEdge(
    val sourceMediaId: Int,
    val targetMediaId: Int,
    val relationType: String
)

data class FranchiseInsights(
    val totalNodes: Int,
    val animeNodes: Int,
    val completedNodes: Int,
    val untouchedNodes: Int,
    val highestRated: FranchiseGraphNode?,
    val scoreSpread: Int?,
    val relationCounts: Map<String, Int>
)

interface FranchiseGraphRepository {
    fun observe(mediaId: Int): Flow<FranchiseGraph?>
    suspend fun refresh(mediaId: Int, force: Boolean = false): Result<Unit>
}

fun FranchiseGraph.insights(): FranchiseInsights {
    val scored = nodes.filter { it.averageScore != null }
    return FranchiseInsights(
        totalNodes = nodes.size,
        animeNodes = nodes.count { it.mediaType == "ANIME" },
        completedNodes = nodes.count { it.listStatus == "COMPLETED" },
        untouchedNodes = nodes.count { it.listStatus == null },
        highestRated = scored.maxByOrNull { it.averageScore ?: 0 },
        scoreSpread = if (scored.size >= 2) {
            (scored.maxOf { it.averageScore ?: 0 } - scored.minOf { it.averageScore ?: 0 })
        } else null,
        relationCounts = edges.groupingBy(FranchiseGraphEdge::relationType).eachCount()
    )
}

/**
 * Deterministic watch order: explicit prequel/sequel constraints win; dates break ties; cycles
 * fall back to chronological order instead of dropping nodes.
 */
fun FranchiseGraph.watchOrder(animeOnly: Boolean = true): List<FranchiseGraphNode> {
    val selected = nodes.filter { !animeOnly || it.mediaType == "ANIME" }
    val selectedIds = selected.mapTo(hashSetOf(), FranchiseGraphNode::mediaId)
    val outgoing = selectedIds.associateWith { linkedSetOf<Int>() }.toMutableMap()
    val indegree = selectedIds.associateWith { 0 }.toMutableMap()

    edges.forEach { edge ->
        if (edge.sourceMediaId !in selectedIds || edge.targetMediaId !in selectedIds) return@forEach
        val (before, after) = when (edge.relationType) {
            "PREQUEL" -> edge.targetMediaId to edge.sourceMediaId
            "SEQUEL" -> edge.sourceMediaId to edge.targetMediaId
            else -> return@forEach
        }
        if (outgoing.getValue(before).add(after)) indegree[after] = indegree.getValue(after) + 1
    }

    val byId = selected.associateBy(FranchiseGraphNode::mediaId)
    val comparator = compareBy<Int>(
        { byId[it]?.startYear ?: Int.MAX_VALUE },
        { byId[it]?.startMonth ?: Int.MAX_VALUE },
        { byId[it]?.startDay ?: Int.MAX_VALUE },
        { it }
    )
    val ready = java.util.PriorityQueue(comparator)
    indegree.filterValues { it == 0 }.keys.forEach(ready::add)
    val ordered = ArrayList<FranchiseGraphNode>(selected.size)
    val emitted = hashSetOf<Int>()
    while (ready.isNotEmpty()) {
        val id = ready.remove()
        if (!emitted.add(id)) continue
        byId[id]?.let(ordered::add)
        outgoing.getValue(id).forEach { target ->
            indegree[target] = indegree.getValue(target) - 1
            if (indegree.getValue(target) == 0) ready.add(target)
        }
    }
    if (ordered.size < selected.size) {
        selected.filterNot { it.mediaId in emitted }
            .sortedWith(compareBy({ it.startYear ?: Int.MAX_VALUE }, { it.startMonth ?: 13 }, { it.mediaId }))
            .forEach(ordered::add)
    }
    return ordered
}
