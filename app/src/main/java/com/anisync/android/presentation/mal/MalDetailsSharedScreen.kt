package com.anisync.android.presentation.mal

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisync.android.R
import com.anisync.android.presentation.model.ProviderMediaIdentity
import com.anisync.android.presentation.provider.details.ProviderDetailsContent
import com.anisync.android.presentation.provider.details.ProviderDetailsFailure
import com.anisync.android.presentation.provider.details.ProviderDetailsStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MalDetailsSharedScreen(
    onBackClick: () -> Unit,
    onRelatedClick: (ProviderMediaIdentity) -> Unit,
    onEditListEntry: ((ProviderMediaIdentity) -> Unit)?,
    modifier: Modifier = Modifier,
    viewModel: MalDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mal_shared_details_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        ProviderDetailsContent(
            state = state.toProviderDetailsUiState(),
            strings = malDetailsStrings(),
            onRetry = viewModel::refresh,
            onRelatedClick = onRelatedClick,
            onEditListEntry = onEditListEntry,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun malDetailsStrings(): ProviderDetailsStrings = ProviderDetailsStrings(
    loading = stringResource(R.string.mal_shared_details_loading),
    retryAction = stringResource(R.string.mal_shared_discover_retry),
    editListAction = stringResource(R.string.mal_shared_details_edit_list),
    alternativeTitles = stringResource(R.string.mal_shared_details_alternative_titles),
    listState = stringResource(R.string.mal_shared_details_list_state),
    synopsis = stringResource(R.string.mal_shared_details_synopsis),
    facts = stringResource(R.string.mal_shared_details_facts),
    statistics = stringResource(R.string.mal_shared_details_statistics),
    genres = stringResource(R.string.mal_shared_details_genres),
    creators = stringResource(R.string.mal_shared_details_creators),
    studios = stringResource(R.string.mal_shared_details_studios),
    background = stringResource(R.string.mal_shared_details_background),
    relations = stringResource(R.string.mal_shared_details_relations),
    recommendations = stringResource(R.string.mal_shared_details_recommendations),
    format = stringResource(R.string.mal_shared_details_format),
    status = stringResource(R.string.mal_shared_details_status),
    startDate = stringResource(R.string.mal_shared_details_start_date),
    endDate = stringResource(R.string.mal_shared_details_end_date),
    episodes = stringResource(R.string.mal_shared_details_episodes),
    chapters = stringResource(R.string.mal_shared_details_chapters),
    volumes = stringResource(R.string.mal_shared_details_volumes),
    score = stringResource(R.string.mal_shared_details_score),
    rank = stringResource(R.string.mal_shared_details_rank),
    popularity = stringResource(R.string.mal_shared_details_popularity),
    progress = stringResource(R.string.mal_shared_details_progress),
    secondaryProgress = stringResource(R.string.mal_shared_details_secondary_progress),
    poster = stringResource(R.string.mal_shared_details_poster),
    openDetails = stringResource(R.string.mal_shared_details_open),
    stale = stringResource(R.string.mal_shared_details_stale),
    failureMessages = mapOf(
        ProviderDetailsFailure.INVALID_IDENTITY to
            stringResource(R.string.mal_shared_details_invalid_identity),
        ProviderDetailsFailure.AUTHENTICATION_REQUIRED to
            stringResource(R.string.mal_shared_error_authentication),
        ProviderDetailsFailure.RATE_LIMITED to
            stringResource(R.string.mal_shared_error_rate_limited),
        ProviderDetailsFailure.OFFLINE to stringResource(R.string.mal_shared_error_offline),
        ProviderDetailsFailure.TIMEOUT to stringResource(R.string.mal_shared_error_timeout),
        ProviderDetailsFailure.TEMPORARY to stringResource(R.string.mal_shared_error_temporary),
        ProviderDetailsFailure.INVALID_RESPONSE to
            stringResource(R.string.mal_shared_error_invalid_response),
        ProviderDetailsFailure.UNKNOWN to stringResource(R.string.mal_shared_error_unknown),
    ),
    genericFailure = stringResource(R.string.mal_shared_error_unknown),
)
