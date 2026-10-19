package com.metrolist.music.photo.v2.drive

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import com.metrolist.music.photo.FramePhotoOrientation
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

    @Test fun `first random group seeds five offline ready photos`() = runBlocking {
        val fixture = Fixture(this)
        val many = (1..8).map { DriveFile("many-$it", "$it.jpg", "image/jpeg", capabilities = DriveFile.Capabilities(true)) }
        fixture.start(many)
        fixture.loaded()
        yield()
        assertEquals(5, (fixture.loads + fixture.prefetches).distinct().size)
        assertEquals(4, fixture.prefetches.distinct().size)
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
        fixture.loadBlock = { release.await(); DriveSlide(it.id, fromCache = false) }
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
            if (it.id == "photo-1") DriveSlide(it.id, fromCache = true) else throw DriveException(DriveFailure.NETWORK)
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
            DriveSlide(it.id, fromCache = false)
        }
        fixture.start(photos); started.await()
        fixture.loadBlock = { DriveSlide(it.id, fromCache = true) }
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
            DriveSlide(it.id, fromCache = true)
        }
        fixture.start(photos); fixture.loaded()
        assertEquals(1, fixture.reconnects)
        assertTrue(fixture.state.active)
        assertTrue(fixture.state.current!!.fromCache)
        assertEquals(DriveFailure.REAUTHORIZE, fixture.state.error?.failure)
        fixture.controller.stop()
    }

    @Test fun `online metadata replaces cache only source without clearing visible frame`() = runBlocking {
        val fixture = Fixture(this)
        fixture.controller.start(account, api, "Cached", photos, allowDownloads = false)
        fixture.loaded()
        val current = fixture.state.current
        assertTrue(fixture.downloadPermissions.all { !it })

        val refreshed = photos + DriveFile("photo-5", "5.jpg", "image/jpeg", capabilities = DriveFile.Capabilities(true))
        fixture.controller.updateSource(account, api, "Drive", refreshed)
        assertSame(current, fixture.state.current)
        assertEquals("Drive", fixture.state.folderName)
        assertEquals(5, fixture.state.photoCount)
        fixture.controller.stop()
    }

    @Test fun `online single photo absent from cache replaces the visible offline frame`() = runBlocking {
        val fixture = Fixture(this)
        fixture.controller.start(account, api, "Cached", listOf(photos.first()), allowDownloads = false)
        fixture.loaded()
        val cached = fixture.state.current
        val replacement = DriveFile("replacement", "new.jpg", "image/jpeg", capabilities = DriveFile.Capabilities(true))

        fixture.controller.updateSource(account, api, "Drive", listOf(replacement))
        fixture.loaded()

        assertNotEquals(cached, fixture.state.current)
        assertEquals("replacement", fixture.state.photo?.id)
        assertTrue(fixture.downloadPermissions.last())
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
        fixture.loadBlock = { release.await(); DriveSlide(it.id, fromCache = false) }
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

    @Test fun `portrait screens stack two landscape photos per interval`() = runBlocking {
        val fixture = Fixture(this)
        fixture.orientationOfId = { if (it.startsWith("land")) FramePhotoOrientation.LANDSCAPE else FramePhotoOrientation.UNKNOWN }
        fixture.controller.setLandscapeScreen(false)
        fixture.start(landPhotos.take(3)); fixture.loaded()
        assertEquals(2, fixture.loads.size)
        assertEquals(fixture.loads[0], fixture.state.photo?.id)
        assertEquals(fixture.loads[1], fixture.state.pairPhoto?.id)
        assertTrue(fixture.state.current!!.pairImage != null)
        // The odd leftover of the shuffle round shows alone before a new round refills the queue.
        fixture.controller.next(); fixture.loaded()
        assertEquals(3, fixture.loads.size)
        assertTrue(fixture.state.current!!.pairImage == null)
        fixture.controller.stop()
    }

    @Test fun `landscape screens pair portrait photos per interval`() = runBlocking {
        val fixture = Fixture(this)
        fixture.orientationOfId = { if (it.startsWith("port")) FramePhotoOrientation.PORTRAIT else FramePhotoOrientation.UNKNOWN }
        fixture.start(portraitPhotos); fixture.loaded()
        assertEquals(2, fixture.loads.size)
        assertNotNull(fixture.state.current!!.pairImage)
        assertEquals(fixture.loads[1], fixture.state.pairPhoto?.id)
        fixture.controller.stop()
    }

    @Test fun `mismatched partners wait for a later slide and never share an interval`() = runBlocking {
        val fixture = Fixture(this)
        fixture.orientationOfId = { if (it.startsWith("land")) FramePhotoOrientation.LANDSCAPE else FramePhotoOrientation.PORTRAIT }
        fixture.controller.setLandscapeScreen(false)
        fixture.start(mixedPhotos); fixture.loaded()
        var pairs = 0
        repeat(8) {
            fixture.loaded()
            val current = fixture.state.current!!
            if (current.pairImage != null) {
                pairs++
                assertTrue(fixture.state.photo?.id?.startsWith("land") == true)
                assertTrue(fixture.state.pairPhoto?.id?.startsWith("land") == true)
                assertNotEquals(fixture.state.photo?.id, fixture.state.pairPhoto?.id)
            }
            fixture.controller.next()
        }
        assertTrue(pairs > 0)
        assertEquals(mixedPhotos.map { it.id }.toSet(), fixture.loads.distinct().toSet())
        fixture.controller.stop()
    }

    @Test fun `previous and next replay paired intervals from display history`() = runBlocking {
        val fixture = Fixture(this)
        fixture.orientationOfId = { if (it.startsWith("land")) FramePhotoOrientation.LANDSCAPE else FramePhotoOrientation.UNKNOWN }
        fixture.controller.setLandscapeScreen(false)
        fixture.start(landPhotos); fixture.loaded()
        assertTrue(fixture.state.current!!.pairImage != null)
        val pairState = fixture.state.current
        fixture.controller.next(); fixture.loaded()
        val afterState = fixture.state.current
        fixture.controller.previous(); fixture.loaded()
        assertEquals(pairState, fixture.state.current)
        fixture.controller.next(); fixture.loaded()
        assertEquals(afterState, fixture.state.current)
        fixture.controller.stop()
    }

    @Test fun `suspending during a pair keeps both photos queued for the resumed slide`() = runBlocking {
        val fixture = Fixture(this)
        fixture.orientationOfId = { if (it.startsWith("land")) FramePhotoOrientation.LANDSCAPE else FramePhotoOrientation.UNKNOWN }
        fixture.controller.setLandscapeScreen(false)
        val release = CompletableDeferred<Unit>()
        fixture.loadBlock = { photo ->
            if (fixture.loads.size >= 2) release.await()
            DriveSlide(photo.id, fromCache = false)
        }
        fixture.start(landPhotos.take(2))
        assertEquals(2, fixture.loads.size)
        assertTrue(fixture.state.loading)
        fixture.controller.setSuspended(true)
        release.complete(Unit); yield()
        assertFalse(fixture.state.loading)
        val primaryId = fixture.loads[0]
        val partnerId = fixture.loads[1]
        fixture.controller.setSuspended(false); fixture.loaded()
        assertEquals(primaryId, fixture.state.photo?.id)
        assertEquals(partnerId, fixture.state.pairPhoto?.id)
        assertTrue(fixture.state.current!!.pairImage != null)
        fixture.controller.stop()
    }

    @Test fun `a new round does not open with the photos of the previous interval`() = runBlocking {
        val fixture = Fixture(this)
        fixture.orientationOfId = { if (it.startsWith("land")) FramePhotoOrientation.LANDSCAPE else FramePhotoOrientation.UNKNOWN }
        fixture.controller.setLandscapeScreen(false)
        fixture.start(landPhotos); fixture.loaded()
        var previous = setOfNotNull(fixture.state.photo?.id, fixture.state.pairPhoto?.id)
        repeat(9) {
            fixture.controller.next(); fixture.loaded()
            val current = setOfNotNull(fixture.state.photo?.id, fixture.state.pairPhoto?.id)
            assertNotEquals(previous, current)
            previous = current
        }
        fixture.controller.stop()
    }

    @Test fun `a failed partner load shows the primary with the remaining candidate`() = runBlocking {
        val fixture = Fixture(this)
        fixture.orientationOfId = { if (it.startsWith("land")) FramePhotoOrientation.LANDSCAPE else FramePhotoOrientation.UNKNOWN }
        fixture.controller.setLandscapeScreen(false)
        fixture.loadBlock = { photo ->
            if (fixture.loads.size == 2) throw DriveException(DriveFailure.NETWORK)
            DriveSlide(photo.id, fromCache = false)
        }
        fixture.start(landPhotos.take(3)); fixture.loaded()
        assertEquals(fixture.loads[0], fixture.state.photo?.id)
        assertEquals(fixture.loads[2], fixture.state.pairPhoto?.id)
        assertTrue(fixture.state.current!!.pairImage != null)
        assertEquals(1, fixture.state.skippedCount)
        assertEquals(DriveFailure.NETWORK, fixture.state.error?.failure)
        fixture.controller.stop()
    }

    @Test fun `rotation re-evaluates pairing for the following intervals`() = runBlocking {
        val fixture = Fixture(this)
        fixture.orientationOfId = { if (it.startsWith("land")) FramePhotoOrientation.LANDSCAPE else FramePhotoOrientation.UNKNOWN }
        fixture.controller.setLandscapeScreen(false)
        fixture.start(landPhotos.take(3)); fixture.loaded()
        assertTrue(fixture.state.current!!.pairImage != null)
        fixture.controller.setLandscapeScreen(true)
        fixture.controller.next(); fixture.loaded()
        assertTrue(fixture.state.current!!.pairImage == null)
        fixture.controller.stop()
    }

    @Test fun `refreshed online metadata keeps a paired frame intact`() = runBlocking {
        val fixture = Fixture(this)
        fixture.orientationOfId = { if (it.startsWith("land")) FramePhotoOrientation.LANDSCAPE else FramePhotoOrientation.UNKNOWN }
        fixture.controller.setLandscapeScreen(false)
        fixture.start(landPhotos.take(3)); fixture.loaded()
        val pairState = fixture.state
        fixture.controller.updateSource(account, api, "Drive", landPhotos.take(3))
        assertEquals(pairState.current, fixture.state.current)
        assertEquals(pairState.photo, fixture.state.photo)
        assertEquals(pairState.pairPhoto, fixture.state.pairPhoto)
        fixture.controller.stop()
    }

    /** A deferred partner whose round expired must not force a whole fresh-round scan. */
    private fun staleDeferredReloadDelta(seed: Long): Int? = runBlocking {
        val fixture = Fixture(this, shuffleSeed = seed)
        fixture.orientationOfId = { if (it.startsWith("land")) FramePhotoOrientation.LANDSCAPE else FramePhotoOrientation.PORTRAIT }
        fixture.start(landPhotos.take(1) + portraitPhotos.take(1)); fixture.loaded()
        // Landscape screen: the portrait photo shows alone and the landscape photo waits deferred.
        if (fixture.state.photo?.id?.startsWith("port") != true) {
            fixture.controller.stop()
            return@runBlocking null
        }
        fixture.controller.setLandscapeScreen(false)
        val before = fixture.loads.size
        fixture.controller.next(); fixture.loaded()
        val delta = fixture.loads.size - before
        fixture.controller.stop()
        delta
    }

    @Test fun `an expired-round deferred primary waits without decoding the fresh round`() {
        val deltas = (0L until 40L).mapNotNull { staleDeferredReloadDelta(it) }
        assertTrue(deltas.isNotEmpty())
        assertTrue(deltas.all { it <= 2 })
    }

    @Test fun `partner failures fall through to the remaining round candidates`() = runBlocking {
        val photos = listOf("port-ok-1", "port-ok-2", "port-bad-1", "port-bad-2")
            .map { DriveFile(it, "$it.jpg", "image/jpeg", capabilities = DriveFile.Capabilities(true)) }
        val fixture = Fixture(this)
        fixture.orientationOfId = { if (it.startsWith("port")) FramePhotoOrientation.PORTRAIT else FramePhotoOrientation.UNKNOWN }
        fixture.loadBlock = { photo ->
            if (photo.id.contains("bad")) throw DriveException(DriveFailure.NETWORK)
            DriveSlide(photo.id, fromCache = false)
        }
        fixture.start(photos); fixture.loaded()
        // Bad candidates are skipped without stopping; the interval shows readable photos only.
        assertTrue(fixture.state.current != null)
        assertTrue(fixture.state.playing)
        val shown = listOfNotNull(fixture.state.photo?.id, fixture.state.pairPhoto?.id)
        assertTrue(shown.all { it.contains("ok") })
        fixture.controller.stop()
    }

    private class Fixture(scope: CoroutineScope, waitInterval: suspend (Long) -> Unit = { CompletableDeferred<Unit>().await() }, shuffleSeed: Long = 24L) {
        val loads = mutableListOf<String>()
        val prefetches = mutableListOf<String>()
        val downloadPermissions = mutableListOf<Boolean>()
        var reconnects = 0
        var loadBlock: suspend (DriveFile) -> DriveSlide<String> = { DriveSlide(it.id, fromCache = false) }
        var orientationOfId: (String) -> FramePhotoOrientation = { FramePhotoOrientation.UNKNOWN }
        val controller = DriveSlideshowController(
            scope = scope,
            load = { _, photo, _, allowDownload -> loads += photo.id; downloadPermissions += allowDownload; loadBlock(photo) },
            prefetch = { _, photo, _ -> prefetches += photo.id },
            random = Random(shuffleSeed),
            waitInterval = waitInterval,
            onAuthorizationFailure = { reconnects++ },
            orientationOf = { id -> orientationOfId(id) },
        )
        val state get() = controller.state.value
        fun start(photos: List<DriveFile>) = controller.start(account, api, "Selected folder", photos)
        suspend fun loaded() { withTimeout(2_000) { controller.state.first { !it.loading } } }
    }

    companion object {
        private val account = DriveAccount("account", "test@example.com")
        private val photos = (1..4).map { DriveFile("photo-$it", "$it.jpg", "image/jpeg", capabilities = DriveFile.Capabilities(true)) }
        private val landPhotos = (1..4).map { DriveFile("land-$it", "$it.jpg", "image/jpeg", capabilities = DriveFile.Capabilities(true)) }
        private val portraitPhotos = (1..3).map { DriveFile("port-$it", "$it.jpg", "image/jpeg", capabilities = DriveFile.Capabilities(true)) }
        private val mixedPhotos = landPhotos.take(2) + portraitPhotos.take(1)
        private val api = object : DrivePhotoAccess {
            override suspend fun account() = account
            override suspend fun folders(parentId: String, progress: (Int) -> Unit) = emptyList<DriveFile>()
            override suspend fun photos(parentId: String, progress: (Int) -> Unit) = photos
            override suspend fun download(photo: DriveFile, target: File) = error("No network in slideshow tests")
            override fun close() = Unit
        }
    }
}
