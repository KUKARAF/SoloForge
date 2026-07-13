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

class GristClientTest {

    private val config = GristConfig(
        baseUrl = "https://csv.osmosis.page",
        docId = "docId123",
        tableId = "Products",
        apiKey = "key",
    )

    private fun clientReturning(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        GristClient(
            MockEngine { request ->
                lastRequestUrl = request.url.toString()
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        )

    private var lastRequestUrl: String? = null

    @Test
    fun findByBarcodeEncodesFilterAsJsonQueryParam() = runBlocking {
        val client = clientReturning("""{"records":[]}""")
        client.findByBarcode(config, "5901234123457").getOrThrow()
        assertTrue(lastRequestUrl!!.contains("filter="))
        assertTrue(lastRequestUrl!!.contains("barcode"))
        assertTrue(lastRequestUrl!!.contains("5901234123457"))
        assertTrue(lastRequestUrl!!.startsWith("https://csv.osmosis.page/api/docs/docId123/tables/Products/records"))
    }

    @Test
    fun findByBarcodeReturnsFirstMatchingRecordFields() = runBlocking {
        val body = """
            {"records":[{"id":1,"fields":{"barcode":"5901234123457","name":"Soy Sauce",
             "kcal100":60.0,"proteinG100":6.0,"carbsG100":6.0,"fatG100":0.0,"fiberG100":0.5,
             "sodiumMg100":2400.0,"servingG":15.0,"source":"openfoodfacts","fetchedEpoch":1000}}]}
        """.trimIndent()
        val fields = clientReturning(body).findByBarcode(config, "5901234123457").getOrThrow()
        assertEquals("Soy Sauce", fields?.name)
        assertEquals(2400.0, fields?.sodiumMg100!!, 0.0)
    }

    @Test
    fun findByBarcodeReturnsNullOnNoMatch() = runBlocking {
        val fields = clientReturning("""{"records":[]}""").findByBarcode(config, "0000000000000").getOrThrow()
        assertNull(fields)
    }

    @Test
    fun insertPostsToRecordsEndpoint() = runBlocking {
        val fields = GristProductFields(
            barcode = "5901234123457",
            name = "Soy Sauce",
            kcal100 = 60.0,
            proteinG100 = 6.0,
            carbsG100 = 6.0,
            fatG100 = 0.0,
            fiberG100 = 0.5,
            sodiumMg100 = 2400.0,
            servingG = 15.0,
            source = "openfoodfacts",
            fetchedEpoch = 1000L,
        )
        val result = clientReturning("""{"records":[{"id":1}]}""").insert(config, fields)
        assertTrue(result.isSuccess)
        assertEquals("https://csv.osmosis.page/api/docs/docId123/tables/Products/records", lastRequestUrl)
    }

    @Test
    fun httpErrorBecomesFailure() = runBlocking {
        val result = clientReturning("""{"error":"nope"}""", HttpStatusCode.Unauthorized)
            .findByBarcode(config, "5901234123457")
        assertTrue(result.isFailure)
    }
}
