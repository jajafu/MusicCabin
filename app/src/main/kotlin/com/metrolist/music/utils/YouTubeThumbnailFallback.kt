/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import coil3.intercept.Interceptor
import coil3.network.HttpException
import coil3.request.ErrorResult
import coil3.request.ImageResult

/**
 * Retries YouTube video thumbnails at a lower resolution when the requested file is missing.
 *
 * YouTube only generates `maxresdefault`/`sddefault` images for some videos; the higher-resolution
 * URLs return HTTP 404 for the rest (the lower variants such as `hqdefault` still exist). Without
 * a fallback those items render with no cover at all. This interceptor steps the `i.ytimg.com`
 * URL down one level per failed attempt, so every list, player, notification, and car surface
 * benefits without touching each call site.
 */
class YouTubeThumbnailFallbackInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        var current = chain
        var result = current.proceed()
        var attempts = 0
        while (result is ErrorResult && attempts < MAX_DOWNGRADE_ATTEMPTS) {
            val url = current.request.data as? String ?: break
            if (!isMissingThumbnail(result.throwable)) break
            val fallback = downgradeYouTubeThumbnail(url) ?: break
            current = current.withRequest(current.request.newBuilder().data(fallback).build())
            result = current.proceed()
            attempts++
        }
        return result
    }

    private fun isMissingThumbnail(throwable: Throwable): Boolean {
        val httpException = throwable as? HttpException ?: return false
        return httpException.response.code == 404
    }
}

private const val MAX_DOWNGRADE_ATTEMPTS = 4

/**
 * Returns the same `i.ytimg.com` thumbnail URL one resolution step lower, or null when the URL
 * is not a YouTube video thumbnail or is already at the lowest variant. Query parameters are
 * preserved by replacing only the file name.
 */
internal fun downgradeYouTubeThumbnail(url: String): String? {
    if (!url.contains("i.ytimg.com")) return null
    return when {
        "maxresdefault.jpg" in url -> url.replace("maxresdefault.jpg", "sddefault.jpg")
        "sddefault.jpg" in url -> url.replace("sddefault.jpg", "hqdefault.jpg")
        "hq720.jpg" in url -> url.replace("hq720.jpg", "hqdefault.jpg")
        "hqdefault.jpg" in url -> url.replace("hqdefault.jpg", "mqdefault.jpg")
        "mqdefault.jpg" in url -> url.replace("mqdefault.jpg", "default.jpg")
        else -> null
    }
}
