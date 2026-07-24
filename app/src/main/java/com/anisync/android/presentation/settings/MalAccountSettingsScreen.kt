package com.anisync.android.presentation.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisync.android.R
import com.anisync.android.presentation.settings.provider.ProviderAccountAction
import com.anisync.android.presentation.settings.provider.ProviderAccountSettingsContent

@Composable
fun MalAccountSettingsScreen(
    onBackClick: () -> Unit,
    viewModel: MalAccountSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingAction by remember { mutableStateOf<ProviderAccountAction?>(null) }

    SettingsScreenScaffold(
        title = stringResource(R.string.provider_account_screen_title),
        onBackClick = onBackClick,
    ) {
        ProviderAccountSettingsContent(
            state = state,
            onAction = { action ->
                if (action == ProviderAccountAction.REVOKE_MAL_CONSENT) {
                    viewModel.onAction(action)
                } else {
                    pendingAction = action
                }
            },
        )
    }

    pendingAction?.let { action ->
        val changingProvider = action == ProviderAccountAction.CHANGE_PROVIDER
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = {
                Text(
                    stringResource(
                        if (changingProvider) {
                            R.string.provider_account_change_dialog_title
                        } else {
                            R.string.provider_account_delete_dialog_title
                        },
                    ),
                )
            },
            text = { Text(stringResource(R.string.provider_account_delete_dialog_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        pendingAction = null
                        viewModel.onAction(action)
                    },
                ) {
                    Text(stringResource(R.string.provider_account_delete_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text(stringResource(R.string.provider_account_dialog_cancel))
                }
            },
        )
    }
}
