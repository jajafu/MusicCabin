package com.metrolist.music.photo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

class PhotoFramePlaybackTest {
    @Test
    fun `shuffle is unique per round and avoids boundary repeats`() {
        repeat(20) { seed ->
            val source = listOf("a", "b", "c", "d")
            val queue = FrameShuffleQueue(source + "a", Random(seed))
            var previous: String? = null
            repeat(10) {
                val round = List(source.size) { queue.next()!! }
                assertEquals(source.toSet(), round.toSet())
                assertNotEquals(previous, round.first())
                previous = round.last()
            }
        }
    }

    @Test
    fun `failed URIs are not retried indefinitely`() {
        val queue = FrameShuffleQueue(listOf("a", "b"))
        queue.markFailed("a")
        assertEquals("b", queue.next())
        queue.markFailed("b")
        assertNull(queue.next())
    }

    @Test
    fun `previous and next walk the playback history`() {
        val queue = FrameShuffleQueue(listOf("a", "b", "c"), Random(1))
        val first = queue.next()
        val second = queue.next()

        assertEquals(first, queue.previous())
        assertEquals(second, queue.next())
    }

    @Test
    fun `empty pool does not load or start a timer`() = runBlocking {
        val engine = PhotoFramePlayback<String>(load = { error("Unexpected image request") }, onUnreadable = {})
        var state: FramePlaybackState<String>? = null
        engine.play(emptyList(), 60_000) { state = it }
        assertNull(state!!.current)
        assertFalse(state.exhausted)
    }

    @Test
    fun `single photo is loaded once without a timer`() = runBlocking {
        var loads = 0
        val engine = PhotoFramePlayback(load = { uri: String -> loads++; uri }, onUnreadable = {})
        var state: FramePlaybackState<String>? = null
        engine.play(listOf("a"), 60_000) { state = it }
        assertEquals(1, loads)
        assertEquals("a", state!!.current!!.images.single().uri)
        assertNull(state.incoming)
    }

    @Test
    fun `manual next command advances without waiting for the interval`() = runBlocking {
        val session = FramePlaybackSession(listOf("a", "b", "c"), Random(1))
        val firstShown = CompletableDeferred<Unit>()
        val secondShown = CompletableDeferred<Unit>()
        val previousShown = CompletableDeferred<Unit>()
        val shown = mutableListOf<String>()
        val engine = PhotoFramePlayback<String>(
            load = { it },
            onUnreadable = {},
            transitionMillis = 0,
        )
        val job = launch {
            engine.play(session, 60_000) { state ->
                state.current?.let { slide ->
                    val uri = slide.images.single().uri
                    if (shown.lastOrNull() != uri) {
                        shown += uri
                        if (shown.size == 1) firstShown.complete(Unit)
                        if (shown.size == 2) secondShown.complete(Unit)
                        if (shown.size == 3) previousShown.complete(Unit)
                    }
                }
            }
        }

        firstShown.await()
        session.request(FramePlaybackCommand.NEXT)
        secondShown.await()
        session.request(FramePlaybackCommand.PREVIOUS)
        previousShown.await()
        job.cancelAndJoin()

        assertNotEquals(shown[0], shown[1])
        assertEquals(shown[0], shown[2])
    }

    @Test
    fun `all unreadable images terminate with an empty error state`() = runBlocking {
        val failures = mutableListOf<String>()
        val engine = PhotoFramePlayback<String>(load = { null }, onUnreadable = { failures += it })
        var state: FramePlaybackState<String>? = null
        engine.play(listOf("a", "b"), 0) { state = it }
        assertEquals(setOf("a", "b"), failures.toSet())
        assertEquals(2, failures.size)
        assertTrue(state!!.exhausted)
        assertNull(state.current)
    }

