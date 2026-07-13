package com.kbul.spicycrab.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private const val ENDPOINT = "https://world.openfoodfacts.org/api/v2/product"

@Singleton
class OpenFoodFactsClient internal constructor(engine: HttpClientEngine) {

    @Inject constructor() : this(OkHttp.create())

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient(engine) {
        install(ContentNegotiation) { json(this@OpenFoodFactsClient.json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
    }

    suspend fun lookup(barcode: String): Result<OpenFoodFactsProduct?> = runCatching {
        val resp: HttpResponse = client.get("$ENDPOINT/$barcode.json")
        val raw = resp.bodyAsText()
        if (!resp.status.isSuccess()) error("Open Food Facts ${resp.status.value}: ${raw.take(300)}")
        val parsed = json.decodeFromString(OpenFoodFactsResponse.serializer(), raw)
        if (parsed.status == 0) null else parsed.product
    }
}
