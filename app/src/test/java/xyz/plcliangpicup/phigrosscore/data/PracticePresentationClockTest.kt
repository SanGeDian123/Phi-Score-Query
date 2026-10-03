package xyz.plcliangpicup.phigrosscore.data

import org.junit.Assert.*
import org.junit.Test

class PracticePresentationClockTest {
    @Test fun presentationProjectsAtEverySpeedAndStopsAtWrittenFrames() {
        for (step in 10..40) {
            val speed = practiceSpeed(step)
            assertEquals(48_000.0 + 480 * speed, practicePresentedFrame(48_000, 1_000_000_000,
                1_010_000_000, 900_000_000, 40_000, 60_000, 48_000, speed)!!, .001)
            assertEquals(48_100.0, practicePresentedFrame(48_000, 1_000_000_000,
                1_010_000_000, 900_000_000, 40_000, 48_100, 48_000, speed)!!, .001)
        }
    }
    @Test fun startupSilenceOldEpochStalledAndFutureTimestampsUseFallback() {
        assertNull(practicePresentedFrame(100, 1_000_000_000, 1_010_000_000, 900_000_000, 200, 1000, 48000, 1f))
        assertNull(practicePresentedFrame(300, 1_000_000_000, 1_010_000_000, 1_005_000_000, 200, 1000, 48000, 1f))
        assertNull(practicePresentedFrame(300, 1_000_000_000, 1_101_000_000, 900_000_000, 200, 1000, 48000, 1f))
        assertNull(practicePresentedFrame(300, 1_020_000_000, 1_010_000_000, 900_000_000, 200, 1000, 48000, 1f))
    }
}
