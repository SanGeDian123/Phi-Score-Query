package xyz.plcliangpicup.phigrosscore.data

/** Project a fresh hardware presentation timestamp, in source PCM frames.
 * Reject timestamps from before a flush/start/rate change or a stalled output.
 */
internal fun practicePresentedFrame(timestampFrame: Long, timestampNanos: Long,
    nowNanos: Long, epochNanos: Long, firstMusicFrame: Long, queuedFrames: Long,
    sampleRate: Int, speed: Float): Double? {
    val age = nowNanos - timestampNanos
    if (timestampNanos < epochNanos || age !in 0L..100_000_000L ||
        timestampFrame < firstMusicFrame || timestampFrame > queuedFrames) return null
    return (timestampFrame + age / 1e9 * sampleRate * speed).coerceAtMost(queuedFrames.toDouble())
}
