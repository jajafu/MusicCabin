package com.metrolist.music.photo.v2.drive

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DriveProbePreferences(
    val enabled: Boolean = false,
    val account: DriveAccount? = null,
    val selectedPath: List<DriveFile>? = null,
)

interface DriveProbeSettings {
    suspend fun read(): DriveProbePreferences
    suspend fun write(value: DriveProbePreferences)
}

enum class DriveProbeStage { READY, AUTHORIZING, CONNECTED, LISTING, LISTED, DOWNLOADING, PREVIEWED, REFRESHED, CANCELLED }

data class DriveProbeState<P>(
    val initialized: Boolean = false,
    val enabled: Boolean = false,
    val busy: Boolean = false,
    val awaitingConsent: Boolean = false,
    val connected: Boolean = false,
    val account: DriveAccount? = null,
    val path: List<DriveFile> = emptyList(),
    val selectedPath: List<DriveFile>? = null,
    val folders: List<DriveFile> = emptyList(),
    val selectionRevision: Int = 0,
    val photos: List<DriveFile> = emptyList(),
    val listed: Boolean = false,
    val progress: Int = 0,
    val preview: P? = null,
    val previewName: String? = null,
    val stage: DriveProbeStage = DriveProbeStage.READY,
    val error: DriveException? = null,
) {
    val folderId: String get() = path.lastOrNull()?.id ?: "root"
}

