package com.kbul.spicycrab.data.notes

import com.kbul.spicycrab.data.db.entities.FastSession
import com.kbul.spicycrab.data.db.entities.FoodEntry
import com.kbul.spicycrab.data.db.entities.WeightEntry
import com.kbul.spicycrab.data.db.entities.WorkoutSession
import java.time.LocalDate

/**
 * One day's records rendered as a single markdown daily note: a leading YAML frontmatter block
 * (`---` fenced) holding aggregate metrics, then the journal text as the body.
 *
 * The frontmatter is an ordered map of scalar `key: value` lines so it round-trips losslessly
 * through [render] and [parse]. Room stays the source of truth; this is only the wire shape for
 * the notes server, so parsing recovers the frontmatter map and body but not the individual
 * Room rows (those live locally).
 */
data class DailyNote(
    val date: LocalDate,
    val frontmatter: LinkedHashMap<String, String>,
    val body: String,
) {
    fun render(): String {
        val sb = StringBuilder()
        sb.append("---\n")
        for ((k, v) in frontmatter) {
            sb.append(k).append(": ").append(v).append('\n')
        }
        sb.append("---\n")
        sb.append(body)
        return sb.toString()
    }
}

object DailyNoteMapper {

    /**
     * Frontmatter keys SoloForge owns and overwrites on every whole-note PUT. Any key NOT in this
     * set (e.g. substance metrics appended by the server's /api/stats, or keys another app wrote)
     * is preserved by [renderMerged].
     */
    val MANAGED_KEYS: Set<String> = setOf(
        "date", "kcal", "protein", "carbs", "fat", "fiber", "food_entries",
        "vegan", "vegetarian", "salt_g", "fasted_hours", "weight_kg",
        "workout_minutes", "workout", "stretches", "last_modified",
    )

    /** `diary/YYYY-MM-DD` id used to address a day's note on the server. */
    fun notePath(date: LocalDate): String = "diary/$date"

    /**
     * Renders [managed] for a whole-note PUT while preserving foreign frontmatter. Only
     * [MANAGED_KEYS] are overwritten; every other key from [existingContent] (substance lines the
     * server appended via /api/stats, or another app's keys) survives untouched. The body is
     * SoloForge-owned (the journal), so it is taken from [managed].
     */
    fun renderMerged(managed: DailyNote, existingContent: String?): String {
        val existing = existingContent?.let { parse(it).frontmatter } ?: LinkedHashMap()
        val merged = LinkedHashMap<String, String>()
        for ((k, v) in existing) if (k !in MANAGED_KEYS) merged[k] = v
        for ((k, v) in managed.frontmatter) merged[k] = v
        return managed.copy(frontmatter = merged).render()
    }

    /**
     * Builds the daily note from a day's Room rows. Metrics are summed across the day; where an
     * entity carries [lastModifiedEpoch] the newest is surfaced as `last_modified`.
     */
    fun build(
        date: LocalDate,
        journalText: String,
        foods: List<FoodEntry>,
        fasts: List<FastSession>,
        weights: List<WeightEntry>,
        workouts: List<WorkoutSession>,
    ): DailyNote {
        val fm = LinkedHashMap<String, String>()
        fm["date"] = date.toString()

        if (foods.isNotEmpty()) {
            fm["kcal"] = num(foods.sumOf { it.kcal / it.peopleCount })
            fm["protein"] = num(foods.sumOf { it.proteinG / it.peopleCount })
            fm["carbs"] = num(foods.sumOf { it.carbsG / it.peopleCount })
            fm["fat"] = num(foods.sumOf { it.fatG / it.peopleCount })
            fm["fiber"] = num(foods.sumOf { it.fiberG / it.peopleCount })
            fm["food_entries"] = foods.size.toString()
            // Whole-day diet flags: true only if every entry qualifies (one non-veg item flips it).
            fm["vegan"] = bool(foods.all { it.isVegan })
            fm["vegetarian"] = bool(foods.all { it.isVegetarian })
        }
        // salt_g from summed sodium (sodium→salt factor 2.5), only when any entry carries sodium.
        if (foods.any { it.sodiumMg > 0.0 }) {
            fm["salt_g"] = oneDecimal(foods.sumOf { it.sodiumMg } * 2.5 / 1000.0)
        }
        fasts.filter { it.completed }.maxByOrNull { it.endEpoch ?: 0L }?.let { fast ->
            val end = fast.endEpoch ?: return@let
            fm["fasted_hours"] = num((end - fast.startEpoch) / 3_600_000.0)
        }
        weights.maxByOrNull { it.timestampEpoch }?.let { fm["weight_kg"] = num(it.weightKg) }
        workouts.filter { it.endEpoch != null && !it.isStretch }.takeIf { it.isNotEmpty() }?.let { done ->
            fm["workout_minutes"] = num(done.sumOf { it.totalSeconds } / 60.0)
        }
        // Per-day booleans, always emitted: a real (non-stretch) session vs. a stretch marker.
        fm["workout"] = bool(workouts.any { !it.isStretch })
        fm["stretches"] = bool(workouts.any { it.isStretch })

        val lastModified = buildList {
            addAll(foods.map { it.lastModifiedEpoch })
            addAll(weights.map { it.lastModifiedEpoch })
            addAll(workouts.map { it.lastModifiedEpoch })
        }.maxOrNull()
        if (lastModified != null) fm["last_modified"] = lastModified.toString()

        return DailyNote(date, fm, journalText)
    }

    /**
     * Parses a full markdown note back into frontmatter map + body. Only the first `---`-fenced
     * block at the very start is treated as frontmatter; a `---` anywhere in the body is left
     * untouched. The `date` frontmatter key seeds [DailyNote.date] when present.
     */
    fun parse(content: String, fallbackDate: LocalDate = LocalDate.ofEpochDay(0)): DailyNote {
        val normalized = content.replace("\r\n", "\n")
        val fm = LinkedHashMap<String, String>()
        if (!normalized.startsWith("---\n")) {
            return DailyNote(fallbackDate, fm, normalized)
        }
        val afterOpen = normalized.substring(4)
        val closeIdx = afterOpen.indexOf("\n---")
        if (closeIdx < 0) {
            return DailyNote(fallbackDate, fm, normalized)
        }
        val block = afterOpen.substring(0, closeIdx)
        for (line in block.split('\n')) {
            if (line.isBlank()) continue
            val sep = line.indexOf(':')
            if (sep < 0) continue
            fm[line.substring(0, sep).trim()] = line.substring(sep + 1).trim()
        }
        // Body starts after the closing "---" line and its trailing newline (if any).
        var bodyStart = closeIdx + 4 // skip "\n---"
        if (bodyStart < afterOpen.length && afterOpen[bodyStart] == '\n') bodyStart += 1
        val body = if (bodyStart <= afterOpen.length) afterOpen.substring(bodyStart) else ""
        val date = fm["date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: fallbackDate
        return DailyNote(date, fm, body)
    }

    private fun bool(value: Boolean): String = if (value) "true" else "false"

    /** Rounds to one decimal place, e.g. `6.25` → `6.3`, `6.0` → `6`. */
    private fun oneDecimal(value: Double): String = num(Math.round(value * 10.0) / 10.0)

    /** Compact number: drops the trailing `.0` for whole values so `60.0` renders as `60`. */
    private fun num(value: Double): String {
        val rounded = Math.round(value * 100.0) / 100.0
        return if (rounded == Math.floor(rounded) && !rounded.isInfinite()) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
    }
}
