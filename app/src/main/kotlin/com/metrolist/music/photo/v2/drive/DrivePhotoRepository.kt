package com.metrolist.music.photo.v2.drive

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
data class DriveAccount(val permissionId: String, val emailAddress: String)

@Serializable
data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long? = null,
    val modifiedTime: String? = null,
    val version: String? = null,
    val capabilities: Capabilities = Capabilities(),
) {
    @Serializable
    data class Capabilities(val canDownload: Boolean = false)

    val canPreview: Boolean
        get() = capabilities.canDownload && mimeType in PREVIEW_MIME_TYPES && (size == null || size in 1..MAX_DOWNLOAD_BYTES)

    companion object {
        const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        const val MAX_DOWNLOAD_BYTES = 20L * 1024 * 1024
        val PREVIEW_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}

interface DrivePhotoAccess : AutoCloseable {
    suspend fun account(): DriveAccount
    suspend fun folders(parentId: String, progress: (Int) -> Unit = {}): List<DriveFile>
    suspend fun photos(parentId: String, progress: (Int) -> Unit = {}): List<DriveFile>
    suspend fun download(photo: DriveFile, target: File): Long
}

/** Dedicated, GET-only client. Never installs authorization on the app's music or Coil clients. */
class DrivePhotoRepository(
    private val authorization: DriveAuthorizationProvider,
    private val client: HttpClient = newClient(),
    private val backoff: suspend (Long) -> Unit = { delay(it) },
) : DrivePhotoAccess {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun account(): DriveAccount {
        val about = metadata<About>("about", mapOf("fields" to "user(permissionId,emailAddress)"))
        if (about.user.permissionId.isBlank() || about.user.emailAddress.isBlank()) invalidResponse()
        return about.user
    }

    override suspend fun folders(parentId: String, progress: (Int) -> Unit): List<DriveFile> =
        list(parentId, "mimeType = '${DriveFile.FOLDER_MIME_TYPE}'", progress)

    override suspend fun photos(parentId: String, progress: (Int) -> Unit): List<DriveFile> =
        list(parentId, "mimeType contains 'image/'", progress)

    private suspend fun list(parentId: String, filter: String, progress: (Int) -> Unit): List<DriveFile> {
        requireId(parentId)
        val result = linkedMapOf<String, DriveFile>()
        val visited = hashSetOf<String>()
        var next: String? = null
        do {
            currentCoroutineContext().ensureActive()
            val page = metadata<FilePage>("files", buildMap {
                put("q", "'$parentId' in parents and trashed = false and $filter")
                put("spaces", "drive")
                put("corpora", "user")
                put("pageSize", "100")
                put("orderBy", "name_natural")
                put("fields", "nextPageToken,incompleteSearch,files(id,name,mimeType,size,modifiedTime,version,capabilities(canDownload))")
                next?.let { put("pageToken", it) }
            })
            if (page.incompleteSearch) invalidResponse()
            page.files.forEach {
                requireId(it.id)
                result[it.id] = it
            }
            progress(result.size)
            next = page.nextPageToken?.takeIf { it.isNotBlank() }
            if (next != null && !visited.add(next)) invalidResponse()
        } while (next != null)
        return result.values.toList()
    }

    /** The caller owns a temporary file. Partial downloads are deleted before returning an error. */
    override suspend fun download(photo: DriveFile, target: File): Long = withContext(Dispatchers.IO) {
        try {
            requireId(photo.id)
            if (!photo.capabilities.canDownload) throw DriveException(DriveFailure.DOWNLOAD_FORBIDDEN)
            if (photo.mimeType !in DriveFile.PREVIEW_MIME_TYPES) throw DriveException(DriveFailure.INVALID_IMAGE)
            if (photo.size != null && photo.size !in 1..DriveFile.MAX_DOWNLOAD_BYTES) throw DriveException(DriveFailure.TOO_LARGE)
            request("files/${photo.id}", mapOf("alt" to "media")) { response ->
                val output = try { target.outputStream() } catch (_: IOException) { throw DriveException(DriveFailure.STORAGE) }
                val bytes = output.use { response.copyBounded(it, DriveFile.MAX_DOWNLOAD_BYTES) }
                if (bytes == 0L) throw DriveException(DriveFailure.INVALID_IMAGE)
                bytes
            }
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    private suspend inline fun <reified T> metadata(path: String, params: Map<String, String>): T =
        request(path, params) { response ->
            val data = ByteArrayOutputStream()
            response.copyBounded(data, 2L * 1024 * 1024)
            try {
                json.decodeFromString<T>(data.toString(Charsets.UTF_8.name()))
            } catch (_: SerializationException) {
                invalidResponse()
            } catch (_: IllegalArgumentException) {
                invalidResponse()
            }
        }

    private suspend fun <T> request(path: String, params: Map<String, String>, consume: suspend (HttpResponse) -> T): T {
        var refreshed = false
        var retry = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val token = authorization.accessToken()
            try {
                return client.prepareGet("https://www.googleapis.com/drive/v3/$path") {
                    url { params.forEach { (key, value) -> parameters.append(key, value) } }
                    header(HttpHeaders.Authorization, "Bearer $token")
                }.execute { response ->
                    if (response.status.value in 200..299) return@execute consume(response)
                    val code = response.status.value
                    val errorBody = ByteArrayOutputStream()
                    response.copyBounded(errorBody, 64 * 1024L)
                    val reasons = runCatching {
                        json.decodeFromString<ErrorBody>(errorBody.toString(Charsets.UTF_8.name()))
                            .error.errors.map { it.reason }.toSet()
                    }.getOrDefault(emptySet())
                    val failure = when {
                        code == 401 -> DriveFailure.REAUTHORIZE
                        code == 429 || reasons.any { it in RATE_LIMIT_REASONS } -> DriveFailure.RATE_LIMITED
                        code == 403 && "accessNotConfigured" in reasons -> DriveFailure.CONFIGURATION
                        code == 403 && "fileNotDownloadable" in reasons -> DriveFailure.DOWNLOAD_FORBIDDEN
                        code == 403 -> DriveFailure.PERMISSION
                        code == 404 -> DriveFailure.NOT_FOUND
                        code in 500..599 -> DriveFailure.SERVER
                        else -> DriveFailure.INVALID_RESPONSE
                    }
                    val waitMs = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()?.coerceIn(0, 30)?.times(1000)
                    throw RequestFailure(failure, code, waitMs)
                }
            } catch (error: RequestFailure) {
                if (error.code == 401 && !refreshed) {
                    refreshed = true
                    authorization.accessToken(forceRefresh = true)
                    continue
                }
                if (error.failure in setOf(DriveFailure.RATE_LIMITED, DriveFailure.SERVER) && retry < 2) {
                    backoff(error.waitMs ?: (1000L shl retry))
                    retry++
                    continue
                }
                throw DriveException(error.failure, error.code)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: DriveException) {
                throw error
            } catch (_: IOException) {
                throw DriveException(DriveFailure.NETWORK)
            }
        }
    }

    private suspend fun HttpResponse.copyBounded(output: OutputStream, limit: Long): Long {
        if ((contentLength() ?: 0) > limit) throw DriveException(DriveFailure.TOO_LARGE)
        val channel = bodyAsChannel()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = channel.readAvailable(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw DriveException(DriveFailure.TOO_LARGE)
            try { output.write(buffer, 0, count) } catch (_: IOException) { throw DriveException(DriveFailure.STORAGE) }
        }
        return total
    }

    override fun close() = client.close()

    @Serializable private data class About(val user: DriveAccount)
    @Serializable private data class FilePage(
        val files: List<DriveFile>,
        val nextPageToken: String? = null,
        val incompleteSearch: Boolean = false,
    )
    @Serializable private data class ErrorBody(val error: ErrorDetail)
    @Serializable private data class ErrorDetail(val errors: List<ErrorReason> = emptyList())
    @Serializable private data class ErrorReason(val reason: String)
    private class RequestFailure(val failure: DriveFailure, val code: Int, val waitMs: Long?) : Exception()

    companion object {
        private val RATE_LIMIT_REASONS = setOf("rateLimitExceeded", "userRateLimitExceeded", "sharingRateLimitExceeded")
        private fun requireId(id: String) {
            if (!id.matches(Regex("[a-zA-Z0-9_-]{1,256}"))) invalidResponse()
        }
        private fun invalidResponse(): Nothing = throw DriveException(DriveFailure.INVALID_RESPONSE)
        private fun newClient() = HttpClient(CIO) {
            expectSuccess = false
            followRedirects = false
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 20_000
            }
        }
    }
}
