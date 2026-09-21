package com.kbul.spicycrab.domain.nutrition

data class NutritionEstimate(
    val itemName: String,
    val grams: Double,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
    val sodiumMg: Double = 0.0,
    val isVegan: Boolean = false,
    val isVegetarian: Boolean = false,
    val alcoholG: Double = 0.0,
    val caffeineMg: Double = 0.0,
    val sugarG: Double = 0.0,
    val confidence: String,
    val notes: String,
    val modelUsed: String = "",
    val detailPrompt: String = "",
    val peopleCount: Int = 1,
    /** True for barcode-scanned pantry items: not assumed eaten until the user confirms. */
    val pendingConsumption: Boolean = false,
)
