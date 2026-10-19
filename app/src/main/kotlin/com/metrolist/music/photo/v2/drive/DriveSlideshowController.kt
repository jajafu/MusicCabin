package com.metrolist.music.photo.v2.drive

import com.metrolist.music.photo.FramePhotoOrientation
import com.metrolist.music.photo.canPair
import com.metrolist.music.photo.needsPairing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * One visible Drive interval: a single photo, or a pair of same-orientation
 * photos sharing the frame like the local photo frame playback does.
 */
data class DriveSlide<P>(val image: P, val fromCache: Boolean, val pairImage: P? = null)

data class DriveSlideshowState<P>(
    val active: Boolean = false,
    val playing: Boolean = false,
    val loading: Boolean = false,
    val folderName: String? = null,
    val current: DriveSlide<P>? = null,
    val photo: DriveFile? = null,
    val photoCount: Int = 0,
    val skippedCount: Int = 0,
    val canPrevious: Boolean = false,
    val intervalSeconds: Int = 10,
    val error: DriveException? = null,
    val pairPhoto: DriveFile? = null,
)

private class QueuedFile(val file: DriveFile, val round: Int)

/** A photo whose orientation was decoded; it waits for a later pair or single. */
private class DeferredFile(val file: DriveFile, val orientation: FramePhotoOrientation, val round: Int)

private class DrivePhoto<P>(val file: DriveFile, val image: P, val fromCache: Boolean, val orientation: FramePhotoOrientation)

