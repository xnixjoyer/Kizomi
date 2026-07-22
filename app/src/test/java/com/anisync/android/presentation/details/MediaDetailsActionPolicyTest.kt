package com.anisync.android.presentation.details

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Final merge-gate contract for the compact Favourite/Share placement accepted in PR #37. */
class MediaDetailsActionPolicyTest {
    @Test
    fun `secondary actions live at content end with material touch targets`() {
        assertEquals(DetailsSecondaryActionPlacement.CONTENT_END, detailsSecondaryActionPlacement)
        assertTrue(DETAILS_SECONDARY_ACTION_TOUCH_TARGET_DP >= 48)
    }
}
