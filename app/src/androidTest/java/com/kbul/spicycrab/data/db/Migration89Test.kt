package com.kbul.spicycrab.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration89Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate8To9BackfillsConsumptionFields() {
        val dbName = "migration-8-9-test"
        helper.createDatabase(dbName, 8).apply {
            execSQL(
                "INSERT INTO food_entries (id, timestampEpoch, lastModifiedEpoch, itemName, grams, kcal, " +
                    "proteinG, carbsG, fatG, fiberG, sodiumMg, comment, modelUsed, confidence, imagePath) " +
                    "VALUES (5, 2000, 2000, 'Miso soup', 250.0, 80.0, 5.0, 8.0, 2.0, 1.0, 950.0, " +
                    "'', 'manual', 'user', NULL)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 9, true, MIGRATION_8_9)

        migrated.query(
            "SELECT itemName, peopleCount, consumedEpoch, addedEpoch FROM food_entries WHERE id = 5"
        ).use { cursor ->
            check(cursor.moveToFirst())
            check(cursor.getString(0) == "Miso soup")
            check(cursor.getInt(1) == 1)
            check(cursor.getLong(2) == 2000L)
            check(cursor.getLong(3) == 2000L)
        }

        migrated.execSQL(
            "INSERT INTO food_entries (timestampEpoch, lastModifiedEpoch, itemName, grams, kcal, " +
                "proteinG, carbsG, fatG, fiberG, sodiumMg, comment, modelUsed, confidence, imagePath, " +
                "peopleCount, consumedEpoch, addedEpoch) " +
                "VALUES (4000, 4000, 'Soy sauce bottle', 150.0, 90.0, 8.0, 10.0, 0.0, 0.0, 6000.0, " +
                "'scanned', 'barcode:off', 'high', NULL, 3, NULL, 4000)"
        )
        migrated.query(
            "SELECT peopleCount, consumedEpoch FROM food_entries WHERE itemName = 'Soy sauce bottle'"
        ).use { cursor ->
            check(cursor.moveToFirst())
            check(cursor.getInt(0) == 3)
            check(cursor.isNull(1))
        }
        migrated.close()
    }
}
