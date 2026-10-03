package xyz.plcliangpicup.phigrosscore.data
import org.junit.Assert.*
import org.junit.Test

class PracticeAutoSoundScheduleTest {
    @Test fun liveLatencyChangeMovesFutureHitsWithoutReplayingPastOnes() {
        val schedule = PracticeAutoSoundSchedule(doubleArrayOf(1.0, 2.0, 3.0), intArrayOf(1, 2, 3))
        val hits = mutableListOf<Int>()
        schedule.seek(0.0, .1)
        schedule.dispatch(1.1, .1) { hits.add(it) }
        schedule.dispatch(2.0, .2) { hits.add(it) }
        schedule.dispatch(2.2, .2) { hits.add(it) }
        schedule.dispatch(3.0, -.1) { hits.add(it) }
        assertEquals(listOf(1, 2, 3), hits)
        schedule.dispatch(4.0, -.1) { hits.add(it) }
        assertEquals(3, hits.size)
    }
    @Test fun notesStayOnAudioClockWithoutAnyRenderFrames() {
        val schedule = PracticeAutoSoundSchedule(doubleArrayOf(.1,.2,.2,.3), intArrayOf(1,2,3,4))
        val hits = mutableListOf<Int>()
        schedule.seek(0.0)
        // No UI frame callback is involved, including simultaneous dense notes.
        schedule.dispatch(.099) { hits.add(it) }; assertTrue(hits.isEmpty())
        schedule.dispatch(.1) { hits.add(it) }; assertEquals(listOf(1), hits)
        schedule.dispatch(.2) { hits.add(it) }; assertEquals(listOf(1,2,3), hits)
        schedule.dispatch(.2) { hits.add(it) }; assertEquals(3, hits.size)
        schedule.dispatch(.3) { hits.add(it) }; assertEquals(listOf(1,2,3,4), hits)
    }
    @Test fun seekSkipsHistoricalHitsAndAllowsReplayAfterBackwardSeek() {
        val schedule = PracticeAutoSoundSchedule(doubleArrayOf(1.0,2.0,3.0), intArrayOf(1,2,3))
        val hits = mutableListOf<Int>()
        schedule.seek(2.0); schedule.dispatch(2.0) { hits.add(it) }
        assertEquals(listOf(2), hits)
        schedule.seek(1.0); schedule.dispatch(1.0) { hits.add(it) }
        assertEquals(listOf(2,1), hits)
    }
}
