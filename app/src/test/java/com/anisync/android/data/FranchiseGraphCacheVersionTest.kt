package com.anisync.android.data

import com.anisync.android.domain.FranchiseGraph
import com.anisync.android.domain.FranchiseGraphNode
import com.anisync.android.domain.FranchiseGraphTruncationReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FranchiseGraphCacheVersionTest {
    @Test
    fun `legacy two hop payload is never exposed as a complete graph`() {
        val legacy = FranchiseGraph(
            rootMediaId = 1,
            nodes = listOf(node(1), node(2)),
            edges = emptyList(),
            fetchedAtEpochMillis = 100L
        )

        val repaired = markLegacyCacheIncomplete(legacy)

        assertTrue(repaired.isTruncated)
        assertEquals(FranchiseGraphTruncationReason.LEGACY_CACHE, repaired.truncationReason)
        assertEquals(1, repaired.schemaVersion)
        assertEquals(legacy.nodes, repaired.nodes)
    }

    @Test
    fun `current payload keeps its completeness and truncation metadata`() {
        val complete = FranchiseGraph(
            rootMediaId = 1,
            nodes = listOf(node(1)),
            edges = emptyList(),
            fetchedAtEpochMillis = 100L,
            schemaVersion = FRANCHISE_GRAPH_SCHEMA_VERSION
        )
        val limited = complete.copy(
            isTruncated = true,
            truncationReason = FranchiseGraphTruncationReason.NODE_LIMIT
        )

        assertFalse(markLegacyCacheIncomplete(complete).isTruncated)
        assertEquals(limited, markLegacyCacheIncomplete(limited))
    }

    private fun node(id: Int) = FranchiseGraphNode(
        mediaId = id,
        mediaType = "ANIME",
        titleUserPreferred = "Season $id"
    )
}
