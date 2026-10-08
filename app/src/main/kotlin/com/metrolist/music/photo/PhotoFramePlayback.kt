package com.metrolist.music.photo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/** Maximum retained navigation steps; mirrors the Drive slideshow cap. */
internal const val FRAME_HISTORY_LIMIT = 200

/** Holds URI strings only; image ownership belongs to the visible screen. */
internal class FrameShuffleQueue(
    uris: List<String>,
    private val random: Random = Random.Default,
    preferredFirst: String? = null,
) {
    private val photos = uris.distinct()
    private val failed = mutableSetOf<String>()
    private var round = emptyList<FrameQueueEntry>()
    private var roundNumber = -1
    private var index = 0
    private var previous: String? = null
    private var lastReadable: String? = null
    // A screen-matching photo pinned from cached bounds: shown once up front
    // so the first slide needs no trial decodes, then excluded from the first
    // shuffled round so it is not repeated. Later rounds stay fully shuffled.
    private val pinned: String? = preferredFirst?.takeIf { it in photos }
    private var pendingPin: String? = pinned
    private var excludePinnedFromNextRound = pinned != null
    private val history = mutableListOf<FrameQueueEntry>()
    private var historyIndex = -1

    fun markFailed(uri: String) {
        failed += uri
        if (previous == uri) previous = lastReadable
    }
    fun markFailed(uris: Set<String>) { failed += uris }
    fun markReadable(uri: String) {
        lastReadable = uri
        previous = uri
    }

    fun nextEntry(): FrameQueueEntry? {
        while (historyIndex < history.lastIndex) {
            val next = history[++historyIndex]
            if (next.uri !in failed) {
                previous = next.uri
                return next
            }
        }
        pendingPin?.let { pin ->
            pendingPin = null
            if (pin !in failed) {
                roundNumber = 0
                return recordEntry(FrameQueueEntry(pin, 0))
            }
            excludePinnedFromNextRound = false
        }
        while (true) {
            if (index >= round.size) {
                val available = photos.filterNot { it in failed }
                if (available.isEmpty()) return null
                roundNumber++
                val firstRound = excludePinnedFromNextRound
                excludePinnedFromNextRound = false
                val candidates = if (firstRound && available.size > 1) available.filterNot { it == pinned } else available
                round = candidates.shuffled(random).map { FrameQueueEntry(it, roundNumber) }.toMutableList().apply {
                    if (size > 1 && first().uri == previous) {
                        val swap = random.nextInt(1, size)
                        this[0] = this[swap].also { this[swap] = this[0] }
                    }
                }
                index = 0
            }
            val next = round[index++]
            if (next.uri !in failed) {
                return recordEntry(next)
            }
        }
    }

    fun next(): String? = nextEntry()?.uri

    private fun recordEntry(next: FrameQueueEntry): FrameQueueEntry {
        history += next
        while (history.size > FRAME_HISTORY_LIMIT) {
            history.removeAt(0)
        }
        historyIndex = history.lastIndex
        previous = next.uri
        return next
    }

    fun previous(currentUri: String? = null): String? {
        currentUri?.let { uri ->
            history.indexOfLast { it.uri == uri }.takeIf { it >= 0 }?.let { historyIndex = it }
        }
        while (historyIndex > 0) {
            val previous = history[--historyIndex]
            if (previous.uri !in failed) {
                this.previous = previous.uri
                return previous.uri
            }
        }
        return null
    }
}

/** Photo orientation used to decide whether two photos share one frame interval. */
enum class FramePhotoOrientation {
    PORTRAIT,
    LANDSCAPE,
    SQUARE,
    UNKNOWN,
    ;

    companion object {
        fun of(width: Int, height: Int): FramePhotoOrientation =
            when {
                width <= 0 || height <= 0 -> UNKNOWN
                width == height -> SQUARE
                width > height -> LANDSCAPE
                else -> PORTRAIT
            }
    }
}

internal data class FrameQueueEntry(val uri: String, val round: Int)

internal data class FramePhotoCandidate(
    val uri: String,
    val orientation: FramePhotoOrientation,
    val round: Int,
)

/** Landscape screens pair portraits side by side; portrait screens stack landscapes. */
internal fun FramePhotoOrientation.needsPairing(isLandscapeScreen: Boolean): Boolean =
    when (this) {
        FramePhotoOrientation.PORTRAIT -> isLandscapeScreen
        FramePhotoOrientation.LANDSCAPE -> !isLandscapeScreen
        FramePhotoOrientation.SQUARE,
        FramePhotoOrientation.UNKNOWN,
        -> false
    }

