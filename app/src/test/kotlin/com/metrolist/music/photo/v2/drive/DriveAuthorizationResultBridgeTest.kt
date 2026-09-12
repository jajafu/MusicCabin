package com.metrolist.music.photo.v2.drive

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DriveAuthorizationResultBridgeTest {
    @Test fun `a late cancelled result cannot complete the next authorization`() = runBlocking {
        val bridge = DriveAuthorizationResultBridge()
        val first = async(start = CoroutineStart.UNDISPATCHED) { bridge.launch(request()) {} }
        first.cancelAndJoin()
        try {
            bridge.launch(request()) { fail("Must not launch over a cancelled pending result") }
            fail("Expected pending launch guard")
        } catch (error: DriveException) {
            assertEquals(DriveFailure.UNAVAILABLE, error.failure)
        }
        bridge.complete(ActivityResult(Activity.RESULT_OK, Intent().putExtra("id", "old")))
        val second = async(start = CoroutineStart.UNDISPATCHED) { bridge.launch(request()) {} }
        assertFalse(second.isCompleted)
        bridge.complete(ActivityResult(Activity.RESULT_OK, Intent().putExtra("id", "new")))
        assertEquals("new", second.await()?.getStringExtra("id"))
    }

    @Test fun `cancelled consent returns no intent and disposed bridge ignores its result`() = runBlocking {
        val bridge = DriveAuthorizationResultBridge()
        val first = async(start = CoroutineStart.UNDISPATCHED) { bridge.launch(request()) {} }
        bridge.complete(ActivityResult(Activity.RESULT_CANCELED, Intent()))
        assertNull(first.await())
        val second = async(start = CoroutineStart.UNDISPATCHED) { bridge.launch(request()) {} }
        bridge.dispose()
        bridge.complete(ActivityResult(Activity.RESULT_OK, Intent()))
        second.join()
        assertTrue(second.isCancelled)
    }

    private fun request(): IntentSenderRequest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        return IntentSenderRequest.Builder(
            PendingIntent.getActivity(context, 0, Intent("test"), PendingIntent.FLAG_IMMUTABLE).intentSender,
        ).build()
    }
}
