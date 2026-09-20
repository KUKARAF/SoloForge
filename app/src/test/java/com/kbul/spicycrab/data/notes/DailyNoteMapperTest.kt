package com.kbul.spicycrab.data.notes

import com.kbul.spicycrab.data.db.entities.FastSession
import com.kbul.spicycrab.data.db.entities.FoodEntry
import com.kbul.spicycrab.data.db.entities.WeightEntry
import com.kbul.spicycrab.data.db.entities.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyNoteMapperTest {

    private val date = LocalDate.of(2026, 9, 20)

    private fun food(kcal: Double, protein: Double, people: Int = 1) = FoodEntry(
        timestampEpoch = 0L,
        lastModifiedEpoch = 100L,
        itemName = "meal",
        grams = 100.0,
        kcal = kcal,
        proteinG = protein,
        carbsG = 0.0,
        fatG = 0.0,
        fiberG = 0.0,
        comment = "",
        modelUsed = "",
        confidence = "",
        imagePath = null,
        peopleCount = people,
    )

    @Test
    fun buildPutsMetricsInFrontmatterAndJournalInBody() {
        val note = DailyNoteMapper.build(
            date = date,
            journalText = "felt great today",
            foods = listOf(food(500.0, 40.0), food(300.0, 20.0)),
            fasts = listOf(
                FastSession(
                    modeName = "SIXTEEN_EIGHT",
                    targetSeconds = 0,
                    eatingWindowSeconds = 0,
                    startEpoch = 0L,
                    endEpoch = 16 * 3_600_000L,
                    completed = true,
                ),
            ),
            weights = listOf(WeightEntry(timestampEpoch = 5L, lastModifiedEpoch = 50L, weightKg = 82.5, note = "")),
            workouts = listOf(
                WorkoutSession(
                    modeName = "HIIT",
                    startEpoch = 0L,
                    endEpoch = 1L,
                    totalSeconds = 1800L,
                    intervalSeconds = 30,
                    exerciseSeconds = 900L,
                    restSeconds = 900L,
                    notes = "",
                    lastModifiedEpoch = 200L,
                ),
            ),
        )
        assertEquals("2026-09-20", note.frontmatter["date"])
        assertEquals("800", note.frontmatter["kcal"])
        assertEquals("60", note.frontmatter["protein"])
        assertEquals("16", note.frontmatter["fasted_hours"])
        assertEquals("82.5", note.frontmatter["weight_kg"])
        assertEquals("30", note.frontmatter["workout_minutes"])
        assertEquals("200", note.frontmatter["last_modified"])
        assertEquals("felt great today", note.body)
    }

    @Test
    fun peopleCountDividesMetrics() {
        val note = DailyNoteMapper.build(date, "", listOf(food(800.0, 80.0, people = 2)), emptyList(), emptyList(), emptyList())
        assertEquals("400", note.frontmatter["kcal"])
        assertEquals("40", note.frontmatter["protein"])
    }

    @Test
    fun renderThenParseRoundTrips() {
        val note = DailyNoteMapper.build(
            date = date,
            journalText = "line one\nline two with --- dashes inside\nline three",
            foods = listOf(food(1234.0, 56.7)),
            fasts = emptyList(),
            weights = emptyList(),
            workouts = emptyList(),
        )
        val parsed = DailyNoteMapper.parse(note.render())
        assertEquals(note.date, parsed.date)
        assertEquals(note.frontmatter, parsed.frontmatter)
        assertEquals(note.body, parsed.body)
    }

    @Test
    fun parseHandlesContentWithoutFrontmatter() {
        val parsed = DailyNoteMapper.parse("just a plain body", fallbackDate = date)
        assertTrue(parsed.frontmatter.isEmpty())
        assertEquals("just a plain body", parsed.body)
        assertEquals(date, parsed.date)
    }

    @Test
    fun emptyDayHasOnlyDateFrontmatter() {
        val note = DailyNoteMapper.build(date, "", emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(1, note.frontmatter.size)
        assertEquals("2026-09-20", note.frontmatter["date"])
        assertEquals("", note.body)
    }
}
