package xyz.plcliangpicup.phigrosscore.data

import kotlin.math.abs
import kotlin.math.roundToInt

/** ACC of judged notes only, matching the final Phigros Perfect/Good weights. */
internal fun practiceRealtimeAccuracy(perfect: Int, good: Int, bad: Int, miss: Int): Double {
    val judged = perfect + good + bad + miss
    return if (judged <= 0) 100.0 else (perfect + good * .65) * 100.0 / judged
}

/** Positive means late; negative means early. */
internal fun practiceGoodTimingLabel(deltaSeconds: Double): String {
    val milliseconds = (abs(deltaSeconds) * 1000.0).roundToInt()
    return "GOOD ${if (deltaSeconds >= 0.0) "+" else "−"}$milliseconds ms · ${if (deltaSeconds >= 0.0) "偏晚" else "偏早"}"
}
