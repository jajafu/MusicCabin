package com.metrolist.music.photo.v2.drive

import coil3.disk.DiskCache
import coil3.disk.directory
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DriveImageCacheTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun `repeated reads and reopening the disk cache reuse downloads`() = runBlocking {
        var disk = newDisk()
        try {
            val api = Api()
            val loader = loader(disk)
            assertFalse(loader.read(account, photo, api) { file, cached -> assertEquals("image", file.readText()); cached })
            val directory = disk.directory.toFile()
            disk.shutdown()
            disk = DiskCache.Builder().directory(directory).maxSizeBytes(1024 * 1024).build()
            val nextLoader = loader(disk)
            assertTrue(nextLoader.read(account, photo, api) { file, cached -> assertEquals("image", file.readText()); cached })
            assertEquals(1, api.downloads)
            assertTrue(disk.size > 0)
        } finally { disk.shutdown() }
    }

    @Test fun `account and remote version changes cannot reuse a different cached image`() = runBlocking {
        val disk = newDisk()
        try {
            val api = Api()
            val loader = loader(disk)
            loader.prefetch(account, photo, api)
            loader.prefetch(account, photo.copy(version = "2"), api)
            loader.prefetch(account.copy(permissionId = "another-account"), photo, api)
            assertEquals(3, api.downloads)
            assertNotEquals(DriveImageCache.key(account, photo), DriveImageCache.key(account, photo.copy(modifiedTime = "later")))
        } finally { disk.shutdown() }
    }

    @Test fun `prefetched photo is a cache hit and clearing the cache makes the next read download again`() = runBlocking {
        val disk = newDisk()
        try {
            val api = Api()
            val loader = loader(disk)
            loader.prefetch(account, photo, api)
            loader.read(account, photo, api) { _, cached -> assertTrue(cached) }
            assertEquals(1, api.downloads)
            disk.clear()
            loader.read(account, photo, api) { _, cached -> assertFalse(cached) }
            assertEquals(2, api.downloads)
        } finally { disk.shutdown() }
    }

    @Test fun `failed and cancelled downloads leave no committed entries or temporary files`() = runBlocking {
        val disk = newDisk()
        val temp = files.newFolder()
        try {
            val api = Api()
            val loader = DriveImageCache({ disk }, temp) { true }
            for (error in listOf(DriveException(DriveFailure.NETWORK), CancellationException())) {
                api.downloadBlock = { it.writeText("partial"); throw error }
                try {
                    loader.prefetch(account, photo, api)
                    fail("Expected failed download")
                } catch (actual: Exception) {
                    if (error is CancellationException) assertTrue(actual is CancellationException)
                    else assertSame(error, actual)
                }
                assertNull(disk.openSnapshot(DriveImageCache.key(account, photo)))
                assertTrue(temp.listFiles()!!.isEmpty())
            }
        } finally { disk.shutdown() }
    }

    @Test fun `invalid new images do not commit and a corrupt cached image downloads once for recovery`() = runBlocking {
        val disk = newDisk()
        try {
            val api = Api()
            val invalid = DriveImageCache({ disk }, files.newFolder()) { false }
            try {
                invalid.prefetch(account, photo, api)
                fail("Expected image validation failure")
            } catch (error: DriveException) { assertEquals(DriveFailure.INVALID_IMAGE, error.failure) }
            assertNull(disk.openSnapshot(DriveImageCache.key(account, photo)))
            val loader = loader(disk)
            loader.prefetch(account, photo, api)
            loader.read(account, photo, api) { _, cached ->
                if (cached) throw DriveException(DriveFailure.INVALID_IMAGE)
            }
            assertEquals(3, api.downloads)
        } finally { disk.shutdown() }
    }

    @Test fun `disabled cache skips prefetch and removes foreground temporary files`() = runBlocking {
        val api = Api()
        val temp = files.newFolder()
        val loader = DriveImageCache({ null }, temp) { true }
        loader.prefetch(account, photo, api)
        assertEquals(0, api.downloads)
        repeat(2) { loader.read(account, photo, api) { file, cached -> assertTrue(file.exists()); assertFalse(cached) } }
        assertEquals(2, api.downloads)
        assertTrue(temp.listFiles()!!.isEmpty())
    }

    @Test fun `cache-only fallback never invokes Drive for missing photos`() = runBlocking {
        val disk = newDisk()
        try {
            val api = Api()
            val loader = loader(disk)
            loader.prefetch(account, photo, api)
            loader.read(account, photo, api, allowDownload = false) { _, cached -> assertTrue(cached) }
            try {
                loader.read(account, photo.copy(id = "missing"), api, allowDownload = false) { _, _ -> }
                fail("Expected cache miss")
            } catch (_: DriveCacheMissException) { }
            assertEquals(1, api.downloads)
        } finally { disk.shutdown() }
    }

    @Test fun `cached slide does not wait for unrelated prefetch and pending download is shared`() = runBlocking {
        val disk = newDisk()
        val release = CompletableDeferred<Unit>()
        try {
            val api = Api()
            val loader = loader(disk)
            loader.prefetch(account, photo, api)
            val started = CompletableDeferred<Unit>()
            api.downloadBlock = { file -> started.complete(Unit); release.await(); file.writeText("image") }
            val nextPhoto = photo.copy(id = "next")
            val prefetch = async { loader.prefetch(account, nextPhoto, api) }
            started.await()
            withTimeout(2_000) {
                loader.read(account, photo, api) { _, cached -> assertTrue(cached) }
            }
            val foreground = async { loader.read(account, nextPhoto, api) { _, cached -> cached } }
            release.complete(Unit)
            prefetch.await()
            assertTrue(foreground.await())
            assertEquals(2, api.downloads)
        } finally {
            release.complete(Unit)
            disk.shutdown()
        }
    }

    private fun newDisk() = DiskCache.Builder().directory(files.newFolder()).maxSizeBytes(1024 * 1024).build()
    private fun loader(disk: DiskCache) = DriveImageCache({ disk }, files.newFolder()) { it.readText() == "image" }

    private class Api : DrivePhotoAccess {
        var downloads = 0
        var downloadBlock: suspend (File) -> Unit = { it.writeText("image") }
        override suspend fun account() = account
        override suspend fun folders(parentId: String, progress: (Int) -> Unit) = emptyList<DriveFile>()
        override suspend fun photos(parentId: String, progress: (Int) -> Unit) = listOf(photo)
        override suspend fun download(photo: DriveFile, target: File): Long {
            downloads++
            downloadBlock(target)
            return target.length()
        }
        override fun close() = Unit
    }

    companion object {
        private val account = DriveAccount("account", "test@example.com")
        private val photo = DriveFile("photo", "photo.jpg", "image/jpeg", version = "1", capabilities = DriveFile.Capabilities(true))
    }
}
