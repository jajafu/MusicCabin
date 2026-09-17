package com.metrolist.music.photo.v2

import android.content.Context
import android.graphics.BitmapFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.Image
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Scale
import com.metrolist.music.BuildConfig
import com.metrolist.music.constants.MaxImageCacheSizeKey
import com.metrolist.music.photo.v2.drive.*
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.safeDataStoreEdit
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@HiltViewModel
class PhotoFrameV2ViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    authorizationFactory: DriveAuthorizationFactory,
) : ViewModel() {
    private val selectedSource = MutableStateFlow<FrameV2Source?>(null)
    val source = selectedSource.asStateFlow()
    private val sourceWrites = Mutex()
    private val imageCache = DriveImageCache(
        diskCache = {
            if ((context.dataStore.data.first()[MaxImageCacheSizeKey] ?: 512) == 0) null
            else context.imageLoader.diskCache
        },
        temporaryDirectory = File(context.cacheDir, "photo_frame_v2/downloads"),
        validate = { file ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            bounds.outWidth > 0 && bounds.outHeight > 0
        },
        indexFile = File(context.filesDir, "photo_frame_v2/drive-cache-index-v1.json"),
    )
    private suspend fun loadImage(
        context: Context,
        api: DrivePhotoAccess,
        account: DriveAccount,
        photo: DriveFile,
        allowDownload: Boolean = true,
    ): DriveSlide<Image> =
        imageCache.read(account, photo, api, allowDownload) { file, cached ->
            val result = context.imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(file)
                    .size(1920, 1920)
                    .scale(Scale.FIT)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .networkCachePolicy(CachePolicy.DISABLED)
                    .build(),
            ) as? SuccessResult ?: throw DriveException(DriveFailure.INVALID_IMAGE)
            DriveSlide(result.image, cached)
        }

    val slideshow: DriveSlideshowController<Image> = DriveSlideshowController(
        scope = viewModelScope,
        load = { account, photo, api, allowDownload -> loadImage(context, api, account, photo, allowDownload) },
        prefetch = imageCache::prefetch,
        onAuthorizationFailure = ::requireReconnect,
    )
    private var cacheScan: Job? = null
    private var cacheIndexing: Job? = null
    private var cacheOnlySession = false
    val controller: DriveProbeController<Image> = DriveProbeController(
        scope = viewModelScope,
        available = BuildConfig.DRIVE_OAUTH_AVAILABLE,
        settings = AndroidDriveProbeSettings(context),
        authorizationFactory = authorizationFactory,
        repositoryFactory = { DrivePhotoRepository(it) },
        loadPreview = { repository, account, photo -> loadImage(context, repository, account, photo).image },
        onFolderSelected = { api, account, path, photos ->
            cacheOnlySession = false
            cacheScan?.cancel()
            slideshow.updateSource(account, api, path.lastOrNull()?.name, photos)
            cacheIndexing?.cancel()
            cacheIndexing = viewModelScope.launch {
                try {
                    imageCache.indexAvailable(account, photos)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    return@launch
                }
            }
        },
        onStop = ::stopSlideshow,
    )
    val state = controller.state

    init {
        viewModelScope.launch {
            val saved = state.first { it.initialized || (!it.busy && it.error != null) }
            val choice = try { context.dataStore.data.first()[SourceKey] } catch (_: IOException) { null }
            val restored = FrameV2Source.entries.firstOrNull { it.name == choice }
                ?: if (saved.enabled && saved.selectedPath != null) FrameV2Source.DRIVE else FrameV2Source.LOCAL
            selectedSource.compareAndSet(null, restored)
        }
    }

    fun selectSource(source: FrameV2Source) {
        if (selectedSource.value == source) return
        if (state.value.initialized) controller.stop()
        selectedSource.value = source
        viewModelScope.launch {
            withContext(NonCancellable) {
                sourceWrites.withLock {
                    if (!context.safeDataStoreEdit { it[SourceKey] = source.name }) controller.reportStorageError()
                }
            }
        }
    }

    suspend fun startCachedSlideshow(): Boolean {
        if (slideshow.state.value.active) return true
        val saved = state.value
        val account = saved.account ?: return false
        val cached = try {
            imageCache.cachedPhotos(account, maximumCount = 5)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            controller.reportStorageError()
            return false
        }
        if (cached.isEmpty()) return false
        slideshow.start(
            account = account,
            api = CacheOnlyDrivePhotoAccess,
            folderName = saved.selectedPath?.lastOrNull()?.name,
            photos = cached,
            allowDownloads = false,
        )
        cacheOnlySession = true
        cacheScan?.cancel()
        cacheScan = viewModelScope.launch {
            try {
                val allCached = imageCache.cachedPhotos(account)
                if (cacheOnlySession && allCached.isNotEmpty()) {
                    slideshow.updateSource(
                        account = account,
                        api = CacheOnlyDrivePhotoAccess,
                        folderName = saved.selectedPath?.lastOrNull()?.name,
                        photos = allCached,
                        allowDownloads = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (cacheOnlySession) controller.reportStorageError()
            }
        }
        return true
    }

    private fun stopSlideshow() {
        cacheOnlySession = false
        cacheScan?.cancel()
        cacheScan = null
        cacheIndexing?.cancel()
        cacheIndexing = null
        slideshow.stop()
    }

    private fun requireReconnect() {
        controller.requireReconnect()
    }

    override fun onCleared() = controller.stop()

    private companion object {
        val SourceKey = stringPreferencesKey("photo_frame_v2_source")
    }
}

enum class FrameV2Source { LOCAL, DRIVE }

private object CacheOnlyDrivePhotoAccess : DrivePhotoAccess {
    override suspend fun account(): DriveAccount = throw DriveCacheMissException()
    override suspend fun folders(parentId: String, progress: (Int) -> Unit): List<DriveFile> = throw DriveCacheMissException()
    override suspend fun photos(parentId: String, progress: (Int) -> Unit): List<DriveFile> = throw DriveCacheMissException()
    override suspend fun download(photo: DriveFile, target: File): Long = throw DriveCacheMissException()
    override fun close() = Unit
}

private class AndroidDriveProbeSettings(private val context: Context) : DriveProbeSettings {
    private val writes = Mutex()
    override suspend fun read(): DriveProbePreferences {
        val preferences = try { context.dataStore.data.first() }
        catch (_: IOException) { throw DriveException(DriveFailure.STORAGE) }
        val id = preferences[AccountId]
        val email = preferences[AccountEmail]
        return DriveProbePreferences(
            enabled = preferences[Enabled] ?: false,
            account = if (id != null && email != null) DriveAccount(id, email) else null,
            selectedPath = preferences[FolderPath]?.let { saved ->
                runCatching { Json.decodeFromString<List<DriveFile>>(saved) }.getOrNull()
            },
        )
    }

    override suspend fun write(value: DriveProbePreferences) = withContext(NonCancellable) {
        // User choices must reach disk even when the page closes; preserve their submission order.
        writes.withLock {
            withContext(Dispatchers.IO) {
                val saved = context.safeDataStoreEdit {
                    it[Enabled] = value.enabled
                    if (value.account == null) {
                        it.remove(AccountId)
                        it.remove(AccountEmail)
                        it.remove(FolderPath)
                    } else {
                        it[AccountId] = value.account.permissionId
                        it[AccountEmail] = value.account.emailAddress
                        if (value.selectedPath == null) it.remove(FolderPath)
                        else it[FolderPath] = Json.encodeToString(value.selectedPath)
                    }
                }
                if (!saved) throw DriveException(DriveFailure.STORAGE)
            }
        }
    }

    private companion object {
        val Enabled = booleanPreferencesKey("photo_frame_v2_drive_enabled")
        val AccountId = stringPreferencesKey("photo_frame_v2_drive_account_id")
        val AccountEmail = stringPreferencesKey("photo_frame_v2_drive_account_email")
        val FolderPath = stringPreferencesKey("photo_frame_v2_drive_folder_path")
    }
}
