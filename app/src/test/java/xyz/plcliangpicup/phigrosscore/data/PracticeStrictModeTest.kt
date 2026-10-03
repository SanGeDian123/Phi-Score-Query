package xyz.plcliangpicup.phigrosscore.data

import org.junit.Assert.*
import org.junit.Test

class PracticeStrictModeTest {
    @Test fun challengeBoundariesAreSymmetric() {
        for (sign in listOf(-1, 1)) {
            fun grade(ms: Double) = PracticeJudgeRules.Strict.grade(ms * sign / 1000)
            assertEquals(PracticeGrade.PERFECT, grade(40.0))
            assertEquals(PracticeGrade.GOOD, grade(40.001))
            assertEquals(PracticeGrade.GOOD, grade(75.0))
            assertEquals(PracticeGrade.BAD, grade(75.001))
            assertEquals(PracticeGrade.BAD, grade(140.0))
            assertNull(grade(140.001))
        }
    }
    @Test fun normalModeKeepsExistingWindowsAndSpeedScalesRealTime() {
        assertEquals(PracticeGrade.PERFECT, PracticeJudgeRules.Normal.grade(.08))
        assertEquals(PracticeGrade.GOOD, PracticeJudgeRules.Normal.grade(.16))
        assertEquals(PracticeGrade.BAD, PracticeJudgeRules.Normal.grade(.20))
        for (speed in listOf(.5f, 1f, 2f)) {
            assertEquals(PracticeGrade.GOOD, PracticeJudgeRules.Strict.grade(practiceTimingDelta(.06 * speed, speed)))
            assertFalse(practiceMayMiss(1.21 * speed, 1.0 * speed, speed, .14))
            assertTrue(practiceMayMiss(1.221 * speed, 1.0 * speed, speed, .14))
        }
    }
    @Test fun strictCandidatePriorityUsesItsGoodWindow() {
        assertEquals(2, practiceChooseHit(listOf(PracticeHitCandidate(1, 1.10, 0f),
            PracticeHitCandidate(2, 1.05, 30f)), 1.0, 1f, 1000f, .075))
        assertNull(PracticeJudgeRules.Strict.grade(Double.NaN))
    }
}
