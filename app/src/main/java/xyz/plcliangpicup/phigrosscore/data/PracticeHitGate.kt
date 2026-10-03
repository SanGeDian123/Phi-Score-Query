package xyz.plcliangpicup.phigrosscore.data

/** Tickets belong to one continuous playback/mode interval. */
internal class PracticeHitGate {
    private var generation = 0L
    private var running = false
    private var automatic = false
    private var released = false
    data class Ticket(val generation: Long, val requestedAt: Long)
    @Synchronized fun setRunning(value: Boolean) {
        if (running != value) { running = value; generation++ }
    }
    @Synchronized fun setAutomatic(value: Boolean) {
        if (automatic != value) { automatic = value; generation++ }
    }
    @Synchronized fun request(auto: Boolean, now: Long): Ticket? =
        if (running && !released && auto == automatic) Ticket(generation, now) else null
    @Synchronized fun valid(ticket: Ticket): Boolean =
        running && !released && ticket.generation == generation
    @Synchronized fun canRetry(ticket: Ticket, now: Long): Boolean =
        valid(ticket) && now - ticket.requestedAt in 0..24
    @Synchronized fun release() { released = true; running = false; generation++ }
}