    @Test
    fun `broken photo does not prevent valid photos playing`() = runBlocking {
        val failures = mutableListOf<String>()
        val displayed = mutableSetOf<String>()
        val engine = PhotoFramePlayback(
            load = { uri: String -> uri.takeUnless { it == "bad" } },
            onUnreadable = { failures += it },
            transitionMillis = 0,
        )
        try {
            engine.play(listOf("a", "bad", "b"), 0) {
                it.current?.let { slide -> displayed.addAll(slide.uris) }
                if (displayed.size == 2) throw CancellationException("Test complete")
            }
        } catch (_: CancellationException) {
            // Stop the otherwise infinite slideshow once both readable images were displayed.
        }
        assertEquals(setOf("a", "b"), displayed)
        assertTrue(failures.all { it == "bad" })
    }

    @Test
    fun `screen cancellation cancels pending decode without reporting a bad photo`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val failures = mutableListOf<String>()
        val engine = PhotoFramePlayback<String>(
            load = { entered.complete(Unit); awaitCancellation() },
            onUnreadable = { failures += it },
        )
        val job = launch { engine.play(listOf("a"), 0) { error("Unexpected completed load") } }
        entered.await()
        job.cancelAndJoin()
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `pause checkpoints current pending and remaining time without reshuffling`() = runBlocking {
        val session = FramePlaybackSession(listOf("a", "b", "c"))
        val preloading = CompletableDeferred<Unit>()
        val requested = mutableListOf<String>()
        var time = 0L
        val engine = PhotoFramePlayback(
            load = { uri: String ->
                requested += uri
                if (requested.size == 2) {
                    time = 250L
                    preloading.complete(Unit)
                    awaitCancellation()
                }
                uri
            },
            onUnreadable = { error("Cancellation is not a failed image") },
            nowMillis = { time },
        )
        val job = launch { engine.play(session, 1_000) {} }
        preloading.await()
        job.cancelAndJoin()
        assertEquals(requested[0], session.currentUri)
        assertEquals(requested[1], session.pendingUri)
        assertEquals(750L, session.remainingMillis)

        val resumed = mutableListOf<String>()
        val resumeEngine = PhotoFramePlayback(
            load = { uri: String -> resumed += uri; uri },
            onUnreadable = {},
        )
        try {
            resumeEngine.play(session, 0) {
                if (it.incoming != null) throw CancellationException("Both saved frames restored")
            }
        } catch (_: CancellationException) {
            assertEquals(requested, resumed)
        }
        assertTrue(session.queue.next() !in requested)
    }

    @Test
    fun `a disconnected source skips its remaining URIs without repeated timeouts`() = runBlocking {
        val requested = mutableListOf<String>()
        val uris = List(1_000) { "usb-$it" }
        val engine = PhotoFramePlayback<String>(
            load = { requested += it; null },
            onUnreadable = {},
            unavailableUris = { uris.toSet() },
        )
        engine.play(uris, 60_000) { assertTrue(it.exhausted) }
        assertEquals(1, requested.size)
    }

    @Test
    fun `load timeout skips a stalled provider and terminates`() = runBlocking {
        val failed = mutableListOf<String>()
        val engine = PhotoFramePlayback<String>(
            load = { awaitCancellation() },
            onUnreadable = { failed += it },
            loadTimeoutMillis = 10,
        )
        engine.play(listOf("stalled"), 0) { assertTrue(it.exhausted) }
        assertEquals(listOf("stalled"), failed)
    }

    @Test
    fun `a broken last photo in a round cannot stall two readable photos`() = runBlocking {
        repeat(20) { seed ->
            val shown = mutableListOf<String>()
            val engine = PhotoFramePlayback(
                load = { uri: String -> uri.takeUnless { it == "bad" } },
                onUnreadable = {},
                transitionMillis = 0,
            )
            val session = FramePlaybackSession(listOf("a", "b", "bad"), Random(seed))
            try {
                engine.play(session, 0) { frame ->
                    if (frame.incoming == null && frame.current != null) {
                        shown += frame.current.images.single().uri
                        if (shown.size == 40) throw CancellationException("Test complete")
                    }
                }
            } catch (_: CancellationException) {
                // Every seed must reach 40 frames, not silently return after a repeated preload.
            }
            assertEquals("Slideshow stopped for seed $seed", 40, shown.size)
            assertTrue("Repeated photo for seed $seed: $shown", shown.zipWithNext().all { (first, second) -> first != second })
        }
    }

