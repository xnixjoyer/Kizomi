package com.anisync.android.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.anisync.android.data.NavBarStyle
import com.anisync.android.presentation.components.navigation.CompactNavBar
import com.anisync.android.presentation.components.navigation.CompactNavBarItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CompactNavBarDistributionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun oneThroughFiveVisibleItemsReceiveEqualMeasuredSlots() {
        composeRule.setContent {
            MaterialTheme {
                Column {
                    (1..5).forEach { count ->
                        CompactNavBar(
                            style = NavBarStyle.ANCHORED,
                            modifier = Modifier.width(320.dp)
                        ) {
                            repeat(count) { index ->
                                CompactNavBarItem(
                                    selected = index == 0,
                                    onClick = {},
                                    icon = { Text("$index") },
                                    label = { Text("Item $index") },
                                    showLabel = false,
                                    modifier = Modifier.testTag("slot-$count-$index")
                                )
                            }
                        }
                    }
                }
            }
        }

        (1..5).forEach { count ->
            val widths = (0 until count).map { index ->
                composeRule.onNodeWithTag("slot-$count-$index")
                    .fetchSemanticsNode().boundsInRoot.width
            }
            widths.drop(1).forEach { width ->
                assertEquals(widths.first(), width, 1f)
            }
        }
    }
}
