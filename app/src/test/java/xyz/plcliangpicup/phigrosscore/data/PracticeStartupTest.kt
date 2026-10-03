package xyz.plcliangpicup.phigrosscore.data

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeStartupTest {
    @Test fun countdownWaitsForActualPreparationAtEveryStartPosition() = runTest {
        for (position in listOf(0, 15, 120)) {
            val ready = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            val task = launch {
                practiceStartAfterPreparation(
                    prepare = { events.add("prepare:$position"); ready.await(); events.add("ready") },
                    onCountdown = { events.add("count:$it") },
                    start = { events.add("start") },
                )
            }
            runCurrent()
            advanceTimeBy(5000); runCurrent()
            assertEquals(listOf("prepare:$position"), events)
            ready.complete(Unit); runCurrent()
            assertEquals(listOf("prepare:$position", "ready", "count:3"), events)
            advanceTimeBy(2999); runCurrent()
            assertFalse(events.contains("start"))
            advanceTimeBy(1); runCurrent(); task.join()
            assertEquals(listOf("prepare:$position", "ready", "count:3", "count:2", "count:1", "count:0", "start"), events)
        }
    }
    @Test fun previewAlsoWaitsForPreparation() = runTest {
        val ready = CompletableDeferred<Unit>()
        var started = false
        val task = launch { practiceStartAfterPreparation({ ready.await() }, null, { started = true }) }
        runCurrent(); advanceTimeBy(5000); runCurrent(); assertFalse(started)
        ready.complete(Unit); task.join(); assertTrue(started)
    }
    @Test fun backgroundDuringPreparationNeverStartsPlayback() = runTest {
        var started = false
        val task = launch { practiceStartAfterPreparation({ awaitCancellation() }, {}, { started = true }) }
        runCurrent(); task.cancelAndJoin(); advanceUntilIdle(); assertFalse(started)
    }
    @Test fun backgroundDuringCountdownNeverStartsPlayback() = runTest {
        var started = false
        val task = launch { practiceStartAfterPreparation({}, {}, { started = true }) }
        runCurrent(); advanceTimeBy(1500); task.cancelAndJoin(); advanceUntilIdle(); assertFalse(started)
    }
}
