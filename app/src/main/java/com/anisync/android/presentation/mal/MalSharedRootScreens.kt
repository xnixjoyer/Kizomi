package com.anisync.android.presentation.mal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.anisync.android.data.mal.api.MalMediaKey
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.presentation.util.LocalMainNavBarInset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MalSharedLibraryScreen(
    onMediaClick: (MalMediaKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MalLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigationInset = LocalMainNavBarInset.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Library") }) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { viewModel.selectType(TrackingMediaType.ANIME) }) {
                    Text(if (state.mediaType == TrackingMediaType.ANIME) "• Anime" else "Anime")
                }
                TextButton(onClick = { viewModel.selectType(TrackingMediaType.MANGA) }) {
                    Text(if (state.mediaType == TrackingMediaType.MANGA) "• Manga" else "Manga")
                }
                TextButton(onClick = viewModel::refresh) { Text("Refresh") }
            }
            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error?.let {
                Text(
                    "Refresh failed: ${it.kind.name}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp + navigationInset,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.entries, key = { it.localMediaId }) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            onMediaClick(MalMediaKey(entry.mediaType, entry.malId))
                        },
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = entry.coverUrl,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.title,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${entry.state.status?.name.orEmpty()} · ${entry.state.progress}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MalSharedAccountScreen(
    modifier: Modifier = Modifier,
    viewModel: MalProviderAccountViewModel = hiltViewModel(),
) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var confirm by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Account") }) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("MyAnimeList", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Kizomi uses MyAnimeList as the active provider for this session. Changing provider deletes local provider-bound credentials and data; nothing is copied.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { confirm = true }, enabled = !busy) {
                Text("Disconnect, delete local data, and change provider")
            }
            if (busy) CircularProgressIndicator()
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Disconnect MyAnimeList?") },
            text = {
                Text("The current account, credentials, account caches, queues, mappings, extension state, and controllable local files will be deleted. No data will be copied. A fresh login is required.")
            },
            confirmButton = {
                Button(onClick = {
                    confirm = false
                    viewModel.disconnectAndDelete()
                }) { Text("Delete and continue") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancel") }
            },
        )
    }
}
