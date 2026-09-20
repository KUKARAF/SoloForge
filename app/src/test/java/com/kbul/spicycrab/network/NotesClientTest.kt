package com.kbul.spicycrab.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesClientTest {

    private val config = NotesConfig(
        baseUrl = "https://notes.osmosis.page",
        token = "device-token",
    )

    private var lastUrl: String? = null
    private var lastMethod: HttpMethod? = null
    private var lastAuth: String? = null
    private var lastBody: String? = null

    private fun clientReturning(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        NotesClient(
            MockEngine { request ->
                lastUrl = request.url.toString()
                lastMethod = request.method
                lastAuth = request.headers[HttpHeaders.Authorization]
                lastBody = request.body.toString()
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        )

    private val noteBody = """
        {"meta":{"id":"diary/2026-09-20","title":"2026-09-20","owner_id":"u1",
         "created_at":"t","updated_at":"t","version":"sha123"},
         "content":"---\ndate: 2026-09-20\n---\nhello"}
    """.trimIndent()

    @Test
    fun getNoteParsesMetaAndContentAndSendsBearer() = runBlocking {
        val note = clientReturning(noteBody).getNote(config, "diary/2026-09-20").getOrThrow()
        assertEquals("diary/2026-09-20", note?.meta?.id)
        assertEquals("sha123", note?.meta?.version)
        assertTrue(note!!.content.contains("hello"))
        assertEquals("Bearer device-token", lastAuth)
        assertEquals("https://notes.osmosis.page/api/notes/diary/2026-09-20", lastUrl)
    }

    @Test
    fun getNoteReturnsNullOn404() = runBlocking {
        val note = clientReturning("""{"error":"not found"}""", HttpStatusCode.NotFound)
            .getNote(config, "diary/2000-01-01").getOrThrow()
        assertNull(note)
    }

    @Test
    fun createNotePostsToNotesEndpoint() = runBlocking {
        val meta = clientReturning("""{"id":"diary/2026-09-20","version":"v1"}""")
            .createNote(config, "diary/2026-09-20", "---\n---\nbody").getOrThrow()
        assertEquals("diary/2026-09-20", meta.id)
        assertEquals(HttpMethod.Post, lastMethod)
        assertEquals("https://notes.osmosis.page/api/notes", lastUrl)
    }

    @Test
    fun putNotePutsToPathWithVersion() = runBlocking {
        val meta = clientReturning("""{"id":"diary/2026-09-20","version":"v2"}""")
            .putNote(config, "diary/2026-09-20", "---\n---\nbody", "v1").getOrThrow()
        assertEquals("v2", meta.version)
        assertEquals(HttpMethod.Put, lastMethod)
        assertEquals("https://notes.osmosis.page/api/notes/diary/2026-09-20", lastUrl)
    }

    @Test
    fun deleteNoteHitsPath() = runBlocking {
        val result = clientReturning("").deleteNote(config, "diary/2026-09-20")
        assertTrue(result.isSuccess)
        assertEquals(HttpMethod.Delete, lastMethod)
        assertEquals("https://notes.osmosis.page/api/notes/diary/2026-09-20", lastUrl)
    }

    @Test
    fun postStatHitsStatsEndpoint() = runBlocking {
        val result = clientReturning("""{"ok":true}""").postStat(config, "protein", 60.0, "0720", "2026-09-20")
        assertTrue(result.isSuccess)
        assertEquals("https://notes.osmosis.page/api/stats", lastUrl)
    }

    @Test
    fun httpErrorBecomesFailure() = runBlocking {
        val result = clientReturning("""{"error":"nope"}""", HttpStatusCode.Unauthorized)
            .getNote(config, "diary/2026-09-20")
        assertTrue(result.isFailure)
    }
}
