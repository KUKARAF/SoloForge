package com.kbul.spicycrab.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

data class GristConfig(
    val baseUrl: String,
    val docId: String,
    val tableId: String,
    val apiKey: String,
)

@Singleton
class GristClient internal constructor(engine: HttpClientEngine) {

    @Inject constructor() : this(OkHttp.create())

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient(engine) {
        install(ContentNegotiation) { json(this@GristClient.json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
    }

    suspend fun findByBarcode(config: GristConfig, barcode: String): Result<GristProductFields?> = runCatching {
        val filter = json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("barcode", buildJsonArray { add(JsonPrimitive(barcode)) })
            },
        )
        val resp: HttpResponse = client.get(recordsUrl(config)) {
            headers { append(HttpHeaders.Authorization, "Bearer ${config.apiKey}") }
            parameter("filter", filter)
        }
        val raw = resp.bodyAsText()
        if (!resp.status.isSuccess()) error("Grist ${resp.status.value}: ${raw.take(300)}")
        val parsed = json.decodeFromString(GristRecordsResponse.serializer(), raw)
        parsed.records.firstOrNull()?.fields
    }

    suspend fun insert(config: GristConfig, fields: GristProductFields): Result<Unit> = runCatching {
        val request = GristInsertRequest(records = listOf(GristInsertRecord(fields)))
        val resp: HttpResponse = client.post(recordsUrl(config)) {
            headers { append(HttpHeaders.Authorization, "Bearer ${config.apiKey}") }
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        val raw = resp.bodyAsText()
        if (!resp.status.isSuccess()) error("Grist ${resp.status.value}: ${raw.take(300)}")
    }

    private fun recordsUrl(config: GristConfig): String =
        "${config.baseUrl.trimEnd('/')}/api/docs/${config.docId}/tables/${config.tableId}/records"
}
