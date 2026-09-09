/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.innertube

import java.io.FilterInputStream
import java.io.InputStream

internal class UploadProgressInputStream(
    input: InputStream,
    private val contentLength: Long,
    private val onProgress: (Float) -> Unit,
) : FilterInputStream(input) {
    private var bytesRead = 0L

    override fun read(): Int =
        super.read().also { value ->
            if (value >= 0) reportBytesRead(1)
        }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int =
        super.read(buffer, offset, length).also { count ->
            if (count > 0) reportBytesRead(count)
        }

    private fun reportBytesRead(count: Int) {
        bytesRead += count
        onProgress((bytesRead.toFloat() / contentLength).coerceAtMost(1f))
    }
}
