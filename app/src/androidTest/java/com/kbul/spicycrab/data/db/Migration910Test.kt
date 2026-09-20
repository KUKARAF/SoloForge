package com.kbul.spicycrab.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration910Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate9To10BackfillsDietAndStretchFlags() {
        val dbName = "migration-9-10-test"
        helper.createDatabase(dbName, 9).apply {
            execSQL(
                "INSERT INTO food_entries (id, timestampEpoch, lastModifiedEpoch, itemName, grams, kcal, " +
                    "proteinG, carbsG, fatG, fiberG, sodiumMg, comment, modelUsed, confidence, imagePath, " +
                    "peopleCount, consumedEpoch, addedEpoch) " +
                    "VALUES (7, 3000, 3000, 'Tofu bowl', 300.0, 400.0, 25.0, 30.0, 12.0, 6.0, 500.0, " +
                    "'', 'manual', 'user', NULL, 1, 3000, 3000)"
            )
            execSQL(
                "INSERT INTO workout_sessions (id, modeName, startEpoch, endEpoch, totalSeconds, " +
                    "intervalSeconds, exerciseSeconds, restSeconds, notes, lastModifiedEpoch) " +
                    "VALUES (3, 'SIMPLE', 3000, 4800, 1800, 0, 0, 0, '', 4800)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 10, true, MIGRATION_9_10)

        migrated.query(
            "SELECT itemName, isVegan, isVegetarian FROM food_entries WHERE id = 7"
        ).use { cursor ->
            check(cursor.moveToFirst())
            check(cursor.getString(0) == "Tofu bowl")
            check(cursor.getInt(1) == 0)
            check(cursor.getInt(2) == 0)
        }

        migrated.query(
            "SELECT modeName, isStretch FROM workout_sessions WHERE id = 3"
        ).use { cursor ->
            check(cursor.moveToFirst())
            check(cursor.getString(0) == "SIMPLE")
            check(cursor.getInt(1) == 0)
        }

        // New rows can set the added columns.
        migrated.execSQL(
            "INSERT INTO food_entries (timestampEpoch, lastModifiedEpoch, itemName, grams, kcal, " +
                "proteinG, carbsG, fatG, fiberG, sodiumMg, comment, modelUsed, confidence, imagePath, " +
                "peopleCount, consumedEpoch, addedEpoch, isVegan, isVegetarian) " +
                "VALUES (5000, 5000, 'Lentil soup', 250.0, 180.0, 12.0, 20.0, 2.0, 8.0, 300.0, " +
                "'', 'manual', 'user', NULL, 1, 5000, 5000, 1, 1)"
        )
        migrated.query(
            "SELECT isVegan, isVegetarian FROM food_entries WHERE itemName = 'Lentil soup'"
        ).use { cursor ->
            check(cursor.moveToFirst())
            check(cursor.getInt(0) == 1)
            check(cursor.getInt(1) == 1)
        }

        migrated.execSQL(
            "INSERT INTO workout_sessions (modeName, startEpoch, endEpoch, totalSeconds, " +
                "intervalSeconds, exerciseSeconds, restSeconds, notes, lastModifiedEpoch, isStretch) " +
                "VALUES ('SIMPLE', 6000, 6000, 0, 0, 0, 0, 'Stretched', 6000, 1)"
        )
        migrated.query(
            "SELECT isStretch FROM workout_sessions WHERE notes = 'Stretched'"
        ).use { cursor ->
            check(cursor.moveToFirst())
            check(cursor.getInt(0) == 1)
        }

        // The new substance_entries table exists and accepts rows.
        migrated.execSQL(
            "INSERT INTO substance_entries (key, amountInt, timestampEpoch, lastModifiedEpoch, synced) " +
                "VALUES ('caffeine', 40, 7000, 7000, 0)"
        )
        migrated.query(
            "SELECT key, amountInt, synced FROM substance_entries"
        ).use { cursor ->
            check(cursor.moveToFirst())
            check(cursor.getString(0) == "caffeine")
            check(cursor.getInt(1) == 40)
            check(cursor.getInt(2) == 0)
        }
        migrated.close()
    }
}
