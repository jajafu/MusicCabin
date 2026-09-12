package com.metrolist.music.photo.v2

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

internal fun Modifier.frameStatus() = semantics { liveRegion = LiveRegionMode.Polite }
internal fun Modifier.semanticsForFrameToggle(label: String) = semantics { contentDescription = label }
