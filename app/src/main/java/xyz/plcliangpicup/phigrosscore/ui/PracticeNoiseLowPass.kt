package xyz.plcliangpicup.phigrosscore.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Music-only PCM low-pass for noise fields. Phigros 4.0.1's LevelControl scene
 * stores 1500 Hz / 0.1 s; ProgressControl linearly restores 22000 Hz then
 * disables its AudioLowPassFilter. Its native filter implementation is not
 * available, so this uses an explicit second-order biquad with the serialized
 * resonance Q = 1 rather than a device-dependent equalizer approximation.
 * Owned exclusively by the music output thread.
 */
internal class PracticeNoiseLowPass(private val sampleRate: Int, private val channels: Int) {
    companion object {
        const val BLOCKED_CUTOFF_HZ = 1500.0
        const val UNFILTERED_CUTOFF_HZ = 22000.0
        const val TRANSITION_SECONDS = .1
        private const val RESONANCE_Q = 1.0
        private const val COEFFICIENT_INTERVAL = 32
    }

    init { require(sampleRate > 0 && channels in 1..2) }

    private val x1 = DoubleArray(channels)
    private val x2 = DoubleArray(channels)
    private val y1 = DoubleArray(channels)
    private val y2 = DoubleArray(channels)
    private var outputRate = sampleRate.toDouble()
    private var blocked = false
    private var firstBlock = true
    private var startCutoff = UNFILTERED_CUTOFF_HZ
    private var targetCutoff = UNFILTERED_CUTOFF_HZ
    private var transitionElapsed = TRANSITION_SECONDS
    private var coefficientFrames = 0
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    var cutoffHz = UNFILTERED_CUTOFF_HZ
        private set
    var isBypassed = true
        private set

    fun setPlaybackSpeed(speed: Float) {
        require(speed.isFinite() && speed > 0f)
        outputRate = sampleRate * speed.toDouble()
        coefficientFrames = 0
    }

    fun setBlocked(value: Boolean) {
        if (blocked == value) return
        blocked = value
        // Unity adds the filter with cutoff=1500 and disables it. Its first
        // enabling coroutine therefore starts and ends at 1500; subsequent
        // activations start at the previous/current restoration frequency.
        val firstActivation = value && firstBlock
        if (firstActivation) cutoffHz = BLOCKED_CUTOFF_HZ
        if (value) firstBlock = false
        startCutoff = cutoffHz
        targetCutoff = if (value) BLOCKED_CUTOFF_HZ else UNFILTERED_CUTOFF_HZ
        transitionElapsed = if (firstActivation) TRANSITION_SECONDS else 0.0
        isBypassed = false
        coefficientFrames = 0
    }

    /** Transport changes must never leave the next playback filtered. */
    fun reset() {
        blocked = false
        firstBlock = true
        startCutoff = UNFILTERED_CUTOFF_HZ
        targetCutoff = UNFILTERED_CUTOFF_HZ
        cutoffHz = UNFILTERED_CUTOFF_HZ
        transitionElapsed = TRANSITION_SECONDS
        isBypassed = true
        coefficientFrames = 0
        x1.fill(0.0); x2.fill(0.0); y1.fill(0.0); y2.fill(0.0)
    }

    /** Track the last dry frames without copying ordinary, unfiltered music. */
    fun observePcm(source: ByteArray, offset: Int, bytes: Int) {
        require(bytes % (channels * 2) == 0)
        val frames = bytes / (channels * 2)
        if (frames == 0) return
        for (channel in 0 until channels) {
            if (frames > 1) {
                val previous = readSample(source, offset + (frames - 2) * channels * 2 + channel * 2)
                x2[channel] = previous; y2[channel] = previous
            } else {
                x2[channel] = x1[channel]; y2[channel] = y1[channel]
            }
            val last = readSample(source, offset + (frames - 1) * channels * 2 + channel * 2)
            x1[channel] = last; y1[channel] = last
        }
    }

    fun process(source: ByteArray, offset: Int, bytes: Int, destination: ByteArray, destinationOffset: Int = 0) {
        require(bytes % (channels * 2) == 0)
        require(offset >= 0 && offset + bytes <= source.size)
        require(destinationOffset >= 0 && destinationOffset + bytes <= destination.size)
        if (isBypassed) {
            source.copyInto(destination, destinationOffset, offset, offset + bytes)
            observePcm(source, offset, bytes)
            return
        }
        var input = offset
        var output = destinationOffset
        val end = offset + bytes
        while (input < end) {
            if (transitionElapsed < TRANSITION_SECONDS) {
                transitionElapsed = (transitionElapsed + 1.0 / outputRate).coerceAtMost(TRANSITION_SECONDS)
                cutoffHz = startCutoff + (targetCutoff - startCutoff) * (transitionElapsed / TRANSITION_SECONDS)
            }
            if (!blocked && transitionElapsed >= TRANSITION_SECONDS) {
                // Exact passthrough after release, including low sample rates.
                isBypassed = true
                source.copyInto(destination, output, input, end)
                observePcm(source, input, end - input)
                return
            }
            if (coefficientFrames-- <= 0) {
                updateCoefficients()
                coefficientFrames = COEFFICIENT_INTERVAL - 1
            }
            for (channel in 0 until channels) {
                val sample = readSample(source, input)
                val filtered = b0 * sample + b1 * x1[channel] + b2 * x2[channel] -
                    a1 * y1[channel] - a2 * y2[channel]
                x2[channel] = x1[channel]; x1[channel] = sample
                y2[channel] = y1[channel]; y1[channel] = filtered
                val value = filtered.roundToInt().coerceIn(-32768, 32767)
                destination[output] = value.toByte()
                destination[output + 1] = (value shr 8).toByte()
                input += 2; output += 2
            }
        }
    }

    private fun updateCoefficients() {
        // The AudioTrack rate changes with practice speed. Coefficients use
        // that output rate so the audible cutoff stays at the official Hz.
        val frequency = cutoffHz.coerceIn(1.0, outputRate * .49)
        val omega = 2.0 * PI * frequency / outputRate
        val cosine = cos(omega)
        val alpha = sin(omega) / (2.0 * RESONANCE_Q)
        val divisor = 1.0 + alpha
        b0 = (1.0 - cosine) / (2.0 * divisor)
        b1 = (1.0 - cosine) / divisor
        b2 = b0
        a1 = -2.0 * cosine / divisor
        a2 = (1.0 - alpha) / divisor
    }

    private fun readSample(bytes: ByteArray, offset: Int): Double =
        (((bytes[offset].toInt() and 255) or (bytes[offset + 1].toInt() shl 8)).toShort()).toDouble()
}
