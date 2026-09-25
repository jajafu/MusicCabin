package com.metrolist.music.photo

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class PhotoCatalogTest {
    @get:Rule val temporary = TemporaryFolder()
    private val preferences = MemoryPreferences()
    private val documents = FakeDocuments()
    private val folder = Uri.parse("content://test/tree/folder")
    private val image = Uri.parse("content://test/photo/one")

    @Test
    fun `construction does not access preferences provider or disk`() {
        val catalog = PhotoCatalog(
            { throw AssertionError("Preferences were accessed before entry") },
            { throw AssertionError("Provider was accessed before entry") },
            { throw AssertionError("Manifest was accessed before entry") },
        )
        assertFalse(catalog.state.value.initialized)
        assertTrue(catalog.state.value.photos.isEmpty())
    }

    @Test
    fun `picked photos append deduplicate and retain transient access until revoked`() = runBlocking {
        documents.persistable = false
        val catalog = catalog()
        catalog.addPhotos(listOf(image, image))
        catalog.addPhotos(listOf(Uri.parse("content://test/photo/two")))
        assertEquals(2, catalog.state.value.photos.size)

        documents.readError = SecurityException("Grant revoked")
        val reopened = catalog()
        reopened.initialize()
        assertEquals(2, reopened.state.value.sources.size)
        assertTrue(reopened.state.value.sources.all { it.needsPermission })
        assertTrue(reopened.state.value.photos.isEmpty())
    }

    @Test
    fun `TV migration adds prior frame sources without replacing frame one selections`() = runBlocking {
        val catalog = catalog()
        catalog.addPhotos(listOf(image))
        val importedUri = "content://test/photo/from-old-tv-frame"
        val previous = FrameSource(importedUri, "received", FrameSelectionType.PICKED_PHOTO)

        catalog.importSources(listOf(previous, previous))
        catalog.importSources(listOf(previous))

        assertEquals(setOf(image.toString(), importedUri), catalog.state.value.sources.map { it.uri }.toSet())
        assertEquals(2, catalog.state.value.photos.size)
        catalog.removeSources(setOf(importedUri))
        assertEquals(listOf(image.toString()), catalog.state.value.sources.map { it.uri })
    }

    @Test
    fun `revoked folder grant retains source choice and avoids scan`() = runBlocking {
        val catalog = catalogWithLegacyFolder()
        documents.granted.clear()
        documents.scanCalls = 0

        val reopened = catalog()
        reopened.initialize()
        assertTrue(reopened.state.value.sources.single().needsPermission)
        assertTrue(reopened.state.value.photos.isEmpty())
        assertEquals(0, documents.scanCalls)
        assertEquals(FrameError.PERMISSION, reopened.state.value.error)
    }

    @Test
    fun `failed rescan preserves last complete manifest and can recover`() = runBlocking {
        val catalog = catalogWithLegacyFolder()
        val previous = manifest().read().manifest
        documents.scanError = FileNotFoundException("USB removed")
        catalog.rescan()
        assertEquals(previous, manifest().read().manifest)
        assertFalse(catalog.state.value.scanning)
        assertTrue(catalog.state.value.sources.single().unavailable)

        documents.scanError = null
        catalog.rescan()
        assertEquals(1, catalog.state.value.photos.size)
        assertFalse(catalog.state.value.sources.single().unavailable)
    }

    @Test
    fun `cancelled rescan keeps prior index and always clears scanning state`() = runBlocking {
        val catalog = catalogWithLegacyFolder()
        val previous = manifest().read().manifest
        documents.scanError = CancellationException("cancelled")
        try {
            catalog.rescan()
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(previous, manifest().read().manifest)
            assertFalse(catalog.state.value.scanning)
        }
    }

    @Test
    fun `failed image preserves playback snapshot and source removal does not revoke grants`() = runBlocking {
        val catalog = catalogWithLegacyFolder()
        val photos = catalog.state.value.photos
        catalog.markUnreadable(photos.single().uri)
        assertEquals(photos, catalog.state.value.photos)
        assertTrue(catalog.state.value.sources.single().unavailable)
        assertFalse(catalog.state.value.sources.single().needsPermission)

        catalog.removeSource(folder.toString())
        assertTrue(catalog.state.value.photos.isEmpty())
        assertTrue(documents.hasPersistedRead(folder))
        assertTrue(manifest().read().manifest.photos.isEmpty())
    }

    @Test
    fun `single corrupt image does not disable folder but unplugged root does`() = runBlocking {
        documents.childCount = 3
        val catalog = catalogWithLegacyFolder()
        val photos = catalog.state.value.photos
        catalog.markUnreadable(photos[0].uri)
        assertFalse(catalog.state.value.sources.single().unavailable)
        assertEquals(1, catalog.state.value.sources.single().unreadableCount)

        documents.readError = FileNotFoundException("USB unplugged")
        catalog.markUnreadable(photos[1].uri)
        assertTrue(catalog.state.value.sources.single().unavailable)
        assertEquals(photos, catalog.state.value.photos)

        documents.readError = null
        catalog.rescan()
        assertFalse(catalog.state.value.sources.single().unavailable)
        assertEquals(photos, catalog.state.value.photos)
    }

    private fun manifest() = PhotoFrameManifest(File(temporary.root, "index.json"))
    private fun catalog() = PhotoCatalog({ preferences }, { documents }, ::manifest)

    private suspend fun catalogWithLegacyFolder(): PhotoCatalog {
        documents.granted.add(folder)
        preferences.updateData {
            mutablePreferencesOf(
                stringPreferencesKey("photo_frame_sources_v1") to Json.encodeToString(
                    listOf(FrameSource(folder.toString(), "folder", FrameSelectionType.FOLDER)),
                ),
            )
        }
        return catalog().also {
            it.initialize()
            it.rescan()
        }
    }

    private class MemoryPreferences : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private class FakeDocuments : FrameDocumentAccess {
        val granted = hashSetOf<Uri>()
        var persistable = true
        var readError: Exception? = null
        var scanError: Exception? = null
        var scanCalls = 0
        var childCount = 1

        override fun hasPersistedRead(uri: Uri) = uri in granted
        override fun persistRead(uri: Uri): Boolean {
            if (persistable) granted.add(uri)
            return persistable
        }

        override suspend fun picked(uri: Uri): FrameDocument {
            readError?.let { throw it }
            return FrameDocument(uri.toString(), "photo", "image/jpeg")
        }

        override suspend fun folder(uri: Uri): FrameDocument {
            readError?.let { throw it }
            return FrameDocument("content://test/tree/folder/document/folder", "folder", "vnd.android.document/directory")
        }

        override suspend fun children(treeUri: Uri, directoryUri: String): List<FrameDocument> {
            scanCalls++
            scanError?.let { throw it }
            return List(childCount) { FrameDocument("content://test/tree/folder/document/$it", "photo", "image/jpeg") }
        }
    }
}
