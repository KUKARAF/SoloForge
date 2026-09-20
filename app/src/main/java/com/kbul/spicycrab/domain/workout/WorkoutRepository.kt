package com.kbul.spicycrab.domain.workout

import android.content.Context
import androidx.core.content.ContextCompat
import com.kbul.spicycrab.data.db.dao.WorkoutSessionDao
import com.kbul.spicycrab.data.db.entities.WorkoutSession
import com.kbul.spicycrab.data.notes.NotesSyncRepository
import com.kbul.spicycrab.notifications.WorkoutNotificationService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepository @Inject constructor(
    private val dao: WorkoutSessionDao,
    private val stateHolder: WorkoutStateHolder,
    private val notesSync: NotesSyncRepository,
    @ApplicationContext private val context: Context,
) {

    fun observeAll(): Flow<List<WorkoutSession>> = dao.observeAll()
    fun observeActive(): Flow<ActiveWorkoutState?> =
        combine(stateHolder.state, dao.observeActive()) { memory, dbActive ->
            memory ?: dbActive?.let { session ->
                ActiveWorkoutState(
                    sessionId = session.id,
                    mode = WorkoutMode.fromName(session.modeName),
                    startEpoch = session.startEpoch,
                    intervalSeconds = session.intervalSeconds,
                    phase = if (WorkoutMode.fromName(session.modeName) == WorkoutMode.EXERCISE_REST) {
                        WorkoutPhase.PAUSED
                    } else {
                        WorkoutPhase.EXERCISE
                    },
                    phaseStartEpoch = if (WorkoutMode.fromName(session.modeName) == WorkoutMode.EXERCISE_REST) {
                        System.currentTimeMillis()
                    } else {
                        session.startEpoch
                    },
                    accumulatedExerciseSeconds = session.exerciseSeconds,
                    accumulatedRestSeconds = session.restSeconds,
                ).also { stateHolder.set(it) }
            }
        }

    suspend fun start(mode: WorkoutMode, intervalSeconds: Int): WorkoutSession {
        val now = System.currentTimeMillis()
        val draft = WorkoutSession(
            modeName = mode.name,
            startEpoch = now,
            endEpoch = null,
            totalSeconds = 0L,
            intervalSeconds = if (mode == WorkoutMode.INTERVAL) intervalSeconds else 0,
            exerciseSeconds = 0L,
            restSeconds = 0L,
            notes = "",
            lastModifiedEpoch = now,
        )
        val id = dao.insert(draft)
        val saved = draft.copy(id = id)
        ContextCompat.startForegroundService(
            context,
            WorkoutNotificationService.startIntent(context, id, mode, intervalSeconds),
        )
        notesSync.scheduleSyncForEpoch(now)
        return saved
    }

    /**
     * Records that the user stretched today. Idempotent: a second tap on the same day keeps the
     * day marked without adding a duplicate. Stored as a completed [WorkoutSession] with
     * [WorkoutSession.isStretch] = true so it never appears as an active or "real" workout.
     */
    suspend fun logStretch(): Boolean {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        if (dao.countStretchesBetween(start, end) > 0) return false
        val now = System.currentTimeMillis()
        dao.insert(
            WorkoutSession(
                modeName = WorkoutMode.SIMPLE.name,
                startEpoch = now,
                endEpoch = now,
                totalSeconds = 0L,
                intervalSeconds = 0,
                exerciseSeconds = 0L,
                restSeconds = 0L,
                notes = "Stretched",
                lastModifiedEpoch = now,
                isStretch = true,
            )
        )
        notesSync.scheduleSyncForEpoch(now)
        return true
    }

    fun togglePhase() {
        context.startService(WorkoutNotificationService.togglePhaseIntent(context))
    }

    fun togglePause() {
        context.startService(WorkoutNotificationService.togglePauseIntent(context))
    }

    fun pause() {
        context.startService(WorkoutNotificationService.pauseIntent(context))
    }

    suspend fun stop() {
        context.startService(WorkoutNotificationService.stopIntent(context))
    }

    suspend fun update(updated: WorkoutSession) {
        dao.update(updated.copy(lastModifiedEpoch = System.currentTimeMillis()))
        notesSync.scheduleSyncForEpoch(updated.startEpoch)
    }

    suspend fun delete(session: WorkoutSession) {
        dao.delete(session)
        notesSync.scheduleSyncForEpoch(session.startEpoch)
    }
}
