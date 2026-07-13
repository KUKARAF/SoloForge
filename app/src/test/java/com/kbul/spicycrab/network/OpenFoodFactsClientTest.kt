package com.kbul.spicycrab.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFoodFactsClientTest {

    private fun clientReturning(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        OpenFoodFactsClient(
            MockEngine {
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        )

    @Test
    fun parsesProductWithSodiumInGrams() = runBlocking {
        val body = """
            {"status":1,"product":{"product_name":"Soy Sauce","serving_quantity":15.0,
             "nutriments":{"energy-kcal_100g":60.0,"proteins_100g":6.0,"carbohydrates_100g":6.0,
             "fat_100g":0.0,"fiber_100g":0.5,"sodium_100g":6.0}}}
        """.trimIndent()
        val product = clientReturning(body).lookup("1234567890123").getOrThrow()
        assertEquals("Soy Sauce", product?.productName)
        assertEquals(60.0, product?.nutriments?.energyKcal100g!!, 0.0)
        assertEquals(6.0, product.nutriments?.sodium100g!!, 0.0)
    }

    @Test
    fun statusZeroMeansMiss() = runBlocking {
        val body = """{"status":0}"""
        val product = clientReturning(body).lookup("0000000000000").getOrThrow()
        assertNull(product)
    }

    @Test
    fun httpErrorBecomesFailure() = runBlocking {
        val result = clientReturning("""{"status":0}""", HttpStatusCode.InternalServerError)
            .lookup("1234567890123")
        assertTrue(result.isFailure)
    }
}
