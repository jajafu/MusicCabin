package com.metrolist.music.photo.v2.drive

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class DriveProbeControllerTest {
    @Test fun `selecting folder starts only after complete metadata and persists it for reconnect`() = runBlocking {
        val fixture = Fixture(this)
        fixture.connected()
        val folder = DriveFile("folder", "Photos", DriveFile.FOLDER_MIME_TYPE)
        fixture.api.folderResults = listOf(folder)
        fixture.controller.refreshFolders(); fixture.idle()
        fixture.controller.openFolder(folder); fixture.idle()
        val release = CompletableDeferred<Unit>()
        fixture.api.photosBlock = { release.await(); listOf(photo) }
        fixture.controller.useFolder()
        assertEquals(0, fixture.playbacks)
        release.complete(Unit); fixture.idle()
        assertEquals(1, fixture.playbacks)
        assertEquals(listOf(folder), fixture.settings.saved.selectedPath)
        fixture.controller.background()
        fixture.controller.connect(noUi); fixture.idle()
        assertEquals(listOf(folder), fixture.controller.state.value.path)
        assertEquals(1, fixture.playbacks)
        fixture.controller.requireReconnect()
        assertFalse(fixture.controller.state.value.connected)
        fixture.controller.connect(noUi); fixture.idle()
        assertEquals(listOf(folder), fixture.controller.state.value.path)
        fixture.controller.disconnect(); fixture.idle()
        assertNull(fixture.settings.saved.selectedPath)
    }

    @Test fun `initialization disabled build and local disable never initialize OAuth`() = runBlocking {
        val disabled = Fixture(this, available = false)
        disabled.settings.saved = DriveProbePreferences(enabled = true)
        disabled.ready()
        disabled.controller.setEnabled(true)
        disabled.idle()
        disabled.controller.connect(noUi)
        assertEquals(0, disabled.creations)
        assertFalse(disabled.controller.state.value.enabled)

        val enabled = Fixture(this)
        enabled.ready()
        assertEquals(0, enabled.creations)
        enabled.controller.setEnabled(false)
        enabled.idle()
        enabled.controller.connect(noUi)
        assertEquals(0, enabled.creations)
        assertFalse(enabled.settings.saved.enabled)
    }

    @Test fun `late authorization cannot bind account write preferences or create repository after disable`() = runBlocking {
        val fixture = Fixture(this)
        fixture.ready()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        fixture.auth.authorizeBlock = {
            started.complete(Unit)
            withContext(NonCancellable) { release.await() }
        }
        fixture.controller.connect(noUi)
        started.await()
        fixture.controller.setEnabled(false)
        fixture.idle()
        release.complete(Unit)
        yield()
        assertFalse(fixture.controller.state.value.connected)
        assertNull(fixture.settings.saved.account)
        assertFalse(fixture.settings.saved.enabled)
        assertEquals(0, fixture.repositories)
        assertTrue(fixture.auth.clears > 0)
    }

    @Test fun `late photo list after disconnect cannot restore photos account or preview`() = runBlocking {
        val fixture = Fixture(this)
        fixture.connected()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        fixture.api.photosBlock = {
            started.complete(Unit)
            withContext(NonCancellable) { release.await(); listOf(photo) }
        }
        fixture.controller.listPhotos()
        started.await()
        fixture.controller.disconnect()
        fixture.idle()
        release.complete(Unit)
        yield()
        assertNull(fixture.settings.saved.account)
        assertNull(fixture.controller.state.value.account)
        assertTrue(fixture.controller.state.value.photos.isEmpty())
        assertFalse(fixture.controller.state.value.connected)
    }

    @Test fun `token renewal verifies identity and preview is released on background`() = runBlocking {
        val fixture = Fixture(this)
        fixture.connected()
        fixture.controller.listPhotos()
        fixture.idle()
        fixture.controller.preview(photo)
        fixture.idle()
        assertEquals("decoded", fixture.controller.state.value.preview)
        fixture.controller.refreshToken()
        fixture.idle()
        assertEquals(1, fixture.auth.refreshes)
        assertEquals(DriveProbeStage.REFRESHED, fixture.controller.state.value.stage)
        fixture.controller.background()
        assertNull(fixture.controller.state.value.preview)
        assertFalse(fixture.controller.state.value.connected)
        assertEquals(account, fixture.settings.saved.account)
        assertTrue(fixture.api.closed)
    }

    @Test fun `unavailable OAuth and cancellation are visible without retry loops`() = runBlocking {
        for (failure in listOf(DriveFailure.UNAVAILABLE, DriveFailure.CANCELLED, DriveFailure.TIMEOUT)) {
            val fixture = Fixture(this)
            fixture.ready()
            fixture.auth.authorizeBlock = { throw DriveException(failure, 17) }
            fixture.controller.connect(noUi)
            fixture.idle()
            assertEquals(failure, fixture.controller.state.value.error?.failure)
            assertFalse(fixture.controller.state.value.busy)
            assertFalse(fixture.controller.state.value.connected)
            assertEquals(0, fixture.repositories)
            fixture.controller.stop()
        }
    }

    @Test fun `changed account after token renewal clears private session`() = runBlocking {
        val fixture = Fixture(this)
        fixture.connected()
        fixture.api.currentAccount = DriveAccount("different", "different@example.com")
        fixture.controller.refreshToken()
        fixture.idle()
        assertEquals(DriveFailure.REAUTHORIZE, fixture.controller.state.value.error?.failure)
        assertFalse(fixture.controller.state.value.connected)
        assertEquals(account, fixture.settings.saved.account)
    }

    @Test fun `failure on later page exposes no partial photos as a successful source`() = runBlocking {
        val fixture = Fixture(this)
        fixture.connected()
        fixture.api.photosBlock = { throw DriveException(DriveFailure.NETWORK) }
        fixture.controller.listPhotos()
        fixture.idle()
        assertTrue(fixture.controller.state.value.photos.isEmpty())
        assertFalse(fixture.controller.state.value.listed)
        assertEquals(DriveFailure.NETWORK, fixture.controller.state.value.error?.failure)
        fixture.controller.stop()
    }

    @Test fun `reentering resumes the saved album without browsing or selecting it again`() = runBlocking {
        val fixture = Fixture(this)
        val album = DriveFile("saved", "Saved album", DriveFile.FOLDER_MIME_TYPE)
        fixture.settings.saved = DriveProbePreferences(true, account, listOf(album))
        fixture.controller.initialize(); fixture.idle()
        fixture.controller.resumeSavedFolder(); fixture.idle()
        assertEquals(1, fixture.playbacks)
        assertEquals("saved", fixture.api.lastPhotoFolder)
        assertEquals(0, fixture.api.folderCalls)
        assertEquals(0, fixture.controller.state.value.selectionRevision)
        fixture.controller.background()
        fixture.controller.resumeSavedFolder(); fixture.idle()
        assertEquals(2, fixture.playbacks)
        assertEquals(listOf(album), fixture.settings.saved.selectedPath)
        fixture.controller.stop()
    }

    @Test fun `missing selection does not authorize and expired access waits for manual reconnect`() = runBlocking {
        val fixture = Fixture(this)
        fixture.ready()
        fixture.controller.resumeSavedFolder()
        assertEquals(0, fixture.creations)
        fixture.controller.stop()
        val expired = Fixture(this)
        expired.settings.saved = DriveProbePreferences(true, account, emptyList())
        expired.auth.authorizeBlock = { throw DriveException(DriveFailure.REAUTHORIZE) }
        expired.controller.initialize(); expired.idle()
        expired.controller.resumeSavedFolder(); expired.idle()
        yield()
        assertEquals(1, expired.creations)
        assertEquals(0, expired.playbacks)
        assertEquals(DriveFailure.REAUTHORIZE, expired.controller.state.value.error?.failure)
        assertNotNull(expired.settings.saved.selectedPath)
        expired.controller.stop()
    }

    private class Fixture(scope: CoroutineScope, available: Boolean = true) {
        val settings = Settings()
        val auth = Authorization()
        val api = Api()
        var creations = 0
        var repositories = 0
        var playbacks = 0
        val controller = DriveProbeController(
            scope, available, settings,
            DriveAuthorizationFactory { creations++; auth },
            { repositories++; api },
            { _, _, _ -> "decoded" },
            onFolderSelected = { _, _, _, _ -> playbacks++ },
        )
        suspend fun idle() { withTimeout(2_000) { controller.state.first { !it.busy } } }
        suspend fun ready() {
            controller.initialize(); idle()
            controller.setEnabled(true); idle()
        }
        suspend fun connected() {
            ready()
            controller.connect(noUi); idle()
            assertTrue(controller.state.value.connected)
        }
    }

    private class Settings : DriveProbeSettings {
        var saved = DriveProbePreferences()
        override suspend fun read() = saved
        override suspend fun write(value: DriveProbePreferences) { saved = value }
    }

    private class Authorization : DriveAuthorizationProvider {
        var authorizeBlock: suspend () -> Unit = {}
        var clears = 0
        var refreshes = 0
        override suspend fun authorize(accountEmail: String?, ui: DriveAuthorizationUi) = authorizeBlock()
        override fun bindAccount(email: String) = Unit
        override suspend fun accessToken(forceRefresh: Boolean): String {
            if (forceRefresh) refreshes++
            return "test"
        }
        override fun clear() { clears++ }
    }

    private class Api : DrivePhotoAccess {
        var closed = false
        var currentAccount = account
        var photosBlock: suspend () -> List<DriveFile> = { listOf(photo) }
        var folderResults = emptyList<DriveFile>()
        var lastPhotoFolder: String? = null
        var folderCalls = 0
        override suspend fun account() = currentAccount
        override suspend fun folders(parentId: String, progress: (Int) -> Unit): List<DriveFile> { folderCalls++; return folderResults }
        override suspend fun photos(parentId: String, progress: (Int) -> Unit): List<DriveFile> {
            lastPhotoFolder = parentId
            progress(100)
            return photosBlock()
        }
        override suspend fun download(photo: DriveFile, target: File) = error("No real downloads")
        override fun close() { closed = true }
    }

    companion object {
        private val account = DriveAccount("account", "test@example.com")
        private val photo = DriveFile("photo", "test.jpg", "image/jpeg", capabilities = DriveFile.Capabilities(true))
        private val noUi = DriveAuthorizationUi { error("Unexpected consent UI") }
    }
}
