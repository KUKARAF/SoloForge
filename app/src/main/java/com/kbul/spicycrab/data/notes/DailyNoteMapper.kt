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

    /** `diary/YYYY-MM-DD` id used to address a day's note on the server. */
    fun notePath(date: LocalDate): String = "diary/$date"

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
        }
        fasts.filter { it.completed }.maxByOrNull { it.endEpoch ?: 0L }?.let { fast ->
            val end = fast.endEpoch ?: return@let
            fm["fasted_hours"] = num((end - fast.startEpoch) / 3_600_000.0)
        }
        weights.maxByOrNull { it.timestampEpoch }?.let { fm["weight_kg"] = num(it.weightKg) }
        workouts.filter { it.endEpoch != null }.takeIf { it.isNotEmpty() }?.let { done ->
            fm["workout_minutes"] = num(done.sumOf { it.totalSeconds } / 60.0)
        }

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
    fun parse(content: String, fallbackDate: LocalDate = LocalDate.EPOCH): DailyNote {
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
