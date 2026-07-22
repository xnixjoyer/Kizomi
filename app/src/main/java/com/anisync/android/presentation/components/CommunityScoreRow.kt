package com.anisync.android.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anisync.android.data.CommunityScoreMode
import com.anisync.android.type.MediaType
import java.util.Locale

/** Jikan's score endpoint is anime-only; non-anime views always retain AniList scores. */
internal fun communityScoreModeForMediaType(
    mode: CommunityScoreMode,
    mediaType: MediaType?
): CommunityScoreMode = if (mediaType == MediaType.ANIME) mode else CommunityScoreMode.ANILIST

/** Clearly labels the two independent community aggregates; no personal score is overwritten. */
@Composable
fun CommunityScoreRow(
    mode: CommunityScoreMode,
    aniListScore: Int?,
    malScore: Double?,
    modifier: Modifier = Modifier,
    malScoreStale: Boolean = false,
    compact: Boolean = false,
    showUnavailable: Boolean = true
) {
    val showAniList = mode.usesAniList && (aniListScore != null || showUnavailable)
    val showMal = mode.usesMyAnimeList && (malScore != null || showUnavailable)
    if (!showAniList && !showMal) return

    val aniListText = aniListScore?.let { String.format(Locale.ROOT, "%.1f", it / 10f) } ?: "—"
    val malText = malScore?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "—"
    val semanticsText = buildList {
        if (showAniList) add("AniList $aniListText")
        if (showMal) add("MyAnimeList ${if (malScoreStale) "cached " else ""}$malText")
    }.joinToString(", ")

    Surface(
        modifier = modifier.semantics { contentDescription = semanticsText },
        shape = RoundedCornerShape(if (compact) 8.dp else 10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 6.dp else 8.dp,
                vertical = if (compact) 3.dp else 4.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = Color(0xFFFFC107),
                modifier = Modifier
                    .size(if (compact) 14.dp else 16.dp)
                    .then(if (compact) Modifier else Modifier.padding(end = 1.dp))
            )
            if (showAniList) {
                ScoreSource(label = "AL", score = aniListText, compact = compact, brand = MaterialTheme.colorScheme.primary)
            }
            if (showAniList && showMal) {
                Text(
                    text = "/",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (showMal) {
                ScoreSource(
                    label = "MAL",
                    score = if (malScoreStale && malScore != null) "~$malText" else malText,
                    compact = compact,
                    brand = MAL_BLUE
                )
            }
        }
    }
}

@Composable
private fun ScoreSource(label: String, score: String, compact: Boolean, brand: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Surface(shape = RoundedCornerShape(4.dp), color = brand) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                color = Color.White,
                fontSize = if (compact) 8.sp else 9.sp,
                lineHeight = if (compact) 9.sp else 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = score,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private val MAL_BLUE = Color(0xFF2E51A2)
