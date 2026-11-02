package com.metrolist.music.photo

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest

/**
 * Full-screen song-cover fallback for the frame screens: shown when no photo
 * slide is available (nothing imported, all photos unreadable, or the cloud
 * source has no slide yet) instead of a plain black screen. The cover is the
 * same thumbnail the player page uses and follows the current song, so it
 * updates automatically while the frame stays open. Shared by the phone, TV
 * and Drive frame screens so the empty state stays in sync.
 */
@Composable
internal fun FrameCoverFallback(
    thumbnailUrl: String?,
    scale: ContentScale,
    modifier: Modifier = Modifier,
) {
    if (thumbnailUrl.isNullOrBlank()) return
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(thumbnailUrl)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build(),
        contentDescription = null,
        contentScale = scale,
        modifier = modifier.fillMaxSize(),
    )
}
