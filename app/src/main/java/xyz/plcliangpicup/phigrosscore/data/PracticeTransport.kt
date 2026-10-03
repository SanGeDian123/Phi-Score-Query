package xyz.plcliangpicup.phigrosscore.data

import kotlin.math.roundToInt

internal const val PRACTICE_LOOP_WAIT_MS = 3000L
internal fun practiceSpeed(step: Int): Float = step.coerceIn(10, 40) / 20f
internal fun practiceSpeedStep(value: Float): Int = (value * 20).roundToInt().coerceIn(10, 40)
internal fun practiceVisualTime(audio: Double, latencyMs: Int, speed: Float): Double = audio - latencyMs / 1000.0 * speed
internal fun practiceInterpolatedAudio(sample: Double, elapsed: Double, speed: Float): Double = sample + elapsed.coerceIn(0.0, .25) * speed
internal fun practiceCarryHold(start: Double, end: Double, cursor: Double): Boolean = start < cursor && end > cursor
internal fun practiceTimingDelta(chartDelta: Double, speed: Float): Double = chartDelta / speed
internal fun practiceCandidateRadius(speed: Float): Double = .201 * speed
internal fun practiceTouchTime(frameChartTime: Double, eventUptimeMs: Long, frameUptimeMs: Long, speed: Float): Double =
    frameChartTime + ((eventUptimeMs - frameUptimeMs) / 1000.0).coerceIn(-.25, .10) * speed

internal fun practiceEventVisualTime(samplePositionMs: Long, sampleUptimeMs: Long,
    eventUptimeMs: Long, latencyMs: Int, speed: Float, advancing: Boolean): Double = practiceVisualTime(
    samplePositionMs / 1000.0 + (if (advancing) ((eventUptimeMs - sampleUptimeMs) / 1000.0).coerceIn(-10.0, 10.0) * speed else 0.0),
    latencyMs, speed)

internal fun practiceMayMiss(confirmedChartTime: Double, targetChartTime: Double, speed: Float,
    badWindow: Double = .20): Boolean =
    confirmedChartTime > targetChartTime + (badWindow + .08) * speed

/** Preview combo is a chart-position count, not a set of scored judgements. */
internal fun practiceHudScore(fullRun: Boolean, noteCount: Int, perfect: Int, good: Int, bad: Int, maxCombo: Int): Int {
    if (!fullRun || noteCount <= 0) return 0
    return calculatePlayScoreAndAccuracy(noteCount, perfect, good, bad,
        (noteCount - perfect - good - bad).coerceAtLeast(0), maxCombo).score
}

internal data class PracticeRange(val start: Double, val end: Double) {
    fun seek(from: Double, delta: Double): Double = (from + delta).coerceIn(start, end)
    companion object {
        fun bounded(start: Double, end: Double, duration: Double): PracticeRange {
            val limit = duration.coerceAtLeast(.001)
            val gap = minOf(.1, limit)
            val safeStart = (if (start.isFinite()) start else 0.0).coerceIn(0.0, limit - gap)
            val safeEnd = (if (end.isFinite()) end else limit).coerceIn(safeStart + gap, limit)
            return PracticeRange(safeStart, safeEnd)
        }
    }
}

/** Counts completed single-finger taps. The caller rejects motion and multi-touch. */
internal class PracticeTripleTap {
    private var side = 0
    private var count = 0
    private var first = 0L
    fun reset() { side = 0; count = 0; first = 0L }
    fun tap(newSide: Int, timeMs: Long): Boolean {
        if (newSide == 0) { reset(); return false }
        if (newSide != side || timeMs - first > 650L || timeMs < first) {
            side = newSide; count = 0; first = timeMs
        }
        count++
        if (count == 3) { reset(); return true }
        return false
    }
}
