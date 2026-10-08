/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.tv

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.metrolist.music.BuildConfig
import com.metrolist.music.R
import com.metrolist.music.utils.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shared TV update state so the HOME banner and the UPDATE page stay in sync.
 * Phone flow only opens a browser; TV has no browser, so this state also owns
 * the download-to-cache + system-installer flow with unknown-sources guidance.
 */
class TvUpdateState {
    var isChecking by mutableStateOf(false)
    var hasUpdate by mutableStateOf(false)
    var latestVersion by mutableStateOf<String?>(null)
    var changelog by mutableStateOf<String?>(null)
    var checkError by mutableStateOf<String?>(null)
    var showChangelog by mutableStateOf(false)

    var isDownloading by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
    var progressText by mutableStateOf<String?>(null)
    var downloadedFile: File? by mutableStateOf(null)
    var downloadError by mutableStateOf<String?>(null)
    var hasPartialDownload by mutableStateOf(false)
    var canInstall by mutableStateOf(false)

    fun refreshInstallPermission(context: Context) {
        canInstall = Updater.canInstallPackages(context)
    }

    fun check(
        scope: CoroutineScope,
        context: Context,
        failedText: (String) -> String,
        noApkText: String,
        forceRefresh: Boolean = true,
    ) {
        if (isChecking) return
        scope.launch {
            isChecking = true
            checkError = null
            withContext(Dispatchers.IO) {
                Updater.checkForUpdate(forceRefresh = forceRefresh)
                    .onSuccess { (releaseInfo, update) ->
                        latestVersion = releaseInfo?.versionName
                        hasUpdate = update
                        changelog = releaseInfo?.description
                        if (update && releaseInfo != null &&
                            Updater.getDownloadUrlForCurrentVariant(releaseInfo) == null
                        ) {
                            hasUpdate = false
                            checkError = noApkText
                        }
                        if (releaseInfo != null) {
                            val staged = Updater.updateApkFile(context, releaseInfo.versionName)
                            if (!update && staged.exists()) {
                                // Already running this release; the staged APK would never be installed.
                                staged.delete()
                            }
                            downloadedFile = if (staged.exists() && staged.length() > 0) staged else null
                            hasPartialDownload = downloadedFile == null &&
                                Updater.partialDownloadLength(staged) > 0
                            // Drop APKs staged for other versions so the cache does not grow per release.
                            Updater.removeOtherStagedApks(staged)
                        }
                    }.onFailure {
                        checkError = failedText(it.message ?: "Unknown error")
                    }
            }
            refreshInstallPermission(context)
            isChecking = false
        }
    }

    fun download(
        scope: CoroutineScope,
        context: Context,
        downloadingText: (Int) -> String,
        failedText: (String) -> String,
    ) {
        val releaseInfo = Updater.getCachedLatestRelease() ?: return
        val url = Updater.getDownloadUrlForCurrentVariant(releaseInfo) ?: return
        if (isDownloading) return
        scope.launch {
            isDownloading = true
            downloadError = null
            val dest = Updater.updateApkFile(context, releaseInfo.versionName)
            val expectedTotal = Updater.getAssetForCurrentVariant(releaseInfo)?.size?.takeIf { it > 0 }
            val resumedFrom = Updater.partialDownloadLength(dest)
            if (expectedTotal != null && resumedFrom > 0) {
                progress = (resumedFrom.toFloat() / expectedTotal).coerceIn(0f, 1f)
                progressText = downloadingText((resumedFrom * 100 / expectedTotal).toInt())
            } else {
                progress = 0f
                progressText = null
            }
            val result = withContext(Dispatchers.IO) {
                var lastProgressUpdateAt = 0L
                Updater.downloadApk(url, dest, expectedTotal = expectedTotal) { downloaded, total ->
                    val now = SystemClock.elapsedRealtime()
                    val isComplete = total != null && downloaded >= total
                    if (isComplete || now - lastProgressUpdateAt >= PROGRESS_UPDATE_INTERVAL_MILLIS) {
                        lastProgressUpdateAt = now
                        launch(Dispatchers.Main) {
                            if (total != null && total > 0) {
                                progress = (downloaded.toFloat() / total).coerceIn(0f, 1f)
                                progressText = downloadingText((downloaded * 100 / total).toInt())
                            } else {
                                progressText = downloadingText(0)
                            }
                        }
                    }
                }
            }
            // Back on Main: queued progress updates have run, so final state is safe to set.
            refreshInstallPermission(context)
            isDownloading = false
            result.onSuccess {
                downloadedFile = it
                hasPartialDownload = false
                progress = 1f
                progressText = null
            }.onFailure {
                downloadError = failedText(it.message ?: "Unknown error")
                downloadedFile = null
                // Keep the partial file so the next tap resumes instead of restarting.
                hasPartialDownload = Updater.partialDownloadLength(dest) > 0
                if (!hasPartialDownload) progressText = null
            }
        }
    }

    fun startInstall(context: Context): Boolean {
        val file = downloadedFile ?: return false
        if (!Updater.canInstallPackages(context)) {
            Updater.openUnknownSourcesSettings(context)
            return false
        }
        val intent = Updater.buildInstallIntent(context, file) ?: return false
        runCatching { context.startActivity(intent) }
            .onFailure { downloadError = it.message }
        return true
    }
}

