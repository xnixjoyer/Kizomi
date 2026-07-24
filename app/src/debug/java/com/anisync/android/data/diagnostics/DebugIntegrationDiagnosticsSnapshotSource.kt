package com.anisync.android.data.diagnostics

import androidx.core.net.toUri
import com.anisync.android.BuildConfig
import com.anisync.android.data.account.AccountStore
import com.anisync.android.data.local.AppDatabase
import com.anisync.android.data.mal.account.MalAccount
import com.anisync.android.data.mal.account.MalAccountRepository
import com.anisync.android.data.mal.account.MalTokenStatus
import com.anisync.android.data.provider.ActiveProviderStore
import com.anisync.android.domain.provider.ActiveProvider
import com.anisync.android.presentation.diagnostics.DiagnosticsParityRegistry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class DebugIntegrationDiagnosticsSnapshotSource @Inject constructor(
    private val providerStore: ActiveProviderStore,
    private val aniListAccounts: AccountStore,
    private val malAccounts: MalAccountRepository,
    private val database: AppDatabase,
    private val recorder: IntegrationDiagnosticsRecorder,
) : IntegrationDiagnosticsSnapshotSource {
    override suspend fun snapshot(): IntegrationDiagnosticsSnapshot = withContext(Dispatchers.IO) {
        val providerState = providerStore.snapshot()
        val malAccount = if (providerState.activeProvider == ActiveProvider.MAL_ONLY) {
            runCatching { malAccounts.activeAccount() }.getOrNull()
        } else {
            null
        }
        val aniListAccount = if (providerState.activeProvider == ActiveProvider.ANILIST_ONLY) {
            aniListAccounts.activeAccount.value
        } else {
            null
        }
        val runtime = recorder.runtimeSnapshot()
        val redirect = BuildConfig.MAL_OAUTH_REDIRECT_URI.toUri()
        val clientIdPresent = BuildConfig.MAL_OAUTH_CLIENT_ID.isNotBlank()
        val accountPresent = malAccount != null || aniListAccount != null
        val lastRefresh = recorder.lastRefreshOutcome()

        IntegrationDiagnosticsSnapshot(
            build = DiagnosticsBuildMetadata(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toLong(),
                buildType = BuildConfig.BUILD_TYPE,
                sourceRevision = null,
                oauthEnvironment = BuildConfig.MAL_OAUTH_ENVIRONMENT,
                redirectScheme = redirect.scheme.orEmpty().ifBlank { "unknown" },
                redirectHost = redirect.host.orEmpty().ifBlank { "unknown" },
                redirectPath = redirect.path.orEmpty().ifBlank { "unknown" },
                clientIdPresent = clientIdPresent,
                databaseSchemaVersion = runCatching {
                    database.openHelper.readableDatabase.version
                }.getOrNull(),
            ),
            session = DiagnosticsSessionMetadata(
                activeProvider = providerState.activeProvider,
                transitionPhase = providerState.transitionPhase,
                configuration = configurationFor(providerState.activeProvider, clientIdPresent),
                sessionState = sessionStateFor(
                    activeProvider = providerState.activeProvider,
                    malAccount = malAccount,
                    aniListAccountPresent = aniListAccount != null,
                    aniListAccountExpired = aniListAccount?.isExpired == true,
                ),
                pendingOAuthTransaction = DiagnosticAvailability.UNKNOWN,
                tokenVaultHealth = vaultHealthFor(
                    activeProvider = providerState.activeProvider,
                    malAccount = malAccount,
                    aniListAccountPresent = aniListAccount != null,
                ),
                accountRecordPresent = accountPresent,
                lastSuccessfulRestoreEpochMillis = recorder.lastSuccessfulRestoreEpochMillis(),
                lastRefreshOutcome = lastRefresh?.first,
                lastRefreshEpochMillis = lastRefresh?.second,
            ),
            runtime = runtime,
            parity = DiagnosticsParityRegistry.defaultItems(),
            checklist = DiagnosticsParityRegistry.checklist(
                configurationPresent = clientIdPresent,
                redirectConfigured = redirect.scheme != null && redirect.host != null,
                accountRestored = accountPresent,
                blockedInactiveRequestCount = runtime.blockedInactiveProviderRequestCount,
            ),
            capturedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    private fun configurationFor(
        provider: ActiveProvider,
        malClientIdPresent: Boolean,
    ): DiagnosticAvailability = when (provider) {
        ActiveProvider.MAL_ONLY -> if (malClientIdPresent) {
            DiagnosticAvailability.AVAILABLE
        } else {
            DiagnosticAvailability.UNAVAILABLE
        }
        ActiveProvider.ANILIST_ONLY -> DiagnosticAvailability.AVAILABLE
        ActiveProvider.UNCONFIGURED -> DiagnosticAvailability.UNKNOWN
    }

    private fun sessionStateFor(
        activeProvider: ActiveProvider,
        malAccount: MalAccount?,
        aniListAccountPresent: Boolean,
        aniListAccountExpired: Boolean,
    ): DiagnosticSessionState = when (activeProvider) {
        ActiveProvider.UNCONFIGURED -> DiagnosticSessionState.NOT_CONFIGURED
        ActiveProvider.ANILIST_ONLY -> when {
            !aniListAccountPresent -> DiagnosticSessionState.MISSING
            aniListAccountExpired -> DiagnosticSessionState.EXPIRED
            else -> DiagnosticSessionState.CONNECTED
        }
        ActiveProvider.MAL_ONLY -> when (malAccount?.tokenStatus) {
            null -> DiagnosticSessionState.MISSING
            MalTokenStatus.ACTIVE -> {
                val expiry = malAccount.tokenExpiresAtEpochMillis
                if (expiry != null && expiry <= System.currentTimeMillis()) {
                    DiagnosticSessionState.EXPIRED
                } else if (expiry != null) {
                    DiagnosticSessionState.REFRESHABLE_EXPIRY
                } else {
                    DiagnosticSessionState.CONNECTED
                }
            }
            MalTokenStatus.EXPIRED -> DiagnosticSessionState.EXPIRED
            MalTokenStatus.MISSING -> DiagnosticSessionState.MISSING
            MalTokenStatus.CORRUPT -> DiagnosticSessionState.CORRUPT
            MalTokenStatus.KEYSTORE_RESET -> DiagnosticSessionState.KEYSTORE_RESET
        }
    }

    private fun vaultHealthFor(
        activeProvider: ActiveProvider,
        malAccount: MalAccount?,
        aniListAccountPresent: Boolean,
    ): DiagnosticAvailability = when (activeProvider) {
        ActiveProvider.UNCONFIGURED -> DiagnosticAvailability.ABSENT
        ActiveProvider.ANILIST_ONLY -> if (aniListAccountPresent) {
            DiagnosticAvailability.AVAILABLE
        } else {
            DiagnosticAvailability.ABSENT
        }
        ActiveProvider.MAL_ONLY -> when (malAccount?.tokenStatus) {
            MalTokenStatus.ACTIVE, MalTokenStatus.EXPIRED -> DiagnosticAvailability.AVAILABLE
            MalTokenStatus.MISSING -> DiagnosticAvailability.ABSENT
            MalTokenStatus.CORRUPT, MalTokenStatus.KEYSTORE_RESET ->
                DiagnosticAvailability.UNAVAILABLE
            null -> DiagnosticAvailability.ABSENT
        }
    }
}
