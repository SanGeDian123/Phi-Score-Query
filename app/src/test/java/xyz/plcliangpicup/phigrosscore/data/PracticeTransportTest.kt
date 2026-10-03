package xyz.plcliangpicup.phigrosscore.data

import org.junit.Assert.*
import org.junit.Test

class PracticeTransportTest {
    @Test fun seekingPreviewWithRestoredComboDoesNotRunScoreValidation() {
        for (combo in listOf(1, 500, 2077)) {
            assertEquals(0, practiceHudScore(false, 2077, 0, 0, 0, combo))
        }
        assertEquals(1_000_000, practiceHudScore(true, 2077, 2077, 0, 0, 2077))
    }
    @Test fun speedHas31StableDiscretePositions() {
        assertEquals(31, (10..40).map(::practiceSpeed).distinct().size)
        assertEquals(.5f, practiceSpeed(10), 0f)
        assertEquals(2f, practiceSpeed(40), 0f)
        for (step in 10..40) assertEquals(step, practiceSpeedStep(practiceSpeed(step)))
        assertEquals(10, practiceSpeedStep(-2f))
        assertEquals(40, practiceSpeedStep(8f))
    }
    @Test fun speedScalesTransportButNotRealJudgementWindow() {
        for (speed in listOf(.5f, 1f, 2f)) {
            val audio = practiceInterpolatedAudio(30.0, .1, speed)
            assertEquals(30.0 + .1 * speed, audio, 1e-8)
            assertEquals(30.0, practiceVisualTime(audio, 100, speed), 1e-8)
            assertEquals(.08, practiceTimingDelta(.08 * speed, speed), 1e-8)
        }
        assertEquals(30.5, practiceInterpolatedAudio(30.0, 2.0, 2f), 1e-8)
        assertEquals(3000L, PRACTICE_LOOP_WAIT_MS)
    }
    @Test fun candidateSearchCoversRealTimeWindowAtEverySpeed() {
        for (speed in listOf(.5f, 1f, 2f)) {
            val radius = practiceCandidateRadius(speed)
            assertTrue(radius > .20 * speed)
            assertEquals(.201, practiceTimingDelta(radius, speed), 1e-8)
        }
    }
    @Test fun batchedFlickSamplesKeepTheirOwnTiming() {
        val frameChartTime = 10.0
        val frameUptime = 1000L
        assertEquals(9.90, practiceTouchTime(frameChartTime, 900L, frameUptime, 1f), 1e-8)
        assertEquals(10.0, practiceTouchTime(frameChartTime, 1000L, frameUptime, 1f), 1e-8)
        assertEquals(9.80, practiceTouchTime(frameChartTime, 900L, frameUptime, 2f), 1e-8)
    }
    @Test fun delayedTouchUsesAudioSampleAtEventTime() {
        assertEquals(9.80, practiceEventVisualTime(10_000, 1_000, 900, 100, 1f, true), 1e-8)
        assertEquals(10.0, practiceEventVisualTime(10_000, 1_000, 1_100, 100, 2f, true), 1e-8)
        assertEquals(9.9, practiceEventVisualTime(10_500, 1_500, 1_000, 100, 1f, true), 1e-8)
        // A touch during queued startup silence must see the same frozen chart as AUTOPLAY.
        assertEquals(9.95, practiceEventVisualTime(10_000, 1_000, 1_300, 100, .5f, false), 1e-8)
        assertFalse(practiceMayMiss(10.27, 10.0, 1f))
        assertTrue(practiceMayMiss(10.29, 10.0, 1f))
        assertFalse(practiceMayMiss(10.55, 10.0, 2f))
    }
    @Test fun manualAndAutoplayShareLatencyAtEveryPlaybackSpeed() {
        for (speed in listOf(.5f, 1f, 2f)) for (latency in listOf(-200, 0, 200)) {
            val target = 10.0
            val offset = latency / 1000.0 * speed
            val audioMs = ((target + offset) * 1000).toLong()
            assertEquals(target, practiceEventVisualTime(audioMs, 1_000, 1_000, latency, speed, true), .001)
            val hits = mutableListOf<Int>()
            val schedule = PracticeAutoSoundSchedule(doubleArrayOf(target), intArrayOf(7))
            schedule.seek(0.0, offset)
            schedule.dispatch(audioMs / 1000.0, offset) { hits.add(it) }
            assertEquals(listOf(7), hits)
        }
    }
    @Test fun rangeRejectsReversalAndOutOfBounds() {
        val range = PracticeRange.bounded(500.0, -1.0, 120.0)
        assertEquals(119.9, range.start, 1e-8)
        assertEquals(120.0, range.end, 1e-8)
        assertEquals(PracticeRange(0.0, 120.0), PracticeRange.bounded(Double.NaN, Double.NaN, 120.0))
        assertEquals(PracticeRange(0.0, .05), PracticeRange.bounded(10.0, 20.0, .05))
    }
    @Test fun seekClampsInOriginalSongSeconds() {
        val range = PracticeRange(12.0, 20.0)
        assertEquals(12.0, range.seek(13.0, -3.0), 0.0)
        assertEquals(20.0, range.seek(19.0, 3.0), 0.0)
        assertEquals(15.0, range.seek(18.0, -3.0), 0.0)
    }
    @Test fun onlyHoldsActuallyCrossingCursorAreCarried() {
        assertTrue(practiceCarryHold(10.0, 20.0, 15.0))
        assertFalse(practiceCarryHold(10.0, 20.0, 10.0))
        assertFalse(practiceCarryHold(10.0, 20.0, 20.0))
        assertFalse(practiceCarryHold(10.0, 20.0, 21.0))
        // Rewinding before its head makes the note pending again, never an old held note.
        assertFalse(practiceCarryHold(10.0, 20.0, 9.0))
    }
    @Test fun tripleTapRequiresSameSideAnd650msAndResetsAfterTrigger() {
        val taps = PracticeTripleTap()
        assertFalse(taps.tap(-1, 1000))
        assertFalse(taps.tap(-1, 1250))
        assertTrue(taps.tap(-1, 1650))
        assertFalse(taps.tap(-1, 1700))
        taps.reset()
        assertFalse(taps.tap(1, 2000))
        assertFalse(taps.tap(1, 2200))
        assertFalse(taps.tap(1, 2651))
        assertFalse(taps.tap(-1, 2700))
        assertFalse(taps.tap(-1, 2800))
        assertTrue(taps.tap(-1, 2900))
    }
}
