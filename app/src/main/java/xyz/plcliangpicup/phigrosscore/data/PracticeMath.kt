package xyz.plcliangpicup.phigrosscore.data

import kotlin.math.*

/** RPE's easing identifiers; values 0 and 1 are linear. */
fun practiceEasing(type: Int, value: Float): Float {
    val x = value.toDouble().coerceIn(0.0, 1.0)
    fun bounce(t: Double): Double {
        val n = 7.5625; val d = 2.75
        return when {
            t < 1 / d -> n * t * t
            t < 2 / d -> n * (t - 1.5 / d).pow(2) + .75
            t < 2.5 / d -> n * (t - 2.25 / d).pow(2) + .9375
            else -> n * (t - 2.625 / d).pow(2) + .984375
        }
    }
    fun elastic(t: Double): Double = when (t) {
        0.0 -> 0.0; 1.0 -> 1.0
        else -> 2.0.pow(-10 * t) * sin((t * 10 - .75) * (2 * PI / 3)) + 1
    }
    val c = 1.70158
    return when (type) {
        2 -> sin(x * PI / 2); 3 -> 1 - cos(x * PI / 2)
        4 -> 1 - (1 - x).pow(2); 5 -> x * x
        6 -> (1 - cos(PI * x)) / 2
        7 -> if (x < .5) 2 * x * x else 1 - (-2 * x + 2).pow(2) / 2
        8 -> 1 - (1 - x).pow(3); 9 -> x.pow(3)
        10 -> 1 - (1 - x).pow(4); 11 -> x.pow(4)
        12 -> if (x < .5) 4 * x.pow(3) else 1 - (-2 * x + 2).pow(3) / 2
        13 -> if (x < .5) 8 * x.pow(4) else 1 - (-2 * x + 2).pow(4) / 2
        14 -> 1 - (1 - x).pow(5); 15 -> x.pow(5)
        16 -> if (x == 1.0) 1.0 else 1 - 2.0.pow(-10 * x)
        17 -> if (x == 0.0) 0.0 else 2.0.pow(10 * x - 10)
        18 -> sqrt(1 - (x - 1).pow(2)); 19 -> 1 - sqrt(1 - x * x)
        20 -> 1 + (c + 1) * (x - 1).pow(3) + c * (x - 1).pow(2)
        21 -> (c + 1) * x.pow(3) - c * x * x
        22 -> if (x < .5) (1 - sqrt(1 - (2 * x).pow(2))) / 2 else (sqrt(1 - (-2 * x + 2).pow(2)) + 1) / 2
        23 -> { val k = c * 1.525; if (x < .5) (2 * x).pow(2) * ((k + 1) * 2 * x - k) / 2 else ((2 * x - 2).pow(2) * ((k + 1) * (2 * x - 2) + k) + 2) / 2 }
        24 -> elastic(x); 25 -> 1 - elastic(1 - x)
        26 -> bounce(x); 27 -> 1 - bounce(1 - x)
        28 -> if (x < .5) (1 - bounce(1 - 2 * x)) / 2 else (1 + bounce(2 * x - 1)) / 2
        29 -> when { x == 0.0 || x == 1.0 -> x; x < .5 -> -(2.0.pow(20 * x - 10) * sin((20 * x - 11.125) * 2 * PI / 4.5)) / 2; else -> 2.0.pow(-20 * x + 10) * sin((20 * x - 11.125) * 2 * PI / 4.5) / 2 + 1 }
        else -> x
    }.toFloat()
}

fun practiceBezier(points: List<Float>, x: Float): Float {
    fun component(t: Float, a: Float, b: Float) = 3 * (1-t).pow(2) * t * a + 3 * (1-t) * t*t * b + t*t*t
    var lo = 0f; var hi = 1f
    repeat(22) { val mid = (lo+hi)/2; if (component(mid, points[0], points[2]) < x) lo=mid else hi=mid }
    return component((lo+hi)/2, points[1], points[3])
}

fun evaluatePracticeLayers(layers: List<List<PracticeEvent>>, time: Double, default: Float): Float =
    if (layers.isEmpty()) default else layers.sumOf { evaluatePracticeEvents(it, time, 0f).toDouble() }.toFloat()

/** Events have already been converted from beats to seconds by the parser. */
class PracticeTravel(events: List<PracticeEvent>) {
    private val layers = events.groupBy { it.layer }.values.toList()
    private val times: DoubleArray
    private val prefix: DoubleArray
    private val first: DoubleArray
    private val last: DoubleArray
    init {
        val boundaries = sortedSetOf(0.0)
        events.forEach { e ->
            boundaries.add(e.startBeat); boundaries.add(e.endBeat)
            if (e.start != e.end && (e.easingType > 1 || e.bezier.isNotEmpty())) {
                val count = ceil((e.endBeat-e.startBeat).coerceAtLeast(0.0)*240).toInt().coerceAtLeast(1)
                for (i in 1 until count) boundaries.add(e.startBeat+(e.endBeat-e.startBeat)*i/count)
            }
        }
        times=boundaries.toDoubleArray(); prefix=DoubleArray(times.size)
        first=DoubleArray(times.size); last=DoubleArray(times.size)
        for(i in times.indices) {
            val end=times.getOrElse(i+1){times[i]+1}
            val eps=min(1e-8,(end-times[i])/4)
            first[i]=evaluatePracticeLayers(layers,times[i]+eps,0f).toDouble()
            last[i]=evaluatePracticeLayers(layers,end-eps,0f).toDouble()
            if(i+1<times.size) prefix[i+1]=prefix[i]+(end-times[i])*(first[i]+last[i])/2
        }
    }
    fun at(time: Double): Double {
        var index=times.binarySearch(time)
        if(index<0) index=-index-2
        if(index<0) return (time-times.first())*first.first()
        val dt=time-times[index]
        if(index==times.lastIndex) return prefix[index]+dt*first[index]
        val slope=(last[index]-first[index])/(times[index+1]-times[index])
        return prefix[index]+first[index]*dt+slope*dt*dt/2
    }
    fun between(start: Double,end: Double)=at(end)-at(start)
}

data class PracticeField(val screenWidth: Float, val height: Float) {
    val width = min(screenWidth, height * 16f / 9f)
    val left = (screenWidth-width)/2
    val centerX = screenWidth/2
    val centerY = height/2
    val xScale = width/1350f
    val yScale = height/900f
    val noteWidth = width*.13175016f
}

const val PRACTICE_PERFECT_WINDOW = .08
const val PRACTICE_GOOD_WINDOW = .16
const val PRACTICE_BAD_WINDOW = .20
const val PRACTICE_RELEASE_GRACE = .05

fun practiceTangentDistance(touchX: Float, touchY: Float, lineX: Float, lineY: Float, rotation: Float, targetX: Float): Float {
    val angle = Math.toRadians(rotation.toDouble())
    return abs((touchX-lineX)*cos(angle).toFloat() + (touchY-lineY)*sin(angle).toFloat() - targetX)
}

fun practiceTravelPixels(line: PracticeJudgeLine, from: Double, to: Double, height: Float): Float =
    (line.travel.between(from,to) * height * (10.0 / 45.0 / .83175) / 2).toFloat()
