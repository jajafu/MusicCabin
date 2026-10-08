package com.metrolist.music.photo

import kotlinx.serialization.Serializable

@Serializable
enum class FrameSelectionType { PICKED_PHOTO, FOLDER }

@Serializable
data class FrameSource(
    val uri: String,
    val name: String,
    val type: FrameSelectionType,
    val photoCount: Int = 0,
    val needsPermission: Boolean = false,
    val unavailable: Boolean = false,
    val scanned: Boolean = false,
    val unreadableCount: Int = 0,
    // Decoded bounds recorded at import; 0 means unknown. Lets stable transfer
    // imports skip revalidation and lets playback pick a screen-matching photo
    // without trial decodes. Bounds ignore EXIF rotation, so they are only a
    // hint: the decoded bitmap stays authoritative for pairing.
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class FramePhoto(val uri: String, val sourceUri: String) {
    val stableId: String get() = uri
}

@Serializable
data class FrameSettings(
    val intervalSeconds: Int = 10,
    val crop: Boolean = true,
    val showClock: Boolean = true,
    val showSongInfo: Boolean = true,
    val showLyrics: Boolean = true,
    val s2tEnabled: Boolean = true,
) {
    internal fun validated() = copy(intervalSeconds = intervalSeconds.takeIf { it in setOf(5, 10, 15, 30, 60) } ?: 10)
}

enum class FrameError { STORAGE, PERMISSION, UNREADABLE, INVALID_IMAGE, MANIFEST }

data class FrameCatalogState(
    val sources: List<FrameSource> = emptyList(),
    val photos: List<FramePhoto> = emptyList(),
    val settings: FrameSettings = FrameSettings(),
    val scanning: Boolean = false,
    val scanCount: Int = 0,
    val error: FrameError? = null,
    val initialized: Boolean = false,
)

internal fun FrameSource.cachedOrientation(): FramePhotoOrientation =
    FramePhotoOrientation.of(width, height)

internal fun mergeFramePhotos(sources: List<FrameSource>, indexed: List<FramePhoto>): List<FramePhoto> {
    val available = sources.filterNot { it.needsPermission || it.unavailable }
    val folderUris = available.filter { it.type == FrameSelectionType.FOLDER }.mapTo(hashSetOf()) { it.uri }
    return buildList {
        available.filter { it.type == FrameSelectionType.PICKED_PHOTO }.forEach {
            add(FramePhoto(it.uri, it.uri))
        }
        addAll(indexed.filter { it.sourceUri in folderUris })
    }.distinctBy(FramePhoto::stableId)
}
