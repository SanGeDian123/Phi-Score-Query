package xyz.plcliangpicup.phigrosscore.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.*
import org.junit.Test

class PracticeNoiseLowPassTest {
    private val rate = 48000

    private fun tone(frames: Int, frequency: Double, channels: Int = 1, rightSilent: Boolean = false): ByteArray {
        val bytes = ByteArray(frames * channels * 2)
        repeat(frames) { frame ->
            val value = (12000 * sin(2 * PI * frequency * frame / rate)).roundToInt()
            repeat(channels) { channel ->
                val sample = if (rightSilent && channel == 1) 0 else value
                val offset = (frame * channels + channel) * 2
                bytes[offset] = sample.toByte(); bytes[offset + 1] = (sample shr 8).toByte()
            }
        }
        return bytes
    }

    private fun rms(bytes: ByteArray, firstFrame: Int = 0, channels: Int = 1, channel: Int = 0): Double {
        var squares = 0.0
        val frames = bytes.size / (channels * 2)
        for (frame in firstFrame until frames) {
            val offset = (frame * channels + channel) * 2
            val sample = ((bytes[offset].toInt() and 255) or (bytes[offset + 1].toInt() shl 8)).toShort().toDouble()
            squares += sample * sample
        }
        return sqrt(squares / (frames - firstFrame))
    }

    @Test fun normalMusicAndResetAreExactPassthrough() {
        val filter = PracticeNoiseLowPass(rate, 1)
        val source = tone(rate / 4, 7300.0)
        val output = ByteArray(source.size)
        filter.process(source, 0, source.size, output)
        assertArrayEquals(source, output)
        filter.setBlocked(true)
        filter.process(source, 0, source.size, output)
        assertFalse(filter.isBypassed)
        filter.reset()
        filter.process(source, 0, source.size, output)
        assertArrayEquals(source, output)
        assertEquals(22000.0, filter.cutoffHz, 0.0)
    }

    @Test fun blocksHighFrequenciesButPreservesLowMusic() {
        fun gain(frequency: Double): Double {
            val filter = PracticeNoiseLowPass(rate, 1)
            val source = tone(rate / 2, frequency)
            val output = ByteArray(source.size)
            filter.setBlocked(true)
            filter.process(source, 0, source.size, output)
            return rms(output, rate / 4) / rms(source, rate / 4)
        }
        assertTrue(gain(200.0) in .95..1.1)
        assertTrue(gain(6000.0) < .07)
    }

    @Test fun stereoFilterDoesNotLeakBetweenChannels() {
        val filter = PracticeNoiseLowPass(rate, 2)
        val source = tone(rate / 4, 6000.0, 2, true)
        val output = ByteArray(source.size)
        filter.setBlocked(true)
        filter.process(source, 0, source.size, output)
        assertTrue(rms(output, rate / 8, 2, 0) > 0)
        assertEquals(0.0, rms(output, 0, 2, 1), 0.0)
    }

    @Test fun cutoffTransitionUses100msWallTimeAtEveryPracticeSpeed() {
        for (speed in listOf(.5f, 1f, 2f)) {
            val filter = PracticeNoiseLowPass(rate, 1)
            filter.setPlaybackSpeed(speed)
            filter.setBlocked(true)
            filter.setBlocked(false)
            val restoration = ByteArray((rate * speed * .11).toInt() * 2)
            filter.process(restoration, 0, restoration.size, ByteArray(restoration.size))
            filter.setBlocked(true)
            val half = tone((rate * speed * .05).toInt(), 200.0)
            filter.process(half, 0, half.size, ByteArray(half.size))
            assertEquals(11750.0, filter.cutoffHz, 1.0)
            filter.process(half, 0, half.size, ByteArray(half.size))
            assertEquals(1500.0, filter.cutoffHz, 1.0)
        }
    }

    @Test fun reversingTransitionStartsAtCurrentCutoffAndRestoresBypass() {
        val filter = PracticeNoiseLowPass(rate, 1)
        filter.setBlocked(true)
        filter.setBlocked(false)
        val restoration = ByteArray(rate / 8 * 2)
        filter.process(restoration, 0, restoration.size, ByteArray(restoration.size))
        filter.setBlocked(true)
        val half = tone(rate / 20, 9000.0)
        filter.process(half, 0, half.size, ByteArray(half.size))
        val reversingAt = filter.cutoffHz
        filter.setBlocked(false)
        assertEquals(reversingAt, filter.cutoffHz, 0.0)
        filter.process(half, 0, half.size, ByteArray(half.size))
        assertEquals((reversingAt + 22000) / 2, filter.cutoffHz, 1.0)
        val restored = tone(rate / 8, 9000.0)
        filter.process(restored, 0, restored.size, ByteArray(restored.size))
        assertTrue(filter.isBypassed)
        val output = ByteArray(restored.size)
        filter.process(restored, 0, restored.size, output)
        assertArrayEquals(restored, output)
    }

    @Test fun firstTouchImmediatelyUsesOfficial1500HzThenLaterTouchesLerp() {
        val filter = PracticeNoiseLowPass(rate, 1)
        assertTrue(filter.isBypassed)
        filter.setBlocked(true)
        assertFalse(filter.isBypassed)
        assertEquals(1500.0, filter.cutoffHz, 0.0)
        val music = tone(rate / 8, 6000.0)
        val output = ByteArray(music.size)
        filter.process(music, 0, music.size, output)
        assertTrue(rms(output, rate / 16) / rms(music, rate / 16) < .07)
        filter.setBlocked(false)
        assertEquals(1500.0, filter.cutoffHz, 0.0)
        filter.process(music, 0, music.size, output)
        assertTrue(filter.isBypassed)
        filter.setBlocked(true)
        assertEquals(22000.0, filter.cutoffHz, 0.0)
        val half = tone(rate / 20, 6000.0)
        filter.process(half, 0, half.size, ByteArray(half.size))
        assertEquals(11750.0, filter.cutoffHz, 1.0)
        filter.reset()
        assertTrue(filter.isBypassed)
        filter.setBlocked(true)
        assertEquals(1500.0, filter.cutoffHz, 0.0)
    }

    @Test fun chunkBoundariesDoNotChangeSoundOrModifySource() {
        val source = tone(rate / 4, 6700.0, 2)
        val sourceCopy = source.copyOf()
        val whole = ByteArray(source.size)
        val chunks = ByteArray(source.size)
        val first = PracticeNoiseLowPass(rate, 2).apply { setBlocked(true) }
        val second = PracticeNoiseLowPass(rate, 2).apply { setBlocked(true) }
        first.process(source, 0, source.size, whole)
        var offset = 0
        while (offset < source.size) {
            val bytes = minOf(196, source.size - offset)
            second.process(source, offset, bytes, chunks, offset)
            offset += bytes
        }
        assertArrayEquals(whole, chunks)
        assertArrayEquals(sourceCopy, source)
        assertTrue(abs(first.cutoffHz - second.cutoffHz) < .001)
    }
}
