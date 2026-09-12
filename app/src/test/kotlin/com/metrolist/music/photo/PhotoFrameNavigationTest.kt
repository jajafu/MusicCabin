package com.metrolist.music.photo

import android.app.Application
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.ui.screens.Screens
import com.metrolist.music.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PhotoFrameNavigationTest {
    @Test
    fun `available photo frame occupies one main menu slot`() {
        val expected = if (BuildConfig.PHOTO_FRAME_V2_AVAILABLE)
            listOf("home", "search_input", "photo_frame_v2", "library")
        else listOf("home", "search_input", "photo_frame", "library")
        assertEquals(expected, Screens.MainScreens.map { it.route })
    }

    @Test
    fun `fullscreen route keeps original tab for back and avoids duplicate entries`() {
        val controller = NavHostController(ApplicationProvider.getApplicationContext()).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            graph = createGraph(startDestination = Screens.Home.route) {
                (Screens.MainScreens + Screens.PhotoFrame).distinct().forEach { screen -> composable(screen.route) {} }
            }
        }
        controller.navigate(Screens.Library.route)
        (Screens.MainScreens.filter { it.isPhotoFrame } + Screens.PhotoFrame).distinct().forEach { frame ->
            repeat(2) { controller.navigate(frame.route) { launchSingleTop = true } }
            assertEquals(frame.route, controller.currentDestination?.route)
            assertTrue(Screens.isPhotoFrameRoute(controller.currentDestination?.route))
            assertTrue(controller.popBackStack())
            assertEquals(Screens.Library.route, controller.currentDestination?.route)
        }
    }
}
