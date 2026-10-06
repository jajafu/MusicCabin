package com.metrolist.music.photo

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.composed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.metrolist.music.tv.isAndroidTv
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.di.LyricsHelperEntryPoint
import com.metrolist.music.lyrics.LyricsResult
import com.metrolist.music.lyrics.markManualLyrics
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.ListDialog
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Lyrics source picker shared by the photo frame screens (phone, TV and V2).
 * Searches all enabled providers for the current track and lets the user pick
 * a replacement, which is persisted to the lyrics database so later playback
 * keeps using it. Keyword inputs stay available as a fallback for dirty
 * YouTube titles.
 */
private fun Modifier.tvPickerFocus(): Modifier = composed {
    val tvMode = isAndroidTv(LocalContext.current)
    var focused by remember { mutableStateOf(false) }
    this.onFocusChanged { focused = it.isFocused }
        .then(if (tvMode && focused) Modifier.border(3.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier)
}

@Composable
fun FrameLyricsSourcePicker(
    metadata: MediaMetadata,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val connection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    var results by remember(metadata.id) { mutableStateOf(emptyList<LyricsResult>()) }
    var isLoading by remember(metadata.id) { mutableStateOf(true) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var showAdjust by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable(metadata.id) { mutableStateOf(metadata.title) }
    var artist by rememberSaveable(metadata.id) { mutableStateOf(metadata.artists.joinToString { it.name }) }

    fun runSearch(searchTitle: String, searchArtist: String) {
        searchJob?.cancel()
        searchJob = scope.launch(Dispatchers.IO) {
            isLoading = true
            results = emptyList()
            runCatching {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    LyricsHelperEntryPoint::class.java,
                ).lyricsHelper().getAllLyrics(
                    metadata.id,
                    searchTitle,
                    searchArtist,
                    metadata.duration,
                    metadata.album?.title,
                ) { result ->
                    results = results + result
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(metadata.id) {
        runSearch(title, artist)
    }

    if (showAdjust) {
        DefaultDialog(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            onDismiss = { showAdjust = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = null
                )
            },
            title = { Text(stringResource(R.string.adjust_lyrics_search)) },
            buttons = {
                TextButton(
                    onClick = { showAdjust = false },
                    modifier = Modifier.heightIn(min = 48.dp).tvPickerFocus(),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }

                Spacer(Modifier.size(8.dp))

                TextButton(
                    onClick = {
                        showAdjust = false
                        runSearch(title, artist)
                    },
                    modifier = Modifier.heightIn(min = 48.dp).tvPickerFocus(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text(stringResource(R.string.song_title)) },
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                singleLine = true,
                label = { Text(stringResource(R.string.song_artists)) },
            )
        }
    }

    var expandedItemIndex by rememberSaveable {
        mutableIntStateOf(-1)
    }

    ListDialog(
        onDismiss = {
            searchJob?.cancel()
            onDismiss()
        },
    ) {
        item {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.switch_lyrics_source),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { showAdjust = true },
                    modifier = Modifier.heightIn(min = 48.dp).tvPickerFocus(),
                ) {
                    Text(stringResource(R.string.adjust_lyrics_search))
                }
            }
        }
        itemsIndexed(results) { index, result ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .tvPickerFocus()
                        .clickable {
                            searchJob?.cancel()
                            scope.launch(Dispatchers.IO) {
                                // Mark first so the frame KTV auto-refetch (which
                                // restarts on the DB change below) yields to this pick.
                                context.markManualLyrics(metadata.id)
                                connection?.database?.query {
                                    upsert(
                                        LyricsEntity(
                                            id = metadata.id,
                                            lyrics = result.lyrics,
                                            provider = result.providerName,
                                        ),
                                    )
                                }
                            }
                            onDismiss()
                        }
                        .padding(12.dp)
                        .animateContentSize(),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = result.lyrics,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (index == expandedItemIndex) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = result.providerName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                        )
                        if (result.lyrics.startsWith("[")) {
                            Icon(
                                painter = painterResource(R.drawable.sync),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier =
                                    Modifier
                                        .padding(start = 4.dp)
                                        .size(18.dp),
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        expandedItemIndex = if (expandedItemIndex == index) -1 else index
                    },
                    modifier = Modifier.tvPickerFocus(),
                ) {
                    Icon(
                        painter = painterResource(if (index == expandedItemIndex) R.drawable.expand_less else R.drawable.expand_more),
                        contentDescription = null,
                    )
                }
            }
        }

        if (isLoading) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        if (!isLoading && results.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.lyrics_not_found),
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                )
            }
        }
    }
}
