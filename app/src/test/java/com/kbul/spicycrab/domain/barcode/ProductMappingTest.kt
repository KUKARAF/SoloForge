package com.kbul.spicycrab.domain.barcode

import com.kbul.spicycrab.network.GristProductFields
import com.kbul.spicycrab.network.OpenFoodFactsNutriments
import com.kbul.spicycrab.network.OpenFoodFactsProduct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductMappingTest {

    @Test
    fun sodiumInGramsConvertsToMg() {
        val product = OpenFoodFactsProduct(
            productName = "Soy Sauce",
            nutriments = OpenFoodFactsNutriments(
                energyKcal100g = 60.0,
                proteins100g = 6.0,
                carbohydrates100g = 6.0,
                fat100g = 0.0,
                fiber100g = 0.5,
                sodium100g = 6.0,
            ),
        ).toProduct("5901234123457")
        assertEquals(6000.0, product!!.sodiumMg100, 0.0)
    }

    @Test
    fun fallsBackToSaltDividedBy2Point5WhenSodiumMissing() {
        val product = OpenFoodFactsProduct(
            productName = "Crackers",
            nutriments = OpenFoodFactsNutriments(energyKcal100g = 450.0, salt100g = 1.25),
        ).toProduct("1111111111111")
        assertEquals(500.0, product!!.sodiumMg100, 0.0)
    }

    @Test
    fun zeroSodiumWhenNeitherFieldPresent() {
        val product = OpenFoodFactsProduct(
            productName = "Water",
            nutriments = OpenFoodFactsNutriments(energyKcal100g = 0.0),
        ).toProduct("2222222222222")
        assertEquals(0.0, product!!.sodiumMg100, 0.0)
    }

    @Test
    fun missingKcalIsTreatedAsMiss() {
        val product = OpenFoodFactsProduct(
            productName = "Mystery",
            nutriments = OpenFoodFactsNutriments(sodium100g = 1.0),
        ).toProduct("3333333333333")
        assertNull(product)
    }

    @Test
    fun missingNutrimentsIsTreatedAsMiss() {
        val product = OpenFoodFactsProduct(productName = "No data").toProduct("4444444444444")
        assertNull(product)
    }

    @Test
    fun blankNameFallsBackToScannedProduct() {
        val product = OpenFoodFactsProduct(
            productName = "  ",
            nutriments = OpenFoodFactsNutriments(energyKcal100g = 10.0),
        ).toProduct("5555555555555")
        assertEquals("Scanned product", product!!.name)
    }

    @Test
    fun gristFieldsRoundTripToProductAndBack() {
        val fields = GristProductFields(
            barcode = "5901234123457",
            name = "Soy Sauce",
            kcal100 = 60.0,
            proteinG100 = 6.0,
            carbsG100 = 6.0,
            fatG100 = 0.0,
            fiberG100 = 0.5,
            sodiumMg100 = 6000.0,
            servingG = 15.0,
            source = "openfoodfacts",
            fetchedEpoch = 1000L,
        )
        val product = fields.toProduct("5901234123457")
        assertEquals(fields.name, product.name)
        assertEquals(fields.sodiumMg100, product.sodiumMg100, 0.0)

        val roundTripped = product.toGristFields()
        assertEquals(fields.barcode, roundTripped.barcode)
        assertEquals(fields.kcal100, roundTripped.kcal100, 0.0)
        assertTrue(roundTripped.fetchedEpoch > 0)
    }

    @Test
    fun scaledEstimateUsesServingSizeWhenPresent() {
        val product = Product(
            barcode = "5901234123457",
            name = "Soy Sauce",
            kcal100 = 60.0,
            proteinG100 = 6.0,
            carbsG100 = 6.0,
            fatG100 = 0.0,
            fiberG100 = 0.5,
            sodiumMg100 = 6000.0,
            servingG = 15.0,
            source = "openfoodfacts",
        )
        val estimate = product.toNutritionEstimate()
        assertEquals(15.0, estimate.grams, 0.0)
        assertEquals(9.0, estimate.kcal, 0.0)
        assertEquals(900.0, estimate.sodiumMg, 0.0)
        assertTrue(estimate.pendingConsumption)
        assertEquals("high", estimate.confidence)
    }

    @Test
    fun scaledEstimateFallsBackTo100gWithoutServingSize() {
        val product = Product(
            barcode = "5901234123457",
            name = "Soy Sauce",
            kcal100 = 60.0,
            proteinG100 = 6.0,
            carbsG100 = 6.0,
            fatG100 = 0.0,
            fiberG100 = 0.5,
            sodiumMg100 = 6000.0,
            servingG = null,
            source = "openfoodfacts",
        )
        val estimate = product.toNutritionEstimate()
        assertEquals(100.0, estimate.grams, 0.0)
        assertEquals(60.0, estimate.kcal, 0.0)
        assertTrue(estimate.notes.isNotBlank())
    }
}
