package com.metrolist.music.viewmodels

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.photo.FrameError
import com.metrolist.music.photo.FrameSettings
import com.metrolist.music.photo.FramePhotoReceiver
import com.metrolist.music.photo.PhotoCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@HiltViewModel
class PhotoFrameViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalog: PhotoCatalog,
) : ViewModel() {
    data class TransferState(val url: String? = null, val count: Int = 0, val error: Boolean = false)

    val state = catalog.state
    private val _transfer = MutableStateFlow(TransferState())
    val transfer = _transfer.asStateFlow()
    private val _error = MutableStateFlow<FrameError?>(null)
    val error = _error.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _generation = MutableStateFlow(0)
    val generation = _generation.asStateFlow()
    private var operation: Job? = null
    private var settingsOperation: Job? = null
    private var operationId = 0L
    private var receiverJob: Job? = null
    private var receiver: FramePhotoReceiver? = null
    private var receiverGeneration = 0
    private var receiverRequested = false
    private var receiving = false

    fun initialize() {
        if (!state.value.initialized) runOperation { catalog.initialize() }
    }

    fun addPhotos(uris: List<Uri>) = runOperation { catalog.addPhotos(uris); _generation.value++ }
    fun rescan() = runOperation { catalog.rescan(); _generation.value++ }
    fun removeSource(uri: String) = runOperation {
        catalog.removeSource(uri)
        if (state.value.sources.none { it.uri == uri }) deleteReceivedCopy(uri)
        _generation.value++
    }
    fun clear() = runOperation {
        closeReceiver()
        try {
            catalog.clear()
            if (state.value.sources.isNotEmpty()) throw IOException("Cannot clear photo sources")
            deleteReceivedFiles()
            _generation.value++
        } finally {
            if (receiverRequested) startReceiver()
        }
    }
    fun clearReceived() = runOperation {
        closeReceiver()
        try {
            val received = state.value.sources.map { it.uri }
                .filter { FramePhotoReceiver.isImportedUri(context, it) }.toSet()
            catalog.removeSources(received)
            if (state.value.sources.any { it.uri in received }) throw IOException("Cannot clear received photos")
            deleteReceivedFiles()
            _generation.value++
        } finally {
            if (receiverRequested) startReceiver()
        }
    }

    fun startReceiver() {
        receiverRequested = true
        if (receiving) return
        receiving = true
        val current = ++receiverGeneration
        _transfer.value = TransferState()
        receiverJob = viewModelScope.launch {
            val server = FramePhotoReceiver(context) { file ->
                runBlocking { catalog.addPhotos(listOf(file.toUri())) }
                if (state.value.sources.none { it.uri == file.toUri().toString() }) {
                    throw IOException("Cannot add received photo")
                }
                _generation.value++
                _transfer.value = _transfer.value.copy(count = _transfer.value.count + 1)
            }
            try {
                val url = withContext(Dispatchers.IO) { server.start() }
                if (receiving && receiverGeneration == current) {
                    receiver = server
                    _transfer.value = _transfer.value.copy(url = url)
                } else server.close()
            } catch (cancelled: CancellationException) {
                server.close()
                throw cancelled
            } catch (_: Exception) {
                server.close()
                if (receiving && receiverGeneration == current) _transfer.value = TransferState(error = true)
            }
        }
    }

    fun stopReceiver() {
        receiverRequested = false
        closeReceiver()
    }

    private fun closeReceiver() {
        receiving = false
        receiverGeneration++
        receiverJob?.cancel()
        receiverJob = null
        receiver?.close()
        receiver = null
        _transfer.value = TransferState()
    }

    private fun deleteReceivedCopy(uri: String) {
        if (!FramePhotoReceiver.isImportedUri(context, uri)) return
        uri.toUri().path?.let { File(it).delete() }
    }

    private fun deleteReceivedFiles() {
        FramePhotoReceiver.importsDirectory(context).listFiles()?.forEach { file ->
            if (file.isFile && !file.delete()) throw IOException("Cannot delete received photo")
        }
    }
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

    override fun onCleared() {
        stopReceiver()
        super.onCleared()
    }
}
