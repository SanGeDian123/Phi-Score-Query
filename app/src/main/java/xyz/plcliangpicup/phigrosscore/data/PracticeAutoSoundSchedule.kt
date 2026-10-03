package xyz.plcliangpicup.phigrosscore.data

/** Owned exclusively by the audio thread; rendering never drives this cursor. */
internal class PracticeAutoSoundSchedule(private val times: DoubleArray, private val ids: IntArray) {
    private var next = 0
    init { require(times.size == ids.size) }
    fun seek(seconds: Double, offsetSeconds: Double = 0.0) {
        var lo = 0; var hi = times.size
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (times[mid] + offsetSeconds < seconds - .000001) lo = mid + 1 else hi = mid }
        next = lo
    }
    fun dispatch(seconds: Double, offsetSeconds: Double = 0.0, hit: (Int) -> Unit) {
        while (next < times.size && times[next] + offsetSeconds <= seconds) hit(ids[next++])
    }
}
