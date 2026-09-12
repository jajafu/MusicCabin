package com.metrolist.music.photo.v2

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import com.metrolist.music.R
import com.metrolist.music.photo.DirectUsbListing
import com.metrolist.music.photo.DirectUsbPhotoSource
import com.metrolist.music.photo.DirectUsbRoot
import com.metrolist.music.photo.MediaStoreAlbum
import com.metrolist.music.photo.MediaStorePhoto
import com.metrolist.music.photo.MediaStorePhotoSource
import com.metrolist.music.photo.MediaStoreVolume
import com.metrolist.music.photo.MediaStoreVolumeDiagnostic
import com.metrolist.music.photo.MediaStoreVolumeKind
import com.metrolist.music.photo.hasMediaStorePhotoAccess
import com.metrolist.music.photo.mergeMediaStorePhotos
import com.metrolist.music.photo.requiredMediaStorePhotoPermissions
import com.metrolist.music.photo.updateMediaStoreFolderSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val MediaStorePageSize = 80

@Composable
internal fun MediaStorePhotoBrowser(
    onDismiss: () -> Unit,
    onPhotosSelected: (List<Uri>) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val source = remember(context) { MediaStorePhotoSource(context.applicationContext) }
    val selected = remember { mutableStateListOf<String>() }
    val selectedLookup by remember { derivedStateOf { selected.toHashSet() } }
    var permissionRevision by remember { mutableIntStateOf(0) }
    val hasAccess = remember(permissionRevision) { hasMediaStorePhotoAccess(context) }
    var volumes by remember { mutableStateOf(emptyList<MediaStoreVolume>()) }
    var selectedVolume by remember { mutableStateOf<MediaStoreVolume?>(null) }
    var albums by remember { mutableStateOf(emptyList<MediaStoreAlbum>()) }
    var selectedAlbum by remember { mutableStateOf<MediaStoreAlbum?>(null) }
    val fullySelectedAlbums = remember { mutableStateListOf<String>() }
    var albumsLoading by remember { mutableStateOf(false) }
    var albumsError by remember { mutableStateOf(false) }
    var albumRevision by remember { mutableIntStateOf(0) }
    var bulkSelecting by remember { mutableStateOf(false) }
    var bulkSelectionError by remember { mutableStateOf(false) }
    var photos by remember { mutableStateOf(emptyList<MediaStorePhoto>()) }
    var nextOffset by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }
    var requestedOffset by remember { mutableIntStateOf(0) }
    var requestRevision by remember { mutableIntStateOf(0) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var directRoot by remember { mutableStateOf<DirectUsbRoot?>(null) }
    var directRootLoading by remember { mutableStateOf(false) }
    var directRootError by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionRevision++
    }

    BackHandler(enabled = directRoot == null, onBack = onDismiss)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRevision++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasAccess, permissionRevision) {
        if (!hasAccess) {
            volumes = emptyList()
            selectedVolume = null
            albums = emptyList()
            selectedAlbum = null
            photos = emptyList()
            return@LaunchedEffect
        }
        try {
            val available = source.availableVolumes()
            volumes = available
            val retained = available.firstOrNull { it.name == selectedVolume?.name }
            selectedVolume = retained ?: available.firstOrNull()
            requestedOffset = 0
            requestRevision++
            albumRevision++
            loadError = false
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            volumes = emptyList()
            selectedVolume = null
            loadError = true
        }
    }

    LaunchedEffect(selectedVolume, albumRevision) {
        val volume = selectedVolume ?: return@LaunchedEffect
        albumsLoading = true
        albumsError = false
        try {
            val available = source.loadAlbums(volume)
            albums = available
            selectedAlbum = selectedAlbum?.let { selected -> available.firstOrNull { it.id == selected.id } }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            albums = emptyList()
            selectedAlbum = null
            albumsError = true
        } finally {
            albumsLoading = false
        }
    }

    LaunchedEffect(selectedVolume, selectedAlbum?.id, requestRevision) {
        val volume = selectedVolume ?: return@LaunchedEffect
        val offset = requestedOffset
        loading = true
        loadError = false
        try {
            val page = source.loadPage(volume, offset, MediaStorePageSize, selectedAlbum?.id)
            photos = if (offset == 0) page.photos else mergeMediaStorePhotos(photos, page.photos)
            nextOffset = page.nextOffset
            hasMore = page.hasMore
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            loadError = true
        } finally {
            loading = false
        }
    }

    directRoot?.let { root ->
        DirectUsbPhotoBrowser(
            root = root,
            selected = selected,
            selectedLookup = selectedLookup,
            onDismiss = { directRoot = null },
            onAdd = { onPhotosSelected(selected.distinct().map(Uri::parse)) },
        )
        return
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            MediaStoreBrowserTopBar(
                selectedCount = selected.size,
                diagnosticsEnabled = hasAccess && volumes.isNotEmpty(),
                onDismiss = onDismiss,
                onDiagnostics = { showDiagnostics = true },
                onAdd = {
                    onPhotosSelected(selected.distinct().map(Uri::parse))
                },
            )
            when {
                !hasAccess -> MediaStorePermissionContent(
                    onRequest = { permissionLauncher.launch(requiredMediaStorePhotoPermissions()) },
                    onSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                )
                else -> {
                    if (volumes.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowItems(volumes, key = MediaStoreVolume::name) { volume ->
                                FilterChip(
                                    selected = volume.name == selectedVolume?.name,
                                    onClick = {
                                        if (volume.name != selectedVolume?.name) {
                                            selectedVolume = volume
                                            selectedAlbum = null
                                            albums = emptyList()
                                            photos = emptyList()
                                            requestedOffset = 0
                                            albumRevision++
                                            requestRevision++
                                            bulkSelectionError = false
                                        }
                                    },
                                    label = { Text(mediaStoreVolumeLabel(volume)) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                )
                            }
                        }
                    }
                    MediaStoreAlbumControls(
                        albums = albums,
                        selectedAlbum = selectedAlbum,
                        loading = albumsLoading,
                        loadError = albumsError,
                        bulkSelecting = bulkSelecting,
                        bulkSelectionError = bulkSelectionError,
                        fullySelected = selectedAlbum?.let { album -> albumKey(selectedVolume, album) in fullySelectedAlbums } == true,
                        onSelectAlbum = { album ->
                            if (album?.id != selectedAlbum?.id) {
                                selectedAlbum = album
                                photos = emptyList()
                                requestedOffset = 0
                                requestRevision++
                                bulkSelectionError = false
                            }
                        },
                        onRetry = { albumRevision++ },
                        onToggleFolder = {
                            val volume = selectedVolume ?: return@MediaStoreAlbumControls
                            val album = selectedAlbum ?: return@MediaStoreAlbumControls
                            val key = albumKey(volume, album)
                            scope.launch {
                                bulkSelecting = true
                                bulkSelectionError = false
                                try {
                                    val folderUris = source.loadAlbumPhotos(volume, album.id).map(MediaStorePhoto::uri)
                                    if (folderUris.isEmpty()) {
                                        bulkSelectionError = true
                                        return@launch
                                    }
                                    val shouldSelect = key !in fullySelectedAlbums
                                    val updated = updateMediaStoreFolderSelection(selected, folderUris, shouldSelect)
                                    selected.clear()
                                    selected.addAll(updated)
                                    if (shouldSelect) {
                                        if (key !in fullySelectedAlbums) fullySelectedAlbums.add(key)
                                    } else {
                                        fullySelectedAlbums.remove(key)
                                    }
                                } catch (error: Exception) {
                                    if (error is CancellationException) throw error
                                    bulkSelectionError = true
                                } finally {
                                    bulkSelecting = false
                                }
                            }
                        },
                    )
                    MediaStorePhotoGrid(
                        photos = photos,
                        selected = selected,
                        selectedLookup = selectedLookup,
                        loading = loading,
                        loadError = loadError,
                        hasMore = hasMore,
                        onRetry = { requestRevision++ },
                        onLoadMore = {
                            requestedOffset = nextOffset
                            requestRevision++
                        },
                        onPhotoDeselected = { fullySelectedAlbums.clear() },
                        directBrowseEnabled = selectedVolume?.kind == MediaStoreVolumeKind.REMOVABLE,
                        directBrowseLoading = directRootLoading,
                        directBrowseError = directRootError,
                        onDirectBrowse = {
                            val volume = selectedVolume ?: return@MediaStorePhotoGrid
                            scope.launch {
                                directRootLoading = true
                                directRootError = false
                                try {
                                    directRoot = source.directRemovableRoot(volume)
                                    directRootError = directRoot == null
                                } catch (error: Exception) {
                                    if (error is CancellationException) throw error
                                    directRootError = true
                                } finally {
                                    directRootLoading = false
                                }
                            }
                        },
                    )
                }
            }
        }
    }
    if (showDiagnostics) {
        MediaStoreStorageDiagnosticsDialog(
            source = source,
            volumes = volumes,
            onDismiss = { showDiagnostics = false },
        )
    }
}

