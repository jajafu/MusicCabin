package com.metrolist.music.tv

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.photo.FrameError
import com.metrolist.music.photo.FrameSettings
import com.metrolist.music.photo.PhotoCatalog
import com.metrolist.music.photo.v2.LocalPhotoCatalog
import com.metrolist.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@HiltViewModel
class TvPhotoFrameViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalog: PhotoCatalog,
    private val previousTvCatalog: LocalPhotoCatalog,
) : ViewModel() {
    data class TransferState(val url: String? = null, val count: Int = 0, val error: Boolean = false)
    enum class Source { LOCAL, TRANSFER }

    val state = catalog.state
    private val mutableSource = MutableStateFlow(Source.LOCAL)
    val source = mutableSource.asStateFlow()
    private var sourceLoaded = false
    private val mutableError = MutableStateFlow<FrameError?>(null)
    val error = mutableError.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableGeneration = MutableStateFlow(0)
    val generation = mutableGeneration.asStateFlow()
    private val mutableTransfer = MutableStateFlow(TransferState())
    val transfer = mutableTransfer.asStateFlow()
    private var operation: Job? = null
    private var settingsOperation: Job? = null
    private var receiverJob: Job? = null
    private var receiver: TvPhotoReceiver? = null
    private var receiverGeneration = 0
    private var receiving = false
    private var receiverRequested = false

    fun initialize() {
        if (operation?.isActive == true) return
        runOperation {
            catalog.initialize()
            migratePreviousTvPhotos()
            if (!sourceLoaded) {
                val saved = context.dataStore.data.first()
                mutableSource.value = Source.entries.firstOrNull { it.name == saved[SourceKey] }
                    ?: when {
                        saved[PreviousSourceKey] == "DRIVE" -> Source.TRANSFER
                        state.value.sources.none { !TvPhotoReceiver.isImportedUri(context, it.uri) } &&
                            state.value.sources.any { TvPhotoReceiver.isImportedUri(context, it.uri) } -> Source.TRANSFER
                        else -> Source.LOCAL
                    }
                sourceLoaded = true
            }
        }
    }

    fun selectSource(source: Source) {
        mutableSource.value = source
        viewModelScope.launch {
            try { context.dataStore.edit { it[SourceKey] = source.name } }
            catch (_: IOException) { mutableError.value = FrameError.STORAGE }
        }
    }

    private suspend fun migratePreviousTvPhotos() {
        if (context.dataStore.data.first()[MigratedKey] == true) return
        previousTvCatalog.initialize()
        val legacy = previousTvCatalog.state.value
        if (!legacy.initialized) return
        val empty = state.value.sources.isEmpty()
        catalog.importSources(legacy.sources)
        val received = TvPhotoReceiver.importsDirectory(context).listFiles().orEmpty()
            .filter { it.isFile && it.extension.equals("jpg", ignoreCase = true) }
            .map { it.toUri() }
        if (received.isNotEmpty()) catalog.addPhotos(received)
        val expected = legacy.sources.map { it.uri } + received.map { it.toString() }
        if (!expected.all { uri -> state.value.sources.any { it.uri == uri } }) return
        if (empty && legacy.sources.isNotEmpty()) catalog.updateSettings(legacy.settings)
        catalog.rescan()
        context.dataStore.edit { it[MigratedKey] = true }
        mutableGeneration.value++
    }

    fun addPhotos(uris: List<Uri>) = runOperation { catalog.addPhotos(uris); mutableGeneration.value++ }
    fun rescan() = runOperation { catalog.rescan(); mutableGeneration.value++ }
    fun removeSource(uri: String) = runOperation {
        catalog.removeSource(uri)
        if (state.value.sources.none { it.uri == uri }) deleteImportedCopy(uri)
        mutableGeneration.value++
    }
    fun clearLocal() = runOperation {
        val selected = state.value.sources.map { it.uri }
            .filterNot { TvPhotoReceiver.isImportedUri(context, it) }.toSet()
        catalog.removeSources(selected)
        mutableGeneration.value++
    }
    fun clearImported() = runOperation {
        closeReceiver()
        try {
            val selected = state.value.sources.map { it.uri }
                .filter { TvPhotoReceiver.isImportedUri(context, it) }.toSet()
            catalog.removeSources(selected)
            if (state.value.sources.any { it.uri in selected }) throw IOException("Cannot clear imported photos")
            TvPhotoReceiver.importsDirectory(context).listFiles()?.forEach { file ->
                if (file.isFile && !file.delete()) throw IOException("Cannot delete imported photo")
            }
            mutableGeneration.value++
        } finally {
            if (receiverRequested) startReceiver()
        }
    }

    private fun deleteImportedCopy(uri: String) {
        if (!TvPhotoReceiver.isImportedUri(context, uri)) return
        uri.toUri().path?.let { java.io.File(it).delete() }
    }

    fun updateSettings(settings: FrameSettings) {
        settingsOperation?.cancel()
        settingsOperation = viewModelScope.launch {
            try { catalog.updateSettings(settings) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableError.value = FrameError.STORAGE }
        }
    }

    fun startReceiver() {
        receiverRequested = true
        if (receiving) return
        receiving = true
        val current = ++receiverGeneration
        mutableTransfer.value = TransferState()
        receiverJob = viewModelScope.launch {
            val server = TvPhotoReceiver(context) { file ->
                runBlocking { catalog.addPhotos(listOf(file.toUri())) }
                if (state.value.sources.none { it.uri == file.toUri().toString() }) {
                    throw IOException("Cannot add TV photo")
                }
                mutableGeneration.value++
                mutableTransfer.value = mutableTransfer.value.copy(count = mutableTransfer.value.count + 1)
            }
            try {
                val url = withContext(Dispatchers.IO) { server.start() }
                if (receiving && receiverGeneration == current) {
                    receiver = server
                    mutableTransfer.value = mutableTransfer.value.copy(url = url)
                } else server.close()
            } catch (cancelled: CancellationException) {
                server.close()
                throw cancelled
            } catch (_: Exception) {
                server.close()
                if (receiving && receiverGeneration == current) mutableTransfer.value = TransferState(error = true)
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
        mutableTransfer.value = TransferState()
    }

    fun cancelOperation() { operation?.cancel() }
    fun dismissError() { mutableError.value = null; catalog.clearError() }
    suspend fun markUnreadable(uri: String) = catalog.markUnreadable(uri)

    fun unavailableUris(): Set<String> {
        val snapshot = state.value
        val unavailable = snapshot.sources.filter { it.needsPermission || it.unavailable }.mapTo(hashSetOf()) { it.uri }
        return snapshot.photos.filter { it.sourceUri in unavailable }.mapTo(hashSetOf()) { it.uri }
    }

    private fun runOperation(block: suspend () -> Unit) {
        if (operation?.isActive == true) return
        operation = viewModelScope.launch {
            mutableBusy.value = true
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableError.value = FrameError.STORAGE }
            finally { mutableBusy.value = false }
        }
    }

    override fun onCleared() {
        stopReceiver()
        super.onCleared()
    }

    private companion object {
        val MigratedKey = booleanPreferencesKey("tv_photo_frame_v1_migrated")
        val SourceKey = stringPreferencesKey("tv_photo_frame_v1_source")
        val PreviousSourceKey = stringPreferencesKey("photo_frame_v2_source")
    }
}
