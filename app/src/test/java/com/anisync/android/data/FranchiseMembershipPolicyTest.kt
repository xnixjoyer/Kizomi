package com.anisync.android.data

import com.anisync.android.domain.FranchiseGraph
import com.anisync.android.domain.FranchiseGraphEdge
import com.anisync.android.domain.FranchiseGraphNode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FranchiseMembershipPolicyTest {
    @Test
    fun `weak crossover relation types are rejected`() {
        assertFalse(isFranchiseMembershipRelation("CHARACTER"))
        assertFalse(isFranchiseMembershipRelation("OTHER"))
        assertTrue(isFranchiseMembershipRelation("SEQUEL"))
        assertTrue(isFranchiseMembershipRelation("SPIN_OFF"))
        assertTrue(isFranchiseMembershipRelation("ADAPTATION"))
    }

    @Test
    fun `traversal cannot cross from a franchise through a character relation`() = runTest {
        val pages = mapOf(
            1 to RelationPageNode(
                node(1),
                listOf(
                    RelationSnapshot(node(2), "SEQUEL"),
                    RelationSnapshot(node(99), "CHARACTER"),
                ),
                false,
            ),
            2 to RelationPageNode(node(2), listOf(RelationSnapshot(node(3), "SEQUEL")), false),
            3 to RelationPageNode(node(3), emptyList(), false),
            99 to RelationPageNode(node(99), listOf(RelationSnapshot(node(100), "SEQUEL")), false),
            100 to RelationPageNode(node(100), emptyList(), false),
        )
        val graph = traverseFranchiseGraph(
            rootMediaId = 1,
            source = FranchiseRelationBatchSource { ids, _ -> ids.mapNotNull(pages::get) },
        )

        assertEquals(listOf(1, 2, 3), graph.nodes.map { it.mediaId })
        assertEquals(listOf("SEQUEL", "SEQUEL"), graph.edges.map { it.relationType })
    }

    @Test
    fun `legacy contaminated graph is pruned to root strong component`() {
        val graph = FranchiseGraph(
            rootMediaId = 1,
            nodes = listOf(node(1), node(2), node(99), node(100)),
            edges = listOf(
                FranchiseGraphEdge(1, 2, "SEQUEL"),
                FranchiseGraphEdge(1, 99, "CHARACTER"),
                FranchiseGraphEdge(99, 100, "SEQUEL"),
            ),
            fetchedAtEpochMillis = 1L,
            schemaVersion = 2,
        )

        val sanitized = sanitizeFranchiseGraphMembership(graph)

        assertEquals(listOf(1, 2), sanitized.nodes.map { it.mediaId })
        assertEquals(listOf(FranchiseGraphEdge(1, 2, "SEQUEL")), sanitized.edges)
    }

    private fun node(id: Int) = FranchiseGraphNode(
        mediaId = id,
        mediaType = "ANIME",
        titleUserPreferred = "Media $id",
    )
}
