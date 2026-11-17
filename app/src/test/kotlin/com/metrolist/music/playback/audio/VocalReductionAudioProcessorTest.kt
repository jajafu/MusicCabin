package com.metrolist.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.math.sin

class VocalReductionAudioProcessorTest {

    private val sampleRate = 48_000

    @Test
    fun `centre panned tone above the crossover is cancelled`() {
        val processor = stereoProcessor(enabled = true)
        val input = stereoSine(frequencyHz = 1_000.0, frames = sampleRate / 2, invertRight = false)
        val inRms = rms(input)
        val outRms = rms(drain(processor, input))

        // L = R cancels in the difference path; only filter leakage remains.
        assertTrue("expected outRms=$outRms to be far below inRms=$inRms", outRms < 0.1 * inRms)
    }

    @Test
    fun `side tone above the crossover survives`() {
        val processor = stereoProcessor(enabled = true)
        val input = stereoSine(frequencyHz = 1_000.0, frames = sampleRate / 2, invertRight = true)
        val inRms = rms(input)
        val outRms = rms(drain(processor, input))

        assertTrue("expected outRms=$outRms to stay near inRms=$inRms", outRms > 0.7 * inRms)
    }

    @Test
    fun `centre panned bass below the crossover is preserved at original amplitude`() {
        val processor = stereoProcessor(enabled = true)
        val input = stereoSine(frequencyHz = 60.0, frames = sampleRate / 2, invertRight = false)
        val inRms = rms(input)
        val output = drain(processor, input)
        val outRms = rms(output)

        // Two-sided on purpose: a wrong OUTPUT_SCALE (bass doubled or halved)
        // must fail this test, not only a missing low-pass.
        assertEquals("expected outRms=$outRms to stay near inRms=$inRms", inRms, outRms, 0.1 * inRms)

        // Both output channels always carry the same sample (effectively mono).
        while (output.hasRemaining()) {
            val left = output.getShort()
            val right = output.getShort()
            assertEquals(left.toInt(), right.toInt())
        }
    }

    @Test
    fun `mono input passes through untouched`() {
        val processor = VocalReductionAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(sampleRate, 1, C.ENCODING_PCM_16BIT))
        processor.setEnabled(true)

        val input = monoSine(frequencyHz = 1_000.0, frames = sampleRate / 4)
        val inRms = rms(input)
        val outRms = rms(drain(processor, input))
        assertEquals(inRms, outRms, 1e-9)
    }

    @Test
    fun `disabled processor passes through untouched`() {
        val processor = stereoProcessor(enabled = false)
        val input = stereoSine(frequencyHz = 1_000.0, frames = sampleRate / 4, invertRight = false)
        val inRms = rms(input)
        val outRms = rms(drain(processor, input))

        assertEquals(inRms, outRms, 1e-9)
    }

    @Test
    fun `flush drops filter state so silence stays silent`() {
        val processor = stereoProcessor(enabled = true)
        processor.queueInput(stereoSine(frequencyHz = 1_000.0, frames = sampleRate / 2, invertRight = false))
        processor.getOutput()
        processor.flush()

        // Freshly allocated direct buffers are zero-filled and already in read
        // position (position 0, limit = capacity); do NOT flip() - that would
        // collapse the limit to zero and queue nothing.
        val silence = ByteBuffer
            .allocateDirect(480 * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        val output = drain(processor, silence)

        assertEquals(480 * 2, output.remaining() / 2)
        while (output.hasRemaining()) {
            assertEquals(0, output.getShort().toInt())
        }
    }

    @Test
    fun `re-enabling drops stale filter history`() {
        val processor = stereoProcessor(enabled = true)
        processor.queueInput(stereoSine(frequencyHz = 1_000.0, frames = sampleRate / 2, invertRight = false))
        processor.getOutput()

        processor.setEnabled(false)
        val passthroughSilence = ByteBuffer
            .allocateDirect(480 * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(480 * 2, drain(processor, passthroughSilence).remaining() / 2)

        // Re-enabling must not let the pre-disable filter state ring into the
        // new session: with the history dropped, digital silence stays silent.
        processor.setEnabled(true)
        val silenceAfterReEnable = ByteBuffer
            .allocateDirect(480 * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        val output = drain(processor, silenceAfterReEnable)

        assertEquals(480 * 2, output.remaining() / 2)
        while (output.hasRemaining()) {
            assertEquals(0, output.getShort().toInt())
        }
    }

    @Test
    fun `float input format is rejected`() {
        val processor = VocalReductionAudioProcessor()
        var thrown: AudioProcessor.UnhandledAudioFormatException? = null
        try {
            processor.configure(AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_FLOAT))
        } catch (e: AudioProcessor.UnhandledAudioFormatException) {
            thrown = e
        }
        if (thrown == null) {
            fail("expected UnhandledAudioFormatException for float input")
        }
    }

    @Test
    fun `queue end of stream reports ended once drained`() {
        val processor = stereoProcessor(enabled = true)
        processor.queueInput(stereoSine(frequencyHz = 1_000.0, frames = 480, invertRight = false))
        processor.getOutput()

        processor.queueEndOfStream()
        assertTrue(processor.isEnded)
    }

    private fun stereoProcessor(enabled: Boolean): VocalReductionAudioProcessor =
        VocalReductionAudioProcessor().apply {
            configure(AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
            setEnabled(enabled)
        }

    private fun drain(
        processor: VocalReductionAudioProcessor,
        input: ByteBuffer,
    ): ByteBuffer {
        processor.queueInput(input)
        return processor.getOutput()
    }

    /** Interleaved stereo sine with amplitude 0.5; the right channel is inverted for side content. */
    private fun stereoSine(
        frequencyHz: Double,
        frames: Int,
        invertRight: Boolean,
    ): ByteBuffer =
        ByteBuffer.allocateDirect(frames * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                repeat(frames) { i ->
                    val value = (sin(2.0 * PI * frequencyHz * i / sampleRate) * 0.5 * 32767.0).toInt().toShort()
                    val right = if (invertRight) (-value.toInt()).toShort() else value
                    putShort(value)
                    putShort(right)
                }
                flip()
            }

    private fun monoSine(
        frequencyHz: Double,
        frames: Int,
    ): ByteBuffer =
        ByteBuffer.allocateDirect(frames * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                repeat(frames) { i ->
                    putShort((sin(2.0 * PI * frequencyHz * i / sampleRate) * 0.5 * 32767.0).toInt().toShort())
                }
                flip()
            }

    /** Non-consuming RMS: reads a copy, restoring the byte order that duplicate() drops. */
    private fun rms(buffer: ByteBuffer): Double {
        val view = buffer.duplicate()
        view.order(buffer.order())
        var sumSquares = 0.0
        var count = 0
        while (view.hasRemaining()) {
            val value = view.getShort().toDouble() / 32768.0
            sumSquares += value * value
            count++
        }
        return if (count == 0) 0.0 else sqrt(sumSquares / count)
    }
}