    private fun orientedEngine() =
        PhotoFramePlayback<String>(load = { it }, onUnreadable = {}, transitionMillis = 0)

    private suspend fun collectSlides(
        engine: PhotoFramePlayback<String>,
        session: FramePlaybackSession,
        count: Int,
        isLandscape: Boolean,
        orientationOf: (String) -> FramePhotoOrientation,
    ): List<List<String>> {
        val shown = mutableListOf<List<String>>()
        try {
            engine.play(
                session, 0,
                isLandscape = { isLandscape },
                orientationOf = orientationOf,
            ) { state ->
                state.current?.let { slide ->
                    if (shown.lastOrNull() != slide.uris) {
                        shown += slide.uris
                        if (shown.size == count) throw CancellationException("Test complete")
                    }
                }
            }
        } catch (_: CancellationException) {
            // Stop the otherwise infinite slideshow once enough slides were displayed.
        }
        return shown
    }

    private fun orientationOf(uri: String) = when {
        uri.startsWith("portrait") -> FramePhotoOrientation.PORTRAIT
        uri.startsWith("landscape") -> FramePhotoOrientation.LANDSCAPE
        else -> FramePhotoOrientation.UNKNOWN
    }

    private fun seedForQueueOrder(uris: List<String>, expectedOrder: List<String>): Int =
        (0..10_000).first { seed ->
            val queue = FrameShuffleQueue(uris, Random(seed))
            expectedOrder.all { queue.next() == it }
        }

    @Test
    fun `pair search skips mismatched photos to find a later matching orientation`() = runBlocking {
        val portraits = listOf("portrait-a", "portrait-b")
        val landscape = "landscape-a"
        val pool = listOf(portraits[0], landscape, portraits[1])
        val seed = seedForQueueOrder(pool, pool)
        val session = FramePlaybackSession(pool, Random(seed))
        val engine = orientedEngine()
        var firstSlide: List<String>? = null

        try {
            engine.play(
                session, 0,
                isLandscape = { true },
                orientationOf = ::orientationOf,
            ) { state ->
                if (state.incoming == null && state.current != null) {
                    firstSlide = state.current.uris
                    throw CancellationException("First slide captured")
                }
            }
        } catch (_: CancellationException) {
            // Stop after the first slide has been committed.
        }

        assertEquals(portraits.toSet(), firstSlide?.toSet())
        assertEquals(listOf(landscape), session.deferredPhotos.map { it.uri })
    }

    @Test
    fun `cancelled pair search retains deferred candidate and resumes pairing`() = runBlocking {
        val first = "portrait-a"
        val deferred = "landscape-a"
        val partner = "portrait-b"
        val pool = listOf(first, deferred, partner)
        val session = FramePlaybackSession(pool, Random(seedForQueueOrder(pool, pool)))
        val candidateLoading = CompletableDeferred<Unit>()
        val interrupted = PhotoFramePlayback<String>(
            load = { uri ->
                if (uri == partner) {
                    candidateLoading.complete(Unit)
                    awaitCancellation()
                }
                uri
            },
            onUnreadable = { error("Cancellation is not a failed image") },
        )
        val interruptedJob = launch {
            interrupted.play(
                session, 60_000,
                isLandscape = { true },
                orientationOf = ::orientationOf,
            ) { error("The interrupted slide must not be published") }
        }
        candidateLoading.await()
        interruptedJob.cancelAndJoin()

        assertEquals(first, session.buildingPrimary?.uri)
        assertEquals(partner, session.buildingPartner?.uri)
        assertEquals(listOf(deferred), session.deferredPhotos.map { it.uri })

        val resumedSlide = CompletableDeferred<List<String>>()
        val resumeEngine = orientedEngine()
        try {
            resumeEngine.play(
                session, 0,
                isLandscape = { true },
                orientationOf = ::orientationOf,
            ) { state ->
                if (state.incoming == null && state.current != null) {
                    resumedSlide.complete(state.current.uris)
                    throw CancellationException("Paired slide captured")
                }
            }
        } catch (_: CancellationException) {
            // The restored first slide is sufficient for this checkpoint.
        }
        assertEquals(setOf(first, partner), resumedSlide.await().toSet())
        assertEquals(listOf(deferred), session.deferredPhotos.map { it.uri })

        val nextSlide = CompletableDeferred<List<String>>()
        val finalJob = launch {
            resumeEngine.play(
                session, 60_000,
                isLandscape = { true },
                orientationOf = ::orientationOf,
            ) { state ->
                if (state.incoming == null && state.current != null) {
                    if (state.current.uris == listOf(first, partner)) {
                        session.request(FramePlaybackCommand.NEXT)
                    } else if (state.current.uris == listOf(deferred)) {
                        nextSlide.complete(state.current.uris)
                    }
                }
            }
        }
        assertEquals(listOf(deferred), nextSlide.await())
        finalJob.cancelAndJoin()
    }