@Composable
fun TvUpdatePage(
    state: TvUpdateState,
    modifier: Modifier = Modifier,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val checkButtonFocus = remember { FocusRequester() }
    // Download, install and open-settings never coexist; they share one
    // requester so update transitions can steer focus to the primary action.
    val actionButtonFocus = remember { FocusRequester() }
    // The entry button used to be disabled while an automatic check or download
    // was already running, so the initial focus request could be ignored.
    // Buttons now stay focusable while their own operation runs, but keep the
    // retry as a safety net. Remember whether it landed.
    var entryFocusLanded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        state.refreshInstallPermission(context)
        checkButtonFocus.requestFocus()
    }
    LaunchedEffect(state.isChecking, state.isDownloading) {
        if (!entryFocusLanded && !state.isChecking && !state.isDownloading) {
            checkButtonFocus.requestFocus()
        }
    }
    // When a check reveals an update, move focus to the download button so the
    // next OK press continues the flow. Likewise, when a download finishes,
    // move focus to install (or open-settings). Each transition steers once.
    var downloadSteered by remember { mutableStateOf(false) }
    var installSteered by remember { mutableStateOf(false) }
    val showDownloadAction = state.hasUpdate && state.downloadedFile == null
    val showInstallAction = state.downloadedFile != null
    LaunchedEffect(showDownloadAction, state.isChecking, state.isDownloading) {
        if (showDownloadAction && !state.isChecking && !state.isDownloading) {
            if (!downloadSteered) {
                downloadSteered = true
                installSteered = false
                actionButtonFocus.requestFocus()
            }
        } else {
            downloadSteered = false
        }
    }
    LaunchedEffect(showInstallAction, state.isDownloading) {
        if (showInstallAction && !state.isDownloading) {
            if (!installSteered) {
                installSteered = true
                actionButtonFocus.requestFocus()
            }
        } else {
            installSteered = false
        }
    }
    DisposableEffect(lifecycleOwner, state, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.refreshInstallPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { TvHeading(tvLocalizedString(R.string.tv_update, R.string.tv_update_zh_tw)) }
        item {
            Text(
                text = tvLocalizedString(R.string.tv_update_current, R.string.tv_update_current_zh_tw, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        item {
            Text(
                text = tvLocalizedString(R.string.tv_update_desc, R.string.tv_update_desc_zh_tw),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        if (state.isChecking) {
            item { TvMessage(tvLocalizedString(R.string.tv_update_checking, R.string.tv_update_checking_zh_tw)) }
        }

        state.checkError?.let { item { TvMessage(it) } }

        if (!state.isChecking && !state.hasUpdate && state.latestVersion != null && state.checkError == null) {
            item { TvMessage(tvLocalizedString(R.string.tv_update_up_to_date, R.string.tv_update_up_to_date_zh_tw)) }
        }

        if (state.hasUpdate && state.latestVersion != null) {
            item {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = tvLocalizedString(R.string.tv_update_available, R.string.tv_update_available_zh_tw, state.latestVersion!!),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = tvLocalizedString(R.string.tv_update_install_hint, R.string.tv_update_install_hint_zh_tw),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        if (state.isDownloading || state.progressText != null) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    state.progressText?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        }

        state.downloadError?.let { item { TvMessage(it) } }

        if (state.downloadedFile != null && !state.isDownloading) {
            item { TvMessage(tvLocalizedString(R.string.tv_update_downloaded, R.string.tv_update_downloaded_zh_tw)) }
            if (!state.canInstall) {
                item {
                    Text(
                        text = tvLocalizedString(R.string.tv_update_allow_install_hint, R.string.tv_update_allow_install_hint_zh_tw),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                TvButton(
                    text = tvLocalizedString(R.string.tv_update_check, R.string.tv_update_check_zh_tw),
                    selected = false,
                    modifier = Modifier
                        .focusRequester(checkButtonFocus)
                        .onFocusChanged { if (it.isFocused) entryFocusLanded = true },
                    // Stay focusable while its own check runs (a repeat press is
                    // a guarded no-op); only yield while a download owns the flow.
                    // Disabling the focused button would strand remote focus.
                    enabled = !state.isDownloading,
                ) { onCheck() }
                if (state.hasUpdate) {
                    if (state.downloadedFile != null) {
                        if (state.canInstall) {
                            TvButton(
                                text = tvLocalizedString(R.string.tv_update_install, R.string.tv_update_install_zh_tw),
                                selected = true,
                                modifier = Modifier.focusRequester(actionButtonFocus),
                                enabled = !state.isDownloading,
                            ) { onInstall() }
                        } else {
                            TvButton(
                                text = tvLocalizedString(R.string.tv_update_open_settings, R.string.tv_update_open_settings_zh_tw),
                                selected = true,
                                modifier = Modifier.focusRequester(actionButtonFocus),
                                enabled = !state.isDownloading,
                            ) { onOpenSettings() }
                        }
                    } else {
                        TvButton(
                            text = if (state.hasPartialDownload || state.downloadError != null) {
                                tvLocalizedString(R.string.tv_update_resume, R.string.tv_update_resume_zh_tw)
                            } else {
                                tvLocalizedString(R.string.tv_update_download, R.string.tv_update_download_zh_tw)
                            },
                            selected = true,
                            modifier = Modifier.focusRequester(actionButtonFocus),
                            // Stay focusable while downloading so focus waits on
                            // this button with the progress indicator below.
                            enabled = !state.isChecking,
                        ) { onDownload() }
                    }
                }
            }
        }

        if (state.changelog != null && state.hasUpdate) {
            item {
                TvButton(
                    text = stringResource(
                        if (state.showChangelog) R.string.hide_changelog else R.string.view_changelog,
                    ),
                    selected = false,
                ) { state.showChangelog = !state.showChangelog }
            }
            if (state.showChangelog) {
                item {
                    Text(
                        text = state.changelog!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}

private const val PROGRESS_UPDATE_INTERVAL_MILLIS = 200L
