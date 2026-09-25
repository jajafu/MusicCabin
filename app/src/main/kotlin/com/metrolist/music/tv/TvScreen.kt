/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Timeline
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.models.filterVideoSongs
import com.metrolist.innertube.models.filterYoutubeShorts
import com.metrolist.music.R
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.HideYoutubeShortsKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.ui.utils.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class TvPage { HOME, SEARCH, QUEUE, FRAME }

private data class TvSongSection(val title: String, val label: String?, val songs: List<SongItem>)

@Composable
fun TvScreen(database: MusicDatabase, playerConnection: PlayerConnection?, onExitApp: () -> Unit) {
    val context = LocalContext.current
    val isChinese = LocalConfiguration.current.locales[0].language == "zh"
    val quickPicksFlow = remember(database) { database.quickPicks() }
    val quickPicks by quickPicksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val localSongs = remember(quickPicks) { quickPicks.take(6) }
    val ready by playerConnection?.service?.isPlayerReady?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) }
    val playing by playerConnection?.isEffectivelyPlaying?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) }
    val metadata by playerConnection?.mediaMetadata?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf<MediaMetadata?>(null) }
    val queueWindows by playerConnection?.queueWindows?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(emptyList<Timeline.Window>()) }
    val currentQueueIndex by playerConnection?.currentWindowIndex?.collectAsStateWithLifecycle()
        ?: remember { mutableIntStateOf(-1) }

    var page by remember { mutableStateOf(TvPage.HOME) }
    var homeReload by remember { mutableIntStateOf(0) }
    var homeSections by remember { mutableStateOf<List<TvSongSection>>(emptyList()) }
    var homeLoading by remember { mutableStateOf(true) }
    var homeFailed by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var searchTerm by remember { mutableStateOf("") }
    var searchReload by remember { mutableIntStateOf(0) }
    var searchSongs by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }

    val homeFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val queueFocus = remember { FocusRequester() }
    val searchFieldFocus = remember { FocusRequester() }

    BackHandler(page != TvPage.HOME) { page = TvPage.HOME }
    LaunchedEffect(page) {
        when (page) {
            TvPage.HOME -> homeFocus.requestFocus()
            TvPage.SEARCH -> searchFieldFocus.requestFocus()
            TvPage.QUEUE -> queueFocus.requestFocus()
            TvPage.FRAME -> Unit
        }
    }

    LaunchedEffect(homeReload) {
        homeLoading = true
        homeFailed = false
        runCatching {
            withContext(Dispatchers.IO) {
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val hideVideos = context.dataStore.get(HideVideoSongsKey, false)
                val hideShorts = context.dataStore.get(HideYoutubeShortsKey, false)
                val seen = hashSetOf<String>()
                buildList {
                    for (section in YouTube.home().getOrThrow().sections) {
                        if (size == 4) break
                        val songs = section.items.filterIsInstance<SongItem>()
                            .filterExplicit(hideExplicit)
                            .filterVideoSongs(hideVideos)
                            .filterYoutubeShorts(hideShorts)
                            .filter { it.id !in seen }
                            .take(6)
                        if (songs.isEmpty()) continue
                        seen.addAll(songs.map { it.id })
                        add(TvSongSection(
                            section.title.ifBlank {
                                context.getString(
                                    if (isChinese)
                                        R.string.tv_recommendations_zh_tw else R.string.tv_recommendations
                                )
                            },
                            section.label,
                            songs,
                        ))
                    }
                }
            }
        }.onSuccess { homeSections = it }
            .onFailure { homeFailed = true }
        homeLoading = false
    }

    LaunchedEffect(searchTerm, searchReload) {
        if (searchTerm.isBlank()) return@LaunchedEffect
        searchLoading = true
        searchFailed = false
        searchSongs = emptyList()
        runCatching {
            withContext(Dispatchers.IO) {
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val hideVideos = context.dataStore.get(HideVideoSongsKey, false)
                val hideShorts = context.dataStore.get(HideYoutubeShortsKey, false)
                YouTube.search(searchTerm, YouTube.SearchFilter.FILTER_SONG)
                    .getOrThrow().items.filterIsInstance<SongItem>()
                    .filterExplicit(hideExplicit)
                    .filterVideoSongs(hideVideos)
                    .filterYoutubeShorts(hideShorts)
                    .distinctBy { it.id }
                    .take(50)
            }
        }.onSuccess { searchSongs = it }
            .onFailure { searchFailed = true }
        searchLoading = false
    }

    val submitSearch = {
        val query = searchText.trim()
        if (query.isNotEmpty()) {
            searchTerm = query
            searchReload++
        }
    }

    if (page == TvPage.FRAME) {
        androidx.compose.runtime.CompositionLocalProvider(LocalPlayerConnection provides playerConnection) {
            TvPhotoFrameScreen(onExit = { page = TvPage.HOME })
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Surface(
                modifier = Modifier.width(228.dp).fillMaxHeight(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 24.dp),
                    )
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            TvNavigationItem(stringResource(R.string.home), R.drawable.home_outlined, page == TvPage.HOME, Modifier.focusRequester(homeFocus)) {
                                page = TvPage.HOME
                            }
                        }
                        item {
                            TvNavigationItem(stringResource(R.string.search), R.drawable.search, page == TvPage.SEARCH, Modifier.focusRequester(searchFocus)) {
                                page = TvPage.SEARCH
                            }
                        }
                        item {
                            TvNavigationItem(stringResource(R.string.queue), R.drawable.queue_music, page == TvPage.QUEUE, Modifier.focusRequester(queueFocus)) {
                                page = TvPage.QUEUE
                            }
                        }
                        item {
                            TvNavigationItem(tvLocalizedString(R.string.tv_photo_frame_title, R.string.tv_photo_frame_title_zh_tw),
                                R.drawable.insert_photo, false) {
                                page = TvPage.FRAME
                            }
                        }
                    }
                    TvNavigationItem(
                        tvLocalizedString(R.string.tv_stop_and_exit, R.string.tv_stop_and_exit_zh_tw),
                        R.drawable.close,
                        false,
                    ) { onExitApp() }
                }
            }

            when (page) {
                TvPage.HOME -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        state = rememberLazyListState(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (localSongs.isNotEmpty()) item {
                            TvHeading(
                                stringResource(R.string.quick_picks),
                                tvLocalizedString(R.string.tv_listening_history, R.string.tv_listening_history_zh_tw),
                            )
                        }
                        items(localSongs, key = { "local_${it.id}" }) { song ->
                            TvSongRow(
                                title = song.title,
                                artists = song.orderedArtists.joinToString { it.name },
                                thumbnailUrl = song.thumbnailUrl?.resize(160, 160),
                                enabled = ready,
                            ) {
                                playerConnection?.playQueue(
                                    ListQueue(
                                        title = context.getString(R.string.tv_quick_picks),
                                        items = localSongs.map { it.toMediaItem() },
                                        startIndex = localSongs.indexOfFirst { it.id == song.id },
                                    )
                                )
                            }
                        }
                        homeSections.forEachIndexed { sectionIndex, section ->
                            item(key = "section_$sectionIndex") {
                                TvHeading(section.title, section.label)
                            }
                            items(section.songs, key = { "remote_${it.id}" }) { song ->
                                TvSongRow(
                                    title = song.title,
                                    artists = song.artists.joinToString { it.name },
                                    thumbnailUrl = song.thumbnail.resize(160, 160),
                                    enabled = ready,
                                ) {
                                    playerConnection?.playQueue(
                                        YouTubeQueue(WatchEndpoint(videoId = song.id), song.toMediaMetadata())
                                    )
                                }
                            }
                        }
                        if (homeLoading) item { TvMessage(stringResource(R.string.tv_loading)) }
                        if (homeFailed) item {
                            TvButton(stringResource(R.string.retry), false) { homeReload++ }
                        }
                        if (!homeLoading && quickPicks.isEmpty() && homeSections.isEmpty() && !homeFailed) {
                            item { TvMessage(stringResource(R.string.tv_empty_home)) }
                        }
                    }
                }
                TvPage.SEARCH -> {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        TvHeading(stringResource(R.string.search))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = searchText,
                                onValueChange = { searchText = it },
                                label = { Text(stringResource(R.string.tv_search_hint)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                                modifier = Modifier.weight(1f).focusRequester(searchFieldFocus),
                            )
                            TvButton(stringResource(R.string.search), false) { submitSearch() }
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(searchSongs, key = { it.id }) { song ->
                                TvSongRow(
                                    title = song.title,
                                    artists = song.artists.joinToString { it.name },
                                    thumbnailUrl = song.thumbnail.resize(160, 160),
                                    enabled = ready,
                                ) {
                                    playerConnection?.playQueue(
                                        YouTubeQueue(WatchEndpoint(videoId = song.id), song.toMediaMetadata())
                                    )
                                }
                            }
                            if (searchLoading) item { TvMessage(stringResource(R.string.tv_loading)) }
                            if (searchFailed) item {
                                TvButton(stringResource(R.string.retry), false) { searchReload++ }
                            }
                            if (searchTerm.isNotBlank() && !searchLoading && !searchFailed && searchSongs.isEmpty()) {
                                item { TvMessage(stringResource(R.string.tv_no_results)) }
                            }
                        }
                    }
                }
                TvPage.QUEUE -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { TvHeading(stringResource(R.string.queue)) }
                        if (queueWindows.isEmpty()) item { TvMessage(stringResource(R.string.tv_empty_queue)) }
                        itemsIndexed(queueWindows, key = { index, window -> "${index}_${window.mediaItem.mediaId}" }) { index, window ->
                            val item = window.mediaItem
                            TvSongRow(
                                title = item.mediaMetadata.title?.toString().orEmpty(),
                                artists = item.mediaMetadata.artist?.toString().orEmpty(),
                                thumbnailUrl = item.mediaMetadata.artworkUri?.toString()?.resize(160, 160),
                                enabled = ready,
                                selected = index == currentQueueIndex,
                            ) {
                                val player = playerConnection?.player ?: return@TvSongRow
                                val timeline = player.currentTimeline
                                val actualIndex = (0 until timeline.windowCount).firstOrNull {
                                    timeline.getWindow(it, Timeline.Window()).uid == window.uid
                                } ?: return@TvSongRow
                                player.seekTo(actualIndex, 0L)
                                playerConnection.play()
                            }
                        }
                    }
                }
                TvPage.FRAME -> Unit
            }
        }

        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                metadata?.thumbnailUrl?.let { TvArtwork(it.resize(144, 144), 64.dp) }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = metadata?.title ?: stringResource(R.string.tv_nothing_playing),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = metadata?.artists?.joinToString { it.name }.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TvButton(stringResource(R.string.previous), false, enabled = ready) {
                    playerConnection?.seekToPrevious()
                }
                TvButton(stringResource(if (playing) R.string.pause else R.string.play), false, enabled = ready) {
                    playerConnection?.togglePlayPause()
                }
                TvButton(stringResource(R.string.next), false, enabled = ready) {
                    playerConnection?.seekToNext()
                }
            }
        }
    }
}

