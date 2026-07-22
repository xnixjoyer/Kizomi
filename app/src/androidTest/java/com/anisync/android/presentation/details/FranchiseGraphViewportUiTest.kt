package com.anisync.android.presentation.details

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.anisync.android.domain.FranchiseGraph
import com.anisync.android.domain.FranchiseGraphEdge
import com.anisync.android.domain.FranchiseGraphNode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FranchiseGraphViewportUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun extremePanAndZoomRemainClippedBelowTabsAndRelationFilters() {
        composeRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Universe tabs",
                            modifier = Modifier
                                .height(56.dp)
                                .testTag("franchise_mode_tabs")
                        )
                        FranchiseGraphCanvas(
                            graph = longGraph(),
                            resetViewportSignal = 0,
                            onMediaClick = {},
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        val viewport = composeRule.onNodeWithTag("franchise_graph_viewport")
        viewport.assertIsDisplayed()
        viewport.performTouchInput {
            val c = center
            pinch(
                start0 = c + Offset(-24f, -24f),
                end0 = c + Offset(-220f, -220f),
                start1 = c + Offset(24f, 24f),
                end1 = c + Offset(220f, 220f)
            )
            repeat(4) {
                swipe(
                    start = center,
                    end = Offset(center.x - width, center.y - height),
                    durationMillis = 120
                )
            }
        }

        composeRule.onNodeWithTag("franchise_mode_tabs").assertIsDisplayed()
        composeRule.onNodeWithTag("franchise_relation_filters").assertIsDisplayed()
        viewport.assertIsDisplayed()

        val tabsBounds = composeRule.onNodeWithTag("franchise_mode_tabs")
            .fetchSemanticsNode().boundsInRoot
        val filtersBounds = composeRule.onNodeWithTag("franchise_relation_filters")
            .fetchSemanticsNode().boundsInRoot
        val viewportBounds = viewport.fetchSemanticsNode().boundsInRoot

        assertTrue(tabsBounds.bottom <= filtersBounds.top)
        assertTrue(filtersBounds.bottom <= viewportBounds.top)
    }

    private fun longGraph(): FranchiseGraph {
        val nodes = (1..18).map { id ->
            FranchiseGraphNode(
                mediaId = id,
                mediaType = "ANIME",
                titleUserPreferred = "Season $id",
                startYear = 2000 + id
            )
        }
        val edges = buildList {
            for (id in 1 until 18) {
                add(FranchiseGraphEdge(id, id + 1, "SEQUEL"))
                add(FranchiseGraphEdge(id + 1, id, "PREQUEL"))
            }
        }
        return FranchiseGraph(
            rootMediaId = 9,
            nodes = nodes,
            edges = edges,
            fetchedAtEpochMillis = 1L,
            schemaVersion = 2
        )
    }
}
