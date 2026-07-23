package com.anisync.android.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anisync.android.data.mal.api.MalImportResult
import com.anisync.android.data.mal.api.MalLibraryRepository
import com.anisync.android.domain.tracking.TrackingMediaType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@HiltWorker
class MalImportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: MalLibraryRepository,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val accountId = inputData.getString(KEY_ACCOUNT_ID)?.takeIf(String::isNotBlank)
            ?: return Result.failure()
        val mediaType = inputData.getString(KEY_MEDIA_TYPE)?.let { raw ->
            runCatching { TrackingMediaType.valueOf(raw) }.getOrNull()
        } ?: return Result.failure()
        return when (val result = repository.refresh(accountId, mediaType)) {
            is MalImportResult.Success -> Result.success(
                Data.Builder()
                    .putInt(KEY_IMPORTED_COUNT, result.importedEntries)
                    .putInt(KEY_REMOVED_COUNT, result.removedEntries)
                    .build()
            )
            is MalImportResult.Failure -> if (result.error.retryable) {
                Result.retry()
            } else {
                Result.failure(
                    Data.Builder().putString(KEY_ERROR_KIND, result.error.kind.name).build()
                )
            }
        }
    }

    companion object {
        private const val KEY_ACCOUNT_ID = "mal_local_account_id"
        private const val KEY_MEDIA_TYPE = "tracking_media_type"
        private const val KEY_IMPORTED_COUNT = "imported_count"
        private const val KEY_REMOVED_COUNT = "removed_count"
        private const val KEY_ERROR_KIND = "error_kind"

        fun enqueue(
            context: Context,
            localAccountId: String,
            mediaType: TrackingMediaType,
        ) {
            require(localAccountId.isNotBlank())
            val request = OneTimeWorkRequestBuilder<MalImportWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(KEY_ACCOUNT_ID, localAccountId)
                        .putString(KEY_MEDIA_TYPE, mediaType.name)
                        .build()
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                uniqueName(localAccountId, mediaType),
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancelAccount(context: Context, localAccountId: String) {
            TrackingMediaType.entries.forEach { mediaType ->
                WorkManager.getInstance(context.applicationContext)
                    .cancelUniqueWork(uniqueName(localAccountId, mediaType))
            }
        }

        private fun uniqueName(localAccountId: String, mediaType: TrackingMediaType): String =
            "mal-import:${accountHash(localAccountId)}:${mediaType.name}"

        private fun accountHash(localAccountId: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(localAccountId.toByteArray(Charsets.UTF_8))
                .take(12)
                .joinToString("") { byte -> "%02x".format(byte) }
    }
}
