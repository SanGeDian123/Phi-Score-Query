package xyz.plcliangpicup.phigrosscore.data

/** Pause hit region uses chart-field pixels, not Material's enlarged touch target. */
internal data class PracticePauseTarget(val centerX: Float, val centerY: Float, val radius: Float) {
    fun contains(x: Float, y: Float) = kotlin.math.abs(x - centerX) <= radius && kotlin.math.abs(y - centerY) <= radius
}

internal fun practicePauseTarget(field: PracticeField): PracticePauseTarget {
    val scale = field.height / 864f
    return PracticePauseTarget(field.left + 42f * scale, 47f * scale, 32f * scale)
}

/** Only completed, stationary, single-finger taps can request a pause.
 * The gameplay caller cancels the gesture whenever the touch hits a note.
 */
internal class PracticePauseGesture {
    private var pointer = -1L
    private var downAt = 0L
    private var downX = 0f
    private var downY = 0f
    private var firstTapAt = Long.MIN_VALUE
    private var firstTapX = 0f
    private var firstTapY = 0f

    fun reset() { pointer = -1L; firstTapAt = Long.MIN_VALUE }

    fun down(id: Long, x: Float, y: Float, timeMs: Long, inside: Boolean) {
        if (!inside) { reset(); return }
        pointer = id; downAt = timeMs; downX = x; downY = y
    }

    fun move(id: Long, x: Float, y: Float, inside: Boolean, slop: Float) {
        if (id != pointer || !inside || distanceSquared(x, y, downX, downY) > slop * slop) reset()
    }

    fun up(id: Long, x: Float, y: Float, timeMs: Long, inside: Boolean,
        slop: Float, doubleTap: Boolean): Boolean {
        val valid = pointer == id && inside && timeMs - downAt in 0L..250L &&
            distanceSquared(x, y, downX, downY) <= slop * slop
        pointer = -1L
        if (!valid) { reset(); return false }
        if (!doubleTap) { reset(); return true }
        if (firstTapAt != Long.MIN_VALUE && timeMs - firstTapAt in 0L..350L &&
            distanceSquared(x, y, firstTapX, firstTapY) <= slop * slop * 4f) {
            reset(); return true
        }
        firstTapAt = timeMs; firstTapX = x; firstTapY = y
        return false
    }

    private fun distanceSquared(x: Float, y: Float, otherX: Float, otherY: Float): Float =
        (x - otherX) * (x - otherX) + (y - otherY) * (y - otherY)
}
