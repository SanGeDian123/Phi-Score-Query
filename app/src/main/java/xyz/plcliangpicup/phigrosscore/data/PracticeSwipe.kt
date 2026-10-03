package xyz.plcliangpicup.phigrosscore.data

/** One continuous fast stroke consumes at most one Flick. A pause or a turn
 * starts another stroke, even if Android sent no stationary MOVE events.
 */
internal data class PracticeSwipe(
    val consumed: Boolean = false,
    val lastSwipeMs: Long = Long.MIN_VALUE,
    val directionX: Float = 0f,
    val directionY: Float = 0f,
) {
    fun sample(dx: Float, dy: Float, elapsedMs: Long, nowMs: Long, fieldWidth: Float): PracticeSwipe {
        if (elapsedMs <= 0 || fieldWidth <= 0) return this
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        val speed = distance * 1000f / elapsedMs / fieldWidth
        val turned = distance > fieldWidth * .002f && dx * directionX + dy * directionY < 0f
        val rearmed = elapsedMs >= 80 || speed < .2f || turned
        val available = !consumed || rearmed
        val fast = speed >= .8f
        return copy(consumed = !available,
            lastSwipeMs = if (available && fast) nowMs else if (rearmed) Long.MIN_VALUE else lastSwipeMs,
            directionX = if (fast) dx / distance else directionX,
            directionY = if (fast) dy / distance else directionY)
    }
    fun consume() = copy(consumed = true, lastSwipeMs = Long.MIN_VALUE)
}
