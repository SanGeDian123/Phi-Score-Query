package xyz.plcliangpicup.phigrosscore.data
import org.junit.Assert.*
import org.junit.Test
class PracticeHitGateTest {
    @Test fun bothModesAcceptHitsAsSoonAsAudioStarts() {
        for (auto in listOf(false,true)) {
            val gate = PracticeHitGate(); gate.setAutomatic(auto)
            assertNull(gate.request(auto, 0))
            gate.setRunning(true)
            val hit = gate.request(auto, 10)!!
            assertTrue(gate.valid(hit)); assertNull(gate.request(!auto, 10))
        }
    }
    @Test fun pauseResumeNeverReplaysOldQueuedHits() {
        val gate = PracticeHitGate(); gate.setRunning(true)
        val hit = gate.request(false, 10)!!
        gate.setRunning(false); gate.setRunning(true)
        assertFalse(gate.valid(hit)); assertNotNull(gate.request(false, 12))
    }
    @Test fun modeChangesInvalidateOldRequestsButRepeatedStateDoesNot() {
        val gate = PracticeHitGate(); gate.setRunning(true)
        val hit = gate.request(false, 10)!!
        gate.setRunning(true); gate.setAutomatic(false)
        assertTrue(gate.valid(hit))
        gate.setAutomatic(true); gate.setAutomatic(false)
        assertFalse(gate.valid(hit))
    }
    @Test fun boundedRetryCannotPlayAStaleHitOrSurviveRelease() {
        val gate = PracticeHitGate(); gate.setRunning(true)
        val hit = gate.request(false, 10)!!
        assertTrue(gate.canRetry(hit, 14)); assertFalse(gate.canRetry(hit, 35))
        assertTrue(gate.valid(hit)) // A delayed first attempt is still accepted.
        gate.release(); assertFalse(gate.valid(hit))
    }
}
