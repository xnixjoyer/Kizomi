package com.anisync.android.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisync.android.domain.provider.ActiveProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MalAccountSettingsScreen(
    onBackClick: () -> Unit,
    viewModel: MalAccountSettingsViewModel = hiltViewModel(),
) {
    val providerState by viewModel.providerState.collectAsStateWithLifecycle()
    val consent by viewModel.consentRecord.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Active provider") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                when (providerState.activeProvider) {
                    ActiveProvider.ANILIST_ONLY -> "AniList"
                    ActiveProvider.MAL_ONLY -> "MyAnimeList"
                    ActiveProvider.UNCONFIGURED -> "Not configured"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "Kizomi uses one provider for the whole app. Changing provider disconnects the current account, deletes credentials, account caches, queues, mappings, and extension state, and returns to first-run selection. No progress, score, status, date, note, or other account data is copied.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { confirm = true },
                enabled = !busy && providerState.activeProvider != ActiveProvider.UNCONFIGURED,
            ) { Text("Disconnect, delete local data, and change provider") }
            if (consent != null) {
                TextButton(onClick = viewModel::revokeMalConsent) {
                    Text("Revoke stored MyAnimeList consent")
                }
            }
            if (busy) CircularProgressIndicator()
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Change active provider?") },
            text = {
                Text("The current account will be disconnected. Tokens, account-bound caches, queues, mappings, pending work, calendar-extension state, controllable image cache entries, and local exports will be deleted. A new login is required. Nothing is copied to the next provider.")
            },
            confirmButton = {
                Button(onClick = {
                    confirm = false
                    viewModel.disconnectAndChangeProvider()
                }) { Text("Delete and continue") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
