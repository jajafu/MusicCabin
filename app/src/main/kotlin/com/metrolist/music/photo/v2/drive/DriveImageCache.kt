package com.metrolist.music.photo.v2.drive

import coil3.disk.DiskCache
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DriveCacheMissException : Exception()

/** Uses the app's existing cache instance; never opens another cache on its directory. */
class DriveImageCache(
    private val diskCache: suspend () -> DiskCache?,
    private val temporaryDirectory: File,
    private val validate: (File) -> Boolean,
) {
    suspend fun prefetch(account: DriveAccount, photo: DriveFile, api: DrivePhotoAccess) {
        if (diskCache() == null) return
        read(account, photo, api) { _, _ -> Unit }
    }

    suspend fun <T> read(
        account: DriveAccount,
        photo: DriveFile,
        api: DrivePhotoAccess,
        allowDownload: Boolean = true,
        consume: suspend (File, Boolean) -> T,
    ): T = withContext(Dispatchers.IO) {
        try {
            val cache = diskCache()
            val key = key(account, photo)
            // A ready slide must not wait behind an unrelated, slow prefetch download.
            cached(cache, key, consume)?.let { return@withContext it.value }
            if (!allowDownload) throw DriveCacheMissException()
            access.withLock {
                // The preloader may have finished this exact file while we waited.
                cached(cache, key, consume)?.let { return@withLock it.value }
                if (!temporaryDirectory.isDirectory && !temporaryDirectory.mkdirs()) {
                    throw DriveException(DriveFailure.STORAGE)
                }
                // Recover only this loader's temporary downloads left by process death.
                temporaryDirectory.listFiles()?.filter { it.name.startsWith("download-") }?.forEach { it.delete() }
                val file = File.createTempFile("download-", ".tmp", temporaryDirectory)
                try {
                    api.download(photo, file)
                    currentCoroutineContext().ensureActive()
                    if (!validate(file)) throw DriveException(DriveFailure.INVALID_IMAGE)
                    val editor = cache?.openEditor(key)
                    if (editor == null) return@withLock consume(file, false)
                    var committed = false
                    try {
                        file.inputStream().use { input -> editor.data.toFile().outputStream().use { input.copyTo(it) } }
                        editor.metadata.toFile().writeText("photo-frame-drive-v1")
                        currentCoroutineContext().ensureActive()
                        val saved = editor.commitAndOpenSnapshot()
                        committed = true
                        if (saved == null) return@withLock consume(file, false)
                        try {
                            saved.use { consume(it.data.toFile(), false) }
                        } catch (error: DriveException) {
                            if (error.failure == DriveFailure.INVALID_IMAGE) cache.remove(key)
                            throw error
                        }
                    } finally {
                        if (!committed) editor.abort()
                    }
                } finally {
                    file.delete()
                }
            }
        } catch (_: IOException) {
            throw DriveException(DriveFailure.STORAGE)
        }
    }

    private data class Cached<T>(val value: T)

    private suspend fun <T> cached(
        cache: DiskCache?,
        key: String,
        consume: suspend (File, Boolean) -> T,
    ): Cached<T>? {
        val snapshot = cache?.openSnapshot(key) ?: return null
        return try {
            snapshot.use { Cached(consume(it.data.toFile(), true)) }
        } catch (error: DriveException) {
            if (error.failure != DriveFailure.INVALID_IMAGE) throw error
            cache.remove(key)
            null
        }
    }

    companion object {
        // Serializes overlapping screen instances as well as foreground and speculative loads.
        private val access = Mutex()

        fun key(account: DriveAccount, photo: DriveFile): String = "photo-frame-drive-v1:" +
            Json.encodeToString(listOf(account.permissionId, photo.id, photo.version, photo.modifiedTime, photo.size?.toString()))
    }
}