    @Test
    fun `resume finishes the interrupted pair instead of replaying the primary alone`() = runBlocking {
        val visible = "landscape-c"
        val primary = "portrait-a"
        val partner = "portrait-b"
        val pool = listOf(visible, primary, partner)
        val session = FramePlaybackSession(pool, Random(seedForQueueOrder(pool, pool)))
        val partnerLoading = CompletableDeferred<Unit>()
        val interrupted = PhotoFramePlayback<String>(
            load = { uri ->
                if (uri == partner && !partnerLoading.isCompleted) {
                    partnerLoading.complete(Unit)
                    awaitCancellation()
                }
                uri
            },
            onUnreadable = { error("Cancellation is not a failed image") },
            transitionMillis = 0,
        )
        val interruptedJob = launch {
            interrupted.play(
                session, 60_000,
                isLandscape = { true },
                orientationOf = ::orientationOf,
            ) { }
        }
        partnerLoading.await()
        interruptedJob.cancelAndJoin()

        // The visible slide stays put and the half-built pair is what the session kept.
        assertEquals(listOf(visible), session.currentUris)
        assertEquals(primary, session.buildingPrimary?.uri)
        assertEquals(partner, session.buildingPartner?.uri)

        val shown = collectSlides(orientedEngine(), session, 2, true, ::orientationOf)
        assertEquals(
            "The primary must not be shown alone before the paired slide",
            listOf(listOf(visible), listOf(primary, partner)),
            shown,
        )
    }

    @Test
    fun `preloading forward history leaves visible checkpoint unchanged across cancellation`() = runBlocking {
        val session = FramePlaybackSession(listOf("a", "b", "c"), Random(9))
        val displayed = List(4) { CompletableDeferred<String>() }
        val historyTargetLoading = CompletableDeferred<Unit>()
        val watchHistoryPreload = AtomicBoolean(false)
        val shown = mutableListOf<String>()
        var expectedNext: String? = null
        val firstEngine = PhotoFramePlayback<String>(
            load = { uri ->
                if (watchHistoryPreload.get() && uri == expectedNext) {
                    historyTargetLoading.complete(Unit)
                }
                uri
            },
            onUnreadable = {},
            transitionMillis = 0,
        )
        val job = launch {
            firstEngine.play(session, 60_000) { state ->
                if (state.incoming == null && state.current != null) {
                    val uri = state.current.images.single().uri
                    if (shown.lastOrNull() != uri) {
                        shown += uri
                        displayed[shown.lastIndex].complete(uri)
                        if (shown.size == 3) expectedNext = uri
                        if (shown.size == 4) watchHistoryPreload.set(true)
                    }
                }
            }
        }
        displayed[0].await()
        session.request(FramePlaybackCommand.NEXT)
        displayed[1].await()
        session.request(FramePlaybackCommand.NEXT)
        val thirdUri = displayed[2].await()
        session.request(FramePlaybackCommand.PREVIOUS)
        val visibleBeforePause = displayed[3].await()
        assertNotEquals(thirdUri, visibleBeforePause)
        assertEquals(shown[1], visibleBeforePause)
        expectedNext = thirdUri
        historyTargetLoading.await()
        job.cancelAndJoin()

        val expectedNextUri = thirdUri
        assertEquals(listOf(visibleBeforePause), session.currentUris)
        assertEquals(1, session.slideIndex)
        assertEquals(2, session.pendingHistoryIndex)

        val resumedFirst = CompletableDeferred<String>()
        val resumedNext = CompletableDeferred<String>()
        val resumeJob = launch {
            PhotoFramePlayback<String>(load = { it }, onUnreadable = {}, transitionMillis = 0)
                .play(session, 60_000) { state ->
                    if (state.incoming == null && state.current != null) {
                        val uri = state.current.images.single().uri
                        if (!resumedFirst.isCompleted) {
                            resumedFirst.complete(uri)
                            session.request(FramePlaybackCommand.NEXT)
                        } else if (uri == expectedNextUri) {
                            resumedNext.complete(uri)
                        }
                    }
                }
        }
        assertEquals(visibleBeforePause, resumedFirst.await())
        assertEquals(expectedNextUri, resumedNext.await())
        resumeJob.cancelAndJoin()
    }

