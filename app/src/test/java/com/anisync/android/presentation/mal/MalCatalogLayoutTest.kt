package com.anisync.android.presentation.mal

import com.anisync.android.data.mal.api.MalSeason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MalCatalogLayoutTest {
    @Test
    fun `large font increases adaptive card width while narrow screens remain one column safe`() {
        val normal = malCatalogCardMinWidthDp(1f)
        val large = malCatalogCardMinWidthDp(1.4f)

        assertEquals(144, normal)
        assertTrue(large > normal)
        assertTrue(large <= 202)
        assertEquals(1, 200 / large)
    }

    @Test
    fun `calendar months map to the official MAL seasons`() {
        assertEquals(MalSeason.WINTER, malSeasonForMonth(1))
        assertEquals(MalSeason.SPRING, malSeasonForMonth(4))
        assertEquals(MalSeason.SUMMER, malSeasonForMonth(7))
        assertEquals(MalSeason.FALL, malSeasonForMonth(10))
    }
}
