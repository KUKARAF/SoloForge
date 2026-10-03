package com.kbul.spicycrab.notifications

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.kbul.spicycrab.MainActivity
import com.kbul.spicycrab.R
import com.kbul.spicycrab.domain.nutrition.FailedAnalysisStore
import com.kbul.spicycrab.domain.nutrition.FoodAnalysisOutcome
import com.kbul.spicycrab.domain.nutrition.FoodAnalysisSession
import com.kbul.spicycrab.domain.nutrition.FoodRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import kotlin.math.roundToInt

@HiltWorker
class FoodAnalysisWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: FoodRepository,
    private val session: FoodAnalysisSession,
    private val failedStore: FailedAnalysisStore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val imageFile = inputData.getString(KEY_IMAGE_PATH)?.let(::File)
        val comment = inputData.getString(KEY_COMMENT).orEmpty()
        val retryOf = inputData.getString(KEY_RETRY_OF)
        val result = if (imageFile != null) repository.analyze(imageFile, comment) else repository.analyzeText(comment)

        val estimate = result.getOrElse { error ->
            val failed = FoodAnalysisOutcome.Failed(id, error.message ?: "Analysis failed")
            if (!session.offerToScreen(failed)) {
                failedStore.record(imageFile, comment, failed.message, retryOf)
                session.publish(failed)
                postResult("Couldn't analyze your meal", "${failed.message} — retry it from the Food tab.")
            }
            return Result.success()
        }

        if (session.offerToScreen(FoodAnalysisOutcome.Ready(id, estimate))) return Result.success()

        val saved = repository.save(estimate, comment, imageFile)
        retryOf?.let { failedStore.remove(it) }
        session.publish(FoodAnalysisOutcome.AutoSaved(id, saved))
        postResult("Meal saved", "${saved.itemName} · ${saved.kcal.roundToInt()} kcal")
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(
            PROGRESS_NOTIF_ID,
            baseNotification("Analyzing meal…", "Your meal will be saved when analysis finishes.")
                .setOngoing(true)
                .build(),
        )

    private fun postResult(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val notif: Notification = baseNotification(title, text).setAutoCancel(true).build()
        NotificationManagerCompat.from(applicationContext).notify(RESULT_NOTIF_ID, notif)
    }

    private fun baseNotification(title: String, text: String): NotificationCompat.Builder {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(applicationContext, NotificationChannels.FOOD_ANALYSIS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
    }

    companion object {
        const val KEY_IMAGE_PATH = "image_path"
        const val KEY_COMMENT = "comment"
        const val KEY_RETRY_OF = "retry_of"
        private const val PROGRESS_NOTIF_ID = 4001
        private const val RESULT_NOTIF_ID = 4002
    }
}
