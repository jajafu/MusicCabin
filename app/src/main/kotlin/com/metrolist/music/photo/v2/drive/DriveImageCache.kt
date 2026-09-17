package com.metrolist.music.photo.v2.drive

import coil3.disk.DiskCache
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DriveCacheMissException : Exception()

/** Uses the app's existing cache instance; never opens another cache on its directory. */
class DriveImageCache(
    private val diskCache: suspend () -> DiskCache?,
    private val temporaryDirectory: File,
    private val indexFile: File? = null,
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
            cached(cache, key, consume)?.let {
                remember(account, photo)
                return@withContext it.value
            }
            if (!allowDownload) throw DriveCacheMissException()
            access.withLock {
                // The preloader may have finished this exact file while we waited.
                cached(cache, key, consume)?.let {
                    remember(account, photo)
                    return@withLock it.value
                }
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
                            saved.use { consume(it.data.toFile(), false) }.also { remember(account, photo) }
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

    /** Returns only intact entries for this account, so playback can begin without Drive or OAuth. */
    suspend fun cachedPhotos(account: DriveAccount, maximumCount: Int? = null): List<DriveFile> = withContext(Dispatchers.IO) {
        require(maximumCount == null || maximumCount > 0)
        val cache = diskCache() ?: return@withContext emptyList()
        indexAccess.withLock {
            val entries = readIndex()
            val unavailable = mutableSetOf<IndexedPhoto>()
            val available = mutableListOf<DriveFile>()
            for (entry in entries.filter { it.permissionId == account.permissionId }.shuffled()) {
                currentCoroutineContext().ensureActive()
                if (maximumCount != null && available.size >= maximumCount) break
                val snapshot = cache.openSnapshot(key(account, entry.photo))
                if (snapshot == null) {
                    unavailable += entry
                    continue
                }
                val valid = snapshot.use { validate(it.data.toFile()) }
                if (valid) {
                    available += entry.photo
                } else {
                    cache.remove(key(account, entry.photo))
                    unavailable += entry
                }
            }
            currentCoroutineContext().ensureActive()
            if (unavailable.isNotEmpty()) writeIndex(entries.filterNot { it in unavailable })
            available.distinctBy { it.id }
        }
    }

    /** Adds cache entries discovered from refreshed Drive metadata without downloading them. */
    suspend fun indexAvailable(account: DriveAccount, photos: List<DriveFile>) = withContext(Dispatchers.IO) {
        val cache = diskCache() ?: return@withContext
        indexAccess.withLock {
            val entries = readIndex().toMutableList()
            var changed = false
            photos.filter { it.canPreview }.distinctBy { it.id }.forEach { photo ->
                currentCoroutineContext().ensureActive()
                val snapshot = cache.openSnapshot(key(account, photo)) ?: return@forEach
                val valid = snapshot.use { validate(it.data.toFile()) }
                if (!valid) {
                    cache.remove(key(account, photo))
                    return@forEach
                }
                val indexed = IndexedPhoto(account.permissionId, photo)
                if (entries.any { it == indexed }) return@forEach
                entries.removeAll { it.permissionId == account.permissionId && it.photo.id == photo.id }
                entries += indexed
                changed = true
            }
            currentCoroutineContext().ensureActive()
            if (changed) writeIndex(entries)
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

    private suspend fun remember(account: DriveAccount, photo: DriveFile) {
        if (indexFile == null) return
        indexAccess.withLock {
            val entries = readIndex().toMutableList()
            if (entries.any { it.permissionId == account.permissionId && it.photo == photo }) return@withLock
            entries.removeAll { it.permissionId == account.permissionId && it.photo.id == photo.id }
            entries += IndexedPhoto(account.permissionId, photo)
            writeIndex(entries)
        }
    }

    private fun readIndex(): List<IndexedPhoto> {
        val file = indexFile ?: return emptyList()
        if (!file.isFile) return emptyList()
        return try {
            Json.decodeFromString(file.readText())
        } catch (_: IOException) {
            emptyList()
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    private fun writeIndex(entries: List<IndexedPhoto>) {
        val file = indexFile ?: return
        val parent = file.parentFile ?: throw IOException("Cache index has no parent")
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Cannot create cache index directory")
        val temporary = File.createTempFile(file.name, ".tmp", parent)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(Json.encodeToString(entries).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            temporary.delete()
        }
    }

    @Serializable
    private data class IndexedPhoto(val permissionId: String, val photo: DriveFile)

    companion object {
        // Serializes overlapping screen instances as well as foreground and speculative loads.
        private val access = Mutex()
        private val indexAccess = Mutex()

        fun key(account: DriveAccount, photo: DriveFile): String = "photo-frame-drive-v1:" +
            Json.encodeToString(listOf(account.permissionId, photo.id, photo.version, photo.modifiedTime, photo.size?.toString()))
    }
}
