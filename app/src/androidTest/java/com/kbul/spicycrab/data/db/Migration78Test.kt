package com.kbul.spicycrab.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration78Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate7To8AddsSodiumWithZeroBackfill() {
        val dbName = "migration-7-8-test"
        helper.createDatabase(dbName, 7).apply {
            execSQL(
                "INSERT INTO food_entries (id, timestampEpoch, lastModifiedEpoch, itemName, grams, kcal, " +
                    "proteinG, carbsG, fatG, fiberG, comment, modelUsed, confidence, imagePath) " +
                    "VALUES (3, 1000, 1000, 'Chicken & rice', 350.0, 540.5, 42.0, 55.0, 14.0, 3.5, " +
                    "'post-workout', 'manual', 'user', NULL)"
            )
            execSQL(
                "INSERT INTO meal_presets (id, name, grams, kcal, proteinG, carbsG, fatG, fiberG, comment, createdEpoch) " +
                    "VALUES (1, 'Overnight oats', 300.0, 420.0, 18.0, 60.0, 12.0, 9.5, 'jar prep', 2000)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        migrated.query("SELECT itemName, kcal, sodiumMg, imagePath FROM food_entries WHERE id = 3").use { cursor ->
            check(cursor.moveToFirst())
            check(cursor.getString(0) == "Chicken & rice")
            check(cursor.getDouble(1) == 540.5)
            check(cursor.getDouble(2) == 0.0)
            check(cursor.isNull(3))
        }

        migrated.query("SELECT name, fiberG, sodiumMg FROM meal_presets WHERE id = 1").use { cursor ->
            check(cursor.moveToFirst())
            check(cursor.getString(0) == "Overnight oats")
            check(cursor.getDouble(1) == 9.5)
            check(cursor.getDouble(2) == 0.0)
        }

        migrated.execSQL(
            "INSERT INTO food_entries (timestampEpoch, lastModifiedEpoch, itemName, grams, kcal, " +
                "proteinG, carbsG, fatG, fiberG, sodiumMg, comment, modelUsed, confidence, imagePath) " +
                "VALUES (3000, 3000, 'Miso soup', 250.0, 80.0, 5.0, 8.0, 2.0, 1.0, 950.0, '', 'manual', 'user', NULL)"
        )
        migrated.query("SELECT sodiumMg FROM food_entries WHERE itemName = 'Miso soup'").use { cursor ->
            check(cursor.moveToFirst())
            check(cursor.getDouble(0) == 950.0)
        }
        migrated.close()
    }
}
