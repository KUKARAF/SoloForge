package com.kbul.spicycrab.data.notes

import com.kbul.spicycrab.data.db.entities.FastSession
import com.kbul.spicycrab.data.db.entities.FoodEntry
import com.kbul.spicycrab.data.db.entities.WeightEntry
import com.kbul.spicycrab.data.db.entities.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyNoteMapperTest {

    private val date = LocalDate.of(2026, 9, 20)

    private fun food(
        kcal: Double,
        protein: Double,
        people: Int = 1,
        sodiumMg: Double = 0.0,
        isVegan: Boolean = false,
        isVegetarian: Boolean = false,
    ) = FoodEntry(
        timestampEpoch = 0L,
        lastModifiedEpoch = 100L,
        itemName = "meal",
        grams = 100.0,
        kcal = kcal,
        proteinG = protein,
        carbsG = 0.0,
        fatG = 0.0,
        fiberG = 0.0,
        sodiumMg = sodiumMg,
        comment = "",
        modelUsed = "",
        confidence = "",
        imagePath = null,
        peopleCount = people,
        isVegan = isVegan,
        isVegetarian = isVegetarian,
    )

    private fun workout(isStretch: Boolean, totalSeconds: Long = 600L) = WorkoutSession(
        modeName = "SIMPLE",
        startEpoch = 0L,
        endEpoch = 1L,
        totalSeconds = totalSeconds,
        intervalSeconds = 0,
        exerciseSeconds = 0L,
        restSeconds = 0L,
        notes = "",
        lastModifiedEpoch = 200L,
        isStretch = isStretch,
    )

    private fun build(
        foods: List<FoodEntry> = emptyList(),
        workouts: List<WorkoutSession> = emptyList(),
        journal: String = "",
    ) = DailyNoteMapper.build(date, journal, foods, emptyList(), emptyList(), workouts)

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
            workouts = listOf(workout(isStretch = false, totalSeconds = 1800L)),
        )
        assertEquals("2026-09-20", note.frontmatter["date"])
        assertEquals("800", note.frontmatter["kcal"])
        assertEquals("60", note.frontmatter["protein"])
        assertEquals("16", note.frontmatter["fasted_hours"])
        assertEquals("82.5", note.frontmatter["weight_kg"])
        assertEquals("30", note.frontmatter["workout_minutes"])
        assertEquals("felt great today", note.body)
    }

    @Test
    fun peopleCountDividesMetrics() {
        val note = build(foods = listOf(food(800.0, 80.0, people = 2)))
        assertEquals("400", note.frontmatter["kcal"])
        assertEquals("40", note.frontmatter["protein"])
    }

    @Test
    fun allVeganDayIsVeganAndVegetarian() {
        val note = build(
            foods = listOf(
                food(300.0, 10.0, isVegan = true, isVegetarian = true),
                food(200.0, 8.0, isVegan = true, isVegetarian = true),
            ),
        )
        assertEquals("true", note.frontmatter["vegan"])
        assertEquals("true", note.frontmatter["vegetarian"])
    }

    @Test
    fun mixedVeganAndVegetarianIsVegetarianOnly() {
        val note = build(
            foods = listOf(
                food(300.0, 10.0, isVegan = true, isVegetarian = true),
                food(200.0, 8.0, isVegan = false, isVegetarian = true),
            ),
        )
        assertEquals("false", note.frontmatter["vegan"])
        assertEquals("true", note.frontmatter["vegetarian"])
    }

    @Test
    fun anyNonVegEntryMakesBothFalse() {
        val note = build(
            foods = listOf(
                food(300.0, 10.0, isVegan = true, isVegetarian = true),
                food(500.0, 40.0, isVegan = false, isVegetarian = false),
            ),
        )
        assertEquals("false", note.frontmatter["vegan"])
        assertEquals("false", note.frontmatter["vegetarian"])
    }

    @Test
    fun saltGramsSumsSodiumWithConversion() {
        // (600 + 400) mg total sodium * 2.5 / 1000 = 2.5 g salt.
        val note = build(
            foods = listOf(
                food(300.0, 10.0, sodiumMg = 600.0),
                food(200.0, 8.0, sodiumMg = 400.0),
            ),
        )
        assertEquals("2.5", note.frontmatter["salt_g"])
    }

    @Test
    fun noSodiumDataOmitsSalt() {
        val note = build(foods = listOf(food(300.0, 10.0, sodiumMg = 0.0)))
        assertFalse(note.frontmatter.containsKey("salt_g"))
    }

    @Test
    fun workoutSessionMakesWorkoutTrue() {
        val note = build(workouts = listOf(workout(isStretch = false)))
        assertEquals("true", note.frontmatter["workout"])
        assertEquals("false", note.frontmatter["stretches"])
    }

    @Test
    fun stretchOnlyMakesStretchesTrueWorkoutFalse() {
        val note = build(workouts = listOf(workout(isStretch = true, totalSeconds = 0L)))
        assertEquals("false", note.frontmatter["workout"])
        assertEquals("true", note.frontmatter["stretches"])
        // A stretch alone must not register workout minutes.
        assertFalse(note.frontmatter.containsKey("workout_minutes"))
    }

    @Test
    fun bothWorkoutAndStretchTrue() {
        val note = build(workouts = listOf(workout(isStretch = false), workout(isStretch = true, totalSeconds = 0L)))
        assertEquals("true", note.frontmatter["workout"])
        assertEquals("true", note.frontmatter["stretches"])
    }

    @Test
    fun noSessionsMakesBothFalse() {
        val note = build()
        assertEquals("false", note.frontmatter["workout"])
        assertEquals("false", note.frontmatter["stretches"])
    }

    @Test
    fun noFoodDayOmitsFoodKeysButKeepsWorkoutBooleans() {
        val note = build()
        assertFalse(note.frontmatter.containsKey("vegan"))
        assertFalse(note.frontmatter.containsKey("vegetarian"))
        assertFalse(note.frontmatter.containsKey("salt_g"))
        assertFalse(note.frontmatter.containsKey("kcal"))
        assertTrue(note.frontmatter.containsKey("workout"))
        assertTrue(note.frontmatter.containsKey("stretches"))
        assertEquals("2026-09-20", note.frontmatter["date"])
        assertEquals("", note.body)
    }

    @Test
    fun renderThenParseRoundTrips() {
        val note = DailyNoteMapper.build(
            date = date,
            journalText = "line one\nline two with --- dashes inside\nline three",
            foods = listOf(food(1234.0, 56.7, sodiumMg = 500.0, isVegan = true, isVegetarian = true)),
            fasts = emptyList(),
            weights = emptyList(),
            workouts = listOf(workout(isStretch = false)),
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
    fun renderMergedPreservesForeignKeysAndUpdatesManagedOnes() {
        // Existing server note carries substance metrics (/api/stats) and another app's key,
        // plus a stale managed value that our fresh compute should overwrite.
        val existing = """
            ---
            date: 2026-09-20
            kcal: 999
            caffeine: [40@0720, 30@1500]
            alcohol: 15@1930
            mood: great
            ---
            server body that should be replaced
        """.trimIndent()

        val managed = build(
            foods = listOf(food(500.0, 40.0, isVegan = true, isVegetarian = true)),
            workouts = listOf(workout(isStretch = false)),
            journal = "my journal body",
        )
        val merged = DailyNoteMapper.parse(DailyNoteMapper.renderMerged(managed, existing))

        // Foreign keys survive untouched.
        assertEquals("[40@0720, 30@1500]", merged.frontmatter["caffeine"])
        assertEquals("15@1930", merged.frontmatter["alcohol"])
        assertEquals("great", merged.frontmatter["mood"])
        // Managed keys are overwritten with freshly computed values.
        assertEquals("500", merged.frontmatter["kcal"])
        assertEquals("true", merged.frontmatter["vegan"])
        assertEquals("true", merged.frontmatter["workout"])
        // SoloForge owns the body (the journal).
        assertEquals("my journal body", merged.body)
    }

    @Test
    fun renderMergedWithNoExistingNoteJustRenders() {
        val managed = build(foods = listOf(food(300.0, 10.0)))
        val out = DailyNoteMapper.parse(DailyNoteMapper.renderMerged(managed, null))
        assertEquals(managed.frontmatter, out.frontmatter)
    }
}
