/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import coil3.BitmapImage
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.network.HttpException
import coil3.network.NetworkHeaders
import coil3.network.NetworkResponse
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.size.Size
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class YouTubeThumbnailFallbackTest {
    @Test
    fun `maxres steps down through every variant to default`() {
        val base = "https://i.ytimg.com/vi/abc123"
        val query = "?sqp=-oaymwE"
        var url = "$base/maxresdefault.jpg$query"
        val expected =
            listOf(
                "$base/sddefault.jpg$query",
                "$base/hqdefault.jpg$query",
                "$base/mqdefault.jpg$query",
                "$base/default.jpg$query",
            )
        expected.forEach { next ->
            url = downgradeYouTubeThumbnail(url)!!
            assertEquals(next, url)
        }
        assertEquals(null, downgradeYouTubeThumbnail(url))
    }

    @Test
    fun `hq720 falls back to hqdefault`() {
        assertEquals(
            "https://i.ytimg.com/vi/abc123/hqdefault.jpg",
            downgradeYouTubeThumbnail("https://i.ytimg.com/vi/abc123/hq720.jpg"),
        )
    }

    @Test
    fun `non youtube urls are left alone`() {
        assertEquals(null, downgradeYouTubeThumbnail("https://lh3.googleusercontent.com/a-/x=s100"))
        assertEquals(null, downgradeYouTubeThumbnail("https://i.ytimg.com/vi/abc123/0.jpg"))
    }

    @Test
    fun `interceptor retries a 404 with the downgraded url`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val requested = mutableListOf<String>()
        val chain = recordingChain(context, requested) { data ->
            if (data.endsWith("maxresdefault.jpg")) {
                errorResult(context, data, 404)
            } else {
                successResult(context, data)
            }
        }

        val result = runBlocking {
            YouTubeThumbnailFallbackInterceptor().intercept(chain)
        }

        assertTrue(result is SuccessResult)
        assertEquals(
            listOf(
                "https://i.ytimg.com/vi/abc123/maxresdefault.jpg",
                "https://i.ytimg.com/vi/abc123/sddefault.jpg",
            ),
            requested,
        )
    }

    @Test
    fun `interceptor does not retry non 404 failures`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val requested = mutableListOf<String>()
        val chain = recordingChain(context, requested) { data ->
            errorResult(context, data, 500)
        }

        val result = runBlocking {
            YouTubeThumbnailFallbackInterceptor().intercept(chain)
        }

        assertTrue(result is ErrorResult)
        assertEquals(listOf("https://i.ytimg.com/vi/abc123/maxresdefault.jpg"), requested)
    }

    private fun recordingChain(
        context: Context,
        requested: MutableList<String>,
        respond: (String) -> ImageResult,
    ): Interceptor.Chain {
        val initial =
            ImageRequest
                .Builder(context)
                .data("https://i.ytimg.com/vi/abc123/maxresdefault.jpg")
                .build()
        return object : Interceptor.Chain {
            private var current: ImageRequest = initial
            override val request: ImageRequest get() = current
            override val size: Size = Size.ORIGINAL
            override suspend fun proceed(): ImageResult {
                val data = current.data as String
                requested += data
                return respond(data)
            }
            override fun withRequest(request: ImageRequest): Interceptor.Chain {
                current = request
                return this
            }
            override fun withSize(size: Size): Interceptor.Chain = this
        }
    }

    private fun errorResult(
        context: Context,
        data: String,
        code: Int,
    ): ErrorResult {
        val response = NetworkResponse(code = code, headers = NetworkHeaders.EMPTY)
        val request = ImageRequest.Builder(context).data(data).build()
        val image = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImage()
        return ErrorResult(image, request, HttpException(response))
    }

    private fun successResult(
        context: Context,
        data: String,
    ): SuccessResult {
        val request = ImageRequest.Builder(context).data(data).build()
        val image = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImage()
        return SuccessResult(image, request, DataSource.NETWORK)
    }
}
