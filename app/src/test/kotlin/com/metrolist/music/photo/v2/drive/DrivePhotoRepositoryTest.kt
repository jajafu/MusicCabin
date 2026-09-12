package com.metrolist.music.photo.v2.drive

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DrivePhotoRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val photo = DriveFile("photo_1", "image.jpg", "image/jpeg", capabilities = DriveFile.Capabilities(true))

    @Test fun `listing follows empty pages and deduplicates IDs without broadening the selected folder`() = runBlocking {
        val auth = FakeAuthorization()
        val pages = mutableListOf<String?>()
        val counts = mutableListOf<Int>()
        repository(auth) { request ->
            assertEquals("'folder_1' in parents and trashed = false and mimeType contains 'image/'", request.url.parameters["q"])
            assertEquals("user", request.url.parameters["corpora"])
            assertEquals("100", request.url.parameters["pageSize"])
            val page = request.url.parameters["pageToken"]
            pages += page
            respond(when (page) {
                null -> """{"files":[{"id":"a","name":"old","mimeType":"image/jpeg"}],"nextPageToken":"page +2"}"""
                "page +2" -> """{"files":[],"nextPageToken":"third"}"""
                else -> """{"files":[{"id":"a","name":"new","mimeType":"image/jpeg","size":"123"},{"id":"b","name":"b","mimeType":"image/png"}]}"""
            })
        }.use { api ->
            val photos = api.photos("folder_1") { counts += it }
            assertEquals(listOf("a", "b"), photos.map { it.id })
            assertEquals("new", photos.first().name)
            assertEquals(123L, photos.first().size)
            assertEquals(listOf(null, "page +2", "third"), pages)
            assertEquals(listOf(1, 1, 2), counts)
        }
    }

    @Test fun `repeated page token and incomplete search never report a complete list`() = runBlocking {
        for (body in listOf("""{"files":[],"nextPageToken":"repeat"}""", """{"files":[],"incompleteSearch":true}""")) {
            var requests = 0
            repository { requests++; respond(body) }.use { api ->
                fails(DriveFailure.INVALID_RESPONSE) { api.photos("folder") }
                assertTrue(requests <= 2)
            }
        }
    }

    @Test fun `401 refreshes only once and does not expose tokens in errors`() = runBlocking {
        val auth = FakeAuthorization()
        var calls = 0
        repository(auth) { request ->
            calls++
            assertEquals("Bearer ${if (calls == 1) "old-token" else "new-token"}", request.headers[HttpHeaders.Authorization])
            respond("{}", HttpStatusCode.Unauthorized)
        }.use { api ->
            val error = fails(DriveFailure.REAUTHORIZE) { api.account() }
            assertEquals(401, error.code)
            assertEquals(2, calls)
            assertEquals(1, auth.refreshes)
            assertFalse(error.toString().contains("token"))
        }
    }

    @Test fun `successful token retry resumes the request`() = runBlocking {
        val auth = FakeAuthorization()
        var calls = 0
        repository(auth) {
            calls++
            if (calls == 1) respond("{}", HttpStatusCode.Unauthorized)
            else respond("""{"user":{"permissionId":"account","emailAddress":"test@example.com"}}""")
        }.use { api ->
            assertEquals("account", api.account().permissionId)
            assertEquals(1, auth.refreshes)
        }
    }

    @Test fun `rate limit honors bounded retry after and stops after two retries`() = runBlocking {
        var calls = 0
        val waits = mutableListOf<Long>()
        repository(backoff = { waits += it }) {
            calls++
            respond("{}", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "999"))
        }.use { api ->
            fails(DriveFailure.RATE_LIMITED) { api.photos("folder") }
            assertEquals(3, calls)
            assertEquals(listOf(30_000L, 30_000L), waits)
        }
    }

    @Test fun `permission missing file and disabled API are distinguished without retry`() = runBlocking {
        val cases = listOf(
            Triple(HttpStatusCode.Forbidden, "insufficientPermissions", DriveFailure.PERMISSION),
            Triple(HttpStatusCode.Forbidden, "accessNotConfigured", DriveFailure.CONFIGURATION),
            Triple(HttpStatusCode.NotFound, "notFound", DriveFailure.NOT_FOUND),
        )
        for ((status, reason, failure) in cases) {
            var calls = 0
            repository { calls++; respond("""{"error":{"errors":[{"reason":"$reason"}]}}""", status) }.use { api ->
                fails(failure) { api.account() }
                assertEquals(1, calls)
            }
        }
    }

    @Test fun `cancelling backoff stops retries`() = runBlocking {
        var calls = 0
        val waiting = CompletableDeferred<Unit>()
        repository(backoff = { waiting.complete(Unit); awaitCancellation() }) {
            calls++
            respond("{}", HttpStatusCode.ServiceUnavailable)
        }.use { api ->
            val job = launch { api.photos("folder") }
            waiting.await()
            job.cancelAndJoin()
            assertEquals(1, calls)
        }
    }

    @Test fun `forbidden and oversized photos never start a download`(): Unit = runBlocking {
        repository { error("Unexpected download") }.use { api ->
            fails(DriveFailure.DOWNLOAD_FORBIDDEN) { api.download(photo.copy(capabilities = DriveFile.Capabilities()), temporary.newFile()) }
            fails(DriveFailure.TOO_LARGE) { api.download(photo.copy(size = DriveFile.MAX_DOWNLOAD_BYTES + 1), temporary.newFile()) }
            fails(DriveFailure.INVALID_IMAGE) { api.download(photo.copy(mimeType = "image/heic"), temporary.newFile()) }
        }
    }

    @Test fun `oversized stream without content length deletes its partial file`() = runBlocking {
        val target = temporary.newFile()
        repository { respond(ByteArray(DriveFile.MAX_DOWNLOAD_BYTES.toInt() + 1)) }.use { api ->
            fails(DriveFailure.TOO_LARGE) { api.download(photo, target) }
            assertFalse(target.exists())
        }
    }

    @Test fun `download uses alt media only for the selected file`() = runBlocking {
        val target = temporary.newFile()
        repository { request ->
            assertEquals("/drive/v3/files/photo_1", request.url.encodedPath)
            assertEquals("media", request.url.parameters["alt"])
            respond(byteArrayOf(1, 2, 3))
        }.use { api ->
            assertEquals(3L, api.download(photo, target))
            assertArrayEquals(byteArrayOf(1, 2, 3), target.readBytes())
        }
    }

    @Test fun `invalid IDs and malformed responses do not become empty successful lists`(): Unit = runBlocking {
        repository { error("Unexpected request") }.use { api ->
            fails(DriveFailure.INVALID_RESPONSE) { api.photos("folder' or trashed=true") }
        }
        repository { respond("{}") }.use { api ->
            fails(DriveFailure.INVALID_RESPONSE) { api.photos("folder") }
        }
    }

    private fun repository(
        auth: FakeAuthorization = FakeAuthorization(),
        backoff: suspend (Long) -> Unit = {},
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): DrivePhotoRepository = DrivePhotoRepository(auth, HttpClient(MockEngine { request ->
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("https", request.url.protocol.name)
        assertEquals("www.googleapis.com", request.url.host)
        assertFalse(request.url.toString().contains("token"))
        handler(request)
    }) { followRedirects = false }, backoff)

    private suspend fun fails(failure: DriveFailure, block: suspend () -> Any): DriveException {
        try { block(); throw AssertionError("Expected $failure") } catch (error: DriveException) {
            assertEquals(failure, error.failure)
            return error
        }
    }

    private class FakeAuthorization : DriveAuthorizationProvider {
        var refreshes = 0
        override suspend fun authorize(accountEmail: String?, ui: DriveAuthorizationUi) = Unit
        override fun bindAccount(email: String) = Unit
        override suspend fun accessToken(forceRefresh: Boolean): String {
            if (forceRefresh) refreshes++
            return if (refreshes == 0) "old-token" else "new-token"
        }
        override fun clear() = Unit
    }
}