    @Test
    fun `landscape screen pairs two portraits into one slide`() = runBlocking {
        val pool = listOf("portrait-a", "portrait-b", "portrait-c", "portrait-d")
        val shown = collectSlides(
            orientedEngine(), FramePlaybackSession(pool, Random(7)), 2, true, ::orientationOf,
        )
        assertEquals(2, shown.size)
        assertTrue(shown.all { it.size == 2 })
        assertEquals(pool.toSet(), shown.flatten().toSet())
    }

    @Test
    fun `portrait screen pairs two landscapes into one slide`() = runBlocking {
        val pool = listOf("landscape-a", "landscape-b", "landscape-c", "landscape-d")
        val shown = collectSlides(
            orientedEngine(), FramePlaybackSession(pool, Random(3)), 2, false, ::orientationOf,
        )
        assertEquals(2, shown.size)
        assertTrue(shown.all { it.size == 2 })
        assertEquals(pool.toSet(), shown.flatten().toSet())
    }

    @Test
    fun `photos matching the screen orientation stay single`() = runBlocking {
        val pool = listOf("landscape-a", "landscape-b", "landscape-c")
        val shown = collectSlides(
            orientedEngine(), FramePlaybackSession(pool, Random(5)), 3, true, ::orientationOf,
        )
        assertEquals(3, shown.size)
        assertTrue(shown.all { it.size == 1 })
        assertEquals(pool.toSet(), shown.flatten().toSet())
    }

    @Test
    fun `unknown orientation never pairs`() = runBlocking {
        val pool = listOf("a", "b", "c", "d")
        val shown = collectSlides(
            orientedEngine(), FramePlaybackSession(pool, Random(11)), 4, true, ::orientationOf,
        )
        assertEquals(4, shown.size)
        assertTrue(shown.all { it.size == 1 })
    }

    @Test
    fun `unpaired photo falls back to single and partner plays next`() = runBlocking {
        val pool = listOf("portrait-a", "landscape-b")
        val shown = collectSlides(
            orientedEngine(), FramePlaybackSession(pool, Random(1)), 2, true, ::orientationOf,
        )
        assertEquals(2, shown.size)
        assertTrue(shown.all { it.size == 1 })
        assertEquals(pool.toSet(), shown.flatten().toSet())
    }

    @Test
    fun `previous returns to the paired slide with both photos`() = runBlocking {
        val pool = listOf("portrait-a", "portrait-b", "portrait-c", "portrait-d")
        val session = FramePlaybackSession(pool, Random(7))
        val engine = orientedEngine()
        val firstShown = CompletableDeferred<List<String>>()
        val secondShown = CompletableDeferred<List<String>>()
        val backShown = CompletableDeferred<List<String>>()
        val shown = mutableListOf<List<String>>()
        val job = launch {
            engine.play(
                session, 60_000,
                isLandscape = { true },
                orientationOf = ::orientationOf,
            ) { state ->
                state.current?.let { slide ->
                    if (shown.lastOrNull() != slide.uris) {
                        shown += slide.uris
                        when (shown.size) {
                            1 -> firstShown.complete(slide.uris)
                            2 -> secondShown.complete(slide.uris)
                            3 -> backShown.complete(slide.uris)
                        }
                    }
                }
            }
        }

        val first = firstShown.await()
        assertEquals(2, first.size)
        session.request(FramePlaybackCommand.NEXT)
        secondShown.await()
        session.request(FramePlaybackCommand.PREVIOUS)
        assertEquals(first, backShown.await())
        job.cancelAndJoin()
    }

