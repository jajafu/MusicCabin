package com.metrolist.music.photo

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.tv.isAndroidTv
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PhotoCatalog internal constructor(
    private val preferencesFactory: () -> DataStore<Preferences>,
    private val documentsFactory: () -> FrameDocumentAccess,
    private val manifestFactory: () -> PhotoFrameManifest,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        { context.dataStore },
        { AndroidFrameDocumentAccess(context, allowTvImports = isAndroidTv(context)) },
        { PhotoFrameManifest(File(context.filesDir, "photo_frame/index-v1.json")) },
    )

    private val preferences by lazy(preferencesFactory)
    private val documents by lazy(documentsFactory)
    private val manifest by lazy(manifestFactory)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(FrameCatalogState())
    val state: StateFlow<FrameCatalogState> = mutableState.asStateFlow()

    private var folderPhotos = emptyList<FramePhoto>()
    private var scannedFolders = emptySet<String>()
    private val failedPhotos = hashSetOf<String>()

    suspend fun initialize() = operation(initializeFirst = false) {
        if (!state.value.initialized) initializeLocked()
    }

    suspend fun addPhotos(uris: List<Uri>) = operation {
        val sources = state.value.sources.associateByTo(linkedMapOf()) { it.uri }
        for (uri in uris.distinct()) {
            currentCoroutineContext().ensureActive()
            try {
                documents.persistRead(uri)
                val photo = documents.picked(uri)
                sources[uri.toString()] = FrameSource(uri.toString(), photo.name, FrameSelectionType.PICKED_PHOTO, photoCount = 1, scanned = true)
                failedPhotos.remove(uri.toString())
            } catch (error: Exception) {
                rethrowCancellation(error)
                setError(sourceError(error))
            }
        }
        saveSources(sources.values.toList())
    }

    suspend fun rescan() = operation {
        failedPhotos.clear()
        scanSources(state.value.sources)
    }

    suspend fun removeSource(uri: String) = operation {
        saveSources(state.value.sources.filterNot { it.uri == uri })
        pruneIndex()
    }

    suspend fun removeSources(uris: Set<String>) = operation {
        saveSources(state.value.sources.filterNot { it.uri in uris })
        pruneIndex()
    }

    suspend fun importSources(sources: List<FrameSource>) = operation {
        val existing = state.value.sources.mapTo(hashSetOf()) { it.uri }
        val additions = sources.distinctBy { it.uri }.filter { it.uri !in existing }.map { validate(it) }
        if (additions.isNotEmpty()) saveSources(state.value.sources + additions)
    }

    suspend fun clear() = operation {
        saveSources(emptyList())
        failedPhotos.clear()
        pruneIndex()
    }

    suspend fun updateSettings(settings: FrameSettings) = operation {
        val validated = settings.validated()
        preferences.edit { it[SettingsKey] = json.encodeToString(validated) }
        mutableState.update { it.copy(settings = validated) }
    }

    fun clearError() {
        mutableState.update { it.copy(error = null) }
    }

    suspend fun markUnreadable(uri: String) = operation {
        failedPhotos.add(uri)
        val affected = state.value.sources.map { source ->
            val members = if (source.type == FrameSelectionType.PICKED_PHOTO) listOf(source.uri)
                else folderPhotos.filter { it.sourceUri == source.uri }.map { it.uri }
            if (uri !in members) return@map source
            val sourceUri = source.uri.toUri()
            val needsPermission = if (source.type == FrameSelectionType.FOLDER) {
                !documents.hasPersistedRead(sourceUri)
            } else {
                try {
                    documents.picked(sourceUri)
                    false
                } catch (error: Exception) {
                    rethrowCancellation(error)
                    error is SecurityException
                }
            }
            val rootUnavailable = if (source.type == FrameSelectionType.FOLDER && !needsPermission) {
                try {
                    withTimeoutOrNull(3_000) { documents.folder(sourceUri) } == null
                } catch (error: Exception) {
                    rethrowCancellation(error)
                    true
                }
            } else false
            source.copy(
                needsPermission = needsPermission,
                unavailable = rootUnavailable || (members.isNotEmpty() && members.all { it in failedPhotos }),
                unreadableCount = if (rootUnavailable || needsPermission) members.size else members.count { it in failedPhotos },
            )
        }
        // Keep the playback snapshot stable; the controller skips failed URIs for this session.
        mutableState.update { it.copy(sources = affected, error = FrameError.UNREADABLE) }
    }

    private suspend fun initializeLocked() {
        val stored = preferences.data.first()
        var damaged = false
        val sources = try {
            stored[SourcesKey]?.let { json.decodeFromString<List<FrameSource>>(it) }.orEmpty().distinctBy { it.uri }
        } catch (_: SerializationException) {
            damaged = true
            emptyList()
        }
        val settings = try {
            stored[SettingsKey]?.let { json.decodeFromString<FrameSettings>(it) }?.validated() ?: FrameSettings()
        } catch (_: SerializationException) {
            damaged = true
            FrameSettings()
        }
        val read = manifest.read()
        val folderUris = sources.filter { it.type == FrameSelectionType.FOLDER }.mapTo(hashSetOf()) { it.uri }
        folderPhotos = read.manifest.photos.filter { it.sourceUri in folderUris }
        scannedFolders = read.manifest.scannedFolders.intersect(folderUris)
        val checked = sources.map { validate(it) }
        mutableState.value = FrameCatalogState(
            sources = checked,
            photos = mergeFramePhotos(checked, folderPhotos),
            settings = settings,
            error = when {
                damaged || read.damaged -> FrameError.MANIFEST
                checked.any { it.needsPermission } -> FrameError.PERMISSION
                checked.any { it.unavailable } -> FrameError.UNREADABLE
                else -> null
            },
            initialized = true,
        )
    }

    private suspend fun validate(source: FrameSource): FrameSource {
        currentCoroutineContext().ensureActive()
        val uri = source.uri.toUri()
        val count = if (source.type == FrameSelectionType.PICKED_PHOTO) 1 else folderPhotos.count { it.sourceUri == source.uri }
        val base = source.copy(photoCount = count, needsPermission = false, unavailable = false, unreadableCount = 0, scanned = source.type == FrameSelectionType.PICKED_PHOTO || source.uri in scannedFolders)
        return try {
            if (source.type == FrameSelectionType.FOLDER) {
                if (!documents.hasPersistedRead(uri)) return base.copy(needsPermission = true)
                base.copy(name = documents.folder(uri).name)
            } else {
                base.copy(name = documents.picked(uri).name)
            }
        } catch (error: Exception) {
            rethrowCancellation(error)
            base.copy(needsPermission = error is SecurityException, unavailable = error !is SecurityException)
        }
    }

    private suspend fun scanSources(sources: List<FrameSource>) {
        mutableState.update { it.copy(scanning = true, scanCount = 0, error = null) }
        var completed = 0
        try {
            for (source in sources) {
                currentCoroutineContext().ensureActive()
                val checked = validate(source)
                if (checked.needsPermission || checked.unavailable) {
                    publishSource(checked)
                    setError(if (checked.needsPermission) FrameError.PERMISSION else FrameError.UNREADABLE)
                    continue
                }
                if (checked.type == FrameSelectionType.PICKED_PHOTO) {
                    publishSource(checked)
                    completed++
                    continue
                }
                val photos = try {
                    val treeUri = checked.uri.toUri()
                    val root = documents.folder(treeUri)
                    scanFrameFolder(checked.uri, root.uri, { documents.children(treeUri, it) }) { count ->
                        mutableState.update { it.copy(scanCount = completed + count) }
                    }
                } catch (error: Exception) {
                    rethrowCancellation(error)
                    publishSource(checked.copy(needsPermission = error is SecurityException, unavailable = error !is SecurityException))
                    setError(sourceError(error))
                    continue
                }
                currentCoroutineContext().ensureActive()
                val replacement = folderPhotos.filterNot { it.sourceUri == checked.uri } + photos
                val scanned = scannedFolders + checked.uri
                // Only a complete scan replaces the previous index; cancellation never publishes partial results.
                manifest.write(FrameManifest(photos = replacement, scannedFolders = scanned))
                folderPhotos = replacement
                scannedFolders = scanned
                failedPhotos.removeAll(photos.mapTo(hashSetOf()) { it.uri })
                publishSource(checked.copy(photoCount = photos.size, scanned = true))
                completed += photos.size
            }
        } finally {
            mutableState.update { it.copy(scanning = false) }
        }
    }

    private suspend fun saveSources(sources: List<FrameSource>) {
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            preferences.edit { it[SourcesKey] = json.encodeToString(sources) }
            mutableState.update { it.copy(sources = sources, photos = mergeFramePhotos(sources, folderPhotos)) }
        }
    }

    private fun publishSource(source: FrameSource) {
        mutableState.update { current ->
            val updated = current.sources.map { if (it.uri == source.uri) source else it }
            current.copy(sources = updated, photos = mergeFramePhotos(updated, folderPhotos))
        }
    }

    private fun pruneIndex() {
        val retained = state.value.sources.mapTo(hashSetOf()) { it.uri }
        val photos = folderPhotos.filter { it.sourceUri in retained }
        val scanned = scannedFolders.intersect(retained)
        manifest.write(FrameManifest(photos = photos, scannedFolders = scanned))
        folderPhotos = photos
        scannedFolders = scanned
    }

    private suspend fun operation(initializeFirst: Boolean = true, block: suspend () -> Unit): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (initializeFirst && !state.value.initialized) initializeLocked()
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (_: SecurityException) {
                setError(FrameError.PERMISSION)
            } catch (_: IOException) {
                setError(FrameError.STORAGE)
            } catch (_: SerializationException) {
                setError(FrameError.MANIFEST)
            }
        }
    }

    private fun setError(error: FrameError) = mutableState.update { it.copy(error = error) }

    private fun sourceError(error: Exception): FrameError = when (error) {
        is SecurityException -> FrameError.PERMISSION
        is InvalidFrameImageException -> FrameError.INVALID_IMAGE
        else -> FrameError.UNREADABLE
    }

    private fun rethrowCancellation(error: Exception) {
        if (error is CancellationException) throw error
    }

    private companion object {
        val SourcesKey = stringPreferencesKey("photo_frame_sources_v1")
        val SettingsKey = stringPreferencesKey("photo_frame_settings_v1")
    }
}
