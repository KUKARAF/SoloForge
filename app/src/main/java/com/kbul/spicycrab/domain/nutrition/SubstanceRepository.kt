package com.kbul.spicycrab.domain.nutrition

import com.kbul.spicycrab.data.db.dao.SubstanceEntryDao
import com.kbul.spicycrab.data.db.entities.SubstanceEntry
import com.kbul.spicycrab.data.prefs.SecureKeyStore
import com.kbul.spicycrab.data.prefs.SettingsRepo
import com.kbul.spicycrab.network.NotesClient
import com.kbul.spicycrab.network.NotesConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/** Registry definition for a stats metric; custom substance keys fall back to a generic bar/sum. */
private data class RegistrySpec(val unit: String, val label: String, val chart: String, val agg: String)

/**
 * Local-first store of timed substance samples. Rows are the source of truth in Room; each is
 * mirrored once to the append-only `/api/stats` endpoint (which the server renders as
 * `key: value@HHMM` inline lists). A synced sample is never re-POSTed. Edits and deletes are
 * intentionally local-only — the stats stream is append-only so they do not propagate.
 */
@Singleton
class SubstanceRepository @Inject constructor(
    private val dao: SubstanceEntryDao,
    private val notesClient: NotesClient,
    private val settings: SettingsRepo,
    private val keyStore: SecureKeyStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushMutex = Mutex()
    private val registeredThisSession = Collections.synchronizedSet(mutableSetOf<String>())

    fun observeAll(): Flow<List<SubstanceEntry>> = dao.observeAll()

    /** Substance metrics offered as quick-add chips (booleans below are not user-logged). */
    val builtInKeys: List<String> = listOf("alcohol", "caffeine", "nicotine", "sugar")

    suspend fun add(key: String, amountInt: Int, timestampEpoch: Long): SubstanceEntry {
        val now = System.currentTimeMillis()
        val entry = SubstanceEntry(
            key = key.trim().lowercase(),
            amountInt = amountInt,
            timestampEpoch = timestampEpoch,
            lastModifiedEpoch = now,
            synced = false,
        )
        val saved = entry.copy(id = dao.insert(entry))
        flushUnsynced()
        return saved
    }

    /** Local-only (append-only server: not propagated). */
    suspend fun update(entry: SubstanceEntry) =
        dao.update(entry.copy(lastModifiedEpoch = System.currentTimeMillis()))

    /** Local-only (append-only server: not propagated). */
    suspend fun delete(entry: SubstanceEntry) = dao.delete(entry)

    /** Posts any not-yet-synced samples to `/api/stats`; safe to call on startup and after logging. */
    fun flushUnsynced() {
        scope.launch { flushInternal() }
    }

    private suspend fun flushInternal() = flushMutex.withLock {
        val config = config() ?: return
        // Register the boolean daily metrics so the stats board can read them, even on a day with
        // no substance samples to post. Idempotent and once-per-session.
        ensureRegistered(config, "vegan")
        ensureRegistered(config, "vegetarian")
        val pending = dao.unsynced()
        if (pending.isEmpty()) return
        pending.map { it.key }.distinct().forEach { ensureRegistered(config, it) }
        val zone = ZoneId.systemDefault()
        for (entry in pending) {
            val at = Instant.ofEpochMilli(entry.timestampEpoch).atZone(zone)
            val hhmm = "%02d%02d".format(at.hour, at.minute)
            val date = at.toLocalDate().toString()
            val result = notesClient.postStat(config, entry.key, entry.amountInt, hhmm, date)
            if (result.isSuccess) dao.markSynced(entry.id)
        }
    }

    private suspend fun ensureRegistered(config: NotesConfig, key: String) {
        if (!registeredThisSession.add(key)) return
        val spec = REGISTRY[key] ?: RegistrySpec(unit = "", label = key, chart = "bar", agg = "sum")
        // Idempotent on the server; failure is non-fatal (only affects GET series, not posting).
        notesClient.putStatRegistry(config, key, spec.unit, spec.label, spec.chart, spec.agg)
    }

    private suspend fun config(): NotesConfig? {
        val s = settings.current()
        if (!s.notesSyncEnabled) return null
        val token = keyStore.getNotesToken()?.takeIf { it.isNotBlank() } ?: return null
        val baseUrl = s.notesBaseUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL
        return NotesConfig(baseUrl = baseUrl, token = token)
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://notes.osmosis.page"
        // Shared conventions — must match the journal app and the rust_note stats view exactly.
        val REGISTRY: Map<String, RegistrySpec> = linkedMapOf(
            "alcohol" to RegistrySpec("g", "Alcohol", "bar", "sum"),
            "caffeine" to RegistrySpec("mg", "Caffeine", "line", "sum"),
            "nicotine" to RegistrySpec("mg", "Nicotine", "bar", "sum"),
            "sugar" to RegistrySpec("g", "Sugar", "bar", "sum"),
            "vegan" to RegistrySpec("", "Vegan", "boolean", "last"),
            "vegetarian" to RegistrySpec("", "Vegetarian", "boolean", "last"),
        )
    }
}