@Composable
private fun TvHeading(text: String, label: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 4.dp, height = 48.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
        Column {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (!label.isNullOrBlank()) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TvNavigationItem(
    text: String,
    icon: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val highlighted = focused || selected
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 58.dp).onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(12.dp),
        color = when {
            focused -> colors.primary
            selected -> colors.primaryContainer
            else -> colors.surface
        },
        border = if (focused) BorderStroke(2.dp, colors.onSurface) else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val contentColor = when {
                focused -> colors.onPrimary
                selected -> colors.onPrimaryContainer
                else -> colors.onSurfaceVariant
            }
            Icon(painterResource(icon), contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun tvLocalizedString(english: Int, chinese: Int, vararg args: Any): String =
    stringResource(if (LocalConfiguration.current.locales[0].language == "zh") chinese else english, *args)

@Composable
private fun TvArtwork(url: String?, size: Dp) {
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Icon(painterResource(R.drawable.music_note), contentDescription = null, modifier = Modifier.size(32.dp))
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                error = painterResource(R.drawable.music_note),
            )
        }
    }
}

@Composable
private fun TvMessage(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(12.dp))
}

@Composable
private fun TvButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .heightIn(min = 52.dp),
        border = if (focused) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected || focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected || focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TvSongRow(
    title: String,
    artists: String,
    thumbnailUrl: String?,
    enabled: Boolean,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val active = selected || focused
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(12.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (focused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvArtwork(thumbnailUrl, 72.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (artists.isNotBlank()) {
                    Text(
                        text = artists,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
