/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.metrolist.music.BuildConfig
import com.metrolist.music.R

@Immutable
sealed class Screens(
    @StringRes val titleId: Int,
    @DrawableRes val iconIdInactive: Int,
    @DrawableRes val iconIdActive: Int,
    val route: String,
) {
    object Home : Screens(
        titleId = R.string.home,
        iconIdInactive = R.drawable.home_outlined,
        iconIdActive = R.drawable.home_filled,
        route = "home"
    )

    object Search : Screens(
        titleId = R.string.search,
        iconIdInactive = R.drawable.search,
        iconIdActive = R.drawable.search,
        route = "search_input"
    )

    object ListenTogether : Screens(
        titleId = R.string.together,
        iconIdInactive = R.drawable.group_outlined,
        iconIdActive = R.drawable.group_filled,
        route = "listen_together"
    )

    object Library : Screens(
        titleId = R.string.filter_library,
        iconIdInactive = R.drawable.library_music_outlined,
        iconIdActive = R.drawable.library_music_filled,
        route = "library"
    )

    object PhotoFrame : Screens(
        titleId = R.string.photo_frame,
        iconIdInactive = R.drawable.insert_photo,
        iconIdActive = R.drawable.insert_photo,
        route = "photo_frame"
    )

    object PhotoFrameV2 : Screens(
        titleId = R.string.photo_frame_v2_title,
        iconIdInactive = R.drawable.cloud,
        iconIdActive = R.drawable.cloud,
        route = "photo_frame_v2",
    )

    val isPhotoFrame: Boolean get() = this == PhotoFrame || this == PhotoFrameV2

    companion object {
        fun isPhotoFrameRoute(route: String?) = route == PhotoFrame.route || route == PhotoFrameV2.route

        // A destination may be initialized before this companion (for example by a deep link).
        val MainScreens by lazy {
            buildList {
                add(Home)
                add(Search)
                add(if (BuildConfig.PHOTO_FRAME_V2_AVAILABLE) PhotoFrameV2 else PhotoFrame)
                add(Library)
            }
        }
    }
}
