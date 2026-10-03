package xyz.plcliangpicup.phigrosscore.data

import kotlin.math.abs

internal enum class PracticeGrade { PERFECT, GOOD, BAD, MISS }

/** Phigros challenge-mode windows, in real seconds (independent of playback speed). */
internal data class PracticeJudgeRules(val perfect: Double, val good: Double, val bad: Double) {
    fun grade(delta: Double): PracticeGrade? = when {
        !delta.isFinite() -> null
        abs(delta) <= perfect -> PracticeGrade.PERFECT
        abs(delta) <= good -> PracticeGrade.GOOD
        abs(delta) <= bad -> PracticeGrade.BAD
        else -> null
    }
    companion object {
        val Normal = PracticeJudgeRules(.08, .16, .20)
        val Strict = PracticeJudgeRules(.04, .075, .14)
    }
}

// Hit regions are independent of the user's visual note-size setting.
fun practiceHitRadius(fieldWidth: Float) = fieldWidth * .118125f

/** Keep a swipe briefly available when it reaches a Flick before the note does. */
fun practiceRecentSwipe(lastSwipeMs: Long, nowMs: Long): Boolean =
    lastSwipeMs != Long.MIN_VALUE && nowMs >= lastSwipeMs && nowMs - lastSwipeMs <= 220L

/** A Hold keeps its original finger even when its judge line moves or other fingers tap. */
fun <T> practiceHoldHasContact(ownerPointerId: Long, fingers: Map<Long, T>,
    onLane: (T) -> Boolean): Boolean =
    (ownerPointerId >= 0 && fingers.containsKey(ownerPointerId)) || fingers.values.any(onLane)

data class PracticeHitCandidate(val id: Int, val target: Double, val distance: Float)

fun practiceChooseHit(candidates: List<PracticeHitCandidate>, now: Double, speed: Float,
    fieldWidth: Float, goodWindow: Double = PRACTICE_GOOD_WINDOW): Int? {
    val good = candidates.filter { abs((now - it.target) / speed) <= goodWindow }
    val eligible = good.ifEmpty { candidates }
    val nearest = eligible.minOfOrNull { it.distance } ?: return null
    // Nearby dense notes keep chronological priority; distant simultaneous lanes
    // follow the finger's position. Early BAD must not steal a valid hit.
    return eligible.filter { it.distance <= nearest + fieldWidth * .02f }
        .minWithOrNull(compareBy<PracticeHitCandidate> { it.target }.thenBy { it.distance })?.id
}

/** Map batched input to the line pose actually displayed, without changing hit timing. */
class PracticeDisplayedTimeline {
    private val frames = java.util.ArrayDeque<Pair<Long, Double>>()
    fun clear() = frames.clear()
    fun record(uptimeMs: Long, chartTime: Double) {
        frames.addLast(uptimeMs to chartTime)
        while (frames.size > 64) frames.removeFirst()
    }
    fun at(uptimeMs: Long, fallback: Double): Double =
        frames.lastOrNull { it.first <= uptimeMs }?.second ?: frames.firstOrNull()?.second ?: fallback
}
