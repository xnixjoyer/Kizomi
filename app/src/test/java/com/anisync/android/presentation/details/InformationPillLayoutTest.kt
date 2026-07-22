package com.anisync.android.presentation.details

import com.anisync.android.presentation.details.components.partitionInfoPills
import com.anisync.android.presentation.details.components.shouldIncludeFranchiseInfoPill
import com.anisync.android.type.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InformationPillLayoutTest {
    @Test
    fun `items are balanced deterministically across at most five rows`() {
        assertEquals(emptyList<List<Int>>(), partitionInfoPills<Int>(emptyList()))
        assertEquals(listOf(listOf(1)), partitionInfoPills(listOf(1)))
        assertEquals(listOf(listOf(1), listOf(2), listOf(3)), partitionInfoPills((1..3).toList()))
        assertEquals(5, partitionInfoPills((1..5).toList()).size)
        assertEquals(listOf(2, 1, 1, 1, 1), partitionInfoPills((1..6).toList()).map { it.size })
        assertEquals(listOf(3, 2, 2, 2, 2), partitionInfoPills((1..11).toList()).map { it.size })
        assertEquals((1..17).toList(), partitionInfoPills((1..17).toList()).flatten())
    }

    @Test
    fun `franchise action is anime only and requires a callback`() {
        assertTrue(shouldIncludeFranchiseInfoPill(MediaType.ANIME, true))
        assertFalse(shouldIncludeFranchiseInfoPill(MediaType.ANIME, false))
        assertFalse(shouldIncludeFranchiseInfoPill(MediaType.MANGA, true))
        assertFalse(shouldIncludeFranchiseInfoPill(null, true))
    }
}
