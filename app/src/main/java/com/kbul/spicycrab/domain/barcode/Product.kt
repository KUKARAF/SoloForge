package com.kbul.spicycrab.domain.barcode

import com.kbul.spicycrab.domain.nutrition.NutritionEstimate

data class Product(
    val barcode: String,
    val name: String,
    val kcal100: Double,
    val proteinG100: Double,
    val carbsG100: Double,
    val fatG100: Double,
    val fiberG100: Double,
    val sodiumMg100: Double,
    val servingG: Double?,
    val source: String,
)

fun Product.toNutritionEstimate(): NutritionEstimate {
    val grams = servingG?.takeIf { it > 0.0 } ?: 100.0
    val scale = grams / 100.0
    return NutritionEstimate(
        itemName = name,
        grams = grams,
        kcal = kcal100 * scale,
        proteinG = proteinG100 * scale,
        carbsG = carbsG100 * scale,
        fatG = fatG100 * scale,
        fiberG = fiberG100 * scale,
        sodiumMg = sodiumMg100 * scale,
        confidence = "high",
        notes = if (servingG != null) "" else "Per 100 g; no serving size on file — scale by weight if needed.",
        modelUsed = "barcode:$source",
        pendingConsumption = true,
    )
}
