package com.anisync.android.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisync.android.R
import com.anisync.android.presentation.model.MediaListItemPresentation
import com.anisync.android.presentation.model.ProviderMediaIdentity

/**
 * Shared provider-neutral list/search card. Provider-native data must be adapted before reaching it.
 */
@Composable
fun ProviderMediaListItem(
    item: MediaListItemPresentation,
    onClick: (ProviderMediaIdentity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = item.normalizedProgress
    val total = item.normalizedTotal

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.a11y_action_open_details, item.title),
            ) { onClick(item.identity) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = item.coverUrl,
                contentDescription = stringResource(R.string.a11y_media_poster, item.title),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(84.dp)
                    .aspectRatio(0.7f),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (progress != null) {
                    Text(
                        text = stringResource(
                            R.string.progress_format,
                            progress,
                            total?.toString() ?: stringResource(R.string.progress_unknown),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}
