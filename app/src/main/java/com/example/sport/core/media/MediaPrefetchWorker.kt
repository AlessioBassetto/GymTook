package com.example.sport.core.media

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
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scarica in background i media di una scheda, così sono disponibili anche nei seminterrati
 * senza campo. Gestito da WorkManager: sopravvive a chiusura app e riavvii, rispetta i vincoli
 * di rete e riprova con backoff esponenziale.
 */
@HiltWorker
class MediaPrefetchWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mediaRepository: MediaRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val routineId = inputData.getLong(KEY_ROUTINE_ID, -1L)
        if (routineId < 0) return Result.failure()
        val result = mediaRepository.prefetchRoutine(routineId)
        val output = workDataOf(
            KEY_TOTAL to result.total,
            KEY_DOWNLOADED to result.downloaded,
            KEY_FAILED to result.failed.size,
        )
        return when {
            result.isComplete -> Result.success(output)
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.success(output)
        }
    }

    companion object {
        const val KEY_ROUTINE_ID = "routine_id"
        const val KEY_TOTAL = "total"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_FAILED = "failed"
        private const val MAX_ATTEMPTS = 3

        fun uniqueWorkName(routineId: Long) = "media-prefetch-$routineId"
    }
}

@Singleton
class MediaPrefetchScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /**
     * Accoda il download offline di una scheda (idempotente: una sola richiesta per scheda).
     * @param wifiOnly true per non consumare dati mobili (impostazione utente).
     */
    fun prefetchRoutine(routineId: Long, wifiOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<MediaPrefetchWorker>()
            .setInputData(workDataOf(MediaPrefetchWorker.KEY_ROUTINE_ID to routineId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            MediaPrefetchWorker.uniqueWorkName(routineId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
