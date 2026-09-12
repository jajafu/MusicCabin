package com.metrolist.music.photo.v2

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.photo.FrameError
import com.metrolist.music.photo.FramePlaybackSession
import com.metrolist.music.photo.FrameSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class LocalPhotoFrameViewModel @Inject constructor(private val catalog: LocalPhotoCatalog) : ViewModel() {
    val state = catalog.state
    private val _error = MutableStateFlow<FrameError?>(null)
    val error = _error.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _generation = MutableStateFlow(0)
    val generation = _generation.asStateFlow()
    private var operation: Job? = null
    private var settingsOperation: Job? = null
    private var operationId = 0L
    // The session holds URI strings only (no decoded images), so keeping it here
    // lets rotation reuse the shuffle order and resume at the current photo.
    private var sessionKey: Int? = null
    private var session: FramePlaybackSession? = null

    internal fun playbackSession(uris: List<String>, generation: Int): FramePlaybackSession {
        val key = 31 * uris.hashCode() + generation
        val cached = session
        if (cached == null || sessionKey != key) {
            return FramePlaybackSession(uris).also {
                session = it
                sessionKey = key
            }
        }
        return cached
    }

    fun initialize() {
        if (!state.value.initialized) runOperation { catalog.initialize() }
    }

    fun addPhotos(uris: List<Uri>) = runOperation { catalog.addPhotos(uris); _generation.value++ }
    fun rescan() = runOperation { catalog.rescan(); _generation.value++ }
    fun removeSource(uri: String) = runOperation { catalog.removeSource(uri) }
    fun clear() = runOperation { catalog.clear() }
    fun updateSettings(settings: FrameSettings) {
        settingsOperation?.cancel()
        settingsOperation = viewModelScope.launch {
            try {
                catalog.updateSettings(settings)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _error.value = FrameError.STORAGE
            }
        }
    }
    fun cancelOperation() { operation?.cancel() }
    fun dismissError() {
        _error.value = null
        catalog.clearError()
    }

    suspend fun markUnreadable(uri: String) = catalog.markUnreadable(uri)

    fun unavailableUris(): Set<String> {
        val snapshot = state.value
        val unavailable = snapshot.sources.filter { it.needsPermission || it.unavailable }.mapTo(hashSetOf()) { it.uri }
        return if (unavailable.isEmpty()) emptySet()
        else snapshot.photos.filter { it.sourceUri in unavailable }.mapTo(hashSetOf()) { it.uri }
    }

    private fun runOperation(block: suspend () -> Unit) {
        if (operation?.isActive == true) return
        val id = ++operationId
        operation = viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _error.value = FrameError.STORAGE
            } finally {
                if (operationId == id) _busy.value = false
            }
        }
    }
}