@Composable
private fun MediaStoreBrowserTopBar(
    selectedCount: Int,
    diagnosticsEnabled: Boolean,
    onDismiss: () -> Unit,
    onDiagnostics: () -> Unit,
    onAdd: () -> Unit,
) {
    val diagnosticsLabel = "${stringResource(R.string.storage)} · ${stringResource(R.string.details)}"
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.photo_browser_close))
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.photo_browser_title),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selectedCount > 0) {
                Text(
                    pluralStringResource(R.plurals.photo_browser_selected, selectedCount, selectedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (diagnosticsEnabled) {
            IconButton(
                onClick = onDiagnostics,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(painterResource(R.drawable.info), diagnosticsLabel)
            }
        }
        Button(onClick = onAdd, enabled = selectedCount > 0, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.photo_browser_add, selectedCount))
        }
    }
}

@Composable
private fun MediaStoreStorageDiagnosticsDialog(
    source: MediaStorePhotoSource,
    volumes: List<MediaStoreVolume>,
    onDismiss: () -> Unit,
) {
    var diagnostics by remember(volumes) { mutableStateOf(emptyList<MediaStoreVolumeDiagnostic>()) }
    var loading by remember(volumes) { mutableStateOf(true) }
    var failed by remember(volumes) { mutableStateOf(false) }
    LaunchedEffect(source, volumes) {
        loading = true
        failed = false
        try {
            diagnostics = source.loadVolumeDiagnostics(volumes.filterNot { it.kind == MediaStoreVolumeKind.ALL })
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            diagnostics = emptyList()
            failed = true
        } finally {
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.storage), null) },
        title = { Text("${stringResource(R.string.storage)} · ${stringResource(R.string.details)}") },
        text = {
            Column(
                Modifier.widthIn(max = 640.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Android SDK ${android.os.Build.VERSION.SDK_INT}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    failed -> Text(
                        stringResource(R.string.photo_browser_load_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    else -> diagnostics.forEach { diagnostic ->
                        MediaStoreStorageDiagnosticCard(diagnostic)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

@Composable
private fun MediaStoreStorageDiagnosticCard(diagnostic: MediaStoreVolumeDiagnostic) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(mediaStoreVolumeLabel(diagnostic.volume), style = MaterialTheme.typography.titleMedium)
            diagnostic.storage?.description?.takeIf(String::isNotBlank)?.let { description ->
                Text(description, style = MaterialTheme.typography.bodyLarge)
            }
            Text("MediaStore · ${diagnostic.volume.name}", style = MaterialTheme.typography.bodyLarge)
            diagnostic.indexedPhotoCount?.let { count ->
                Text(
                    pluralStringResource(R.plurals.photo_browser_folder_photos, count, count),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } ?: Text(
                stringResource(R.string.photo_browser_load_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
            diagnostic.storage?.let { storage ->
                Text("State · ${storage.state}", style = MaterialTheme.typography.bodyLarge)
                storage.path?.let { Text("Path · $it", style = MaterialTheme.typography.bodyLarge) }
                storage.uuid?.let { Text("UUID · $it", style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
}

@Composable
private fun MediaStorePermissionContent(onRequest: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(R.drawable.insert_photo), null, Modifier.size(56.dp))
        Spacer(Modifier.size(16.dp))
        Text(
            stringResource(R.string.photo_browser_permission_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.photo_browser_permission_description),
            Modifier.widthIn(max = 560.dp).padding(top = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FlowRow(
            Modifier.widthIn(max = 560.dp).padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onRequest, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.photo_browser_allow))
            }
            OutlinedButton(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.photo_browser_app_settings))
            }
        }
    }
}

@Composable
private fun MediaStoreAlbumControls(
    albums: List<MediaStoreAlbum>,
    selectedAlbum: MediaStoreAlbum?,
    loading: Boolean,
    loadError: Boolean,
    bulkSelecting: Boolean,
    bulkSelectionError: Boolean,
    fullySelected: Boolean,
    onSelectAlbum: (MediaStoreAlbum?) -> Unit,
    onRetry: () -> Unit,
    onToggleFolder: () -> Unit,
) {
    when {
        loading && albums.isEmpty() -> Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Text(stringResource(R.string.photo_browser_folders_loading))
        }
        loadError && albums.isEmpty() -> Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.photo_browser_folder_error),
                Modifier.weight(1f),
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.retry))
            }
        }
        albums.isNotEmpty() -> LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "all-folders") {
                FilterChip(
                    selected = selectedAlbum == null,
                    onClick = { onSelectAlbum(null) },
                    label = { Text(stringResource(R.string.photo_browser_all_folders)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
            rowItems(albums, key = MediaStoreAlbum::id) { album ->
                val name = album.name.ifBlank { stringResource(R.string.photo_browser_other_folder) }
                val count = pluralStringResource(R.plurals.photo_browser_folder_photos, album.photoCount, album.photoCount)
                FilterChip(
                    selected = album.id == selectedAlbum?.id,
                    onClick = { onSelectAlbum(album) },
                    label = { Text("$name · $count", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }

    selectedAlbum?.let { album ->
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val folderName = album.name.ifBlank { stringResource(R.string.photo_browser_other_folder) }
            Text(
                folderName,
                Modifier.widthIn(min = 120.dp, max = 320.dp).padding(vertical = 12.dp),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            OutlinedButton(
                onClick = onToggleFolder,
                enabled = !bulkSelecting,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                if (bulkSelecting) {
                    CircularProgressIndicator(Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    stringResource(
                        when {
                            bulkSelecting -> R.string.photo_browser_selecting_folder
                            fullySelected -> R.string.photo_browser_deselect_folder
                            else -> R.string.photo_browser_select_folder
                        },
                        album.photoCount,
                    ),
                )
            }
        }
        if (bulkSelectionError) {
            Text(
                stringResource(R.string.photo_browser_folder_error),
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun MediaStorePhotoGrid(
    photos: List<MediaStorePhoto>,
    selected: MutableList<String>,
    selectedLookup: Set<String>,
    loading: Boolean,
    loadError: Boolean,
    hasMore: Boolean,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onPhotoDeselected: () -> Unit,
    directBrowseEnabled: Boolean,
    directBrowseLoading: Boolean,
    directBrowseError: Boolean,
    onDirectBrowse: () -> Unit,
) {
    when {
        photos.isEmpty() && loading -> BrowserCenteredMessage {
            CircularProgressIndicator()
            Text(stringResource(R.string.photo_browser_loading), Modifier.padding(top = 12.dp))
        }
        photos.isEmpty() && loadError -> BrowserCenteredMessage {
            Text(stringResource(R.string.photo_browser_load_error), color = MaterialTheme.colorScheme.error)
            Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.retry))
            }
        }
        photos.isEmpty() -> BrowserCenteredMessage {
            Icon(painterResource(R.drawable.hide_image), null, Modifier.size(48.dp))
            Text(stringResource(R.string.photo_browser_empty), Modifier.padding(top = 12.dp))
            if (directBrowseEnabled) {
                Button(
                    onClick = onDirectBrowse,
                    enabled = !directBrowseLoading,
                    modifier = Modifier.padding(top = 16.dp).heightIn(min = 48.dp),
                ) {
                    if (directBrowseLoading) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(stringResource(R.string.photo_browser_browse_device))
                }
                if (directBrowseError) {
                    Text(
                        stringResource(R.string.photo_browser_folder_error),
                        Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 132.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            gridItems(photos, key = MediaStorePhoto::uri) { photo ->
                MediaStorePhotoTile(
                    photo = photo,
                    selected = photo.uri in selectedLookup,
                    onToggle = {
                        if (photo.uri in selectedLookup) {
                            selected.remove(photo.uri)
                            onPhotoDeselected()
                        } else {
                            selected.add(photo.uri)
                        }
                    },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    when {
                        loading -> CircularProgressIndicator(Modifier.size(32.dp))
                        loadError -> Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.retry))
                        }
                        hasMore -> OutlinedButton(onClick = onLoadMore, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.photo_browser_load_more))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectUsbPhotoBrowser(
    root: DirectUsbRoot,
    selected: MutableList<String>,
    selectedLookup: Set<String>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val source = remember(root.path) { DirectUsbPhotoSource(root.path) }
    var currentPath by rememberSaveable(root.path) { mutableStateOf(root.path) }
    var listing by remember { mutableStateOf<DirectUsbListing?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var bulkSelecting by remember { mutableStateOf(false) }
    var bulkCount by remember { mutableIntStateOf(0) }
    var bulkSelectionError by remember { mutableStateOf(false) }
    val fullySelectedFolders = remember { mutableStateListOf<String>() }
    val selectedFolderCounts = remember { mutableStateMapOf<String, Int>() }
    val parentPath = remember(currentPath) { runCatching { source.parentOf(currentPath) }.getOrNull() }

    BackHandler {
        if (parentPath != null) currentPath = parentPath else onDismiss()
    }
    LaunchedEffect(currentPath, revision) {
        loading = true
        loadError = false
        try {
            listing = source.loadDirectory(currentPath)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            listing = null
            loadError = true
        } finally {
            loading = false
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            MediaStoreBrowserTopBar(
                selectedCount = selected.size,
                diagnosticsEnabled = false,
                onDismiss = {
                    if (parentPath != null) currentPath = parentPath else onDismiss()
                },
                onDiagnostics = {},
                onAdd = onAdd,
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(
                        onClick = { parentPath?.let { currentPath = it } },
                        enabled = parentPath != null && !bulkSelecting,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(painterResource(R.drawable.arrow_upward), stringResource(R.string.back))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(root.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listing?.directory?.name ?: currentPath.substringAfterLast('/'),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            bulkSelecting = true
                            bulkSelectionError = false
                            bulkCount = listing?.photos?.size ?: 0
                            try {
                                val folderPhotos = source.loadFolderPhotos(currentPath) {}
                                bulkCount = folderPhotos.size
                                if (folderPhotos.isEmpty()) {
                                    bulkSelectionError = true
                                    return@launch
                                }
                                val uris = folderPhotos.map(MediaStorePhoto::uri)
                                val shouldSelect = currentPath !in fullySelectedFolders
                                val updated = updateMediaStoreFolderSelection(selected, uris, shouldSelect)
                                selected.clear()
                                selected.addAll(updated)
                                if (shouldSelect) {
                                    if (currentPath !in fullySelectedFolders) fullySelectedFolders.add(currentPath)
                                    selectedFolderCounts[currentPath] = folderPhotos.size
                                } else {
                                    fullySelectedFolders.remove(currentPath)
                                    selectedFolderCounts.remove(currentPath)
                                }
                            } catch (error: Exception) {
                                if (error is CancellationException) throw error
                                bulkSelectionError = true
                            } finally {
                                bulkSelecting = false
                            }
                        }
                    },
                    enabled = !loading && !loadError && !bulkSelecting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    if (bulkSelecting) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(
                        stringResource(
                            when {
                                bulkSelecting -> R.string.photo_browser_selecting_folder
                                currentPath in fullySelectedFolders -> R.string.photo_browser_deselect_folder
                                else -> R.string.photo_browser_select_folder
                            },
                            if (bulkSelecting) bulkCount else selectedFolderCounts[currentPath] ?: listing?.photos?.size ?: 0,
                        ),
                    )
                }
            }
            if (bulkSelectionError) {
                Text(
                    stringResource(R.string.photo_browser_folder_error),
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            when {
                loading -> BrowserCenteredMessage {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.photo_browser_loading), Modifier.padding(top = 12.dp))
                }
                loadError -> BrowserCenteredMessage {
                    Text(stringResource(R.string.photo_browser_folder_error), color = MaterialTheme.colorScheme.error)
                    Button(onClick = { revision++ }, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.retry))
                    }
                }
                else -> DirectUsbEntries(
                    listing = listing,
                    selected = selected,
                    selectedLookup = selectedLookup,
                    enabled = !bulkSelecting,
                    onOpenDirectory = {
                        currentPath = it
                        bulkSelectionError = false
                    },
                    onPhotoDeselected = { fullySelectedFolders.clear() },
                )
            }
        }
    }
}

@Composable
private fun DirectUsbEntries(
    listing: DirectUsbListing?,
    selected: MutableList<String>,
    selectedLookup: Set<String>,
    enabled: Boolean,
    onOpenDirectory: (String) -> Unit,
    onPhotoDeselected: () -> Unit,
) {
    val entries = listing ?: return
    if (entries.directories.isEmpty() && entries.photos.isEmpty()) {
        BrowserCenteredMessage {
            Icon(painterResource(R.drawable.hide_image), null, Modifier.size(48.dp))
            Text(stringResource(R.string.photo_frame_no_images), Modifier.padding(top = 12.dp))
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 132.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        gridItems(entries.directories, key = { "folder:${it.path}" }) { directory ->
            OutlinedButton(
                onClick = { onOpenDirectory(directory.path) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(12.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.storage), null, Modifier.size(40.dp))
                    Text(directory.name, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
        gridItems(entries.photos, key = MediaStorePhoto::uri) { photo ->
            MediaStorePhotoTile(
                photo = photo,
                selected = photo.uri in selectedLookup,
                onToggle = {
                    if (!enabled) return@MediaStorePhotoTile
                    if (photo.uri in selectedLookup) {
                        selected.remove(photo.uri)
                        onPhotoDeselected()
                    } else {
                        selected.add(photo.uri)
                    }
                },
            )
        }
    }
}

@Composable
private fun MediaStorePhotoTile(photo: MediaStorePhoto, selected: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val description = stringResource(R.string.photo_browser_photo_description, photo.name)
    val request = remember(photo.uri) {
        ImageRequest.Builder(context)
            .data(photo.uri.toUri())
            .size(320, 320)
            .scale(Scale.FILL)
            .precision(Precision.INEXACT)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .networkCachePolicy(CachePolicy.DISABLED)
            .build()
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.Checkbox, onClick = onToggle)
            .semantics { contentDescription = description },
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
            error = painterResource(R.drawable.hide_image),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
                .padding(start = 8.dp, top = 20.dp, end = 40.dp, bottom = 8.dp),
        ) {
            Text(photo.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(32.dp),
            shape = CircleShape,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.55f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (selected) Icon(painterResource(R.drawable.check), null, Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun BrowserCenteredMessage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
private fun mediaStoreVolumeLabel(volume: MediaStoreVolume): String = when (volume.kind) {
    MediaStoreVolumeKind.ALL -> stringResource(R.string.photo_browser_all_storage)
    MediaStoreVolumeKind.PRIMARY -> stringResource(R.string.photo_browser_internal_storage)
    MediaStoreVolumeKind.REMOVABLE -> stringResource(R.string.photo_browser_external_storage, volume.name.take(8))
    MediaStoreVolumeKind.LEGACY -> stringResource(R.string.photo_browser_device_storage)
}

private fun albumKey(volume: MediaStoreVolume?, album: MediaStoreAlbum): String = "${volume?.name}:${album.id}"
