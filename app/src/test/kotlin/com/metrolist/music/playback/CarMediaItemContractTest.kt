package com.metrolist.music.playback

import android.app.Application
import androidx.core.net.toUri
import androidx.media3.common.MediaMetadata
import com.metrolist.music.extensions.metadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contract tests for car-side playable item construction. `ResolvingDataSource` injects the real
 * stream URL from the item's customCacheKey (an item without a URI cannot be added to the player),
 * and the in-app queue/now-playing screens read the app-model MediaMetadata from the item tag and
 * dereference it unconditionally — so a rebuilt YouTube item MUST carry both.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CarMediaItemContractTest {

    @OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test
    fun `rebuilt youtube item carries mediaId, uri, customCacheKey and the app metadata tag`() {
        val item = rebuildCarYouTubeMediaItem(
            videoId = "dQw4w9WgXcQ",
            metadata = MediaMetadata.Builder().setTitle("Title").setArtist("Artist").build(),
        )

        assertEquals("dQw4w9WgXcQ", item.mediaId)
        assertEquals("dQw4w9WgXcQ".toUri(), item.localConfiguration?.uri)
        assertEquals("dQw4w9WgXcQ", item.localConfiguration?.customCacheKey)
        assertEquals("Title", item.mediaMetadata.title)
        assertEquals(true, item.mediaMetadata.isPlayable)
        assertEquals(false, item.mediaMetadata.isBrowsable)

        // The app-model tag is what the queue page and scrobbling read; it must never be null.
        assertNotNull(item.metadata)
        assertEquals("dQw4w9WgXcQ", item.metadata?.id)
        assertEquals("Title", item.metadata?.title)
        assertEquals("Artist", item.metadata?.artists?.firstOrNull()?.name)
    }
}
