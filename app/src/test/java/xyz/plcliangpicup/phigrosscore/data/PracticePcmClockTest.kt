package xyz.plcliangpicup.phigrosscore.data

import org.junit.Assert.*
import org.junit.Test

class PracticePcmClockTest {
    @Test fun everyRateKeepsAtLeast120msOutputReserve() {
        val rate = 48000
        assertEquals(3840L, practicePlayingLeadFrames(rate, .5f))
        assertEquals(5760L, practicePlayingLeadFrames(rate, 1f))
        assertEquals(11520L, practicePlayingLeadFrames(rate, 2f))
        assertTrue(practicePlayingLeadFrames(rate, .5f) / (rate * .5f) >= .12f)
        assertTrue(practicePlayingLeadFrames(rate, 2f) <= rate * .30f)
    }
    @Test fun bufferedSilenceDoesNotAdvanceChartOrSkipFirstNotes() {
        val clock = PracticePcmClock(48000, 48000 * 180)
        for (origin in listOf(0, 48000 * 60, 48000 * 120)) {
            assertEquals(origin, clock.consumedFrame(origin, 48000, 48960))
            assertEquals(origin, clock.consumedFrame(origin, 48960, 48960))
            assertEquals(origin + 480, clock.consumedFrame(origin, 49440, 48960))
        }
    }
    @Test fun seekUsesOriginalSongFramesAtEverySpeed() {
        val clock = PracticePcmClock(44100, 44100 * 180)
        for (speed in listOf(.5, 1.0, 2.0)) {
            val origin = clock.frameAt(60200)
            val consumed = (44100 * speed).toLong()
            assertEquals(60200 + (1000 * speed).toInt(), clock.milliseconds(clock.consumedFrame(origin, consumed, 0)))
        }
    }
    @Test fun seekAndCompletionAreBounded() {
        val clock = PracticePcmClock(48000, 48000 * 180)
        assertEquals(0, clock.frameAt(-100))
        assertEquals(48000 * 180, clock.frameAt(999999))
        assertEquals(180000, clock.milliseconds(clock.consumedFrame(48000 * 179, 480000, 0)))
    }
}