internal fun canPair(first: FramePhotoOrientation, second: FramePhotoOrientation): Boolean =
    first == second && (first == FramePhotoOrientation.PORTRAIT || first == FramePhotoOrientation.LANDSCAPE)

internal data class FrameImage<T>(val uri: String, val image: T)

/** One display interval shows a single photo, or two same-orientation photos when paired. */
internal data class FrameSlide<T>(val images: List<FrameImage<T>>) {
    init {
        require(images.isNotEmpty() && images.size <= 2) { "A slide holds one or two photos" }
    }

    val uris: List<String> get() = images.map { it.uri }
}

internal enum class FramePlaybackCommand { PREVIOUS, NEXT }

internal data class FramePlaybackState<T>(
    val current: FrameSlide<T>? = null,
    val incoming: FrameSlide<T>? = null,
    val exhausted: Boolean = false,
)

/** A pause checkpoint contains no decoded images and can survive a background interval. */
internal class FramePlaybackSession(
    uris: List<String>,
    random: Random = Random.Default,
    preferredFirst: String? = null,
) {
    val queue = FrameShuffleQueue(uris, random, preferredFirst)
    val empty = uris.isEmpty()
    val single = uris.distinct().size == 1
    var currentUris: List<String> = emptyList()
    var pendingUris: List<String>? = null
    var intervalMillis = 0L
    var remainingMillis = 0L
    val slideHistory = mutableListOf<List<String>>()
    var slideIndex = -1
    var pendingHistoryIndex: Int? = null
    var buildingPrimary: FramePhotoCandidate? = null
    var buildingPartner: FramePhotoCandidate? = null
    val deferredPhotos = mutableListOf<FramePhotoCandidate>()

    /** First URI of the visible slide; kept so single-photo callers keep working. */
    var currentUri: String?
        get() = currentUris.firstOrNull()
        set(value) {
            currentUris = if (value == null) emptyList() else listOf(value)
        }

    /** First URI of the preloaded but not yet visible slide. */
    var pendingUri: String?
        get() = pendingUris?.firstOrNull()
        set(value) {
            pendingUris = value?.let(::listOf)
        }

    private val commands = Channel<FramePlaybackCommand>(Channel.UNLIMITED)

    fun request(command: FramePlaybackCommand) {
        commands.trySend(command)
    }

    suspend fun awaitCommand(timeoutMillis: Long): FramePlaybackCommand? =
        withTimeoutOrNull(timeoutMillis.coerceAtLeast(0L)) { commands.receive() }
}

