package com.anisync.android.worker.mal

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.anisync.android.data.mal.account.MalAccountCredentialStore
import com.anisync.android.data.mal.calendar.MalCalendarRepository
import com.anisync.android.data.provider.ActiveProviderStore
import com.anisync.android.domain.calendar.provider.ProviderCalendarLoadResult
import com.anisync.android.domain.calendar.provider.ProviderCalendarQuery
import com.anisync.android.domain.calendar.provider.ProviderCalendarSession
import com.anisync.android.domain.provider.ActiveProvider
import com.anisync.android.domain.provider.ProviderRuntimeState
import com.anisync.android.widget.provider.FileProviderCalendarSnapshotStore
import com.anisync.android.widget.provider.ProviderCalendarSnapshot
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface MalCalendarRefreshOutcome {
    data object Success : MalCalendarRefreshOutcome
    data object SkippedInactiveProvider : MalCalendarRefreshOutcome
    data class Retry(val retryAfterMillis: Long? = null) : MalCalendarRefreshOutcome
    data object Failure : MalCalendarRefreshOutcome
}

@Singleton
class MalCalendarRefreshCoordinator internal constructor(
    private val runtimeState: () -> ProviderRuntimeState,
    private val activeAccountKey: suspend () -> String?,
    private val loadCalendar: suspend (
        ProviderCalendarSession,
        ProviderCalendarQuery,
        Boolean,
    ) -> ProviderCalendarLoadResult,
    private val writeSnapshot: suspend (ProviderCalendarSnapshot) -> Unit,
    private val nowEpochMillis: () -> Long,
    private val zoneId: ZoneId,
) {
    @Inject
    constructor(
        activeProviderStore: ActiveProviderStore,
        accounts: MalAccountCredentialStore,
        repository: MalCalendarRepository,
        snapshotStore: FileProviderCalendarSnapshotStore,
    ) : this(
        runtimeState = activeProviderStore::snapshot,
        activeAccountKey = { accounts.activeAccount()?.localAccountId },
        loadCalendar = repository::load,
        writeSnapshot = snapshotStore::write,
        nowEpochMillis = System::currentTimeMillis,
        zoneId = ZoneId.systemDefault(),
    )

    suspend fun refresh(): MalCalendarRefreshOutcome {
        val state = runtimeState()
        if (!state.providerTrafficAllowed || state.activeProvider != ActiveProvider.MAL_ONLY) {
            return MalCalendarRefreshOutcome.SkippedInactiveProvider
        }
        val accountKey = activeAccountKey() ?: return MalCalendarRefreshOutcome.Failure
        val nowMillis = nowEpochMillis()
        val startSeconds = nowMillis / 1_000L
        val query = ProviderCalendarQuery(
            startEpochSeconds = startSeconds,
            endEpochSeconds = startSeconds + REFRESH_WINDOW_SECONDS,
            zoneId = zoneId.id,
        )
        val result = loadCalendar(
            ProviderCalendarSession(
                runtimeProvider = ActiveProvider.MAL_ONLY,
                providerTrafficAllowed = true,
                accountKey = accountKey,
            ),
            query,
            true,
        )
        return when (result) {
            is ProviderCalendarLoadResult.Content -> {
                writeSnapshot(
                    ProviderCalendarSnapshot(
                        provider = ActiveProvider.MAL_ONLY,
                        generatedAtEpochMillis = result.fetchedAtEpochMillis,
                        entries = result.entries,
                    )
                )
                MalCalendarRefreshOutcome.Success
            }
            is ProviderCalendarLoadResult.Failure -> {
                if (result.retryable) {
                    MalCalendarRefreshOutcome.Retry(result.retryAfterMillis)
                } else {
                    MalCalendarRefreshOutcome.Failure
                }
            }
            is ProviderCalendarLoadResult.Unavailable -> MalCalendarRefreshOutcome.Failure
        }
    }

    companion object {
        private const val REFRESH_WINDOW_SECONDS = 7L * 24L * 60L * 60L
    }
}

enum class MalCalendarWorkerDecision {
    SUCCESS,
    RETRY,
    FAILURE,
}

internal suspend fun decideMalCalendarWork(
    refresh: suspend () -> MalCalendarRefreshOutcome,
): MalCalendarWorkerDecision = try {
    when (refresh()) {
        MalCalendarRefreshOutcome.Success,
        MalCalendarRefreshOutcome.SkippedInactiveProvider -> MalCalendarWorkerDecision.SUCCESS
        is MalCalendarRefreshOutcome.Retry -> MalCalendarWorkerDecision.RETRY
        MalCalendarRefreshOutcome.Failure -> MalCalendarWorkerDecision.FAILURE
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Throwable) {
    MalCalendarWorkerDecision.RETRY
}

@HiltWorker
class MalCalendarRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val coordinator: MalCalendarRefreshCoordinator,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = when (decideMalCalendarWork(coordinator::refresh)) {
        MalCalendarWorkerDecision.SUCCESS -> Result.success()
        MalCalendarWorkerDecision.RETRY -> Result.retry()
        MalCalendarWorkerDecision.FAILURE -> Result.failure()
    }

    companion object {
        const val PERIODIC_WORK_NAME = "mal_calendar_periodic_refresh_v1"
        const val IMMEDIATE_WORK_NAME = "mal_calendar_immediate_refresh_v1"

        fun periodicRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<MalCalendarRefreshWorker>(
                12L,
                TimeUnit.HOURS,
                2L,
                TimeUnit.HOURS,
            )
                .setConstraints(networkConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                .build()

        fun immediateRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<MalCalendarRefreshWorker>()
                .setConstraints(networkConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                .build()

        private fun networkConstraints(): Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
