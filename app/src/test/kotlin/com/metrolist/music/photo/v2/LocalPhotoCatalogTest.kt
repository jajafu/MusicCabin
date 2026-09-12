package com.metrolist.music.photo.v2

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.metrolist.music.photo.*
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class LocalPhotoCatalogTest {
    @get:Rule val temporary = TemporaryFolder()
    private val preferences = MemoryPreferences()
    private val documents = Documents()
    private val first = Uri.parse("content://test/photo/first")
    private val second = Uri.parse("content://test/photo/second")
    private fun manifest(name: String) = PhotoFrameManifest(File(temporary.root, name))
    private fun local() = LocalPhotoCatalog({ preferences }, { documents }, { manifest("v2.json") })
    private fun original() = PhotoCatalog({ preferences }, { documents }, { manifest("original.json") })

    @Test fun `v2 photos and display settings survive reopening without altering original frame`() = runBlocking {
        val original = original()
        original.addPhotos(listOf(first))
        original.updateSettings(FrameSettings(intervalSeconds = 30, crop = false))
        val v2 = local()
        v2.initialize()
        assertTrue(v2.state.value.photos.isEmpty())
        v2.addPhotos(listOf(second, second))
        v2.updateSettings(FrameSettings(intervalSeconds = 5, showClock = false))
        val reopened = local().also { it.initialize() }
        assertEquals(listOf(second.toString()), reopened.state.value.photos.map { it.uri })
        assertEquals(5, reopened.state.value.settings.intervalSeconds)
        assertFalse(reopened.state.value.settings.showClock)
        reopened.clear()
        val old = original().also { it.initialize() }
        assertEquals(listOf(first.toString()), old.state.value.photos.map { it.uri })
        assertEquals(30, old.state.value.settings.intervalSeconds)
        assertFalse(old.state.value.settings.crop)
        assertTrue(local().also { it.initialize() }.state.value.photos.isEmpty())
    }

    @Test fun `revoked local photo retains selection and recovers after permission returns`() = runBlocking {
        local().addPhotos(listOf(first))
        documents.denied = true
        val denied = local().also { it.initialize() }
        assertTrue(denied.state.value.photos.isEmpty())
        assertEquals(first.toString(), denied.state.value.sources.single().uri)
        assertEquals(FrameError.PERMISSION, denied.state.value.error)
        documents.denied = false
        val restored = local().also { it.initialize() }
        assertEquals(listOf(first.toString()), restored.state.value.photos.map { it.uri })
    }

    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    private class Documents : FrameDocumentAccess {
        var denied = false
        override fun hasPersistedRead(uri: Uri) = !denied
        override fun persistRead(uri: Uri) = true
        override suspend fun picked(uri: Uri): FrameDocument {
            if (denied) throw SecurityException()
            return FrameDocument(uri.toString(), "Photo", "image/jpeg")
        }
        override suspend fun folder(uri: Uri): FrameDocument = error("No folder scan expected")
        override suspend fun children(treeUri: Uri, directoryUri: String): List<FrameDocument> = error("No folder scan expected")
    }
}
