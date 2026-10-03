package xyz.plcliangpicup.phigrosscore.data

import org.junit.Assert.*
import org.junit.Test

class PracticePauseGestureTest {
    private fun tap(gesture: PracticePauseGesture, time: Long, doubleTap: Boolean = true): Boolean {
        gesture.down(1, 20f, 20f, time, true)
        return gesture.up(1, 20f, 20f, time + 40, true, 8f, doubleTap)
    }

    @Test fun defaultRequiresTwoCompletedTapsAndThenResets() {
        val gesture = PracticePauseGesture()
        assertFalse(tap(gesture, 1000))
        assertTrue(tap(gesture, 1160))
        assertFalse(tap(gesture, 1320))
    }

    @Test fun twoOrThreeFingerChordNeverBecomesAPauseTap() {
        for (fingers in 2..3) {
            val gesture = PracticePauseGesture()
            assertFalse(tap(gesture, 1000))
            gesture.down(1, 20f, 20f, 1160, true)
            repeat(fingers - 1) { gesture.reset() } // POINTER_DOWN invalidates the whole stroke.
            assertFalse(gesture.up(1, 20f, 20f, 1200, true, 8f, true))
            assertFalse(tap(gesture, 1280))
        }
    }

    @Test fun hittingAnyNoteInsidePauseAreaCancelsPendingPause() {
        val gesture = PracticePauseGesture()
        repeat(3) { i ->
            gesture.down(1, 20f, 20f, 1000L + i * 120, true)
            gesture.reset() // Tap/Hold/Drag/Flick judgement wins over the HUD.
            assertFalse(gesture.up(1, 20f, 20f, 1040L + i * 120, true, 8f, true))
        }
    }

    @Test fun cancelCannotTurnReleaseOrNextTapIntoPause() {
        val gesture = PracticePauseGesture()
        assertFalse(tap(gesture, 1000))
        gesture.down(1, 20f, 20f, 1100, true)
        gesture.reset()
        assertFalse(gesture.up(1, 20f, 20f, 1140, true, 8f, true))
        assertFalse(tap(gesture, 1200))
    }

    @Test fun movementLeavingTargetAndLongHoldsDoNotPause() {
        val gesture = PracticePauseGesture()
        gesture.down(1, 20f, 20f, 1000, true)
        gesture.move(1, 60f, 20f, true, 8f)
        assertFalse(gesture.up(1, 20f, 20f, 1040, true, 8f, false))
        gesture.down(1, 20f, 20f, 1100, true)
        gesture.move(1, 20f, 20f, false, 8f)
        assertFalse(gesture.up(1, 20f, 20f, 1140, true, 8f, false))
        gesture.down(1, 20f, 20f, 1200, true)
        assertFalse(gesture.up(1, 20f, 20f, 1600, true, 8f, false))
    }

    @Test fun optionalSingleTapStillRejectsMultitouchAndOutsideTouches() {
        val gesture = PracticePauseGesture()
        assertTrue(tap(gesture, 1000, false))
        gesture.down(1, 20f, 20f, 1100, false)
        assertFalse(gesture.up(1, 20f, 20f, 1140, true, 8f, false))
        gesture.down(1, 20f, 20f, 1200, true)
        gesture.reset()
        assertFalse(gesture.up(1, 20f, 20f, 1240, true, 8f, false))
    }

    @Test fun timedOutDoubleTapDoesNotPauseAndGeometryMatchesEveryAspectRatio() {
        val gesture = PracticePauseGesture()
        assertFalse(tap(gesture, 1000))
        assertFalse(tap(gesture, 1500))
        for ((width, height) in listOf(1920f to 1080f, 2400f to 1080f, 1280f to 800f)) {
            val field = PracticeField(width, height)
            val target = practicePauseTarget(field)
            assertTrue(target.contains(target.centerX, target.centerY))
            assertFalse(target.contains(field.centerX, field.centerY))
            assertEquals(field.left + 42f * height / 864f, target.centerX, .001f)
        }
    }
}
