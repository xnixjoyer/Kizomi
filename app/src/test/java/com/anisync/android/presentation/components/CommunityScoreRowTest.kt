package com.anisync.android.presentation.components

import com.anisync.android.data.CommunityScoreMode
import com.anisync.android.type.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class CommunityScoreRowTest {
    @Test
    fun `anime honors mode while manga and unknown retain AniList`() {
        assertEquals(
            CommunityScoreMode.BOTH,
            communityScoreModeForMediaType(CommunityScoreMode.BOTH, MediaType.ANIME)
        )
        assertEquals(
            CommunityScoreMode.ANILIST,
            communityScoreModeForMediaType(CommunityScoreMode.MYANIMELIST, MediaType.MANGA)
        )
        assertEquals(
            CommunityScoreMode.ANILIST,
            communityScoreModeForMediaType(CommunityScoreMode.BOTH, null)
        )
    }
}
