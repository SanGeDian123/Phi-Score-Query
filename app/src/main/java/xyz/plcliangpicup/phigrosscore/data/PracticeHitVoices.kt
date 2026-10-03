package xyz.plcliangpicup.phigrosscore.data

/** Caller serializes access; injected native calls allow rejection recovery tests. */
internal class PracticeHitVoices(capacity: Int, private val start: (Int) -> Int, private val stop: (Int) -> Unit) {
    private val streams = IntArray(capacity.also { require(it > 0) })
    private var oldest = 0
    private var count = 0
    fun play(sample: Int): Boolean {
        var stream = start(sample)
        if (stream == 0) {
            if (count > 0) {
                stop(streams[oldest])
                oldest = (oldest + 1) % streams.size
                count--
            }
            stream = start(sample)
        }
        if (stream == 0) return false
        if (count == streams.size) { oldest = (oldest + 1) % streams.size; count-- }
        streams[(oldest + count) % streams.size] = stream
        count++
        return true
    }
}
