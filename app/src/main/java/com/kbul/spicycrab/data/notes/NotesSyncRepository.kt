package com.kbul.spicycrab.data.notes

import com.kbul.spicycrab.data.db.dao.FastSessionDao
import com.kbul.spicycrab.data.db.dao.FoodEntryDao
import com.kbul.spicycrab.data.db.dao.JournalEntryDao
import com.kbul.spicycrab.data.db.dao.WeightEntryDao
import com.kbul.spicycrab.data.db.dao.WorkoutSessionDao
import com.kbul.spicycrab.data.prefs.SecureKeyStore
import com.kbul.spicycrab.data.prefs.SettingsRepo
import com.kbul.spicycrab.network.NotesClient
import com.kbul.spicycrab.network.NotesConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Write-through sync of a day's records to the rust_note daily-notes server. Room remains the
 * source of truth and offline cache; this only mirrors a day into `diary/YYYY-MM-DD` after a
 * local write, and only when the user has enabled sync AND stored a bearer token.
 *
 * All entry points return [Result] and never throw, so a callers's local write is never
 * jeopardised by a network failure. When sync is disabled the calls are cheap no-ops.
 */
@Singleton
class NotesSyncRepository @Inject constructor(
    private val notesClient: NotesClient,
    private val settingsRepo: SettingsRepo,
    private val keyStore: SecureKeyStore,
    private val journalDao: JournalEntryDao,
    private val fastDao: FastSessionDao,
    private val foodDao: FoodEntryDao,
    private val weightDao: WeightEntryDao,
    private val workoutDao: WorkoutSessionDao,
) {

    /** True when the user has opted in and a bearer token is present. */
    suspend fun isActive(): Boolean {
        val enabled = settingsRepo.current().notesSyncEnabled
        return enabled && keyStore.hasNotesToken()
    }

    private suspend fun config(): NotesConfig? {
        val settings = settingsRepo.current()
        if (!settings.notesSyncEnabled) return null
        val token = keyStore.getNotesToken()?.takeIf { it.isNotBlank() } ?: return null
        val baseUrl = settings.notesBaseUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL
        return NotesConfig(baseUrl = baseUrl, token = token)
    }

    /**
     * Rebuilds the daily note for [date] from local rows and PUTs it (auto-creates). No-op success
     * when sync is off. Uses the current server version as `expected_version` to avoid clobbering
     * a newer server copy; a stale-version 409 surfaces as a failure the caller may ignore.
     */
    suspend fun syncDay(date: LocalDate): Result<Unit> = withContext(Dispatchers.IO) {
        val config = config() ?: return@withContext Result.success(Unit)
        runCatching {
            val note = buildNote(date)
            val path = DailyNoteMapper.notePath(date)
            val existingVersion = notesClient.getNote(config, path).getOrNull()?.meta?.version
            notesClient.putNote(config, path, note.render(), existingVersion).getOrThrow()
            Unit
        }
    }

    suspend fun deleteDay(date: LocalDate): Result<Unit> = withContext(Dispatchers.IO) {
        val config = config() ?: return@withContext Result.success(Unit)
        notesClient.deleteNote(config, DailyNoteMapper.notePath(date))
    }

    private suspend fun buildNote(date: LocalDate): DailyNote {
        val zone = ZoneId.systemDefault()
        val startOfDay = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        fun inDay(epoch: Long) = epoch in startOfDay until endOfDay

        val journalText = journalDao.observeAll().first()
            .firstOrNull { it.dateEpochDay == date.toEpochDay() }?.text ?: ""
        val foods = foodDao.observeAll().first().filter { inDay(it.timestampEpoch) }
        val fasts = fastDao.observeAll().first().filter { inDay(it.startEpoch) }
        val weights = weightDao.observeAll().first().filter { inDay(it.timestampEpoch) }
        val workouts = workoutDao.observeAll().first().filter { inDay(it.startEpoch) }

        return DailyNoteMapper.build(date, journalText, foods, fasts, weights, workouts)
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://notes.osmosis.page"
    }
}
