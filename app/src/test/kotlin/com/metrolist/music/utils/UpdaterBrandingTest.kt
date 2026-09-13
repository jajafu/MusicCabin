package com.metrolist.music.utils

import com.metrolist.music.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UpdaterBrandingTest {
    @Test
    fun `branded and historical release assets remain recognized`() {
        val names = listOf(
            "MusicCabin-v13.6.75-car.apk",
            "Metrolist-AndroidCar-v13.6.73-car.apk",
            "Metrolist.apk",
            "Metrolist-with-Google-Cast.apk",
            "app-arm64-v8a-release.apk",
            "MusicCabin-v13.6.75-car.apk.sha256",
            "unrelated.apk",
        )
        val assets = Updater.parseAssets(JSONArray().apply {
            names.forEach { name ->
                put(JSONObject().apply {
                    put("name", name)
                    put("browser_download_url", "https://github.com/jajafu/MusicCabin/releases/download/v13.6.75-car/$name")
                    put("size", 123L)
                })
            }
        })

        assertEquals(names.take(5), assets.map { it.name })
        assertEquals(listOf("foss", "foss", "foss", "gms", "foss"), assets.map { it.variant })
        assertEquals(listOf("universal", "universal", "universal", "universal", "arm64-v8a"), assets.map { it.architecture })
    }

    @Test
    fun `current MusicCabin release only updates updater-enabled builds`() {
        val name = "MusicCabin-v13.6.75-car.apk"
        val assets = Updater.parseAssets(JSONArray().put(JSONObject().apply {
            put("name", name)
            put("browser_download_url", "https://github.com/jajafu/MusicCabin/releases/download/v13.6.75-car/$name")
            put("size", 123L)
        }))
        val release = ReleaseInfo("v13.6.75-car", "MusicCabin v13.6.75-car", "", "", assets)
        assertEquals(
            if (BuildConfig.UPDATER_AVAILABLE) assets.single().downloadUrl else null,
            Updater.getDownloadUrlForCurrentVariant(release),
        )
    }

    @Test
    fun `branded release titles compare correctly with installed and historical versions`() {
        assertTrue(Updater.isUpdateAvailable("13.6.74", "MusicCabin v13.6.75-car"))
        assertFalse(Updater.isUpdateAvailable("13.6.75", "MusicCabin v13.6.75-car"))
        assertFalse(Updater.isUpdateAvailable("13.6.75", "MusicCabin v13.6.73-car"))
        assertEquals(0, Updater.compareVersions("v13.6.73-car", "MusicCabin v13.6.73-car"))
    }
}
