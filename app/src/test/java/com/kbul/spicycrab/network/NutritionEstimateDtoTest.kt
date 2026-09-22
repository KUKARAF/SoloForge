package com.kbul.spicycrab.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionEstimateDtoTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun parsesVeganVegetarianAndSubstances() {
        val body = """
            {"item_name":"Weizen beer","estimated_grams":500,"calories":210,
             "protein_g":2.0,"carbs_g":18.0,"fat_g":0.0,"fiber_g":0.0,"sodium_mg":25,
             "vegan":true,"vegetarian":true,"alcohol_g":15,"caffeine_mg":0,"sugar_g":3,
             "confidence":"medium","notes":"~500ml"}
        """.trimIndent()
        val dto = json.decodeFromString(NutritionEstimateDto.serializer(), body)
        assertTrue(dto.vegan)
        assertTrue(dto.vegetarian)
        assertEquals(15.0, dto.alcoholG, 0.0)
        assertEquals(0.0, dto.caffeineMg, 0.0)
        assertEquals(3.0, dto.sugarG, 0.0)
    }

    @Test
    fun defaultsSubstancesToZeroWhenAbsent() {
        val body = """
            {"item_name":"Salad","estimated_grams":200,"calories":150,
             "protein_g":4.0,"carbs_g":10.0,"fat_g":8.0,"fiber_g":5.0,"sodium_mg":100,
             "confidence":"high","notes":""}
        """.trimIndent()
        val dto = json.decodeFromString(NutritionEstimateDto.serializer(), body)
        assertEquals(0.0, dto.alcoholG, 0.0)
        assertEquals(0.0, dto.caffeineMg, 0.0)
        assertEquals(0.0, dto.sugarG, 0.0)
        assertEquals(false, dto.vegan)
    }
}
