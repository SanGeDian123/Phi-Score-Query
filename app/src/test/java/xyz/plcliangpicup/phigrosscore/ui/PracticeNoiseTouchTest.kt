package xyz.plcliangpicup.phigrosscore.ui

import org.junit.Assert.*
import org.junit.Test
import xyz.plcliangpicup.phigrosscore.data.PracticeNoisePoint

class PracticeNoiseTouchTest {
    private val point = PracticeNoisePoint(40f, 50f)
    @Test fun showAndHideUseOneHundredMillisecondsAndKeepLastPosition() {
        val runtime = PracticeNoiseTouch()
        val first = runtime.update(mapOf(1L to point), 0.0).single()
        assertEquals(0f, first.scale(0.0), 0f)
        assertEquals(.5f, first.scale(.05), .0001f)
        val shown = runtime.update(mapOf(1L to point), .1).single()
        assertEquals(1f, shown.scale(.1), .0001f)
        val hidden = runtime.update(emptyMap(), .1).single()
        assertEquals(point, hidden.point)
        assertEquals(.5f, hidden.scale(.15), .0001f)
        assertTrue(runtime.update(emptyMap(), .21).isEmpty())
    }
    @Test fun movingBlockedFingerKeepsScaleAndOtherFingerSurvivesLift() {
        val runtime = PracticeNoiseTouch()
        runtime.update(mapOf(1L to point, 2L to point), 0.0)
        val moved = PracticeNoisePoint(100f, 200f)
        val both = runtime.update(mapOf(1L to moved, 2L to point), .2)
        assertEquals(moved, both.first { it.id == 1L }.point)
        assertEquals(1f, both.first().scale(.2), 0f)
        runtime.update(mapOf(2L to point), .2)
        val remaining = runtime.update(mapOf(2L to moved), .4).single()
        assertEquals(2L, remaining.id); assertEquals(moved, remaining.point)
        assertEquals(1f, remaining.scale(.4), 0f)
    }
    @Test fun capIsTenAndResetRemovesAllEffects() {
        val runtime = PracticeNoiseTouch()
        assertEquals(10, runtime.update((0L..14L).associateWith { point }, 0.0).size)
        runtime.reset()
        assertTrue(runtime.update(emptyMap(), 0.5).isEmpty())
    }
}
