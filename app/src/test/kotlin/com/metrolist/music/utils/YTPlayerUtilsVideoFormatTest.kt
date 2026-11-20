package com.metrolist.music.utils

import com.metrolist.innertube.models.response.PlayerResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YTPlayerUtilsVideoFormatTest {
    private fun format(
        itag: Int,
        mimeType: String,
        width: Int?,
        height: Int?,
    ) = PlayerResponse.StreamingData.Format(
        itag = itag,
        url = "https://example.com/$itag",
        mimeType = mimeType,
        bitrate = 1_000_000,
        width = width,
        height = height,
        contentLength = 1_000_000,
        quality = "hd360",
        fps = 30,
        qualityLabel = height?.let { "${it}p" },
        averageBitrate = 900_000,
        audioQuality = null,
        approxDurationMs = "200000",
        audioSampleRate = 44100,
        audioChannels = 2,
        loudnessDb = null,
        lastModified = null,
        signatureCipher = null,
        cipher = null,
        audioTrack = null,
    )

    @Test
    fun picksItag18WhenTheAndroidClientReturnsMuxedFormats() {
        val formats = listOf(
            format(itag = 22, mimeType = "video/mp4", width = 1280, height = 720),
            format(itag = 18, mimeType = "video/mp4", width = 640, height = 360),
        )

        assertEquals(18, YTPlayerUtils.selectMuxedVideoFormat(formats)?.itag)
        assertNull(YTPlayerUtils.selectMuxedVideoFormat(emptyList()))
    }

    @Test
    fun fallsBackToTheClosest360pWhenItag18IsMissing() {
        val formats = listOf(
            format(itag = 22, mimeType = "video/mp4", width = 1280, height = 720),
            format(itag = 43, mimeType = "video/webm", width = 640, height = 360),
        )

        assertEquals(43, YTPlayerUtils.selectMuxedVideoFormat(formats)?.itag)
    }

    @Test
    fun ignoresAudioOnlyAndUnresolvedFormats() {
        val formats = listOf(
            format(itag = 140, mimeType = "audio/mp4", width = null, height = null),
            format(itag = 18, mimeType = "video/mp4", width = null, height = null),
        )

        assertNull(YTPlayerUtils.selectMuxedVideoFormat(formats))
    }
}
