package com.metrolist.music.photo.v2.drive

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class DriveSlideshowControllerTest {
    @Test fun `folder starts automatically and each random round has every photo without consecutive repeats`() = runBlocking {
        val fixture = Fixture(this)
        fixture.start(photos)
        fixture.loaded()
        val firstRound = mutableListOf(fixture.state.photo!!.id)
        fixture.controller.togglePlaying()
        fixture.controller.togglePlaying()
        repeat(photos.size - 1) {
            fixture.controller.next(); fixture.loaded()
            firstRound += fixture.state.photo!!.id
        }
        assertEquals(photos.map { it.id }.toSet(), firstRound.toSet())
        val previous = fixture.state.photo!!.id
        fixture.controller.next(); fixture.loaded()
        assertNotEquals(previous, fixture.state.photo!!.id)
        fixture.controller.stop()
    }

    @Test fun `prefetch keeps compressed files ahead without changing the displayed photo`() = runBlocking {
        val fixture = Fixture(this)
        fixture.start(photos)
        fixture.loaded()
        yield()
        assertEquals(1, fixture.loads.size)
        assertEquals(3, fixture.prefetches.distinct().size)
        assertFalse(fixture.state.photo!!.id in fixture.prefetches)
        assertEquals(fixture.loads.single(), fixture.state.photo!!.id)
        fixture.controller.stop()
    }

    @Test fun `timer advances with selected interval while pause prevents automatic work`() = runBlocking {
        val ticks = Channel<Unit>(Channel.UNLIMITED)
        val waits = mutableListOf<Long>()
        val fixture = Fixture(this, waitInterval = { waits += it; ticks.receive() })
        fixture.start(photos); fixture.loaded(); yield()
        val first = fixture.state.photo!!.id
        assertEquals(10_000L, waits.last())
        ticks.send(Unit)
        withTimeout(2_000) { fixture.controller.state.first { it.photo?.id != first && !it.loading } }
        fixture.controller.setInterval(30); yield()
        assertEquals(30_000L, waits.last())
        fixture.controller.togglePlaying()
        val count = fixture.loads.size
        ticks.send(Unit); yield()
        assertEquals(count, fixture.loads.size)
        fixture.controller.next(); fixture.loaded()
        assertFalse(fixture.state.playing)
        fixture.controller.stop()
    }

    @Test fun `a slow next photo preserves the displayed image`() = runBlocking {
        val fixture = Fixture(this)
        fixture.start(photos); fixture.loaded()
        val first = fixture.state.current
        val release = CompletableDeferred<Unit>()
        fixture.loadBlock = { release.await(); DriveSlide(it.id, false) }
        fixture.controller.next()
        assertTrue(fixture.state.loading)
        assertEquals(first, fixture.state.current)
        release.complete(Unit); fixture.loaded()
        assertNotEquals(first, fixture.state.current)
        fixture.controller.stop()
    }

    @Test fun `unsupported and corrupt photos are skipped and all failures stop without a retry loop`() = runBlocking {
        val fixture = Fixture(this)
        fixture.loadBlock = { throw DriveException(DriveFailure.INVALID_IMAGE) }
        fixture.start(photos + photos.first().copy(id = "unsupported", mimeType = "image/heic"))
        fixture.loaded()
        assertFalse(fixture.state.playing)
        assertEquals(5, fixture.state.skippedCount)
        assertEquals(photos.size, fixture.loads.size)
        assertNull(fixture.state.current)
        fixture.controller.stop()
    }

    @Test fun `missing downloads skip to cached photos and retain the last successful image`() = runBlocking {
        val fixture = Fixture(this)
        fixture.loadBlock = {
            if (it.id == "photo-1") DriveSlide(it.id, true) else throw DriveException(DriveFailure.NETWORK)
        }
        fixture.start(photos); fixture.loaded()
        assertEquals("photo-1", fixture.state.photo?.id)
        assertTrue(fixture.state.current!!.fromCache)
        repeat(4) { fixture.controller.next(); fixture.loaded() }
        assertEquals("photo-1", fixture.state.photo?.id)
        assertTrue(fixture.downloadPermissions.drop(1).any { !it })
        val firstCacheOnly = fixture.downloadPermissions.indexOf(false)
        assertTrue(fixture.downloadPermissions.drop(firstCacheOnly).none { it })
        fixture.controller.stop()
    }

    @Test fun `late download cannot replace a new folder or restore a stopped session`() = runBlocking {
        val fixture = Fixture(this)
        val release = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        fixture.loadBlock = {
            started.complete(Unit)
            withContext(NonCancellable) { release.await() }
            DriveSlide(it.id, false)
        }
        fixture.start(photos); started.await()
        fixture.loadBlock = { DriveSlide(it.id, true) }
        fixture.start(listOf(photos.first().copy(id = "new"))); fixture.loaded()
        release.complete(Unit); yield()
        assertEquals("new", fixture.state.photo?.id)
        fixture.controller.stop(); yield()
        assertFalse(fixture.state.active)
        assertNull(fixture.state.current)
    }

    @Test fun `authorization failure enables reconnect while cached photos remain playable`() = runBlocking {
        val fixture = Fixture(this)
        var first = true
        fixture.loadBlock = {
            if (first) {
                first = false
                throw DriveException(DriveFailure.REAUTHORIZE)
            }
            DriveSlide(it.id, true)
        }
        fixture.start(photos); fixture.loaded()
        assertEquals(1, fixture.reconnects)
        assertTrue(fixture.state.active)
        assertTrue(fixture.state.current!!.fromCache)
        assertEquals(DriveFailure.REAUTHORIZE, fixture.state.error?.failure)
        fixture.controller.stop()
    }

    @Test fun `empty folder finishes and a single photo does not repeatedly load or prefetch itself`() = runBlocking {
        val fixture = Fixture(this)
        fixture.start(emptyList()); fixture.loaded()
        assertFalse(fixture.state.playing)
        fixture.start(listOf(photos.first())); fixture.loaded(); yield()
        assertEquals(1, fixture.loads.size)
        assertTrue(fixture.prefetches.isEmpty())
        fixture.controller.stop()
    }

    @Test fun `previous follows display history and next returns forward before resuming shuffle`() = runBlocking {
        val fixture = Fixture(this)
        fixture.start(photos); fixture.loaded()
        val first = fixture.state.photo
        assertFalse(fixture.state.canPrevious)
        fixture.controller.next(); fixture.loaded()
        val second = fixture.state.photo
        assertTrue(fixture.state.canPrevious)
        fixture.controller.previous(); fixture.loaded()
        assertEquals(first, fixture.state.photo)
        assertFalse(fixture.state.canPrevious)
        fixture.controller.next(); fixture.loaded()
        assertEquals(second, fixture.state.photo)
        fixture.controller.stop()
    }

    @Test fun `settings suspend loading and preserve the current photo and interrupted next photo`() = runBlocking {
        val fixture = Fixture(this)
        fixture.start(photos); fixture.loaded()
        val first = fixture.state.photo
        val release = CompletableDeferred<Unit>()
        fixture.loadBlock = { release.await(); DriveSlide(it.id, false) }
        fixture.controller.next()
        val pending = fixture.loads.last()
        assertTrue(fixture.state.loading)
        fixture.controller.setSuspended(true)
        release.complete(Unit); yield()
        assertEquals(first, fixture.state.photo)
        assertFalse(fixture.state.loading)
        val count = fixture.loads.size
        fixture.controller.next(); yield()
        assertEquals(count, fixture.loads.size)
        fixture.controller.setSuspended(false)
        fixture.controller.next(); fixture.loaded()
        assertEquals(pending, fixture.state.photo?.id)
        fixture.controller.stop()
    }

    private class Fixture(scope: CoroutineScope, waitInterval: suspend (Long) -> Unit = { CompletableDeferred<Unit>().await() }) {
        val loads = mutableListOf<String>()
        val prefetches = mutableListOf<String>()
        val downloadPermissions = mutableListOf<Boolean>()
        var reconnects = 0
        var loadBlock: suspend (DriveFile) -> DriveSlide<String> = { DriveSlide(it.id, false) }
        val controller = DriveSlideshowController(
            scope = scope,
            load = { _, photo, _, allowDownload -> loads += photo.id; downloadPermissions += allowDownload; loadBlock(photo) },
            prefetch = { _, photo, _ -> prefetches += photo.id },
            random = Random(24),
            waitInterval = waitInterval,
            onAuthorizationFailure = { reconnects++ },
        )
        val state get() = controller.state.value
        fun start(photos: List<DriveFile>) = controller.start(account, api, "Selected folder", photos)
        suspend fun loaded() { withTimeout(2_000) { controller.state.first { !it.loading } } }
    }

    companion object {
        private val account = DriveAccount("account", "test@example.com")
        private val photos = (1..4).map { DriveFile("photo-$it", "$it.jpg", "image/jpeg", capabilities = DriveFile.Capabilities(true)) }
        private val api = object : DrivePhotoAccess {
            override suspend fun account() = account
            override suspend fun folders(parentId: String, progress: (Int) -> Unit) = emptyList<DriveFile>()
            override suspend fun photos(parentId: String, progress: (Int) -> Unit) = photos
            override suspend fun download(photo: DriveFile, target: File) = error("No network in slideshow tests")
            override fun close() = Unit
        }
    }
}
