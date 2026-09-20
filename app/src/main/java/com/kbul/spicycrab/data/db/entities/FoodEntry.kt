package com.kbul.spicycrab.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "food_entries")
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampEpoch: Long,
    val lastModifiedEpoch: Long,
    val itemName: String,
    val grams: Double,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
    val sodiumMg: Double = 0.0,
    val comment: String,
    val modelUsed: String,
    val confidence: String,
    val imagePath: String?,
    /** How many people shared this entry. Totals and the food list divide by this. */
    val peopleCount: Int = 1,
    /** Null until the user confirms consumption (e.g. a barcode-scanned pantry item). */
    val consumedEpoch: Long? = null,
    /** When this row was created; unlike [timestampEpoch] it is not user-editable. */
    val addedEpoch: Long = 0L,
    /** Contains no animal products. Invariant: if true, [isVegetarian] must also be true. */
    val isVegan: Boolean = false,
    /** Contains no meat/fish (dairy/eggs allowed). Implied true whenever [isVegan] is true. */
    val isVegetarian: Boolean = false,
)
