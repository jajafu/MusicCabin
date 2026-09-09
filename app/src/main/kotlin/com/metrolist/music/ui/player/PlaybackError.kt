/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackException
import com.metrolist.music.BuildConfig
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.models.MediaMetadata
import java.time.Instant

@Composable
fun PlaybackError(
    error: PlaybackException,
    retry: () -> Unit,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val streamClient by playerConnection.currentStreamClient.collectAsState()
    val isOnline by playerConnection.service.connectivityObserver.networkStatus.collectAsState()
    val causes = remember(error) { error.causeChain() }
    val rawErrorMessages =
        remember(causes) {
            causes.mapNotNull { it.message?.takeIf(String::isNotBlank) }.distinct()
        }
    val isExplicitRestricted =
        rawErrorMessages.any {
            it.contains("confirm your age", ignoreCase = true) ||
                it.contains("age-restricted", ignoreCase = true) ||
                it.contains("LOGIN_REQUIRED", ignoreCase = true) ||
                it.contains("403", ignoreCase = true)
        }
    val isJobCancelled =
        rawErrorMessages.any {
            it.contains("job", ignoreCase = true) &&
                (it.contains("cancelled", ignoreCase = true) ||
                    it.contains("canceled", ignoreCase = true) ||
                    it.contains("cancellat", ignoreCase = true))
        }
    val guidance =
        when {
            isExplicitRestricted -> stringResource(R.string.error_explicit_login_recommended)
            isJobCancelled -> stringResource(R.string.error_job_cancelled)
            else -> null
        }
    val errorMessage =
        playbackErrorMessages(
            isOnline = isOnline,
            offlineMessage = stringResource(R.string.error_offline_playback),
            guidance = guidance,
            rawErrorMessages = rawErrorMessages,
        ).joinToString("\n").ifBlank { stringResource(R.string.error_unknown) }
    val causeSummary =
        remember(causes) {
            causes
                .drop(1)
                .joinToString(" → ") { cause ->
                    cause.javaClass.simpleName.ifBlank { cause.javaClass.name }
                }
        }
    val errorCodeName = remember(error) { error.errorCodeName.removePrefix("ERROR_CODE_") }
    val errorReport =
        remember(error, mediaMetadata, streamClient) {
            buildPlaybackErrorReport(error, mediaMetadata, streamClient)
        }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.error),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(
                if (isOnline) R.string.error_playback_failed else R.string.error_no_internet_connection,
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Code: $errorCodeName (${error.errorCode})",
            style =
            MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )

        if (causeSummary.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = causeSummary,
                style =
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = retry,
                shape = RoundedCornerShape(20.dp),
                colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.replay),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.retry))
            }

            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Metrolist Playback Error", errorReport))
                },
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.content_copy),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.copy))
            }
        }
    }
}

internal fun Throwable.causeChain(): List<Throwable> =
    generateSequence(this) { it.cause }.take(8).toList()

internal fun playbackErrorMessages(
    isOnline: Boolean,
    offlineMessage: String,
    guidance: String?,
    rawErrorMessages: List<String>,
): List<String> =
    if (isOnline) (listOfNotNull(guidance) + rawErrorMessages).distinct() else listOf(offlineMessage)

private fun buildPlaybackErrorReport(
    error: PlaybackException,
    mediaMetadata: MediaMetadata?,
    streamClient: String?,
): String =
    buildString {
        appendLine("Metrolist Playback Error Report")
        appendLine("================================")
        appendLine("Time: ${Instant.ofEpochMilli(error.timestampMs)}")
        appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Architecture: ${BuildConfig.ARCHITECTURE}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine()
        appendLine("Media")
        appendLine("-----")
        appendLine("Title: ${mediaMetadata?.title ?: "Unknown"}")
        appendLine("Artists: ${mediaMetadata?.artists?.joinToString(", ") { it.name } ?: "Unknown"}")
        appendLine("ID: ${mediaMetadata?.id ?: "Unknown"}")
        appendLine("Link: ${mediaMetadata?.id?.let { "https://music.youtube.com/watch?v=$it" } ?: "Unknown"}")
        appendLine("Stream client: ${streamClient ?: "Not resolved"}")
        appendLine()
        appendLine("Error")
        appendLine("-----")
        appendLine("Code: ${error.errorCodeName.removePrefix("ERROR_CODE_")} (${error.errorCode})")
        error.causeChain().forEachIndexed { index, cause ->
            appendLine("[$index] ${cause.javaClass.name}: ${cause.message ?: "(no message)"}")
        }
        appendLine()
        appendLine("Stack trace")
        appendLine("-----------")
        append(error.stackTraceToString())
    }

/**
 * Get human-readable error code name from PlaybackException error code
 */
internal val PlaybackException.errorCodeName: String
    get() = when (errorCode) {
        PlaybackException.ERROR_CODE_UNSPECIFIED -> "ERROR_CODE_UNSPECIFIED"
        PlaybackException.ERROR_CODE_REMOTE_ERROR -> "ERROR_CODE_REMOTE_ERROR"
        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> "ERROR_CODE_BEHIND_LIVE_WINDOW"
        PlaybackException.ERROR_CODE_TIMEOUT -> "ERROR_CODE_TIMEOUT"
        PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK -> "ERROR_CODE_FAILED_RUNTIME_CHECK"
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> "ERROR_CODE_IO_UNSPECIFIED"
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED"
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT"
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> "ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE"
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "ERROR_CODE_IO_BAD_HTTP_STATUS"
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "ERROR_CODE_IO_FILE_NOT_FOUND"
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "ERROR_CODE_IO_NO_PERMISSION"
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> "ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED"
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> "ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "ERROR_CODE_PARSING_CONTAINER_MALFORMED"
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "ERROR_CODE_PARSING_MANIFEST_MALFORMED"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED"
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> "ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED"
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "ERROR_CODE_DECODER_INIT_FAILED"
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> "ERROR_CODE_DECODER_QUERY_FAILED"
        PlaybackException.ERROR_CODE_DECODING_FAILED -> "ERROR_CODE_DECODING_FAILED"
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES"
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "ERROR_CODE_DECODING_FORMAT_UNSUPPORTED"
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> "ERROR_CODE_AUDIO_TRACK_INIT_FAILED"
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> "ERROR_CODE_AUDIO_TRACK_WRITE_FAILED"
        PlaybackException.ERROR_CODE_DRM_UNSPECIFIED -> "ERROR_CODE_DRM_UNSPECIFIED"
        PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED -> "ERROR_CODE_DRM_SCHEME_UNSUPPORTED"
        PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED -> "ERROR_CODE_DRM_PROVISIONING_FAILED"
        PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR -> "ERROR_CODE_DRM_CONTENT_ERROR"
        PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> "ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED"
        PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION -> "ERROR_CODE_DRM_DISALLOWED_OPERATION"
        PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR -> "ERROR_CODE_DRM_SYSTEM_ERROR"
        PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED -> "ERROR_CODE_DRM_DEVICE_REVOKED"
        PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED -> "ERROR_CODE_DRM_LICENSE_EXPIRED"
        else -> "UNKNOWN_ERROR_$errorCode"
    }
