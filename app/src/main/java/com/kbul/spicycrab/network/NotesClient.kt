package com.kbul.spicycrab.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPath
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Config for the rust_note daily-notes server at notes.osmosis.page. The [token] is a device
 * bearer token the user obtains via interactive login and pastes into settings; there is no
 * static API key. Kept separate from the unrelated Grist barcode cache (csv.osmosis.page).
 */
data class NotesConfig(
    val baseUrl: String,
    val token: String,
)

@Singleton
class NotesClient internal constructor(engine: HttpClientEngine) {

    @Inject constructor() : this(OkHttp.create())

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = false }

    private val client = HttpClient(engine) {
        install(ContentNegotiation) { json(this@NotesClient.json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
    }

    /** Returns the note, or null when the server reports 404 (no note for that path yet). */
    suspend fun getNote(config: NotesConfig, path: String): Result<NoteResponse?> = runCatching {
        val resp: HttpResponse = client.get(noteUrl(config, path)) {
            bearer(config)
        }
        if (resp.status == HttpStatusCode.NotFound) return@runCatching null
        val raw = resp.bodyAsText()
        if (!resp.status.isSuccess()) error("Notes ${resp.status.value}: ${raw.take(300)}")
        json.decodeFromString(NoteResponse.serializer(), raw)
    }

    suspend fun createNote(config: NotesConfig, idOrTitle: String, content: String): Result<NoteMeta> = runCatching {
        val resp: HttpResponse = client.post(notesUrl(config)) {
            bearer(config)
            contentType(ContentType.Application.Json)
            setBody(CreateNoteRequest(idOrTitle, content))
        }
        val raw = resp.bodyAsText()
        if (!resp.status.isSuccess()) error("Notes ${resp.status.value}: ${raw.take(300)}")
        json.decodeFromString(NoteMeta.serializer(), raw)
    }

    /** Auto-creates the note if missing. Send [expectedVersion] to guard against a stale overwrite. */
    suspend fun putNote(
        config: NotesConfig,
        path: String,
        content: String,
        expectedVersion: String? = null,
    ): Result<NoteMeta> = runCatching {
        val resp: HttpResponse = client.put(noteUrl(config, path)) {
            bearer(config)
            contentType(ContentType.Application.Json)
            setBody(PutNoteRequest(content, expectedVersion))
        }
        val raw = resp.bodyAsText()
        if (!resp.status.isSuccess()) error("Notes ${resp.status.value}: ${raw.take(300)}")
        json.decodeFromString(NoteMeta.serializer(), raw)
    }

    suspend fun deleteNote(config: NotesConfig, path: String): Result<Unit> = runCatching {
        val resp: HttpResponse = client.delete(noteUrl(config, path)) {
            bearer(config)
        }
        val raw = resp.bodyAsText()
        if (!resp.status.isSuccess()) error("Notes ${resp.status.value}: ${raw.take(300)}")
    }

    /** Appends a structured metric into the day's frontmatter server-side. */
    suspend fun postStat(
        config: NotesConfig,
        key: String,
        value: Double,
        at: String? = null,
        date: String? = null,
    ): Result<Unit> = runCatching {
        val resp: HttpResponse = client.post(statsUrl(config)) {
            bearer(config)
            contentType(ContentType.Application.Json)
            setBody(StatRequest(key, value, at, date))
        }
        val raw = resp.bodyAsText()
        if (!resp.status.isSuccess()) error("Notes ${resp.status.value}: ${raw.take(300)}")
    }

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(config: NotesConfig) {
        headers { append(HttpHeaders.Authorization, "Bearer ${config.token}") }
    }

    private fun base(config: NotesConfig): String = config.baseUrl.trimEnd('/')
    private fun notesUrl(config: NotesConfig): String = "${base(config)}/api/notes"
    private fun noteUrl(config: NotesConfig, path: String): String =
        "${base(config)}/api/notes/${path.trim('/').encodeURLPath()}"
    private fun statsUrl(config: NotesConfig): String = "${base(config)}/api/stats"
}
