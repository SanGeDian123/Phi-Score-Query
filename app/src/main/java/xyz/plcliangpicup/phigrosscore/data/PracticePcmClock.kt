package xyz.plcliangpicup.phigrosscore.data

/** Maintain 120 ms of real output at slow and fast rates, plus a minimum write reserve. */
internal fun practicePlayingLeadFrames(sampleRate: Int, speed: Float): Long =
    maxOf((sampleRate * .08f).toLong(), (sampleRate * speed * .12f).toLong())

/** Original-song coordinates derived from consumed PCM frames, never decoder time. */
internal class PracticePcmClock(private val sampleRate: Int, private val frames: Int) {
    init { require(sampleRate > 0 && frames >= 0) }
    fun frameAt(milliseconds: Long): Int = (milliseconds.coerceAtLeast(0) * sampleRate / 1000).coerceAtMost(frames.toLong()).toInt()
    fun consumedFrame(origin: Int, head: Long, firstMusicFrame: Long): Int =
        (origin.toLong() + (head - firstMusicFrame).coerceAtLeast(0)).coerceIn(0, frames.toLong()).toInt()
    fun milliseconds(frame: Int): Int = (frame.coerceIn(0, frames).toLong() * 1000 / sampleRate).toInt()
}
