package xyz.plcliangpicup.phigrosscore.data

import org.junit.Assert.*
import org.junit.Test

class PracticePlaybackClockTest {
    @Test fun quantizedHeadsStaySmoothAtEverySpeed() {
        for (step in 10..40) {
            val speed = step / 20f
            val clock = PracticePlaybackClock()
            var previous = clock.sample(0.0, 0, speed, true).seconds
            for (time in 8L..5000L step 8) {
                val sample = clock.sample((time / 20 * 20) / 1000.0 * speed, time, speed, true)
                assertTrue(sample.advancing)
                assertTrue("rate=$speed time=$time", sample.seconds > previous)
                assertEquals(time / 1000.0 * speed, sample.seconds, .035 * speed)
                previous = sample.seconds
            }
        }
    }
    @Test fun stalledOutputAndSeeksDoNotContinueAnOldTimeline() {
        val clock = PracticePlaybackClock()
        clock.sample(10.0, 1000, 2f, true)
        assertFalse(clock.sample(10.0, 1100, 2f, true).advancing)
        assertEquals(10.0, clock.sample(10.0, 1200, 2f, true).seconds, 0.0)
        clock.reset()
        assertEquals(2.0, clock.sample(2.0, 1300, .5f, false).seconds, 0.0)
    }
}
