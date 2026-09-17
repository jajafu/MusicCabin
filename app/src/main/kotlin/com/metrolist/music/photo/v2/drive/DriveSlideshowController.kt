package com.metrolist.music.photo.v2.drive

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

data class DriveSlide<P>(val image: P, val fromCache: Boolean)

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
)

/** UI-dispatcher owner of the shuffle order. Prefetch reserves files without decoding images. */
class DriveSlideshowController<P>(
    private val scope: CoroutineScope,
    private val load: suspend (DriveAccount, DriveFile, DrivePhotoAccess, Boolean) -> DriveSlide<P>,
    private val prefetch: suspend (DriveAccount, DriveFile, DrivePhotoAccess) -> Unit,
    private val random: Random = Random.Default,
    private val waitInterval: suspend (Long) -> Unit = { delay(it) },
    private val onAuthorizationFailure: () -> Unit = {},
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
    private val failed = mutableSetOf<String>()
    private val queue = ArrayDeque<DriveFile>()
    private val history = mutableListOf<DriveFile>()
    private var historyIndex = -1
    private var suspended = false
    private var pendingPhoto: DriveFile? = null

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
        pendingPhoto = null
        this.account = account
        this.api = api
        this.photos = photos.filter { it.canPreview }.distinctBy { it.id }
        unsupportedCount = photos.size - this.photos.size
        downloadAllowed = allowDownloads
        failed.clear()
        queue.clear()
        val currentPhoto = state.value.photo?.id?.let { id -> this.photos.firstOrNull { it.id == id } }
        history.clear()
        historyIndex = if (currentPhoto == null) -1 else 0
        if (currentPhoto != null) history += currentPhoto
        mutableState.value = state.value.copy(
            active = true,
            loading = false,
            folderName = folderName,
            photo = currentPhoto,
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
                while (session == generation) {
                    currentCoroutineContext().ensureActive()
                    val targetIndex = if (previous) historyIndex - 1 else historyIndex + 1
                    val fromHistory = targetIndex in history.indices
                    if (previous && !fromHistory) break
                    replenish()
                    val photo = if (fromHistory) history[targetIndex] else queue.removeFirstOrNull() ?: break
                    pendingPhoto = if (fromHistory) null else photo
                    try {
                        val slide = load(selectedAccount, photo, selectedApi, downloadAllowed)
                        currentCoroutineContext().ensureActive()
                        if (session != generation) return@launch
                        pendingPhoto = null
                        if (fromHistory) historyIndex = targetIndex else {
                            history += photo
                            if (history.size > 200) history.removeAt(0)
                            historyIndex = history.lastIndex
                        }
                        mutableState.value = state.value.copy(current = slide, photo = photo, canPrevious = historyIndex > 0, error = if (downloadAllowed) null else state.value.error)
                        return@launch
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        if (session != generation) return@launch
                        pendingPhoto = null
                        if (fromHistory) {
                            history.removeAt(targetIndex)
                            if (targetIndex <= historyIndex) historyIndex--
                        }
                        val failure = error as? DriveException
                        if (failure?.failure in DOWNLOAD_INTERRUPTED) {
                            downloadAllowed = false
                            preloader?.cancel()
                        }
                        if (failure?.failure in AUTHORIZATION_INTERRUPTED) onAuthorizationFailure()
                        failed += photo.id
                        queue.removeAll { it.id == photo.id }
                        mutableState.value = state.value.copy(
                            skippedCount = unsupportedCount + failed.size,
                            canPrevious = historyIndex > 0,
                            error = if (error is DriveCacheMissException) state.value.error
                                else failure ?: DriveException(DriveFailure.UNKNOWN),
                        )
                    }
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
            pendingPhoto?.let { queue.addFirst(it) }
            pendingPhoto = null
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
        queue.clear()
        history.clear()
        historyIndex = -1
        pendingPhoto = null
        failed.clear()
        mutableState.value = DriveSlideshowState(intervalSeconds = state.value.intervalSeconds)
    }

    private fun replenish() {
        if (queue.isNotEmpty()) return
        val round = photos.filterNot { it.id in failed }.shuffled(random).toMutableList()
        if (round.size > 1 && round.first().id == state.value.photo?.id) {
            val swap = random.nextInt(1, round.size)
            round[0] = round[swap].also { round[swap] = round[0] }
        }
        queue.addAll(round)
    }

    private fun schedule() {
        if (suspended || !state.value.active || !state.value.playing || state.value.loading || state.value.current == null) return
        val session = generation
        val selectedAccount = account ?: return
        val selectedApi = api ?: return
        replenish()
        // The visible photo plus four random upcoming photos seeds five offline-ready frames.
        val upcoming = if (downloadAllowed) queue.filter { it.id != state.value.photo?.id }.take(4) else emptyList()
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
