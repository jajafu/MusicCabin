/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Audio processor that reduces centre-panned vocals ("vocal reduction").
 *
 * Pop mixes place the lead vocal in the exact centre, so subtracting the
 * right channel from the left cancels it. A plain L-R subtraction also kills
 * the bass, which is mixed mono in the centre too, so the stereo pair is
 * split at a fixed crossover instead:
 *
 *   out = lowPass(L + R) + highPass(L - R)   (scaled by [OUTPUT_SCALE])
 *
 * Below [CROSSOVER_HZ] the mono sum passes through, keeping drums and bass;
 * above it the L-R difference cancels centre content. Both channels receive
 * the same signal, so the output is effectively mono while enabled - that is
 * inherent to this technique. Mono input has no centre/side split and is
 * always passed through untouched.
 */
@UnstableApi
@SuppressWarnings("Deprecated")
class VocalReductionAudioProcessor : AudioProcessor {

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var isActive = false

    @Volatile
    private var vocalReductionEnabled = false

    // Set when re-enabling, consumed by the audio thread in queueInput: stale
    // filter history from a previous session must be dropped, but the filter
    // state itself is only ever touched on the audio thread.
    @Volatile
    private var filterResetPending = false

    private var buffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    private var lowPass: CrossoverBiquad? = null
    private var highPass: CrossoverBiquad? = null

    companion object {
        private const val TAG = "VocalReductionAudioProcessor"

        /**
         * Crossover between the kept bass (channel sum) and the cancelled
         * centre (channel difference). The lead vocal fundamental rarely sits
         * far below it, while kick and bass stay under it in most mixes.
         */
        private const val CROSSOVER_HZ = 200.0

        // Butterworth Q keeps the low-pass/high-pass pair complementary with
        // no peak at the crossover.
        private const val CROSSOVER_Q = 0.7071067811865476

        // 0.5 keeps the bass sum at its original amplitude and side content
        // above the crossover at roughly its per-channel amplitude. Near the
        // crossover the two paths add quadrature-wise, so near-full-scale hard
        //-panned content may peak slightly above full scale; the sample clamp
        // below soft-clips that instead of wrapping.
        private const val OUTPUT_SCALE = 0.5

        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }

    /**
     * Enable or disable vocal reduction. Safe to call during playback: the
     * audio thread picks the new value up on the next input buffer.
     */
    @Synchronized
    fun setEnabled(enabled: Boolean) {
        if (vocalReductionEnabled != enabled) {
            Timber.tag(TAG).d("Vocal reduction %s", if (enabled) "enabled" else "disabled")
            if (enabled) filterResetPending = true
        }
        vocalReductionEnabled = enabled
    }

    fun isEnabled(): Boolean = vocalReductionEnabled

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding

        Timber.tag(TAG)
            .d("Configured: sampleRate=$sampleRate, channels=$channelCount, encoding=$encoding")

        // Only 16-bit PCM stereo/mono, same contract as the custom EQ processor.
        if (encoding != C.ENCODING_PCM_16BIT || channelCount > 2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        // (Re)build the crossover for the current sample rate with fresh state.
        lowPass = CrossoverBiquad.lowPass(CROSSOVER_HZ, sampleRate, CROSSOVER_Q)
        highPass = CrossoverBiquad.highPass(CROSSOVER_HZ, sampleRate, CROSSOVER_Q)

        isActive = true
        return inputAudioFormat
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!vocalReductionEnabled || channelCount != 2 || encoding != C.ENCODING_PCM_16BIT) {
            // Disabled or mono: nothing to cancel, pass through untouched.
            val remaining = inputBuffer.remaining()
            if (remaining == 0) return

            val out = replaceOutputBuffer(remaining)
            out.put(inputBuffer)
            out.flip()
            return
        }

        val inputSize = inputBuffer.remaining()
        if (inputSize == 0) return

        if (filterResetPending) {
            lowPass?.reset()
            highPass?.reset()
            filterResetPending = false
        }

        val out = replaceOutputBuffer(inputSize)
        processAudioBuffer16Bit(inputBuffer, out)
        out.flip()
    }

    /**
     * Process interleaved 16-bit stereo PCM: both output channels receive
     * `0.5 * (lowPass(L+R) + highPass(L-R))`.
     */
    private fun processAudioBuffer16Bit(input: ByteBuffer, output: ByteBuffer) {
        val lp = lowPass
        val hp = highPass
        if (lp == null || hp == null) {
            // Crossover not initialised yet (configure pending): pass through.
            output.put(input)
            return
        }

        val sampleCount = input.remaining() / 2 // 2 bytes per 16-bit sample

        repeat(sampleCount / channelCount) {
            val left = input.getShort().toDouble() / 32768.0
            val right = input.getShort().toDouble() / 32768.0

            val processed = OUTPUT_SCALE * (lp.processSample(left + right) + hp.processSample(left - right))
            val outputSample = (processed * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
            output.putShort(outputSample)
            output.putShort(outputSample)
        }
    }

    override fun getOutput(): ByteBuffer {
        val buffer = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return buffer
    }

    override fun isEnded(): Boolean {
        return inputEnded && outputBuffer.remaining() == 0
    }

    @Deprecated("Deprecated in Java")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false

        // Drop filter state so a seek does not carry ringing across tracks.
        lowPass?.reset()
        highPass?.reset()
    }

    override fun reset() {
        @Suppress("DEPRECATION")
        flush()
        buffer = EMPTY_BUFFER
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
        isActive = false
        lowPass = null
        highPass = null
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (buffer.capacity() < size) {
            buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }
        outputBuffer = buffer
        return buffer
    }
}

/**
 * Minimal RBJ cookbook biquad (Direct Form I) used by the vocal reduction
 * crossover. The custom EQ has its own filter class, but it only implements
 * peaking/shelf types, so the low-pass/high-pass pair lives here.
 */
private class CrossoverBiquad private constructor(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun processSample(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x
        y2 = y1
        y1 = y
        return y
    }

    fun reset() {
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
    }

    companion object {
        fun lowPass(
            frequencyHz: Double,
            sampleRate: Int,
            q: Double,
        ): CrossoverBiquad {
            val w0 = 2.0 * PI * frequencyHz / sampleRate
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * q)

            val b1 = 1.0 - cosW0
            val a0 = 1.0 + alpha
            return CrossoverBiquad(
                b0 = (b1 / 2.0) / a0,
                b1 = b1 / a0,
                b2 = (b1 / 2.0) / a0,
                a1 = (-2.0 * cosW0) / a0,
                a2 = (1.0 - alpha) / a0,
            )
        }

        fun highPass(
            frequencyHz: Double,
            sampleRate: Int,
            q: Double,
        ): CrossoverBiquad {
            val w0 = 2.0 * PI * frequencyHz / sampleRate
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * q)

            val b1 = -(1.0 + cosW0)
            val a0 = 1.0 + alpha
            return CrossoverBiquad(
                b0 = (-b1 / 2.0) / a0,
                b1 = b1 / a0,
                b2 = (-b1 / 2.0) / a0,
                a1 = (-2.0 * cosW0) / a0,
                a2 = (1.0 - alpha) / a0,
            )
        }
    }
}