/** Confined to the caller's UI dispatcher. Every external completion must pass the session gate. */
class DriveProbeController<P>(
    private val scope: CoroutineScope,
    private val available: Boolean,
    private val settings: DriveProbeSettings,
    private val authorizationFactory: DriveAuthorizationFactory,
    private val repositoryFactory: (DriveAuthorizationProvider) -> DrivePhotoAccess,
    private val loadPreview: suspend (DrivePhotoAccess, DriveAccount, DriveFile) -> P,
    private val onFolderSelected: (DrivePhotoAccess, DriveAccount, List<DriveFile>, List<DriveFile>) -> Unit = { _, _, _, _ -> },
    private val onStop: () -> Unit = {},
) {
    private val mutableState = MutableStateFlow(DriveProbeState<P>())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var operation: Job? = null
    private var authorization: DriveAuthorizationProvider? = null
    private var repository: DrivePhotoAccess? = null

    fun initialize() {
        if (state.value.initialized || state.value.busy) return
        runOperation {
            val saved = settings.read()
            checkCurrent()
            update { copy(initialized = true, enabled = saved.enabled && available, account = saved.account, selectedPath = saved.selectedPath) }
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (!state.value.initialized) return
        stop()
        mutableState.value = state.value.copy(enabled = enabled && available, error = null)
        runOperation { settings.write(DriveProbePreferences(state.value.enabled, state.value.account, state.value.selectedPath)) }
    }

    fun resumeSavedFolder() {
        if (state.value.account == null || state.value.selectedPath == null) return
        if (state.value.connected) {
            if (canStart()) runOperation { selectPhotos(state.value.selectedPath!!, persist = false, startPlayback = true) }
        } else {
            connect(DriveAuthorizationUi { throw DriveException(DriveFailure.REAUTHORIZE) }, startSavedPlayback = true)
        }
    }

    fun connect(ui: DriveAuthorizationUi, startSavedPlayback: Boolean = false) {
        if (!canStart(requireConnection = false)) return
        runOperation {
            val provider = authorization ?: authorizationFactory.create().also { authorization = it }
            update { copy(stage = DriveProbeStage.AUTHORIZING) }
            provider.authorize(state.value.account?.emailAddress, DriveAuthorizationUi { sender ->
                checkCurrent()
                update { copy(awaitingConsent = true) }
                try { ui.resolve(sender) } finally {
                    if (current()) update { copy(awaitingConsent = false) }
                }
            })
            checkCurrent()
            val api = repository ?: repositoryFactory(provider).also { repository = it }
            val account = api.account()
            checkCurrent()
            if (state.value.account?.permissionId?.let { it != account.permissionId } == true) {
                provider.clear()
                throw DriveException(DriveFailure.REAUTHORIZE)
            }
            provider.bindAccount(account.emailAddress)
            settings.write(DriveProbePreferences(enabled = true, account = account, selectedPath = state.value.selectedPath))
            checkCurrent()
            update { copy(account = account, connected = true, stage = DriveProbeStage.CONNECTED) }
            if (startSavedPlayback) selectPhotos(state.value.selectedPath!!, persist = false, startPlayback = true)
            else browse(api, state.value.selectedPath ?: emptyList())
        }
    }

    fun openFolder(folder: DriveFile) {
        if (!canStart() || folder !in state.value.folders) return
        runOperation { browse(repository!!, state.value.path + folder) }
    }

    fun parentFolder() {
        if (!canStart() || state.value.path.isEmpty()) return
        runOperation { browse(repository!!, state.value.path.dropLast(1)) }
    }

    fun refreshFolders() {
        if (!canStart()) return
        runOperation { browse(repository!!, state.value.path) }
    }

    fun useFolder() = listPhotos(startPlayback = true)

    fun listPhotos(startPlayback: Boolean = false) {
        if (!canStart()) return
        runOperation { selectPhotos(state.value.path, persist = startPlayback, startPlayback = startPlayback) }
    }

    private suspend fun Operation.selectPhotos(path: List<DriveFile>, persist: Boolean, startPlayback: Boolean) {
        update { copy(stage = DriveProbeStage.LISTING, progress = 0, listed = false, photos = emptyList(), preview = null, previewName = null) }
        val photos = repository!!.photos(path.lastOrNull()?.id ?: "root") { count ->
            if (current()) update { copy(progress = count) }
        }
        checkCurrent()
        if (persist) {
            settings.write(DriveProbePreferences(state.value.enabled, state.value.account, path))
            checkCurrent()
            update { copy(selectedPath = path, selectionRevision = selectionRevision + 1) }
        }
        update { copy(path = path, photos = photos, listed = true, stage = DriveProbeStage.LISTED) }
        if (startPlayback) onFolderSelected(repository!!, state.value.account!!, path, photos)
    }

    fun preview(photo: DriveFile) {
        if (!canStart() || photo !in state.value.photos || !photo.canPreview) return
        runOperation {
            update { copy(stage = DriveProbeStage.DOWNLOADING, preview = null, previewName = null) }
            val image = loadPreview(repository!!, state.value.account!!, photo)
            checkCurrent()
            update { copy(preview = image, previewName = photo.name, stage = DriveProbeStage.PREVIEWED) }
        }
    }

    fun refreshToken() {
        if (!canStart()) return
        runOperation {
            authorization!!.accessToken(forceRefresh = true)
            checkCurrent()
            val account = repository!!.account()
            checkCurrent()
            if (account.permissionId != state.value.account?.permissionId) throw DriveException(DriveFailure.REAUTHORIZE)
            update { copy(stage = DriveProbeStage.REFRESHED) }
        }
    }

    fun disconnect() {
        if (!state.value.initialized) return
        stop()
        mutableState.value = state.value.copy(account = null, selectedPath = null)
        runOperation { settings.write(DriveProbePreferences(state.value.enabled)) }
    }

    fun background() {
        if (!state.value.awaitingConsent) stop()
    }

    fun requireReconnect() {
        authorization?.clear()
        mutableState.value = state.value.copy(connected = false)
    }

    fun reportStorageError() {
        mutableState.value = state.value.copy(error = DriveException(DriveFailure.STORAGE))
    }

    fun stop() {
        onStop()
        generation++
        operation?.cancel()
        operation = null
        authorization?.clear()
        authorization = null
        repository?.close()
        repository = null
        mutableState.value = state.value.copy(
            busy = false, awaitingConsent = false, connected = false, path = emptyList(), folders = emptyList(),
            photos = emptyList(), listed = false, progress = 0, preview = null, previewName = null,
            stage = DriveProbeStage.CANCELLED, error = null,
        )
    }

    private fun canStart(requireConnection: Boolean = true): Boolean = state.value.let {
        available && it.initialized && it.enabled && !it.busy && (!requireConnection || it.connected)
    }

    private suspend fun Operation.browse(api: DrivePhotoAccess, path: List<DriveFile>) {
        update { copy(stage = DriveProbeStage.LISTING, progress = 0) }
        val folders = api.folders(path.lastOrNull()?.id ?: "root") { count ->
            if (current()) update { copy(progress = count) }
        }
        checkCurrent()
        update { copy(path = path, folders = folders, photos = emptyList(), listed = false, preview = null, previewName = null, stage = DriveProbeStage.CONNECTED) }
    }

    private fun runOperation(block: suspend Operation.() -> Unit) {
        if (state.value.busy) return
        val context = Operation(++generation)
        mutableState.value = state.value.copy(busy = true, error = null)
        operation = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                context.block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (context.current()) {
                    val failure = error as? DriveException ?: DriveException(DriveFailure.UNKNOWN)
                    if (failure.failure in setOf(DriveFailure.REAUTHORIZE, DriveFailure.PERMISSION, DriveFailure.UNAVAILABLE)) {
                        authorization?.clear()
                        context.update { copy(connected = false, folders = emptyList(), photos = emptyList(), listed = false, preview = null, previewName = null) }
                    }
                    context.update { copy(error = failure) }
                }
            } finally {
                if (context.current()) context.update { copy(busy = false, awaitingConsent = false) }
            }
        }
    }

    private inner class Operation(private val id: Long) {
        fun current() = id == generation
        suspend fun checkCurrent() {
            currentCoroutineContext().ensureActive()
            if (!current()) throw CancellationException()
        }
        fun update(transform: DriveProbeState<P>.() -> DriveProbeState<P>) {
            if (current()) mutableState.value = state.value.transform()
        }
    }
}
