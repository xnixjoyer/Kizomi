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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.anisync.android.data.mal.account.MalAccountCredentialStore
import com.anisync.android.data.mal.api.MalApiFailure
import com.anisync.android.data.mal.api.MalApiFailureKind
import com.anisync.android.data.mal.api.MalLibraryRefreshResult
import com.anisync.android.data.mal.api.MalLibraryItem
import com.anisync.android.data.mal.api.MalLibraryRepository
import com.anisync.android.data.mal.api.MalMediaKey
import com.anisync.android.data.provider.ProviderSessionCoordinator
import com.anisync.android.domain.tracking.TrackingMediaType
import com.anisync.android.presentation.navigation.MalNativeDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

private enum class MalRootTab { CATALOG, LIBRARY, ACCOUNT }

@Serializable
private object MalProviderRoot

@Composable
fun MalProviderMainScreen() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = MalProviderRoot,
    ) {
        composable<MalProviderRoot> {
            MalProviderRootScreen(
                onMediaClick = { key ->
                    navController.navigate(
                        MalNativeDetails(key.mediaType.name, key.malId)
                    )
                },
            )
        }
        composable<MalNativeDetails> {
            MalDetailsScreen(
                onBackClick = { navController.popBackStack() },
                onRelatedClick = { key ->
                    navController.navigate(
                        MalNativeDetails(key.mediaType.name, key.malId)
                    )
                },
            )
        }
    }
}

@Composable
private fun MalProviderRootScreen(
    onMediaClick: (MalMediaKey) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(MalRootTab.CATALOG) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == MalRootTab.CATALOG,
                    onClick = { tab = MalRootTab.CATALOG },
                    icon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    label = { Text("Discover") },
                )
                NavigationBarItem(
                    selected = tab == MalRootTab.LIBRARY,
                    onClick = { tab = MalRootTab.LIBRARY },
                    icon = { Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null) },
                    label = { Text("Library") },
                )
                NavigationBarItem(
                    selected = tab == MalRootTab.ACCOUNT,
                    onClick = { tab = MalRootTab.ACCOUNT },
                    icon = { Icon(Icons.Outlined.AccountCircle, contentDescription = null) },
                    label = { Text("Account") },
                )
            }
        },
    ) { padding ->
        when (tab) {
            MalRootTab.CATALOG -> MalCatalogScreen(
                onBackClick = {},
                onMediaClick = onMediaClick,
                showBackButton = false,
                modifier = Modifier.padding(padding),
            )
            MalRootTab.LIBRARY -> MalLibraryScreen(
                onMediaClick = onMediaClick,
                modifier = Modifier.padding(padding),
            )
            MalRootTab.ACCOUNT -> MalProviderAccountScreen(
                modifier = Modifier.padding(padding),
            )
        }
    }
}

data class MalLibraryUiState(
    val mediaType: TrackingMediaType = TrackingMediaType.ANIME,
    val entries: List<MalLibraryItem> = emptyList(),
    val loading: Boolean = false,
    val error: MalApiFailure? = null,
)

@HiltViewModel
class MalLibraryViewModel @Inject constructor(
    private val repository: MalLibraryRepository,
    private val accounts: MalAccountCredentialStore,
) : ViewModel() {
    private val selectedType = MutableStateFlow(TrackingMediaType.ANIME)
    private val mutableState = MutableStateFlow(MalLibraryUiState())
    val uiState: StateFlow<MalLibraryUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                selectedType,
                selectedType.flatMapLatest { type ->
                    val account = accounts.activeAccount()
                    if (account == null) flowOf(emptyList())
                    else repository.observeLibrary(account.localAccountId, type)
                },
            ) { type, entries -> type to entries }
                .collect { (type, entries) ->
                    mutableState.update { it.copy(mediaType = type, entries = entries) }
                }
        }
        refresh()
    }

    fun selectType(type: TrackingMediaType) {
        selectedType.value = type
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            val account = accounts.activeAccount()
            if (account == null) {
                mutableState.update {
                    it.copy(
                        loading = false,
                        error = MalApiFailure(MalApiFailureKind.NOT_AUTHENTICATED),
                    )
                }
                return@launch
            }
            when (val result = repository.refresh(account.localAccountId, selectedType.value)) {
                is MalLibraryRefreshResult.Success -> mutableState.update { it.copy(loading = false) }
                is MalLibraryRefreshResult.Failure -> mutableState.update {
                    it.copy(loading = false, error = result.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MalLibraryScreen(
    onMediaClick: (MalMediaKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MalLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("MyAnimeList library") }) },
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
                contentPadding = PaddingValues(16.dp),
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

@HiltViewModel
class MalProviderAccountViewModel @Inject constructor(
    private val coordinator: ProviderSessionCoordinator,
) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun disconnectAndDelete() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            runCatching { coordinator.disconnectAndDeleteAllLocalProviderData() }
            _busy.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MalProviderAccountScreen(
    modifier: Modifier = Modifier,
    viewModel: MalProviderAccountViewModel = hiltViewModel(),
) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var confirm by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Active provider") }) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("MyAnimeList", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Changing provider is destructive. Kizomi does not copy progress, scores, status, dates, notes, or other account data.",
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
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
