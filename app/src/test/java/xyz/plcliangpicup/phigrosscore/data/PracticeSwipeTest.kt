package xyz.plcliangpicup.phigrosscore.data

import org.junit.Assert.*
import org.junit.Test

class PracticeSwipeTest {
    @Test fun dragContactDoesNotConsumeVerticalFlick() {
        val swipe = PracticeSwipe().sample(0f, 20f, 16, 1000, 1000f)
        assertFalse(swipe.consumed)
        assertTrue(practiceRecentSwipe(swipe.lastSwipeMs, 1000))
    }
    @Test fun continuousStrokeCannotHitTwoFlicksButDirectionChangeCan() {
        val used = PracticeSwipe().sample(0f, 20f, 16, 1000, 1000f).consume()
        assertTrue(used.sample(0f, 20f, 16, 1016, 1000f).consumed)
        val reversed = used.sample(0f, -20f, 16, 1016, 1000f)
        assertFalse(reversed.consumed)
        assertTrue(practiceRecentSwipe(reversed.lastSwipeMs, 1016))
    }
    @Test fun pauseWithoutStationaryMoveRearmsTheNextSwipe() {
        val used = PracticeSwipe().sample(0f, 20f, 16, 1000, 1000f).consume()
        val paused = used.sample(0f, 0f, 120, 1120, 1000f)
        assertFalse(paused.consumed)
        val next = paused.sample(0f, 20f, 16, 1136, 1000f)
        assertTrue(practiceRecentSwipe(next.lastSwipeMs, 1136))
    }
    @Test fun batchedSamplesAndSeparateFingersHaveIndependentState() {
        val first = PracticeSwipe().sample(0f, 20f, 16, 1000, 1000f).consume()
        val other = PracticeSwipe().sample(20f, 0f, 16, 1000, 1000f)
        assertTrue(first.consumed)
        assertFalse(other.consumed)
        assertEquals(first, first.sample(20f, 0f, 0, 1000, 1000f))
    }
}
