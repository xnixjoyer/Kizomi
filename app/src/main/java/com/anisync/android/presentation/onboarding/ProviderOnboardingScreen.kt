package com.anisync.android.presentation.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisync.android.R
import com.anisync.android.domain.provider.ProviderTransitionPhase

@Composable
fun ProviderOnboardingScreen(
    modifier: Modifier = Modifier,
    viewModel: ProviderOnboardingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val providerState by viewModel.providerState.collectAsStateWithLifecycle()
    val consent by viewModel.consentRecord.collectAsStateWithLifecycle()
    var showMalConsent by remember { mutableStateOf(false) }
    var consentChecked by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ProviderOnboardingEffect.OpenBrowser -> context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(effect.url))
                )
                is ProviderOnboardingEffect.Error -> errorMessage = effect.reason
            }
        }
    }

    val legacy = providerState.transitionPhase == ProviderTransitionPhase.LEGACY_SELECTION_REQUIRED
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (legacy) {
                    "Choose which existing account Kizomi should keep"
                } else {
                    "Choose one provider for this installation"
                },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (legacy) {
                    "The other provider's credentials, queues, mappings, and account data will be deleted. No data is copied."
                } else {
                    "Kizomi uses only the selected provider. You can change later after disconnecting and deleting the current local account data."
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = viewModel::signInWithAniList,
                modifier = Modifier.fillMaxWidth(),
                enabled = providerState.transitionPhase != ProviderTransitionPhase.PURGING,
            ) { Text(if (legacy) "Keep AniList" else "Sign in with AniList") }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    if (legacy) viewModel.signInWithMal(consentChecked = false)
                    else showMalConsent = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = providerState.transitionPhase != ProviderTransitionPhase.PURGING,
            ) { Text(if (legacy) "Keep MyAnimeList" else "Sign in with MyAnimeList") }
            if (consent != null) {
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = viewModel::revokeMalConsent) {
                    Text("Revoke stored MyAnimeList consent")
                }
            }
        }
    }

    if (showMalConsent) {
        MalConsentDialog(
            checked = consentChecked,
            onCheckedChange = { consentChecked = it },
            onDismiss = {
                showMalConsent = false
                consentChecked = false
            },
            onContinue = {
                showMalConsent = false
                viewModel.signInWithMal(consentChecked)
            },
        )
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Sign-in unavailable") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) { Text("OK") }
            },
        )
    }
}

@Composable
private fun MalConsentDialog(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Before connecting MyAnimeList") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Kizomi will open MyAnimeList in your browser. Kizomi never asks for your password and stores the issued credentials locally on this device.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked, onCheckedChange = onCheckedChange)
                    Spacer(Modifier.width(8.dp))
                    Text("I have read and agree to the linked policies and MyAnimeList terms.")
                }
                TextButton(onClick = { open(PRIVACY_URL) }) { Text("Privacy Policy") }
                TextButton(onClick = { open(TERMS_URL) }) { Text("Terms of Use") }
                TextButton(onClick = { open(DELETION_URL) }) { Text("Data Deletion") }
                TextButton(onClick = { open(MAL_TERMS_URL) }) { Text("MyAnimeList API terms") }
            }
        },
        confirmButton = {
            Button(onClick = onContinue, enabled = checked) { Text("Continue to MyAnimeList") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private const val PRIVACY_URL = "https://github.com/xnixjoyer/Kizomi/blob/main/PRIVACY.md"
private const val TERMS_URL = "https://github.com/xnixjoyer/Kizomi/blob/main/TERMS_OF_USE.md"
private const val DELETION_URL = "https://github.com/xnixjoyer/Kizomi/blob/main/DATA_DELETION.md"
private const val MAL_TERMS_URL = "https://myanimelist.net/static/apiagreement.html"
