package com.anisync.android.data

import com.anisync.android.domain.FranchiseGraphEdge
import com.anisync.android.domain.FranchiseGraphNode
import com.anisync.android.domain.FranchiseGraphTruncationReason
import com.anisync.android.domain.watchOrder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FranchiseGraphTraversalExtendedTest {
    @Test
    fun `split cours and parts are complete from first middle and newest roots`() = runTest {
        val source = chainSource(1..6)

        listOf(1, 3, 6).forEach { root ->
            val graph = traverseFranchiseGraph(root, source)
            assertEquals((1..6).toList(), graph.nodes.map(FranchiseGraphNode::mediaId))
            assertFalse(graph.isTruncated)
        }
    }

    @Test
    fun `same node reached over multiple paths is stored once while directed edges remain`() = runTest {
        val graph = traverseFranchiseGraph(
            rootMediaId = 1,
            source = graphSource(
                1 to listOf(2 to "SEQUEL", 3 to "SEQUEL"),
                2 to listOf(1 to "PREQUEL", 4 to "SEQUEL"),
                3 to listOf(1 to "PREQUEL", 4 to "SEQUEL"),
                4 to listOf(2 to "PREQUEL", 3 to "PREQUEL")
            )
        )

        assertEquals(listOf(1, 2, 3, 4), graph.nodes.map(FranchiseGraphNode::mediaId))
        assertEquals(8, graph.edges.size)
        assertEquals(graph.edges.distinct(), graph.edges)
        assertFalse(graph.isTruncated)
    }

    @Test
    fun `relation pages are accumulated before traversal continues`() = runTest {
        val source = FranchiseRelationBatchSource { ids, page ->
            ids.map { id ->
                val relations = when {
                    id == 1 && page == 1 -> listOf(RelationSnapshot(node(2), "SEQUEL"))
                    id == 1 && page == 2 -> listOf(RelationSnapshot(node(3), "SEQUEL"))
                    id == 2 -> listOf(RelationSnapshot(node(1), "PREQUEL"))
                    id == 3 -> listOf(RelationSnapshot(node(1), "PREQUEL"))
                    else -> emptyList()
                }
                RelationPageNode(
                    node = node(id),
                    relations = relations,
                    hasNextPage = id == 1 && page == 1
                )
            }
        }

        val graph = traverseFranchiseGraph(1, source, relationPageLimit = 3)

        assertEquals(listOf(1, 2, 3), graph.nodes.map(FranchiseGraphNode::mediaId))
        assertTrue(FranchiseGraphEdge(1, 2, "SEQUEL") in graph.edges)
        assertTrue(FranchiseGraphEdge(1, 3, "SEQUEL") in graph.edges)
        assertFalse(graph.isTruncated)
    }

    @Test
    fun `depth node and request limits are strict and visible`() = runTest {
        val source = chainSource(1..20)

        val depth = traverseFranchiseGraph(1, source, maxDepth = 2)
        assertTrue(depth.isTruncated)
        assertEquals(FranchiseGraphTruncationReason.DEPTH_LIMIT, depth.truncationReason)
        assertTrue(depth.maxDepthReached >= 2)

        val nodes = traverseFranchiseGraph(1, source, maxNodes = 4)
        assertTrue(nodes.isTruncated)
        assertEquals(FranchiseGraphTruncationReason.NODE_LIMIT, nodes.truncationReason)
        assertEquals(4, nodes.nodes.size)

        val requests = traverseFranchiseGraph(1, source, batchSize = 1, maxRequests = 2)
        assertTrue(requests.isTruncated)
        assertEquals(FranchiseGraphTruncationReason.REQUEST_LIMIT, requests.truncationReason)
        assertEquals(2, requests.requestCount)
    }

    @Test
    fun `pagination limit is surfaced instead of silently returning a complete graph`() = runTest {
        val graph = traverseFranchiseGraph(
            rootMediaId = 1,
            source = FranchiseRelationBatchSource { ids, _ ->
                ids.map { id -> RelationPageNode(node(id), emptyList(), hasNextPage = true) }
            },
            relationPageLimit = 2
        )

        assertTrue(graph.isTruncated)
        assertEquals(FranchiseGraphTruncationReason.PAGINATION_LIMIT, graph.truncationReason)
    }

    @Test
    fun `watch order preserves prequel and sequel direction`() = runTest {
        val graph = traverseFranchiseGraph(3, chainSource(1..5))

        assertEquals(listOf(1, 2, 3, 4, 5), graph.watchOrder().map(FranchiseGraphNode::mediaId))
    }

    @Test
    fun `nodes and edges are deterministic regardless of source ordering`() = runTest {
        val forward = traverseFranchiseGraph(1, diamondSource(reverse = false))
        val reverse = traverseFranchiseGraph(1, diamondSource(reverse = true))

        assertEquals(forward.nodes, reverse.nodes)
        assertEquals(forward.edges, reverse.edges)
        assertEquals(forward.maxDepthReached, reverse.maxDepthReached)
        assertEquals(forward.isTruncated, reverse.isTruncated)
    }

    @Test
    fun `cycles terminate without dropping nodes`() = runTest {
        val graph = traverseFranchiseGraph(
            1,
            graphSource(
                1 to listOf(2 to "SEQUEL"),
                2 to listOf(3 to "SEQUEL"),
                3 to listOf(1 to "SEQUEL")
            )
        )

        assertEquals(listOf(1, 2, 3), graph.nodes.map(FranchiseGraphNode::mediaId))
        assertEquals(3, graph.watchOrder().size)
        assertFalse(graph.isTruncated)
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

    private fun graphSource(vararg definitions: Pair<Int, List<Pair<Int, String>>>) =
        FranchiseRelationBatchSource { ids, _ ->
            val byId = definitions.toMap()
            ids.map { id ->
                RelationPageNode(
                    node = node(id),
                    relations = byId[id].orEmpty().map { (target, type) ->
                        RelationSnapshot(node(target), type)
                    },
                    hasNextPage = false
                )
            }
        }

    private fun diamondSource(reverse: Boolean) = FranchiseRelationBatchSource { ids, _ ->
        val relations = mapOf(
            1 to listOf(2 to "SEQUEL", 3 to "SEQUEL"),
            2 to listOf(1 to "PREQUEL", 4 to "SEQUEL"),
            3 to listOf(1 to "PREQUEL", 4 to "SEQUEL"),
            4 to listOf(2 to "PREQUEL", 3 to "PREQUEL")
        )
        ids.sortedWith(if (reverse) compareByDescending { it } else compareBy { it }).map { id ->
            val ordered = relations[id].orEmpty().let { if (reverse) it.reversed() else it }
            RelationPageNode(
                node(id),
                ordered.map { (target, type) -> RelationSnapshot(node(target), type) },
                false
            )
        }
    }

    private fun node(id: Int) = FranchiseGraphNode(
        mediaId = id,
        mediaType = "ANIME",
        titleUserPreferred = "Part $id",
        startYear = 2010 + id,
        startMonth = 1,
        startDay = 1
    )
}
