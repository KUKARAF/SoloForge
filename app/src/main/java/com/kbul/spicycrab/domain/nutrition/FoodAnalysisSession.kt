package com.kbul.spicycrab.domain.nutrition

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kbul.spicycrab.data.db.entities.FoodEntry
import com.kbul.spicycrab.notifications.FoodAnalysisWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface FoodAnalysisOutcome {
    val workId: UUID

    data class Ready(override val workId: UUID, val estimate: NutritionEstimate) : FoodAnalysisOutcome
    data class Failed(override val workId: UUID, val message: String) : FoodAnalysisOutcome
    data class AutoSaved(override val workId: UUID, val entry: FoodEntry) : FoodAnalysisOutcome
}

/**
 * Runs food analysis in WorkManager so it survives the user leaving the app. When the result
 * arrives, it is handed to the Analyze screen for review if that screen is visible; otherwise it
 * is saved without confirmation.
 */
@Singleton
class FoodAnalysisSession @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val lock = Any()
    private var screenVisible = false
    private var awaitedWorkId: UUID? = null

    private val _outcomes = MutableSharedFlow<FoodAnalysisOutcome>(extraBufferCapacity = 16)
    val outcomes: SharedFlow<FoodAnalysisOutcome> = _outcomes.asSharedFlow()

    fun submit(imageFile: File?, comment: String, retryOf: String?): UUID {
        val request = OneTimeWorkRequestBuilder<FoodAnalysisWorker>()
            .setInputData(
                workDataOf(
                    FoodAnalysisWorker.KEY_IMAGE_PATH to imageFile?.absolutePath,
                    FoodAnalysisWorker.KEY_COMMENT to comment,
                    FoodAnalysisWorker.KEY_RETRY_OF to retryOf,
                )
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        synchronized(lock) { awaitedWorkId = request.id }
        WorkManager.getInstance(context).enqueue(request)
        return request.id
    }

    fun cancel(workId: UUID) {
        synchronized(lock) { if (awaitedWorkId == workId) awaitedWorkId = null }
        WorkManager.getInstance(context).cancelWorkById(workId)
    }

    fun setScreenVisible(visible: Boolean) {
        synchronized(lock) { screenVisible = visible }
    }

    /** Returns true if the visible Analyze screen took the result; false means the caller must auto-save. */
    fun offerToScreen(outcome: FoodAnalysisOutcome): Boolean = synchronized(lock) {
        val taken = screenVisible && awaitedWorkId == outcome.workId
        if (taken) {
            awaitedWorkId = null
            _outcomes.tryEmit(outcome)
        }
        taken
    }

    fun publish(outcome: FoodAnalysisOutcome) {
        _outcomes.tryEmit(outcome)
    }
}
