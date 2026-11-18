package com.metrolist.innertube.pages

import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.models.filterVideoSongs
import com.metrolist.innertube.models.filterYoutubeShorts
import com.metrolist.innertube.models.filterAiArtists
import com.metrolist.innertube.models.filterHiddenArtists
import com.metrolist.innertube.models.filterPodcasts

data class BrowseResult(
    val title: String?,
    val items: List<Item>,
) {
    data class Item(
        val title: String?,
        val items: List<YTItem>,
    )

    fun filterExplicit(enabled: Boolean = true) =
        if (enabled) {
            copy(
                items =
                    items.mapNotNull {
                        it.copy(
                            items =
                                it.items
                                    .filterExplicit()
                                    .ifEmpty { return@mapNotNull null },
                        )
                    },
            )
        } else {
            this
        }

    fun filterVideoSongs(disableVideos: Boolean = false) =
        if (disableVideos) {
            copy(
                items =
                    items.mapNotNull {
                        it.copy(
                            items =
                                it.items
                                    .filterVideoSongs(true)
                                    .ifEmpty { return@mapNotNull null },
                        )
                    },
            )
        } else {
            this
        }

    fun filterYoutubeShorts(enabled: Boolean = false) =
        if (enabled) {
            copy(
                items =
                    items.mapNotNull {
                        it.copy(
                            items =
                                it.items
                                    .filterYoutubeShorts(true)
                                    .ifEmpty { return@mapNotNull null },
                        )
                    },
            )
        } else {
            this
        }

    fun filterPodcasts(enabled: Boolean) = filterItems { it.filterPodcasts(enabled) }

    fun filterHiddenArtists(hiddenArtistIds: Set<String>) = filterItems { it.filterHiddenArtists(hiddenArtistIds) }

    fun filterAiArtists(enabled: Boolean) = filterItems { it.filterAiArtists(enabled) }

    private fun filterItems(transform: (List<YTItem>) -> List<YTItem>) =
        copy(items = items.mapNotNull { item -> item.copy(items = transform(item.items)).takeIf { it.items.isNotEmpty() } })
}
