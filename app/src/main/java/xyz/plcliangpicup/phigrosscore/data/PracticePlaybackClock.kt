package xyz.plcliangpicup.phigrosscore.data

import kotlin.math.abs

/** Smooth quantized hardware heads without allowing a stalled output to run indefinitely. */
internal class PracticePlaybackClock {
    private var raw = Double.NaN
    private var changedAt = 0L
    private var sampledAt = 0L
    private var position = 0.0
    private var wasAdvancing = false
    data class Sample(val seconds: Double, val advancing: Boolean)
    fun reset() { raw = Double.NaN; wasAdvancing = false }
    fun sample(seconds: Double, nowMs: Long, speed: Float, playing: Boolean): Sample {
        val first = raw.isNaN()
        if (first || seconds != raw) { raw = seconds; changedAt = nowMs }
        val advancing = playing && nowMs - changedAt <= 80
        val elapsed = ((nowMs - sampledAt) / 1000.0).coerceIn(0.0, .1)
        val target = seconds + if (advancing) ((nowMs - changedAt) / 1000.0).coerceIn(0.0, .04) * speed else 0.0
        val predicted = position + if (advancing) elapsed * speed else 0.0
        position = if (first || !advancing || !wasAdvancing || abs(target - predicted) > .15 * speed) target
            else predicted + (target - predicted).coerceIn(-elapsed * speed * .1, elapsed * speed * .1)
        sampledAt = nowMs
        wasAdvancing = advancing
        return Sample(position, advancing)
    }
}