/** UI-dispatcher owner of the shuffle order. Prefetch reserves files without decoding images. */
class DriveSlideshowController<P>(
    private val scope: CoroutineScope,
    private val load: suspend (DriveAccount, DriveFile, DrivePhotoAccess, Boolean) -> DriveSlide<P>,
    private val prefetch: suspend (DriveAccount, DriveFile, DrivePhotoAccess) -> Unit,
    private val random: Random = Random.Default,
    private val waitInterval: suspend (Long) -> Unit = { delay(it) },
    private val onAuthorizationFailure: () -> Unit = {},
    private val orientationOf: (P) -> FramePhotoOrientation = { FramePhotoOrientation.UNKNOWN },
) {
    private val mutableState = MutableStateFlow(DriveSlideshowState<P>())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var worker: Job? = null
    private var timer: Job? = null
    private var preloader: Job? = null
    private var account: DriveAccount? = null
    private var api: DrivePhotoAccess? = null
    private var photos = emptyList<DriveFile>()
    private var unsupportedCount = 0
    private var downloadAllowed = true
    private var round = 0
    private val failed = mutableSetOf<String>()
    private val queue = ArrayDeque<QueuedFile>()
    private val deferred = mutableListOf<DeferredFile>()
    /** Photo files pulled for the slide being built; suspension puts them back at the queue front. */
    private val pendingPhotos = mutableListOf<QueuedFile>()
    /** One entry per visible interval: the displayed primary, optionally its paired partner. */
    private val history = mutableListOf<List<DriveFile>>()
    private var historyIndex = -1
    private var suspended = false

    /** Screen orientation set by the player screen; pairing applies it on the next load. */
    private var landscapeScreen = true

    fun setLandscapeScreen(value: Boolean) {
        landscapeScreen = value
    }

    fun start(
        account: DriveAccount,
        api: DrivePhotoAccess,
        folderName: String?,
        photos: List<DriveFile>,
        allowDownloads: Boolean = true,
    ) {
        stop()
        this.account = account
        this.api = api
        this.photos = photos.filter { it.canPreview }.distinctBy { it.id }
        unsupportedCount = photos.size - this.photos.size
        downloadAllowed = allowDownloads
        mutableState.value = state.value.copy(
            active = true, playing = true, folderName = folderName, photoCount = this.photos.size,
            skippedCount = unsupportedCount,
        )
        next()
    }

    /** Switches a cache-only session to refreshed Drive metadata without clearing the visible frame. */
    fun updateSource(
        account: DriveAccount,
        api: DrivePhotoAccess,
        folderName: String?,
        photos: List<DriveFile>,
        allowDownloads: Boolean = true,
    ) {
        if (!state.value.active || this.account?.permissionId != account.permissionId) {
            start(account, api, folderName, photos, allowDownloads)
            return
        }
        generation++
        worker?.cancel()
        timer?.cancel()
        preloader?.cancel()
        worker = null
        timer = null
        preloader = null
        pendingPhotos.clear()
        deferred.clear()
        this.account = account
        this.api = api
        this.photos = photos.filter { it.canPreview }.distinctBy { it.id }
        unsupportedCount = photos.size - this.photos.size
        downloadAllowed = allowDownloads
        failed.clear()
        queue.clear()
        val currentPhoto = state.value.photo?.id?.let { id -> this.photos.firstOrNull { it.id == id } }
        // The revived slide is only complete when both photos survive; otherwise show single.
        val currentPairPhoto = if (currentPhoto == null) null
        else state.value.pairPhoto?.id?.let { id -> this.photos.firstOrNull { it.id == id } }
        history.clear()
        historyIndex = if (currentPhoto == null) -1 else 0
        if (currentPhoto != null) history += listOfNotNull(currentPhoto, currentPairPhoto)
        mutableState.value = state.value.copy(
            active = true,
            loading = false,
            folderName = folderName,
            photo = currentPhoto,
            pairPhoto = currentPairPhoto,
            photoCount = this.photos.size,
            skippedCount = unsupportedCount,
            canPrevious = false,
            error = null,
        )
        if (currentPhoto == null) next() else schedule()
    }

    fun next() = advance(previous = false)

    fun previous() {
        if (state.value.canPrevious) advance(previous = true)
    }

    private fun advance(previous: Boolean) {
        if (!state.value.active || state.value.loading || suspended) return
        timer?.cancel()
        val session = generation
        val selectedAccount = account ?: return
        val selectedApi = api ?: return
        mutableState.value = state.value.copy(loading = true)
        worker = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                suspend fun loadPhoto(file: DriveFile): DrivePhoto<P>? = try {
                    val slide = load(selectedAccount, file, selectedApi, downloadAllowed)
                    if (session != generation) null
                    else DrivePhoto(file, slide.image, slide.fromCache, orientationOf(slide.image))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (session == generation) recordFailure(file, error)
                    null
                }

                /** Builds 1..2 display files for the next interval, pairing within the current round. */
                suspend fun buildSlideFiles(): List<DrivePhoto<P>>? {
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        // A interrupted retry can leave both queues empty while this worker is
                        // still building; refill once more before giving up, so a single failed
                        // candidate cannot stop the slideshow while readable photos remain.
                        if (queue.isEmpty() && deferred.isEmpty()) replenish()
                        val candidate = deferred.removeFirstOrNull()?.let { QueuedFile(it.file, it.round) }
                            ?: queue.removeFirstOrNull()
                            ?: return null
                        pendingPhotos += candidate
                        val primary = loadPhoto(candidate.file)
                        if (session != generation) return null
                        if (primary == null) {
                            pendingPhotos.remove(candidate)
                            continue
                        }
                        if (!primary.orientation.needsPairing(landscapeScreen)) {
                            pendingPhotos.clear()
                            return listOf(primary)
                        }

                        // Portrait photos pair on landscape screens and landscapes stack on portrait
                        // screens; the partner must come from the same shuffle round. A loaded but
                        // mismatched partner waits in deferred for a later slide instead of repeating
                        // this interval, like FramePlaybackSession.deferredPhotos does.
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val partnerCandidate = deferred.indexOfFirst {
                                it.round == candidate.round && canPair(primary.orientation, it.orientation)
                            }.takeIf { it >= 0 }?.let { deferred.removeAt(it) }
                                ?.let { QueuedFile(it.file, it.round) }
                                ?: queue.removeFirstOrNull()
                            if (partnerCandidate == null) {
                                pendingPhotos.clear()
                                return listOf(primary)
                            }
                            if (partnerCandidate.round != candidate.round) {
                                // The primary's round is over (like a stale deferred photo after a
                                // refresh): stop the wait like the local engine does instead of
                                // decoding the whole fresh round within one interval.
                                deferred += DeferredFile(partnerCandidate.file, FramePhotoOrientation.UNKNOWN, partnerCandidate.round)
                                pendingPhotos.remove(partnerCandidate)
                                pendingPhotos.clear()
                                return listOf(primary)
                            }
                            pendingPhotos += partnerCandidate
                            val partner = loadPhoto(partnerCandidate.file)
                            if (session != generation) return null
                            if (partner == null) {
                                pendingPhotos.remove(partnerCandidate)
                                continue
                            }
                            if (canPair(primary.orientation, partner.orientation)) {
                                pendingPhotos.clear()
                                return listOf(primary, partner)
                            }
                            pendingPhotos.remove(partnerCandidate)
                            deferred += DeferredFile(
                                partner.file, partner.orientation, partnerCandidate.round,
                            )
                        }
                    }
                }

                while (session == generation) {
                    currentCoroutineContext().ensureActive()
                    val targetIndex = if (previous) historyIndex - 1 else historyIndex + 1
                    val fromHistory = targetIndex in history.indices
                    if (previous && !fromHistory) break
                    val slidePhotos: List<DrivePhoto<P>>? = if (fromHistory) {
                        history[targetIndex].take(2).mapNotNull { loadPhoto(it) }
                    } else {
                        replenish()
                        buildSlideFiles()
                    }
                    if (session != generation) return@launch
                    if (slidePhotos == null) break
                    if (slidePhotos.isEmpty()) {
                        // A replayed history entry with no readable photo is dropped; the walk continues.
                        if (fromHistory) {
                            history.removeAt(targetIndex)
                            if (targetIndex <= historyIndex) historyIndex--
                            continue
                        }
                        break
                    }
                    val primary = slidePhotos.first()
                    val partner = slidePhotos.getOrNull(1)
                    if (fromHistory) {
                        historyIndex = targetIndex
                    } else {
                        history += slidePhotos.map { it.file }
                        if (history.size > 200) history.removeAt(0)
                        historyIndex = history.lastIndex
                    }
                    mutableState.value = state.value.copy(
                        current = DriveSlide(primary.image, primary.fromCache, partner?.image),
                        photo = primary.file,
                        pairPhoto = partner?.file,
                        canPrevious = historyIndex > 0,
                        error = if (downloadAllowed) null else state.value.error,
                    )
                    return@launch
                }
                if (session == generation) mutableState.value = state.value.copy(playing = false)
            } finally {
                if (session == generation) {
                    mutableState.value = state.value.copy(loading = false)
                    schedule()
                }
            }
        }
    }

    private fun recordFailure(file: DriveFile, error: Exception) {
        val failure = error as? DriveException
        if (failure?.failure in DOWNLOAD_INTERRUPTED) {
            downloadAllowed = false
            preloader?.cancel()
        }
        if (failure?.failure in AUTHORIZATION_INTERRUPTED) onAuthorizationFailure()
        failed += file.id
        queue.removeAll { it.file.id == file.id }
        mutableState.value = state.value.copy(
            skippedCount = unsupportedCount + failed.size,
            error = if (error is DriveCacheMissException) state.value.error
                else failure ?: DriveException(DriveFailure.UNKNOWN),
        )
    }

    fun togglePlaying() {
        if (!state.value.active) return
        val playing = !state.value.playing
        mutableState.value = state.value.copy(playing = playing)
        timer?.cancel()
        preloader?.cancel()
        if (playing) {
            // A manual retry can recover files skipped during a temporary network outage.
            if (failed.isNotEmpty() || !downloadAllowed) {
                failed.clear()
                queue.clear()
                // Stale deferred photos carry expired rounds; a fresh retry starts clean.
                // pendingPhotos belongs to the in-flight worker and stays as is.
                deferred.clear()
                downloadAllowed = true
                mutableState.value = state.value.copy(skippedCount = unsupportedCount, error = null)
            }
            if (state.value.current == null) next() else schedule()
        }
    }

    fun setInterval(seconds: Int) {
        if (seconds !in INTERVALS) return
        mutableState.value = state.value.copy(intervalSeconds = seconds)
        timer?.cancel()
        schedule()
    }

    fun setSuspended(value: Boolean) {
        if (suspended == value) return
        suspended = value
        if (value) {
            generation++
            worker?.cancel()
            timer?.cancel()
            preloader?.cancel()
            for (entry in pendingPhotos.reversed()) {
                queue.removeAll { it.file.id == entry.file.id }
                deferred.removeAll { it.file.id == entry.file.id }
                queue.addFirst(QueuedFile(entry.file, entry.round))
            }
            pendingPhotos.clear()
            mutableState.value = state.value.copy(loading = false)
        } else if (state.value.active) {
            if (state.value.current == null) next() else schedule()
        }
    }

    fun stop() {
        generation++
        worker?.cancel()
        timer?.cancel()
        preloader?.cancel()
        worker = null
        timer = null
        preloader = null
        account = null
        api = null
        photos = emptyList()
        downloadAllowed = true
        round = 0
        queue.clear()
        deferred.clear()
        pendingPhotos.clear()
        history.clear()
        historyIndex = -1
        failed.clear()
        mutableState.value = DriveSlideshowState(intervalSeconds = state.value.intervalSeconds)
    }

    private fun replenish() {
        if (queue.isNotEmpty()) return
        round++
        val files = photos.filterNot { it.id in failed }.shuffled(random).toMutableList()
        // A new round must not open with a photo of the just-displayed interval, matching
        // the local engine's previous-photo guard: the head alone is protected here, and
        // a small folder may still repeat its only remaining photos.
        val blocked = state.value.let { setOfNotNull(it.photo?.id, it.pairPhoto?.id) }
        if (files.size > 1 && files.first().id in blocked) {
            val swap = files.indices.drop(1).firstOrNull { files[it].id !in blocked }
            if (swap != null) files[0] = files[swap].also { files[swap] = files[0] }
        }
        queue.addAll(files.map { QueuedFile(it, round) })
    }

    private fun schedule() {
        if (suspended || !state.value.active || !state.value.playing || state.value.loading || state.value.current == null) return
        val session = generation
        val selectedAccount = account ?: return
        val selectedApi = api ?: return
        replenish()
        val visiblePhotoId = setOfNotNull(state.value.photo?.id, state.value.pairPhoto?.id)
        // The visible interval plus four random upcoming photos seeds five offline-ready frames.
        val upcoming = if (downloadAllowed) queue.filter { it.file.id !in visiblePhotoId }.take(4).map { it.file } else emptyList()
        if (!downloadAllowed) preloader?.cancel()
        if (preloader?.isActive != true) {
            preloader = scope.launch {
                for (photo in upcoming) {
                    try {
                        prefetch(selectedAccount, photo, selectedApi)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Retry through foreground loading; speculative failures do not mark a photo corrupt.
                        break
                    }
                    currentCoroutineContext().ensureActive()
                    if (session != generation) return@launch
                }
            }
        }
        timer?.cancel()
        if (photos.count { it.id !in failed } <= 1) return
        timer = scope.launch {
            waitInterval(state.value.intervalSeconds * 1000L)
            if (session == generation) next()
        }
    }

    companion object {
        val INTERVALS = listOf(5, 10, 15, 30, 60)
        private val DOWNLOAD_INTERRUPTED = setOf(
            DriveFailure.NETWORK, DriveFailure.TIMEOUT, DriveFailure.RATE_LIMITED, DriveFailure.SERVER,
            DriveFailure.REAUTHORIZE, DriveFailure.PERMISSION, DriveFailure.UNAVAILABLE,
            DriveFailure.CONFIGURATION, DriveFailure.STORAGE,
        )
        private val AUTHORIZATION_INTERRUPTED = setOf(
            DriveFailure.REAUTHORIZE, DriveFailure.PERMISSION, DriveFailure.UNAVAILABLE, DriveFailure.CONFIGURATION,
        )
    }
}
