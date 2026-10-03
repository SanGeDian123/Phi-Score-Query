package xyz.plcliangpicup.phigrosscore.data
import org.junit.Assert.*
import org.junit.Test

class PracticeJudgementTest {
    @Test fun earlySwipeRemainsAvailableOnlyForShortFlickWindow() {
        assertTrue(practiceRecentSwipe(1_000L, 1_160L))
        assertTrue(practiceRecentSwipe(1_000L, 1_220L))
        assertFalse(practiceRecentSwipe(1_000L, 1_221L))
        assertFalse(practiceRecentSwipe(1_000L, 999L))
        assertFalse(practiceRecentSwipe(Long.MIN_VALUE, 1_160L))
    }
    @Test fun holdOwnerSurvivesOtherHandThreeNoteChord() {
        val fingers = mutableMapOf<Long, Boolean>(1L to false)
        repeat(3) { index ->
            val other = index.toLong() + 2
            fingers[other] = false
            assertTrue(practiceHoldHasContact(1L, fingers) { it })
            fingers.remove(other)
            assertTrue(practiceHoldHasContact(1L, fingers) { it })
        }
        fingers.remove(1L)
        assertFalse(practiceHoldHasContact(1L, fingers) { it })
    }
    @Test fun holdCanTransferToAnotherFingerOnLane() {
        val fingers = mapOf(2L to true, 3L to false)
        assertTrue(practiceHoldHasContact(1L, fingers) { it })
        assertFalse(practiceHoldHasContact(1L, fingers.mapValues { false }) { it })
    }
    @Test fun denseLaneDoesNotStealEarlierNote() {
        assertEquals(1, practiceChooseHit(listOf(PracticeHitCandidate(1, 1.0, 10f),
            PracticeHitCandidate(2, 1.1, 0f)), 1.09, 1f, 1000f))
    }
    @Test fun simultaneousDistantLanesFollowTouch() {
        assertEquals(2, practiceChooseHit(listOf(PracticeHitCandidate(1, 1.0, 95f),
            PracticeHitCandidate(2, 1.0, 0f)), 1.0, 1f, 1000f))
    }
    @Test fun validHitWinsOverEarlyBad() {
        assertEquals(2, practiceChooseHit(listOf(PracticeHitCandidate(1, 1.19, 0f),
            PracticeHitCandidate(2, 1.1, 40f)), 1.0, 1f, 1000f))
    }
    @Test fun rotatedLinesUseVisiblePoseAndTangentStrip() {
        val frames = PracticeDisplayedTimeline()
        frames.record(100, 1.0); frames.record(116, 1.016); frames.record(132, 1.032)
        assertEquals(1.016, frames.at(125, 2.0), .00001)
        assertEquals(0f, practiceTangentDistance(500f, 80f, 0f, 0f, 90f, 80f), .0001f)
        assertEquals(118.125f, practiceHitRadius(1000f), .001f)
        frames.clear(); assertEquals(2.0, frames.at(125, 2.0), .00001)
    }
}
