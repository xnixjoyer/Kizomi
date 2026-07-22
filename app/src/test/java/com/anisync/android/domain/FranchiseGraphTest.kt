package com.anisync.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FranchiseGraphTest {
    @Test
    fun watchOrderHonorsExplicitPrequelAndSequelBeforeChronology() {
        val graph = graph(
            nodes = listOf(
                node(1, "Middle", 2020),
                node(2, "Prequel released later", 2022),
                node(3, "Sequel released earlier", 2019)
            ),
            edges = listOf(
                FranchiseGraphEdge(sourceMediaId = 1, targetMediaId = 2, relationType = "PREQUEL"),
                FranchiseGraphEdge(sourceMediaId = 1, targetMediaId = 3, relationType = "SEQUEL")
            )
        )

        assertEquals(listOf(2, 1, 3), graph.watchOrder().map(FranchiseGraphNode::mediaId))
    }

    @Test
    fun watchOrderFallsBackToStableChronologyForCycles() {
        val graph = graph(
            nodes = listOf(node(10, "Later", 2021), node(11, "Earlier", 2018)),
            edges = listOf(
                FranchiseGraphEdge(10, 11, "SEQUEL"),
                FranchiseGraphEdge(11, 10, "SEQUEL")
            )
        )

        assertEquals(listOf(11, 10), graph.watchOrder().map(FranchiseGraphNode::mediaId))
    }

    @Test
    fun animeOnlyOrderExcludesMangaButFullOrderKeepsIt() {
        val anime = node(1, "Anime", 2020)
        val manga = node(2, "Manga", 2010, mediaType = "MANGA")
        val graph = graph(listOf(anime, manga), emptyList())

        assertEquals(listOf(1), graph.watchOrder(animeOnly = true).map(FranchiseGraphNode::mediaId))
        assertEquals(listOf(2, 1), graph.watchOrder(animeOnly = false).map(FranchiseGraphNode::mediaId))
    }

    @Test
    fun insightsSummarizeViewerProgressScoresAndRelations() {
        val graph = graph(
            nodes = listOf(
                node(1, "Complete", 2018, score = 88, listStatus = "COMPLETED"),
                node(2, "Untouched", 2020, score = 71),
                node(3, "Watching", 2022, score = null, listStatus = "CURRENT")
            ),
            edges = listOf(
                FranchiseGraphEdge(1, 2, "SEQUEL"),
                FranchiseGraphEdge(2, 3, "SEQUEL"),
                FranchiseGraphEdge(3, 1, "ALTERNATIVE")
            )
        )

        val insights = graph.insights()

        assertEquals(3, insights.totalNodes)
        assertEquals(3, insights.animeNodes)
        assertEquals(1, insights.completedNodes)
        assertEquals(1, insights.untouchedNodes)
        assertEquals(1, insights.highestRated?.mediaId)
        assertEquals(17, insights.scoreSpread)
        assertEquals(mapOf("SEQUEL" to 2, "ALTERNATIVE" to 1), insights.relationCounts)
    }

    @Test
    fun scoreSpreadIsAbsentWithOnlyOneRatedEntry() {
        val insights = graph(listOf(node(1, "Only", 2020, score = 80)), emptyList()).insights()

        assertNull(insights.scoreSpread)
    }

    private fun graph(
        nodes: List<FranchiseGraphNode>,
        edges: List<FranchiseGraphEdge>
    ) = FranchiseGraph(
        rootMediaId = nodes.first().mediaId,
        nodes = nodes,
        edges = edges,
        fetchedAtEpochMillis = 1L
    )

    private fun node(
        id: Int,
        title: String,
        year: Int,
        mediaType: String = "ANIME",
        score: Int? = null,
        listStatus: String? = null
    ) = FranchiseGraphNode(
        mediaId = id,
        mediaType = mediaType,
        titleUserPreferred = title,
        averageScore = score,
        startYear = year,
        listStatus = listStatus
    )
}
