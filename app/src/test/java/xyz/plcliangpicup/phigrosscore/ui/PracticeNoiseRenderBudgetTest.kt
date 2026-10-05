package xyz.plcliangpicup.phigrosscore.ui

import org.junit.Assert.*
import org.junit.Test

class PracticeNoiseRenderBudgetTest {
    @Test fun highResolutionAndDifferentAspectRatiosStayWithinThePixelBudget() {
        for ((w, h) in listOf(1920f to 1080f, 3840f to 2160f, 2048f to 1536f, 2560f to 1080f,
            1080f to 1920f, 640f to 360f, 1f to 1f)) {
            for ((budget, divisor) in listOf(640 * 360 to 1, 128 * 72 to 8)) {
                val size = practiceNoiseRenderSize(w, h, budget, divisor)
                assertTrue(size.width > 0 && size.height > 0)
                assertTrue(size.width * size.height <= budget)
            }
        }
    }

    @Test fun disappearanceAndResizeRefreshImmediatelyAndDisplayRateDoesNotIncreaseMaskRate() {
        val clock = PracticeNoiseVisualClock()
        var updates = 0
        repeat(121) { if (clock.refresh(it / 120.0, 1920f, 1080f, false)) updates++ }
        assertEquals(31, updates)
        assertTrue(clock.refresh(1.001, 1920f, 1080f, true))
        assertTrue(clock.refresh(1.002, 1920f, 1080f, false))
        assertTrue(clock.refresh(1.003, 960f, 540f, false))
        assertTrue(clock.refresh(.1, 960f, 540f, false))
        clock.reset()
        assertTrue(clock.refresh(.101, 960f, 540f, false))
    }
}
