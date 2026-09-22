package com.kbul.spicycrab.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema deltas:
 *   v1: fast_sessions
 *   v2: + food_entries, + weight_entries
 *   v3: food_entries gains lastModifiedEpoch
 *   v4: + workout_sessions
 *   v5: weight_entries gains lastModifiedEpoch
 *   v6: + meal_presets
 *   v7: + journal_entries
 *   v8: food_entries and meal_presets gain sodiumMg
 *   v9: food_entries gains peopleCount, consumedEpoch, addedEpoch
 *   v10: food_entries gains isVegan, isVegetarian; workout_sessions gains isStretch;
 *        + substance_entries
 *
 * Early development used `fallbackToDestructiveMigration()`, so v1–v3 schema
 * JSONs were never exported. The migrations below exist as defensive paths
 * (and documentation of the deltas) but are not unit-tested against legacy
 * schemas because those schemas don't exist.
 *
 * Going forward (v4 → v5 → ...): bump `AppDatabase.version`, add a new
 * Migration object here, build once to generate `app/schemas/<n>.json`, then
 * add a `MigrationTestHelper`-based test under `app/src/androidTest/...`.
 */

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS food_entries (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                timestampEpoch INTEGER NOT NULL,
                itemName TEXT NOT NULL,
                grams REAL NOT NULL,
                kcal REAL NOT NULL,
                proteinG REAL NOT NULL,
                carbsG REAL NOT NULL,
                fatG REAL NOT NULL,
                fiberG REAL NOT NULL,
                comment TEXT NOT NULL,
                modelUsed TEXT NOT NULL,
                confidence TEXT NOT NULL,
                imagePath TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS weight_entries (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                timestampEpoch INTEGER NOT NULL,
                weightKg REAL NOT NULL,
                note TEXT NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Can't ALTER TABLE ADD COLUMN with NOT NULL DEFAULT — Room's schema check
        // would see a mismatched defaultValue. Rename + recreate + copy is the
        // standard Room workaround. Backfills lastModifiedEpoch from timestampEpoch
        // (treats existing rows as never edited).
        db.execSQL(
            """
            CREATE TABLE food_entries_new (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                timestampEpoch INTEGER NOT NULL,
                lastModifiedEpoch INTEGER NOT NULL,
                itemName TEXT NOT NULL,
                grams REAL NOT NULL,
                kcal REAL NOT NULL,
                proteinG REAL NOT NULL,
                carbsG REAL NOT NULL,
                fatG REAL NOT NULL,
                fiberG REAL NOT NULL,
                comment TEXT NOT NULL,
                modelUsed TEXT NOT NULL,
                confidence TEXT NOT NULL,
                imagePath TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO food_entries_new
                (id, timestampEpoch, lastModifiedEpoch, itemName, grams, kcal,
                 proteinG, carbsG, fatG, fiberG, comment, modelUsed, confidence, imagePath)
            SELECT id, timestampEpoch, timestampEpoch, itemName, grams, kcal,
                   proteinG, carbsG, fatG, fiberG, comment, modelUsed, confidence, imagePath
            FROM food_entries
            """.trimIndent()
        )
        db.execSQL("DROP TABLE food_entries")
        db.execSQL("ALTER TABLE food_entries_new RENAME TO food_entries")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS workout_sessions (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                modeName TEXT NOT NULL,
                startEpoch INTEGER NOT NULL,
                endEpoch INTEGER,
                totalSeconds INTEGER NOT NULL,
                intervalSeconds INTEGER NOT NULL,
                exerciseSeconds INTEGER NOT NULL,
                restSeconds INTEGER NOT NULL,
                notes TEXT NOT NULL,
                lastModifiedEpoch INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE weight_entries_new (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                timestampEpoch INTEGER NOT NULL,
                lastModifiedEpoch INTEGER NOT NULL,
                weightKg REAL NOT NULL,
                note TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO weight_entries_new
                (id, timestampEpoch, lastModifiedEpoch, weightKg, note)
            SELECT id, timestampEpoch, timestampEpoch, weightKg, note
            FROM weight_entries
            """.trimIndent()
        )
        db.execSQL("DROP TABLE weight_entries")
        db.execSQL("ALTER TABLE weight_entries_new RENAME TO weight_entries")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS meal_presets (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                grams REAL NOT NULL,
                kcal REAL NOT NULL,
                proteinG REAL NOT NULL,
                carbsG REAL NOT NULL,
                fatG REAL NOT NULL,
                fiberG REAL NOT NULL,
                comment TEXT NOT NULL,
                createdEpoch INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS journal_entries (
                dateEpochDay INTEGER NOT NULL,
                text TEXT NOT NULL,
                lastModifiedEpoch INTEGER NOT NULL,
                PRIMARY KEY(dateEpochDay)
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE food_entries_new (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                timestampEpoch INTEGER NOT NULL,
                lastModifiedEpoch INTEGER NOT NULL,
                itemName TEXT NOT NULL,
                grams REAL NOT NULL,
                kcal REAL NOT NULL,
                proteinG REAL NOT NULL,
                carbsG REAL NOT NULL,
                fatG REAL NOT NULL,
                fiberG REAL NOT NULL,
                sodiumMg REAL NOT NULL,
                comment TEXT NOT NULL,
                modelUsed TEXT NOT NULL,
                confidence TEXT NOT NULL,
                imagePath TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO food_entries_new
                (id, timestampEpoch, lastModifiedEpoch, itemName, grams, kcal,
                 proteinG, carbsG, fatG, fiberG, sodiumMg, comment, modelUsed, confidence, imagePath)
            SELECT id, timestampEpoch, lastModifiedEpoch, itemName, grams, kcal,
                   proteinG, carbsG, fatG, fiberG, 0.0, comment, modelUsed, confidence, imagePath
            FROM food_entries
            """.trimIndent()
        )
        db.execSQL("DROP TABLE food_entries")
        db.execSQL("ALTER TABLE food_entries_new RENAME TO food_entries")

        db.execSQL(
            """
            CREATE TABLE meal_presets_new (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                grams REAL NOT NULL,
                kcal REAL NOT NULL,
                proteinG REAL NOT NULL,
                carbsG REAL NOT NULL,
                fatG REAL NOT NULL,
                fiberG REAL NOT NULL,
                sodiumMg REAL NOT NULL,
                comment TEXT NOT NULL,
                createdEpoch INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO meal_presets_new
                (id, name, grams, kcal, proteinG, carbsG, fatG, fiberG, sodiumMg, comment, createdEpoch)
            SELECT id, name, grams, kcal, proteinG, carbsG, fatG, fiberG, 0.0, comment, createdEpoch
            FROM meal_presets
            """.trimIndent()
        )
        db.execSQL("DROP TABLE meal_presets")
        db.execSQL("ALTER TABLE meal_presets_new RENAME TO meal_presets")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE food_entries_new (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                timestampEpoch INTEGER NOT NULL,
                lastModifiedEpoch INTEGER NOT NULL,
                itemName TEXT NOT NULL,
                grams REAL NOT NULL,
                kcal REAL NOT NULL,
                proteinG REAL NOT NULL,
                carbsG REAL NOT NULL,
                fatG REAL NOT NULL,
                fiberG REAL NOT NULL,
                sodiumMg REAL NOT NULL,
                comment TEXT NOT NULL,
                modelUsed TEXT NOT NULL,
                confidence TEXT NOT NULL,
                imagePath TEXT,
                peopleCount INTEGER NOT NULL,
                consumedEpoch INTEGER,
                addedEpoch INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO food_entries_new
                (id, timestampEpoch, lastModifiedEpoch, itemName, grams, kcal,
                 proteinG, carbsG, fatG, fiberG, sodiumMg, comment, modelUsed, confidence, imagePath,
                 peopleCount, consumedEpoch, addedEpoch)
            SELECT id, timestampEpoch, lastModifiedEpoch, itemName, grams, kcal,
                   proteinG, carbsG, fatG, fiberG, sodiumMg, comment, modelUsed, confidence, imagePath,
                   1, timestampEpoch, timestampEpoch
            FROM food_entries
            """.trimIndent()
        )
        db.execSQL("DROP TABLE food_entries")
        db.execSQL("ALTER TABLE food_entries_new RENAME TO food_entries")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Recreate food_entries to append isVegan/isVegetarian (backfilled false), matching the
        // established rename+copy pattern so the exported schema carries no column defaultValue.
        db.execSQL(
            """
            CREATE TABLE food_entries_new (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                timestampEpoch INTEGER NOT NULL,
                lastModifiedEpoch INTEGER NOT NULL,
                itemName TEXT NOT NULL,
                grams REAL NOT NULL,
                kcal REAL NOT NULL,
                proteinG REAL NOT NULL,
                carbsG REAL NOT NULL,
                fatG REAL NOT NULL,
                fiberG REAL NOT NULL,
                sodiumMg REAL NOT NULL,
                comment TEXT NOT NULL,
                modelUsed TEXT NOT NULL,
                confidence TEXT NOT NULL,
                imagePath TEXT,
                peopleCount INTEGER NOT NULL,
                consumedEpoch INTEGER,
                addedEpoch INTEGER NOT NULL,
                isVegan INTEGER NOT NULL,
                isVegetarian INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO food_entries_new
                (id, timestampEpoch, lastModifiedEpoch, itemName, grams, kcal,
                 proteinG, carbsG, fatG, fiberG, sodiumMg, comment, modelUsed, confidence, imagePath,
                 peopleCount, consumedEpoch, addedEpoch, isVegan, isVegetarian)
            SELECT id, timestampEpoch, lastModifiedEpoch, itemName, grams, kcal,
                   proteinG, carbsG, fatG, fiberG, sodiumMg, comment, modelUsed, confidence, imagePath,
                   peopleCount, consumedEpoch, addedEpoch, 0, 0
            FROM food_entries
            """.trimIndent()
        )
        db.execSQL("DROP TABLE food_entries")
        db.execSQL("ALTER TABLE food_entries_new RENAME TO food_entries")

        // Recreate workout_sessions to append isStretch (backfilled false).
        db.execSQL(
            """
            CREATE TABLE workout_sessions_new (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                modeName TEXT NOT NULL,
                startEpoch INTEGER NOT NULL,
                endEpoch INTEGER,
                totalSeconds INTEGER NOT NULL,
                intervalSeconds INTEGER NOT NULL,
                exerciseSeconds INTEGER NOT NULL,
                restSeconds INTEGER NOT NULL,
                notes TEXT NOT NULL,
                lastModifiedEpoch INTEGER NOT NULL,
                isStretch INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO workout_sessions_new
                (id, modeName, startEpoch, endEpoch, totalSeconds, intervalSeconds,
                 exerciseSeconds, restSeconds, notes, lastModifiedEpoch, isStretch)
            SELECT id, modeName, startEpoch, endEpoch, totalSeconds, intervalSeconds,
                   exerciseSeconds, restSeconds, notes, lastModifiedEpoch, 0
            FROM workout_sessions
            """.trimIndent()
        )
        db.execSQL("DROP TABLE workout_sessions")
        db.execSQL("ALTER TABLE workout_sessions_new RENAME TO workout_sessions")

        // New table for timed substance samples (alcohol/caffeine/nicotine/custom).
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS substance_entries (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                key TEXT NOT NULL,
                amountInt INTEGER NOT NULL,
                timestampEpoch INTEGER NOT NULL,
                lastModifiedEpoch INTEGER NOT NULL,
                synced INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
