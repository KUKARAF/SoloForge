package com.kbul.spicycrab.notifications

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
import com.kbul.spicycrab.data.notes.NotesSyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Recurring todo keys as defined in the notes server's `settings/recurring-todos`. */
@Singleton
class RecurringTodos @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notesSync: NotesSyncRepository,
) {
    fun markExerciseDone() = RecurringTodoWorker.enqueue(context, EXERCISE)
    fun markStretchesDone() = RecurringTodoWorker.enqueue(context, STRETCHES)
    suspend fun stretchesDoneRemotely(): Boolean = notesSync.isRecurringDone(STRETCHES) == true

    private companion object {
        const val EXERCISE = "excercise"
        const val STRETCHES = "stretches"
    }
}

/** Marks a notes-server recurring todo (e.g. `excercise`, `stretches`) done, retrying until online. */
@HiltWorker
class RecurringTodoWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notesSync: NotesSyncRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val key = inputData.getString(KEY_TODO) ?: return Result.failure()
        return if (notesSync.markRecurringDone(key).isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val KEY_TODO = "todo_key"

        fun enqueue(context: Context, key: String) {
            val request = OneTimeWorkRequestBuilder<RecurringTodoWorker>()
                .setInputData(workDataOf(KEY_TODO to key))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("recurring_done_$key", ExistingWorkPolicy.KEEP, request)
        }
    }
}
