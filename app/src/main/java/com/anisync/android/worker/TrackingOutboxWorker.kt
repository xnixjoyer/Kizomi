package com.anisync.android.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anisync.android.data.tracking.TrackingDrainResult
import com.anisync.android.data.tracking.TrackingOutboxExecutor
import com.anisync.android.data.tracking.TrackingOutboxScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal enum class TrackingOutboxWorkerDecision {
    SUCCESS,
    RETRY,
}

/**
 * Pure worker boundary used by tests and by [TrackingOutboxWorker]. Cancellation remains structured
 * control flow; an unexpected storage/runtime failure is retried rather than acknowledged as success.
 */
internal suspend fun decideTrackingOutboxWork(
    drain: suspend () -> TrackingDrainResult,
): TrackingOutboxWorkerDecision = try {
    if (drain().hasUnsettledDeliveries) {
        TrackingOutboxWorkerDecision.RETRY
    } else {
        TrackingOutboxWorkerDecision.SUCCESS
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Throwable) {
    TrackingOutboxWorkerDecision.RETRY
}

@HiltWorker
class TrackingOutboxWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val executor: TrackingOutboxExecutor,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = when (
        decideTrackingOutboxWork { executor.drain(MAX_DELIVERIES_PER_RUN) }
    ) {
        TrackingOutboxWorkerDecision.SUCCESS -> Result.success()
        TrackingOutboxWorkerDecision.RETRY -> Result.retry()
    }

    companion object {
        private const val MAX_DELIVERIES_PER_RUN = 100
    }
}

@Singleton
class AndroidTrackingOutboxScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : TrackingOutboxScheduler {
    override fun enqueue() {
        val request = OneTimeWorkRequestBuilder<TrackingOutboxWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // Appending never cancels an in-flight provider write. It also closes the narrow race where
        // a new command arrives while the current worker is completing its final empty poll.
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "tracking-command-outbox"
    }
}