/** Runs only inside a foreground screen effect. Cancellation releases both frames. */
internal class PhotoFramePlayback<T : Any>(
    private val load: suspend (String) -> T?,
    private val onUnreadable: suspend (String) -> Unit,
    private val loadTimeoutMillis: Long = 10_000,
    private val transitionMillis: Long = 350,
    private val unavailableUris: () -> Set<String> = { emptySet() },
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    suspend fun play(
        uris: List<String>,
        intervalMillis: Long,
        isLandscape: () -> Boolean = { true },
        orientationOf: (T) -> FramePhotoOrientation = { FramePhotoOrientation.UNKNOWN },
        publish: (FramePlaybackState<T>) -> Unit,
    ) = play(FramePlaybackSession(uris), intervalMillis, isLandscape, orientationOf, publish)

    suspend fun play(
        session: FramePlaybackSession,
        intervalMillis: Long,
        isLandscape: () -> Boolean = { true },
        orientationOf: (T) -> FramePhotoOrientation = { FramePhotoOrientation.UNKNOWN },
        publish: (FramePlaybackState<T>) -> Unit,
    ) = coroutineScope {
        if (session.intervalMillis != intervalMillis) {
            session.intervalMillis = intervalMillis
            session.remainingMillis = intervalMillis
        }
        // A pending history destination was only preloaded, not made visible. The
        // current URI checkpoint is authoritative after an effect is cancelled.
        session.pendingHistoryIndex = null
        val queue = session.queue
        suspend fun loadPhoto(uri: String): Pair<FrameImage<T>, FramePhotoOrientation>? {
            val image: T? = try {
                withTimeoutOrNull(loadTimeoutMillis) { load(uri) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (image == null) {
                queue.markFailed(uri)
                onUnreadable(uri)
                queue.markFailed(unavailableUris())
                return null
            }
            queue.markReadable(uri)
            return FrameImage(uri, image) to orientationOf(image)
        }
        suspend fun loadSlideByUris(uris: List<String>): FrameSlide<T>? {
            val loaded = mutableListOf<FrameImage<T>>()
            for (uri in uris) {
                currentCoroutineContext().ensureActive()
                loadPhoto(uri)?.let { loaded += it.first }
                if (loaded.size == 2) break
            }
            return loaded.takeIf { it.isNotEmpty() }?.let(::FrameSlide)
        }
        suspend fun loadCandidate(candidate: FramePhotoCandidate): Pair<FrameImage<T>, FramePhotoCandidate>? {
            val (frame, orientation) = loadPhoto(candidate.uri) ?: return null
            return frame to candidate.copy(orientation = orientation)
        }
        suspend fun buildNextSlide(): FrameSlide<T>? {
            // Replaying a saved pending slide must yield to an interrupted pair search:
            // pendingUris then holds only the primary, and using it directly would show
            // that photo alone before the paired slide repeats it.
            if (session.buildingPrimary == null) {
                session.pendingUris?.let { pending ->
                    loadSlideByUris(pending)?.let { slide ->
                        session.pendingUris = slide.uris
                        return slide
                    }
                    session.pendingUris = null
                }
            }

            while (true) {
                currentCoroutineContext().ensureActive()
                val primaryCandidate = session.buildingPrimary ?: run {
                    val deferredIndex = session.deferredPhotos.indices.firstOrNull()
                    val candidate = if (deferredIndex != null) {
                        session.deferredPhotos.removeAt(deferredIndex)
                    } else {
                        val entry = queue.nextEntry() ?: return null
                        FramePhotoCandidate(entry.uri, FramePhotoOrientation.UNKNOWN, entry.round)
                    }
                    session.pendingUris = listOf(candidate.uri)
                    candidate.also { session.buildingPrimary = it }
                }
                val (primary, resolvedPrimary) = loadCandidate(primaryCandidate) ?: run {
                    session.buildingPrimary = null
                    session.buildingPartner = null
                    continue
                }
                session.buildingPrimary = resolvedPrimary
                if (!resolvedPrimary.orientation.needsPairing(isLandscape())) {
                    session.buildingPrimary = null
                    session.pendingUris = listOf(primary.uri)
                    return FrameSlide(listOf(primary))
                }

                while (true) {
                    currentCoroutineContext().ensureActive()
                    val partnerCandidate = session.buildingPartner ?: run {
                        val matchingDeferred = session.deferredPhotos.indexOfFirst {
                            it.round == resolvedPrimary.round && canPair(resolvedPrimary.orientation, it.orientation)
                        }
                        if (matchingDeferred >= 0) {
                            session.deferredPhotos.removeAt(matchingDeferred).also {
                                session.buildingPartner = it
                            }
                        } else {
                            val entry = queue.nextEntry()
                            if (entry == null) {
                                null
                            } else {
                                val candidate = FramePhotoCandidate(entry.uri, FramePhotoOrientation.UNKNOWN, entry.round)
                                if (entry.round != resolvedPrimary.round) {
                                    session.deferredPhotos += candidate
                                    null
                                } else {
                                    session.pendingUris = listOf(resolvedPrimary.uri)
                                    candidate.also { session.buildingPartner = it }
                                }
                            }
                        }
                    }
                    if (partnerCandidate == null) {
                        session.buildingPrimary = null
                        session.pendingUris = listOf(primary.uri)
                        return FrameSlide(listOf(primary))
                    }

                    val (partner, resolvedPartner) = loadCandidate(partnerCandidate) ?: run {
                        session.buildingPartner = null
                        continue
                    }
                    if (canPair(resolvedPrimary.orientation, resolvedPartner.orientation) &&
                        resolvedPrimary.round == resolvedPartner.round
                    ) {
                        session.buildingPrimary = null
                        session.buildingPartner = null
                        session.pendingUris = listOf(primary.uri, partner.uri)
                        return FrameSlide(listOf(primary, partner))
                    }
                    session.deferredPhotos += resolvedPartner
                    session.buildingPartner = null
                }
            }
        }
        /**
         * Builds only the first slide with at most two readable decodes: a photo
         * matching the screen shows alone, two pairable photos show as a pair,
         * and a mismatched second photo shows alone while the first waits in
         * [FramePlaybackSession.deferredPhotos]. Unreadable photos never count
         * toward the budget. Later slides keep the exhaustive pair search above.
         */
        suspend fun buildFirstSlide(): FrameSlide<T>? {
            if (session.buildingPrimary != null || session.buildingPartner != null) return buildNextSlide()
            session.pendingUris?.let { pending ->
                loadSlideByUris(pending)?.let { slide ->
                    session.pendingUris = slide.uris
                    return slide
                }
                session.pendingUris = null
            }
            var primary: FrameImage<T>? = null
            var resolvedPrimary: FramePhotoCandidate? = null
            while (primary == null) {
                currentCoroutineContext().ensureActive()
                val candidate = if (session.deferredPhotos.isNotEmpty()) {
                    session.deferredPhotos.removeAt(0)
                } else {
                    val entry = queue.nextEntry() ?: return null
                    FramePhotoCandidate(entry.uri, FramePhotoOrientation.UNKNOWN, entry.round)
                }
                session.pendingUris = listOf(candidate.uri)
                val loaded = loadCandidate(candidate) ?: continue
                val (frame, resolved) = loaded
                if (!resolved.orientation.needsPairing(isLandscape())) {
                    session.buildingPrimary = null
                    session.buildingPartner = null
                    session.pendingUris = listOf(frame.uri)
                    return FrameSlide(listOf(frame))
                }
                primary = frame
                resolvedPrimary = resolved
                session.buildingPrimary = resolved
            }
            val first = requireNotNull(primary)
            val firstCandidate = requireNotNull(resolvedPrimary)
            while (true) {
                currentCoroutineContext().ensureActive()
                val deferredMatch = session.deferredPhotos.indexOfFirst {
                    it.uri != first.uri && canPair(firstCandidate.orientation, it.orientation)
                }
                if (deferredMatch >= 0) {
                    val partnerCandidate = session.deferredPhotos.removeAt(deferredMatch)
                    session.buildingPartner = partnerCandidate
                    val loaded = loadCandidate(partnerCandidate)
                    if (loaded == null) {
                        session.buildingPartner = null
                        continue
                    }
                    val (partner, resolvedPartner) = loaded
                    session.buildingPrimary = null
                    session.buildingPartner = null
                    if (canPair(firstCandidate.orientation, resolvedPartner.orientation)) {
                        session.pendingUris = listOf(first.uri, partner.uri)
                        return FrameSlide(listOf(first, partner))
                    }
                    return if (!resolvedPartner.orientation.needsPairing(isLandscape())) {
                        session.deferredPhotos += firstCandidate
                        session.pendingUris = listOf(partner.uri)
                        FrameSlide(listOf(partner))
                    } else {
                        session.deferredPhotos += resolvedPartner
                        session.pendingUris = listOf(first.uri)
                        FrameSlide(listOf(first))
                    }
                }
                val entry = queue.nextEntry()
                if (entry == null || entry.uri == first.uri) {
                    session.buildingPrimary = null
                    session.buildingPartner = null
                    session.pendingUris = listOf(first.uri)
                    return FrameSlide(listOf(first))
                }
                session.pendingUris = listOf(first.uri)
                session.buildingPartner = FramePhotoCandidate(entry.uri, FramePhotoOrientation.UNKNOWN, entry.round)
                val loaded = loadCandidate(session.buildingPartner!!)
                if (loaded == null) {
                    session.buildingPartner = null
                    continue
                }
                val (partner, resolvedPartner) = loaded
                session.buildingPrimary = null
                session.buildingPartner = null
                if (canPair(firstCandidate.orientation, resolvedPartner.orientation)) {
                    session.pendingUris = listOf(first.uri, partner.uri)
                    return FrameSlide(listOf(first, partner))
                }
                // The second photo ends the wait: show its single when it fits
                // the screen, otherwise show the first alone. Either way the
                // other photo waits in deferredPhotos for a later pair.
                session.deferredPhotos += firstCandidate
                if (!resolvedPartner.orientation.needsPairing(isLandscape())) {
                    session.pendingUris = listOf(partner.uri)
                    return FrameSlide(listOf(partner))
                }
                session.deferredPhotos += resolvedPartner
                session.pendingUris = listOf(first.uri)
                return FrameSlide(listOf(first))
            }
        }
        fun recordSlide(slide: FrameSlide<T>) {
            val pendingHistory = session.pendingHistoryIndex
                ?.takeIf { session.slideHistory.getOrNull(it) == slide.uris }
            if (pendingHistory != null) {
                session.slideIndex = pendingHistory
                session.pendingHistoryIndex = null
            } else if (session.slideHistory.getOrNull(session.slideIndex) != slide.uris) {
                if (session.slideIndex < session.slideHistory.lastIndex) {
                    session.slideHistory.subList(session.slideIndex + 1, session.slideHistory.size).clear()
                }
                if (session.slideHistory.lastOrNull() != slide.uris) {
                    session.slideHistory += slide.uris
                    while (session.slideHistory.size > FRAME_HISTORY_LIMIT) {
                        session.slideHistory.removeAt(0)
                    }
                }
                session.slideIndex = session.slideHistory.lastIndex
            }
            session.currentUris = slide.uris
            if (session.pendingUris == slide.uris) {
                session.pendingUris = null
            }
        }
        suspend fun advanceSlide(): FrameSlide<T>? {
            if (session.slideIndex < session.slideHistory.lastIndex) {
                val nextHistoryIndex = session.slideIndex + 1
                loadSlideByUris(session.slideHistory[nextHistoryIndex])?.let { slide ->
                    session.pendingHistoryIndex = nextHistoryIndex
                    return slide
                }
                session.slideHistory.subList(session.slideIndex + 1, session.slideHistory.size).clear()
                session.pendingHistoryIndex = null
            }
            session.pendingHistoryIndex = null
            return buildNextSlide()
        }
        suspend fun loadPreviousSlide(current: FrameSlide<T>?): FrameSlide<T>? {
            val currentIndex = current?.let { session.slideHistory.lastIndexOf(it.uris) }
                ?.takeIf { it >= 0 } ?: session.slideIndex
            var targetIndex = currentIndex - 1
            while (targetIndex >= 0) {
                currentCoroutineContext().ensureActive()
                val uris = session.slideHistory[targetIndex]
                loadSlideByUris(uris)?.let {
                    session.pendingHistoryIndex = targetIndex
                    return it
                }
                targetIndex--
            }
            session.pendingHistoryIndex = null
            return null
        }

        var current = session.currentUris.takeIf { it.isNotEmpty() }?.let { loadSlideByUris(it) }
        if (current == null) {
            current = if (session.slideHistory.isEmpty()) buildFirstSlide() else buildNextSlide()
            session.pendingUris = null
        }
        if (current == null) {
            session.currentUris = emptyList()
            publish(FramePlaybackState(exhausted = !session.empty))
            return@coroutineScope
        }
        recordSlide(current)
        publish(FramePlaybackState(current))
        if (session.single) return@coroutineScope

        while (true) {
            val started = nowMillis()
            val preload = async { advanceSlide() }
            var command: FramePlaybackCommand? = null
            var incoming: FrameSlide<T>? = null
            try {
                command = session.awaitCommand(session.remainingMillis)
                if (command == null) {
                    incoming = preload.await()
                    if (incoming != null) {
                        delay((session.remainingMillis - (nowMillis() - started)).coerceAtLeast(0))
                    }
                } else if (command == FramePlaybackCommand.NEXT && preload.isCompleted) {
                    incoming = preload.await()
                } else {
                    preload.cancelAndJoin()
                    val requestedCommand = checkNotNull(command)
                    incoming = when (requestedCommand) {
                        FramePlaybackCommand.PREVIOUS -> loadPreviousSlide(current)
                        FramePlaybackCommand.NEXT -> advanceSlide()
                    }
                }
            } finally {
                preload.cancel()
                session.remainingMillis = (session.remainingMillis - (nowMillis() - started)).coerceAtLeast(0)
            }
            if (incoming == null) {
                if (command == FramePlaybackCommand.PREVIOUS) {
                    session.remainingMillis = intervalMillis
                    continue
                }
                session.currentUris = emptyList()
                publish(FramePlaybackState(exhausted = true))
                return@coroutineScope
            }
            if (incoming.uris == current?.uris) {
                // An identically reshuffled slide (e.g. a lone pair) must not
                // cross-fade into itself; drop the rejected preload, keep the
                // current frame, and look again after a fresh interval.
                session.pendingUris = null
                session.remainingMillis = intervalMillis
                continue
            }
            publish(FramePlaybackState(current, incoming))
            delay(transitionMillis)
            current = incoming
            recordSlide(incoming)
            session.remainingMillis = intervalMillis
            publish(FramePlaybackState(current))
        }
    }
}
