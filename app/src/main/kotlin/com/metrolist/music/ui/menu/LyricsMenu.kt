/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.menu

import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.metrolist.music.LocalDatabase
import com.metrolist.music.R
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.lyrics.LyricsEntry
import com.metrolist.music.lyrics.LyricsResyncHelper
import com.metrolist.music.lyrics.LyricsTranslationHelper
import com.metrolist.music.lyrics.LyricsUtils
import com.metrolist.music.lyrics.markManualLyrics
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.ListDialog
import com.metrolist.music.ui.component.Material3MenuGroup
import com.metrolist.music.ui.component.Material3MenuItemData
import com.metrolist.music.ui.component.NewAction
import com.metrolist.music.ui.component.NewActionGrid
import com.metrolist.music.ui.component.TextFieldDialog
import com.metrolist.music.viewmodels.LyricsMenuViewModel
import com.metrolist.music.constants.OpenRouterApiKey
import com.metrolist.music.constants.DeeplApiKey
import com.metrolist.music.constants.AiProviderKey
import com.metrolist.music.constants.TranslateLanguageKey
import com.metrolist.music.constants.TranslateModeKey
import com.metrolist.music.constants.RespectAgentPositioningKey
import com.metrolist.music.constants.ShowIntervalIndicatorKey
import com.metrolist.music.constants.OpenRouterBaseUrlKey
import com.metrolist.music.constants.OpenRouterDefaultBaseUrl
import com.metrolist.music.constants.OpenRouterDefaultModel
import com.metrolist.music.constants.OpenRouterModelKey
import com.metrolist.music.constants.DeeplFormalityKey
import com.metrolist.music.utils.rememberPreference
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun WordTimingEditorDialog(
    lyrics: LyricsEntity?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var entries by remember(lyrics?.lyrics) {
        mutableStateOf(
            lyrics?.lyrics?.let(LyricsUtils::parseLyrics).orEmpty()
        )
    }
    var selectedLine by rememberSaveable { mutableIntStateOf(0) }
    var timingInputs by remember(lyrics?.lyrics) { mutableStateOf<Map<String, String>>(emptyMap()) }
    val selected = entries.getOrNull(selectedLine)
    val hasInvalidTiming =
        timingInputs.values.any { value ->
            parseEditorSeconds(value)?.let { it.isFinite() && it >= 0.0 } != true
        } || entries.hasInvalidTiming()

    LaunchedEffect(entries.size) {
        if (selectedLine !in entries.indices) {
            selectedLine = 0
        }
    }

    DefaultDialog(
        onDismiss = onDismiss,
        modifier = Modifier.fillMaxSize(),
        title = { Text(stringResource(R.string.lyrics_editor_title)) },
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(
                enabled = entries.isNotEmpty() && !hasInvalidTiming,
                onClick = { onSave(LyricsUtils.serializeRichSyncLyrics(entries)) },
            ) { Text(stringResource(R.string.lyrics_editor_save)) }
        },
    ) {
        if (entries.isEmpty()) {
            Text(stringResource(R.string.lyrics_editor_no_synced_lyrics))
        } else {
            if (hasInvalidTiming) {
                Text(
                    text = stringResource(R.string.lyrics_editor_invalid_time),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(entries) { index, entry ->
                        val displayText = if (entry.text.isBlank()) {
                            stringResource(R.string.lyrics_editor_blank_line)
                        } else {
                            entry.text
                        }
                        Text(
                            text = "${formatEditorSeconds(entry.editorLineTimeSeconds())}  $displayText",
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedLine = index }
                                .padding(10.dp),
                            color = if (index == selectedLine) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1.2f).verticalScroll(rememberScrollState()).imePadding(),
                ) {
                    if (selected != null) {
                        val selectedTitle = if (selected.text.isBlank()) {
                            stringResource(R.string.lyrics_editor_blank_line)
                        } else {
                            selected.text
                        }
                        Text(selectedTitle, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        if (selected.words.isNullOrEmpty()) {
                            if (selected.text.isNotBlank()) {
                                TextButton(onClick = {
                                    val start = selected.time / 1000.0
                                    val end = entries.getOrNull(selectedLine + 1)?.editorLineTimeSeconds()
                                        ?: (start + 3.0)
                                    val words = LyricsUtils.buildSkeletonWords(selected.text, start, end)
                                    if (words.isNotEmpty()) {
                                        entries = entries.toMutableList().also {
                                            it[selectedLine] = selected.copy(words = words)
                                        }
                                    }
                                }) { Text(stringResource(R.string.lyrics_editor_generate_skeleton)) }
                            }
                        } else {
                            selected.words.forEachIndexed { wordIndex, word ->
                                val startText =
                                    timingInputs["$selectedLine-$wordIndex-start"]
                                        ?: formatEditorInputSeconds(word.startTime)
                                val endText =
                                    timingInputs["$selectedLine-$wordIndex-end"]
                                        ?: formatEditorInputSeconds(word.endTime)
                                val startValue = parseEditorSeconds(startText)?.takeIf { it.isFinite() }
                                val endValue = parseEditorSeconds(endText)?.takeIf { it.isFinite() }
                                val previousEnd = selected.words.getOrNull(wordIndex - 1)?.endTime
                                val nextStart = selected.words.getOrNull(wordIndex + 1)?.startTime
                                val startInvalid = startValue == null || startValue < 0.0 ||
                                    (endValue != null && editorMillis(startValue) > editorMillis(endValue)) ||
                                    (previousEnd != null && editorMillis(startValue) < editorMillis(previousEnd))
                                val endInvalid = endValue == null || endValue < 0.0 ||
                                    (startValue != null && editorMillis(endValue) < editorMillis(startValue)) ||
                                    (nextStart != null && editorMillis(endValue) > editorMillis(nextStart))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = word.text,
                                        modifier = Modifier
                                            .widthIn(min = 40.dp, max = 100.dp)
                                            .padding(end = 4.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    OutlinedTextField(
                                        value = startText,
                                        onValueChange = { value ->
                                            timingInputs = timingInputs + ("$selectedLine-$wordIndex-start" to value)
                                            parseEditorSeconds(value)?.takeIf { it.isFinite() }?.let { parsed ->
                                                updateEditorWord(entries, selectedLine, wordIndex, parsed, null) { entries = it }
                                            }
                                        },
                                        label = { Text(stringResource(R.string.lyrics_editor_start)) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        isError = startInvalid,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    )
                                    OutlinedTextField(
                                        value = endText,
                                        onValueChange = { value ->
                                            timingInputs = timingInputs + ("$selectedLine-$wordIndex-end" to value)
                                            parseEditorSeconds(value)?.takeIf { it.isFinite() }?.let { parsed ->
                                                updateEditorWord(entries, selectedLine, wordIndex, null, parsed) { entries = it }
                                            }
                                        },
                                        label = { Text(stringResource(R.string.lyrics_editor_end)) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        isError = endInvalid,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatEditorSeconds(seconds: Double): String = String.format(Locale.US, "%.2f", seconds)

private fun formatEditorInputSeconds(seconds: Double): String = String.format(Locale.US, "%.3f", seconds)

private fun parseEditorSeconds(value: String): Double? =
    value.trim().replace(',', '.').toDoubleOrNull()

private fun LyricsEntry.editorLineTimeSeconds(): Double =
    words?.firstOrNull()?.startTime ?: (time / 1000.0)

/** Normalizes editor times to the millisecond precision used by the saved format. */
private fun editorMillis(seconds: Double): Long =
    kotlin.math.floor(seconds * 1000.0 + 0.5).toLong()

private fun List<LyricsEntry>.hasInvalidTiming(): Boolean =
    any { entry ->
        val words = entry.words.orEmpty()
        words.any { word ->
            !word.startTime.isFinite() || !word.endTime.isFinite() ||
                word.startTime < 0.0 || editorMillis(word.endTime) < editorMillis(word.startTime)
        } || words.zipWithNext().any { (current, next) ->
            editorMillis(next.startTime) < editorMillis(current.endTime)
        }
    } || zipWithNext().any { (current, next) ->
        editorMillis(next.editorLineTimeSeconds()) < editorMillis(current.editorLineTimeSeconds())
    }

private fun updateEditorWord(
    entries: List<LyricsEntry>,
    lineIndex: Int,
    wordIndex: Int,
    start: Double?,
    end: Double?,
    update: (List<LyricsEntry>) -> Unit,
) {
    val line = entries.getOrNull(lineIndex) ?: return
    val words = line.words?.toMutableList() ?: return
    val word = words.getOrNull(wordIndex) ?: return
    words[wordIndex] = word.copy(startTime = start ?: word.startTime, endTime = end ?: word.endTime)
    update(entries.toMutableList().also { it[lineIndex] = line.copy(words = words) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsMenu(
    lyricsProvider: () -> LyricsEntity?,
    songProvider: () -> SongEntity?,
    mediaMetadataProvider: () -> MediaMetadata,
    onDismiss: () -> Unit,
    onShowOffsetDialog: () -> Unit = {},
    viewModel: LyricsMenuViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuScope = rememberCoroutineScope()
    
    val openRouterApiKey by rememberPreference(OpenRouterApiKey, "")
    val deeplApiKey by rememberPreference(DeeplApiKey, "")
    val aiProvider by rememberPreference(AiProviderKey, "OpenRouter")
    val translateLanguage by rememberPreference(TranslateLanguageKey, "en")
    val translateMode by rememberPreference(TranslateModeKey, "Literal")
    val openRouterBaseUrl by rememberPreference(OpenRouterBaseUrlKey, OpenRouterDefaultBaseUrl)
    val openRouterModel by rememberPreference(OpenRouterModelKey, OpenRouterDefaultModel)
    val deeplFormality by rememberPreference(DeeplFormalityKey, "default")
    var respectAgentPositioning by rememberPreference(RespectAgentPositioningKey, true)
    var showIntervalIndicator by rememberPreference(ShowIntervalIndicatorKey, true)

    val hasApiKey = if (aiProvider == "DeepL") deeplApiKey.isNotBlank() else openRouterApiKey.isNotBlank()
    
    // Observe the authoritative translation-active state from the singleton; this persists
    // correctly across menu open/close cycles and avoids the lyricsProvider() race condition.
    val hasTranslations by LyricsTranslationHelper.hasActiveTranslations.collectAsStateWithLifecycle()

    var showEditDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showWordTimingEditor by rememberSaveable { mutableStateOf(false) }

    if (showWordTimingEditor) {
        WordTimingEditorDialog(
            lyrics = lyricsProvider(),
            onDismiss = { showWordTimingEditor = false },
            onSave = { editedLyrics ->
                val editedId = mediaMetadataProvider().id
                val existing = lyricsProvider()
                menuScope.launch(Dispatchers.IO) {
                    context.markManualLyrics(editedId)
                    database.query {
                        upsert(
                            (existing ?: LyricsEntity(editedId, editedLyrics, "Manual")).copy(
                                id = editedId,
                                lyrics = editedLyrics,
                                provider = "Manual",
                            ),
                        )
                    }
                    LyricsResyncHelper.triggerResync()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.lyrics_editor_saved, Toast.LENGTH_SHORT).show()
                    }
                }
                showWordTimingEditor = false
            },
        )
    }

    if (showEditDialog) {
        TextFieldDialog(
            onDismiss = { showEditDialog = false },
            icon = { Icon(painter = painterResource(R.drawable.edit), contentDescription = null) },
            title = { Text(text = mediaMetadataProvider().title) },
            initialTextFieldValue = TextFieldValue(lyricsProvider()?.lyrics.orEmpty()),
            singleLine = false,
            onDone = {
                val editedId = mediaMetadataProvider().id
                val editedProvider = lyricsProvider()?.provider ?: "Manual"
                val editedLyrics = it
                menuScope.launch(Dispatchers.IO) {
                    context.markManualLyrics(editedId)
                    database.query {
                        upsert(
                            LyricsEntity(
                                id = editedId,
                                lyrics = editedLyrics,
                                provider = editedProvider,
                            ),
                        )
                    }
                }
            },
        )
    }

    var showSearchDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showSearchResultDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val searchMediaMetadataState = remember { mutableStateOf(mediaMetadataProvider()) }
    var searchMediaMetadata by searchMediaMetadataState
    val (titleField, onTitleFieldChange) =
        rememberSaveable(showSearchDialog, stateSaver = TextFieldValue.Saver) {
            mutableStateOf(
                TextFieldValue(
                    text = mediaMetadataProvider().title,
                ),
            )
        }
    val (artistField, onArtistFieldChange) =
        rememberSaveable(showSearchDialog, stateSaver = TextFieldValue.Saver) {
            mutableStateOf(
                TextFieldValue(
                    text = mediaMetadataProvider().artists.joinToString { it.name },
                ),
            )
        }

    val isNetworkAvailable by viewModel.isNetworkAvailable.collectAsStateWithLifecycle()
    val noInternetMessage = stringResource(R.string.error_no_internet)

    // One-tap lyrics source switch: search all enabled providers with the current
    // track metadata and show the results directly. The keyword inputs stay
    // available as a fallback inside the results dialog.
    fun startLyricsSourceSearch() {
        val current = mediaMetadataProvider()
        searchMediaMetadata = current
        onTitleFieldChange(TextFieldValue(current.title))
        onArtistFieldChange(TextFieldValue(current.artists.joinToString { it.name }))
        viewModel.search(
            current.id,
            current.title,
            current.artists.joinToString { it.name },
            current.duration,
            current.album?.title
        )
        showSearchResultDialog = true

        // Show warning only if network is definitely unavailable
        if (!isNetworkAvailable) {
            Toast.makeText(context, noInternetMessage, Toast.LENGTH_SHORT).show()
        }
    }

    if (showSearchDialog) {
        DefaultDialog(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            onDismiss = { showSearchDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = null
                )
            },
            title = { Text(stringResource(R.string.search_lyrics)) },
            buttons = {
                TextButton(
                    onClick = { showSearchDialog = false },
                ) {
                    Text(stringResource(android.R.string.cancel))
                }

                Spacer(Modifier.width(8.dp))

                TextButton(
                    onClick = {
                        showSearchDialog = false
                        onDismiss()
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_WEB_SEARCH).apply {
                                    putExtra(
                                        SearchManager.QUERY,
                                        "${artistField.text} ${titleField.text} lyrics"
                                    )
                                },
                            )
                        } catch (_: Exception) {
                        }
                    },
                ) {
                    Text(stringResource(R.string.search_online))
                }

                Spacer(Modifier.width(8.dp))

                TextButton(
                    onClick = {
                        // Try search regardless of network status indicator
                        // as it might be a false negative
                        viewModel.search(
                            searchMediaMetadata.id,
                            titleField.text,
                            artistField.text,
                            searchMediaMetadata.duration,
                            searchMediaMetadata.album?.title
                        )
                        showSearchResultDialog = true
                        
                        // Show warning only if network is definitely unavailable
                        if (!isNetworkAvailable) {
                            Toast.makeText(context, noInternetMessage, Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        ) {
            OutlinedTextField(
                value = titleField,
                onValueChange = onTitleFieldChange,
                singleLine = true,
                label = { Text(stringResource(R.string.song_title)) },
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = artistField,
                onValueChange = onArtistFieldChange,
                singleLine = true,
                label = { Text(stringResource(R.string.song_artists)) },
            )
        }
    }

    if (showSearchResultDialog) {
        val results by viewModel.results.collectAsStateWithLifecycle()
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

        var expandedItemIndex by rememberSaveable {
            mutableIntStateOf(-1)
        }

        ListDialog(
            onDismiss = { showSearchResultDialog = false },
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
                        onClick = {
                            showSearchDialog = true
                        },
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
                        .clickable {
                            onDismiss()
                            viewModel.cancelSearch()
                            val pickedId = searchMediaMetadata.id
                            menuScope.launch(Dispatchers.IO) {
                                context.markManualLyrics(pickedId)
                                database.query {
                                    upsert(
                                        LyricsEntity(
                                            id = pickedId,
                                            lyrics = result.lyrics,
                                            provider = result.providerName,
                                        ),
                                    )
                                }
                            }
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

    var lyricsOffset by rememberSaveable { mutableIntStateOf(songProvider()?.lyricsOffset ?: 0) }

    LaunchedEffect(songProvider()) {
        lyricsOffset = songProvider()?.lyricsOffset ?: 0
    }

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    LazyColumn(
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            NewActionGrid(
                actions =
                    listOf(
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.edit),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            text = stringResource(R.string.edit),
                            onClick = {
                                showEditDialog = true
                            },
                        ),
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.edit),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            text = stringResource(R.string.edit_word_timings),
                            onClick = { showWordTimingEditor = true },
                        ),
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.cached),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            text = stringResource(R.string.refetch),
                            onClick = {
                                onDismiss()
                                viewModel.refetchLyrics(mediaMetadataProvider(), lyricsProvider())
                            },
                        ),
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.lyrics),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            text = stringResource(R.string.switch_lyrics_source),
                            onClick = {
                                startLyricsSourceSearch()
                            },
                        ),
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.content_copy),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            text = stringResource(R.string.copy),
                            onClick = {
                                lyricsProvider()?.lyrics?.let { lyrics ->
                                    val plainLyrics =
                                        if (lyrics.startsWith("[")) {
                                            LyricsUtils.parseLyrics(lyrics)
                                                .joinToString("\n") { it.text }
                                        } else {
                                            lyrics
                                        }

                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Lyrics", plainLyrics)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                                }
                            },
                        ),
                    ),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                columns = 4,
            )
        }

        item {
            Material3MenuGroup(
                items = buildList {
                    // Add translation toggle option if API key is configured
                    if (hasApiKey) {
                        add(
                            Material3MenuItemData(
                                title = { Text(stringResource(R.string.ai_lyrics_translation)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.translate),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    if (hasTranslations) {
                                        // Remove translations
                                        lyricsProvider()?.let { lyrics ->
                                            val clearedLyrics = LyricsTranslationHelper.clearTranslations(lyrics)
                                            database.query {
                                                upsert(clearedLyrics)
                                            }
                                            // Resets hasActiveTranslations and clears in-memory translations
                                            LyricsTranslationHelper.triggerClearTranslations()
                                        }
                                    } else {
                                        // Trigger translation
                                        LyricsTranslationHelper.triggerManualTranslation()
                                    }
                                },
                                trailingContent = {
                                    Switch(
                                        checked = hasTranslations,
                                        onCheckedChange = { newCheckedState ->
                                            if (newCheckedState) {
                                                // Enable translations – hasActiveTranslations updates when done
                                                LyricsTranslationHelper.triggerManualTranslation()
                                            } else {
                                                // Disable translations – triggerClearTranslations resets hasActiveTranslations
                                                lyricsProvider()?.let { lyrics ->
                                                    val clearedLyrics = LyricsTranslationHelper.clearTranslations(lyrics)
                                                    database.query {
                                                        upsert(clearedLyrics)
                                                    }
                                                    LyricsTranslationHelper.triggerClearTranslations()
                                                }
                                            }
                                        },
                                        thumbContent = {
                                            Icon(
                                                painter = painterResource(
                                                    id = if (hasTranslations) R.drawable.check else R.drawable.close
                                                ),
                                                contentDescription = null,
                                                modifier = Modifier.size(SwitchDefaults.IconSize)
                                            )
                                        },
                                        colors = SwitchDefaults.colors(
                                            uncheckedThumbColor = MaterialTheme.colorScheme.primaryContainer,
                                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            )
                        )
                    }
                    
                    add(
                        Material3MenuItemData(
                            title = { Text(stringResource(R.string.respect_agent_positioning)) },
                            description = { Text(stringResource(R.string.respect_agent_positioning_desc)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.lyrics),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                respectAgentPositioning = !respectAgentPositioning
                            },
                            trailingContent = {
                                Switch(
                                    checked = respectAgentPositioning,
                                    onCheckedChange = { newCheckedState ->
                                        respectAgentPositioning = newCheckedState
                                    },
                                    thumbContent = {
                                        Icon(
                                            painter = painterResource(
                                                id = if (respectAgentPositioning) R.drawable.check else R.drawable.close
                                            ),
                                            contentDescription = null,
                                            modifier = Modifier.size(SwitchDefaults.IconSize)
                                        )
                                    },
                                    colors = SwitchDefaults.colors(
                                        uncheckedThumbColor = MaterialTheme.colorScheme.primaryContainer,
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        )
                    )
                    
                    add(
                        Material3MenuItemData(
                            title = { Text(stringResource(R.string.show_interval_indicator)) },
                            description = { Text(stringResource(R.string.show_interval_indicator_desc)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.lyrics),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                showIntervalIndicator = !showIntervalIndicator
                            },
                            trailingContent = {
                                Switch(
                                    checked = showIntervalIndicator,
                                    onCheckedChange = { newCheckedState ->
                                        showIntervalIndicator = newCheckedState
                                    },
                                    thumbContent = {
                                        Icon(
                                            painter = painterResource(
                                                id = if (showIntervalIndicator) R.drawable.check else R.drawable.close
                                            ),
                                            contentDescription = null,
                                            modifier = Modifier.size(SwitchDefaults.IconSize)
                                        )
                                    },
                                    colors = SwitchDefaults.colors(
                                        uncheckedThumbColor = MaterialTheme.colorScheme.primaryContainer,
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        )
                    )
                    
                    add(
                        Material3MenuItemData(
                            title = { Text(stringResource(R.string.lyrics_offset)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.fast_forward),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                onDismiss()
                                onShowOffsetDialog()
                            },
                            trailingContent = {
                                Text(
                                    text = "${if (lyricsOffset >= 0) "+" else ""}${lyricsOffset}ms",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    )
                }
            )
        }
    }
}
