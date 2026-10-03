package xyz.plcliangpicup.phigrosscore.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PracticeHudIndicatorsTest {
    @Test fun realtimeAccUsesJudgedNotesAndFinalScoreWeights() {
        assertEquals(100.0, practiceRealtimeAccuracy(0, 0, 0, 0), 0.0)
        assertEquals(82.5, practiceRealtimeAccuracy(1, 1, 0, 0), 0.0)
        assertEquals(41.25, practiceRealtimeAccuracy(1, 1, 1, 1), 0.0)
    }

    @Test fun goodTimingShowsDirectionAndRoundedMilliseconds() {
        assertEquals("GOOD −83 ms · 偏早", practiceGoodTimingLabel(-.0826))
        assertEquals("GOOD +124 ms · 偏晚", practiceGoodTimingLabel(.1236))
    }
}
