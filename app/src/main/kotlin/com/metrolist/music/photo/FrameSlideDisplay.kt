package com.metrolist.music.photo

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.BitmapImage
import coil3.compose.asPainter

/** Reads the decoded size without copying pixels; unknown types never pair. */
internal fun coilFrameOrientation(image: coil3.Image): FramePhotoOrientation {
    val bitmap = (image as? BitmapImage)?.bitmap
    return if (bitmap == null || bitmap.isRecycled) {
        FramePhotoOrientation.UNKNOWN
    } else {
        FramePhotoOrientation.of(bitmap.width, bitmap.height)
    }
}

/**
 * Renders one slide. A paired slide shares the frame: side by side on landscape
 * screens, stacked on portrait screens. Singles follow the crop setting as before.
 */
@Composable
internal fun FrameSlideDisplay(
    slide: FrameSlide<coil3.Image>?,
    isLandscape: Boolean,
    scale: ContentScale,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val images = slide?.images ?: return
    if (images.size == 1) {
        val frame = images[0]
        Image(
            painter = remember(frame.image) { frame.image.asPainter(context) },
            contentDescription = null,
            contentScale = scale,
            modifier = modifier.fillMaxSize(),
        )
        return
    }
    if (isLandscape) {
        Row(modifier.fillMaxSize()) {
            images.forEach { frame ->
                Image(
                    painter = remember(frame.image) { frame.image.asPainter(context) },
                    contentDescription = null,
                    contentScale = scale,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
            }
        }
    } else {
        Column(modifier.fillMaxSize()) {
            images.forEach { frame ->
                Image(
                    painter = remember(frame.image) { frame.image.asPainter(context) },
                    contentDescription = null,
                    contentScale = scale,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
            }
        }
    }
}
