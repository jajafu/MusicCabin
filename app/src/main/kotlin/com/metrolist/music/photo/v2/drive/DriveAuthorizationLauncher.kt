package com.metrolist.music.photo.v2.drive

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.CompletableDeferred

/** Keeps a cancelled launch occupied until its matching result arrives, so it cannot resolve a newer launch. */
internal class DriveAuthorizationResultBridge {
    private var pending: CompletableDeferred<Intent?>? = null

    suspend fun launch(request: IntentSenderRequest, launch: (IntentSenderRequest) -> Unit): Intent? {
        if (pending != null) throw DriveException(DriveFailure.UNAVAILABLE)
        val result = CompletableDeferred<Intent?>()
        pending = result
        try {
            launch(request)
        } catch (_: Exception) {
            pending = null
            throw DriveException(DriveFailure.UNAVAILABLE)
        }
        try {
            return result.await()
        } finally {
            // Keep the slot reserved; a late activity result must never complete a different request.
            result.cancel()
        }
    }

    fun complete(result: ActivityResult) {
        val receiver = pending
        pending = null
        if (receiver?.isActive == true) {
            if (result.data?.hasExtra(ActivityResultContracts.StartIntentSenderForResult.EXTRA_SEND_INTENT_EXCEPTION) == true) {
                receiver.completeExceptionally(DriveException(DriveFailure.UNAVAILABLE))
            } else {
                receiver.complete(if (result.resultCode == Activity.RESULT_OK) result.data else null)
            }
        }
    }

    fun dispose() { pending?.cancel() }
}

@Composable
fun rememberDriveAuthorizationUi(): DriveAuthorizationUi {
    val bridge = remember { DriveAuthorizationResultBridge() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult(), bridge::complete)
    DisposableEffect(bridge) { onDispose { bridge.dispose() } }
    return remember(bridge, launcher) {
        DriveAuthorizationUi { sender ->
            bridge.launch(IntentSenderRequest.Builder(sender).build()) { launcher.launch(it) }
        }
    }
}
