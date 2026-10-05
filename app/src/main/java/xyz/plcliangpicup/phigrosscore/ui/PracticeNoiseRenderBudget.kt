package xyz.plcliangpicup.phigrosscore.ui

import kotlin.math.min
import kotlin.math.sqrt

/** Bound visual work independently of display resolution and the judgement clock. */
internal data class PracticeNoiseRenderSize(val width: Int, val height: Int)

internal fun practiceNoiseRenderSize(width: Float, height: Float, maxPixels: Int,
    divisor: Int = 1): PracticeNoiseRenderSize {
    val w = (width / divisor).coerceAtLeast(1f)
    val h = (height / divisor).coerceAtLeast(1f)
    val scale = min(1f, sqrt(maxPixels / (w * h)))
    // Floor rather than round so even unusual aspect ratios stay within the budget.
    return PracticeNoiseRenderSize((w * scale).toInt().coerceAtLeast(1),
        (h * scale).toInt().coerceAtLeast(1))
}

internal class PracticeNoiseVisualClock {
    private var lastUpdate = Double.NaN
    private var width = 0f
    private var height = 0f
    private var empty = true

    fun refresh(now: Double, w: Float, h: Float, isEmpty: Boolean): Boolean {
        val due = !lastUpdate.isFinite() || now < lastUpdate || now - lastUpdate >= 1.0 / 30 - 1e-6 ||
            w != width || h != height || isEmpty != empty
        if (due) { lastUpdate = now; width = w; height = h; empty = isEmpty }
        return due
    }

    fun reset() { lastUpdate = Double.NaN }
}
