package com.anisync.android.data

import com.anisync.android.domain.FranchiseGraphNode
import com.anisync.android.domain.FranchiseGraphTruncationReason
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FranchiseGraphTraversalTest {
    @Test
    fun longSeasonChainIsTraversedBeyondTwoHopsFromMiddleAndNewestRoots() = runTest {
        val source = chainSource(1..7)

        val middle = traverseFranchiseGraph(4, source)
        val newest = traverseFranchiseGraph(7, source)

        assertEquals((1..7).toSet(), middle.nodes.map { it.mediaId }.toSet())
        assertEquals((1..7).toSet(), newest.nodes.map { it.mediaId }.toSet())
        assertFalse(middle.isTruncated)
        assertTrue(middle.maxDepthReached > 2)
    }

    @Test
    fun cyclesAndReciprocalRelationsTerminateAndPreserveDirectedEdges() = runTest {
        val source = FranchiseRelationBatchSource { ids, _ ->
            ids.map { id ->
                val target = if (id == 1) 2 else 1
                RelationPageNode(
                    node(id),
                    listOf(RelationSnapshot(node(target), if (id == 1) "SEQUEL" else "PREQUEL")),
                    false
                )
            }
        }

        val graph = traverseFranchiseGraph(1, source)

        assertEquals(2, graph.nodes.size)
        assertEquals(
            setOf(Triple(1, 2, "SEQUEL"), Triple(2, 1, "PREQUEL")),
            graph.edges.map { Triple(it.sourceMediaId, it.targetMediaId, it.relationType) }.toSet()
        )
        assertFalse(graph.isTruncated)
    }

    @Test
    fun depthNodeRequestAndPaginationLimitsExposeTruncation() = runTest {
        val depth = traverseFranchiseGraph(1, chainSource(1..8), maxDepth = 2)
        assertEquals(FranchiseGraphTruncationReason.DEPTH_LIMIT, depth.truncationReason)

        val nodes = traverseFranchiseGraph(1, chainSource(1..8), maxNodes = 3)
        assertEquals(FranchiseGraphTruncationReason.NODE_LIMIT, nodes.truncationReason)

        val requests = traverseFranchiseGraph(1, chainSource(1..8), maxRequests = 1)
        assertEquals(FranchiseGraphTruncationReason.REQUEST_LIMIT, requests.truncationReason)

        val pagination = traverseFranchiseGraph(
            1,
            FranchiseRelationBatchSource { ids, _ ->
                ids.map { RelationPageNode(node(it), emptyList(), hasNextPage = true) }
            },
            relationPageLimit = 2
        )
        assertEquals(FranchiseGraphTruncationReason.PAGINATION_LIMIT, pagination.truncationReason)
    }

    private fun chainSource(range: IntRange) = FranchiseRelationBatchSource { ids, _ ->
        ids.map { id ->
            val relations = buildList {
                if (id > range.first) add(RelationSnapshot(node(id - 1), "PREQUEL"))
                if (id < range.last) add(RelationSnapshot(node(id + 1), "SEQUEL"))
            }
            RelationPageNode(node(id), relations, false)
        }
    }

    private fun node(id: Int) = FranchiseGraphNode(
        mediaId = id,
        mediaType = "ANIME",
        titleUserPreferred = "Season $id",
        startYear = 2010 + id
    )
}