    @Test
    fun `an identically reshuffled pair is skipped instead of cross-fading into itself`() = runBlocking {
        repeat(30) { seed ->
            val pool = listOf("portrait-a", "portrait-b", "portrait-c", "portrait-d")
            val transitions = mutableListOf<List<String>>()
            val engine = PhotoFramePlayback<String>(load = { it }, onUnreadable = {}, transitionMillis = 0)
            try {
                engine.play(
                    FramePlaybackSession(pool, Random(seed)), 0,
                    isLandscape = { true },
                    orientationOf = ::orientationOf,
                ) { state ->
                    // Record every transition start; a deduped display list would
                    // hide an identical pair cross-fading into itself.
                    if (state.incoming != null) {
                        transitions += state.incoming.uris
                        if (transitions.size == 12) throw CancellationException("done")
                    }
                }
            } catch (_: CancellationException) {
            }
            assertTrue(
                "Seed $seed repeats a slide: $transitions",
                transitions.zipWithNext().none { (first, second) -> first == second },
            )
        }
    }

    @Test
    fun `orientation helpers classify sizes`() {
        assertEquals(FramePhotoOrientation.LANDSCAPE, FramePhotoOrientation.of(1920, 1080))
        assertEquals(FramePhotoOrientation.PORTRAIT, FramePhotoOrientation.of(1080, 1920))
        assertEquals(FramePhotoOrientation.SQUARE, FramePhotoOrientation.of(100, 100))
        assertEquals(FramePhotoOrientation.UNKNOWN, FramePhotoOrientation.of(0, 100))
        assertTrue(FramePhotoOrientation.PORTRAIT.needsPairing(true))
        assertTrue(FramePhotoOrientation.LANDSCAPE.needsPairing(false))
        assertFalse(FramePhotoOrientation.LANDSCAPE.needsPairing(true))
        assertFalse(FramePhotoOrientation.PORTRAIT.needsPairing(false))
        assertFalse(FramePhotoOrientation.SQUARE.needsPairing(true))
        assertFalse(FramePhotoOrientation.UNKNOWN.needsPairing(false))
        assertTrue(canPair(FramePhotoOrientation.PORTRAIT, FramePhotoOrientation.PORTRAIT))
        assertTrue(canPair(FramePhotoOrientation.LANDSCAPE, FramePhotoOrientation.LANDSCAPE))
        assertFalse(canPair(FramePhotoOrientation.PORTRAIT, FramePhotoOrientation.LANDSCAPE))
        assertFalse(canPair(FramePhotoOrientation.SQUARE, FramePhotoOrientation.SQUARE))
    }

    @Test
    fun `shuffle queue history is bounded for long sessions`() {
        val queue = FrameShuffleQueue(listOf("a", "b", "c"), Random(3))
        repeat(FRAME_HISTORY_LIMIT + 60) { queue.next() }
        var steps = 0
        while (queue.previous() != null) {
            steps++
        }
        assertEquals(FRAME_HISTORY_LIMIT - 1, steps)
    }

    @Test
    fun `slide history is bounded for long sessions`() = runBlocking {
        val session = FramePlaybackSession(listOf("a", "b", "c"), Random(3))
        val engine = PhotoFramePlayback<String>(load = { it }, onUnreadable = {}, transitionMillis = 0)
        var committed = 0
        try {
            engine.play(session, 0) { state ->
                if (state.incoming == null && state.current != null) {
                    committed++
                    if (committed == FRAME_HISTORY_LIMIT + 60) throw CancellationException("done")
                }
            }
        } catch (_: CancellationException) {
            // Stop the otherwise infinite slideshow once enough slides were displayed.
        }
        assertEquals(FRAME_HISTORY_LIMIT + 60, committed)
        assertEquals(FRAME_HISTORY_LIMIT, session.slideHistory.size)
        assertEquals(session.slideHistory.lastIndex, session.slideIndex)
    }
}
