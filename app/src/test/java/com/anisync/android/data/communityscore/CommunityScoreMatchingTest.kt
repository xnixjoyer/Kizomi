package com.anisync.android.data.communityscore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityScoreMatchingTest {
    @Test
    fun exactAndPunctuationVariantsRemainHighConfidence() {
        assertEquals(1.0, titleSimilarity("Frieren: Beyond Journey's End", "Frieren Beyond Journey s End"), 0.0001)
        assertTrue(titleSimilarity("Kaguya-sama: Love is War", "Kaguya sama Love is War") >= 0.95)
    }

    @Test
    fun unrelatedFranchiseEntriesAreNotPlausible() {
        assertTrue(titleSimilarity("Fate stay night Unlimited Blade Works", "Fate kaleid liner Prisma Illya") < 0.55)
        assertTrue(titleSimilarity("One Piece", "One Punch Man") < 0.55)
    }

    @Test
    fun emptyTitlesNeverProduceAFalseMatch() {
        assertEquals(0.0, titleSimilarity("", "Cowboy Bebop"), 0.0)
        assertEquals(0.0, titleSimilarity("...", "---"), 0.0)
    }
}
