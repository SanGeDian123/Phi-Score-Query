package xyz.plcliangpicup.phigrosscore.ui

import xyz.plcliangpicup.phigrosscore.data.PracticeNoisePoint

/** TouchBlockBehavior: ten slots, 100 ms show/hide; ownership lasts until lift. */
internal class PracticeNoiseTouch {
    internal data class Hover(val id: Long, var point: PracticeNoisePoint, var from: Float,
        var target: Float, var changedAt: Double) {
        fun scale(time: Double): Float = from + (target - from) *
            ((time - changedAt) / .1).toFloat().coerceIn(0f, 1f)
    }
    private val slots = linkedMapOf<Long, Hover>()
    fun update(touches: Map<Long, PracticeNoisePoint>, time: Double): List<Hover> {
        slots.values.forEach { hover ->
            val point = touches[hover.id]
            val target = if (point == null) 0f else 1f
            if (point != null) hover.point = point
            if (target != hover.target) {
                hover.from = hover.scale(time); hover.target = target; hover.changedAt = time
            }
        }
        slots.entries.removeAll { it.value.target == 0f && it.value.scale(time) <= 0f }
        touches.forEach { (id, point) ->
            if (id !in slots && slots.size < 10) slots[id] = Hover(id, point, 0f, 1f, time)
        }
        return slots.values.toList()
    }
    fun reset() = slots.clear()
}
