package com.anisync.android.presentation.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anisync.android.R
import com.anisync.android.data.TitleLanguage
import com.anisync.android.domain.LibraryEntry
import com.anisync.android.domain.LibraryStatus
import com.anisync.android.presentation.adapters.toMediaListItemPresentation
import com.anisync.android.presentation.components.ProviderMediaListItem
import com.anisync.android.presentation.model.ProviderMediaIdentity
import com.anisync.android.type.MediaType

/**
 * Compact AniList library search result rendered through the provider-neutral shared card.
 *
 * The outer no-argument callback is retained for source compatibility with the existing Library
 * screen. Provider identity is still checked at this adapter boundary before the callback fires.
 */
@Composable
fun LibrarySearchResultCard(
    entry: LibraryEntry,
    mediaType: MediaType,
    onClick: () -> Unit,
    titleLanguage: TitleLanguage,
    modifier: Modifier = Modifier,
) {
    val presentation = remember(entry, mediaType, titleLanguage) {
        entry.toMediaListItemPresentation(mediaType, titleLanguage)
    }

    ProviderMediaListItem(
        item = presentation,
        onClick = { identity ->
            if (identity is ProviderMediaIdentity.AniList && identity.mediaId == entry.mediaId) {
                onClick()
            }
        },
        modifier = modifier,
    )
}

/**
 * Empty state display for library tabs.
 * Shows an appropriate icon and message based on the status and media type.
 */
@Composable
fun EmptyLibraryTabState(
    status: LibraryStatus?,
    type: MediaType,
    modifier: Modifier = Modifier,
) {
    val icon = remember(status, type) {
        when (status) {
            LibraryStatus.CURRENT -> if (type == MediaType.ANIME) {
                Icons.Default.PlayArrow
            } else {
                Icons.AutoMirrored.Filled.MenuBook
            }
            LibraryStatus.PLANNING -> Icons.Default.Check
            LibraryStatus.COMPLETED -> Icons.Default.Check
            else -> Icons.Default.Inbox
        }
    }

    val messageResId = remember(status, type) {
        when (status) {
            LibraryStatus.CURRENT -> if (type == MediaType.ANIME) {
                R.string.empty_watching
            } else {
                R.string.empty_reading
            }
            LibraryStatus.PLANNING -> R.string.empty_planning
            LibraryStatus.COMPLETED -> R.string.empty_completed
            else -> R.string.empty_default
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(80.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = androidx.compose.ui.res.stringResource(messageResId),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
