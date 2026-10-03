package xyz.plcliangpicup.phigrosscore.data
import org.junit.Assert.*
import org.junit.Test
class PracticeHitVoicesTest {
    @Test fun deviceRejectingBeforeConfiguredCapacityStillReclaimsAnExistingVoice() {
        val results = java.util.ArrayDeque(listOf(11, 0, 12))
        val stopped = mutableListOf<Int>()
        val voices = PracticeHitVoices(64, { results.removeFirst() }, { stopped.add(it) })
        assertTrue(voices.play(1)); assertTrue(voices.play(2))
        assertEquals(listOf(11), stopped)
    }
    @Test fun saturationReclaimsOldestVoiceAndRetriesImmediately() {
        val results = java.util.ArrayDeque(listOf(11, 12, 0, 13))
        val stopped = mutableListOf<Int>()
        var calls = 0
        val voices = PracticeHitVoices(2, { calls++; results.removeFirst() }, { stopped.add(it) })
        assertTrue(voices.play(1)); assertTrue(voices.play(2)); assertTrue(voices.play(3))
        assertEquals(listOf(11), stopped); assertEquals(4, calls)
    }
    @Test fun temporaryRejectionCanRecoverWithoutAnExistingVoice() {
        val results = java.util.ArrayDeque(listOf(0, 21))
        val voices = PracticeHitVoices(2, { results.removeFirst() }, { fail("No old voice to stop") })
        assertTrue(voices.play(1))
    }
    @Test fun persistentFailureReturnsForBoundedRetryInsteadOfSpinning() {
        var calls = 0
        val voices = PracticeHitVoices(2, { calls++; 0 }, {})
        assertFalse(voices.play(1)); assertEquals(2, calls)
    }
}
