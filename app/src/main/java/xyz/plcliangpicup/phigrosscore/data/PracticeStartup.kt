package xyz.plcliangpicup.phigrosscore.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** A real readiness barrier; elapsed countdown time never substitutes for loading. */
internal suspend fun practiceStartAfterPreparation(
    prepare: suspend () -> Unit,
    onCountdown: ((Int) -> Unit)?,
    start: suspend () -> Unit,
) {
    prepare()
    currentCoroutineContext().ensureActive()
    if (onCountdown != null) {
        for (number in 3 downTo 1) {
            onCountdown(number)
            delay(PRACTICE_LOOP_WAIT_MS / 3)
        }
        onCountdown(0)
    }
    currentCoroutineContext().ensureActive()
    start()
}
